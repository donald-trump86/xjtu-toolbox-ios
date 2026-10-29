@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.xjtu.toolbox.core.net

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.engine.darwin.KtorNSURLSessionDelegate
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration

/** Ktor owns per-client cookies; NSURLSession must not share or persist them. */
actual fun toolboxEngine(): HttpClientEngine = Darwin.create {
    val configuration = NSURLSessionConfiguration.ephemeralSessionConfiguration
    configuration.HTTPCookieStorage = null
    configuration.HTTPShouldSetCookies = false
    configuration.URLCache = null
    val delegate = KtorNSURLSessionDelegate()
    val session = NSURLSession.sessionWithConfiguration(configuration, delegate, delegateQueue = null)
    usePreconfiguredSession(session, delegate)
    // The default delegate retains NSURLSession's certificate validation.
}
