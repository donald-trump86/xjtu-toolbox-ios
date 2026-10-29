@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.xjtu.toolbox.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.xjtu.toolbox.platform.onIosMain
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle.UIImpactFeedbackStyleLight
import platform.UIKit.UIImpactFeedbackStyle.UIImpactFeedbackStyleSoft
import platform.UIKit.UINotificationFeedbackGenerator
import platform.UIKit.UINotificationFeedbackType.UINotificationFeedbackTypeError
import platform.UIKit.UINotificationFeedbackType.UINotificationFeedbackTypeSuccess

@Composable
actual fun rememberHaptics(): HapticsController = remember { IosHapticsController() }

private class IosHapticsController : HapticsController {
    private val tickGenerator = UIImpactFeedbackGenerator(UIImpactFeedbackStyleLight)
    private val lowGenerator = UIImpactFeedbackGenerator(UIImpactFeedbackStyleSoft)
    private val notificationGenerator = UINotificationFeedbackGenerator()

    private fun play(block: () -> Unit) {
        if (HapticsPrefs.isEnabled()) onIosMain(block)
    }

    override fun tick() = play {
        tickGenerator.prepare()
        tickGenerator.impactOccurred()
    }
    override fun lowTick() = play {
        lowGenerator.prepare()
        lowGenerator.impactOccurred()
    }
    override fun success() = play {
        notificationGenerator.prepare()
        notificationGenerator.notificationOccurred(UINotificationFeedbackTypeSuccess)
    }
    override fun error() = play {
        notificationGenerator.prepare()
        notificationGenerator.notificationOccurred(UINotificationFeedbackTypeError)
    }
}
