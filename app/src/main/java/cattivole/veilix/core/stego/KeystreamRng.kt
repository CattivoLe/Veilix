package cattivole.veilix.core.stego

import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Cryptographic source of carrier positions for format v4 (docs/FORMAT.md).
 *
 * The keystream is defined as the ciphertext of AES-256-GCM sealing 1 MiB of
 * zeros per chunk, chunk `i` using the nonce `00000000 || i` (big-endian).
 * GCM encrypts with AES-CTR starting from counter block `nonce || 00000002`,
 * so plain AES-CTR from that block yields the same bytes. A chunk is 65,536
 * blocks, far from the point where GCM's 32-bit counter and CTR's 128-bit
 * counter could differ.
 */
class KeystreamRng(key: ByteArray) {
    private val keySpec = SecretKeySpec(key, "AES")
    private var chunkIndex = 0L
    private var buffer = ByteArray(0)
    private var position = 0

    fun next(): Long {
        if (position + 8 > buffer.size) refill()
        var value = 0L
        for (i in 0 until 8) {
            value = (value shl 8) or (buffer[position + i].toLong() and 0xFF)
        }
        position += 8
        return value
    }

    private fun refill() {
        val counter = ByteArray(16)
        for (i in 0 until 8) {
            counter[4 + i] = (chunkIndex ushr (56 - 8 * i)).toByte()
        }
        counter[15] = 2
        val cipher = Cipher.getInstance("AES/CTR/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, IvParameterSpec(counter))
        buffer = cipher.doFinal(ByteArray(CHUNK_SIZE))
        position = 0
        chunkIndex++
    }

    companion object {
        const val CHUNK_SIZE = 1 shl 20
    }
}
