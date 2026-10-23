package com.xjtu.toolbox.home

import com.xjtu.toolbox.auth.AccountType
import com.xjtu.toolbox.nav.AppRoute
import com.xjtu.toolbox.nav.appRouteOf
import com.xjtu.toolbox.settings.SettingsStore

/**
 * 底栏由哪几格组成。首页恒在最左（返回键回到它），我的恒在最右（设置与账号的入口），
 * 中间 [MIN_MIDDLE]–[MAX_MIDDLE] 格从首页上的一级功能里挑（[candidates]）。
 *
 * 存盘的是中间那几格的路由 id。格子自己的 id（ShellTab.id、默认启动页）：首页、我的，以及
 * 日程、屁岱、学辅沿用早已存盘的名字（[SettingsStore.TAB_HOME] 等），其余功能用路由 id。
 */
object BottomBarTabs {
    const val MIN_MIDDLE = 1
    const val MAX_MIDDLE = 3
    val DEFAULT_MIDDLE: List<AppRoute> = listOf(AppRoute.Schedule, AppRoute.Agent, AppRoute.Zyxf)

    /** 不能当底栏格的：付款码是盖在当前页上的弹层，没有自己的页面。 */
    private val EXCLUDED: Set<AppRoute> = setOf(AppRoute.PaymentCode)

    /** 能放进底栏的功能：首页上显示的一级功能，按首页目录的顺序。 */
    fun candidates(accountType: AccountType): List<AppService> =
        AppServices.homeFor(accountType).filter { it.route !in EXCLUDED }

    /** 存盘串 → 中间几格：丢掉认不得的、不在候选里的和重复的，超出的截掉，一格不剩时回默认。 */
    fun middleOf(raw: String?, accountType: AccountType): List<AppRoute> {
        val allowed = candidates(accountType).map { it.route }.toSet()
        return raw.orEmpty().split(',').mapNotNull { appRouteOf(it.trim()) }
            .filter { it in allowed }.distinct().take(MAX_MIDDLE)
            .ifEmpty { DEFAULT_MIDDLE }
    }

    fun encode(middle: List<AppRoute>): String = middle.joinToString(",") { it.id }

    fun tabId(route: AppRoute): String = when (route) {
        AppRoute.Schedule -> SettingsStore.TAB_COURSES
        AppRoute.Agent -> SettingsStore.TAB_PIDAI
        AppRoute.Zyxf -> SettingsStore.TAB_TOOLS
        else -> route.id
    }

    /** 底栏从左到右全部格子的 id。 */
    fun barIds(raw: String?, accountType: AccountType): List<String> =
        listOf(SettingsStore.TAB_HOME) + middleOf(raw, accountType).map(::tabId) + SettingsStore.TAB_PROFILE

    /** 默认启动页不在底栏上时回首页。 */
    fun defaultTab(raw: String, barIds: List<String>): String = raw.takeIf { it in barIds } ?: SettingsStore.TAB_HOME

    /** 底栏格上的字：用首页目录的名字，学辅沿用底栏一直以来的短名。 */
    fun label(route: AppRoute): String = when (route) {
        AppRoute.Zyxf -> "学辅"
        else -> AppServices.all.firstOrNull { it.route == route }?.title ?: route.id
    }

    /** 格子 id 对应的字（设置里列默认启动页用）。 */
    fun labelOf(tabId: String): String = when (tabId) {
        SettingsStore.TAB_HOME -> "首页"
        SettingsStore.TAB_PROFILE -> "我的"
        SettingsStore.TAB_COURSES -> label(AppRoute.Schedule)
        SettingsStore.TAB_PIDAI -> label(AppRoute.Agent)
        SettingsStore.TAB_TOOLS -> label(AppRoute.Zyxf)
        else -> appRouteOf(tabId)?.let(::label) ?: tabId
    }
}
