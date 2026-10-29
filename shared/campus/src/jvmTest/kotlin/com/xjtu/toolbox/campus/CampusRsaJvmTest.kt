package com.xjtu.toolbox.campus

import java.security.KeyFactory
import java.security.spec.PKCS8EncodedKeySpec
import javax.crypto.Cipher
import kotlin.io.encoding.Base64
import kotlin.test.*
import kotlinx.coroutines.test.runTest

class CampusRsaJvmTest {
    @Test fun rsaCiphertextDecryptsToOriginalUtf8UsingPkcs1Padding() {
        val password = "synthetic-密码-2048"
        val key = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(Base64.decode(CampusFixtureKey.PRIVATE_PKCS8)))
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding").apply { init(Cipher.DECRYPT_MODE, key) }
        val encrypted = CampusRsa.encryptPassword(password, CampusFixtureKey.PUBLIC)
        assertEquals(password, cipher.doFinal(Base64.decode(encrypted.removePrefix("__RSA__"))).decodeToString())
    }

    @Test fun ordinaryLoginSubmitsDecryptableRsaNotPlaintext() = runTest {
        val key = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(Base64.decode(CampusFixtureKey.PRIVATE_PKCS8)))
        var decryptions = 0
        val school = CampusFakeSchool().apply { credentialCheck = { encrypted ->
            val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding").apply { init(Cipher.DECRYPT_MODE, key) }
            assertEquals("fixture-password", cipher.doFinal(Base64.decode(encrypted.removePrefix("__RSA__"))).decodeToString())
            decryptions++
        } }
        val session = CampusSession(school.engine, TestCampusStore())
        try {
            assertEquals(CampusStatus.AUTHENTICATED, session.login("fixture-user", "fixture-password").status)
            assertEquals(1, decryptions)
        } finally { session.close() }
    }
}
