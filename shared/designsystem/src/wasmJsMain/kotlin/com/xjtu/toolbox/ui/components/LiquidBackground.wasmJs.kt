package com.xjtu.toolbox.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** 浏览器侧的降级：静态 135° 渐变。见 commonMain 的 [FallbackLiquidBackground]。 */
@Composable
internal actual fun PlatformLiquidBackground(
    modifier: Modifier,
    colors: LiquidColors,
    seed: Float,
    speed: Float,
    crease: Float,
    animated: Boolean,
) {
    FallbackLiquidBackground(modifier = modifier, colors = colors)
}
