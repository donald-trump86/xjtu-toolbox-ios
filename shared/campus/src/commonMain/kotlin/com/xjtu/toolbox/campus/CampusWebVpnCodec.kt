package com.xjtu.toolbox.campus

import com.xjtu.toolbox.webvpn.WebVpnCodec
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.takeFrom

/** Wenruo WebVPN's fixed public URL obfuscation key; it does not protect stored credentials. */
object CampusWebVpnCodec : WebVpnCodec {
    const val GATEWAY = "webvpn.xjtu.edu.cn"
    private val key = byteArrayOf(119, 114, 100, 118, 112, 110, 105, 115, 116, 104, 101, 98, 101, 115, 116, 33)
    private val ivHex = key.hex()

    override fun toVpnUrl(url: String): String {
        val plain = Url(url)
        if (plain.host == GATEWAY) return url
        require(plain.protocol.name in setOf("https", "http") && plain.user.isNullOrEmpty() && plain.password.isNullOrEmpty())
        val port = if (plain.specifiedPort > 0 && plain.specifiedPort != plain.protocol.defaultPort) "-${plain.specifiedPort}" else ""
        val host = cfb(plain.host.encodeToByteArray(), false).hex()
        return "https://$GATEWAY/${plain.protocol.name}$port/$ivHex$host${plain.encodedPath.ifEmpty { "/" }}" +
            plain.encodedQuery.takeIf(String::isNotEmpty).orEmpty().let { if (it.isEmpty()) "" else "?$it" } +
            plain.encodedFragment.takeIf(String::isNotEmpty).orEmpty().let { if (it.isEmpty()) "" else "#$it" }
    }

    override fun fromVpnUrl(vpnUrl: String): String? = runCatching {
        val vpn = Url(vpnUrl)
        require(vpn.protocol.name == "https" && vpn.host == GATEWAY)
        val parts = vpn.encodedPath.removePrefix("/").split('/', limit = 3)
        require(parts.size >= 2 && parts[1].startsWith(ivHex) && parts[1].length > 32)
        val protocol = parts[0].substringBefore('-')
        require(protocol in setOf("http", "https"))
        val port = parts[0].substringAfter('-', "").let {
            if (it.isEmpty()) "" else ":${it.toInt().also { port -> require(port in 1..65535) }}"
        }
        val hex = parts[1].substring(32)
        require(hex.length % 2 == 0 && hex.all { it.digitToIntOrNull(16) != null })
        val host = cfb(hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray(), true).decodeToString()
        require(host.matches(Regex("[a-zA-Z0-9.-]+")))
        "$protocol://$host$port/${parts.getOrNull(2).orEmpty()}" +
            (if (vpn.encodedQuery.isEmpty()) "" else "?${vpn.encodedQuery}") +
            (if (vpn.encodedFragment.isEmpty()) "" else "#${vpn.encodedFragment}")
    }.getOrNull()

    private fun cfb(input: ByteArray, decrypt: Boolean): ByteArray {
        val result = ByteArray(input.size)
        var feedback = key.copyOf()
        for (offset in input.indices step 16) {
            val pad = PublicVpnAes.encrypt(key, feedback)
            val length = minOf(16, input.size - offset)
            repeat(length) { result[offset + it] = (input[offset + it].toInt() xor pad[it].toInt()).toByte() }
            if (length == 16) feedback = (if (decrypt) input else result).copyOfRange(offset, offset + 16)
        }
        return result
    }
}

private fun ByteArray.hex(): String = joinToString("") { it.toUByte().toString(16).padStart(2, '0') }

/** AES-128 block operation used solely by the public WebVPN hostname encoding. */
internal object PublicVpnAes {
    private fun multiply(a: Int, b: Int): Int {
        var x = a; var y = b; var product = 0
        repeat(8) {
            if (y and 1 != 0) product = product xor x
            x = ((x shl 1) xor if (x and 128 != 0) 0x11b else 0) and 255
            y = y ushr 1
        }
        return product
    }
    private val sbox = IntArray(256) { value ->
        var inverse = if (value == 0) 0 else 1
        if (value != 0) repeat(254) { inverse = multiply(inverse, value) }
        var result = inverse xor 0x63
        for (shift in 1..4) result = result xor (((inverse shl shift) or (inverse ushr (8 - shift))) and 255)
        result
    }
    fun encrypt(key: ByteArray, input: ByteArray): ByteArray {
        require(key.size == 16 && input.size == 16)
        val expanded = IntArray(176) { if (it < 16) key[it].toInt().and(255) else 0 }
        var rcon = 1
        for (offset in 16 until 176 step 4) {
            var t = IntArray(4) { expanded[offset - 4 + it] }
            if (offset % 16 == 0) {
                t = intArrayOf(sbox[t[1]] xor rcon, sbox[t[2]], sbox[t[3]], sbox[t[0]])
                rcon = multiply(rcon, 2)
            }
            repeat(4) { expanded[offset + it] = expanded[offset - 16 + it] xor t[it] }
        }
        var state = IntArray(16) { input[it].toInt().and(255) xor expanded[it] }
        for (round in 1..10) {
            state = IntArray(16) { index -> sbox[state[(index % 4) + 4 * ((index / 4 + index % 4) % 4)]] }
            if (round != 10) for (column in 0..3) {
                val at = column * 4
                val a = state.copyOfRange(at, at + 4)
                repeat(4) { row ->
                    state[at + row] = multiply(a[row], 2) xor multiply(a[(row + 1) % 4], 3) xor a[(row + 2) % 4] xor a[(row + 3) % 4]
                }
            }
            repeat(16) { state[it] = state[it] xor expanded[round * 16 + it] }
        }
        return ByteArray(16) { state[it].toByte() }
    }
}
