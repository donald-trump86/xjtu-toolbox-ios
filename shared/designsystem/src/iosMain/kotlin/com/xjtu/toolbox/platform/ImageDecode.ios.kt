@file:OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)

package com.xjtu.toolbox.platform

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.IntSize
import kotlin.io.encoding.Base64
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Color
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import platform.Foundation.NSProcessInfo

private const val MAX_IMAGE_PIXELS = 32L * 1024 * 1024

private fun validDimensions(width: Int, height: Int): Boolean =
    width > 0 && height > 0 && width.toLong() * height <= MAX_IMAGE_PIXELS

actual fun decodeImageSize(bytes: ByteArray): IntSize? = runCatching {
    val image = Image.makeFromEncoded(bytes)
    try {
        if (image.width <= 0 || image.height <= 0) null else IntSize(image.width, image.height)
    } finally { image.close() }
}.getOrNull()

actual fun decodeImage(bytes: ByteArray, size: IntSize, maxDim: Int): ImageBitmap? = runCatching {
    if (maxDim <= 0) return@runCatching null
    val image = Image.makeFromEncoded(bytes)
    try {
        val scale = minOf(1.0, maxDim.toDouble() / maxOf(image.width, image.height))
        renderImage(image, (image.width * scale).toInt().coerceAtLeast(1), (image.height * scale).toInt().coerceAtLeast(1))
    } finally { image.close() }
}.getOrNull()

actual fun decodeImageFull(bytes: ByteArray): ImageBitmap? = runCatching {
    val image = Image.makeFromEncoded(bytes)
    try { renderImage(image, image.width, image.height) } finally { image.close() }
}.getOrNull()

actual fun decodeImageDownToWidth(bytes: ByteArray, size: IntSize, wantWidthPx: Int, halveSample: Boolean): ImageBitmap? = runCatching {
    val image = Image.makeFromEncoded(bytes)
    try {
        var sample = 1
        if (wantWidthPx > 0) {
            while (image.width.toLong() / (sample.toLong() * 2) >= wantWidthPx) sample *= 2
        }
        if (halveSample) sample = (sample / 2).coerceAtLeast(1)
        renderImage(image, (image.width / sample).coerceAtLeast(1), (image.height / sample).coerceAtLeast(1))
    } finally { image.close() }
}.getOrNull()

private fun renderImage(image: Image, width: Int, height: Int): ImageBitmap? {
    if (!validDimensions(width, height)) return null
    val surface = Surface.makeRasterN32Premul(width, height)
    try {
        surface.canvas.clear(Color.TRANSPARENT)
        surface.canvas.drawImageRect(image, Rect.makeWH(image.width.toFloat(), image.height.toFloat()), Rect.makeWH(width.toFloat(), height.toFloat()))
        return surface.makeImageSnapshot().toComposeImageBitmap()
    } finally { surface.close() }
}

/** Skia getColor returns unpremultiplied ARGB, including for transparent images. */
actual fun decodeImagePixels(bytes: ByteArray): ImagePixels? = runCatching {
    val image = Image.makeFromEncoded(bytes)
    try {
        val width = image.width
        val height = image.height
        if (!validDimensions(width, height)) return@runCatching null
        val bitmap = Bitmap()
        try {
            if (!bitmap.allocPixels(ImageInfo.makeN32(width, height, ColorAlphaType.UNPREMUL))) return@runCatching null
            if (!image.readPixels(bitmap)) return@runCatching null
            val pixels = IntArray(width * height) { i -> bitmap.getColor(i % width, i / width) }
            ImagePixels(width, height, pixels)
        } finally { bitmap.close() }
    } finally { image.close() }
}.getOrNull()

actual fun cropImageToJpegDataUri(bytes: ByteArray, left: Int, top: Int, right: Int, bottom: Int, maxDim: Int): String? = runCatching {
    if (maxDim <= 0) return@runCatching null
    val image = Image.makeFromEncoded(bytes)
    try {
        val l = left.coerceIn(0, image.width)
        val t = top.coerceIn(0, image.height)
        val r = right.coerceIn(0, image.width)
        val b = bottom.coerceIn(0, image.height)
        if (r <= l || b <= t) return@runCatching null
        val scale = minOf(1.0, maxDim.toDouble() / maxOf(r - l, b - t))
        val width = ((r - l) * scale).toInt().coerceAtLeast(1)
        val height = ((b - t) * scale).toInt().coerceAtLeast(1)
        if (!validDimensions(width, height)) return@runCatching null
        val surface = Surface.makeRasterN32Premul(width, height)
        try {
            surface.canvas.clear(Color.WHITE)
            surface.canvas.drawImageRect(image, Rect.makeLTRB(l.toFloat(), t.toFloat(), r.toFloat(), b.toFloat()), Rect.makeWH(width.toFloat(), height.toFloat()))
            val cropped = surface.makeImageSnapshot()
            try {
                val data = cropped.encodeToData(EncodedImageFormat.JPEG, 88) ?: return@runCatching null
                try { "data:image/jpeg;base64," + Base64.Default.encode(data.bytes) } finally { data.close() }
            } finally { cropped.close() }
        } finally { surface.close() }
    } finally { image.close() }
}.getOrNull()

actual fun imageMemoryCacheBudgetBytes(): Int =
    (NSProcessInfo.processInfo.physicalMemory / 32uL).coerceIn(16uL * 1024uL * 1024uL, 64uL * 1024uL * 1024uL).toInt()
