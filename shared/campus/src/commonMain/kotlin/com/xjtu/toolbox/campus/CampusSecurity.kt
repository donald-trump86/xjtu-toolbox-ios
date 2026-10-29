package com.xjtu.toolbox.campus

import com.xjtu.toolbox.platform.KeyValueStore
import io.ktor.client.engine.HttpClientEngine
import kotlinx.serialization.Serializable
import kotlin.io.encoding.Base64

/** iOS stores every value in a device-only Keychain item. JVM uses process memory for protocol tests. */
expect fun secureCampusStore(namespace: String): KeyValueStore
internal expect fun campusHttpEngine(): HttpClientEngine
internal expect fun secureRandomBytes(count: Int): ByteArray
internal expect fun rsaPkcs1Encrypt(publicKeyPkcs1: ByteArray, plaintext: ByteArray): ByteArray

/** CAS uses RSAES-PKCS1-v1_5 and the literal __RSA__ prefix, as XJTULogin.encryptPassword does. */
object CampusRsa {
    fun encryptPassword(password: String, pem: String): String {
        val base64 = pem.lineSequence().filterNot { it.trim().startsWith("-----") }
            .joinToString("").filterNot(Char::isWhitespace)
        val der = try { Base64.decode(base64) } catch (_: IllegalArgumentException) {
            throw CampusProtocolException("认证公钥格式错误")
        }
        val pkcs1 = pkcs1Key(der)
        return "__RSA__" + Base64.encode(rsaPkcs1Encrypt(pkcs1, password.encodeToByteArray()))
    }

    internal fun pkcs1Key(der: ByteArray): ByteArray {
        val outer = DerReader(der).read(0x30)
        val reader = DerReader(outer)
        return when (outer.firstOrNull()?.toInt()?.and(255)) {
            0x02 -> {
                require(reader.read(0x02).isNotEmpty() && reader.read(0x02).isNotEmpty() && reader.finished) {
                    "认证公钥格式错误"
                }
                der
            }
            0x30 -> {
                val algorithm = reader.read(0x30)
                val rsaOid = byteArrayOf(6, 9, 42, -122, 72, -122, -9, 13, 1, 1, 1)
                require(algorithm.take(rsaOid.size).toByteArray().contentEquals(rsaOid)) { "认证公钥类型错误" }
                val bits = reader.read(0x03)
                require(bits.firstOrNull() == 0.toByte() && reader.finished) { "认证公钥格式错误" }
                pkcs1Key(bits.copyOfRange(1, bits.size))
            }
            else -> throw CampusProtocolException("认证公钥格式错误")
        }
    }
}

private class DerReader(private val bytes: ByteArray) {
    private var position = 0
    val finished: Boolean get() = position == bytes.size
    fun read(tag: Int): ByteArray {
        require(position < bytes.size && bytes[position++].toInt().and(255) == tag) { "认证公钥格式错误" }
        require(position < bytes.size) { "认证公钥格式错误" }
        var length = bytes[position++].toInt().and(255)
        if (length and 128 != 0) {
            val count = length and 127
            require(count in 1..3 && position + count <= bytes.size) { "认证公钥格式错误" }
            length = 0
            repeat(count) { length = (length shl 8) or bytes[position++].toInt().and(255) }
        }
        require(length >= 0 && position + length <= bytes.size) { "认证公钥格式错误" }
        return bytes.copyOfRange(position, position + length).also { position += length }
    }
}

@Serializable
enum class CampusRoute { DIRECT, WEBVPN }
enum class CampusStatus { LOGGED_OUT, RESTORING, AUTHENTICATING, SMS_REQUIRED, AUTHENTICATED, EXPIRED, ERROR }
enum class CampusMfaFlow { MFA_DETECT, SAFETY_VERIFY }

data class CampusSessionState(
    val status: CampusStatus = CampusStatus.LOGGED_OUT,
    val accountId: String? = null,
    val route: CampusRoute = CampusRoute.DIRECT,
    val message: String = "",
    val maskedPhone: String? = null,
    val smsAlreadySent: Boolean = false,
    val mfaFlow: CampusMfaFlow? = null,
)

open class CampusProtocolException(message: String) : RuntimeException(message)
class CampusSessionExpiredException : CampusProtocolException("登录已过期，请重新登录")
class CampusMfaRequiredException : CampusProtocolException("请完成短信验证")

internal val campusJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
internal fun visitorId(): String = secureRandomBytes(16).joinToString("") { it.toUByte().toString(16).padStart(2, '0') }
