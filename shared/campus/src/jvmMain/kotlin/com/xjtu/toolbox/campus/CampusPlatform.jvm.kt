package com.xjtu.toolbox.campus

import com.xjtu.toolbox.platform.KeyValueStore
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import java.math.BigInteger
import java.security.KeyFactory
import java.security.SecureRandom
import java.security.spec.RSAPublicKeySpec
import javax.crypto.Cipher
import kotlinx.serialization.encodeToString

internal actual fun campusHttpEngine(): HttpClientEngine = OkHttp.create {
    config { followRedirects(false); followSslRedirects(false) }
}
internal actual fun secureRandomBytes(count: Int): ByteArray = ByteArray(count).also { SecureRandom().nextBytes(it) }
internal actual fun rsaPkcs1Encrypt(publicKeyPkcs1: ByteArray, plaintext: ByteArray): ByteArray {
    // Extract INTEGERs from the already-validated PKCS#1 sequence, without a third-party crypto dependency.
    var at = 0
    fun read(tag: Int): ByteArray {
        require(publicKeyPkcs1[at++].toInt().and(255) == tag)
        var length = publicKeyPkcs1[at++].toInt().and(255)
        if (length and 128 != 0) {
            val count = length and 127; length = 0
            repeat(count) { length = (length shl 8) or publicKeyPkcs1[at++].toInt().and(255) }
        }
        return publicKeyPkcs1.copyOfRange(at, at + length).also { at += length }
    }
    read(0x30); at = 1
    val firstLength = publicKeyPkcs1[at++].toInt().and(255)
    if (firstLength and 128 != 0) at += firstLength and 127
    val modulus = BigInteger(1, read(0x02))
    val exponent = BigInteger(1, read(0x02))
    val key = KeyFactory.getInstance("RSA").generatePublic(RSAPublicKeySpec(modulus, exponent))
    return try {
        Cipher.getInstance("RSA/ECB/PKCS1Padding").apply { init(Cipher.ENCRYPT_MODE, key, SecureRandom()) }.doFinal(plaintext)
    } catch (_: Exception) { throw CampusProtocolException("认证密码加密失败") }
}

/** No JVM plaintext credential file: desktop protocol tests retain values only for this process. */
actual fun secureCampusStore(namespace: String): KeyValueStore = synchronized(memoryStores) {
    memoryStores.getOrPut(namespace) { JvmMemorySecureStore() }
}
private val memoryStores = mutableMapOf<String, KeyValueStore>()
private class JvmMemorySecureStore : KeyValueStore {
    private val values = java.util.concurrent.ConcurrentHashMap<String, String>()
    override fun getString(key: String): String? = values[key]
    override fun putString(key: String, value: String?) { if (value == null) values.remove(key) else values[key] = value }
    override fun getBoolean(key: String, default: Boolean): Boolean = getString(key)?.toBooleanStrictOrNull() ?: default
    override fun putBoolean(key: String, value: Boolean) = putString(key, value.toString())
    override fun getInt(key: String, default: Int): Int = getString(key)?.toIntOrNull() ?: default
    override fun putInt(key: String, value: Int) = putString(key, value.toString())
    override fun getStringSet(key: String): Set<String>? = getString(key)?.let { runCatching { campusJson.decodeFromString<Set<String>>(it) }.getOrNull() }
    override fun putStringSet(key: String, value: Set<String>) = putString(key, campusJson.encodeToString(value))
    override fun remove(key: String) { values.remove(key) }
    override fun contains(key: String): Boolean = values.containsKey(key)
    override fun clear() = values.clear()
}
