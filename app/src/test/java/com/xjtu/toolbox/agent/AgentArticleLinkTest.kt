package com.xjtu.toolbox.agent

import org.junit.Assert.*
import org.junit.Test

class AgentArticleLinkTest {
    @Test fun wechatLinkKeepsItsArticleAndSignatureParameters() {
        val url = "https://mp.weixin.qq.com/s?__biz=MzA%3D%3D&mid=123&idx=1&sn=a%2Bb&chksm=abc#rd"
        assertEquals(url, AgentArticleLink.fromUrl(url))
        assertEquals(url, AgentArticleLink.fromText("分享一篇文章\n$url"))
        assertEquals("请阅读这篇文章，概括主要内容和要点：\n$url", AgentArticleLink.prompt(url))
    }

    @Test fun shareTextExtractsTheFirstWebLinkAndDropsSurroundingPunctuation() {
        assertEquals("https://example.com/article?id=5", AgentArticleLink.fromText("推荐看看（https://example.com/article?id=5）。"))
        assertEquals("https://example.com/a", AgentArticleLink.fromText("文章 https://example.com/a\n来源 https://example.com/b"))
        assertEquals("https://example.com/topic_(test)", AgentArticleLink.fromText("(https://example.com/topic_(test))"))
        assertEquals("https://example.com/?a=1&b=2", AgentArticleLink.fromText("<a href=\"https://example.com/?a=1&amp;b=2\">文章</a>"))
    }

    @Test fun unsupportedOrMissingLinksAreIgnored() {
        listOf(null, "", "文章标题", "file:///sdcard/a.html", "javascript:alert(1)", "content://provider/article", "xjtu://agent?q=hello")
            .forEach { assertNull(AgentArticleLink.fromUrl(it)); assertNull(AgentArticleLink.fromText(it)) }
    }
}
