package cattivole.veilix.core

import cattivole.veilix.core.format.Payload
import cattivole.veilix.core.stego.CompressionAlgorithm
import cattivole.veilix.core.stego.EncryptionAlgorithm
import cattivole.veilix.core.stego.StegoError
import cattivole.veilix.core.stego.StegoOptions
import cattivole.veilix.core.stego.SteganographyService
import org.junit.Assert.*
import org.junit.Test

/**
 * End-to-end round-trip on synthetic pixel buffers (no Bitmap involved).
 * Covers every supported algorithm combination. If any of these break,
 * cross-platform decode with iOS will also break.
 */
class SteganographyServiceTest {

    private val width = 256
    private val height = 256
    /** Smooth deterministic gradient — looks like a real photo to the encoder. */
    private val pixels: ByteArray = ByteArray(width * height * 4).also { buf ->
        for (y in 0 until height) {
            for (x in 0 until width) {
                val o = (y * width + x) * 4
                buf[o    ] = x.toByte()                            // R
                buf[o + 1] = y.toByte()                            // G
                buf[o + 2] = ((x + y) and 0xFF).toByte()           // B
                buf[o + 3] = 0xFF.toByte()                         // A
            }
        }
    }

    @Test
    fun roundtrip_text_aesgcm_zlib() {
        roundTripText(
            "Meet at the riverside at sundown. Bring the second envelope.",
            EncryptionAlgorithm.AES_GCM,
            CompressionAlgorithm.ZLIB,
            password = "p@ssw0rd-1!"
        )
    }

    @Test
    fun roundtrip_text_chacha_none() {
        roundTripText(
            "Сообщение на кириллице с emoji 🔐 и спецсимволами!",
            EncryptionAlgorithm.CHACHA_POLY,
            CompressionAlgorithm.NONE,
            password = "пароль-1234"
        )
    }

    @Test
    fun roundtrip_text_none_zlib() {
        roundTripText(
            "Plain unencrypted (compressed-only) message.",
            EncryptionAlgorithm.NONE,
            CompressionAlgorithm.ZLIB,
            password = ""
        )
    }

    @Test
    fun roundtrip_file_aesgcm_zlib() {
        val fileBytes = ByteArray(2048) { ((it * 31) and 0xFF).toByte() }
        val encoded = SteganographyService.encode(
            payload = Payload.File("doc.bin", fileBytes),
            pixels = pixels,
            width = width,
            height = height,
            options = StegoOptions(
                encryption = EncryptionAlgorithm.AES_GCM,
                compression = CompressionAlgorithm.ZLIB,
                password = "secure-pw",
                maxImageEdge = null      // service won't resize raw byte buffers
            )
        )
        val decoded = SteganographyService.decode(encoded, width, height, "secure-pw")
        assertTrue(decoded is Payload.File)
        decoded as Payload.File
        assertEquals("doc.bin", decoded.name)
        assertArrayEquals(fileBytes, decoded.data)
    }

    @Test
    fun wrong_password_throws() {
        val encoded = SteganographyService.encode(
            payload = Payload.Text("secret"),
            pixels = pixels,
            width = width,
            height = height,
            options = StegoOptions(
                encryption = EncryptionAlgorithm.AES_GCM,
                compression = CompressionAlgorithm.ZLIB,
                password = "correct"
            )
        )
        try {
            SteganographyService.decode(encoded, width, height, "wrong")
            fail("expected WrongPasswordOrCorrupted")
        } catch (_: StegoError.WrongPasswordOrCorrupted) { /* ok */ }
    }

    @Test
    fun probe_finds_only_unencrypted_data() {
        val encoded = SteganographyService.encode(
            payload = Payload.Text("test"),
            pixels = pixels,
            width = width,
            height = height,
            options = StegoOptions(
                encryption = EncryptionAlgorithm.AES_GCM,
                compression = CompressionAlgorithm.ZLIB,
                password = "x"
            )
        )
        // Since format v4, password-protected data carries no readable
        // marker: the probe must NOT find it. Unencrypted data stays findable.
        val probe = SteganographyService.probe(encoded, width, height)
        assertFalse(probe.hasHiddenData)

        val plain = SteganographyService.encode(
            payload = Payload.Text("test"),
            pixels = pixels,
            width = width,
            height = height,
            options = StegoOptions(EncryptionAlgorithm.NONE, CompressionAlgorithm.ZLIB, "", null)
        )
        val plainProbe = SteganographyService.probe(plain, width, height)
        assertTrue(plainProbe.hasHiddenData)
        assertEquals(EncryptionAlgorithm.NONE, plainProbe.encryption)
        assertEquals(CompressionAlgorithm.ZLIB, plainProbe.compression)
    }

    @Test
    fun probe_finds_nothing_in_clean_image() {
        val probe = SteganographyService.probe(pixels, width, height)
        assertFalse(probe.hasHiddenData)
    }

    @Test
    fun empty_payload_rejected() {
        try {
            SteganographyService.encode(
                payload = Payload.Text(""),
                pixels = pixels,
                width = width,
                height = height,
                options = StegoOptions.DEFAULT.copy(password = "x")
            )
            fail("expected EmptyMessage")
        } catch (_: StegoError.EmptyMessage) { /* ok */ }
    }

    @Test
    fun missing_password_rejected_when_encryption_requires_it() {
        try {
            SteganographyService.encode(
                payload = Payload.Text("test"),
                pixels = pixels,
                width = width,
                height = height,
                options = StegoOptions.DEFAULT.copy(password = "")
            )
            fail("expected PasswordRequired")
        } catch (_: StegoError.PasswordRequired) { /* ok */ }
    }

    // ─── helpers ───

    private fun roundTripText(
        msg: String,
        enc: EncryptionAlgorithm,
        comp: CompressionAlgorithm,
        password: String
    ) {
        val encoded = SteganographyService.encode(
            payload = Payload.Text(msg),
            pixels = pixels,
            width = width,
            height = height,
            options = StegoOptions(enc, comp, password, maxImageEdge = null)
        )
        // Encoded image must differ from input.
        assertFalse(pixels.contentEquals(encoded))
        // Same dimensions.
        assertEquals(pixels.size, encoded.size)

        val decoded = SteganographyService.decode(encoded, width, height, password)
        assertTrue(decoded is Payload.Text)
        assertEquals(msg, (decoded as Payload.Text).text)
    }
}
