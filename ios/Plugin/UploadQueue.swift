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
    let form: Data
    let completion: (Data?, URLResponse?, Error?) -> Void
    let uploadId: String
    let connectTimeout: Double?
    let readTimeout: Double?
    static let UPLOAD_TIMEOUT = 600.0 // 10 minutes
    
    init(urlRequest: URLRequest, form: Data, uploadId: String, connectTimeout: Double?, readTimeout: Double?, completion: @escaping (Data?, URLResponse?, Error?) -> Void) {
        self.urlRequest = urlRequest
        self.form = form
        self.uploadId = uploadId
        self.connectTimeout = connectTimeout
        self.readTimeout = readTimeout
        self.completion = completion
        super.init()
    }
    
    override func main() {
        guard !isCancelled else {
            CAPLog.print("❌ Upload \(uploadId) cancelled before starting")
            return
        }
        
        let fileSize = ByteCountFormatter.string(fromByteCount: Int64(form.count), countStyle: .file)
        let startTime = Date()
        CAPLog.print("▶️ Starting upload \(uploadId) at \(startTime)")
        CAPLog.print("📦 File size for \(uploadId): \(fileSize)")
        
        Task {
            do {
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
}
