package cattivole.veilix.core.bitmap

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import cattivole.veilix.core.stego.SteganographyService

/**
 * Adapter between Android [Bitmap] and the RGBA-laid-out [ByteArray] that
 * [SteganographyService] operates on.
 *
 * **Critical:** the byte order must exactly match iOS's
 * `UIImage.pixelData()` (R, G, B, A per pixel, premultiplied-last) — that's
 * what makes the embedded payload bit-identical and lets one platform decode
 * what the other encoded.
 *
 * `Bitmap.Config.ARGB_8888` stores each pixel as a 32-bit `Int` packed as
 * `0xAARRGGBB`. We unpack into the iOS-style RGBA byte order.
 */
object BitmapPixels {

    /** Extract pixels into RGBA-byte layout (size = width × height × 4). */
    fun toRgba(bitmap: Bitmap): RgbaBuffer {
        val source = ensureArgb8888(bitmap)
        val w = source.width
        val h = source.height
        val ints = IntArray(w * h)
        source.getPixels(ints, 0, w, 0, 0, w, h)

        val out = ByteArray(w * h * 4)
        for (i in ints.indices) {
            val p = ints[i]
            val r = (p ushr 16) and 0xFF
            val g = (p ushr 8)  and 0xFF
            val b =  p          and 0xFF
            val a = (p ushr 24) and 0xFF
            val o = i * 4
            out[o    ] = r.toByte()
            out[o + 1] = g.toByte()
            out[o + 2] = b.toByte()
            out[o + 3] = a.toByte()
        }
        return RgbaBuffer(out, w, h)
    }

    /** Build a new Bitmap from RGBA-byte layout. Always ARGB_8888. */
    fun fromRgba(buffer: RgbaBuffer): Bitmap {
        val w = buffer.width
        val h = buffer.height
        val ints = IntArray(w * h)
        val src = buffer.pixels
        for (i in ints.indices) {
            val o = i * 4
            val r = src[o    ].toInt() and 0xFF
            val g = src[o + 1].toInt() and 0xFF
            val b = src[o + 2].toInt() and 0xFF
            val a = src[o + 3].toInt() and 0xFF
            ints[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.setPixels(ints, 0, w, 0, 0, w, h)
        return bmp
    }

    /**
     * Resize a bitmap so its long edge is at most [maxEdge]. Mirrors iOS's
     * `UIImage.downscaledForStego(maxEdge:)`. Returns the original if it
     * already fits.
     *
     * @param maxEdge `null` means keep original size.
     */
    fun downscaledForStego(bitmap: Bitmap, maxEdge: Int? = 2048): Bitmap {
        if (maxEdge == null) return ensureArgb8888(bitmap)
        val longEdge = maxOf(bitmap.width, bitmap.height)
        if (longEdge <= maxEdge) return ensureArgb8888(bitmap)

        val scale = maxEdge.toFloat() / longEdge
        val newW = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val newH = (bitmap.height * scale).toInt().coerceAtLeast(1)

        val resized = Bitmap.createBitmap(newW, newH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(resized)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        val matrix = Matrix().apply { setScale(scale, scale) }
        canvas.drawBitmap(bitmap, matrix, paint)
        return resized
    }

    /**
     * The carrier as it will be embedded into: downscaled per [maxEdge] and
     * flattened onto white so every pixel is opaque. iOS stores colour
     * premultiplied and PNG stores it straight, so below alpha 255 the RGB
     * values — and the hidden bits — change on the way through export, and
     * at alpha 0 the colour isn't stored at all. Mirrors iOS `flattenToOpaque`.
     */
    fun prepareCarrier(bitmap: Bitmap, maxEdge: Int?): Bitmap {
        val scaled = downscaledForStego(bitmap, maxEdge)
        if (!scaled.hasAlpha()) return scaled
        val out = Bitmap.createBitmap(scaled.width, scaled.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(scaled, 0f, 0f, null)
        out.setHasAlpha(false)
        return out
    }

    /**
     * Some sources (HEIC decoders, picker pipelines) hand back bitmaps in
     * `HARDWARE` or `RGB_565` configs. Force ARGB_8888 before pixel
     * extraction — otherwise getPixels() silently quantises and we can no
     * longer recover the LSB bits exactly.
     */
    private fun ensureArgb8888(bitmap: Bitmap): Bitmap {
        if (bitmap.config == Bitmap.Config.ARGB_8888) return bitmap
        return bitmap.copy(Bitmap.Config.ARGB_8888, /* isMutable = */ false)
            ?: throw IllegalStateException("Bitmap.copy returned null")
    }
}

/**
 * Pixel buffer in iOS-compatible RGBA byte order, sized exactly
 * `width × height × 4`.
 */
data class RgbaBuffer(
    val pixels: ByteArray,
    val width: Int,
    val height: Int
) {
    init {
        require(pixels.size == width * height * 4) {
            "RgbaBuffer size mismatch: expected ${width * height * 4}, got ${pixels.size}"
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RgbaBuffer) return false
        return width == other.width && height == other.height && pixels.contentEquals(other.pixels)
    }
    override fun hashCode(): Int = 31 * (31 * width + height) + pixels.contentHashCode()
}
