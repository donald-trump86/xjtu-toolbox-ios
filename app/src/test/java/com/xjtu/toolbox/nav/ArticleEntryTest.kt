package com.xjtu.toolbox.nav

import android.content.Intent
import org.junit.Assert.*
import org.junit.Test

class ArticleEntryTest {
    @Test fun imagesOpenAndShareAsAttachments() {
        for (action in listOf(Intent.ACTION_VIEW, Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE)) {
            val target = DeepLinkRouter.imageTarget(DeepLinkRouter.PIDAI_LINK_ALIAS, action, "image/png",
                listOf("content://photos/1", "content://photos/1", "content://photos/2"))!!
            assertTrue(target.autoSend)
            assertEquals(AppRoute.Agent, target.route)
            assertEquals(listOf("content://photos/1", "content://photos/2"), target.imageUris)
            assertEquals(2, target.imageCount)
        }
    }

    @Test fun imagesRespectTheExistingMessageLimit() {
        val target = DeepLinkRouter.imageTarget(DeepLinkRouter.PIDAI_LINK_ALIAS, Intent.ACTION_SEND_MULTIPLE,
            "image/jpeg", (1..6).map { "content://photos/$it" })!!
        assertEquals(4, target.imageUris.size)
        assertEquals(6, target.imageCount)
        assertNull(DeepLinkRouter.imageTarget(DeepLinkRouter.PIDAI_LINK_ALIAS, Intent.ACTION_SEND, "text/plain", listOf("content://photos/1")))
        assertNull(DeepLinkRouter.imageTarget(DeepLinkRouter.PIDAI_LINK_ALIAS, Intent.ACTION_VIEW, "image/png", listOf("https://example.com/a.png")))
    }
    @Test fun browserEntryStartsANewReadingRequest() {
        val target = DeepLinkRouter.articleTarget(DeepLinkRouter.PIDAI_LINK_ALIAS, Intent.ACTION_VIEW,
            "https://mp.weixin.qq.com/s?__biz=abc&sn=xyz")!!
        assertEquals(AppRoute.Agent, target.route)
        assertTrue(target.autoSend)
        assertEquals("请阅读这篇文章，概括主要内容和要点：\nhttps://mp.weixin.qq.com/s?__biz=abc&sn=xyz", target.prompt)
    }

    @Test fun sharedTextAndClipLinksUseTheSameReadingEntry() {
        val target = DeepLinkRouter.articleTarget(DeepLinkRouter.PIDAI_LINK_ALIAS, Intent.ACTION_SEND, null,
            listOf("分享一篇好文章", "https://example.com/article"))!!
        assertTrue(target.autoSend)
        assertTrue(target.prompt!!.endsWith("https://example.com/article"))
    }

    @Test fun ordinaryDeepLinksAndOtherActionsDoNotAutoSend() {
        assertFalse(DeepLinkRouter.DeepLinkTarget(AppRoute.Agent, "hello").autoSend)
        assertNull(DeepLinkRouter.articleTarget("com.xjtu.toolbox.MainActivity", Intent.ACTION_VIEW, "https://example.com/"))
        assertNull(DeepLinkRouter.articleTarget(DeepLinkRouter.PIDAI_LINK_ALIAS, Intent.ACTION_EDIT, "https://example.com/"))
        assertNull(DeepLinkRouter.articleTarget(DeepLinkRouter.PIDAI_LINK_ALIAS, Intent.ACTION_VIEW, "file:///a.html"))
        assertNull(DeepLinkRouter.articleTarget(DeepLinkRouter.PIDAI_LINK_ALIAS, Intent.ACTION_SEND, null, listOf("只有标题")))
    }
}
