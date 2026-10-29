@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.xjtu.toolbox.platform

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.usePinned
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSData
import platform.Foundation.NSDataWritingAtomic
import platform.Foundation.NSDataWritingFileProtectionComplete
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileProtectionComplete
import platform.Foundation.NSFileProtectionKey
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.writeToFile

private val storeLock = Any()
private val stores = mutableMapOf<String, KeyValueStore>()
private val settingsNamespaces = setOf("app_settings", "haptics_prefs")
private val sensitiveWords = listOf("password", "passwd", "credential", "cookie", "session", "token", "secret", "ticket", "captcha", "authorization")

/** Settings use defaults; ordinary model caches use protected, atomic files. Auth belongs in Keychain. */
actual fun keyValueStore(name: String): KeyValueStore = synchronizedBlock(storeLock) {
    require(name.isNotBlank() && name.encodeToByteArray().size <= 150) { "Invalid store namespace" }
    require(sensitiveWords.none { it in name.lowercase() }) { "Use the secure credential store" }
    stores.getOrPut(name) { IosKeyValueStore(name) }
}

private class IosKeyValueStore(private val name: String) : KeyValueStore {
    private val defaultsKey = "toolbox.settings.$name"
    private val isSettings = name in settingsNamespaces

    private fun path(): String {
        val fm = NSFileManager.defaultManager
        val root = checkNotNull(fm.URLForDirectory(NSApplicationSupportDirectory, NSUserDomainMask, null, true, null))
        val directory = checkNotNull(root.URLByAppendingPathComponent("ToolboxModelStores", true))
        check(fm.createDirectoryAtURL(directory, true, mapOf(NSFileProtectionKey to NSFileProtectionComplete), null)) {
            "Protected cache directory is unavailable"
        }
        directory.setResourceValue(true, NSURLIsExcludedFromBackupKey, null)
        val file = base64UrlEncode(name.encodeToByteArray()) + ".json"
        return checkNotNull(directory.URLByAppendingPathComponent(file, false)?.path)
    }

    private fun readValues(): Map<String, JsonElement> {
        val text = if (isSettings) {
            NSUserDefaults.standardUserDefaults.stringForKey(defaultsKey) ?: return emptyMap()
        } else {
            val path = path()
            if (!NSFileManager.defaultManager.fileExistsAtPath(path)) return emptyMap()
            val data = checkNotNull(NSData.create(contentsOfFile = path)) { "Protected cache is unavailable" }
            check(data.length <= Int.MAX_VALUE.toULong()) { "Cache is too large" }
            if (data.length == 0uL) return emptyMap()
            checkNotNull(data.bytes).readBytes(data.length.toInt()).decodeToString(throwOnInvalidSequence = true)
        }
        return Json.parseToJsonElement(text) as? JsonObject ?: error("Invalid cache format")
    }

    private fun writeValues(values: Map<String, JsonElement>) {
        val text = JsonObject(values).toString()
        if (isSettings) {
            NSUserDefaults.standardUserDefaults.setObject(text, defaultsKey)
        } else {
            val bytes = text.encodeToByteArray()
            val data = bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
            check(data.writeToFile(path(), NSDataWritingAtomic or NSDataWritingFileProtectionComplete, null)) {
                "Protected cache write failed"
            }
        }
    }

    private fun read(key: String): JsonElement? = synchronizedBlock(storeLock) { readValues()[key] }

    private fun put(key: String, value: JsonElement?) = synchronizedBlock(storeLock) {
        require(sensitiveWords.none { it in key.lowercase() }) { "Use the secure credential store" }
        val next = readValues().toMutableMap()
        if (value == null) next.remove(key) else next[key] = value
        writeValues(next)
    }

    override fun getString(key: String): String? = (read(key) as? JsonPrimitive)?.takeIf { it.isString }?.content
    override fun putString(key: String, value: String?) = put(key, value?.let(::JsonPrimitive))
    override fun getBoolean(key: String, default: Boolean): Boolean = (read(key) as? JsonPrimitive)?.booleanOrNull ?: default
    override fun putBoolean(key: String, value: Boolean) = put(key, JsonPrimitive(value))
    override fun getInt(key: String, default: Int): Int = (read(key) as? JsonPrimitive)?.intOrNull ?: default
    override fun putInt(key: String, value: Int) = put(key, JsonPrimitive(value))
    override fun getStringSet(key: String): Set<String>? = (read(key) as? JsonArray)?.mapNotNull {
        (it as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.content
    }?.toSet()
    override fun putStringSet(key: String, value: Set<String>) = put(key, JsonArray(value.map(::JsonPrimitive)))
    override fun remove(key: String) = synchronizedBlock(storeLock) {
        val next = readValues().toMutableMap()
        if (next.remove(key) != null) writeValues(next)
    }
    override fun contains(key: String): Boolean = synchronizedBlock(storeLock) { readValues().containsKey(key) }
    override fun clear() = synchronizedBlock(storeLock) {
        if (isSettings) {
            NSUserDefaults.standardUserDefaults.removeObjectForKey(defaultsKey)
        } else {
            val path = path()
            val fm = NSFileManager.defaultManager
            if (fm.fileExistsAtPath(path)) check(fm.removeItemAtPath(path, null)) { "Cache removal failed" }
        }
    }
}
