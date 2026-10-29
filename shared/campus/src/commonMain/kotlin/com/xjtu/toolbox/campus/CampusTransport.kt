package com.xjtu.toolbox.campus

import com.xjtu.toolbox.webvpn.WebVpnCodec
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlin.time.Clock

internal const val CAS_HOST = "login.xjtu.edu.cn"
internal const val JWXT_HOST = "jwxt.xjtu.edu.cn"
internal const val JWAPP_HOST = "jwapp.xjtu.edu.cn"
internal const val VPN_HOST = "webvpn.xjtu.edu.cn"
internal const val JWXT_ENTRY = "https://jwxt.xjtu.edu.cn/jwapp/sys/homeapp/index.do"
internal const val JWAPP_ENTRY = "https://org.xjtu.edu.cn/openplatform/oauth/authorize?appId=1370&redirectUri=http%3A%2F%2Fjwapp.xjtu.edu.cn%2Fapp%2Findex&responseType=code&scope=user_info&state=1234"
internal fun epochMillis(): Long = Clock.System.now().toEpochMilliseconds()

@Serializable
internal data class CampusCookie(
    val name: String, val value: String, val domain: String, val path: String,
    val secure: Boolean, val hostOnly: Boolean, val expiresAt: Long?, val site: String,
)

/** A route owns its jar; a logical host owns its cookie partition, including Domain cookies. */
internal class CampusCookieJar(private val now: () -> Long = ::epochMillis) {
    private val cookies = mutableListOf<CampusCookie>()
    fun snapshot(): List<CampusCookie> = cookies.filter { it.expiresAt == null || it.expiresAt > now() }
    fun restore(values: List<CampusCookie>) {
        cookies.clear()
        cookies.addAll(values.filter { it.site in hosts && (it.expiresAt == null || it.expiresAt > now()) })
    }
    fun clear() = cookies.clear()
    fun receive(physical: Url, logical: Url, headers: List<String>) {
        for (header in headers) {
            val parsed = runCatching { parseServerSetCookieHeader(header) }.getOrNull() ?: continue
            if (parsed.name.isEmpty() || parsed.name.any { it <= ' ' || it == ';' }) continue
            val domain = parsed.domain?.lowercase()?.trimStart('.') ?: physical.host.lowercase()
            val hostOnly = parsed.domain.isNullOrBlank()
            if (physical.host != domain && !physical.host.endsWith(".$domain")) continue
            if (!hostOnly && domain !in hosts && domain != "xjtu.edu.cn") continue // reject public-suffix / unrelated domains
            if (parsed.secure && physical.protocol != URLProtocol.HTTPS) continue
            val path = parsed.path?.takeIf { it.startsWith('/') } ?: physical.encodedPath
                .substringBeforeLast('/', "").ifEmpty { "/" }
            val maxAgeText = header.split(';').drop(1).firstOrNull {
                it.trim().substringBefore('=').equals("max-age", true)
            }?.substringAfter('=')?.trim()
            val maxAge = maxAgeText?.toLongOrNull()
            val expiresAt = if (maxAge != null) {
                if (maxAge <= 0) 0 else now() + maxAge.coerceAtMost(365L * 24 * 60 * 60) * 1000
            } else parsed.expires?.timestamp
            val site = logical.host.lowercase()
            cookies.removeAll { it.name == parsed.name && it.domain == domain && it.path == path && it.site == site }
            if (expiresAt == null || expiresAt > now()) cookies += CampusCookie(
                parsed.name, parsed.value, domain, path, parsed.secure, hostOnly, expiresAt, site,
            )
        }
    }
    fun header(physical: Url, logical: Url): String {
        cookies.removeAll { it.expiresAt != null && it.expiresAt <= now() }
        return cookies.filter { cookie ->
            (cookie.site == logical.host || cookie.site == VPN_HOST && physical.host == VPN_HOST) &&
                (physical.host == cookie.domain || !cookie.hostOnly && physical.host.endsWith(".${cookie.domain}")) &&
                (!cookie.secure || physical.protocol == URLProtocol.HTTPS) &&
                (physical.encodedPath == cookie.path || physical.encodedPath.startsWith(cookie.path.let { if (it.endsWith('/')) it else "$it/" }))
        }.sortedByDescending { it.path.length }.joinToString("; ") { "${it.name}=${it.value}" }
    }
    companion object {
        val hosts = setOf(CAS_HOST, JWXT_HOST, JWAPP_HOST, "org.xjtu.edu.cn", VPN_HOST)
    }
}

internal data class CampusReply(
    val status: Int, val url: String, val headers: Headers, val body: String, val physicalUrl: String = url,
)

/** Redirects are handled here so Cookie/Authorization cannot escape their original hosts. No logging plugin. */
internal class CampusTransport(
    engine: HttpClientEngine?, private val codec: WebVpnCodec,
    private val now: () -> Long = ::epochMillis,
) {
    var route: CampusRoute = CampusRoute.DIRECT
    private val jars = CampusRoute.entries.associateWith { CampusCookieJar(now) }
    private val requestMutex = Mutex()
    private val client = HttpClient(engine ?: campusHttpEngine()) {
        followRedirects = false
        expectSuccess = false
        install(HttpTimeout) { requestTimeoutMillis = 30_000; connectTimeoutMillis = 15_000; socketTimeoutMillis = 30_000 }
    }
    fun snapshot(): Map<CampusRoute, List<CampusCookie>> = jars.mapValues { it.value.snapshot() }
    fun restore(values: Map<CampusRoute, List<CampusCookie>>) = jars.forEach { (route, jar) -> jar.restore(values[route].orEmpty()) }
    fun clear() = jars.values.forEach(CampusCookieJar::clear)
    fun close() = client.close()
    fun logical(url: String): Url = if (Url(url).host == VPN_HOST) codec.fromVpnUrl(url)?.let(::Url) ?: Url(url) else Url(url)
    fun isWebVpnGatewayReady(reply: CampusReply): Boolean {
        if (route != CampusRoute.WEBVPN || Url(reply.physicalUrl).host != VPN_HOST || isWebVpnLogin(reply)) return false
        val plain = logical(reply.physicalUrl)
        if (plain.host != VPN_HOST) return plain.host in CampusCookieJar.hosts && plain.host != CAS_HOST
        if (reply.status !in 200..299 || isAuthFailure(reply)) return false
        val gateway = Url("https://$VPN_HOST/")
        return jars.getValue(route).header(gateway, gateway).split(';').any {
            it.trim().substringBefore('=') == "wengine_vpn_ticketwebvpn_xjtu_edu_cn" && it.substringAfter('=', "").isNotBlank()
        }
    }

    suspend fun request(
        url: String, method: HttpMethod = HttpMethod.Get, body: String? = null,
        contentType: ContentType? = null, token: String? = null,
        referer: String? = null, credentialPost: Boolean = false,
    ): CampusReply = requestMutex.withLock {
        var current = logical(url)
        var verb = method
        var payload = body
        var previous = referer
        repeat(13) { redirects ->
            checkAllowed(current, credentialPost && payload != null)
            if (current.host == JWAPP_HOST && current.protocol == URLProtocol.HTTP) {
                current = URLBuilder().takeFrom(current).apply { protocol = URLProtocol.HTTPS; port = 443 }.build()
            }
            val physical = if (route == CampusRoute.WEBVPN && current.host != VPN_HOST) Url(codec.toVpnUrl(current.toString())) else current
            check(physical.host in CampusCookieJar.hosts && (route != CampusRoute.WEBVPN || physical.host == VPN_HOST))
            val result = try {
                client.request(physical) {
                    this.method = verb
                    headers {
                        append(HttpHeaders.Accept, "application/json, text/html;q=0.9, */*;q=0.8")
                        append(HttpHeaders.UserAgent, "XJTUToolBox-iOS/1.0")
                        val cookie = jars.getValue(route).header(physical, current)
                        if (cookie.isNotEmpty()) append(HttpHeaders.Cookie, cookie)
                        if (token != null && current.host == JWAPP_HOST && current.protocol == URLProtocol.HTTPS && current.encodedPath.startsWith("/api/")) {
                            append(HttpHeaders.Authorization, token)
                        }
                        if (previous != null) {
                            val origin = logical(previous!!)
                            val safe = URLBuilder().takeFrom(origin).apply {
                                if (origin.host != current.host || origin.protocol != current.protocol) parameters.clear()
                                fragment = ""
                            }.buildString()
                            append(HttpHeaders.Referrer, if (route == CampusRoute.WEBVPN) codec.toVpnUrl(safe) else safe)
                        }
                        if (verb != HttpMethod.Get && verb != HttpMethod.Head) {
                            append(HttpHeaders.Origin, if (route == CampusRoute.WEBVPN) "https://$VPN_HOST" else "${current.protocol.name}://${current.host}")
                        }
                    }
                    if (contentType != null && payload != null) contentType(contentType)
                    if (payload != null) setBody(payload!!)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { throw CampusProtocolException("校园网络请求失败，请检查网络后重试") }
            jars.getValue(route).receive(physical, current, result.headers.getAll(HttpHeaders.SetCookie).orEmpty())
            val text = try { result.bodyAsText() } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { throw CampusProtocolException("校园网络响应读取失败") }
            if (text.length > 4 * 1024 * 1024) throw CampusProtocolException("校园网络响应过大")
            val location = result.headers[HttpHeaders.Location]
            if (result.status.value in setOf(301, 302, 303, 307, 308) && location != null) {
                if (redirects == 12) throw CampusProtocolException("校园登录跳转次数过多")
                val nextPhysical = URLBuilder().takeFrom(physical).takeFrom(location).build()
                val next = logical(nextPhysical.toString())
                // A 307/308 cannot forward password or SMS bodies to another origin.
                val preserves = result.status.value in setOf(307, 308)
                if (preserves && payload != null && (next.host != current.host || next.protocol != current.protocol)) {
                    throw CampusProtocolException("校园登录返回了不安全的跳转")
                }
                previous = current.toString()
                if (result.status.value == 303 || result.status.value in setOf(301, 302) && verb == HttpMethod.Post) {
                    verb = HttpMethod.Get; payload = null
                }
                current = next
            } else {
                return@withLock CampusReply(result.status.value, current.toString(), result.headers, text, physical.toString())
            }
        }
        throw CampusProtocolException("校园登录跳转次数过多")
    }
    private fun checkAllowed(url: Url, credentialPost: Boolean) {
        if (url.host !in CampusCookieJar.hosts || !url.user.isNullOrEmpty() || !url.password.isNullOrEmpty() ||
            url.protocol !in setOf(URLProtocol.HTTPS, URLProtocol.HTTP) ||
            url.port != url.protocol.defaultPort || url.protocol == URLProtocol.HTTP && url.host != JWAPP_HOST) {
            throw CampusProtocolException("校园请求地址不在允许范围内")
        }
        if (credentialPost && (url.host != CAS_HOST || url.protocol != URLProtocol.HTTPS || url.encodedPath != "/cas/login" && url.encodedPath != "/cas/mfa/detect")) {
            throw CampusProtocolException("校园登录凭据提交地址无效")
        }
    }
}

internal fun formBody(vararg values: Pair<String, String>): String = Parameters.build {
    values.forEach { (name, value) -> append(name, value) }
}.formUrlEncode()
