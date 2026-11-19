package cattivole.veilix.core.stego

import cattivole.veilix.core.compression.CompressionService
import cattivole.veilix.core.crypto.CryptoService
import cattivole.veilix.core.format.Payload
import cattivole.veilix.core.format.PayloadCodec
import cattivole.veilix.core.format.PayloadEnvelope
import cattivole.veilix.core.format.PayloadHeader

/**
 * Pure-JVM core. Operates on RGBA-laid-out [ByteArray] pixel buffers
 * (size = width × height × 4, byte order R,G,B,A per pixel) to stay
 * byte-identical with iOS's `UIImage.pixelData()` output.
 *
 * Higher layers (Compose UI / Bitmap interop) convert `Bitmap` ↔ this
 * buffer and call into here. Keeping the service Bitmap-free makes it
 * unit-testable from pure JVM tests.
 */
object SteganographyService {

    /**
     * Result of `probe(...)`: tells the UI whether the carrier image already
     * contains a valid Veilix header, and what cipher/compression it uses.
     */
    data class ProbeResult(
        val hasHiddenData: Boolean,
        val encryption: EncryptionAlgorithm? = null,
        val compression: CompressionAlgorithm? = null,
        /**
         * Identifies "this image" for the decode back-off: the header's salt,
         * or the bytes a v4 salt would occupy when no header is readable.
         */
        val salt: ByteArray? = null
    ) {
        val requiresPassword: Boolean
            get() = encryption?.requiresPassword ?: false
    }

    // ─────────────────────────────────────────────────────────────────────
    // ENCODE
    // ─────────────────────────────────────────────────────────────────────

    /**
     * @param pixels  RGBA-laid-out bytes (size = width × height × 4)
     * @param width   pixel width
     * @param height  pixel height
     * @return a NEW pixel buffer containing the embedded payload
     */
    fun encode(
        payload: Payload,
        pixels: ByteArray,
        width: Int,
        height: Int,
        options: StegoOptions
    ): ByteArray {
        if (payload.rawByteCount == 0) throw StegoError.EmptyMessage
        if (options.encryption.requiresPassword && options.password.isEmpty()) {
            throw StegoError.PasswordRequired
        }
        require(pixels.size == width * height * 4) {
            "pixels.size != width*height*4"
        }

        val pixelCount = width * height
        val rgbCount = pixelCount * 3
        if (rgbCount <= PayloadHeader.BIT_SIZE) {
            throw StegoError.MessageTooLarge
        }

        // 1) Envelope → compress
        val envelopeBytes = PayloadEnvelope.encode(payload)
        // The decoder refuses to inflate past this limit, so never produce a
        // payload the recipient would be unable to open.
        if (envelopeBytes.size > CompressionService.MAX_DECOMPRESSED_SIZE) {
            throw StegoError.MessageTooLarge
        }
        val compressed = CompressionService.compress(envelopeBytes, options.compression)

        // 2) Embed: hidden format with a password, marked format without one.
        val out = pixels.copyOf()
        if (options.encryption.requiresPassword) {
            StealthFormat.embed(
                plaintext = compressed,
                encryption = options.encryption,
                compression = options.compression,
                password = options.password,
                pixels = out,
                rgbCount = rgbCount
            )
        } else {
            embedMarked(compressed, options.compression, out, rgbCount)
        }
        return out
    }

    /**
     * Format v3 for payloads without a password. Nothing protects these and
     * anyone may read them, so the header stays readable: it is how the
     * recipient's app finds the data without being given anything.
     */
    private fun embedMarked(
        body: ByteArray,
        compression: CompressionAlgorithm,
        pixels: ByteArray,
        rgbCount: Int
    ) {
        // Keep some slack so rejection sampling stays fast.
        if (body.size.toLong() * 8 > (rgbCount - PayloadHeader.BIT_SIZE).toLong() * 9 / 10) {
            throw StegoError.MessageTooLarge
        }
        val salt = CryptoService.randomBytes(CryptoService.SALT_SIZE)
        val header = PayloadHeader(
            version = PayloadHeader.CURRENT_VERSION,
            encryption = EncryptionAlgorithm.NONE,
            compression = compression,
            salt = salt,
            nonce = ByteArray(CryptoService.NONCE_SIZE),
            bodyLength = body.size
        )
        embedSequential(bitsOf(PayloadCodec.encode(header)), pixels, startOffset = 0)

        // The recipient is never asked for a password here, so positions are
        // always keyed to the empty one.
        val seed = CryptoService.deriveSeed("", salt, PayloadHeader.CURRENT_VERSION.toInt())
        embedRandom(bitsOf(body), pixels, rgbCount, PayloadHeader.BIT_SIZE, seed)
    }

    // ─────────────────────────────────────────────────────────────────────
    // DECODE
    // ─────────────────────────────────────────────────────────────────────

    fun decode(
        pixels: ByteArray,
        width: Int,
        height: Int,
        password: String
    ): Payload {
        require(pixels.size == width * height * 4) {
            "pixels.size != width*height*4"
        }
        val rgbCount = width * height * 3
        if (rgbCount <= PayloadHeader.BIT_SIZE) {
            throw StegoError.NoHiddenData
        }

        val headerBits = readSequential(pixels, startOffset = 0, bitCount = PayloadHeader.BIT_SIZE)
        val headerData = bytesFromBits(headerBits)
        val header = try {
            PayloadCodec.decode(headerData)
        } catch (e: StegoError.UnsupportedVersion) {
            throw e
        } catch (e: StegoError.UnsupportedAlgorithm) {
            throw e
        } catch (e: StegoError) {
            // No readable header: either nothing is here or it's a v4 payload,
            // and only the password can tell which.
            if (password.isEmpty()) throw e
            val extracted = StealthFormat.extract(pixels, rgbCount, password)
            val message = CompressionService.decompress(extracted.plaintext, extracted.compression)
            return PayloadEnvelope.decode(message)
        }

        if (header.encryption.requiresPassword && password.isEmpty()) {
            throw StegoError.PasswordRequired
        }

        val bodyBitCountLong = header.bodyLength.toLong() * 8
        if (header.bodyLength < 0 || bodyBitCountLong > rgbCount - PayloadHeader.BIT_SIZE) {
            throw StegoError.HeaderCorrupted
        }
        val bodyBitCount = bodyBitCountLong.toInt()

        if (header.encryption != EncryptionAlgorithm.NONE) {
            return decodeBody(header, pixels, rgbCount, bodyBitCount, password)
        }
        // Unencrypted payloads are keyed to the empty password. Images
        // exported by earlier builds may be keyed to a password that was left
        // in the hidden field, so whatever is typed now is tried as a fallback.
        return try {
            decodeBody(header, pixels, rgbCount, bodyBitCount, "")
        } catch (e: StegoError) {
            if (password.isEmpty()) throw e
            decodeBody(header, pixels, rgbCount, bodyBitCount, password)
        }
    }

    private fun decodeBody(
        header: PayloadHeader,
        pixels: ByteArray,
        rgbCount: Int,
        bodyBitCount: Int,
        password: String
    ): Payload {
        val seed = CryptoService.deriveSeed(
            password = password,
            salt = header.salt,
            version = header.version.toInt()
        )
        val bodyBits = readRandom(
            pixels = pixels,
            rgbCount = rgbCount,
            reservedPrefix = PayloadHeader.BIT_SIZE,
            bitCount = bodyBitCount,
            seed = seed
        )
        val encrypted = bytesFromBits(bodyBits)

        val compressed = when (header.encryption) {
            EncryptionAlgorithm.NONE -> encrypted
            EncryptionAlgorithm.AES_GCM,
            EncryptionAlgorithm.CHACHA_POLY -> {
                val key = CryptoService.deriveKey(
                    password = password,
                    salt = header.salt,
                    info = "veilix.enc.v1",
                    version = header.version.toInt()
                )
                CryptoService.decrypt(
                    data = encrypted,
                    algorithm = header.encryption,
                    key = key,
                    nonce = header.nonce
                )
            }
        }

        val decompressed = CompressionService.decompress(compressed, header.compression)

        return when (header.version.toInt()) {
            1, 2 -> {
                // Legacy: raw UTF-8 text, no envelope.
                Payload.Text(String(decompressed, Charsets.UTF_8))
            }
            3 -> PayloadEnvelope.decode(decompressed)
            else -> throw StegoError.UnsupportedVersion(header.version.toInt())
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // PROBE
    // ─────────────────────────────────────────────────────────────────────

    fun probe(pixels: ByteArray, width: Int, height: Int): ProbeResult {
        if (pixels.size != width * height * 4) return ProbeResult(false)
        val rgbCount = width * height * 3
        if (rgbCount <= PayloadHeader.BIT_SIZE) return ProbeResult(false)

        val headerBits = readSequential(pixels, 0, PayloadHeader.BIT_SIZE)
        val headerData = bytesFromBits(headerBits)

        val header = try {
            PayloadCodec.decode(headerData)
        } catch (_: StegoError) {
            return ProbeResult(
                hasHiddenData = false,
                salt = StealthFormat.candidateSalt(pixels, rgbCount)
            )
        }
        return ProbeResult(
            hasHiddenData = true,
            encryption = header.encryption,
            compression = header.compression,
            salt = header.salt
        )
    }

    // ─────────────────────────────────────────────────────────────────────
    // LSB primitives — RGB only, alpha skipped, MSB-first bit ordering
    // ─────────────────────────────────────────────────────────────────────

    /** Maps a 0-based RGB-channel offset to a byte index in the RGBA buffer. */
    @Suppress("NOTHING_TO_INLINE")
    private inline fun byteIndexFor(rgbOffset: Int): Int {
        val pixel = rgbOffset / 3
        val channel = rgbOffset % 3
        return pixel * 4 + channel
    }

    /** Decomposes a byte buffer into 1-bit values, MSB first per byte. */
    private fun bitsOf(data: ByteArray): ByteArray {
        val out = ByteArray(data.size * 8)
        var pos = 0
        for (byte in data) {
            val b = byte.toInt() and 0xFF
            for (shift in 7 downTo 0) {
                out[pos++] = ((b shr shift) and 1).toByte()
            }
        }
        return out
    }

    /** Inverse of [bitsOf]. Length must be a multiple of 8. */
    private fun bytesFromBits(bits: ByteArray): ByteArray {
        val out = ByteArray(bits.size / 8)
        var current = 0
        for (i in bits.indices) {
            current = (current shl 1) or (bits[i].toInt() and 1)
            if (i and 7 == 7) {
                out[i ushr 3] = current.toByte()
                current = 0
            }
        }
        return out
    }

    private fun embedSequential(
        bits: ByteArray,
        pixels: ByteArray,
        startOffset: Int
    ) {
        for (i in bits.indices) {
            val idx = byteIndexFor(startOffset + i)
            pixels[idx] = ((pixels[idx].toInt() and 0xFE) or (bits[i].toInt() and 1)).toByte()
        }
    }

    private fun readSequential(
        pixels: ByteArray,
        startOffset: Int,
        bitCount: Int
    ): ByteArray {
        val out = ByteArray(bitCount)
        for (i in 0 until bitCount) {
            val idx = byteIndexFor(startOffset + i)
            out[i] = (pixels[idx].toInt() and 1).toByte()
        }
        return out
    }

    private fun embedRandom(
        bits: ByteArray,
        pixels: ByteArray,
        rgbCount: Int,
        reservedPrefix: Int,
        seed: Long
    ) {
        val range = rgbCount - reservedPrefix
        if (range <= 0 || bits.isEmpty()) return

        val rng = SplitMix64(seed)
        val used = PositionSet(range)

        for (bit in bits) {
            var offset: Int
            do {
                offset = rng.nextInt(range)
            } while (!used.insert(offset))
            val idx = byteIndexFor(reservedPrefix + offset)
            pixels[idx] = ((pixels[idx].toInt() and 0xFE) or (bit.toInt() and 1)).toByte()
        }
    }

    private fun readRandom(
        pixels: ByteArray,
        rgbCount: Int,
        reservedPrefix: Int,
        bitCount: Int,
        seed: Long
    ): ByteArray {
        val out = ByteArray(bitCount)
        val range = rgbCount - reservedPrefix
        if (range <= 0 || bitCount == 0) return out

        val rng = SplitMix64(seed)
        val used = PositionSet(range)

        for (i in 0 until bitCount) {
            var offset: Int
            do {
                offset = rng.nextInt(range)
            } while (!used.insert(offset))
            val idx = byteIndexFor(reservedPrefix + offset)
            out[i] = (pixels[idx].toInt() and 1).toByte()
        }
        return out
    }
}
