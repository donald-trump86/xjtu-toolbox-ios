package com.xjtu.toolbox.platform

actual fun deflateBytes(raw: ByteArray): ByteArray? = ZlibCodec.deflate(raw)
actual fun inflateBytes(bytes: ByteArray, maxBytes: Int): ByteArray? = ZlibCodec.inflate(bytes, maxBytes)
actual val deflateAvailable: Boolean = true
