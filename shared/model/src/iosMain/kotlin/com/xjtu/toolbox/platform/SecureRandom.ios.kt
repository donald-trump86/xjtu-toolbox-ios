@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.xjtu.toolbox.platform

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Security.SecRandomCopyBytes
import platform.Security.errSecSuccess
import platform.Security.kSecRandomDefault

actual fun secureRandomInt(bound: Int): Int {
    require(bound > 0) { "Random bound must be positive" }
    val unsignedBound = bound.toUInt()
    val threshold = (0u - unsignedBound) % unsignedBound
    val bytes = ByteArray(4)
    while (true) {
        bytes.usePinned {
            check(SecRandomCopyBytes(kSecRandomDefault, 4uL, it.addressOf(0)) == errSecSuccess) {
                "Secure random generation failed"
            }
        }
        val value = (bytes[0].toUInt() and 255u) or
            ((bytes[1].toUInt() and 255u) shl 8) or
            ((bytes[2].toUInt() and 255u) shl 16) or
            ((bytes[3].toUInt() and 255u) shl 24)
        if (value >= threshold) return (value % unsignedBound).toInt()
    }
}
