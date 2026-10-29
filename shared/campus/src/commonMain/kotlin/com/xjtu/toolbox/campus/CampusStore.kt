package com.xjtu.toolbox.campus

import com.xjtu.toolbox.jwapp.JwappScoreCache
import com.xjtu.toolbox.jwapp.TermScore
import com.xjtu.toolbox.platform.KeyValueStore
import com.xjtu.toolbox.platform.synchronizedBlock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

/** The index lets logout remove exactly one account's secrets and sensitive cached data. */
internal class ScopedCampusStore(private val delegate: KeyValueStore, private val prefix: String) : KeyValueStore {
    private val indexKey = "$prefix:index"
    private fun full(key: String) = "$prefix:$key"
    override fun getString(key: String): String? = delegate.getString(full(key))
    override fun putString(key: String, value: String?) = synchronizedBlock(mutationLock) {
        val keys = delegate.getStringSet(indexKey).orEmpty().toMutableSet()
        if (value == null) {
            delegate.remove(full(key))
            keys.remove(key)
            delegate.putStringSet(indexKey, keys)
        } else {
            keys.add(key)
            // Index first: a failed value write must not leave an undiscoverable secret.
            delegate.putStringSet(indexKey, keys)
            delegate.putString(full(key), value)
        }
    }
    override fun getBoolean(key: String, default: Boolean): Boolean = getString(key)?.toBooleanStrictOrNull() ?: default
    override fun putBoolean(key: String, value: Boolean) = putString(key, value.toString())
    override fun getInt(key: String, default: Int): Int = getString(key)?.toIntOrNull() ?: default
    override fun putInt(key: String, value: Int) = putString(key, value.toString())
    override fun getStringSet(key: String): Set<String>? = getString(key)?.let {
        runCatching { campusJson.decodeFromString<Set<String>>(it) }.getOrNull()
    }
    override fun putStringSet(key: String, value: Set<String>) = putString(key, campusJson.encodeToString(value))
    override fun remove(key: String) = putString(key, null)
    override fun contains(key: String): Boolean = getString(key) != null
    override fun clear() = synchronizedBlock(mutationLock) {
        delegate.getStringSet(indexKey).orEmpty().forEach { delegate.remove(full(it)) }
        delegate.remove(indexKey)
    }
    private companion object { val mutationLock = Any() }
}

@Serializable
private data class ScoreCacheRecord(val savedAt: Long, val scores: List<TermScore>)

/** Account is resolved on each operation, so a retained source/cache cannot return the previous user's grades. */
internal class SecureCampusScoreCache(
    private val accountStore: () -> KeyValueStore?, private val now: () -> Long = ::epochMillis,
    private val mutex: Mutex = Mutex(),
) : JwappScoreCache {
    override suspend fun read(maxAgeMs: Long): List<TermScore>? = mutex.withLock {
        val store = accountStore() ?: return@withLock null
        val raw = store.getString("scores") ?: return@withLock null
        val record = runCatching { campusJson.decodeFromString<ScoreCacheRecord>(raw) }.getOrNull()
        if (record == null || now() < record.savedAt || maxAgeMs >= 0 && now() - record.savedAt > maxAgeMs) {
            store.remove("scores")
            return@withLock null
        }
        record.scores
    }
    override suspend fun write(scores: List<TermScore>) = mutex.withLock {
        accountStore()?.putString("scores", campusJson.encodeToString(ScoreCacheRecord(now(), scores)))
        Unit
    }
}
