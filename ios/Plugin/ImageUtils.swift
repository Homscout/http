import Foundation
import UIKit

class ImageUtils {
    static func resizeImage(_ url: URL, options: [String: Any]) -> URL? {
        guard let image = UIImage(contentsOfFile: url.path) else { return nil }
        
        let maxWidth = options["maxWidth"] as? CGFloat ?? image.size.width
        let maxHeight = options["maxHeight"] as? CGFloat ?? image.size.height
        let quality = CGFloat((options["quality"] as? Int ?? 80)) / 100.0
        let format = options["format"] as? String ?? "jpg"
        
        let scale = min(maxWidth/image.size.width, maxHeight/image.size.height, 1.0)
        let newSize = CGSize(width: image.size.width * scale,
                             height: image.size.height * scale)
        
        UIGraphicsBeginImageContext(newSize)
        image.draw(in: CGRect(origin: .zero, size: newSize))
        let resizedImage = UIGraphicsGetImageFromCurrentImageContext()
        UIGraphicsEndImageContext()
        
        let tempUrl = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString)
            .appendingPathExtension(format)
        
        if format == "png" {
            try? resizedImage?.pngData()?.write(to: tempUrl)
        } else {
            try? resizedImage?.jpegData(compressionQuality: quality)?.write(to: tempUrl)
        }
        
        return tempUrl
    }
}
