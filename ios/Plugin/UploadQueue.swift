//
//  UploadQueue.swift
//  Plugin
//
//  Created by Matt Rozema on 2/12/25.
//  Copyright © 2025 Max Lynch. All rights reserved.
//
import Capacitor


class UploadQueue {
    static let shared = UploadQueue()
    private let queue = OperationQueue()
    private let currentMaxConcurrent = 3
    private let urlSession: URLSession
    
    private init() {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 60 * 5 // 5 minute timeout for initial connection
        config.timeoutIntervalForResource = 60 * 30 // 30 minute timeout for resource
        urlSession = URLSession(configuration: config)
        queue.maxConcurrentOperationCount = currentMaxConcurrent
        queue.qualityOfService = .userInitiated
    }
    
    func getUploadSession() -> URLSession {
        return urlSession
    }
    
    func addUpload(_ operation: Operation) {
        CAPLog.print("📥 Queueing new upload. Current queue size: \(queue.operationCount)")
        queue.addOperation(operation)
        CAPLog.print("📊 Queue status: \(queue.operationCount) total, \(queue.operations.filter { $0.isExecuting }.count) executing")
    }
}

@objc class UploadOperation: Operation, @unchecked Sendable {
    let urlRequest: URLRequest
    let fileUrl: URL
    let body: [String: Any]
    let paramName: String
    let boundary: String
    let completion: @Sendable (Data?, URLResponse?, Error?) -> Void
    let uploadId: String
    let resizeOptions: JSObject?
    let widthHeader: String
    let heightHeader: String
    let sizeHeader: String
    
    init(urlRequest: URLRequest,
         fileUrl: URL,
         body: [String: Any],
         boundary: String,
         name: String,
         uploadId: String,
         resizeOptions: JSObject?,
         widthHeader: String,
         heightHeader: String,
         sizeHeader: String,
         completion: @escaping @Sendable (Data?, URLResponse?, Error?) -> Void) {
        self.urlRequest = urlRequest
        self.fileUrl = fileUrl
        self.body = body
        self.boundary = boundary
        self.paramName = name
        self.uploadId =  uploadId
        self.resizeOptions = resizeOptions
        self.widthHeader = widthHeader
        self.heightHeader = heightHeader
        self.sizeHeader = sizeHeader
        self.completion = completion
        super.init()
    }
    
    override func main() {
        guard !isCancelled else {
            CAPLog.print("❌ Upload \(uploadId) cancelled before starting")
            return
        }
       
        let startTime = Date()
        CAPLog.print("▶️ Starting upload \(uploadId) at \(startTime)")
        
        do {
            // Handle image resizing if needed
            let (finalUrl, metadata) = {
                if let resizeOptions = resizeOptions {
                    if let result = ImageUtils.resizeImage(fileUrl, options: resizeOptions, id: uploadId) {
                        return (result.url, [
                            widthHeader: "\(result.width)",
                            heightHeader: "\(result.height)",
                            sizeHeader: "\(result.fileSize)"
                        ])
                    }
                }
                
                // For both the resize-fail and no-resize cases
                if let metadata = try? getImageMetadata(fileUrl) {
                    return (fileUrl, metadata)
                }
                return (fileUrl, [:])
            }()
            
            let form = try generateMultipartForm(finalUrl, body.merging(metadata) { (_, new) in new })
            
            let task = UploadQueue.shared.getUploadSession().uploadTask(with: urlRequest, from: form) { (data, response, error) in
                if let error = error {
                    CAPLog.print("❌ Upload \(self.uploadId) failed after \(String(format: "%.2f", Date().timeIntervalSince(startTime)))s: \(error)")
                    self.completion(nil, nil, error)
                } else {
                    CAPLog.print("✅ Upload \(self.uploadId) success: \(String(format: "%.2f", Date().timeIntervalSince(startTime)))s")
                    self.completion(data, response, nil)
                }
            }

            task.resume()
        } catch {
            CAPLog.print("❌ Upload \(uploadId) failed: \(error)")
            self.completion(nil, nil, error)

        }
    }
    
    private func getImageMetadata(_ url: URL) throws -> [String: String] {
        guard let imageSource = CGImageSourceCreateWithURL(url as CFURL, nil),
              let imageProperties = CGImageSourceCopyPropertiesAtIndex(imageSource, 0, nil) as? [String: Any] else {
            throw URLError(.cannotDecodeContentData)
        }
        
        let pixelWidth = imageProperties[kCGImagePropertyPixelWidth as String] as? Int ?? 0
        let pixelHeight = imageProperties[kCGImagePropertyPixelHeight as String] as? Int ?? 0
        let fileSize = try FileManager.default.attributesOfItem(atPath: url.path)[.size] as? Int64 ?? 0
        
        return [
            self.widthHeader: "\(pixelWidth)",
            self.heightHeader: "\(pixelHeight)",
            self.sizeHeader: "\(fileSize)"
        ]
    }
    
    private func generateMultipartForm(_ url: URL, _ body: [String:Any]) throws -> Data? {
        var data = Data()
        var remainingBody = body

        // Handle 'key' field first
        if let keyValue = body["key"] as? String {
            data.append("\r\n--\(boundary)\r\n".data(using: .utf8)!)
            data.append("Content-Disposition: form-data; name=\"key\"\r\n\r\n".data(using: .utf8)!)
            data.append(keyValue.data(using: .utf8)!)
            remainingBody.removeValue(forKey: "key")
        }

        // Handle remaining fields
        remainingBody.forEach { key, value in
            if let stringArray = value as? [String] {
                for item in stringArray {
                    data.append("\r\n--\(boundary)\r\n".data(using: .utf8)!)
                    data.append("Content-Disposition: form-data; name=\"\(key)\"\r\n\r\n".data(using: .utf8)!)
                    data.append(item.data(using: .utf8)!)
                }
            } else {
                data.append("\r\n--\(boundary)\r\n".data(using: .utf8)!)
                data.append("Content-Disposition: form-data; name=\"\(key)\"\r\n\r\n".data(using: .utf8)!)
                data.append((value as! String).data(using: .utf8)!)
            }
        }

        // Add file data
        let fileData = try Data(contentsOf: url)
        let fname = url.lastPathComponent
        let mimeType = FilesystemUtils.mimeTypeForPath(path: fname)
        data.append("\r\n--\(boundary)\r\n".data(using: .utf8)!)
        data.append(
            "Content-Disposition: form-data; name=\"\(paramName)\"; filename=\"\(fname)\"\r\n".data(
                using: .utf8)!)
        data.append("Content-Type: \(mimeType)\r\n\r\n".data(using: .utf8)!)
        data.append(fileData)

        data.append("\r\n--\(boundary)--\r\n".data(using: .utf8)!)

        return data
    }
}
