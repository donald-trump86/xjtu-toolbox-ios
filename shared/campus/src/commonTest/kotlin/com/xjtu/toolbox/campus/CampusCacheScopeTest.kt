package com.xjtu.toolbox.campus

import kotlin.test.*

class CampusCacheScopeTest {
    @Test fun logoutClearRejectsLateExamCustomAndChangeWrites() {
        val backing = mutableMapOf<String, TestCampusStore>()
        val scope = CampusCacheScope { backing.getOrPut(it) { TestCampusStore() } }
        val exam = scope.store("schedule:A")
        val custom = scope.store("custom:A")
        val changes = scope.store("changes:A")
        exam.putString("exams", "old-exams")
        custom.putString("courses", "old-custom")
        changes.putStringSet("snapshot", setOf("old-change"))
        scope.revoke()
        scope.clear()
        // These represent synchronous parsing that finishes after its coroutine was cancelled.
        exam.putString("exams", "late-exams")
        custom.putString("courses", "late-custom")
        changes.putStringSet("snapshot", setOf("late-change"))
        assertNull(backing.getValue("schedule:A").getString("exams"))
        assertNull(backing.getValue("custom:A").getString("courses"))
        assertNull(backing.getValue("changes:A").getStringSet("snapshot"))
        assertFalse(exam.contains("exams"))
    }

    @Test fun ordinaryCloseKeepsOfflineDataButOldScopeCannotReadOrWrite() {
        val backing = TestCampusStore()
        val first = CampusCacheScope { backing }
        val stale = first.store("schedule:A")
        stale.putInt("week", 7)
        first.revoke()
        assertEquals(-1, stale.getInt("week", -1))
        stale.putInt("week", 99)
        val restored = CampusCacheScope { backing }
        assertEquals(7, restored.store("schedule:A").getInt("week", -1))
        restored.clear()
        assertNull(backing.getString("week"))
    }

    @Test fun clearingOneAccountLeavesOtherAccountsUntouched() {
        val backing = mutableMapOf<String, TestCampusStore>()
        val factory = { name: String -> backing.getOrPut(name) { TestCampusStore() } }
        val first = CampusCacheScope(factory)
        val second = CampusCacheScope(factory)
        first.store("schedule:A").putString("courses", "A")
        second.store("schedule:B").putString("courses", "B")
        first.clear()
        assertNull(backing.getValue("schedule:A").getString("courses"))
        assertEquals("B", second.store("schedule:B").getString("courses"))
    }
}
