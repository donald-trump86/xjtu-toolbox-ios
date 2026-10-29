import Foundation
import CoreGraphics
import ImageIO
import UniformTypeIdentifiers

// Render the fork's original Tengfei Tower / open-book artwork into the iOS asset.
// ImageIO works without AppKit or WindowServer.
let args = CommandLine.arguments
precondition(args.count == 3, "Usage: swift generate-ios-icons.swift source.png output.png")
guard let source = CGImageSourceCreateWithURL(URL(fileURLWithPath: args[1]) as CFURL, nil),
      let image = CGImageSourceCreateImageAtIndex(source, 0, nil) else {
    fatalError("Cannot read source icon")
}
precondition(image.width == image.height, "Source icon must be square")
let bitmap = CGContext(
    data: nil, width: 1024, height: 1024, bitsPerComponent: 8, bytesPerRow: 0,
    space: CGColorSpace(name: CGColorSpace.sRGB)!, bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue
)!
let rect = CGRect(x: 0, y: 0, width: 1024, height: 1024)
bitmap.setFillColor(CGColor(gray: 1, alpha: 1))
bitmap.fill(rect)
bitmap.interpolationQuality = .high
bitmap.draw(image, in: rect)
let output = CGImageDestinationCreateWithURL(URL(fileURLWithPath: args[2]) as CFURL, UTType.png.identifier as CFString, 1, nil)!
CGImageDestinationAddImage(output, bitmap.makeImage()!, nil)
precondition(CGImageDestinationFinalize(output), "Cannot write icon")
