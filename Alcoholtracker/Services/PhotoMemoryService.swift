import UIKit
import ImageIO

// MARK: - PhotoMemoryService
//
// nonisolated: callers load images off the main actor (Task.detached in the
// photo views), and everything here is pure file/CG work.

nonisolated enum PhotoMemoryService {

    // Saved photos are capped at this edge length; full camera resolution
    // would grow Documents by several MB per memory without visible benefit.
    private static let maxStoredDimension: CGFloat = 2048

    private static var photosDirectory: URL {
        let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        let dir = docs.appendingPathComponent("PhotoMemories", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }

    static func save(_ image: UIImage) -> String? {
        let filename = "\(UUID().uuidString).jpg"
        let url = photosDirectory.appendingPathComponent(filename)
        let scaled = resizedToFit(image, maxDimension: maxStoredDimension)
        guard let data = scaled.jpegData(compressionQuality: 0.75) else { return nil }
        do {
            try data.write(to: url, options: .atomic)
            return filename
        } catch {
            return nil
        }
    }

    static func load(_ filename: String) -> UIImage? {
        let url = photosDirectory.appendingPathComponent(filename)
        guard let data = try? Data(contentsOf: url) else { return nil }
        return UIImage(data: data)
    }

    // Decodes directly at thumbnail size via ImageIO instead of loading the
    // full JPEG: the strip shows 76 pt cells and full decodes caused memory
    // spikes and scroll jank with many memories.
    static func loadThumbnail(_ filename: String, maxPixel: CGFloat = 240) -> UIImage? {
        let url = photosDirectory.appendingPathComponent(filename)
        let options: [CFString: Any] = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceThumbnailMaxPixelSize: maxPixel,
            kCGImageSourceCreateThumbnailWithTransform: true
        ]
        guard
            let source = CGImageSourceCreateWithURL(url as CFURL, nil),
            let cgImage = CGImageSourceCreateThumbnailAtIndex(source, 0, options as CFDictionary)
        else { return nil }
        return UIImage(cgImage: cgImage)
    }

    static func delete(_ filename: String) {
        let url = photosDirectory.appendingPathComponent(filename)
        try? FileManager.default.removeItem(at: url)
    }

    private static func resizedToFit(_ image: UIImage, maxDimension: CGFloat) -> UIImage {
        let maxSide = max(image.size.width, image.size.height)
        guard maxSide > maxDimension, maxSide > 0 else { return image }
        let factor = maxDimension / maxSide
        let newSize = CGSize(width: image.size.width * factor, height: image.size.height * factor)
        let format = UIGraphicsImageRendererFormat.default()
        format.scale = 1
        return UIGraphicsImageRenderer(size: newSize, format: format).image { _ in
            image.draw(in: CGRect(origin: .zero, size: newSize))
        }
    }
}
