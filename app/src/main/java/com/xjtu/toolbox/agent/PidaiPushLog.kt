package com.xjtu.toolbox.agent

import com.xjtu.toolbox.util.AppJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.put

/** 把屁岱推送全文追加到「推送记录」会话及其模型历史（#194）。 */
internal object PidaiPushLog {

    /** 通知栏正文预览的最大字数。 */
    private const val PREVIEW_CHARS = 30

    /** @return 这条记录的时间戳（点通知时据此定位），写盘失败返回 null。 */
    fun append(title: String, body: String): Long? = runCatching {
        val at = System.currentTimeMillis()
        // 后台模型输出里的 markdown 链接 / 图片拆掉，气泡里不出现可点的外链
        val content = "**$title**\n\n${body.replace("](", "] (")}"
        AgentSessionStore().update(AgentPushFocus.SESSION_ID, AgentPushFocus.SESSION_TITLE, lockTitle = true) { old ->
            val history = runCatching { AppJson.parseToJsonElement(old.llmHistory).jsonArray.toList() }.getOrDefault(emptyList()) +
                buildJsonObject {
                    put("role", "assistant")
                    put("content", content)
                }
            old.copy(
                messages = old.messages + StoredMessage(role = "assistant", content = content, timestamp = at),
                llmHistory = JsonArray(history).toString(),
            )
        }
        AgentPushFocus.notifyAppended()
        at
    }.getOrNull()

    fun preview(body: String): String {
        val flat = body.replace('\n', ' ').trim()
        if (flat.length <= PREVIEW_CHARS) return flat
        val head = flat.take(PREVIEW_CHARS).let { if (it.last().isHighSurrogate()) it.dropLast(1) else it }
        return head.trimEnd() + "…点击查看详情"
    }
}
