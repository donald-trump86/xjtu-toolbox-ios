package com.xjtu.toolbox.platform

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UrlSafeBase64Test {
    @Test
    fun rfc4648VectorsMatchPureAndIosCodecs() {
        val vectors = mapOf(
            "" to "", "f" to "Zg", "fo" to "Zm8", "foo" to "Zm9v",
            "foob" to "Zm9vYg", "fooba" to "Zm9vYmE", "foobar" to "Zm9vYmFy",
        )
        for ((plain, encoded) in vectors) {
            val bytes = plain.encodeToByteArray()
            assertEquals(encoded, UrlSafeBase64.encode(bytes))
            assertEquals(encoded, base64UrlEncode(bytes))
            assertContentEquals(bytes, UrlSafeBase64.decode(encoded))
            assertContentEquals(bytes, base64UrlDecode(encoded))
        }
    }

    @Test
    fun urlAlphabetAndOptionalPaddingHaveKnownByteValues() {
        val bytes = byteArrayOf(0xFB.toByte(), 0xFF.toByte())
        assertEquals("-_8", UrlSafeBase64.encode(bytes))
        assertContentEquals(bytes, UrlSafeBase64.decode("-_8"))
        assertContentEquals(bytes, UrlSafeBase64.decode("-_8="))
        assertContentEquals(byteArrayOf(65), UrlSafeBase64.decode("QR"))
        assertContentEquals(byteArrayOf(65), UrlSafeBase64.decode("QQ=="))
    }

    @Test
    fun malformedLengthPaddingAndNonUrlCharactersAreRejected() {
        for (text in listOf("Q", "QQ=", "AB==CD", "Q===", "====", "a b", "aa+aa", "aa/aa", "AA=A")) {
            assertNull(UrlSafeBase64.decode(text), text)
        }
    }
}
