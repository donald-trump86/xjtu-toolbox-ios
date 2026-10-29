@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.xjtu.toolbox.platform

import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.UIKit.NSTextAlignmentCenter
import platform.UIKit.UIApplication
import platform.UIKit.UIColor
import platform.UIKit.UIFont
import platform.UIKit.UILabel
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.UIKit.accessibilityLabel
import platform.darwin.NSEC_PER_SEC
import platform.darwin.dispatch_after
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_time
import platform.darwin.DISPATCH_TIME_NOW

actual fun showBriefMessage(message: String) {
    if (message.isBlank()) return
    dispatch_async(dispatch_get_main_queue()) {
        val window = UIApplication.sharedApplication.connectedScenes
            .filterIsInstance<UIWindowScene>()
            .flatMap { it.windows.filterIsInstance<UIWindow>() }
            .firstOrNull { it.isKeyWindow() } ?: return@dispatch_async
        val (width, height) = window.bounds.useContents { size.width to size.height }
        val label = UILabel(frame = CGRectMake(24.0, height - 150.0, (width - 48.0).coerceAtLeast(1.0), 64.0)).apply {
            text = message
            numberOfLines = 3
            textAlignment = NSTextAlignmentCenter
            font = UIFont.systemFontOfSize(14.0)
            textColor = UIColor.whiteColor
            backgroundColor = UIColor.blackColor.colorWithAlphaComponent(0.85)
            layer.cornerRadius = 10.0
            clipsToBounds = true
            accessibilityLabel = message
        }
        window.addSubview(label)
        dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (3uL * NSEC_PER_SEC).toLong()), dispatch_get_main_queue()) {
            label.removeFromSuperview()
        }
    }
}
