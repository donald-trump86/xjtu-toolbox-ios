package com.xjtu.toolbox.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.xjtu.toolbox.schedule.CourseColors
import com.xjtu.toolbox.settings.SettingsStore
import com.xjtu.toolbox.ui.components.AppFilterChip
import com.xjtu.toolbox.ui.components.LiquidBackground
import kotlin.random.Random
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.ColorPalette
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

internal data class HeroAppearance(
    val palette: String = "auto",
    val colorHex: String = "#2F7CFF",
    val motion: String = "auto",
    val seed: Int = -1,
    val background: String = "liquid",
) {
    val solidColor: Color get() = CourseColors.parseHex(colorHex) ?: Color(0xFF2F7CFF)
    val contentColor: Color get() = if (background == "solid" && solidColor.luminance() > 0.179f) Color.Black else Color.White

    fun save(store: SettingsStore) {
        store.heroBackground = background
        store.heroPalette = palette
        store.heroColorHex = colorHex
        store.heroMotion = motion
        store.heroSeed = seed
    }

    companion object {
        fun read(store: SettingsStore) = HeroAppearance(
            store.heroPalette, store.heroColorHex, store.heroMotion, store.heroSeed, store.heroBackground,
        )
    }
}

/** 草稿只作用于弹窗预览，确定后落盘；返回、点外面和取消均丢弃草稿。 */
@Composable
internal fun HeroAppearanceDialog(
    current: HeroAppearance,
    nextUp: HomeNextUp?,
    examSoon: Boolean,
    onDismiss: () -> Unit,
    onApply: (HeroAppearance) -> Unit,
) {
    var draft by remember { mutableStateOf(current) }
    val mood = rememberLiquidMood(nextUp, examSoon, draft)
    WindowDialog(
        show = true,
        title = "首页卡片外观",
        onDismissRequest = onDismiss,
    ) {
        Column {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.fillMaxWidth().height(104.dp).clip(RoundedCornerShape(20.dp))) {
                    if (draft.background == "solid") {
                        Box(Modifier.matchParentSize().background(draft.solidColor))
                    } else {
                        LiquidBackground(
                            colors = mood.colors, seed = mood.seed, speed = mood.speed,
                            crease = mood.crease, animated = mood.animated,
                            modifier = Modifier.matchParentSize(),
                        )
                        Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.18f)))
                    }
                    Column(Modifier.align(Alignment.CenterStart).padding(18.dp)) {
                        Text("今日安排", color = draft.contentColor, style = MiuixTheme.textStyles.body1)
                        Text("预览配色与流动效果", color = draft.contentColor.copy(alpha = 0.8f),
                            style = MiuixTheme.textStyles.footnote1)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppFilterChip(selected = draft.background == "liquid", onClick = { draft = draft.copy(background = "liquid") }, label = "流动背景")
                    AppFilterChip(selected = draft.background == "solid", onClick = { draft = draft.copy(background = "solid") }, label = "纯色背景")
                }
                Text("颜色", style = MiuixTheme.textStyles.body2)
                if (draft.background == "liquid") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            "auto" to "自动", "midnight" to "午夜蓝", "orchid" to "兰紫",
                            "blossom" to "樱粉", "lagoon" to "湖绿", "honey" to "蜜橙", "custom" to "自选色",
                        ).forEach { (key, label) ->
                            AppFilterChip(selected = draft.palette == key, onClick = { draft = draft.copy(palette = key) }, label = label)
                        }
                    }
                }
                if (draft.background == "solid" || draft.palette == "custom") {
                    ColorPalette(
                        color = CourseColors.parseHex(draft.colorHex) ?: Color(0xFF2F7CFF),
                        onColorChanged = { draft = draft.copy(colorHex = CourseColors.toHex(it)) },
                    )
                }
                if (draft.background == "liquid") {
                    Text("流动方式", style = MiuixTheme.textStyles.body2)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            "auto" to "自动", "gentle" to "轻柔", "flowing" to "舒缓", "lively" to "活跃", "still" to "静止",
                        ).forEach { (key, label) ->
                            AppFilterChip(selected = draft.motion == key, onClick = { draft = draft.copy(motion = key) }, label = label)
                        }
                    }
                    Text("纹理形状", style = MiuixTheme.textStyles.body2)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppFilterChip(selected = draft.seed < 0, onClick = { draft = draft.copy(seed = -1) }, label = "每天自动换纹理")
                        AppFilterChip(
                            selected = draft.seed >= 0,
                            onClick = {
                                // 每次都换一种，并固定到下一次用户主动修改。
                                draft = draft.copy(seed = (draft.seed + Random.nextInt(1, 10000)) % 10000)
                            },
                            label = "随机纹理并固定",
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(text = "取消", onClick = onDismiss, modifier = Modifier.weight(1f))
                TextButton(
                    text = "确定", onClick = { onApply(draft) }, modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
}
