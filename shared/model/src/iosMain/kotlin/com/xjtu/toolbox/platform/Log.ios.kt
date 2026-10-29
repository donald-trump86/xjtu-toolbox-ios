@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.xjtu.toolbox.platform

import platform.Foundation.NSLog

actual fun platformLog(level: LogLevel, tag: String, message: String, error: Throwable?) {
    // Callers may include credentials in every free-text field, including the tag.
    NSLog("Toolbox event: ${level.name}")
}
