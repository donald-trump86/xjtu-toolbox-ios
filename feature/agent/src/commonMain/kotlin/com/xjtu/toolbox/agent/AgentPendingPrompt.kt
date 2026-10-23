package com.xjtu.toolbox.agent

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlin.concurrent.Volatile

/** 页外请求在屁岱页新开对话，或仅填入草稿。 */
object AgentPendingPrompt {

    data class Pending(
        val text: String,
        /** 不进聊天气泡，只进发给模型的 user 消息。 */
        val snapshot: String? = null,
        /** 只填进输入框等用户自己发。外部深链来的都是这种。 */
        val draftOnly: Boolean = false,
        val images: List<String> = emptyList(),
    )

    @Volatile
    private var pending: Pending? = null

    var generation by mutableIntStateOf(0)
        private set

    fun set(text: String, snapshot: String? = null, draftOnly: Boolean = false, images: List<String> = emptyList()) {
        pending = Pending(text, snapshot, draftOnly, images.toList())
        generation++
    }

    fun consume(): Pending? {
        val v = pending
        pending = null
        return v
    }
}
