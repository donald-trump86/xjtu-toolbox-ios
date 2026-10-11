package com.xjtu.toolbox.server

import com.xjtu.toolbox.error.FriendlyError
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import kotlinx.coroutines.CancellationException

/**
 * 四个 `*Routes`（jwxt / library / card / venue）共用的两件小事。
 *
 * ## 失败都走 502
 *
 * 这些端点全部是「取数」：`code == 0` 成功，失败只有一种分法 —— **上游/网络的故障**（同样的
 * 请求稍后重试可能就成），所以统一 502（与 `ApiErrors.UPSTREAM_FAILED` 一条口径，先例是登录那
 * 一族的 502 分支）。`message` 一律由 `:core` 的 `FriendlyError` 给（中文短句、不含学号/姓名）。
 * `CancellationException` 照旧往外抛：客户端断了不该被当成「上游故障」。
 *
 * ## 会话与令牌是两件事
 *
 * 令牌闸门只证明「这一枚令牌对」；数据端点还要「**有会话**」—— 没有会话就拿不到站点，
 * 取数只会一路 502。所以每个端点进门先看 [ServeSession.authenticated]
 * （读它会触发幂等的冷启动恢复：落盘凭据还在就直接算有会话）。
 */

/** 取数失败（含会话过期）：502 + `FriendlyError` 的中文短句。 */
internal suspend fun ApplicationCall.respondUpstreamFailure(error: Throwable, what: String) {
    if (error is CancellationException) throw error
    respond(HttpStatusCode.BadGateway, ApiEnvelope<Unit>(ApiErrors.UPSTREAM_FAILED, message = FriendlyError.of(error, what)))
}

/** 令牌对、但没有会话：401 + 中文短句（与令牌闸门同一个 HTTP 码，message 指路）。 */
internal suspend fun ApplicationCall.respondLoginRequired() =
    respond(HttpStatusCode.Unauthorized, ApiEnvelope<Unit>(ApiErrors.UNAUTHORIZED, message = "请先登录：POST /api/session/login"))
/** 请求参数形状不对：400 + 中文短句。 */
internal suspend fun ApplicationCall.respondBadRequestMessage(message: String) =
    respond(HttpStatusCode.BadRequest, ApiErrors.badRequest(message))

/**
 * 收一个 JSON 请求体；形状不对（缺字段 / JSON 坏 / content-type 不是 JSON）给 null，
 * 由调用方回 400 + 中文短句。
 *
 * 为什么不让它抛：Ktor 默认会把反序列化失败变成**不带信封**的 400 —— 而契约 §4 说 `/api/…`
 * 的每一个响应都是信封。少了这一层，客户端在「我发错了」这种最该看文案的时候会拿到一个空响应体。
 * `CancellationException` 照旧往外抛（客户端断了不该被当成「请求体不对」）。
 *
 * 原先它是 [SessionRoutes] 里的一个 private 函数；`/api/library/action` 也要收请求体 ⇒ 搬家到这里
 *（"四个 `*Routes` 共用的几件小事"这一份）。
 */
internal suspend inline fun <reified T : Any> ApplicationCall.receiveOrNull(): T? = try {
    receive<T>()
} catch (e: CancellationException) {
    throw e
} catch (_: Exception) {
    null
}