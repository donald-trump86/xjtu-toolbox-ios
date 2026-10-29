package com.xjtu.toolbox.campus

import kotlin.io.encoding.Base64
import kotlin.test.*

/**
 * Security.framework crypto checks for the command-line Native test runner.
 * Keychain requires an application identity and is covered by
 * iosApp/Tests/CampusKeychainHostTests.swift through the real app-hosted bridge.
 */
class CampusNativeSecurityTest {
    @Test fun securityFrameworkEncryptsRsaAndGeneratesIndependentRandomBytes() {
        val first = CampusRsa.encryptPassword("native-demo", CampusFixtureKey.PUBLIC)
        val second = CampusRsa.encryptPassword("native-demo", CampusFixtureKey.PUBLIC)
        assertEquals(256, Base64.decode(first.removePrefix("__RSA__")).size)
        assertNotEquals(first, second)
        assertFalse(secureRandomBytes(32).contentEquals(secureRandomBytes(32)))
    }
}
