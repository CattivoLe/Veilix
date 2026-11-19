package cattivole.veilix.core

import cattivole.veilix.core.format.Payload
import cattivole.veilix.core.format.PayloadHeader
import cattivole.veilix.core.stego.CompressionAlgorithm
import cattivole.veilix.core.stego.EncryptionAlgorithm
import cattivole.veilix.core.stego.KeystreamRng
import cattivole.veilix.core.stego.PositionSet
import cattivole.veilix.core.stego.StealthFormat
import cattivole.veilix.core.stego.StegoError
import cattivole.veilix.core.stego.StegoOptions
import cattivole.veilix.core.stego.SteganographyService
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.text.Normalizer
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Format v4: whatever a password protects must not be recognizable without it. */
class StealthFormatTest {
    private val w = 128
    private val h = 128

    private fun embed(
        payload: Payload = Payload.Text("secret"),
        pixels: ByteArray = TestImages.noise(w, h),
        width: Int = w,
        height: Int = h,
        enc: EncryptionAlgorithm = EncryptionAlgorithm.AES_GCM,
        comp: CompressionAlgorithm = CompressionAlgorithm.ZLIB,
        password: String = "pw"
    ) = SteganographyService.encode(payload, pixels, width, height, StegoOptions(enc, comp, password, null))

    private inline fun <reified T : StegoError> assertThrows(block: () -> Unit) {
        try { block(); fail("expected ${T::class.simpleName}") } catch (e: StegoError) {
            assertTrue("expected ${T::class.simpleName}, got $e", e is T)
        }
    }

    // ─── Nothing gives it away ────────────────────────────────────────────

    @Test fun no_marker_in_the_image() {
        for (enc in listOf(EncryptionAlgorithm.AES_GCM, EncryptionAlgorithm.CHACHA_POLY)) {
            val out = embed(enc = enc)
            val header = ByteArray(4)
            for (bit in 0 until 32) {
                val v = out[StealthFormat.byteIndexFor(bit)].toInt() and 1
                header[bit / 8] = ((header[bit / 8].toInt() shl 1) or v).toByte()
            }
            assertFalse(header.contentEquals(PayloadHeader.MAGIC))
        }
    }

    @Test fun probe_sees_nothing_without_password() {
        val probe = SteganographyService.probe(embed(), w, h)
        assertFalse(probe.hasHiddenData)
        assertNull(probe.encryption)
        assertNotNull(probe.salt)
    }

    @Test fun empty_password_finds_nothing() {
        assertThrows<StegoError.NoHiddenData> { SteganographyService.decode(embed(), w, h, "") }
    }

    @Test fun plain_photo_and_wrong_password_fail_the_same_way() {
        assertThrows<StegoError.WrongPasswordOrCorrupted> { SteganographyService.decode(embed(), w, h, "wrong") }
        assertThrows<StegoError.WrongPasswordOrCorrupted> { SteganographyService.decode(TestImages.noise(w, h), w, h, "pw") }
    }

    @Test fun round_trip_every_cipher() {
        for (enc in listOf(EncryptionAlgorithm.AES_GCM, EncryptionAlgorithm.CHACHA_POLY)) {
            for (comp in listOf(CompressionAlgorithm.NONE, CompressionAlgorithm.ZLIB)) {
                val out = embed(Payload.Text("секрет $enc $comp"), enc = enc, comp = comp, password = "pässwörd")
                val got = SteganographyService.decode(out, w, h, "pässwörd") as Payload.Text
                assertEquals("секрет $enc $comp", got.text)
            }
        }
    }

    @Test fun unencrypted_payload_stays_findable() {
        val probe = SteganographyService.probe(embed(enc = EncryptionAlgorithm.NONE, password = ""), w, h)
        assertTrue(probe.hasHiddenData)
        assertEquals(EncryptionAlgorithm.NONE, probe.encryption)
    }

    @Test fun salt_is_new_for_every_image() {
        val a = SteganographyService.probe(embed(), w, h).salt!!
        val b = SteganographyService.probe(embed(), w, h).salt!!
        assertFalse(a.contentEquals(b))
    }

    /** LSB replacement evens out pairs (2i, 2i+1); LSB matching must not. */
    @Test fun histogram_keeps_its_shape() {
        val width = 200; val height = 200
        val cover = TestImages.noise(width, height, seed = 21)
        for (i in cover.indices) if (i % 4 != 3) cover[i] = (cover[i].toInt() and 0xFC).toByte()
        val rng = cattivole.veilix.core.stego.SplitMix64(22)
        val bytes = ByteArray(10_000) { rng.next().toByte() }
        val payload = Payload.File("x", bytes)

        fun imbalance(p: ByteArray): Double {
            val hist = IntArray(256)
            for (i in p.indices) if (i % 4 != 3) hist[p[i].toInt() and 0xFF]++
            var chi = 0.0
            for (e in 0 until 256 step 2) {
                val t = hist[e] + hist[e + 1]
                if (t > 0) chi += (hist[e] - hist[e + 1]).toDouble().let { it * it } / t
            }
            return chi
        }
        val matched = embed(payload, cover, width, height, comp = CompressionAlgorithm.NONE)
        val replaced = embed(payload, cover, width, height, EncryptionAlgorithm.NONE, CompressionAlgorithm.NONE, "")
        assertTrue(imbalance(matched) > imbalance(replaced) * 2)
    }

    @Test fun changed_bits_are_detected() {
        val rng = cattivole.veilix.core.stego.SplitMix64(5)
        val bytes = ByteArray(1200) { rng.next().toByte() }
        val out = embed(Payload.File("x", bytes), TestImages.noise(64, 64), 64, 64, comp = CompressionAlgorithm.NONE)
        for (i in out.indices step 400) if (i % 4 != 3) out[i] = (out[i].toInt() xor 1).toByte()
        assertThrows<StegoError.WrongPasswordOrCorrupted> { SteganographyService.decode(out, 64, 64, "pw") }
    }

    @Test fun password_in_any_unicode_form_opens_the_image() {
        val composed = Normalizer.normalize("й", Normalizer.Form.NFC)
        val decomposed = Normalizer.normalize("й", Normalizer.Form.NFD)
        assertNotEquals(composed, decomposed)
        val got = SteganographyService.decode(embed(password = composed), w, h, decomposed) as Payload.Text
        assertEquals("secret", got.text)
    }

    /** The capacity indicator promises this much. It must really fit. */
    @Test fun advertised_capacity_fits() {
        val advertised = (64 * 64 * 3 - PayloadHeader.BIT_SIZE) * 9 / 10 / 8
        val text = "a".repeat(advertised - 1 - StealthFormat.TAG_SIZE)
        val out = embed(Payload.Text(text), TestImages.noise(64, 64), 64, 64, comp = CompressionAlgorithm.NONE)
        assertEquals(text, (SteganographyService.decode(out, 64, 64, "pw") as Payload.Text).text)
    }

    @Test fun tiny_image_is_rejected() {
        assertThrows<StegoError.MessageTooLarge> { embed(pixels = TestImages.noise(4, 4), width = 4, height = 4) }
    }

    // ─── Keystream and positions ──────────────────────────────────────────

    /** Pins the construction in docs/FORMAT.md: AES-GCM ciphertext of zeros. */
    @Test fun keystream_matches_documented_construction_across_chunks() {
        val key = ByteArray(32) { 7 }
        val rng = KeystreamRng(key)
        val values = LongArray(140_000) { rng.next() }

        fun gcmBlock(chunk: Long, offset: Int): Long {
            val nonce = ByteArray(12)
            for (i in 0 until 8) nonce[4 + i] = (chunk ushr (56 - 8 * i)).toByte()
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            val ct = c.doFinal(ByteArray(offset + 8))
            var v = 0L
            for (i in 0 until 8) v = (v shl 8) or (ct[offset + i].toLong() and 0xFF)
            return v
        }
        assertEquals(gcmBlock(0, 0), values[0])
        assertEquals(gcmBlock(0, 8 * 1000), values[1000])
        assertEquals(gcmBlock(1, 0), values[131_072])
        assertEquals(gcmBlock(1, 8 * 7), values[131_079])
    }

    @Test fun position_set_reports_whether_free() {
        val set = PositionSet(200)
        for (p in listOf(0, 63, 64, 199)) {
            assertTrue(set.insert(p))
            assertFalse(set.insert(p))
        }
    }

    // ─── iOS test vectors ─────────────────────────────────────────────────

    @Test fun decodes_ios_v4_aes() {
        val img = TestImages.png("v4-aes.png")
        assertFalse(SteganographyService.probe(img.pixels, img.width, img.height).hasHiddenData)
        val got = SteganographyService.decode(img.pixels, img.width, img.height, "пароль") as Payload.Text
        assertEquals("v4 golden AES", got.text)
    }

    @Test fun decodes_ios_v4_chacha_file() {
        val img = TestImages.png("v4-chacha.png")
        val got = SteganographyService.decode(img.pixels, img.width, img.height, "pw2") as Payload.File
        assertEquals("доклад.txt", got.name)
        assertArrayEquals("v4 golden file".toByteArray(), got.data)
    }

    @Test fun ios_v4_rejects_wrong_password() {
        val img = TestImages.png("v4-aes.png")
        assertThrows<StegoError.WrongPasswordOrCorrupted> {
            SteganographyService.decode(img.pixels, img.width, img.height, "nope")
        }
    }

    // ─── Android → iOS ────────────────────────────────────────────────────

    /**
     * Writes Android-made v4 images for the iOS decoder to check (run by hand
     * against the iOS app). The files go to app/build/ios-check.
     */
    @Test fun writes_images_for_ios_check() {
        val dir = File("build/ios-check").apply { mkdirs() }
        val width = 160; val height = 120
        val text = embed(Payload.Text("android v4 AES"), TestImages.noise(width, height), width, height, password = "пароль")
        File(dir, "android-v4-aes.png").writeBytes(TestImages.encodePng(text, width, height))
        val file = embed(
            Payload.File("отчёт.txt", "android v4 file".toByteArray()),
            TestImages.noise(width, height), width, height,
            EncryptionAlgorithm.CHACHA_POLY, CompressionAlgorithm.NONE, "pw2"
        )
        File(dir, "android-v4-chacha.png").writeBytes(TestImages.encodePng(file, width, height))
        val plain = embed(
            Payload.Text("android v3 plain"), TestImages.noise(width, height), width, height,
            EncryptionAlgorithm.NONE, CompressionAlgorithm.ZLIB, "leftover"
        )
        File(dir, "android-v3-plain.png").writeBytes(TestImages.encodePng(plain, width, height))

        // And they read back here through the same PNG path.
        val back = TestImages.encodePng(text, width, height).inputStream().use(MiniPngDecoder::decode)
        assertEquals("android v4 AES", (SteganographyService.decode(back.pixels, width, height, "пароль") as Payload.Text).text)
    }
}
