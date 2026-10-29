package com.xjtu.toolbox.platform

/** Ktor's Darwin errors retain NSURLErrorDomain and its numeric code in their cause. */
actual fun classifyNetworkFailure(e: Throwable): NetworkFailure? {
    var cause: Throwable? = e
    repeat(8) {
        val current = cause ?: return null
        val code = current.message?.let { nsUrlErrorCode.find(it)?.groupValues?.get(1)?.toIntOrNull() }
        when (code) {
            -1003, -1006 -> return NetworkFailure.NO_HOST
            -1001 -> return NetworkFailure.TIMEOUT
            -1004 -> return NetworkFailure.REFUSED
            -1200, -1201, -1202, -1203, -1204, -1205, -1206 -> return NetworkFailure.TLS
            -1005, -1009, -1018, -1019, -1020 -> return NetworkFailure.IO
        }
        when (current::class.simpleName) {
            "UnknownHostException", "UnresolvedAddressException" -> return NetworkFailure.NO_HOST
            "SocketTimeoutException", "ConnectTimeoutException", "HttpRequestTimeoutException" -> return NetworkFailure.TIMEOUT
            "ConnectException" -> return NetworkFailure.REFUSED
            "SSLException", "SSLHandshakeException" -> return NetworkFailure.TLS
            "DarwinHttpRequestException", "IOException", "SocketException" -> if (code == null) return NetworkFailure.IO
        }
        cause = current.cause.takeUnless { it === current }
    }
    return null
}

private val nsUrlErrorCode = Regex("NSURLErrorDomain[^\\n]*?Code=(-?\\d+)")
