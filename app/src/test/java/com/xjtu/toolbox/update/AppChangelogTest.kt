package com.xjtu.toolbox.update

import org.junit.Assert.assertEquals
import org.junit.Test

class AppChangelogTest {

    private val entries = listOf("5.2.1", "5.2.0", "5.1.1", "5.1.0").map { it to VersionChangelog(emptyList()) }

    private fun since(lastSeen: String?, current: String) = AppChangelog.since(lastSeen, current, entries).map { it.first }

    @Test
    fun `升到开发版不弹：它的条目还没写全`() {
        assertEquals(emptyList<String>(), since("5.1.1", "5.2.0-dev.3"))
        assertEquals(emptyList<String>(), since("5.2.0-dev.3", "5.2.0-dev.5"))
    }

    @Test
    fun `开发版升到正式版弹完整的那条`() {
        assertEquals(listOf("5.2.0"), since("5.2.0-dev.12", "5.2.0"))
    }

    @Test
    fun `开发版跨到下一版开发版，补上中间发过的正式版`() {
        assertEquals(listOf("5.2.0"), since("5.2.0-dev.9", "5.2.1-dev.2"))
    }

    @Test
    fun `跨多个正式版堆叠展示，降级不弹`() {
        assertEquals(listOf("5.2.1", "5.2.0"), since("5.1.1", "5.2.1"))
        assertEquals(emptyList<String>(), since("5.2.1", "5.2.0"))
    }

    @Test
    fun `全新安装只给当前那条`() {
        assertEquals(listOf("5.2.0"), since(null, "5.2.0-dev.1"))
    }
}
