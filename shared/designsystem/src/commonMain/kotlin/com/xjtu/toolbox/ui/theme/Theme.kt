package com.xjtu.toolbox.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.xjtu.toolbox.settings.SettingsStore
import com.xjtu.toolbox.ui.HapticsPrefs
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/**
 * @param darkModeOverride "system" | "light" | "dark" — 手动覆盖系统深色模式
 * @param dynamicColor 跟随系统壁纸 / 调色盘取色（Monet）
 */
@Composable
fun XJTUToolBoxTheme(
    darkModeOverride: String = "system",
    dynamicColor: Boolean = false,
    customColorHex: String = "",
    applySystemBars: Boolean = true,
    content: @Composable () -> Unit
) {
    val seed = remember(customColorHex) {
        SettingsStore.normalizeThemeColor(customColorHex).takeIf { it.isNotEmpty() }
            ?.removePrefix("#")?.toLong(16)?.let { Color(0xFF000000 or it) }
    }
    val monet = dynamicColor || seed != null
    val mode = if (monet) {
        when (darkModeOverride) {
            "light" -> ColorSchemeMode.MonetLight
            "dark" -> ColorSchemeMode.MonetDark
            else -> ColorSchemeMode.MonetSystem
        }
    } else {
        when (darkModeOverride) {
            "light" -> ColorSchemeMode.Light
            "dark" -> ColorSchemeMode.Dark
            else -> ColorSchemeMode.System
        }
    }
    val controller = remember(mode, seed) { ThemeController(colorSchemeMode = mode, keyColor = seed) }
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (darkModeOverride) {
        "light" -> false
        "dark" -> true
        else -> systemDark
    }
    if (applySystemBars) PlatformSystemBars(darkTheme)
    val platformHaptics = LocalHapticFeedback.current
    val gatedHaptics = remember(platformHaptics) { GatedHapticFeedback(platformHaptics) }
    CompositionLocalProvider(LocalIsDarkTheme provides darkTheme, LocalHapticFeedback provides gatedHaptics) {
        MiuixTheme(controller = controller) {
            val colors = if (monet) {
                remapMonetColorsToMiuix(MiuixTheme.colorScheme, darkTheme)
            } else {
                MiuixTheme.colorScheme
            }
            MiuixTheme(
                colors = if (darkTheme) colors else colors.copy(surface = LightPageBackground),
                content = content,
            )
        }
    }
}

/**
 * 系统触感先过设置里的「触感反馈」开关。miuix 的开关、滑条、下拉刷新、滚到底的回弹
 * 都直接调 LocalHapticFeedback，不经 [com.xjtu.toolbox.ui.rememberHaptics]，不拦就关不掉。
 */
private class GatedHapticFeedback(private val inner: HapticFeedback) : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
        if (HapticsPrefs.isEnabled()) inner.performHapticFeedback(hapticFeedbackType)
    }
}

/** 浅色模式的页面底色；卡片仍是 surfaceVariant（白）。 */
private val LightPageBackground = Color(0xFFF0F1F3)

/**
 * Monet / MD3 按同名角色灌进 MIUIX，会把「灰页 / 白卡片」对调。
 *
 * MIUIX 浅色默认：`surface` 是页面灰（`#F7F7F7`），`surfaceVariant` / `surfaceContainer`
 * 是卡片白。MD3 浅色正好相反：`surface` 近白，`surfaceContainer` 才是略深的灰底，
 * `surfaceVariant` 还带着壁纸色相。一对一映射后 TopAppBar（默认 `surface`）变纯白，
 * 下面的 Tab / 卡片（`surfaceVariant`）变成淡紫，顶栏看起来像被切开。
 *
 * 深色同样不用 MD3 的 `surfaceVariant`（tone 30，过亮），卡片改走 `surfaceContainer`。
 */
private fun remapMonetColorsToMiuix(colors: Colors, dark: Boolean): Colors {
    return if (dark) {
        colors.copy(
            surfaceVariant = colors.surfaceContainer,
        )
    } else {
        colors.copy(
            surface = colors.surfaceContainer,
            surfaceVariant = colors.surface,
            surfaceContainer = colors.surface,
        )
    }
}
