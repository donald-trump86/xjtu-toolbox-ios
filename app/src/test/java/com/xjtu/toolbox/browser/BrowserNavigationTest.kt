package com.xjtu.toolbox.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserNavigationTest {
    @Test fun paymentWaitsForAllAuthenticationHops() {
        assertTrue(isAuthHop("https://org.xjtu.edu.cn/openplatform/oauth/authorize?appId=1659"))
        assertTrue(isAuthHop("https://login.xjtu.edu.cn/cas/login?service=venue"))
        assertTrue(isAuthHop("http://202.117.17.144:8080/web/login/pre.html"))
        assertTrue(isAuthHop("http://202.117.17.144:8080/web/cas/oauth2url.html?code=temporary"))
        assertFalse(isAuthHop("http://202.117.17.144:8080/web/index.html"))
        assertFalse(isAuthHop("http://202.117.17.144:8080/web/pay/show.html?id=order"))
    }
}
