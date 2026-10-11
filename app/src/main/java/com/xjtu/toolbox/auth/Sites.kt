package com.xjtu.toolbox.auth

import com.xjtu.toolbox.util.stringValue
import com.xjtu.toolbox.util.intValue
import com.xjtu.toolbox.util.isNull
import android.util.Log
import com.xjtu.toolbox.util.safeParseJsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

// ─────────────────────────────────────────────────────────────────────
//  13 个业务子系统的 SiteSession 实现。
//
//  设计原则：
//  - 每个子类内部仍然依赖一个 *Login 实例完成 CAS 登录与局部 token 抽取，
//    这是过渡期复用——XJTULogin 状态机本身已经成熟，无须重写。
//  - 局部 token / 标识在 onLoginSuccess 钩子里写入 SiteSession.localToken 供业务层读取。
//  - 业务 API 类只接 SiteSession，不持有 *Login，便于后续整体替换。
// ─────────────────────────────────────────────────────────────────────

// ── JWXT 教务系统 ─────────────────────────────────────────────────────
//
// `JwxtSession` 与它的 `JwxtLogin` 已搬进 `:data`（同一个包、同一个类名）：教务是「两条真数据路由
// 共用同一个站点」（全校课表 + 成绩报表），桌面端要用它自己登录。类名与包路径都没变 ⇒
// `AppLoginState` / `HeadlessSessions` 里那两处 `register(JwxtSession())` 一行不用改。

// ── JWAPP 移动教务系统 ───────────────────────────────────────────────

// mustUseWebVpn=false：永远直连原域名。CAS 入口走 org.xjtu.edu.cn 开放平台，若该入口
// 与 jwapp.xjtu.edu.cn 本身对公网开放，校外可直连；若学校收紧访问，需连校园官方 VPN。
class JwappSession : CasSiteSession("jwapp", "移动教务", mustUseWebVpn = false) {
    override fun createLogin(client: OkHttpClient, visitorId: String?, cachedRsaKey: String?): XJTULogin =
        JwappLogin(session = client, visitorId = visitorId, cachedRsaKey = cachedRsaKey)

    override fun onLoginSuccess(login: XJTULogin) {
        (login as? JwappLogin)?.authToken?.takeIf { it.isNotEmpty() }?.let {
            localToken["auth_token"] = it
        }
    }

    override fun decorateRequest(builder: Request.Builder): Request.Builder {
        localToken["auth_token"]?.let { builder.header("Authorization", it) }
        return builder
    }

    override suspend fun validateLogin(): Boolean = withIo {
        val token = localToken["auth_token"] ?: return@withIo false
        val resp = client.newCall(
            Request.Builder()
                .url("https://jwapp.xjtu.edu.cn/api/biz/v410/common/school/time")
                .header("Authorization", token)
                .get().build()
        ).execute()
        try {
            resp.code == 200
        } finally { resp.close() }
    }

    override fun isAuthFailureResponse(response: Response, bodyPreview: String?): Boolean {
        if (super.isAuthFailureResponse(response, bodyPreview)) return true
        val body = bodyPreview ?: return false
        // 分项成绩接口对「这门课没有细则」也会给 JSON code=401，不能单凭数字当掉登录。
        return body.contains("Authentication error", ignoreCase = true) ||
            (body.contains("token", ignoreCase = true) && body.contains("过期")) ||
            (""""code"\s*:\s*401""".toRegex().containsMatchIn(body) &&
                (body.contains("authentication", ignoreCase = true) ||
                    body.contains("未登录") ||
                    body.contains("过期")))
    }
}

// ── YWTB 一网通办 ─────────────────────────────────────────────────────
//
// `YwtbSession` 与它的 `YwtbLogin` 已搬进 `:data`（同一个包、同一个类名）：一网通办是消息收纳
// 「四路取数共用一个站点」那一条路由要用的站点（`:data` 的 `AppInboxSource` 自己 `ensureSite`），
// 桌面端要自己登它、自己去那四路取数。类名与包路径都没变 ⇒ 下面 `AppLoginState` 里那处
// `register(YwtbSession())` 一行不用改；登录成功那颗令牌改由 SessionManager 的宿主槽位
// `onYwtbToken` 交出去（`:app` 那一行仍然写 `CampusProbe.ywtbToken`，行为逐字不变）。

// ── LIBRARY 图书馆座位 ────────────────────────────────────────────────
// `LibrarySession` 已搬进 `:data`（同一个类名、同一个包）：它是「Sites.kt 里的站点逐个接上
// `:data`」的第一个 —— 它只用到 `CasSiteSession` + `LibraryLogin` + `WebVpnUtil`，这三样
// 现在都在 `:data` 里，于是桌面端可以直接拿它登录（`docs/desktop-port-plan.md` Stage A）。
// 类名与包路径都没变 ⇒ 下面 AppLoginState / HeadlessSessions 的 `register(...)` 一行不用改。

// ── LMS 思源学堂 ─────────────────────────────────────────────────────
// `LmsSession` 与它的 `LmsLogin` 已搬进 `:data`（同一个包、同一个类名）：思源学堂是
// 「桌面端第 18 条真数据路由」，桌面要自己登 lms、自己取课程/活动/详情。类名与包路径都
// 没变 ⇒ `AppLoginState` / `HeadlessSessions` 里那两处 `register(LmsSession())`、
// `LmsDeadlineWorker` / `OwnInbox` / `DownloadManagerScreen` / `ProfileTab` 里那些取数调用点，
// 一行都不用改。

// ── ICLASSFACE 人脸识别签到 ──────────────────────────────────────────────

class IclassfaceSession : CasSiteSession("iclassface", "快速考勤流水", mustUseWebVpn = true) {
    override fun createLogin(client: OkHttpClient, visitorId: String?, cachedRsaKey: String?): XJTULogin =
        com.xjtu.toolbox.iclassface.IclassfaceLogin(session = client, visitorId = visitorId, cachedRsaKey = cachedRsaKey)
}

// ── ATTENDANCE 考勤 kq.xjtu.edu.cn ────────────────────────────────
//
// `AttendanceSession` 与它的 `AttendanceLogin` 已搬进 `:data`（同一个包、同一个类名）：考勤是
// 「桌面端第 16 条真数据路由」，桌面要自己登 `kq`、自己取考勤流水 / 请假 / 课表。类名与包路径都
// 没变 ⇒ `AppLoginState` / `HeadlessSessions` 里那两处 `register(AttendanceSession())`、
// `AgentTool` / `HomeStatsRefresher` / `CourseLinks` / `ScheduleSource` 里那些取数调用点，
// 一行都不用改。

// ── HELLO 迎新/个人信息 ────────────────────────────────────────────────

/**
 * hello.xjtu.edu.cn。凭据是登录落地 URL 上的 JWT，不是 cookie，所以必须在
 * [onLoginSuccess] 里把它转存到 localToken 供 [com.xjtu.toolbox.hello.HelloApi] 取用。
 */
class HelloSession : CasSiteSession("hello", "个人信息", mustUseWebVpn = true) {
    override fun createLogin(client: OkHttpClient, visitorId: String?, cachedRsaKey: String?): XJTULogin =
        com.xjtu.toolbox.hello.HelloLogin(session = client, visitorId = visitorId, cachedRsaKey = cachedRsaKey)

    override fun onLoginSuccess(login: XJTULogin) {
        val hello = login as? com.xjtu.toolbox.hello.HelloLogin
        if (hello == null) {
            android.util.Log.w("HelloSession", "onLoginSuccess: unexpected login type ${login.javaClass.name}")
            return
        }
        hello.accessToken.takeIf { it.isNotBlank() }?.let { localToken["access_token"] = it }
        localToken["system_type"] = hello.systemType
        android.util.Log.d(
            "HelloSession",
            "onLoginSuccess: tokenLen=${hello.accessToken.length} stored=${localToken.containsKey("access_token")} keys=${localToken.keys}"
        )
    }

    // JWT 必须由会话层注入：HelloApi 若在建请求时写死 token，executeWithReAuth
    // 重登后重放的仍是旧头，新令牌用不上。
    override fun decorateRequest(builder: Request.Builder): Request.Builder {
        localToken["access_token"]?.let { token ->
            builder.header("access-token", token)
            builder.header("access_token", token)
        }
        builder.header("systemtype", localToken["system_type"] ?: "yingxin_student_pc")
        builder.header("synAccessSource", "pc")
        return builder
    }

    // 过期 JWT 仍回 HTTP 200 + JSON state!=200，基类只认 401/CAS 页，探活必须读 state。
    override suspend fun validateLogin(): Boolean = withIo {
        val token = localToken["access_token"] ?: return@withIo false
        val resp = client.newCall(
            Request.Builder()
                .url("${com.xjtu.toolbox.hello.HelloLogin.BASE_URL}/yingxin/user/afterLogin?synAccessSource=pc")
                .header("access-token", token)
                .header("access_token", token)
                .header("systemtype", localToken["system_type"] ?: "yingxin_student_pc")
                .header("synAccessSource", "pc")
                .header("Referer", "${com.xjtu.toolbox.hello.HelloLogin.BASE_URL}/yingxin-pc/")
                .get()
                .build()
        ).execute()
        try {
            if (resp.code != 200) return@withIo false
            val body = resp.body.string()
            if (XJTULogin.isAuthFailureResponse(body)) return@withIo false
            runCatching { body.safeParseJsonObject().get("state")?.intValue }.getOrNull() == 200
        } finally {
            resp.close()
        }
    }

    override fun isAuthFailureResponse(response: Response, bodyPreview: String?): Boolean {
        if (super.isAuthFailureResponse(response, bodyPreview)) return true
        val body = bodyPreview ?: return false
        val json = runCatching { body.safeParseJsonObject() }.getOrNull() ?: return false
        val state = json.get("state")?.takeIf { !it.isNull }
            ?.runCatching { intValue }?.getOrNull() ?: return false
        if (state == 200) return false
        val path = response.request.url.encodedPath
        if ("/yingxin/user/" in path) return true
        val message = json.get("message")?.takeIf { !it.isNull }?.stringValue.orEmpty()
        return state == 401 || state == 403 ||
            message.contains("未登录") ||
            message.contains("过期") ||
            message.contains("token", ignoreCase = true)
    }
}

// ── JIAOCAI 教材中心 ──────────────────────────────────────────────────

class JiaocaiSession : CasSiteSession("jiaocai", "教材中心", mustUseWebVpn = true) {
    override fun createLogin(client: OkHttpClient, visitorId: String?, cachedRsaKey: String?): XJTULogin =
        com.xjtu.toolbox.jiaocai.JiaocaiLogin(existingClient = client, visitorId = visitorId, cachedRsaKey = cachedRsaKey)
}

// ── COUPON 餐券 ──────────────────────────────────────────────────────
//
// `CouponSession` 与它的 `CouponLogin` 已搬进 `:data`（同一个包、同一个类名）：加餐券是
// 「桌面端第 15 条真数据路由」，桌面要自己登 `egc.xjtu.edu.cn`、自己查券与领券
//（`:data` 的 `AppCouponSource` 包住原来的 `CouponApi`）。类名与包路径都没变 ⇒
// 下面 `AppLoginState` 里那处 `register(CouponSession())`、以及 `AgentTool` / `HomeStatsRefresher`
// 里那两处 `CouponApi(site)`，一行都不用改。

// ── DZPZ 电子凭证（成绩单） ───────────────────────────────────────────
//
// `DzpzSession` 与它的 `DzpzLogin` 已搬进 `:data`（同一个包、同一个类名）：成绩单是
// 「桌面端第 14 条真数据路由」，桌面要自己登 `dzpz`、自己走完生成-提交-签章-下载七步
//（`:data` 的 `AppTranscriptSource` 包住原来的 `TranscriptApi`）。类名与包路径都没变 ⇒
// 下面 `AppLoginState` 里那处 `register(DzpzSession())` 一行不用改。

// ── VENUE 场馆预订 ─────────────────────────────────────────────
//
// `VenueSession` 与它的 `VenueLogin` 已搬进 `:data`（同一个包、同一个类名，共 3 行）：场馆是
// 「一条真数据路由一个站点」（体育场馆预订），桌面端要自己登场馆站、自己取数。类名与包路径都没变 ⇒
// `AppLoginState` 里那处 `register(VenueSession())` 一行不用改。

// ── SSN 宿舍电费 ──────────────────────────────────────────────────────
//
// `SsnSession` 与它的 `SsnLogin` 已搬进 `:data`（同一个包、同一个类名）：宿舍电费是「桌面端第 17 条
// 真数据路由」，而且**它有自己的独立登录链** —— `org.xjtu.edu.cn` 开放平台的 OAuth2（appId=1759）→
// CAS → 带 `code` 回 `/cems/index/mobile/pay`，站点点下会话、页面 HTML 里内嵌 `cid`，不借校园卡 /
// 教务的会话。取数也是 `:data` 的 `DormPowerApi`（实现 `:core` 的 `DormPowerSource` 端口）。
// 类名与包路径都没变 ⇒ `AppLoginState` / `HeadlessSessions` 里那两处 `register(SsnSession())`
// 一行不用改。

// ── CAMPUS CARD 校园卡 ───────────────────────────────────────────────
// `CampusCardSession` 与它的 `CampusCardLogin` 已搬进 `:data`（同一个包、同一个类名）：校园卡是
// 「桌面端第 9 条真数据路由」，桌面要自己登 ncard、自己取卡面与流水（`:data` 的 `AppCampusCardSource`）。
// 类名与包路径都没变 ⇒ 下面 `AppLoginState` 里那处 `register(CampusCardSession())` 一行不用改。

// ── GSTE 研究生评教 / GMIS 研究生管理信息系统 ─────────────────────────
//
// `GsteSession` / `GmisSession` 与它们共用的 `LandingCasLogin` 已搬进 `:data`（同一个包、同一个
// 类名）：研究生评教那一条路由在桌面上要它自己登录（`:data` 的 `GraduateJudgeApi` + `GraduateJudgeSource`）。
// 类名与包路径都没变 ⇒ 下面 `AppLoginState` 里那两处 `register(...)` 一行不用改。

// ── 智慧教室平台 js.xjtu.edu.cn（空闲教室实时状态） ──────────────
//
// `JsSession` 与它的 `JsLogin` 已搬进 `:data`（同一个包、同一个类名）：空闲教室的「实时状态」
// 那一档是「桌面端第 10 条真数据路由」要用的站点（`:data` 的 `AppEmptyRoomSource` 自己
// `ensureSite("js")`），桌面端因此不需要为它做别的装配。类名与包路径都没变 ⇒ 下面 `AppLoginState`
// 里那处 `register(JsSession())`、以及 `AgentTool` 里按 `JsSession.SITE_KEY` 取会话的调用点
// 一行不用改。

// ─────────────────────────────────────────────────────────────────────
//  辅助
// ─────────────────────────────────────────────────────────────────────

private suspend inline fun <T> withIo(crossinline block: () -> T): T =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { block() }
