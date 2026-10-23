package com.xjtu.toolbox.agent

import com.xjtu.toolbox.network.HttpClients
import com.xjtu.toolbox.util.AppJson
import com.xjtu.toolbox.util.arr
import com.xjtu.toolbox.util.intValue
import com.xjtu.toolbox.util.isNull
import com.xjtu.toolbox.util.isObject
import com.xjtu.toolbox.util.longValue
import com.xjtu.toolbox.util.obj
import com.xjtu.toolbox.util.stringValue
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** [AgentTransport] 的实现。 */
class SseAgentTransport : AgentTransport {

    /** 增量回调派发器：优先 `Dispatchers.Main.immediate`；没有 Main 时取属性就抛，所以包住并退回 [Dispatchers.Default]。 */
    private val callbackDispatcher: CoroutineDispatcher =
        runCatching { Dispatchers.Main.immediate }.getOrElse { Dispatchers.Default }

    /** 流式累积一个 tool_call（OpenAI 把 id/name/arguments 分片下发，按 index 聚合）。 */
    private class ToolCallAcc(var id: String = "", var name: String = "") {
        val args = StringBuilder()
        val arguments: String get() = args.toString()
    }

    override suspend fun stream(
        config: AgentConfig,
        reqBody: JsonObject,
        onDelta: suspend (String) -> Unit,
        onReasoningDelta: suspend (String) -> Unit,
    ): AgentStreamResult {
        var emitted = false
        for (attempt in 0..2) {
            try {
                return streamOnce(config, reqBody,
                    onDelta = { if (it.isNotEmpty()) emitted = true; onDelta(it) },
                    onReasoningDelta = { if (it.isNotEmpty()) emitted = true; onReasoningDelta(it) },
                )
            } catch (e: java.io.IOException) {
                currentCoroutineContext().ensureActive()
                // 尚未展示输出、也没有执行工具，重连不会重复写入或拼接重复回答。
                if (emitted || attempt == 2) throw e
                delay(1_200L * (attempt + 1))
            }
        }
        error("无法连接模型服务")
    }

    private suspend fun streamOnce(
        config: AgentConfig,
        reqBody: JsonObject,
        onDelta: suspend (String) -> Unit,
        onReasoningDelta: suspend (String) -> Unit,
    ): AgentStreamResult = withContext(Dispatchers.IO) {
        val call = httpClient.newCall(
            Request.Builder()
                .url("${config.effectiveBaseUrl}/chat/completions")
                .header("Authorization", "Bearer ${config.apiKey}")
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .post(reqBody.toString().toRequestBody(JSON))
                .build()
        )
        val cancellationWatcher = launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                awaitCancellation()
            } finally {
                call.cancel()
            }
        }
        val resp = try {
            call.execute()
        } catch (e: java.io.IOException) {
            cancellationWatcher.cancel()
            currentCoroutineContext().ensureActive()
            throw e
        }

        try {
            resp.use {
                if (!resp.isSuccessful) {
                    val errBody = resp.body?.string().orEmpty()
                    val errMsg = runCatching {
                        AppJson.parseToJsonElement(errBody).jsonObject
                            .obj("error")?.get("message")?.stringValue
                    }.getOrNull() ?: "HTTP ${resp.code}"
                    throw RuntimeException(agentHttpErrorText(resp.code, errMsg))
                }
                val source = resp.body?.source() ?: throw RuntimeException("LLM 响应为空")
                val contentSb = StringBuilder()
                val reasoningSb = StringBuilder()
                val toolMap = sortedMapOf<Int, ToolCallAcc>()
                var finishReason = "stop"
                var completed = false
                var totalTokens: Long? = null

                while (true) {
                    currentCoroutineContext().ensureActive()
                    val line = try {
                        source.readUtf8Line()
                    } catch (e: java.io.IOException) {
                        currentCoroutineContext().ensureActive()
                        throw e
                    } ?: break
                    if (!line.startsWith("data:")) continue
                    val data = line.substring(5).trim()
                    if (data == "[DONE]") { completed = true; break }
                    val chunk = runCatching { AppJson.parseToJsonElement(data).jsonObject }.getOrNull() ?: continue
                    chunk.get("usage")?.takeIf { !it.isNull && it.isObject }?.jsonObject
                        ?.get("total_tokens")
                        ?.takeIf { !it.isNull }?.longValue?.let { totalTokens = it }
                    val choices = chunk.arr("choices") ?: continue
                    if (choices.size == 0) continue
                    val choice = choices[0].jsonObject
                    choice.get("finish_reason")?.takeIf { !it.isNull }?.stringValue?.let { finishReason = it; completed = true }
                    val delta = choice.obj("delta") ?: continue

                    delta.get("reasoning_content")?.takeIf { !it.isNull }?.stringValue?.let {
                        reasoningSb.append(it)
                        withContext(callbackDispatcher) { onReasoningDelta(it) }
                    }
                    delta.get("content")?.takeIf { !it.isNull }?.stringValue?.let { frag ->
                        if (frag.isNotEmpty()) {
                            contentSb.append(frag)
                            withContext(callbackDispatcher) { onDelta(frag) }
                        }
                    }
                    delta.get("tool_calls")?.takeIf { !it.isNull }?.jsonArray?.forEach { el ->
                        val o = el.jsonObject
                        val idx = o.get("index")?.takeIf { !it.isNull }?.intValue ?: 0
                        val acc = toolMap.getOrPut(idx) { ToolCallAcc() }
                        o.get("id")?.takeIf { !it.isNull }?.stringValue?.let { acc.id = it }
                        o.obj("function")?.let { f ->
                            f.get("name")?.takeIf { !it.isNull }?.stringValue?.let { acc.name = it }
                            f.get("arguments")?.takeIf { !it.isNull }?.stringValue?.let { acc.args.append(it) }
                        }
                    }
                }
                if (!completed) throw java.io.IOException("连接中断，回复尚未完成，请重试")
                return@withContext AgentStreamResult(
                    contentSb.toString(),
                    reasoningSb.toString(),
                    toolMap.values.filter { it.name.isNotBlank() }
                        .map { AgentToolCall(it.id, it.name, it.arguments) },
                    finishReason,
                    totalTokens
                )
            }
        } finally {
            cancellationWatcher.cancel()
        }
    }

    companion object {
        /** 共享连接池：每次 sendMessage 重用同一 OkHttpClient，避免泄漏。 */
        private val httpClient = HttpClients.base.newBuilder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)   // 流式期间连接需保持更久
            .build()

        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
