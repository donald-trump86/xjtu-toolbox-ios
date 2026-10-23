package com.xjtu.toolbox.community

/** 插入图片的 Markdown 前先限定 URL，防止链接闭合后注入其他 Markdown。 */
fun communityImageMarkdown(url: String): String {
    val value = url.trim()
    require(value.matches(Regex("https://[^/?#]+(?:[/?#].*)?")) && value.length <= 4096 && value.none { it.isWhitespace() || it in "<>\\\"" }) { "请输入有效的 HTTPS 图片地址" }
    return "\n![图片](<${value}>)\n"
}
