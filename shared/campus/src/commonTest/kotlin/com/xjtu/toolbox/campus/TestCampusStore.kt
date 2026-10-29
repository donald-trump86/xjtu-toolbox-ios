package com.xjtu.toolbox.campus

import com.xjtu.toolbox.platform.KeyValueStore
import kotlinx.serialization.encodeToString

internal class TestCampusStore : KeyValueStore {
    private val values = mutableMapOf<String, String>()
    override fun getString(key: String): String? = values[key]
    override fun putString(key: String, value: String?) { if (value == null) values.remove(key) else values[key] = value }
    override fun getBoolean(key: String, default: Boolean) = values[key]?.toBooleanStrictOrNull() ?: default
    override fun putBoolean(key: String, value: Boolean) = putString(key, value.toString())
    override fun getInt(key: String, default: Int) = values[key]?.toIntOrNull() ?: default
    override fun putInt(key: String, value: Int) = putString(key, value.toString())
    override fun getStringSet(key: String): Set<String>? = values[key]?.let { campusJson.decodeFromString<Set<String>>(it) }
    override fun putStringSet(key: String, value: Set<String>) = putString(key, campusJson.encodeToString(value))
    override fun remove(key: String) { values.remove(key) }
    override fun contains(key: String) = key in values
    override fun clear() = values.clear()
}
