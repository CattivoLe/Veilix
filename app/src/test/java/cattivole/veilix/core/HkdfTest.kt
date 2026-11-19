package cattivole.veilix.core

import cattivole.veilix.core.crypto.CryptoService
import org.junit.Assert.assertArrayEquals
import org.junit.Test

/**
 * RFC 5869 Appendix A test vectors for HKDF-SHA256. Confirms our manual
 * HKDF implementation matches Apple CryptoKit's `HKDF<SHA256>.deriveKey`.
 */
class HkdfTest {

    @Test
    fun rfc5869_test_case_1() {
        val ikm = ByteArray(22) { 0x0b.toByte() }
        val salt = hex("000102030405060708090a0b0c")
        val info = hex("f0f1f2f3f4f5f6f7f8f9")
        val expected = hex(
            "3cb25f25faacd57a90434f64d0362f2a" +
            "2d2d0a90cf1a5a4c5db02d56ecc4c5bf" +
            "34007208d5b887185865"
        )
        val out = CryptoService.hkdfSha256(ikm, salt, info, expected.size)
        assertArrayEquals(expected, out)
    }

    @Test
    fun rfc5869_test_case_3_empty_salt_and_info() {
        // L=42, IKM=22 bytes 0x0b, salt=empty, info=empty
        val ikm = ByteArray(22) { 0x0b.toByte() }
        val expected = hex(
            "8da4e775a563c18f715f802a063c5a31" +
            "b8a11f5c5ee1879ec3454e5f3c738d2d" +
            "9d201395faa4b61a96c8"
        )
        val out = CryptoService.hkdfSha256(ikm, ByteArray(0), ByteArray(0), expected.size)
        assertArrayEquals(expected, out)
    }

    private fun hex(s: String): ByteArray {
        val clean = s.replace(Regex("\\s"), "")
        require(clean.length % 2 == 0)
        return ByteArray(clean.length / 2) { i ->
            clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}
