package com.xjtu.toolbox.agent

import kotlinx.serialization.json.JsonObject

/**
 * 模型下发的一次工具调用。OpenAI 把 `id` / `function.name` / `function.arguments` 分片下发，
 * 【聚合之后】才长这样：三个字段都是完整的。
 */
class AgentToolCall(
    val id: String,
    val name: String,
    val arguments: String,
)

/**
 * 一次流式（SSE）请求的**聚合结果**：正文、思维链、工具调用、结束原因、总 token。
 *
 * 增量那部分不走这里 —— 它们由 [AgentTransport.stream] 当场回调给调用方（一个字一个字上屏），
 * 这个对象只承载「这一轮最后拿到了什么」。
 */
class AgentStreamResult(
    val content: String,
    val reasoningContent: String,
    val toolCalls: List<AgentToolCall>,
    val finishReason: String,
    val totalTokens: Long?,
)

/**
 * 「把一次 chat/completions 请求发出去、按 SSE 增量收回来」这**一格缝**。
 *
 * 为什么要缝：调度循环（[AgentRunner]）是纯逻辑 —— 拼请求体、聚合、跑工具、落历史、烧保险丝，
 * 一行网络代码都没有；而发请求这件事两端不是一个来路（Android 用 `:app` 的 okhttp 连接池、
 * 桌面用 `:data` 的网络栈）。搬进 `:core` 的循环不该替某一端决定用哪套 HTTP 客户端，
 * 所以把它收成这一个方法：**循环侧只要「给我一个请求体，我把这一轮的增量与结果给你」**。
 *
 * 实现方的责任（`SseAgentTransport` 那条口径，Android 与桌面共用同一份）：
 *  - 增量必须**边收边回调**（[onDelta] / [onReasoningDelta]），不是攒完再一次给；
 *  - 两个回调在 `Dispatchers.Main.immediate` 上执行（上屏的写法在 UI 线程）；
 *  - 协程被取消（用户点「停止」）时立刻断开连接，别让它在后台把 token 烧完；
 *  - HTTP 非 2xx 抛 `RuntimeException(agentHttpErrorText(code, 服务端原文))`，文案与设置页同源。
 */
interface AgentTransport {

    suspend fun stream(
        config: AgentConfig,
        reqBody: JsonObject,
        onDelta: suspend (String) -> Unit,
        onReasoningDelta: suspend (String) -> Unit,
    ): AgentStreamResult
}

/**
 * 调度循环要工具层做的三件事：报出工具清单、报出限流分组键、执行一次调用。
 *
 * 与 [AgentTransport] 同一条纪律：循环在 `:core`，而工具表里既有纯逻辑、也有取数、
 * 还有 Android 专属能力，两边各自收在实现方那一侧。
 */
interface AgentTools {

    /** OpenAI function calling 格式的工具描述（JSON 串，送给模型的那一份）。 */
    val toolDefinitions: String

    /** 这次调用打的是哪个后端系统（限流用）。null = 纯本地工具，不需要间隔。 */
    fun rateLimitKeyOf(toolName: String): String?

    /** 执行一次工具调用，返回给模型看的文本。失败就抛，由循环决定怎么记。 */
    suspend fun execute(name: String, argsJson: String): String
}

/**
 * 按 DeepSeek 错误码表转成能照着办的中文；400/422 等参数类错误保留服务端原文，便于排查。
 *
 * 消费者是 [AgentTransport] 的实现方（非 2xx 时抛的文案）。它跟着缝走而不是跟着某一份
 * okhttp 实现走：桌面与 Android 的失败提示必须逐字相同。
 */
fun agentHttpErrorText(code: Int, serverMessage: String): String = when (code) {
    401 -> "API Key 无效，请到屁岱设置里检查。"
    402 -> "AI 服务商账户余额不足，请先充值。"
    429 -> "请求太频繁，请稍后再试。"
    500, 502, 503 -> "AI 服务商暂时繁忙（HTTP $code），请稍后再试。"
    else -> "LLM 请求失败：$serverMessage"
}
