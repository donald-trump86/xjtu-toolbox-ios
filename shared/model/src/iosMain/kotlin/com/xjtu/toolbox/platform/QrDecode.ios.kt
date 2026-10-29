package com.xjtu.toolbox.platform

import com.xjtu.toolbox.platform.qr.QrReader

actual fun qrDecodeArgb(pixels: IntArray, width: Int, height: Int): String? {
    if (width <= 0 || height <= 0 || width.toLong() * height > pixels.size) return null
    return QrReader.decodeArgb(pixels, width, height)
}
