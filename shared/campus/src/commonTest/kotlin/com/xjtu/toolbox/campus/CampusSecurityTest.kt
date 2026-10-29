package com.xjtu.toolbox.campus

import com.xjtu.toolbox.jwapp.TermScore
import kotlin.io.encoding.Base64
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class CampusSecurityTest {
    @Test fun aesMatchesPublishedNistAes128BlockVector() {
        assertContentEquals(hex("69c4e0d86a7b0430d8cdb78070b4c55a"), PublicVpnAes.encrypt(
            hex("000102030405060708090a0b0c0d0e0f"), hex("00112233445566778899aabbccddeeff")))
    }

    @Test fun webVpnRoundTripPreservesPortQueryAndFragment() {
        for (url in listOf("https://$CAS_HOST/cas/login?service=a%2Fb#step", "http://$JWAPP_HOST:8080/app/index?x=1&y=2", "https://$JWXT_HOST/")) {
            val encoded = CampusWebVpnCodec.toVpnUrl(url)
            assertTrue(encoded.startsWith("https://$VPN_HOST/"))
            assertEquals(url, CampusWebVpnCodec.fromVpnUrl(encoded))
        }
        assertNull(CampusWebVpnCodec.fromVpnUrl("https://example.com/https/abcd/"))
        assertNull(CampusWebVpnCodec.fromVpnUrl("https://$VPN_HOST/https/nothex/"))
    }

    @Test fun rsaSupportsSpkiAndPkcs1WithRandomizedPadding() {
        val pkcs1 = CampusRsa.pkcs1Key(Base64.decode(CampusFixtureKey.PUBLIC))
        val first = CampusRsa.encryptPassword("synthetic-password", CampusFixtureKey.PUBLIC)
        val second = CampusRsa.encryptPassword("synthetic-password", "-----BEGIN RSA PUBLIC KEY-----\n${Base64.encode(pkcs1)}\n-----END RSA PUBLIC KEY-----")
        assertTrue(first.startsWith("__RSA__"))
        assertEquals(256, Base64.decode(first.removePrefix("__RSA__")).size)
        assertEquals(256, Base64.decode(second.removePrefix("__RSA__")).size)
        assertNotEquals(first, second)
        assertEquals(32, visitorId().length)
    }

    @Test fun malformedPublicKeysFailBeforeEncryption() {
        for (key in listOf("not base64", "MAA=", "AQIDBA==")) assertFails { CampusRsa.encryptPassword("synthetic-password", key) }
    }

    @Test fun scoreCacheIsAccountScopedAndExpiresOnClockRollback() = runTest {
        val backing = TestCampusStore()
        var account = "one"
        var now = 1_000L
        val cache = SecureCampusScoreCache({ ScopedCampusStore(backing, "account:$account") }, { now })
        val scores = listOf(TermScore("2026-2027-1", "synthetic term", emptyList()))
        cache.write(scores)
        assertEquals(scores, cache.read(100))
        account = "two"
        assertNull(cache.read(100))
        account = "one"
        assertEquals(scores, cache.read(100))
        now += 101
        assertNull(cache.read(100))
        cache.write(scores)
        now--
        assertNull(cache.read(100))
    }

    @Test fun scopedLogoutDoesNotEraseAnotherAccountOrUnrelatedStore() {
        val backing = TestCampusStore()
        val first = ScopedCampusStore(backing, "account:one")
        val second = ScopedCampusStore(backing, "account:two")
        backing.putString("unrelated", "keep")
        first.putString("session", "test-value")
        first.putString("scores", "test-score-value")
        second.putString("session", "keep-two")
        first.clear()
        assertNull(first.getString("session"))
        assertNull(first.getString("scores"))
        assertEquals("keep-two", second.getString("session"))
        assertEquals("keep", backing.getString("unrelated"))
    }
    private fun hex(value: String) = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
