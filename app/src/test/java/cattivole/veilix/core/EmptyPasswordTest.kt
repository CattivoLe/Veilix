package cattivole.veilix.core

import cattivole.veilix.core.crypto.CryptoService
import cattivole.veilix.core.format.Payload
import cattivole.veilix.core.format.PayloadHeader
import cattivole.veilix.core.stego.CompressionAlgorithm
import cattivole.veilix.core.stego.EncryptionAlgorithm
import cattivole.veilix.core.stego.StegoOptions
import cattivole.veilix.core.stego.SteganographyService
import org.junit.Assert.*
import org.junit.Test

/**
 * Reproduces the bug seen when an iOS-encoded image with encryption=NONE
 * (and consequently an empty password) was decoded on Android and threw
 * `EncryptionFailed`. Root cause: JCE's `SecretKeySpec` rejects zero-length
 * byte arrays, and CryptoService used to call into it for PBKDF2 + HMAC.
 * Apple's CryptoKit accepts empty keys, so the v3 format depends on us
 * doing the same. Now HMAC is hand-rolled via `MessageDigest`.
 */
class EmptyPasswordTest {

    private val salt = ByteArray(16) { it.toByte() }

    @Test
    fun deriveSeed_with_empty_password_does_not_throw() {
        // All three KDF versions must accept empty passwords.
        for (version in PayloadHeader.SUPPORTED_VERSIONS) {
            val seed = CryptoService.deriveSeed("", salt, version.toInt())
            // Just verify it produces a value — different versions yield
            // different seeds, both valid.
            assertNotEquals(0L, seed)
        }
    }

    @Test
    fun deriveKey_with_empty_password_does_not_throw() {
        for (version in PayloadHeader.SUPPORTED_VERSIONS) {
            val key = CryptoService.deriveKey("", salt, "veilix.enc.v1", version.toInt())
            assertEquals(CryptoService.KEY_SIZE, key.size)
        }
    }

    @Test
    fun roundtrip_no_encryption_no_password() {
        // Exactly the scenario the user hit: encryption=NONE, no password.
        val width = 128
        val height = 128
        val pixels = ByteArray(width * height * 4) { (it and 0xFF).toByte() }

        val encoded = SteganographyService.encode(
            payload = Payload.Text("Plain message without encryption"),
            pixels = pixels,
            width = width,
            height = height,
            options = StegoOptions(
                encryption = EncryptionAlgorithm.NONE,
                compression = CompressionAlgorithm.NONE,
                password = "",
                maxImageEdge = null
            )
        )

        val decoded = SteganographyService.decode(encoded, width, height, "")
        assertTrue(decoded is Payload.Text)
        assertEquals("Plain message without encryption", (decoded as Payload.Text).text)
    }
}
