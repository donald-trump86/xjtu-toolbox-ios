package com.xjtu.toolbox.campus

import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class CampusTransportTest {
    @Test fun cookieDomainPathSecureAndLogicalSiteIsolation() {
        val jar = CampusCookieJar { 1_000 }
        val login = Url("https://$CAS_HOST/cas/login")
        jar.receive(login, login, listOf("fixtureCas=value; Domain=xjtu.edu.cn; Path=/cas; Secure", "foreign=value; Domain=example.com; Path=/"))
        assertEquals("fixtureCas=value", jar.header(login, login))
        assertEquals("", jar.header(Url("https://$CAS_HOST/cast"), Url("https://$CAS_HOST/cast")))
        assertEquals("", jar.header(Url("http://$CAS_HOST/cas/login"), Url("http://$CAS_HOST/cas/login")))
        assertEquals("", jar.header(Url("https://$JWXT_HOST/cas/login"), Url("https://$JWXT_HOST/cas/login")))
        assertEquals(1, jar.snapshot().size)
    }

    @Test fun cookieExpirationDeletionAndRestore() {
        var now = 10_000L
        val url = Url("https://$JWXT_HOST/jwapp/index")
        val jar = CampusCookieJar { now }
        jar.receive(url, url, listOf("temporary=one; Path=/; Secure; Max-Age=1"))
        val restored = CampusCookieJar { now }
        restored.restore(jar.snapshot())
        assertEquals("temporary=one", restored.header(url, url))
        now += 1_001
        assertEquals("", restored.header(url, url))
        jar.receive(url, url, listOf("session=one; Path=/", "session=gone; Path=/; Max-Age=0"))
        assertEquals("", jar.header(url, url))
    }

    @Test fun webVpnGatewayCookieIsSharedButServiceCookiesStayPartitioned() {
        val jar = CampusCookieJar { 1_000 }
        val gateway = Url("https://$VPN_HOST/login")
        val cas = Url("https://$CAS_HOST/cas/login")
        val jwxt = Url("https://$JWXT_HOST/jwapp/index")
        val physicalCas = Url(CampusWebVpnCodec.toVpnUrl(cas.toString()))
        val physicalJwxt = Url(CampusWebVpnCodec.toVpnUrl(jwxt.toString()))
        jar.receive(gateway, gateway, listOf("gateway=one; Path=/; Secure"))
        jar.receive(physicalCas, cas, listOf("casPartition=two; Path=/; Secure"))
        assertEquals("gateway=one; casPartition=two", jar.header(physicalCas, cas))
        assertEquals("gateway=one", jar.header(physicalJwxt, jwxt))
    }

    @Test fun routeSwitchUsesIndependentCookieJars() = runTest {
        val seen = mutableListOf<String?>()
        val engine = MockEngine { request ->
            seen += request.headers[HttpHeaders.Cookie]
            respond("ok", headers = headersOf(HttpHeaders.SetCookie, "route=one; Path=/; Secure"))
        }
        val transport = CampusTransport(engine, CampusWebVpnCodec)
        try {
            transport.request("https://$JWXT_HOST/test")
            transport.request("https://$JWXT_HOST/test")
            transport.route = CampusRoute.WEBVPN
            transport.request("https://$JWXT_HOST/test")
            transport.request("https://$JWXT_HOST/test")
            transport.route = CampusRoute.DIRECT
            transport.request("https://$JWXT_HOST/test")
            assertEquals(listOf(null, "route=one", null, "route=one", "route=one"), seen)
        } finally { transport.close() }
    }

    @Test fun unknownRedirectHostIsRejectedBeforeRequest() = runTest {
        var calls = 0
        val transport = CampusTransport(MockEngine {
            calls++
            respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "https://example.com/collect"))
        }, CampusWebVpnCodec)
        try {
            assertFailsWith<CampusProtocolException> { transport.request("https://$CAS_HOST/cas/login") }
            assertEquals(1, calls)
        } finally { transport.close() }
    }

    @Test fun credential307CannotForwardBodyToAnotherSchoolHost() = runTest {
        var calls = 0
        val transport = CampusTransport(MockEngine {
            calls++
            respond("", HttpStatusCode.TemporaryRedirect, headersOf(HttpHeaders.Location, "https://$JWXT_HOST/collect"))
        }, CampusWebVpnCodec)
        try {
            assertFailsWith<CampusProtocolException> {
                transport.request("https://$CAS_HOST/cas/login", HttpMethod.Post, "password=synthetic", ContentType.Application.FormUrlEncoded, credentialPost = true)
            }
            assertEquals(1, calls)
        } finally { transport.close() }
    }

    @Test fun credentialPostIsRestrictedToCasEndpoints() = runTest {
        var calls = 0
        val transport = CampusTransport(MockEngine { calls++; respond("unexpected") }, CampusWebVpnCodec)
        try {
            for (target in listOf("https://$JWXT_HOST/cas/login", "https://$CAS_HOST/other", "http://$CAS_HOST/cas/login")) {
                assertFailsWith<CampusProtocolException> {
                    transport.request(target, HttpMethod.Post, "password=synthetic", credentialPost = true)
                }
            }
            assertEquals(0, calls)
        } finally { transport.close() }
    }

    @Test fun authorizationCannotEscapeMobileApiOn302() = runTest {
        val seen = mutableListOf<Pair<String, String?>>()
        val transport = CampusTransport(MockEngine { request ->
            seen += request.url.host to request.headers[HttpHeaders.Authorization]
            if (request.url.host == JWAPP_HOST) respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "https://$CAS_HOST/cas/login"))
            else respond("ok")
        }, CampusWebVpnCodec)
        try {
            transport.request("https://$JWAPP_HOST/api/score", token = "synthetic-mobile-token")
            assertEquals(listOf(JWAPP_HOST to "synthetic-mobile-token", CAS_HOST to null), seen)
        } finally { transport.close() }
    }

    @Test fun redirectLoopStopsAfterTwelveRedirects() = runTest {
        var calls = 0
        val transport = CampusTransport(MockEngine {
            calls++; respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "/loop"))
        }, CampusWebVpnCodec)
        try {
            assertFailsWith<CampusProtocolException> { transport.request("https://$CAS_HOST/loop") }
            assertEquals(13, calls)
        } finally { transport.close() }
    }

    @Test fun nonDefaultPortsAndUrlCredentialsNeverReachEngine() = runTest {
        var calls = 0
        val transport = CampusTransport(MockEngine { calls++; respond("unexpected") }, CampusWebVpnCodec)
        try {
            for (url in listOf("https://$CAS_HOST:8443/cas/login", "https://user:synthetic@$CAS_HOST/cas/login", "https://xjtu.edu.cn/")) {
                assertFailsWith<CampusProtocolException> { transport.request(url) }
            }
            assertEquals(0, calls)
        } finally { transport.close() }
    }
}
