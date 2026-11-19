package cattivole.veilix.core

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.Inflater
import kotlin.math.abs

/**
 * Minimal PNG decoder for use in unit tests where `java.awt.image` and
 * Android's `BitmapFactory` aren't available. Handles the only PNG flavor
 * Veilix actually exports: 8-bit/channel RGBA, non-interlaced, deflate
 * compressed, with the five standard scanline filters.
 *
 * Output is iOS-compatible RGBA byte layout (the same one
 * `BitmapPixels.toRgba` produces).
 */
internal object MiniPngDecoder {

    data class Decoded(val pixels: ByteArray, val width: Int, val height: Int)

    private val SIGNATURE = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    )

    fun decode(stream: InputStream): Decoded {
        val all = stream.readBytes()
        require(all.size > 8) { "PNG too short" }
        for (i in 0..7) require(all[i] == SIGNATURE[i]) { "Not a PNG (bad signature)" }

        var offset = 8
        var width = 0
        var height = 0
        var bitDepth = 0
        var colorType = 0
        var interlace = 0
        val idat = ByteArrayOutputStream()

        while (offset < all.size) {
            val length = readUInt32BE(all, offset)
            val type = String(all, offset + 4, 4, Charsets.US_ASCII)
            val dataStart = offset + 8
            when (type) {
                "IHDR" -> {
                    width = readUInt32BE(all, dataStart).toInt()
                    height = readUInt32BE(all, dataStart + 4).toInt()
                    bitDepth = all[dataStart + 8].toInt() and 0xFF
                    colorType = all[dataStart + 9].toInt() and 0xFF
                    // bytes 10, 11, 12 = compression, filter, interlace
                    interlace = all[dataStart + 12].toInt() and 0xFF
                }
                "IDAT" -> idat.write(all, dataStart, length.toInt())
                "IEND" -> break
            }
            // chunk = 4 length + 4 type + data + 4 CRC
            offset = dataStart + length.toInt() + 4
        }

        require(bitDepth == 8) { "Only 8-bit channels supported (got $bitDepth)" }
        require(colorType == 6) { "Only RGBA (color type 6) supported (got $colorType)" }
        require(interlace == 0) { "Interlaced PNG not supported" }

        // Inflate all IDAT bytes into raw filtered scanlines.
        val inflater = Inflater()
        inflater.setInput(idat.toByteArray())
        val raw = ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        while (!inflater.finished()) {
            val n = inflater.inflate(buf)
            if (n == 0) {
                if (inflater.needsInput() || inflater.needsDictionary()) break
            }
            raw.write(buf, 0, n)
        }
        inflater.end()

        return Decoded(
            pixels = unfilter(raw.toByteArray(), width, height),
            width = width,
            height = height
        )
    }

    /**
     * Apply inverse PNG scanline filtering (None / Sub / Up / Average / Paeth)
     * to the inflated raw stream. Result is a `width × height × 4` RGBA byte
     * buffer.
     */
    private fun unfilter(filtered: ByteArray, width: Int, height: Int): ByteArray {
        val bpp = 4
        val stride = width * bpp
        val out = ByteArray(width * height * bpp)
        var src = 0
        val prevRow = ByteArray(stride)
        val curRow = ByteArray(stride)

        for (y in 0 until height) {
            val filterType = filtered[src].toInt() and 0xFF
            src++
            // Copy this row's raw bytes
            System.arraycopy(filtered, src, curRow, 0, stride)
            src += stride

            when (filterType) {
                0 -> { /* None — already in curRow */ }
                1 -> {  // Sub
                    for (x in bpp until stride) {
                        curRow[x] = (curRow[x].toInt() + (curRow[x - bpp].toInt() and 0xFF)).toByte()
                    }
                }
                2 -> {  // Up
                    for (x in 0 until stride) {
                        curRow[x] = (curRow[x].toInt() + (prevRow[x].toInt() and 0xFF)).toByte()
                    }
                }
                3 -> {  // Average
                    for (x in 0 until stride) {
                        val left = if (x >= bpp) curRow[x - bpp].toInt() and 0xFF else 0
                        val up = prevRow[x].toInt() and 0xFF
                        curRow[x] = (curRow[x].toInt() + (left + up) / 2).toByte()
                    }
                }
                4 -> {  // Paeth
                    for (x in 0 until stride) {
                        val left = if (x >= bpp) curRow[x - bpp].toInt() and 0xFF else 0
                        val up = prevRow[x].toInt() and 0xFF
                        val upLeft = if (x >= bpp) prevRow[x - bpp].toInt() and 0xFF else 0
                        curRow[x] = (curRow[x].toInt() + paeth(left, up, upLeft)).toByte()
                    }
                }
                else -> error("Unknown PNG filter type $filterType at row $y")
            }

            System.arraycopy(curRow, 0, out, y * stride, stride)
            System.arraycopy(curRow, 0, prevRow, 0, stride)
        }
        return out
    }

    private fun paeth(a: Int, b: Int, c: Int): Int {
        val p = a + b - c
        val pa = abs(p - a)
        val pb = abs(p - b)
        val pc = abs(p - c)
        return when {
            pa <= pb && pa <= pc -> a
            pb <= pc -> b
            else -> c
        }
    }

    private fun readUInt32BE(data: ByteArray, off: Int): Long {
        return ((data[off    ].toLong() and 0xFF) shl 24) or
               ((data[off + 1].toLong() and 0xFF) shl 16) or
               ((data[off + 2].toLong() and 0xFF) shl 8)  or
                (data[off + 3].toLong() and 0xFF)
    }
}
