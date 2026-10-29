package com.xjtu.toolbox.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

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
