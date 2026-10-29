package com.xjtu.toolbox.campus

import com.xjtu.toolbox.platform.KeyValueStore
import com.xjtu.toolbox.platform.synchronizedBlock

/** A login owns these caches. Revocation makes even synchronous, late writes harmless. */
class CampusCacheScope(private val storeFactory: (String) -> KeyValueStore = ::secureCampusStore) {
    private val lock = Any()
    private var active = true
    private val stores = mutableMapOf<String, GuardedStore>()

    fun store(namespace: String): KeyValueStore = synchronizedBlock(lock) {
        stores.getOrPut(namespace) { GuardedStore(storeFactory(namespace)) }
    }

    /** Stop old work without deleting the offline cache on an ordinary app close. */
    fun revoke() = synchronizedBlock(lock) { active = false }

    /** Repeated calls retry failed cleanup, while the revoked writers remain blocked. */
    fun clear() = synchronizedBlock(lock) {
        active = false
        stores.values.forEach { it.delegate.clear() }
    }

    private inner class GuardedStore(val delegate: KeyValueStore) : KeyValueStore {
        private fun <T> read(fallback: T, action: () -> T): T = synchronizedBlock(lock) {
            if (active) action() else fallback
        }
        private fun mutate(action: () -> Unit) = synchronizedBlock(lock) { if (active) action() }
        override fun getString(key: String): String? = read(null) { delegate.getString(key) }
        override fun putString(key: String, value: String?) = mutate { delegate.putString(key, value) }
        override fun getBoolean(key: String, default: Boolean): Boolean = read(default) { delegate.getBoolean(key, default) }
        override fun putBoolean(key: String, value: Boolean) = mutate { delegate.putBoolean(key, value) }
        override fun getInt(key: String, default: Int): Int = read(default) { delegate.getInt(key, default) }
        override fun putInt(key: String, value: Int) = mutate { delegate.putInt(key, value) }
        override fun getStringSet(key: String): Set<String>? = read(null) { delegate.getStringSet(key) }
        override fun putStringSet(key: String, value: Set<String>) = mutate { delegate.putStringSet(key, value) }
        override fun remove(key: String) = mutate { delegate.remove(key) }
        override fun contains(key: String): Boolean = read(false) { delegate.contains(key) }
        override fun clear() = mutate { delegate.clear() }
    }
}
