package cattivole.veilix.core.crypto

import cattivole.veilix.core.stego.EncryptionAlgorithm
import cattivole.veilix.core.stego.StegoError
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Symmetric encryption, AEAD ciphers, and key derivation. Matches the iOS
 * `CryptoService` byte-for-byte:
 *
 *  - v1 keys: HKDF-SHA256 directly on the UTF-8 password
 *  - v2/v3 keys: PBKDF2-HMAC-SHA256 (200,000 iterations) → master, then
 *    HKDF-SHA256 (with empty salt) for `info`-based domain separation
 *  - AES-256-GCM: 12-byte nonce, 16-byte tag (128-bit GCM tag)
 *  - ChaCha20-Poly1305: 12-byte nonce, 16-byte tag (API 28+)
 */
object CryptoService {

    const val NONCE_SIZE = 12
    const val SALT_SIZE = 16
    const val KEY_SIZE = 32
    const val TAG_SIZE = 16
    const val GCM_TAG_BITS = TAG_SIZE * 8

    /**
     * PBKDF2 work factor of formats v2 and v3. Kept only so existing images
     * still open; new images use [cattivole.veilix.core.stego.StealthFormat.KDF_ROUNDS].
     */
    const val PBKDF2_ROUNDS = 200_000

    private val rng = SecureRandom()

    fun randomBytes(count: Int): ByteArray =
        ByteArray(count).also { rng.nextBytes(it) }

    // MARK: - Key derivation

    /**
     * Derives an encryption key. `version` selects the KDF generation; the
     * branches MUST mirror the iOS `CryptoService.deriveKey` implementation.
     */
    fun deriveKey(
        password: String,
        salt: ByteArray,
        info: String,
        version: Int
    ): ByteArray {
        val infoBytes = info.toByteArray(Charsets.UTF_8)
        val pwdBytes = password.toByteArray(Charsets.UTF_8)

        return when (version) {
            1 -> hkdfSha256(
                ikm = pwdBytes,
                salt = salt,
                info = infoBytes,
                outputLen = KEY_SIZE
            )

            2, 3 -> {
                val master = pbkdf2Master(password, salt)
                hkdfSha256(
                    ikm = master,
                    salt = ByteArray(0),
                    info = infoBytes,
                    outputLen = KEY_SIZE
                )
            }

            else -> throw StegoError.UnsupportedVersion(version)
        }
    }

    /**
     * Derives the 64-bit seed used by [cattivole.veilix.core.stego.SplitMix64]
     * to scatter the encrypted body across the carrier image. iOS reads the
     * 8 bytes via `withUnsafeBytes { $0.load(as: UInt64.self) }` — on Apple
     * platforms that's little-endian by default. We mirror that explicitly.
     */
    fun deriveSeed(password: String, salt: ByteArray, version: Int): Long {
        val infoBytes = "veilix.seed.v1".toByteArray(Charsets.UTF_8)
        val pwdBytes = password.toByteArray(Charsets.UTF_8)

        val raw = when (version) {
            1 -> hkdfSha256(
                ikm = pwdBytes,
                salt = salt,
                info = infoBytes,
                outputLen = 8
            )
            2, 3 -> {
                val master = pbkdf2Master(password, salt)
                hkdfSha256(
                    ikm = master,
                    salt = ByteArray(0),
                    info = infoBytes,
                    outputLen = 8
                )
            }
            else -> throw StegoError.UnsupportedVersion(version)
        }

        // iOS reads as native-endian UInt64 — Apple silicon and Intel are
        // both little-endian, so we match that.
        return ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).long
    }

    /**
     * PBKDF2-HMAC-SHA256, hand-rolled (the JDK provider's
     * `PBKDF2WithHmacSHA256` rejects empty passwords via `SecretKeySpec`
     * with "Empty key" — but Veilix derives a seed even when encryption is
     * NONE and the password is empty, exactly mirroring iOS CryptoKit which
     * accepts empty keys). dkLen here is always 32 (SHA-256 output), so a
     * single PRF block suffices and the outer concatenation loop is skipped.
     */
    private fun pbkdf2Master(password: String, salt: ByteArray): ByteArray {
        val pwdBytes = password.toByteArray(Charsets.UTF_8)
        // U_1 = HMAC(password, salt || INT(1)_BE)
        val block = ByteArray(salt.size + 4)
        System.arraycopy(salt, 0, block, 0, salt.size)
        block[salt.size + 3] = 1
        var u = hmacSha256(pwdBytes, block)
        val result = u.copyOf()
        repeat(PBKDF2_ROUNDS - 1) {
            u = hmacSha256(pwdBytes, u)
            for (j in result.indices) {
                result[j] = (result[j].toInt() xor u[j].toInt()).toByte()
            }
        }
        return result
    }

    /**
     * RFC 5869 HKDF-SHA256. Implemented by hand because the JDK doesn't ship
     * HKDF (only the `Mac` HMAC primitive). Matches Apple CryptoKit's
     * `HKDF<SHA256>.deriveKey` output.
     */
    internal fun hkdfSha256(
        ikm: ByteArray,
        salt: ByteArray,
        info: ByteArray,
        outputLen: Int
    ): ByteArray {
        // Per RFC: if salt is empty, use HashLen zeros (32 for SHA-256).
        val effectiveSalt = if (salt.isEmpty()) ByteArray(32) else salt

        // Extract: PRK = HMAC-SHA256(salt, IKM)
        val prk = hmacSha256(key = effectiveSalt, data = ikm)

        // Expand: T(1) | T(2) | ... where
        //   T(n) = HMAC(PRK, T(n-1) || info || n)
        val hashLen = 32
        val n = (outputLen + hashLen - 1) / hashLen
        require(n in 1..255) { "HKDF requested too much output" }

        val output = ByteArray(outputLen)
        var prev = ByteArray(0)
        var written = 0

        for (i in 1..n) {
            val msg = ByteArray(prev.size + info.size + 1)
            System.arraycopy(prev, 0, msg, 0, prev.size)
            System.arraycopy(info, 0, msg, prev.size, info.size)
            msg[prev.size + info.size] = i.toByte()
            prev = hmacSha256(prk, msg)

            val take = minOf(hashLen, outputLen - written)
            System.arraycopy(prev, 0, output, written, take)
            written += take
        }
        return output
    }

    /**
     * Manual HMAC-SHA256 (RFC 2104). Built on `MessageDigest` rather than
     * `Mac` so the implementation accepts empty keys — Java's
     * `SecretKeySpec(ByteArray(0), …)` throws "Empty key", but Veilix
     * legitimately HMACs an empty password when the user picked
     * encryption=NONE (the seed PRNG still gets derived for body
     * placement). iOS CryptoKit's HMAC accepts empty keys, and so must we.
     */
    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val blockSize = 64
        val effectiveKey = if (key.size > blockSize) {
            MessageDigest.getInstance("SHA-256").digest(key)
        } else key
        val padded = effectiveKey.copyOf(blockSize)   // copyOf pads with 0x00

        val ipad = ByteArray(blockSize)
        val opad = ByteArray(blockSize)
        for (i in 0 until blockSize) {
            ipad[i] = (padded[i].toInt() xor 0x36).toByte()
            opad[i] = (padded[i].toInt() xor 0x5C).toByte()
        }

        val sha = MessageDigest.getInstance("SHA-256")
        sha.update(ipad)
        sha.update(data)
        val inner = sha.digest()

        sha.reset()
        sha.update(opad)
        sha.update(inner)
        return sha.digest()
    }

    // MARK: - Encryption

    fun encrypt(
        data: ByteArray,
        algorithm: EncryptionAlgorithm,
        key: ByteArray,
        nonce: ByteArray,
        aad: ByteArray = ByteArray(0)
    ): ByteArray {
        require(nonce.size == NONCE_SIZE) { "nonce must be $NONCE_SIZE bytes" }
        require(key.size == KEY_SIZE) { "key must be $KEY_SIZE bytes" }

        return try {
            when (algorithm) {
                EncryptionAlgorithm.NONE -> data
                EncryptionAlgorithm.AES_GCM -> {
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    cipher.init(
                        Cipher.ENCRYPT_MODE,
                        SecretKeySpec(key, "AES"),
                        GCMParameterSpec(GCM_TAG_BITS, nonce)
                    )
                    if (aad.isNotEmpty()) cipher.updateAAD(aad)
                    // JCE concatenates ciphertext || tag — same byte layout
                    // as `sealed.ciphertext + sealed.tag` on iOS.
                    cipher.doFinal(data)
                }
                EncryptionAlgorithm.CHACHA_POLY -> {
                    val cipher = Cipher.getInstance("ChaCha20-Poly1305")
                    cipher.init(
                        Cipher.ENCRYPT_MODE,
                        SecretKeySpec(key, "ChaCha20"),
                        IvParameterSpec(nonce)
                    )
                    if (aad.isNotEmpty()) cipher.updateAAD(aad)
                    cipher.doFinal(data)
                }
            }
        } catch (t: Throwable) {
            throw StegoError.EncryptionFailed
        }
    }

    fun decrypt(
        data: ByteArray,
        algorithm: EncryptionAlgorithm,
        key: ByteArray,
        nonce: ByteArray,
        aad: ByteArray = ByteArray(0)
    ): ByteArray {
        if (algorithm == EncryptionAlgorithm.NONE) return data
        if (data.size < TAG_SIZE) throw StegoError.WrongPasswordOrCorrupted
        require(nonce.size == NONCE_SIZE) { "nonce must be $NONCE_SIZE bytes" }
        require(key.size == KEY_SIZE) { "key must be $KEY_SIZE bytes" }

        return try {
            when (algorithm) {
                EncryptionAlgorithm.NONE -> data       // unreachable, guarded above
                EncryptionAlgorithm.AES_GCM -> {
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    cipher.init(
                        Cipher.DECRYPT_MODE,
                        SecretKeySpec(key, "AES"),
                        GCMParameterSpec(GCM_TAG_BITS, nonce)
                    )
                    if (aad.isNotEmpty()) cipher.updateAAD(aad)
                    cipher.doFinal(data)
                }
                EncryptionAlgorithm.CHACHA_POLY -> {
                    val cipher = Cipher.getInstance("ChaCha20-Poly1305")
                    cipher.init(
                        Cipher.DECRYPT_MODE,
                        SecretKeySpec(key, "ChaCha20"),
                        IvParameterSpec(nonce)
                    )
                    if (aad.isNotEmpty()) cipher.updateAAD(aad)
                    cipher.doFinal(data)
                }
            }
        } catch (t: Throwable) {
            // AEAD authentication failure → wrong password or tampered.
            throw StegoError.WrongPasswordOrCorrupted
        }
    }
}
