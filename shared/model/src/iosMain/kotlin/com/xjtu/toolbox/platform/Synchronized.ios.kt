package com.xjtu.toolbox.platform

import platform.Foundation.NSRecursiveLock

// A shared recursive monitor also protects plain Kotlin lock tokens across Native threads.
private val monitor = NSRecursiveLock()

actual fun <T> synchronizedBlock(lock: Any, block: () -> T): T {
    monitor.lock()
    return try {
        block()
    } finally {
        monitor.unlock()
    }
}
