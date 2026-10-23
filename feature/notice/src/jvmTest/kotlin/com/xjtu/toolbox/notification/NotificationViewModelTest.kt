package com.xjtu.toolbox.notification

import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.ViewModelStore
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()

    @BeforeTest fun setup() = Dispatchers.setMain(dispatcher)
    @AfterTest fun teardown() {
        store.clear()
        Dispatchers.resetMain()
    }

    @Test fun selectedSourcesDriveBothTheFeedAndSearch() = runTest(dispatcher) {
        val source = FakeNotices()
        val vm = NotificationViewModel(source).also { store.put("notices", it) }
        runCurrent()
        assertEquals(listOf(setOf(NotificationSource.JWC)), source.feedCalls)

        val selected = setOf(NotificationSource.GS, NotificationSource.EE)
        vm.selectSources(selected)
        Snapshot.sendApplyNotifications()
        runCurrent()
        assertEquals(selected, source.feedCalls.last())
        assertEquals(selected, vm.notifications.map { it.source }.toSet())

        vm.searchQuery = "培养"
        Snapshot.sendApplyNotifications()
        runCurrent()
        advanceTimeBy(501)
        runCurrent()
        assertEquals(selected to "培养", source.searchCalls.single())
    }

    @Test fun emptySelectionDoesNotReplaceTheCurrentFeed() = runTest(dispatcher) {
        val source = FakeNotices()
        val vm = NotificationViewModel(source).also { store.put("notices", it) }
        runCurrent()
        vm.selectSources(emptySet())
        Snapshot.sendApplyNotifications()
        runCurrent()
        assertEquals(setOf(NotificationSource.JWC), vm.selectedSources)
        assertEquals(1, source.feedCalls.size)
    }

    @Test fun failedSourceIsReportedInsteadOfAnEmptyFeed() = runTest(dispatcher) {
        val vm = NotificationViewModel(FakeNotices(fail = true)).also { store.put("notices", it) }
        runCurrent()
        assertNotNull(vm.errorMessage)
    }

    private class FakeNotices(val fail: Boolean = false) : NoticeSource {
        val feedCalls = mutableListOf<Set<NotificationSource>>()
        val searchCalls = mutableListOf<Pair<Set<NotificationSource>, String>>()
        override suspend fun page(source: NotificationSource, page: Int): NotificationPage = error("Use selected sources")
        override suspend fun merged(sources: List<NotificationSource>, page: Int): MergedNotificationPage {
            feedCalls += sources.toSet()
            if (fail) error("网络断开")
            return MergedNotificationPage(sources.map { Notification("培养通知", "https://example.invalid/${it.name}", it) }, emptySet(), false)
        }
        override suspend fun search(sources: List<NotificationSource>, keyword: String): MergedNotificationPage {
            searchCalls += sources.toSet() to keyword
            return MergedNotificationPage(emptyList(), emptySet(), false)
        }
    }
}
