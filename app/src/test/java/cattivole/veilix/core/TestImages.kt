package cattivole.veilix.core

import cattivole.veilix.core.stego.SplitMix64
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater

internal object TestImages {
    /** Noisy opaque image. Seeded, so it is the same on every run. */
    fun noise(width: Int = 128, height: Int = 128, seed: Long = 7): ByteArray {
        val rng = SplitMix64(seed)
        val out = ByteArray(width * height * 4)
        for (i in 0 until width * height) {
            for (c in 0 until 3) out[i * 4 + c] = rng.next().toByte()
            out[i * 4 + 3] = 0xFF.toByte()
        }
        return out
    }

    fun resource(name: String): ByteArray =
        javaClass.classLoader!!.getResourceAsStream(name)?.use { it.readBytes() }
            ?: error("fixture missing: test-vectors/$name")

    fun png(name: String): MiniPngDecoder.Decoded =
        resource(name).inputStream().use(MiniPngDecoder::decode)

    /** Minimal RGBA PNG writer, so tests can hand files to the iOS decoder. */
    fun encodePng(pixels: ByteArray, width: Int, height: Int): ByteArray {
        val raw = ByteArrayOutputStream()
        for (y in 0 until height) {
            raw.write(0)
            raw.write(pixels, y * width * 4, width * 4)
        }
        val deflater = Deflater(9)
        deflater.setInput(raw.toByteArray()); deflater.finish()
        val idat = ByteArrayOutputStream()
        val buf = ByteArray(1 shl 16)
        while (!deflater.finished()) idat.write(buf, 0, deflater.deflate(buf))
        deflater.end()

        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
        fun chunk(type: String, data: ByteArray) {
            val t = type.toByteArray(Charsets.US_ASCII)
            fun u32(v: Long) { for (s in 24 downTo 0 step 8) out.write(((v ushr s) and 0xFF).toInt()) }
            u32(data.size.toLong()); out.write(t); out.write(data)
            u32(CRC32().apply { update(t); update(data) }.value)
        }
        val ihdr = ByteArrayOutputStream().apply {
            for (v in listOf(width, height)) for (s in 24 downTo 0 step 8) write((v ushr s) and 0xFF)
            write(8); write(6); write(0); write(0); write(0)
        }.toByteArray()
        chunk("IHDR", ihdr); chunk("IDAT", idat.toByteArray()); chunk("IEND", ByteArray(0))
        return out.toByteArray()
    }
}
