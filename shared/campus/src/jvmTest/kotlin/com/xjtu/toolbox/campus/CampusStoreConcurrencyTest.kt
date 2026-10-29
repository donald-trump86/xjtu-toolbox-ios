package com.xjtu.toolbox.campus

import com.xjtu.toolbox.platform.KeyValueStore
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.*

class CampusStoreConcurrencyTest {
    @Test fun concurrentWrapperInstancesRetainEveryKeyForLogout() {
        val raw = secureCampusStore("index-concurrency-${visitorId()}")
        val first = ScopedCampusStore(raw, "account:A")
        val second = ScopedCampusStore(raw, "account:A")
        val other = ScopedCampusStore(raw, "account:B")
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val writers = listOf(first, second).mapIndexed { writer, store ->
                pool.submit {
                    check(start.await(10, TimeUnit.SECONDS))
                    repeat(250) { store.putString("$writer:$it", "cached-value") }
                }
            }
            start.countDown()
            writers.forEach { it.get(15, TimeUnit.SECONDS) }
            other.putString("session", "other-account")
            first.clear()
            repeat(2) { writer -> repeat(250) { assertNull(raw.getString("account:A:$writer:$it")) } }
            assertEquals("other-account", other.getString("session"))
        } finally { pool.shutdownNow(); raw.clear() }
    }

    @Test fun partiallyFailedValueWriteRemainsDiscoverableForClear() {
        val raw = secureCampusStore("index-partial-write-${visitorId()}")
        val failing = object : KeyValueStore by raw {
            override fun putString(key: String, value: String?) {
                raw.putString(key, value)
                if (key == "account:A:scores") error("Synthetic write failure")
            }
        }
        try {
            val account = ScopedCampusStore(failing, "account:A")
            assertFailsWith<IllegalStateException> { account.putString("scores", "cached-grades") }
            account.clear()
            assertNull(raw.getString("account:A:scores"))
        } finally { raw.clear() }
    }
}
