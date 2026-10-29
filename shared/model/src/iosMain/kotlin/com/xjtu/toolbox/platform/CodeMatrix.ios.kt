package com.xjtu.toolbox.platform

actual fun qrCodeModules(text: String): CodeMatrix? = QrCodec.encode(text)
actual fun code128Modules(text: String): CodeMatrix? = Code128Codec.encode(text)
