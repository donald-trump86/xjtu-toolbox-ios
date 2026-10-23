package com.xjtu.toolbox.agent

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal object AgentArticleLink {
    private val links = Regex("https?://[^\\s<>\\\"'\\u0000-\\u0020]+", RegexOption.IGNORE_CASE)

    fun fromUrl(raw: String?): String? = raw?.trim()?.toHttpUrlOrNull()?.toString()

    fun fromText(text: CharSequence?): String? = links.findAll(text?.toString().orEmpty())
        .mapNotNull { match ->
            var candidate = match.value.trimEnd('。', '，', '；', '！', '？', '、', '）', '】', '》', '」', '』', ',', '.', ';')
            while (candidate.endsWith(')') && candidate.count { it == ')' } > candidate.count { it == '(' }) {
                candidate = candidate.dropLast(1)
            }
            fromUrl(candidate.replace("&amp;", "&"))
        }.firstOrNull()

    fun prompt(url: String): String = "请阅读这篇文章，概括主要内容和要点：\n$url"
}
