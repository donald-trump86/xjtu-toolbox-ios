package com.xjtu.toolbox.shell

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.ui.unit.dp
import com.xjtu.toolbox.nav.AppRoute
import com.xjtu.toolbox.ui.WindowSize
import com.xjtu.toolbox.ui.glass.GLASS_BAR_HEIGHT
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 壳模型（[ShellTab] / [ShellTabs] / [ShellNavState] / [ShellLayout] / [ShellLaunchLayer]）。
 *
 * 这些是共享壳里**没有 Compose 就没有别的办法验证**的那部分：形态判据、底栏占位、
 * 角标规则、弹层优先级。搬进 `:core` 之后它们第一次有了单测 —— 在此之前这些数字只在
 * `:app` 的 `MainScreen` 里算过一遍，谁也没有钉住它们。
 */
class ShellModelTest {

    private fun tab(id: String, badge: Boolean = false) = ShellTab(
        id = id,
        label = id,
        icon = Icons.Filled.Home,
        target = ShellTarget.Custom(id),
        showsAccountBadge = badge,
    )

    // 与 `:app` 的 BottomTab 同序：首页 / 日程 / 屁岱 / 学辅 / 我的（角标在最后那一格）
    private val tabs = ShellTabs(
        listOf(
            tab("HOME"),
            tab("COURSES"),
            tab("PIDAI"),
            tab("TOOLS"),
            tab("PROFILE", badge = true),
        ),
    )

    // ── tab 集合语义 ──

    @Test
    fun `集合按 id 反查并保住顺序`() {
        assertEquals(listOf("HOME", "COURSES", "PIDAI", "TOOLS", "PROFILE"), tabs.ids)
        assertEquals(5, tabs.size)
        assertEquals("HOME", tabs.first.id)
        assertEquals(2, tabs.indexOf("PIDAI"))
        assertEquals("TOOLS", tabs.byId("TOOLS")?.id)
        assertEquals("TOOLS", tabs[3].id)
    }

    @Test
    fun `认不出的 id 返回 null 与负序号（端上自己决定退到哪一格）`() {
        assertNull(tabs.byId("PIDAI_V2"))
        assertNull(tabs.byId(null))
        assertEquals(-1, tabs.indexOf("PIDAI_V2"))
    }

    @Test
    fun `空集合与重复 id 都是构造错误`() {
        assertFailsWith<IllegalArgumentException> { ShellTabs(emptyList()) }
        assertFailsWith<IllegalArgumentException> { ShellTabs(listOf(tab("A"), tab("A"))) }
    }

    // ── 选中态 ──

    @Test
    fun `选中的 id 不在集合里就退到第一格`() {
        val nav = ShellNavState(tabs = tabs, selectedId = "GONE", onSelect = {})
        assertEquals("HOME", nav.selected.id)
        val known = ShellNavState(tabs = tabs, selectedId = "TOOLS", onSelect = {})
        assertEquals("TOOLS", known.selected.id)
    }

    @Test
    fun `点了某一格回调拿到的就是那一格`() {
        var picked: ShellTab? = null
        val nav = ShellNavState(tabs = tabs, selectedId = "HOME", onSelect = { picked = it })
        nav.onSelect(tabs.byId("PROFILE")!!)
        assertEquals("PROFILE", picked?.id)
    }

    // ── 账号角标（多账号数字 / 未登录红点 / 单账号不画）──

    @Test
    fun `角标内容按账号数与登录态分档`() {
        assertEquals(3, ShellAccountBadge.of(accountCount = 3, isLoggedIn = true)?.count)
        assertNull(ShellAccountBadge.of(accountCount = 1, isLoggedIn = false)?.count)
        assertEquals(2, ShellAccountBadge.of(accountCount = 2, isLoggedIn = false)?.count)
        assertNull(ShellAccountBadge.of(accountCount = 1, isLoggedIn = true))
        assertNull(ShellAccountBadge.of(accountCount = 0, isLoggedIn = true))
    }

    @Test
    fun `只有标了角标的那一格才画`() {
        val badge = ShellAccountBadge.of(accountCount = 2, isLoggedIn = true)
        val nav = ShellNavState(tabs = tabs, selectedId = "HOME", onSelect = {}, accountBadge = badge)
        // 没有角标内容时一格都不画
        val bare = ShellNavState(tabs = tabs, selectedId = "HOME", onSelect = {})
        assertNull(bare.badge(tabs.byId("PROFILE")!!))
        // 有角标内容时只有那一格画（其余四格仍然不画）
        assertNotNull(nav.badge(tabs.byId("PROFILE")!!))
        tabs.tabs.filter { it.id != "PROFILE" }.forEach { assertNull(nav.badge(it)) }
    }

    // ── 形态学（屏形状 × 界面风格）──

    @Test
    fun `形态判据`() {
        // 宽屏一律侧栏（界面风格是不是玻璃都一样）
        assertEquals(ShellForm.RAIL, shellForm(isWide = true, windowSize = WindowSize.Compact, glass = true))
        assertEquals(ShellForm.RAIL, shellForm(isWide = true, windowSize = WindowSize.Expanded, glass = false))
        // 手机竖屏 + 玻璃 = 玻璃胶囊；平板竖屏 + 玻璃 = miuix 悬浮胶囊
        assertEquals(ShellForm.GLASS_BAR, shellForm(isWide = false, windowSize = WindowSize.Compact, glass = true))
        assertEquals(ShellForm.FLOATING_PILL, shellForm(isWide = false, windowSize = WindowSize.Medium, glass = true))
        // 经典风格：任何竖屏都是 miuix 经典底栏
        assertEquals(ShellForm.CLASSIC_BAR, shellForm(isWide = false, windowSize = WindowSize.Compact, glass = false))
        assertEquals(ShellForm.CLASSIC_BAR, shellForm(isWide = false, windowSize = WindowSize.Expanded, glass = false))
    }

    @Test
    fun `底栏占位与槽位补偿`() {
        val glassBar = ShellLayout(ShellForm.GLASS_BAR, glass = true, bottomInset = 24.dp)
        assertTrue(glassBar.glassBarPresent)
        // 58 + 有导航条的空隙 8 + 24（与搬迁前 MainScreen 里那一行逐字一致）
        assertEquals(GLASS_BAR_HEIGHT + 8.dp + 24.dp, glassBar.bottomBarReserve)
        // 塞进 floatingToolbar 槽位时要扣掉 miuix 私有的 4dp
        assertEquals(28.dp, glassBar.glassBarSlotBottomPadding)
        assertEquals(0.dp, glassBar.bottomBarSlotOffsetY)

        // 手势条（量不到导航条）：空隙变成 20dp
        val noInset = ShellLayout(ShellForm.GLASS_BAR, glass = true, bottomInset = 0.dp)
        assertEquals(GLASS_BAR_HEIGHT + 20.dp, noInset.bottomBarReserve)
        assertEquals(16.dp, noInset.glassBarSlotBottomPadding)

        // 悬浮胶囊：本体 52 + 26 + inset（没有导航条时 36）
        val pill = ShellLayout(ShellForm.FLOATING_PILL, glass = true, bottomInset = 24.dp)
        assertEquals(FLOATING_BAR_HEIGHT + 26.dp + 24.dp, pill.bottomBarReserve)
        assertEquals(4.dp, pill.bottomBarSlotOffsetY)
        assertEquals(FLOATING_BAR_HEIGHT + 36.dp, ShellLayout(ShellForm.FLOATING_PILL, true, 0.dp).bottomBarReserve)

        // 经典底栏由 Scaffold 的 bottomBar 让位、侧栏不吃浮空占位 ⇒ 都是 0
        assertEquals(0.dp, ShellLayout(ShellForm.CLASSIC_BAR, false, 24.dp).bottomBarReserve)
        assertEquals(0.dp, ShellLayout(ShellForm.RAIL, true, 24.dp).bottomBarReserve)
    }

    // ── 冷启动弹层的优先级 ──

    @Test
    fun `冷启动同一刻只弹一层，公告优先`() {
        assertNull(ShellLaunchLayer.layer(bulletin = false, update = false, changelog = false))
        assertEquals(ShellLaunchLayer.CHANGELOG, ShellLaunchLayer.layer(false, false, true))
        assertEquals(ShellLaunchLayer.UPDATE, ShellLaunchLayer.layer(false, true, true))
        assertEquals(ShellLaunchLayer.BULLETIN, ShellLaunchLayer.layer(true, true, true))
    }

    // ── 导航模型 ──

    @Test
    fun `导航模型三档的形状`() {
        assertEquals(AppRoute.Main, (ShellTarget.App(AppRoute.Main) as ShellTarget.App).route)
        assertEquals(ShellTarget.AllPages, ShellTarget.AllPages)
        assertEquals(ShellTarget.Custom("zyxf"), ShellTarget.Custom("zyxf"))
        // 端自己的页不会与共享路由混到一起
        assertTrue(ShellTarget.Custom("profile") != ShellTarget.App(AppRoute.Main))
    }
}
