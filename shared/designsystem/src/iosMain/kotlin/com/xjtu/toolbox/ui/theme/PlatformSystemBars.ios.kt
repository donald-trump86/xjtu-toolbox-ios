@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.xjtu.toolbox.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import com.xjtu.toolbox.platform.iosActiveWindow
import com.xjtu.toolbox.platform.onIosMain
import platform.UIKit.UIUserInterfaceStyle.UIUserInterfaceStyleDark
import platform.UIKit.UIUserInterfaceStyle.UIUserInterfaceStyleLight

@Composable
actual fun PlatformSystemBars(darkTheme: Boolean) {
    SideEffect {
        onIosMain {
            iosActiveWindow()?.apply {
                overrideUserInterfaceStyle = if (darkTheme) UIUserInterfaceStyleDark else UIUserInterfaceStyleLight
                rootViewController?.setNeedsStatusBarAppearanceUpdate()
            }
        }
    }
}
