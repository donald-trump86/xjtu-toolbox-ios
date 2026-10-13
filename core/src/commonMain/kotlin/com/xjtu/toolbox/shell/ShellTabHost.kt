package com.xjtu.toolbox.shell

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.xjtu.toolbox.ui.components.LocalPageVisible
import kotlinx.coroutines.delay

/**
 * 当前页那一层：**选中过的 tab 才进组合，切走的留在组合里只是透明**（切回来不重新取数）；
 * 切换带横移 + 缩放。
 *
 * 这一段原本在 `:app` 的 `main/MainScreen.kt` 里（`TabHost`），行为逐字未改：同一个
 * 「进过组合就留着」「280ms 后记住上一次是哪个 tab」「隐藏的格子冻结测量约束」。
 * 改的只有「认出是哪一格」的判据 —— 从 `BottomTab.ordinal` 换成 [ShellTabs.indexOf]（值相同）。
 *
 * @param modifier 挂在整层上的修饰（玻璃风格下是内容区的采样层）
 * @param tabModifier 每个格子自己的修饰（玻璃风格下按顶栏折叠进度补留白）
 * @param content 画某一格。**画什么由端上注入** —— 每格要的宿主面不同（`:app` 那五个分支就是这种 provider）。
 */
@Composable
fun ShellTabHost(
    nav: ShellNavState,
    modifier: Modifier = Modifier,
    tabModifier: () -> Modifier = { Modifier },
    content: @Composable (ShellTab) -> Unit,
) {
    val selectedId = nav.selected.id
    var composedIds by remember { mutableStateOf(setOf(selectedId)) }
    LaunchedEffect(selectedId) { composedIds = composedIds + selectedId }

    val selectedIndex = nav.tabs.indexOf(selectedId)
    var previousTabIndex by rememberSaveable { mutableIntStateOf(selectedIndex) }
    val tabSwitchDirection = selectedIndex.compareTo(previousTabIndex)
    LaunchedEffect(selectedId) {
        delay(280)
        previousTabIndex = selectedIndex
    }

    val tabSlideDistance = with(LocalDensity.current) { 28.dp.toPx() }
    Box(modifier.fillMaxSize()) {
        // 别在 key {} 里 return@key：Kotlin 2.4.20 会生成 R8 无法转 dex 的方法名
        nav.tabs.tabs.filter { it.id in composedIds }.forEach { tab ->
            key(tab) {
                val isActive = selectedId == tab.id
                val tabAlpha by animateFloatAsState(
                    targetValue = if (isActive) 1f else 0f,
                    animationSpec = tween(if (isActive) 240 else 180),
                    label = "tabAlpha",
                )
                val tabOffset by animateFloatAsState(
                    targetValue = when {
                        isActive || tabSwitchDirection == 0 -> 0f
                        nav.tabs.indexOf(tab.id) < selectedIndex -> -tabSlideDistance
                        else -> tabSlideDistance
                    },
                    animationSpec = tween(260),
                    label = "tabOffset",
                )
                val tabScale by animateFloatAsState(
                    targetValue = if (isActive) 1f else 0.985f,
                    animationSpec = tween(260),
                    label = "tabScale",
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        // 隐藏的 tab 冻结测量约束，侧栏动画时不跟着逐帧重排
                        .freezeLayoutWhile { !isActive && tabAlpha == 0f }
                        .then(tabModifier())
                        .zIndex(if (isActive) 1f else 0f)
                        .graphicsLayer {
                            alpha = tabAlpha
                            translationX = tabOffset
                            scaleX = tabScale
                            scaleY = tabScale
                        }
                        .pointerInput(isActive) {
                            if (!isActive) {
                                awaitPointerEventScope {
                                    while (true) {
                                        awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                                    }
                                }
                            }
                        },
                ) {
                    // 让隐藏 tab 里的常驻动画停下
                    CompositionLocalProvider(LocalPageVisible provides isActive) {
                        content(tab)
                    }
                }
            }
        }
    }
}

/** [frozen] 为真时沿用上一次的测量约束，子树不跟着父布局尺寸动画重排。 */
@Composable
private fun Modifier.freezeLayoutWhile(frozen: () -> Boolean): Modifier {
    val last = remember { arrayOfNulls<Constraints>(1) }
    return this.layout { measurable, constraints ->
        val use = if (frozen()) last[0] ?: constraints else constraints.also { last[0] = it }
        val placeable = measurable.measure(use)
        layout(constraints.constrainWidth(placeable.width), constraints.constrainHeight(placeable.height)) {
            placeable.place(0, 0)
        }
    }
}
