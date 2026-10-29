package com.xjtu.toolbox.campus

import com.xjtu.toolbox.platform.KeyValueStore
import com.xjtu.toolbox.platform.synchronizedBlock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class CampusStoreConcurrencyTest {
    @Test
    fun concurrentWrapperInstancesRetainEveryKeyForLogout() = runTest {
        val raw = ConcurrentMemoryStore()
        val first = ScopedCampusStore(raw, "account:A")
        val second = ScopedCampusStore(raw, "account:A")
        val other = ScopedCampusStore(raw, "account:B")
        withContext(Dispatchers.Default) {
            withTimeout(15_000L) {
                val ready = List(2) { CompletableDeferred<Unit>() }
                val start = CompletableDeferred<Unit>()
                val writers = listOf(first, second).mapIndexed { writer, store ->
                    launch {
                        ready[writer].complete(Unit)
                        start.await()
                        repeat(250) { store.putString("$writer:$it", "cached-value") }
                    }
                }
                ready.forEach { it.await() }
                start.complete(Unit)
                writers.joinAll()
            }
        }
        repeat(2) { writer ->
            repeat(250) { assertEquals("cached-value", raw.getString("account:A:$writer:$it")) }
        }
        other.putString("session", "other-account")
        first.clear()
        repeat(2) { writer -> repeat(250) { assertNull(raw.getString("account:A:$writer:$it")) } }
        assertNull(raw.getStringSet("account:A:index"))
        assertEquals("other-account", other.getString("session"))
    }

    @Test
    fun partiallyFailedValueWriteRemainsDiscoverableForClear() {
        val raw = ConcurrentMemoryStore()
        val failing = object : KeyValueStore by raw {
            override fun putString(key: String, value: String?) {
                raw.putString(key, value)
                if (key == "account:A:scores") error("Synthetic write failure")
            }
        }
        val account = ScopedCampusStore(failing, "account:A")
        assertFailsWith<IllegalStateException> { account.putString("scores", "cached-grades") }
        assertEquals("cached-grades", raw.getString("account:A:scores"))
        account.clear()
        assertNull(raw.getString("account:A:scores"))
        assertNull(raw.getStringSet("account:A:index"))
    }

    // Each operation is safe; only ScopedCampusStore can make the index read-modify-write atomic.
    private class ConcurrentMemoryStore : KeyValueStore {
        private val delegate = TestCampusStore()
        private val lock = Any()

        override fun getString(key: String): String? = synchronizedBlock(lock) { delegate.getString(key) }
        override fun putString(key: String, value: String?) = synchronizedBlock(lock) { delegate.putString(key, value) }
        override fun getBoolean(key: String, default: Boolean): Boolean = synchronizedBlock(lock) { delegate.getBoolean(key, default) }
        override fun putBoolean(key: String, value: Boolean) = synchronizedBlock(lock) { delegate.putBoolean(key, value) }
        override fun getInt(key: String, default: Int): Int = synchronizedBlock(lock) { delegate.getInt(key, default) }
        override fun putInt(key: String, value: Int) = synchronizedBlock(lock) { delegate.putInt(key, value) }
        override fun getStringSet(key: String): Set<String>? = synchronizedBlock(lock) { delegate.getStringSet(key) }
        override fun putStringSet(key: String, value: Set<String>) = synchronizedBlock(lock) { delegate.putStringSet(key, value) }
        override fun remove(key: String) = synchronizedBlock(lock) { delegate.remove(key) }
        override fun contains(key: String): Boolean = synchronizedBlock(lock) { delegate.contains(key) }
        override fun clear() = synchronizedBlock(lock) { delegate.clear() }
    }
}
