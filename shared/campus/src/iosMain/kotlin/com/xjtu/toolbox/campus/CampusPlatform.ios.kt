@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.xjtu.toolbox.campus

import com.xjtu.toolbox.platform.KeyValueStore
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin
import kotlinx.cinterop.*
import kotlinx.serialization.encodeToString
import platform.CoreFoundation.*
import platform.Foundation.*
import platform.Security.*

internal actual fun campusHttpEngine(): HttpClientEngine = Darwin.create {
    // Ktor owns the per-account/per-route jar; NSURLSession must not share or persist it.
    val configuration = NSURLSessionConfiguration.ephemeralSessionConfiguration
    configuration.HTTPCookieStorage = null
    configuration.HTTPShouldSetCookies = false
    configuration.URLCache = null
    val delegate = io.ktor.client.engine.darwin.KtorNSURLSessionDelegate()
    val session = NSURLSession.sessionWithConfiguration(configuration, delegate, delegateQueue = null)
    usePreconfiguredSession(session, delegate)
    configureRequest { setHTTPShouldHandleCookies(false) }
}
internal actual fun secureRandomBytes(count: Int): ByteArray = ByteArray(count).also { bytes ->
    require(count > 0)
    val status = bytes.usePinned { SecRandomCopyBytes(kSecRandomDefault, count.toULong(), it.addressOf(0)) }
    if (status != errSecSuccess) throw CampusProtocolException("安全随机数生成失败")
}
internal actual fun rsaPkcs1Encrypt(publicKeyPkcs1: ByteArray, plaintext: ByteArray): ByteArray = withCfDictionary {
    put(kSecAttrKeyType, kSecAttrKeyTypeRSA)
    put(kSecAttrKeyClass, kSecAttrKeyClassPublic)
    val data = publicKeyPkcs1.toCfData()
    try {
        memScoped {
            val error = alloc<CFErrorRefVar>()
            error.value = null
            val key = SecKeyCreateWithData(data, dictionary, error.ptr)
            error.value?.let { CFRelease(it) }
            if (key == null) throw CampusProtocolException("认证公钥导入失败")
            try {
                if (!SecKeyIsAlgorithmSupported(key, kSecKeyOperationTypeEncrypt, kSecKeyAlgorithmRSAEncryptionPKCS1)) {
                    throw CampusProtocolException("设备不支持认证加密算法")
                }
                val plain = plaintext.toCfData()
                try {
                    error.value = null
                    val encrypted = SecKeyCreateEncryptedData(key, kSecKeyAlgorithmRSAEncryptionPKCS1, plain, error.ptr)
                    error.value?.let { CFRelease(it) }
                    if (encrypted == null) throw CampusProtocolException("认证密码加密失败")
                    try { encrypted.toBytes() } finally { CFRelease(encrypted) }
                } finally { CFRelease(plain) }
            } finally { CFRelease(key) }
        }
    } finally { CFRelease(data) }
}

/** Each namespace is a Keychain service; every entry is device-only and readable only while unlocked. */
actual fun secureCampusStore(namespace: String): KeyValueStore = KeychainCampusStore(namespace)

private class KeychainCampusStore(namespace: String) : KeyValueStore {
    private val service = "com.xjtu.toolbox.campus.$namespace"
    override fun getString(key: String): String? = query(key) {
        put(kSecReturnData, kCFBooleanTrue)
        put(kSecMatchLimit, kSecMatchLimitOne)
        memScoped {
            val result = alloc<CFTypeRefVar>(); result.value = null
            when (val status = SecItemCopyMatching(dictionary, result.ptr)) {
                errSecItemNotFound -> null
                errSecSuccess -> {
                    val data = result.value ?: return@memScoped null
                    try { (data as CFDataRef).toBytes().decodeToString() } finally { CFRelease(data) }
                }
                else -> throw CampusProtocolException("钥匙串读取失败（系统状态 $status），请解锁设备后重试")
            }
        }
    }
    override fun putString(key: String, value: String?) {
        if (value == null) { remove(key); return }
        val data = value.encodeToByteArray().toCfData()
        try {
            query(key) {
                val status = withCfDictionary {
                    put(kSecValueData, data)
                    put(kSecAttrAccessible, kSecAttrAccessibleWhenUnlockedThisDeviceOnly)
                    SecItemUpdate(this@query.dictionary, dictionary)
                }
                if (status == errSecItemNotFound) {
                    put(kSecValueData, data)
                    put(kSecAttrAccessible, kSecAttrAccessibleWhenUnlockedThisDeviceOnly)
                    val added = SecItemAdd(dictionary, null)
                    if (added != errSecSuccess) throw CampusProtocolException("钥匙串写入失败（系统状态 $added），请解锁设备后重试")
                } else if (status != errSecSuccess) throw CampusProtocolException("钥匙串写入失败（系统状态 $status），请解锁设备后重试")
            }
        } finally { CFRelease(data) }
    }
    override fun getBoolean(key: String, default: Boolean): Boolean = getString(key)?.toBooleanStrictOrNull() ?: default
    override fun putBoolean(key: String, value: Boolean) = putString(key, value.toString())
    override fun getInt(key: String, default: Int): Int = getString(key)?.toIntOrNull() ?: default
    override fun putInt(key: String, value: Int) = putString(key, value.toString())
    override fun getStringSet(key: String): Set<String>? = getString(key)?.let { runCatching { campusJson.decodeFromString<Set<String>>(it) }.getOrNull() }
    override fun putStringSet(key: String, value: Set<String>) = putString(key, campusJson.encodeToString(value))
    override fun remove(key: String) { query(key) { checkDelete(SecItemDelete(dictionary)) } }
    override fun contains(key: String): Boolean = getString(key) != null
    override fun clear() { query(null) { checkDelete(SecItemDelete(dictionary)) } }
    private fun checkDelete(status: Int) {
        if (status != errSecSuccess && status != errSecItemNotFound) throw CampusProtocolException("钥匙串清理失败（系统状态 $status），请解锁设备后重试")
    }
    private fun <T> query(key: String?, action: CfDictionary.() -> T): T = withCfDictionary {
        put(kSecClass, kSecClassGenericPassword)
        string(kSecAttrService, service)
        if (key != null) string(kSecAttrAccount, key)
        action()
    }
}

/** No dictionary callbacks: string values stay alive explicitly until the Security call has completed. */
private class CfDictionary {
    val dictionary = CFDictionaryCreateMutable(null, 0, null, null) ?: error("Cannot create security query")
    private val owned = mutableListOf<CFTypeRef>()
    fun put(key: COpaquePointer?, value: COpaquePointer?) { CFDictionarySetValue(dictionary, key, value) }
    fun string(key: COpaquePointer?, value: String) {
        val ref = CFStringCreateWithCString(null, value, kCFStringEncodingUTF8) ?: error("Cannot encode security query")
        owned += ref
        put(key, ref)
    }
    fun close() { CFRelease(dictionary); owned.forEach { CFRelease(it) } }
}
private inline fun <T> withCfDictionary(action: CfDictionary.() -> T): T {
    val query = CfDictionary()
    return try { query.action() } finally { query.close() }
}
private fun ByteArray.toCfData(): CFDataRef = if (isEmpty()) {
    CFDataCreate(null, null, 0) ?: error("Cannot create security data")
} else usePinned { CFDataCreate(null, it.addressOf(0).reinterpret(), size.toLong()) ?: error("Cannot create security data") }
private fun CFDataRef.toBytes(): ByteArray {
    val length = CFDataGetLength(this).toInt()
    return if (length == 0) byteArrayOf() else CFDataGetBytePtr(this)!!.readBytes(length)
}
