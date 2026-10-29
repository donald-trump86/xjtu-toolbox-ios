package com.xjtu.toolbox.campus

import io.ktor.client.engine.mock.*
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.*
import io.ktor.http.content.OutgoingContent
import kotlin.test.*

/**
 * Synthetic fixtures adapted from the repository's GPL-3.0 testkit:
 * testkit/src/main/kotlin/com/xjtu/toolbox/{library/LibraryFakeUpstream.kt,
 * library/CasSafetyVerifyPage.kt,jwxt/JwxtFakeUpstream.kt,jwapp/JwappFakeUpstream.kt}.
 * These model the upstream formats; they are not a capture of a school account or live-network verification.
 */
internal class CampusFakeSchool {
    var detectMfa = false
    var safetyMfa = false
    var invalidSessions = false
    var expireScheduleOnce = false
    var alwaysExpireSchedule = false
    var scheduleLoginWall = false
    var scheduleValidationWall = false
    var gatewayLandingStatus: Int? = null
    var gatewayTicket: String? = "fixture-vpn"
    var gatewayExpired = false
    var alwaysExpireGateway = false
    var gatewayLoginWall = false
    var gatewayLoginCalls = 0
    var noDetailCode = 200
    var detailGpa = 3.9
    var phoneCalls = 0
    var sendCalls = 0
    var verifyCalls = 0
    var credentialPosts = 0
    var safetyPosts = 0
    var scheduleCalls = 0
    var verified = false
    var credentialCheck: ((String) -> Unit)? = null
    val requests = mutableListOf<HttpRequestData>()
    private var lastCasUrl: String? = null
    val engine = MockEngine { request ->
        requests += request
        val url = CampusWebVpnCodec.fromVpnUrl(request.url.toString())?.let(::Url) ?: request.url
        val body = (request.body as? OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString().orEmpty()
        val form = parseQueryString(body)
        val cookie = request.headers[HttpHeaders.Cookie].orEmpty()
        fun redirect(location: String, setCookie: String? = null): HttpResponseData = respond("", HttpStatusCode.Found,
            Headers.build { append(HttpHeaders.Location, location); if (setCookie != null) append(HttpHeaders.SetCookie, setCookie) })
        fun json(value: String): HttpResponseData = respond(value, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        fun html(value: String, setCookie: String? = null): HttpResponseData = respond(value, headers = Headers.build {
            append(HttpHeaders.ContentType, "text/html; charset=utf-8"); if (setCookie != null) append(HttpHeaders.SetCookie, setCookie)
        })
        fun appendToken(target: String, key: String, value: String): String = URLBuilder(target).apply { parameters.append(key, value) }.buildString()
        fun cas(target: String): HttpResponseData = redirect(URLBuilder("https://$CAS_HOST/cas/login").apply { parameters.append("service", target) }.buildString())
        if (url.host == JWXT_HOST && url.encodedPath.endsWith("/xskcb.do")) {
            scheduleCalls++; assertEquals("2026-2027-1", form["XNXQDM"])
        }
        when {
            request.url.host == VPN_HOST && url.host in setOf(JWXT_HOST, JWAPP_HOST, "org.xjtu.edu.cn") &&
                (gatewayExpired || alwaysExpireGateway && url.encodedPath.endsWith("/xskcb.do")) -> {
                gatewayExpired = true
                redirect("https://$VPN_HOST/login")
            }
            url.host == VPN_HOST && url.encodedPath == "/" -> html("<html>VPN authenticated</html>")
            url.host == VPN_HOST && url.encodedPath == "/login" -> {
                val ticket = url.parameters["ticket"]
                if (url.parameters["cas_login"] == "true" && ticket == null) gatewayLoginCalls++
                if (gatewayLoginWall || url.parameters["cas_login"] != "true" && ticket == null) {
                    html("<html><a href=\"/login?cas_login=true\">统一身份认证</a></html>")
                } else if (ticket != null || !gatewayExpired && !gatewayTicket.isNullOrBlank() &&
                    "wengine_vpn_ticketwebvpn_xjtu_edu_cn=$gatewayTicket" in cookie) {
                    gatewayExpired = false
                    val landing = if (gatewayLandingStatus != null) CampusWebVpnCodec.toVpnUrl("https://$JWXT_HOST/webvpn/last-request")
                        else "https://$VPN_HOST/"
                    redirect(landing, gatewayTicket?.let { "wengine_vpn_ticketwebvpn_xjtu_edu_cn=$it; Path=/; Secure" })
                } else cas(url.toString())
            }
            url.host == JWXT_HOST && url.encodedPath == "/webvpn/last-request" ->
                respond("Last business response", HttpStatusCode.fromValue(gatewayLandingStatus ?: error("Missing landing status")))
            url.host == CAS_HOST && url.encodedPath == "/cas/jwt/publicKey" -> respond(CampusFixtureKey.PUBLIC)
            url.host == CAS_HOST && url.encodedPath == "/cas/mfa/detect" -> {
                assertTrue(form["password"].orEmpty().startsWith("__RSA__"))
                json("""{"code":0,"data":{"state":"detect-state-1","need":${detectMfa && !verified}}}""")
            }
            url.host == CAS_HOST && url.encodedPath in setOf("/cas/mfa/initByType/securephone", "/cas/sec/initByType/securephone") -> {
                assertEquals(if (url.encodedPath.contains("/sec/")) "sec-state-1" else "detect-state-1", url.parameters["state"])
                phoneCalls++
                json("""{"code":0,"data":{"gid":"fixture-gid","securePhone":"138****0000"}}""")
            }
            url.host == CAS_HOST && url.encodedPath == "/attest/api/guard/securephone/send" -> {
                assertTrue(phoneCalls > 0); assertEquals("fixture-gid", parseObject(body).string("gid"))
                sendCalls++; json("""{"code":0}""")
            }
            url.host == CAS_HOST && url.encodedPath == "/attest/api/guard/securephone/valid" -> {
                assertTrue(phoneCalls > 0); assertEquals("fixture-gid", parseObject(body).string("gid"))
                verifyCalls++
                if (parseObject(body).string("code") == "123456") {
                    verified = true; json("""{"code":0,"data":{"status":"2"}}""")
                } else json("""{"code":1,"message":"验证码错误"}""")
            }
            url.host == CAS_HOST && url.encodedPath == "/cas/login" && request.method == HttpMethod.Post -> {
                val service = url.parameters["service"] ?: error("fixture requires service")
                if (form["secState"] != null) {
                    assertEquals(lastCasUrl, url.toString(), "Safety Verify must POST the original URL, not #fm1 action")
                    assertEquals(url.toString(), request.headers[HttpHeaders.Referrer]?.let { CampusWebVpnCodec.fromVpnUrl(it) ?: it })
                    assertEquals("sec-state-1", form["secState"]); assertEquals("e2s1", form["execution"])
                    assertEquals("submit", form["_eventId"]); assertEquals("Login1", form["submit"])
                    assertEquals("", form["geolocation"]); assertFalse(form["fpVisitorId"].isNullOrBlank())
                    assertTrue(verified); assertNull(form["password"])
                    safetyPosts++
                    redirect(appendToken(service, "ticket", "ST-fixture"))
                } else {
                    assertEquals("fixture-user", form["username"])
                    assertTrue(form["password"].orEmpty().startsWith("__RSA__")); assertFalse(body.contains("fixture-password"))
                    credentialCheck?.invoke(form["password"].orEmpty())
                    assertEquals("e1s1", form["execution"])
                    credentialPosts++
                    if (detectMfa) { assertTrue(verified); assertEquals("detect-state-1", form["mfaState"]) }
                    if (safetyMfa && !verified) {
                        lastCasUrl = url.toString()
                        html(SAFETY_PAGE, "TGC=fixture-tgc; Path=/; Secure; HttpOnly")
                    } else redirect(appendToken(service, "ticket", "ST-fixture"), "TGC=fixture-tgc; Path=/; Secure; HttpOnly")
                }
            }
            url.host == CAS_HOST && url.encodedPath == "/cas/login" -> {
                if (!invalidSessions && "TGC=fixture-tgc" in cookie) {
                    redirect(appendToken(url.parameters["service"] ?: error("service"), "ticket", "ST-fixture"))
                } else html("""<html><script>var globalConfig={mfaEnabled:$detectMfa};</script><form id="fm1"><input name="execution" value="e1s1"><input name="password" type="password"></form></html>""")
            }
            url.host == JWXT_HOST && url.encodedPath == "/jwapp/sys/homeapp/index.do" -> {
                assertNull(request.headers[HttpHeaders.Authorization])
                if (scheduleLoginWall) html("<html><form><input name=\"password\" type=\"password\"></form></html>")
                else if (url.parameters["ticket"] != null) html("<html>教务主页</html>", "JSESSIONID=fixture-jwxt; Path=/; Secure; HttpOnly")
                else if (!invalidSessions && "JSESSIONID=fixture-jwxt" in cookie) html("<html>教务主页</html>")
                else cas(url.toString())
            }
            url.host == "org.xjtu.edu.cn" -> {
                assertNull(request.headers[HttpHeaders.Authorization])
                if (!invalidSessions && "TGC=fixture-tgc" in cookie || url.parameters["ticket"] != null) redirect("http://$JWAPP_HOST/app/index?token=fixture-token")
                else cas(url.toString())
            }
            url.host == JWAPP_HOST && url.encodedPath == "/app/index" -> html("<html>移动教务</html>")
            url.host == JWAPP_HOST && url.encodedPath.startsWith("/api/") -> {
                assertEquals("fixture-token", request.headers[HttpHeaders.Authorization])
                assertEquals(URLProtocol.HTTPS, url.protocol)
                when (url.encodedPath.substringAfterLast('/')) {
                    "time" -> json("""{"code":200,"data":{"xnxqdm":"2026-2027-1"}}""")
                    "termScore" -> { assertEquals("*", parseObject(body).string("termCode")); json(SCORES) }
                    "scoreDetail" -> if (parseObject(body).string("id") == "SCORE-NODETAIL") {
                        json("""{"code":$noDetailCode,"msg":"该课程暂无分项成绩","data":null}""")
                    } else json(DETAIL.replace("3.9", detailGpa.toString()))
                    else -> error("Unhandled mobile fixture")
                }
            }
            url.host == JWXT_HOST -> {
                assertNull(request.headers[HttpHeaders.Authorization])
                when (url.encodedPath.substringAfterLast('/')) {
                    "dqxnxq.do" -> if (scheduleValidationWall) html("<html><input name=\"password\" type=\"password\"></html>")
                        else json("""{"datas":{"dqxnxq":{"rows":[{"DM":"2026-2027-1","MC":"2026-2027学年 第一学期"}]}}}""")
                    "xskcb.do" -> {
                        if (alwaysExpireSchedule || expireScheduleOnce.also { expireScheduleOnce = false }) {
                            respond("expired", HttpStatusCode.Unauthorized)
                        } else json(SCHEDULE)
                    }
                    "xsdkkc.do" -> json(CHANGES)
                    "cxjcs.do" -> { assertEquals("2026-2027", form["XN"]); assertEquals("1", form["XQ"])
                        json("""{"datas":{"cxjcs":{"rows":[{"XQKSRQ":"2026-09-14 00:00:00","ZZC":"18"}]}}}""") }
                    "wdksap.do" -> json(EXAMS)
                    "xnxqcx.do" -> json("""{"datas":{"xnxqcx":{"rows":[{"DM":"2026-2027-1","MC":"2026-2027学年 第一学期"},{"DM":"2025-2026-2","MC":"2025-2026学年 第二学期"}]}}}""")
                    "index.do" -> html("<html>课表查询</html>")
                    else -> error("Unhandled schedule fixture: ${url.encodedPath}")
                }
            }
            else -> error("Unhandled synthetic fixture host")
        }
    }
    companion object {
        const val SAFETY_PAGE = """<html><head><title>统一身份认证 · Safety Verify</title></head><body><form id="fm1" action="/cas/sec/login"><input name="secState" value="sec-state-1"><input name="execution" value="e2s1"><input name="_eventId" value="submit"><input name="submit" value="Login1"></form><p>短信验证码已发送到绑定手机号</p></body></html>"""
        const val SCHEDULE = """{"datas":{"xskcb":{"rows":[{"JXBID":"JXB-8001","KCH":"MATH1001","KCM":"高等数学（上）","SKJS":"示例甲","JASMC":"主楼A-101","SKXQ":"1","KSJC":"1","JSJC":"2","SKZC":"1111111111111111"},{"JXBID":"JXB-8002","KCH":"PHYS1002","KCM":"大学物理","SKJS":"示例乙","JASMC":"中2-3201","SKXQ":"3","KSJC":"3","JSJC":"4","SKZC":"1010101010101010"}]}}}"""
        const val CHANGES = """{"datas":{"xsdkkc":{"rows":[{"TKLXDM":"1","JXBID":"JXB-8001","KCM":"高等数学（上）","KCH":"MATH1001","SKZC":"0000100000000000","SKXQ":"1","KSJC":"1","JSJC":"2","JASMC":"主楼A-101","YSKJS":"示例甲/0001","XSKZC":"0000100000000000","XSKXQ":"1","XKSJC":"1","XJSJC":"2","XJASMC":"主楼B-202","XSKJS":"示例甲/0001","SQSJ":"2026-09-28 09:00:00"}]}}}"""
        const val EXAMS = """{"datas":{"wdksap":{"rows":[{"KCM":"高等数学（上）","KCH":"MATH1001","KSRQ":"2027-01-15 00:00:00","KSSJMS":"2027-01-15 14:30-16:30","JASMC":"主楼A-101","ZWH":"27"}]}}}"""
        const val SCORES = """{"code":200,"data":{"termScoreList":[{"termCode":"2026-2027-1","termName":"2026-2027学年 第一学期","scoreList":[{"id":"SCORE-1","termCode":"2026-2027-1","courseName":"高等数学（上）(MATH1001)","score":"86","coursePoint":5.0,"examType":"考试","examProp":"必修","majorFlag":"1","replaceFlag":false,"passFlag":true,"specificReason":null}]}]}}"""
        const val DETAIL = """{"code":200,"data":{"courseName":"高等数学（上）","coursePoint":5.0,"examType":"考试","examProp":"必修","majorFlag":"1","replaceFlag":false,"score":"88","gpa":3.9,"passFlag":true,"specificReason":null,"itemList":[{"itemName":"平时","itemPercent":"30%","itemScore":"85"},{"itemName":"期末","itemPercent":"70%","itemScore":"90"}]}}"""
    }
}
