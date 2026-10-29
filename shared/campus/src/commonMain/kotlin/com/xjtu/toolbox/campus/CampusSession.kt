package com.xjtu.toolbox.campus

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import com.xjtu.toolbox.jwapp.JwappScoreCache
import com.xjtu.toolbox.jwapp.JwappScoreSource
import com.xjtu.toolbox.platform.KeyValueStore
import com.xjtu.toolbox.schedule.JwxtScheduleSource
import com.xjtu.toolbox.webvpn.WebVpnCodec
import io.ktor.client.engine.HttpClientEngine
import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

internal enum class CampusSite(val host: String, val entry: String) {
    GATEWAY(VPN_HOST, "https://webvpn.xjtu.edu.cn/login?cas_login=true"),
    SCHEDULE(JWXT_HOST, JWXT_ENTRY), SCORES(JWAPP_HOST, JWAPP_ENTRY),
}

@Serializable
private data class StoredCampusSession(
    val username: String, val password: String, val route: CampusRoute, val visitor: String,
    val cookies: Map<CampusRoute, List<CampusCookie>>, val tokens: Map<CampusRoute, String>, val savedAt: Long,
)

private data class PendingMfa(
    val flow: CampusMfaFlow, val site: CampusSite, val url: String,
    val fields: Map<String, String>, val gid: String, val phone: String,
    val alreadySent: Boolean, val remaining: List<CampusSite>, val createdAt: Long, val attempts: Int = 0,
)

/** Independent school client. The host supplies only UI and optional MockEngine; no campus-api server is used. */
class CampusSession(
    engine: HttpClientEngine? = null,
    private val store: KeyValueStore = secureCampusStore("session"),
    initialRoute: CampusRoute = CampusRoute.DIRECT,
    vpnCodec: WebVpnCodec = CampusWebVpnCodec,
    private val now: () -> Long = ::epochMillis,
) {
    private val transport = CampusTransport(engine, vpnCodec, now).apply { route = initialRoute }
    private val mutableState = MutableStateFlow(CampusSessionState(route = initialRoute))
    val state: StateFlow<CampusSessionState> = mutableState.asStateFlow()
    private val mutex = Mutex()
    private var sessionGeneration = 0L
    private var username: String? = null
    private var password: String? = null
    private var fingerprint = visitorId()
    private val tokens = mutableMapOf<CampusRoute, String>()
    private val ready = mutableSetOf<Pair<CampusRoute, CampusSite>>()
    private var pending: PendingMfa? = null
    private var interactive = false
    private val schedule = CampusScheduleSource(this)
    private val scores = CampusScoreSource(this)
    val scheduleSource: JwxtScheduleSource get() = schedule
    val scoreSource: JwappScoreSource get() = scores
    val scoreCache: JwappScoreCache
        get() {
            val capturedName = username
            val capturedGeneration = sessionGeneration
            return SecureCampusScoreCache({
                if (capturedName != null && capturedName == username && capturedGeneration == sessionGeneration) accountStore(capturedName) else null
            }, now, mutex)
        }

    suspend fun restore(): CampusSessionState = mutex.withLock {
        val saved = try {
            val account = store.getString("active-account") ?: return@withLock publish(CampusStatus.LOGGED_OUT)
            val restored = accountStore(account).getString("session")?.let {
                runCatching { campusJson.decodeFromString<StoredCampusSession>(it) }.getOrNull()
            }
            restored?.takeIf { it.username == account } ?: return@withLock publish(CampusStatus.LOGGED_OUT)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: CampusProtocolException) {
            return@withLock publish(CampusStatus.ERROR, failure.message ?: "无法读取本机登录状态，请解锁设备后重试")
        } catch (_: Exception) {
            return@withLock publish(CampusStatus.ERROR, "无法读取本机登录状态，请解锁设备后重试")
        }
        sessionGeneration++
        username = saved.username; password = saved.password; fingerprint = saved.visitor
        transport.route = saved.route; transport.restore(saved.cookies)
        tokens.clear(); tokens.putAll(saved.tokens)
        ready.clear(); pending = null; schedule.clearAccountState()
        publish(CampusStatus.RESTORING)
        interactive = false
        guarded { authenticateAll() }.let { result ->
            if (result.status == CampusStatus.ERROR) publish(CampusStatus.EXPIRED, "已保存的登录状态已失效，请重新登录") else result
        }
    }

    suspend fun login(username: String, password: String, route: CampusRoute = transport.route): CampusSessionState = mutex.withLock {
        require(username.isNotBlank() && password.isNotEmpty()) { "请输入账号和密码" }
        sessionGeneration++
        if (this.username != username) {
            transport.clear(); tokens.clear(); ready.clear(); schedule.clearAccountState(); fingerprint = visitorId()
        }
        this.username = username.trim(); this.password = password; transport.route = route
        ready.removeAll { it.first == route }
        pending = null; interactive = true
        publish(CampusStatus.AUTHENTICATING)
        guarded { authenticateAll() }
    }

    /** Explicit user reauthentication may enter the SMS UI. Background source retries never send SMS. */
    suspend fun reAuthenticate(userInitiated: Boolean = true): CampusSessionState = mutex.withLock {
        interactive = userInitiated
        pending = null
        ready.removeAll { it.first == transport.route }
        if (username == null || password == null) return@withLock publish(CampusStatus.EXPIRED, "请重新登录")
        publish(CampusStatus.AUTHENTICATING)
        guarded { authenticateAll() }
    }

    suspend fun ensureSchedule(userInitiated: Boolean = false): JwxtScheduleSource = mutex.withLock {
        interactive = userInitiated
        ensureSite(CampusSite.SCHEDULE)
        schedule
    }
    suspend fun ensureScores(userInitiated: Boolean = false): JwappScoreSource = mutex.withLock {
        interactive = userInitiated
        ensureSite(CampusSite.SCORES)
        scores
    }

    suspend fun requestSms(): CampusSessionState = mutex.withLock {
        val challenge = pending ?: return@withLock state.value
        if (now() - challenge.createdAt !in 0..150_000) {
            pending = null
            return@withLock publish(CampusStatus.EXPIRED, "短信验证已失效，请重新登录")
        }
        if (challenge.alreadySent) return@withLock state.value
        guarded {
            val result = jsonRequest("https://$CAS_HOST/attest/api/guard/securephone/send", buildJsonObject { put("gid", challenge.gid) })
            requireCode(result, 0, "短信发送失败，请稍后重试")
            pending = challenge.copy(alreadySent = true)
            publishMfa(pending!!)
        }
    }

    suspend fun verifySms(code: String): CampusSessionState = mutex.withLock {
        val challenge = pending ?: return@withLock state.value
        if (now() - challenge.createdAt !in 0..150_000 || challenge.attempts >= 3) {
            pending = null
            return@withLock publish(CampusStatus.EXPIRED, "短信验证已失效，请重新登录")
        }
        if (code.isBlank()) return@withLock state.value.copy(message = "请输入短信验证码").also { mutableState.value = it }
        pending = challenge.copy(attempts = challenge.attempts + 1)
        interactive = true
        try {
            val result = jsonRequest("https://$CAS_HOST/attest/api/guard/securephone/valid", buildJsonObject {
                put("gid", challenge.gid); put("code", code.trim())
            })
            val status = result.obj("data")?.string("status")
            if (result.int("code") != 0 || status != null && status != "2") {
                if (pending!!.attempts >= 3) {
                    pending = null
                    return@withLock publish(CampusStatus.EXPIRED, "短信验证次数已用尽，请重新登录")
                }
                return@withLock publishMfa(pending!!, "验证码错误或已失效，请重试")
            }
            pending = null
            publish(CampusStatus.AUTHENTICATING)
            val fields = challenge.fields.toMutableMap()
            val reply = transport.request(
                challenge.url, HttpMethod.Post, fields.toList().let { formBody(*it.toTypedArray()) },
                ContentType.Application.FormUrlEncoded, referer = challenge.url,
                credentialPost = challenge.flow == CampusMfaFlow.MFA_DETECT,
            )
            consume(challenge.site, reply, challenge.remaining)
            continueSites(challenge.remaining)
            authenticated()
            state.value
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: CampusMfaRequiredException) { state.value }
        catch (failure: CampusProtocolException) {
            val current = pending
            if (current == null || current.attempts >= 3) {
                pending = null
                publish(CampusStatus.EXPIRED, "短信验证未完成，请重新登录")
            } else publishMfa(current, failure.message ?: "验证失败，请重试")
        }
    }

    suspend fun logout() = mutex.withLock {
        sessionGeneration++
        accountStore()?.clear()
        store.remove("active-account")
        username = null; password = null; tokens.clear(); ready.clear(); transport.clear(); pending = null
        schedule.clearAccountState(); fingerprint = visitorId()
        publish(CampusStatus.LOGGED_OUT)
        Unit
    }
    fun close() = transport.close()

    private fun sites(): List<CampusSite> = if (transport.route == CampusRoute.WEBVPN) CampusSite.entries else listOf(CampusSite.SCHEDULE, CampusSite.SCORES)
    private suspend fun authenticateAll() {
        continueSites(sites())
        authenticated()
    }
    private suspend fun continueSites(sites: List<CampusSite>) {
        for ((index, site) in sites.withIndex()) {
            if ((transport.route to site) !in ready) authenticateSite(site, sites.drop(index + 1))
        }
    }
    private suspend fun ensureSite(site: CampusSite) {
        if (username == null || password == null) expired()
        if (transport.route == CampusRoute.WEBVPN && transport.route to CampusSite.GATEWAY !in ready) authenticateSite(CampusSite.GATEWAY, listOf(site))
        if (transport.route to site !in ready) authenticateSite(site, emptyList())
    }

    private suspend fun authenticateSite(site: CampusSite, remaining: List<CampusSite>) {
        if (username == null || password == null) expired()
        val reply = transport.request(site.entry)
        consume(site, reply, remaining)
    }

    private suspend fun consume(site: CampusSite, initial: CampusReply, remaining: List<CampusSite>) {
        var reply = initial
        var credentialSubmitted = false
        var accountChosen = false
        var retriedEntry = false
        repeat(6) {
            val doc = Ksoup.parse(reply.body)
            if (isSafetyVerify(doc, reply.body)) {
                val form = doc.selectFirst("#fm1") ?: throw CampusProtocolException("二次验证页面格式错误")
                val secState = form.selectFirst("input[name=secState]")?.attr("value").orEmpty()
                val execution = form.selectFirst("input[name=execution]")?.attr("value").orEmpty()
                if (secState.isEmpty() || execution.isEmpty()) throw CampusProtocolException("二次验证页面缺少状态")
                val phone = phone("/cas/sec/initByType/securephone", secState)
                val fields = mapOf(
                    "secState" to secState, "execution" to execution,
                    "_eventId" to (form.selectFirst("input[name=_eventId]")?.attr("value").orEmpty().ifEmpty { "submit" }),
                    "submit" to (form.selectFirst("input[name=submit]")?.attr("value").orEmpty().ifEmpty { "Login1" }),
                    "geolocation" to "", "fpVisitorId" to fingerprint,
                )
                pending = PendingMfa(CampusMfaFlow.SAFETY_VERIFY, site, reply.url, fields, phone.first, phone.second, true, remaining, now())
                requireMfa(pending!!)
            }
            val plain = transport.logical(reply.url)
            val execution = doc.selectFirst("input[name=execution]")?.attr("value").orEmpty()
            val choices = accountChoices(doc)
            if (plain.host == CAS_HOST && choices.isNotEmpty() && !accountChosen) {
                val choice = choices.firstOrNull { it.first.contains("本科") } ?: choices.first()
                reply = postForm(reply.url, mapOf("execution" to execution, "_eventId" to "submit", "username" to choice.second,
                    "geolocation" to "", "fpVisitorId" to fingerprint, "trustAgent" to "", "useDefault" to "false"))
                accountChosen = true
                return@repeat
            }
            if (site == CampusSite.GATEWAY && transport.isWebVpnGatewayReady(reply)) {
                ready += transport.route to site
                persist()
                return
            }
            if (reply.status in 400..599) throw CampusProtocolException(if (reply.status == 401) "账号或密码错误" else "校园认证暂时不可用")
            if (plain.host == CAS_HOST && plain.encodedPath == "/cas/login" && execution.isNotEmpty()) {
                if (credentialSubmitted) throw CampusProtocolException("账号或密码错误，或需要在统一认证页面完成验证")
                if (requiresCaptcha(doc)) throw CampusProtocolException("统一认证要求图形验证码，请在校园认证页面处理后重试")
                val key = transport.request("https://$CAS_HOST/cas/jwt/publicKey")
                if (key.status != 200) throw CampusProtocolException("无法获取认证公钥")
                val encrypted = CampusRsa.encryptPassword(password ?: throw CampusSessionExpiredException(), key.body)
                var mfaState = ""
                val enabled = Regex("mfaEnabled\\s*[:=]\\s*(true|false)", RegexOption.IGNORE_CASE)
                    .find(reply.body)?.groupValues?.get(1)?.toBooleanStrictOrNull() ?: true
                if (enabled) {
                    val detect = parseObject(postForm("https://$CAS_HOST/cas/mfa/detect", mapOf(
                        "username" to username.orEmpty(), "password" to encrypted, "fpVisitorId" to fingerprint,
                    ), credentials = true).body)
                    val data = detect.obj("data") ?: throw CampusProtocolException("认证验证状态无效")
                    mfaState = data.string("state").orEmpty()
                    if (data.bool("need")) {
                        val phone = phone("/cas/mfa/initByType/securephone", mfaState)
                        val fields = credentialFields(doc, execution, encrypted, mfaState, true)
                        pending = PendingMfa(CampusMfaFlow.MFA_DETECT, site, reply.url, fields, phone.first, phone.second, false, remaining, now())
                        requireMfa(pending!!)
                    }
                }
                reply = postForm(reply.url, credentialFields(doc, execution, encrypted, mfaState, false), credentials = true)
                credentialSubmitted = true
                return@repeat
            }
            if (site != CampusSite.GATEWAY && plain.host == site.host && reply.status in 200..299) {
                if (isAuthFailure(reply)) expired()
                if (site == CampusSite.SCHEDULE) {
                    val validation = transport.request("https://$JWXT_HOST/jwapp/sys/wdkb/modules/jshkcb/dqxnxq.do", HttpMethod.Post,
                        "", ContentType.Application.FormUrlEncoded, referer = site.entry)
                    if (isAuthFailure(validation)) expired()
                    val rows = parseObject(validation.body).obj("datas")?.obj("dqxnxq")?.array("rows")
                    val term = (rows?.firstOrNull() as? JsonObject)?.string("DM")
                    if (validation.status !in 200..299 || term?.matches(Regex("\\d{4}-\\d{4}-[123]")) != true) {
                        throw CampusProtocolException("教务登录校验失败")
                    }
                }
                if (site == CampusSite.SCORES) {
                    val token = plain.parameters["token"] ?: tokens[transport.route]
                    if (token.isNullOrBlank()) throw CampusProtocolException("移动教务未返回登录令牌")
                    tokens[transport.route] = token
                    val validation = transport.request("https://$JWAPP_HOST/api/biz/v410/common/school/time", token = token)
                    if (isAuthFailure(validation)) expired()
                    val json = parseObject(validation.body)
                    if (validation.status !in 200..299 || json.int("code") != 200) throw CampusProtocolException("移动教务登录校验失败")
                }
                ready += transport.route to site
                persist()
                return
            }
            if (!retriedEntry && site != CampusSite.GATEWAY && plain.host != CAS_HOST) {
                retriedEntry = true; reply = transport.request(site.entry); return@repeat
            }
            if (!retriedEntry && plain.host == CAS_HOST && execution.isEmpty()) {
                retriedEntry = true; reply = transport.request(site.entry); return@repeat
            }
            throw CampusProtocolException("校园认证未完成，请重新登录")
        }
        throw CampusProtocolException("校园认证流程未完成")
    }

    private fun credentialFields(doc: Document, execution: String, encrypted: String, mfaState: String, mfa: Boolean): Map<String, String> = mapOf(
        "username" to username.orEmpty(), "password" to encrypted, "execution" to execution, "_eventId" to "submit",
        "submit1" to "Login1", "fpVisitorId" to fingerprint, "captcha" to "", "currentMenu" to "1",
        "failN" to doc.selectFirst("input[name=failN]")?.attr("value").orEmpty(),
        "mfaState" to mfaState, "geolocation" to "", "trustAgent" to if (mfa) "true" else "",
    )
    private suspend fun phone(path: String, state: String): Pair<String, String> {
        if (state.isBlank()) throw CampusProtocolException("二次验证状态为空")
        val url = URLBuilder("https://$CAS_HOST$path").apply { parameters.append("state", state) }.buildString()
        val result = parseObject(transport.request(url).body)
        requireCode(result, 0, "无法获取绑定手机号")
        val data = result.obj("data") ?: throw CampusProtocolException("绑定手机号响应无效")
        val gid = data.string("gid")?.takeIf(String::isNotBlank) ?: throw CampusProtocolException("二次验证标识为空")
        val phone = data.string("securePhone").orEmpty()
        // Never expose an accidentally unmasked phone from upstream.
        val masked = if (phone.length >= 7 && phone.all(Char::isDigit)) phone.take(3) + "****" + phone.takeLast(4) else phone
        return gid to masked
    }
    private suspend fun postForm(url: String, fields: Map<String, String>, credentials: Boolean = false): CampusReply =
        transport.request(url, HttpMethod.Post, formBody(*fields.toList().toTypedArray()), ContentType.Application.FormUrlEncoded, referer = url, credentialPost = credentials)
    private suspend fun jsonRequest(url: String, data: JsonObject): JsonObject =
        parseObject(transport.request(url, HttpMethod.Post, data.toString(), ContentType.Application.Json).body)
    private fun requireCode(result: JsonObject, code: Int, message: String) {
        if (result.int("code") != code) throw CampusProtocolException(message)
    }
    private fun requireMfa(challenge: PendingMfa): Nothing {
        if (interactive) publishMfa(challenge) else {
            pending = null
            publish(CampusStatus.EXPIRED, "登录已过期，需要手动完成二次验证")
        }
        throw CampusMfaRequiredException()
    }
    private fun publishMfa(challenge: PendingMfa, message: String = "请完成短信验证"): CampusSessionState {
        val updated = CampusSessionState(CampusStatus.SMS_REQUIRED, username, transport.route, message,
            challenge.phone, challenge.alreadySent, challenge.flow)
        mutableState.value = updated
        return updated
    }
    private suspend fun guarded(block: suspend () -> Unit): CampusSessionState = try {
        block(); state.value
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (_: CampusMfaRequiredException) { state.value }
    catch (_: CampusSessionExpiredException) { publish(CampusStatus.EXPIRED, "登录已过期，请重新登录") }
    catch (failure: CampusProtocolException) { publish(CampusStatus.ERROR, failure.message ?: "校园认证失败") }
    catch (_: Exception) { publish(CampusStatus.ERROR, "校园认证响应格式无效") }
    private fun publish(status: CampusStatus, message: String = ""): CampusSessionState =
        CampusSessionState(status, username, transport.route, message).also { mutableState.value = it }
    private fun authenticated() { persist(); publish(CampusStatus.AUTHENTICATED) }
    private fun persist() {
        val account = username ?: return
        val secret = password ?: return
        store.putString("active-account", account)
        accountStore(account).putString("session", campusJson.encodeToString(StoredCampusSession(
            account, secret, transport.route, fingerprint, transport.snapshot(), tokens.toMap(), now(),
        )))
    }
    private fun accountStore(account: String): KeyValueStore = ScopedCampusStore(store, "account:$account")
    private fun accountStore(): KeyValueStore? = username?.let(::accountStore)
    private fun expired(): Nothing {
        ready.removeAll { it.first == transport.route }
        publish(CampusStatus.EXPIRED, "登录已过期，请重新登录")
        throw CampusSessionExpiredException()
    }

    internal suspend fun apiRequest(site: CampusSite, url: String, method: HttpMethod = HttpMethod.Get,
        body: String? = null, type: ContentType? = null): CampusReply = mutex.withLock {
        interactive = false
        try {
            ensureSite(site)
            repeat(2) { attempt ->
                val reply = transport.request(url, method, body, type, tokens[transport.route].takeIf { site == CampusSite.SCORES }, site.entry)
                if (!isAuthFailure(reply)) {
                    if (reply.status !in 200..299) throw CampusProtocolException("校园服务请求失败（${reply.status}）")
                    persist()
                    return@withLock reply
                }
                ready.remove(transport.route to site)
                if (transport.route == CampusRoute.WEBVPN && isWebVpnLogin(reply)) ready.remove(transport.route to CampusSite.GATEWAY)
                if (site == CampusSite.SCORES) tokens.remove(transport.route)
                publish(CampusStatus.EXPIRED, "登录已过期，正在重新认证")
                if (attempt == 0) { ensureSite(site); publish(CampusStatus.AUTHENTICATED) } else expired()
            }
            expired()
        } catch (_: CampusMfaRequiredException) { expired() }
    }
}

internal fun parseObject(body: String): JsonObject = try { campusJson.parseToJsonElement(body).jsonObject }
catch (_: Exception) { throw CampusProtocolException("校园服务响应格式无效") }
internal fun JsonObject.string(key: String): String? = (get(key) as? JsonPrimitive)?.contentOrNull
internal fun JsonObject.int(key: String): Int? = string(key)?.toIntOrNull()
internal fun JsonObject.bool(key: String): Boolean = string(key)?.let { it.equals("true", true) || it == "1" } ?: false
internal fun JsonObject.obj(key: String): JsonObject? = get(key) as? JsonObject
internal fun JsonObject.array(key: String): JsonArray? = get(key) as? JsonArray
internal fun isSafetyVerify(doc: Document, html: String): Boolean =
    doc.selectFirst("#fm1 input[name=secState]") != null && (doc.title().contains("Safety Verify", true) ||
        html.contains("/cas/sec/initByType") || html.contains("选择安全认证") || html.contains("二次认证"))
private fun requiresCaptcha(doc: Document): Boolean = doc.selectFirst("input[name=captcha][required]") != null ||
    doc.select("img[src*=captcha], img#captchaImg").any { !it.attr("style").contains("display:none", true) && !it.attr("style").contains("display: none", true) }
private fun accountChoices(doc: Document): List<Pair<String, String>> {
    val modern = doc.select("div.account-wrap").mapNotNull {
        val value = it.selectFirst("el-radio.checkbox-radio")?.attr("label").orEmpty()
        if (value.isBlank()) null else it.selectFirst("div.name")?.text().orEmpty() to value
    }
    return modern.ifEmpty { doc.select("input[name=username][type=radio], input[name=username][type=hidden]").mapNotNull {
        it.attr("value").takeIf(String::isNotBlank)?.let { value -> it.parent()?.text().orEmpty() to value }
    } }
}
internal fun isWebVpnLogin(reply: CampusReply): Boolean = Url(reply.url).let {
    it.host == VPN_HOST && it.encodedPath.trimEnd('/') == "/login"
}
internal fun isAuthFailure(reply: CampusReply): Boolean {
    if (reply.status in setOf(401, 403) || isWebVpnLogin(reply)) return true
    val html = reply.body
    if (html.trimStart().startsWith('<')) {
        val doc = Ksoup.parse(html)
        return isSafetyVerify(doc, html) || doc.selectFirst("form#fm1 input[name=execution]") != null ||
            doc.selectFirst("input[name=password]") != null || Url(reply.url).host == CAS_HOST
    }
    val json = runCatching { campusJson.parseToJsonElement(html) as? JsonObject }.getOrNull() ?: return false
    val message = json.string("msg") ?: json.string("message").orEmpty()
    return message.contains("Authentication error", true) || message.contains("token过期", true) ||
        json.int("code") == 401 && (message.contains("authentication", true) || message.contains("未登录") || message.contains("过期"))
}
