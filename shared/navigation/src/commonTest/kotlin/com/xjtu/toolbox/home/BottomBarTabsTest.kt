package com.xjtu.toolbox.home

import com.xjtu.toolbox.auth.AccountType
import com.xjtu.toolbox.nav.AppRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BottomBarTabsTest {
    private val ug = AccountType.UNDERGRADUATE

    @Test
    fun `没设置过就是原来的五格`() {
        assertEquals(listOf("HOME", "COURSES", "PIDAI", "TOOLS", "PROFILE"), BottomBarTabs.barIds(null, ug))
    }

    @Test
    fun `候选是首页上的一级功能，付款码这种弹层除外`() {
        val routes = BottomBarTabs.candidates(ug).map { it.route }
        assertEquals(AppServices.homeFor(ug).map { it.route } - AppRoute.PaymentCode, routes)
        assertTrue(AppRoute.Library in routes && AppRoute.Games in routes)
        assertFalse(AppRoute.Settings in routes, "设置不在首页上")
    }

    @Test
    fun `中间去重、丢未知和非候选、最多三格，其余功能用路由 id`() {
        assertEquals(
            listOf("HOME", "empty_room", "COURSES", "PROFILE"),
            BottomBarTabs.barIds("empty_room, schedule,empty_room,settings,payment_code,???", ug),
        )
        assertEquals(
            listOf(AppRoute.Notification, AppRoute.Agent, AppRoute.Library),
            BottomBarTabs.middleOf("notification,agent,library,zyxf", ug),
        )
        assertEquals(BottomBarTabs.DEFAULT_MIDDLE, BottomBarTabs.middleOf(",,", ug))
    }

    @Test
    fun `默认启动页不在底栏上就回首页`() {
        val bar = BottomBarTabs.barIds("schedule,notification", ug)
        assertEquals("HOME", BottomBarTabs.defaultTab("PIDAI", bar))
        assertEquals("notification", BottomBarTabs.defaultTab("notification", bar))
    }
}
