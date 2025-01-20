import Foundation
import UIKit
import Capacitor

struct ImageResult {
    let url: URL
    let width: Int
    let height: Int
    let fileSize: Int64
}

class ImageUtils {
    static func resizeImage(_ url: URL, options: [String: Any], id: String) -> ImageResult? {
        return autoreleasepool {
            guard let image = UIImage(contentsOfFile: url.path) else { return Optional.none }
                        
            let maxWidth = options["maxWidth"] as? CGFloat ?? image.size.width
            let maxHeight = options["maxHeight"] as? CGFloat ?? image.size.height
            let quality = CGFloat((options["quality"] as? Int ?? 80)) / 100.0
            let fileFormat = options["format"] as? String ?? "jpg"
            
            let scale = min(maxWidth/image.size.width, maxHeight/image.size.height, 1.0)
            let newSize = CGSize(width: floor(image.size.width * scale),
                                 height: floor(image.size.height * scale))
            
            let format = UIGraphicsImageRendererFormat()
            format.scale = 1.0 // Force 1:1 pixel scaling
            format.preferredRange = .standard
            
            let renderer = UIGraphicsImageRenderer(size: newSize, format: format)
            let resizedImage = renderer.image { context in
                // Disable antialiasing to prevent edge artifacts
                context.cgContext.setRenderingIntent(.relativeColorimetric)
                context.cgContext.interpolationQuality = .medium
                
                // Draw the image at exact size
                image.draw(in: CGRect(origin: .zero, size: newSize))
            }
            
            
            let tempUrl = FileManager.default.temporaryDirectory
                .appendingPathComponent(UUID().uuidString)
                .appendingPathExtension(fileFormat)
            
            var fileSize: Int64 = 0
            if fileFormat == "png" {
                if let data = resizedImage.pngData() {
                    try? data.write(to: tempUrl)
                    fileSize = Int64(data.count)
                }
            } else {
                if let data = resizedImage.jpegData(compressionQuality: quality) {
                    try? data.write(to: tempUrl)
                    fileSize = Int64(data.count)
                }
            }
            
            CAPLog.print("📦 Resized image \(id) to \(ByteCountFormatter.string(fromByteCount: fileSize, countStyle: ByteCountFormatter.CountStyle.binary)) bytes")
                        
            return ImageResult(
                url: tempUrl,
                width: Int(newSize.width),
                height: Int(newSize.height),
                fileSize: fileSize
            )
        }
    }
}
