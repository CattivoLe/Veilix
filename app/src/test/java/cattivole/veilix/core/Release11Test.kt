package cattivole.veilix.core

import cattivole.veilix.core.compression.CompressionService
import cattivole.veilix.core.format.ImageSourceFormat
import cattivole.veilix.core.format.Payload
import cattivole.veilix.core.format.PayloadEnvelope
import cattivole.veilix.core.format.ZipArchive
import cattivole.veilix.core.stego.CompressionAlgorithm
import cattivole.veilix.core.stego.EncryptionAlgorithm
import cattivole.veilix.core.stego.SplitMix64
import cattivole.veilix.core.stego.StegoError
import cattivole.veilix.core.stego.StegoOptions
import cattivole.veilix.core.stego.SteganographyService
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Bug fixes, ZIP, formats and passwords from the 1.1 update, as on iOS. */
class Release11Test {

    // ─── iOS v3 images ────────────────────────────────────────────────────

    @Test fun ios_v3_plain_and_leftover_password() {
        val plain = TestImages.png("v3-plain.png")
        assertEquals("legacy-plain", (SteganographyService.decode(plain.pixels, plain.width, plain.height, "") as Payload.Text).text)
        val stale = TestImages.png("v3-stale.png")
        assertEquals("legacy-stale", (SteganographyService.decode(stale.pixels, stale.width, stale.height, "stale") as Payload.Text).text)
    }

    /** These use LZFSE and LZMA, which Android still can't decompress. */
    @Test fun ios_v3_apple_only_compression_fails_cleanly() {
        for ((name, pw) in listOf("v3-aes.png" to "пароль", "v3-chacha.png" to "pw2")) {
            val img = TestImages.png(name)
            try {
                SteganographyService.decode(img.pixels, img.width, img.height, pw)
                fail("expected UnsupportedAlgorithm for $name")
            } catch (_: StegoError.UnsupportedAlgorithm) { }
        }
    }

    // ─── Leftover password with no encryption ─────────────────────────────

    @Test fun none_ignores_leftover_password_on_both_sides() {
        val w = 128; val h = 128
        val out = SteganographyService.encode(
            Payload.Text("x"), TestImages.noise(w, h), w, h,
            StegoOptions(EncryptionAlgorithm.NONE, CompressionAlgorithm.ZLIB, "leftover", null)
        )
        assertEquals("x", (SteganographyService.decode(out, w, h, "") as Payload.Text).text)
        assertEquals("x", (SteganographyService.decode(out, w, h, "other") as Payload.Text).text)
    }

    // ─── Untrusted input ──────────────────────────────────────────────────

    @Test fun hostile_file_names_become_one_plain_component() {
        val cases = listOf(
            "../../Library/evil.plist", "/etc/passwd", "..", ".", "a/b\\c:d",
            "‮txt.exe", "  .hidden  ", "", "x\u0000y"
        )
        for (raw in cases) {
            val name = PayloadEnvelope.sanitizedFileName(raw)
            assertTrue(raw, name.isNotEmpty())
            assertFalse(raw, name.contains('/') || name.contains('\\') || name.startsWith("."))
            assertFalse(raw, name.contains('‮') || name.contains('\u0000'))
        }
        assertEquals("отчёт 2026.pdf", PayloadEnvelope.sanitizedFileName("отчёт 2026.pdf"))
        val decoded = PayloadEnvelope.decode(PayloadEnvelope.encode(Payload.File("../../evil", byteArrayOf(1))))
        assertFalse((decoded as Payload.File).name.contains('/'))
    }

    @Test fun decompression_bomb_is_refused() {
        val bomb = CompressionService.compress(ByteArray(CompressionService.MAX_DECOMPRESSED_SIZE + (1 shl 20)), CompressionAlgorithm.ZLIB)
        try {
            CompressionService.decompress(bomb, CompressionAlgorithm.ZLIB)
            fail("expected DecompressionFailed")
        } catch (_: StegoError.DecompressionFailed) { }
    }

    // ─── Formats ──────────────────────────────────────────────────────────

    @Test fun sniffs_formats() {
        val png = TestImages.resource("v4-aes.png")
        assertEquals(ImageSourceFormat.PNG, ImageSourceFormat.sniff(png))
        assertEquals(ImageSourceFormat.JPEG, ImageSourceFormat.sniff(byteArrayOf(-1, -40, -1, 0)))
        assertEquals(ImageSourceFormat.GIF, ImageSourceFormat.sniff("GIF89a".toByteArray()))
        assertEquals(ImageSourceFormat.LOSSY_WEBP, ImageSourceFormat.sniff("RIFF\u0000\u0000\u0000\u0000WEBPVP8 ".toByteArray()))
        assertEquals(ImageSourceFormat.UNKNOWN, ImageSourceFormat.sniff("RIFF\u0000\u0000\u0000\u0000WEBPVP8L".toByteArray()))
        assertEquals(ImageSourceFormat.HEIF, ImageSourceFormat.sniff(byteArrayOf(0, 0, 0, 24) + "ftypheic".toByteArray()))
        assertEquals(ImageSourceFormat.UNKNOWN, ImageSourceFormat.sniff(ByteArray(0)))
        assertTrue(ImageSourceFormat.PNG.canCarryData && ImageSourceFormat.UNKNOWN.canCarryData)
        assertFalse(ImageSourceFormat.JPEG.canCarryData || ImageSourceFormat.CAMERA.canCarryData)
        assertNull(ImageSourceFormat.CAMERA.lossyName)
    }

    // ─── ZIP ──────────────────────────────────────────────────────────────

    @Test fun zip_round_trip_and_determinism() {
        val png = TestImages.resource("v4-aes.png")
        val a = ZipArchive.makeStored("IMG_1234.png", png)
        assertTrue(ZipArchive.isArchive(a))
        assertArrayEquals(png, ZipArchive.firstPng(a))
        assertArrayEquals(a, ZipArchive.makeStored("IMG_1234.png", png))
        assertArrayEquals(png, ZipArchive.firstPng(ZipArchive.makeStored("картинка.png", png)))
    }

    @Test fun zip_from_ios_and_tools_opens_and_decodes() {
        for (name in listOf("v4-aes-zip-cli.zip", "v4-aes-zip-ditto.zip")) {
            val png = ZipArchive.firstPng(TestImages.resource(name)) ?: error("no png in $name")
            val img = png.inputStream().use(MiniPngDecoder::decode)
            assertEquals(name, "v4 golden AES", (SteganographyService.decode(img.pixels, img.width, img.height, "пароль") as Payload.Text).text)
        }
    }

    @Test fun zip_rejects_bad_entries_and_never_crashes() {
        assertNull(ZipArchive.firstPng(ZipArchive.makeStored("fake.png", "not an image".toByteArray())))
        assertNull(ZipArchive.firstPng(ZipArchive.makeStored("report.txt", TestImages.resource("v4-aes.png"))))
        assertNull(ZipArchive.firstPng(ByteArray(0)))
        assertNull(ZipArchive.firstPng(ByteArray(500)))

        val png = TestImages.encodePng(TestImages.noise(8, 8), 8, 8)
        val archive = ZipArchive.makeStored("IMG_1.png", png)
        for (len in 0 until archive.size step 37) assertNull(ZipArchive.firstPng(archive.copyOf(len)))
        val rng = SplitMix64(99)
        repeat(3000) {
            val damaged = archive.copyOf()
            repeat(1 + (rng.next() ushr 1).toInt() % 8) {
                damaged[((rng.next() ushr 1) % damaged.size).toInt()] = rng.next().toByte()
            }
            val got = ZipArchive.firstPng(damaged)
            assertTrue(got == null || got.contentEquals(png))
        }
    }

    // ─── Passwords ────────────────────────────────────────────────────────

    @Test fun weak_passwords_are_weak() {
        val cases = listOf(
            "password", "Password1!", "password123", "P@ssw0rd", "PASSWORD", "qwerty", "qwerty123",
            "qwertyuiop", "letmein", "iloveyou", "12345678", "1234567890", "111111111", "abcdefgh",
            "пароль", "Пароль123", "пар0ль", "йцукен", "йцукенгшщз", "абвгдежз", "привет",
            "parol123", "lozinka", "sifra2024", "a".repeat(24), "xzxzxzxzxzxzxzxz", "abababababab"
        )
        for (p in cases) assertTrue(p, PasswordStrength.estimatedBits(p) < 35)
    }

    @Test fun strong_and_generated_passwords_rate_well() {
        for (p in listOf("Tr0ub4dor&3xQ", "gT7#kP2!vL9q", "ю8Щк3ьЖп2фЛ")) assertTrue(p, PasswordStrength.estimatedBits(p) >= 60)
        assertTrue(PasswordStrength.estimatedBits("correct horse battery staple") >= 90)
        assertTrue(PasswordStrength.estimatedBits("серый кот спит на тёплой батарее") >= 90)
        val seen = HashSet<String>()
        repeat(500) {
            val p = PasswordGenerator.generate()
            assertTrue(p, Regex("[a-z2-9]{5}(-[a-z2-9]{5}){3}").matches(p))
            assertFalse(p, p.any { it in "il1o0" })
            assertTrue(p, PasswordStrength.estimatedBits(p) >= 90)
            seen += p
        }
        assertEquals(500, seen.size)
    }

    // ─── Memory ───────────────────────────────────────────────────────────

    @Test fun full_12mp_payload_round_trip() {
        val w = 4000; val h = 3000
        val rng = SplitMix64(11)
        val data = ByteArray(3_900_000) { rng.next().toByte() }
        val t = System.nanoTime()
        val out = SteganographyService.encode(
            Payload.File("big.bin", data), TestImages.noise(w, h), w, h,
            StegoOptions(EncryptionAlgorithm.AES_GCM, CompressionAlgorithm.NONE, "pw", null)
        )
        val back = SteganographyService.decode(out, w, h, "pw") as Payload.File
        println("12 MP full round trip: ${(System.nanoTime() - t) / 1_000_000} ms")
        assertArrayEquals(data, back.data)
        File("build/ios-check").mkdirs()
    }
}
