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
    
    private init() {
        queue.maxConcurrentOperationCount = currentMaxConcurrent
    }
    
    func getUploadSession(connectTimeout: Double? = nil, readTimeout: Double? = nil) -> URLSession {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForResource = (readTimeout ?? 300000.0) / 1000.0
        config.timeoutIntervalForRequest = (connectTimeout ?? 60000.0) / 1000.0
        return URLSession(configuration: config)
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
    let connectTimeout: Double?
    let readTimeout: Double?
    let resizeOptions: JSObject?
    let widthHeader: String
    let heightHeader: String
    let sizeHeader: String
    static let UPLOAD_TIMEOUT = 600.0 // 10 minutes
    
    init(urlRequest: URLRequest,
         fileUrl: URL,
         body: [String: Any],
         boundary: String,
         name: String,
         connectTimeout: Double?,
         readTimeout: Double?,
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
        self.uploadId =  fileUrl.lastPathComponent
        self.connectTimeout = connectTimeout
        self.readTimeout = readTimeout
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
        
        Task {
            do {
                // Handle image resizing if needed
                let (finalUrl, metadata) = {
                    if let resizeOptions = resizeOptions {
                        if let result = ImageUtils.resizeImage(fileUrl, options: resizeOptions) {
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
                
                // Merge metadata into the body
                let updatedBody = body.merging(metadata) { (_, new) in new }

                guard let form = UploadOperation.generateMultipartForm(fileUrl, paramName, boundary, updatedBody) else {
                    throw URLError(.cannotCreateFile)
                }
                
                CAPLog.print("📦 File size for \(uploadId): \(ByteCountFormatter.string(fromByteCount: Int64(body.count), countStyle: .file))")
                
                let session = UploadQueue.shared.getUploadSession(connectTimeout: connectTimeout, readTimeout: readTimeout)
                let (data, response) = try await session.upload(for: urlRequest, from: form)
                                
                if let httpResponse = response as? HTTPURLResponse {
                    CAPLog.print("✅ Upload \(uploadId) completed with status: \(httpResponse.statusCode)")
                    CAPLog.print("⏱️ Duration for \(uploadId): \(String(format: "%.2f", Date().timeIntervalSince(startTime)))s")
                }
                
                completion(data, response, nil)
                
            } catch {
                CAPLog.print("❌ Upload \(uploadId) failed after \(String(format: "%.2f", Date().timeIntervalSince(startTime)))s: \(error.localizedDescription)")
                completion(nil, nil, error)
            }
            
            CAPLog.print("🏁 Upload \(uploadId) operation finished")
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
    
    private static func generateMultipartForm(_ url: URL, _ name: String, _ boundary: String, _ body: [String:Any]) -> Data? {
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
        guard let fileData = try? Data(contentsOf: url) else { return nil }
        let fname = url.lastPathComponent
        let mimeType = FilesystemUtils.mimeTypeForPath(path: fname)
        data.append("\r\n--\(boundary)\r\n".data(using: .utf8)!)
        data.append(
            "Content-Disposition: form-data; name=\"\(name)\"; filename=\"\(fname)\"\r\n".data(
                using: .utf8)!)
        data.append("Content-Type: \(mimeType)\r\n\r\n".data(using: .utf8)!)
        data.append(fileData)

        data.append("\r\n--\(boundary)--\r\n".data(using: .utf8)!)

        return data
    }
}
