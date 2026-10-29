package com.xjtu.toolbox.campus

import com.xjtu.toolbox.jwapp.NoScoreDetailException
import com.xjtu.toolbox.schedule.ScheduleChangeEvent
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class CampusSessionTest {
    @Test fun unavailableSecureStorageProducesRecoverableStartupErrorWithoutNetwork() = runTest {
        val school = CampusFakeSchool()
        val store = object : com.xjtu.toolbox.platform.KeyValueStore by TestCampusStore() {
            override fun getString(key: String): String? = throw CampusProtocolException("受保护存储暂不可用")
        }
        val session = CampusSession(school.engine, store)
        try {
            val result = session.restore()
            assertEquals(CampusStatus.ERROR, result.status)
            assertNull(result.accountId)
            assertEquals("受保护存储暂不可用", result.message)
            assertTrue(school.requests.isEmpty())
        } finally { session.close() }
    }

    @Test fun normalLoginValidatesBothSitesAndParsesSchedule() = runTest {
        val school = CampusFakeSchool()
        val session = CampusSession(school.engine, TestCampusStore())
        try {
            assertEquals(CampusStatus.AUTHENTICATED, session.login("fixture-user", "fixture-password").status)
            assertEquals(1, school.credentialPosts)
            val source = session.ensureSchedule()
            assertEquals("2026-2027-1", source.currentTerm())
            assertEquals(listOf("2026-2027-1", "2025-2026-2"), source.termList())
            assertEquals("2026-2027学年 第一学期", source.termNames()["2026-2027-1"])
            val start = source.termStart("2026-2027-1")
            assertEquals("2026-09-14", start.startDate)
            assertEquals(18, start.totalWeeks)
            assertEquals(18, source.termWeeksOf("2026-2027-1"))
            val schedule = source.schedule("2026-2027-1")
            assertEquals(3, schedule.courses.size)
            val moved = schedule.courses.single { it.location == "主楼B-202" }
            assertEquals("0000100000000000", moved.weekBits)
            assertEquals("示例甲", moved.teacher)
            assertEquals('0', schedule.courses.single { it.location == "主楼A-101" }.weekBits[4])
            assertEquals(ScheduleChangeEvent.Kind.MOVED, schedule.changes.single().kind)
            assertEquals(schedule.changes, source.changes("2026-2027-1"))
            val exam = source.examSchedule("2026-2027-1").single()
            assertEquals("2027-01-15", exam.examDate)
            assertEquals("14:30-16:30", exam.examTime)
            assertEquals("27", exam.seatNumber)
            assertTrue(school.requests.filter { it.url.host != JWAPP_HOST }.all { it.headers[HttpHeaders.Authorization] == null })
        } finally { session.close() }
    }

    @Test fun scoreDetailUsesServerGpaAndCachesAllTerms() = runTest {
        val school = CampusFakeSchool()
        val session = CampusSession(school.engine, TestCampusStore())
        try {
            session.login("fixture-user", "fixture-password")
            val source = session.ensureScores()
            val terms = source.getGrade()
            val score = terms.single().scoreList.single()
            assertEquals(86.0, score.scoreValue)
            assertEquals("MATH1001", score.courseCode)
            assertEquals(terms, session.scoreCache.read(1_000))
            val detail = source.getScoreDetail("SCORE-1")
            assertEquals(3.9, detail.gpa)
            assertEquals(listOf(0.3, 0.7), detail.itemList.map { it.itemPercent })
            assertEquals(listOf(85.0, 90.0), detail.itemList.map { it.itemScoreValue })
            school.detailGpa = 0.0
            assertEquals(3.7, source.getScoreDetail("SCORE-1").gpa)
        } finally { session.close() }
    }

    @Test fun missingScoreDetailJson401IsNotSessionExpiry() = runTest {
        val school = CampusFakeSchool()
        val session = CampusSession(school.engine, TestCampusStore())
        try {
            session.login("fixture-user", "fixture-password")
            for (code in listOf(200, 401, 404)) {
                school.noDetailCode = code
                assertFailsWith<NoScoreDetailException> { session.scoreSource.getScoreDetail("SCORE-NODETAIL") }
                assertEquals(CampusStatus.AUTHENTICATED, session.state.value.status)
            }
            assertEquals(1, school.credentialPosts)
        } finally { session.close() }
    }

    @Test fun detectMfaGetsPhoneBeforeSendingAndWrongCodeCanRetry() = runTest {
        val school = CampusFakeSchool().apply { detectMfa = true }
        val session = CampusSession(school.engine, TestCampusStore())
        try {
            val pending = session.login("fixture-user", "fixture-password")
            assertEquals(CampusStatus.SMS_REQUIRED, pending.status)
            assertEquals(CampusMfaFlow.MFA_DETECT, pending.mfaFlow)
            assertEquals("138****0000", pending.maskedPhone)
            assertFalse(pending.smsAlreadySent)
            assertEquals(1, school.phoneCalls)
            assertEquals(0, school.sendCalls)
            assertTrue(session.requestSms().smsAlreadySent)
            session.requestSms()
            assertEquals(1, school.sendCalls)
            assertEquals(CampusStatus.SMS_REQUIRED, session.verifySms("000000").status)
            assertEquals(0, school.credentialPosts)
            assertEquals(CampusStatus.AUTHENTICATED, session.verifySms("123456").status)
            assertEquals(2, school.verifyCalls)
            assertEquals(1, school.credentialPosts)
        } finally { session.close() }
    }

    @Test fun safetyVerifyKeepsOriginalUrlAndDoesNotResendSmsOrPassword() = runTest {
        val school = CampusFakeSchool().apply { safetyMfa = true }
        val session = CampusSession(school.engine, TestCampusStore())
        try {
            val pending = session.login("fixture-user", "fixture-password")
            assertEquals(CampusStatus.SMS_REQUIRED, pending.status)
            assertEquals(CampusMfaFlow.SAFETY_VERIFY, pending.mfaFlow)
            assertTrue(pending.smsAlreadySent)
            assertEquals(1, school.phoneCalls)
            session.requestSms()
            assertEquals(0, school.sendCalls)
            assertEquals(CampusStatus.SMS_REQUIRED, session.verifySms("000000").status)
            assertEquals(CampusStatus.AUTHENTICATED, session.verifySms("123456").status)
            assertEquals(1, school.safetyPosts)
            assertEquals(1, school.credentialPosts)
        } finally { session.close() }
    }

    @Test fun bothMfaFlowsCancelOnLogout() = runTest {
        for (flow in CampusMfaFlow.entries) {
            val school = CampusFakeSchool().apply { detectMfa = flow == CampusMfaFlow.MFA_DETECT; safetyMfa = flow == CampusMfaFlow.SAFETY_VERIFY }
            val session = CampusSession(school.engine, TestCampusStore())
            try {
                assertEquals(CampusStatus.SMS_REQUIRED, session.login("fixture-user", "fixture-password").status)
                session.logout()
                assertEquals(CampusStatus.LOGGED_OUT, session.verifySms("123456").status)
                assertEquals(0, school.verifyCalls)
            } finally { session.close() }
        }
    }

    @Test fun smsChallengeExpiresAfterThreeRejectedCodes() = runTest {
        val school = CampusFakeSchool().apply { detectMfa = true }
        val session = CampusSession(school.engine, TestCampusStore())
        try {
            session.login("fixture-user", "fixture-password")
            repeat(2) { assertEquals(CampusStatus.SMS_REQUIRED, session.verifySms("000000").status) }
            assertEquals(CampusStatus.EXPIRED, session.verifySms("000000").status)
            assertEquals(CampusStatus.EXPIRED, session.verifySms("123456").status)
            assertEquals(3, school.verifyCalls)
        } finally { session.close() }
    }

    @Test fun smsChallengeExpiresAt150SecondsWithoutSendingCode() = runTest {
        var now = 10_000L
        val school = CampusFakeSchool().apply { detectMfa = true }
        val session = CampusSession(school.engine, TestCampusStore(), now = { now })
        try {
            session.login("fixture-user", "fixture-password")
            now += 150_001
            assertEquals(CampusStatus.EXPIRED, session.verifySms("123456").status)
            session.requestSms()
            assertEquals(0, school.verifyCalls)
            assertEquals(0, school.sendCalls)
        } finally { session.close() }
    }

    @Test fun restoreValidatesPersistedSessionsWithoutCredentialPost() = runTest {
        val store = TestCampusStore()
        val first = CampusSession(CampusFakeSchool().engine, store)
        first.login("fixture-user", "fixture-password")
        first.scoreSource.getGrade()
        first.close()
        val school = CampusFakeSchool()
        val restored = CampusSession(school.engine, store)
        try {
            assertEquals(CampusStatus.AUTHENTICATED, restored.restore().status)
            assertEquals(0, school.credentialPosts)
            assertNotNull(restored.scoreCache.read(10_000))
        } finally { restored.close() }
    }

    @Test fun expiredRestoreCannotPromptOrSendSmsInBackground() = runTest {
        val store = TestCampusStore()
        val first = CampusSession(CampusFakeSchool().engine, store)
        first.login("fixture-user", "fixture-password"); first.close()
        val school = CampusFakeSchool().apply { invalidSessions = true; detectMfa = true }
        val restored = CampusSession(school.engine, store)
        try {
            assertEquals(CampusStatus.EXPIRED, restored.restore().status)
            restored.requestSms()
            assertEquals(0, school.sendCalls)
            assertEquals(CampusStatus.EXPIRED, restored.verifySms("123456").status)
            assertEquals(0, school.verifyCalls)
        } finally { restored.close() }
    }

    @Test fun expiryRetriesAndReplaysOnlyOnce() = runTest {
        val school = CampusFakeSchool()
        val session = CampusSession(school.engine, TestCampusStore())
        try {
            session.login("fixture-user", "fixture-password")
            school.expireScheduleOnce = true
            assertEquals(3, session.scheduleSource.schedule("2026-2027-1").courses.size)
            assertEquals(2, school.scheduleCalls)
            assertEquals(CampusStatus.AUTHENTICATED, session.state.value.status)
            school.alwaysExpireSchedule = true
            assertFailsWith<CampusSessionExpiredException> { session.scheduleSource.schedule("2026-2027-1") }
            assertEquals(4, school.scheduleCalls)
            assertEquals(CampusStatus.EXPIRED, session.state.value.status)
        } finally { session.close() }
    }

    @Test fun scheduleLandingAndValidationLoginWallsCannotAuthenticate() = runTest {
        for (validation in listOf(false, true)) {
            val school = CampusFakeSchool().apply { scheduleLoginWall = !validation; scheduleValidationWall = validation }
            val session = CampusSession(school.engine, TestCampusStore())
            try {
                assertEquals(CampusStatus.EXPIRED, session.login("fixture-user", "fixture-password").status)
                assertTrue(school.requests.none { it.url.host == JWAPP_HOST })
            } finally { session.close() }
        }
    }

    @Test fun logoutClearsSecretsSessionsAndSensitiveCache() = runTest {
        val store = TestCampusStore()
        val session = CampusSession(CampusFakeSchool().engine, store)
        try {
            session.login("fixture-user", "fixture-password")
            session.scoreSource.getGrade()
            assertNotNull(session.scoreCache.read(10_000))
            session.logout()
            assertNull(session.state.value.accountId)
            assertNull(session.scoreCache.read(10_000))
            assertNull(store.getString("active-account"))
            assertNull(store.getString("account:fixture-user:session"))
            assertNull(store.getString("account:fixture-user:scores"))
            assertFailsWith<CampusSessionExpiredException> { session.scheduleSource.currentTerm() }
        } finally { session.close() }
    }

    @Test fun retainedScoreCacheCannotReadOrWriteAfterSessionChanges() = runTest {
        val school = CampusFakeSchool()
        val session = CampusSession(school.engine, TestCampusStore())
        try {
            assertEquals(CampusStatus.AUTHENTICATED, session.login("fixture-user", "fixture-password").status)
            val oldLoginCache = session.scoreCache
            val grades = session.scoreSource.getGrade()
            assertEquals(grades, oldLoginCache.read(1_000))
            assertEquals(CampusStatus.AUTHENTICATED, session.login("fixture-user", "fixture-password").status)
            assertNull(oldLoginCache.read(1_000))
            oldLoginCache.write(emptyList())
            assertEquals(grades, session.scoreCache.read(1_000))
            val oldRestoreCache = session.scoreCache
            assertEquals(CampusStatus.AUTHENTICATED, session.restore().status)
            assertNull(oldRestoreCache.read(1_000))
            oldRestoreCache.write(emptyList())
            assertEquals(grades, session.scoreCache.read(1_000))
            val oldLogoutCache = session.scoreCache
            session.logout()
            oldLogoutCache.write(grades)
            assertNull(session.scoreCache.read(1_000))
            assertEquals(CampusStatus.AUTHENTICATED, session.login("fixture-user", "fixture-password").status)
            oldLogoutCache.write(grades)
            assertNull(oldLogoutCache.read(1_000))
            assertNull(session.scoreCache.read(1_000))
        } finally { session.close() }
    }

    @Test fun webVpnRestoreAcceptsProxiedBusiness200401And403Landings() = runTest {
        for (status in listOf(200, 401, 403)) {
            val store = TestCampusStore()
            val first = CampusSession(CampusFakeSchool().engine, store, CampusRoute.WEBVPN)
            try {
                assertEquals(CampusStatus.AUTHENTICATED, first.login("fixture-user", "fixture-password").status)
            } finally { first.close() }
            val school = CampusFakeSchool().apply { gatewayLandingStatus = status }
            val restored = CampusSession(school.engine, store)
            try {
                assertEquals(CampusStatus.AUTHENTICATED, restored.restore().status, "Business landing status $status")
                assertEquals(0, school.credentialPosts)
                val urls = school.requests.map { CampusWebVpnCodec.fromVpnUrl(it.url.toString())?.let(::Url) ?: it.url }
                val landing = urls.indexOfFirst { it.encodedPath == "/webvpn/last-request" }
                val scheduleEntry = urls.indexOfFirst { it.host == JWXT_HOST && it.encodedPath == "/jwapp/sys/homeapp/index.do" }
                val scheduleValidation = urls.indexOfFirst { it.encodedPath.endsWith("/dqxnxq.do") }
                val scoreValidation = urls.indexOfFirst { it.host == JWAPP_HOST && it.encodedPath.endsWith("/time") }
                assertTrue(landing >= 0)
                assertTrue(scheduleEntry > landing)
                assertTrue(scheduleValidation > scheduleEntry)
                assertTrue(scoreValidation > scheduleValidation)
            } finally { restored.close() }
        }
    }

    @Test fun webVpnProxiedLandingCanAuthenticateWithoutGatewayCookie() = runTest {
        val school = CampusFakeSchool().apply { gatewayLandingStatus = 403; gatewayTicket = null }
        val session = CampusSession(school.engine, TestCampusStore(), CampusRoute.WEBVPN)
        try {
            assertEquals(CampusStatus.AUTHENTICATED, session.login("fixture-user", "fixture-password").status)
            assertEquals(1, school.credentialPosts)
            assertEquals("2026-2027-1", session.scheduleSource.currentTerm())
            assertTrue(school.requests.all { it.url.host == VPN_HOST })
            assertTrue(school.requests.none { it.headers[HttpHeaders.Cookie].orEmpty().contains("wengine_vpn_ticketwebvpn_xjtu_edu_cn=") })
        } finally { session.close() }
    }

    @Test fun webVpnGatewayPageWithoutNonEmptyTicketCannotAuthenticate() = runTest {
        for (ticket in listOf(null, "")) {
            val school = CampusFakeSchool().apply { gatewayTicket = ticket }
            val session = CampusSession(school.engine, TestCampusStore(), CampusRoute.WEBVPN)
            try {
                assertEquals(CampusStatus.ERROR, session.login("fixture-user", "fixture-password").status)
                assertTrue(school.requests.none { CampusWebVpnCodec.fromVpnUrl(it.url.toString())?.let(::Url)?.host == JWXT_HOST })
            } finally { session.close() }
        }
    }

    @Test fun webVpnRestoreLoginWallRejectsStoredGatewayTicket() = runTest {
        val store = TestCampusStore()
        val first = CampusSession(CampusFakeSchool().engine, store, CampusRoute.WEBVPN)
        try {
            assertEquals(CampusStatus.AUTHENTICATED, first.login("fixture-user", "fixture-password").status)
        } finally { first.close() }
        val school = CampusFakeSchool().apply { gatewayLoginWall = true }
        val restored = CampusSession(school.engine, store)
        try {
            assertEquals(CampusStatus.EXPIRED, restored.restore().status)
            assertEquals(1, school.gatewayLoginCalls)
            assertEquals(0, school.credentialPosts)
            assertTrue(school.requests.single().headers[HttpHeaders.Cookie].orEmpty().contains("wengine_vpn_ticketwebvpn_xjtu_edu_cn="))
        } finally { restored.close() }
    }

    @Test fun webVpnBusinessLandingStillRequiresIndependentSiteValidation() = runTest {
        val school = CampusFakeSchool().apply { gatewayLandingStatus = 401; scheduleValidationWall = true }
        val session = CampusSession(school.engine, TestCampusStore(), CampusRoute.WEBVPN)
        try {
            assertEquals(CampusStatus.EXPIRED, session.login("fixture-user", "fixture-password").status)
            assertTrue(school.requests.any {
                CampusWebVpnCodec.fromVpnUrl(it.url.toString())?.let(::Url)?.encodedPath?.endsWith("/dqxnxq.do") == true
            })
            assertTrue(school.requests.none { CampusWebVpnCodec.fromVpnUrl(it.url.toString())?.let(::Url)?.host == JWAPP_HOST })
        } finally { session.close() }
    }

    @Test fun webVpnGatewayExpiryRecoversEachBusinessSiteBeforeOneReplay() = runTest {
        for (site in listOf(CampusSite.SCHEDULE, CampusSite.SCORES)) {
            val school = CampusFakeSchool()
            val session = CampusSession(school.engine, TestCampusStore(), CampusRoute.WEBVPN)
            try {
                assertEquals(CampusStatus.AUTHENTICATED, session.login("fixture-user", "fixture-password").status)
                val before = school.requests.size
                school.gatewayExpired = true
                if (site == CampusSite.SCHEDULE) assertEquals(3, session.scheduleSource.schedule("2026-2027-1").courses.size)
                else assertEquals(1, session.scoreSource.getGrade().size)
                val urls = school.requests.drop(before).map { CampusWebVpnCodec.fromVpnUrl(it.url.toString())?.let(::Url) ?: it.url }
                val path = if (site == CampusSite.SCHEDULE) "/xskcb.do" else "/termScore"
                val gateway = urls.indexOfFirst { it.host == VPN_HOST && it.parameters["cas_login"] == "true" }
                val entryUrl = Url(site.entry)
                val entry = urls.indexOfFirst { it.host == entryUrl.host && it.encodedPath == entryUrl.encodedPath }
                assertEquals(2, urls.count { it.encodedPath.endsWith(path) })
                assertTrue(gateway > 0)
                assertTrue(entry > gateway)
                assertTrue(urls.indexOfLast { it.encodedPath.endsWith(path) } > entry)
                assertEquals(2, school.gatewayLoginCalls)
                assertEquals(1, school.credentialPosts)
                assertEquals(CampusStatus.AUTHENTICATED, session.state.value.status)
            } finally { session.close() }
        }
    }

    @Test fun webVpnGatewayExpiryMfaCannotPromptOrSendSmsInBackground() = runTest {
        val school = CampusFakeSchool()
        val session = CampusSession(school.engine, TestCampusStore(), CampusRoute.WEBVPN)
        try {
            assertEquals(CampusStatus.AUTHENTICATED, session.login("fixture-user", "fixture-password").status)
            school.gatewayExpired = true; school.invalidSessions = true; school.detectMfa = true
            assertFailsWith<CampusSessionExpiredException> { session.scheduleSource.schedule("2026-2027-1") }
            assertEquals(CampusStatus.EXPIRED, session.state.value.status)
            assertNull(session.state.value.mfaFlow)
            assertNull(session.state.value.maskedPhone)
            assertEquals(2, school.gatewayLoginCalls)
            assertEquals(1, school.scheduleCalls)
            assertEquals(1, school.credentialPosts)
            assertEquals(1, school.phoneCalls)
            assertEquals(CampusStatus.EXPIRED, session.requestSms().status)
            assertEquals(CampusStatus.EXPIRED, session.verifySms("123456").status)
            assertEquals(0, school.sendCalls)
            assertEquals(0, school.verifyCalls)
        } finally { session.close() }
    }

    @Test fun webVpnGatewayExpiryStopsAfterOneReplay() = runTest {
        val school = CampusFakeSchool()
        val session = CampusSession(school.engine, TestCampusStore(), CampusRoute.WEBVPN)
        try {
            assertEquals(CampusStatus.AUTHENTICATED, session.login("fixture-user", "fixture-password").status)
            school.alwaysExpireGateway = true
            assertFailsWith<CampusSessionExpiredException> { session.scheduleSource.schedule("2026-2027-1") }
            assertEquals(2, school.scheduleCalls)
            assertEquals(2, school.gatewayLoginCalls)
            assertEquals(1, school.credentialPosts)
            assertEquals(CampusStatus.EXPIRED, session.state.value.status)
        } finally { session.close() }
    }

    @Test fun webVpnRouteAuthenticatesGatewayAndProxiesBothServices() = runTest {
        val school = CampusFakeSchool()
        val session = CampusSession(school.engine, TestCampusStore(), CampusRoute.WEBVPN)
        try {
            assertEquals(CampusStatus.AUTHENTICATED, session.login("fixture-user", "fixture-password").status)
            assertEquals("2026-2027-1", session.scheduleSource.currentTerm())
            assertEquals(1, session.scoreSource.getGrade().size)
            assertTrue(school.requests.all { it.url.host == VPN_HOST })
            assertTrue(school.requests.any { it.url.encodedPath == "/login" && it.url.parameters["cas_login"] == "true" })
            assertTrue(school.requests.filter { it.headers[HttpHeaders.Authorization] != null }.all {
                CampusWebVpnCodec.fromVpnUrl(it.url.toString())?.startsWith("https://$JWAPP_HOST/api/") == true
            })
        } finally { session.close() }
    }
}
