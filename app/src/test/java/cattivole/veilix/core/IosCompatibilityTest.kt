package cattivole.veilix.core

import cattivole.veilix.core.crypto.CryptoService
import cattivole.veilix.core.format.Payload
import cattivole.veilix.core.format.PayloadCodec
import cattivole.veilix.core.format.PayloadHeader
import cattivole.veilix.core.stego.SteganographyService
import org.junit.Assert.*
import org.junit.Test

/**
 * End-to-end cross-platform regression. Loads a PNG produced by the iOS
 * Veilix encoder and decodes it with the Android core.
 */
class IosCompatibilityTest {

    @Test
    fun decodes_ios_encoded_image_with_no_encryption() {
        val stream = javaClass.classLoader.getResourceAsStream("v3-plain-cat.png")
            ?: error("fixture missing: test-vectors/v3-plain-cat.png")

        val image = stream.use(MiniPngDecoder::decode)
        println("Fixture: ${image.width} × ${image.height} px (${image.pixels.size} bytes)")

        // 1. Read header bytes sequentially (matches both iOS and Android).
        val headerData = readSequentialHeaderBytes(image.pixels)
        val header = PayloadCodec.decode(headerData)
        println("Header: version=${header.version}  enc=${header.encryption}  comp=${header.compression}")
        println("        bodyLength=${header.bodyLength}")
        println("        salt  = ${header.salt.toHex()}")
        println("        nonce = ${header.nonce.toHex()}")

        // 2. Derive the seed exactly like the decoder does, then dump every
        //    intermediate so we can compare with iOS:
        val pwdBytes = "".toByteArray(Charsets.UTF_8)
        println("\nKDF trace (password=empty, version=${header.version}):")
        println("  pwd UTF-8 bytes (${pwdBytes.size}): ${pwdBytes.toHex()}")

        val seed = CryptoService.deriveSeed(
            password = "",
            salt = header.salt,
            version = header.version.toInt()
        )
        println("  seed (Long, hex) = 0x${seed.toULong().toString(16).padStart(16, '0')}")
        println("  seed (Long, dec) = $seed")

        // 3. Full pipeline.
        val probe = SteganographyService.probe(image.pixels, image.width, image.height)
        assertTrue("Probe should detect hidden data", probe.hasHiddenData)

        try {
            val payload = SteganographyService.decode(
                image.pixels, image.width, image.height, password = ""
            )
            if (payload is Payload.Text) {
                println("\n✅ Recovered text (${payload.text.length} chars):\n---\n${payload.text}\n---")
            }
            assertTrue(payload is Payload.Text)
        } catch (e: Throwable) {
            println("\n❌ Decode failed: ${e.javaClass.simpleName} — ${e.message}")
            throw e
        }
    }

    private fun readSequentialHeaderBytes(pixels: ByteArray): ByteArray {
        val bits = ByteArray(PayloadHeader.BIT_SIZE)
        for (i in 0 until PayloadHeader.BIT_SIZE) {
            val pixel = i / 3
            val channel = i % 3
            val byteIdx = pixel * 4 + channel
            bits[i] = (pixels[byteIdx].toInt() and 1).toByte()
        }
        val out = ByteArray(PayloadHeader.SIZE)
        var cur = 0
        for (i in bits.indices) {
            cur = (cur shl 1) or (bits[i].toInt() and 1)
            if (i and 7 == 7) { out[i ushr 3] = cur.toByte(); cur = 0 }
        }
        return out
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it.toInt() and 0xFF) }
}
