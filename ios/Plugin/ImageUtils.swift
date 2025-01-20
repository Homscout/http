import Foundation
import UIKit

struct ImageResult {
    let url: URL
    let width: Int
    let height: Int
    let fileSize: Int64
}

class ImageUtils {
    static func resizeImage(_ url: URL, options: [String: Any]) -> ImageResult? {
        guard let image = UIImage(contentsOfFile: url.path) else { return nil }
        
        let maxWidth = options["maxWidth"] as? CGFloat ?? image.size.width
        let maxHeight = options["maxHeight"] as? CGFloat ?? image.size.height
        let quality = CGFloat((options["quality"] as? Int ?? 80)) / 100.0
        let format = options["format"] as? String ?? "jpg"
        
        let scale = min(maxWidth/image.size.width, maxHeight/image.size.height, 1.0)
        let newSize = CGSize(width: image.size.width * scale,
                             height: image.size.height * scale)
        
        let renderer = UIGraphicsImageRenderer(size: newSize)
        let resizedImage = renderer.image { context in
            // Disable antialiasing to prevent edge artifacts
            context.cgContext.setRenderingIntent(.absoluteColorimetric)
            context.cgContext.interpolationQuality = .high
            
            // Draw the image at exact size
            image.draw(in: CGRect(origin: .zero, size: newSize))
        }
        
        let tempUrl = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString)
            .appendingPathExtension(format)
        
        var fileSize: Int64 = 0
        if format == "png" {
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
        
        return ImageResult(
            url: tempUrl,
            width: Int(newSize.width),
            height: Int(newSize.height),
            fileSize: fileSize
        )
    }
}
