package cattivole.veilix.core.stego

import cattivole.veilix.core.crypto.CryptoService
import java.security.SecureRandom
import java.text.Normalizer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Format v4, used for everything protected by a password. Nothing in the
 * image announces that a payload is there: no magic bytes, no readable
 * header, and without the password the embedded bits can't be told apart from
 * noise by reading them. Full specification: docs/FORMAT.md in the iOS repo.
 *
 * ```
 * salt (16 B)        fixed public pseudo-random positions
 * sealed meta (23 B) password-derived positions, AES-256-GCM
 * body               password-derived positions, chosen AEAD
 * ```
 *
 * Bits are written with LSB matching (±1) rather than LSB replacement, which
 * leaves the pair-of-values histogram artefact classic detectors look for.
 *
 * MUST stay byte-compatible with iOS `StealthFormat.swift`.
 */
object StealthFormat {
    const val VERSION: Byte = 4
    const val SALT_SIZE = 16
    const val META_SIZE = 7
    const val TAG_SIZE = 16
    const val SEALED_META_SIZE = META_SIZE + TAG_SIZE
    const val KDF_ROUNDS = 600_000

    /** Public: the salt has to be readable before any key exists. "VEILIXV4". */
    private val SALT_POSITION_SEED: Long = 0x5645_494C_4958_5634L
    private const val SALT_BIT_COUNT = SALT_SIZE * 8
    /** Each key derived from the random salt seals exactly one message. */
    private val FIXED_NONCE = ByteArray(CryptoService.NONCE_SIZE)

    /** Same 90% slack as the marked format, keeps rejection sampling fast. */
    private fun fits(bodySize: Int, rgbCount: Int): Boolean {
        val usable = rgbCount.toLong() - SALT_BIT_COUNT
        return usable > 0 && (SEALED_META_SIZE.toLong() + bodySize) * 8 <= usable * 9 / 10
    }

    // ─── Embed ────────────────────────────────────────────────────────────

    fun embed(
        plaintext: ByteArray,
        encryption: EncryptionAlgorithm,
        compression: CompressionAlgorithm,
        password: String,
        pixels: ByteArray,
        rgbCount: Int
    ) {
        require(encryption.requiresPassword)
        // Checked first: no reason to spend the key derivation on a payload
        // that doesn't fit.
        if (!fits(plaintext.size + TAG_SIZE, rgbCount)) throw StegoError.MessageTooLarge

        val salt = CryptoService.randomBytes(SALT_SIZE)
        val keys = deriveKeys(password, salt)

        val bodySize = plaintext.size + TAG_SIZE
        val meta = byteArrayOf(
            VERSION, encryption.rawValue, compression.rawValue,
            (bodySize ushr 24).toByte(), (bodySize ushr 16).toByte(),
            (bodySize ushr 8).toByte(), bodySize.toByte()
        )

        val sealedMeta = CryptoService.encrypt(
            meta, EncryptionAlgorithm.AES_GCM, keys.meta, FIXED_NONCE, aad = salt
        )
        val body = CryptoService.encrypt(
            plaintext, encryption, keys.body, FIXED_NONCE, aad = salt + meta
        )

        val taken = PositionSet(rgbCount)
        val writer = MatchingWriter()

        val saltPositions = SplitMix64(SALT_POSITION_SEED)
        forEachBit(salt) { bit ->
            val offset = nextFreeOffset(taken, rgbCount) { saltPositions.next() }
            writer.write(bit, offset, pixels)
        }

        val positions = KeystreamRng(keys.positions)
        forEachBit(sealedMeta + body) { bit ->
            val offset = nextFreeOffset(taken, rgbCount) { positions.next() }
            writer.write(bit, offset, pixels)
        }
    }

    // ─── Extract ──────────────────────────────────────────────────────────

    class Extracted(val compression: CompressionAlgorithm, val plaintext: ByteArray)

    /**
     * A wrong password and an image with nothing in it look exactly the same
     * here — both end in [StegoError.WrongPasswordOrCorrupted]. That's the point.
     */
    fun extract(pixels: ByteArray, rgbCount: Int, password: String): Extracted {
        if (!fits(0, rgbCount)) throw StegoError.WrongPasswordOrCorrupted

        val taken = PositionSet(rgbCount)
        val saltPositions = SplitMix64(SALT_POSITION_SEED)
        val salt = read(SALT_SIZE, pixels, taken, rgbCount) { saltPositions.next() }

        val keys = deriveKeys(password, salt)
        val positions = KeystreamRng(keys.positions)

        val sealedMeta = read(SEALED_META_SIZE, pixels, taken, rgbCount) { positions.next() }
        val meta = CryptoService.decrypt(
            sealedMeta, EncryptionAlgorithm.AES_GCM, keys.meta, FIXED_NONCE, aad = salt
        )
        if (meta.size != META_SIZE) throw StegoError.WrongPasswordOrCorrupted
        if (meta[0] != VERSION) throw StegoError.UnsupportedVersion(meta[0].toInt() and 0xFF)
        val encryption = EncryptionAlgorithm.fromRaw(meta[1])
            ?.takeIf { it.requiresPassword } ?: throw StegoError.UnsupportedAlgorithm
        val compression = CompressionAlgorithm.fromRaw(meta[2]) ?: throw StegoError.UnsupportedAlgorithm

        var bodySizeLong = 0L
        for (i in 3..6) bodySizeLong = (bodySizeLong shl 8) or (meta[i].toLong() and 0xFF)
        if (bodySizeLong > Int.MAX_VALUE || !fits(bodySizeLong.toInt(), rgbCount)) {
            throw StegoError.WrongPasswordOrCorrupted
        }
        val bodySize = bodySizeLong.toInt()

        val body = read(bodySize, pixels, taken, rgbCount) { positions.next() }
        val plaintext = CryptoService.decrypt(
            body, encryption, keys.body, FIXED_NONCE, aad = salt + meta
        )
        return Extracted(compression, plaintext)
    }

    /**
     * The bytes that would be the salt if the image holds a v4 payload. Any
     * image has them, so this says nothing about whether one is there. Used
     * only to tell images apart.
     */
    fun candidateSalt(pixels: ByteArray, rgbCount: Int): ByteArray? {
        if (rgbCount <= SALT_BIT_COUNT) return null
        val taken = PositionSet(rgbCount)
        val saltPositions = SplitMix64(SALT_POSITION_SEED)
        return read(SALT_SIZE, pixels, taken, rgbCount) { saltPositions.next() }
    }

    // ─── Keys ─────────────────────────────────────────────────────────────

    class Keys(val meta: ByteArray, val body: ByteArray, val positions: ByteArray)

    fun deriveKeys(password: String, salt: ByteArray): Keys {
        // The same password can arrive as different bytes depending on the
        // keyboard and platform ("й" as one code point or two).
        val passwordBytes = Normalizer.normalize(password, Normalizer.Form.NFC)
            .toByteArray(Charsets.UTF_8)
        if (passwordBytes.isEmpty()) throw StegoError.PasswordRequired
        val master = pbkdf2Sha256(passwordBytes, salt, KDF_ROUNDS)
        fun expand(label: String) = CryptoService.hkdfSha256(
            ikm = master,
            salt = ByteArray(0),
            info = label.toByteArray(Charsets.UTF_8),
            outputLen = CryptoService.KEY_SIZE
        )
        return Keys(
            meta = expand("veilix.v4.meta"),
            body = expand("veilix.v4.body"),
            positions = expand("veilix.v4.positions")
        )
    }

    /**
     * PBKDF2-HMAC-SHA256 with a single 32-byte block, on the platform `Mac`
     * (much faster than a hand-rolled HMAC). The key is never empty here. The
     * password-based key factories aren't used because how they turn `char[]`
     * into bytes differs between providers.
     */
    internal fun pbkdf2Sha256(password: ByteArray, salt: ByteArray, rounds: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(password, "HmacSHA256"))
        mac.update(salt)
        mac.update(byteArrayOf(0, 0, 0, 1))
        var u = mac.doFinal()
        val result = u.copyOf()
        repeat(rounds - 1) {
            u = mac.doFinal(u)
            for (j in result.indices) result[j] = (result[j].toInt() xor u[j].toInt()).toByte()
        }
        return result
    }

    // ─── Bit plumbing ─────────────────────────────────────────────────────

    private inline fun nextFreeOffset(taken: PositionSet, range: Int, next: () -> Long): Int {
        var offset: Int
        do {
            offset = java.lang.Long.remainderUnsigned(next(), range.toLong()).toInt()
        } while (!taken.insert(offset))
        return offset
    }

    private inline fun read(
        byteCount: Int,
        pixels: ByteArray,
        taken: PositionSet,
        range: Int,
        next: () -> Long
    ): ByteArray {
        val out = ByteArray(byteCount)
        for (i in 0 until byteCount) {
            var byte = 0
            repeat(8) {
                val offset = nextFreeOffset(taken, range, next)
                byte = (byte shl 1) or (pixels[byteIndexFor(offset)].toInt() and 1)
            }
            out[i] = byte.toByte()
        }
        return out
    }

    /** Most significant bit of each byte first. */
    private inline fun forEachBit(data: ByteArray, action: (Int) -> Unit) {
        for (byte in data) {
            val b = byte.toInt() and 0xFF
            for (shift in 7 downTo 0) action((b ushr shift) and 1)
        }
    }

    internal fun byteIndexFor(rgbOffset: Int): Int = (rgbOffset / 3) * 4 + rgbOffset % 3

    /**
     * LSB matching: a channel whose LSB is already right is left alone, any
     * other one moves by ±1 in a random direction. Only the LSB carries data,
     * so the direction needs no agreement with the reader.
     */
    private class MatchingWriter {
        private val random = SecureRandom()
        private var directionBits = 0L
        private var directionsLeft = 0

        fun write(bit: Int, offset: Int, pixels: ByteArray) {
            val index = byteIndexFor(offset)
            val value = pixels[index].toInt() and 0xFF
            if (value and 1 == bit) return
            val moveUp = when (value) {
                0 -> true
                255 -> false
                else -> {
                    if (directionsLeft == 0) {
                        directionBits = random.nextLong()
                        directionsLeft = 64
                    }
                    val up = directionBits and 1L == 1L
                    directionBits = directionBits ushr 1
                    directionsLeft--
                    up
                }
            }
            pixels[index] = (if (moveUp) value + 1 else value - 1).toByte()
        }
    }
}
