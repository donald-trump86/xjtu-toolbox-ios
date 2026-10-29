@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.xjtu.toolbox.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSThread
import platform.Foundation.NSURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UINavigationController
import platform.UIKit.UIScreen
import platform.UIKit.UITabBarController
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.UIKit.popoverPresentationController
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

internal fun onIosMain(block: () -> Unit) {
    if (NSThread.isMainThread) block() else dispatch_async(dispatch_get_main_queue(), block)
}

internal fun iosActiveWindow(): UIWindow? = UIApplication.sharedApplication.connectedScenes
    .filterIsInstance<UIWindowScene>()
    .flatMap { it.windows.filterIsInstance<UIWindow>() }
    .firstOrNull { it.isKeyWindow() }

internal fun iosTopViewController(): UIViewController? {
    var controller = iosActiveWindow()?.rootViewController ?: return null
    while (true) {
        controller = controller.presentedViewController
            ?: (controller as? UINavigationController)?.visibleViewController
            ?: (controller as? UITabBarController)?.selectedViewController
            ?: return controller
    }
}

@Composable
actual fun rememberPhoneDialer(): (String) -> Unit = remember {
    { phone: String ->
        onIosMain {
            if (!phone.matches(Regex("[+0-9*#() -]+"))) return@onIosMain
            val number = phone.filter { it.isDigit() || it in "+*#" }.replace("#", "%23")
            val url = NSURL.URLWithString("tel:$number") ?: return@onIosMain
            val app = UIApplication.sharedApplication
            if (app.canOpenURL(url)) app.openURL(url, emptyMap<Any?, Any?>(), null)
            else showBriefMessage("当前设备无法拨打电话")
        }
    }
}

@Composable
actual fun rememberShareLink(): (title: String, url: String) -> Unit = remember {
    { title: String, url: String ->
        onIosMain {
            val controller = iosTopViewController() ?: return@onIosMain
            val parsed = NSURL.URLWithString(url)?.takeIf { it.scheme in setOf("https", "http") }
                ?: return@onIosMain
            val share = UIActivityViewController(listOf(title, parsed), null)
            share.popoverPresentationController?.apply {
                sourceView = controller.view
                sourceRect = controller.view.bounds.useContents {
                    CGRectMake(size.width / 2.0, size.height / 2.0, 1.0, 1.0)
                }
            }
            controller.presentViewController(share, true, null)
        }
    }
}

private var screenOnRequests = 0
private var previousIdleTimerDisabled = false

@Composable
actual fun KeepScreenOn(enabled: Boolean) {
    DisposableEffect(enabled) {
        if (enabled) onIosMain {
            val app = UIApplication.sharedApplication
            if (screenOnRequests++ == 0) previousIdleTimerDisabled = app.idleTimerDisabled
            app.idleTimerDisabled = true
        }
        onDispose {
            if (enabled) onIosMain {
                if (--screenOnRequests == 0) UIApplication.sharedApplication.idleTimerDisabled = previousIdleTimerDisabled
            }
        }
    }
}

@Composable
actual fun smallestScreenWidthDp(): Int = UIScreen.mainScreen.bounds.useContents {
    minOf(size.width, size.height).toInt()
}
