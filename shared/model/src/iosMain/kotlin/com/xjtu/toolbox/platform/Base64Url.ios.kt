package com.xjtu.toolbox.platform

actual fun base64UrlEncode(bytes: ByteArray): String = UrlSafeBase64.encode(bytes)
actual fun base64UrlDecode(text: String): ByteArray? = UrlSafeBase64.decode(text)
actual val base64Available: Boolean = true
