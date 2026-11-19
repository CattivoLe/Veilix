package cattivole.veilix.core.format

import cattivole.veilix.core.stego.CompressionAlgorithm
import cattivole.veilix.core.stego.EncryptionAlgorithm
import cattivole.veilix.core.stego.StegoError
import java.util.zip.CRC32

/**
 * On-disk header sitting in the first [BIT_SIZE] LSBs of the carrier image
 * (sequential, RGB-only, alpha skipped). Total 43 bytes = 344 bits.
 *
 * ```
 * +-------------------+--------+
 * | Magic "VLX1"      | 4 B    |
 * | Version           | 1 B    |
 * | Encryption algo   | 1 B    |
 * | Compression algo  | 1 B    |
 * | Salt              | 16 B   |
 * | Nonce             | 12 B   |
 * | Body length (BE)  | 4 B    |
 * | Header CRC32 (BE) | 4 B    |
 * +-------------------+--------+
 * ```
 *
 * MUST stay byte-identical to the iOS `PayloadHeader` — cross-platform decode
 * depends on this exact layout.
 */
data class PayloadHeader(
    val version: Byte,
    val encryption: EncryptionAlgorithm,
    val compression: CompressionAlgorithm,
    val salt: ByteArray,       // exactly 16 bytes
    val nonce: ByteArray,      // exactly 12 bytes
    val bodyLength: Int        // unsigned-uint32 value; stored as Int via UInt
) {
    companion object {
        /** ASCII "VLX1". */
        val MAGIC: ByteArray = byteArrayOf(0x56, 0x4C, 0x58, 0x31)

        /** Encoder writes v3; decoder accepts v1/v2/v3. */
        const val CURRENT_VERSION: Byte = 3
        val SUPPORTED_VERSIONS: Set<Byte> = setOf(1, 2, 3)

        /** Header size in bytes (43). */
        const val SIZE: Int = 43

        /** Header size in LSB bits (344). */
        const val BIT_SIZE: Int = SIZE * 8
    }
}

/**
 * Pure byte-level pack/unpack for [PayloadHeader]. No image-pixel work here.
 */
object PayloadCodec {

    fun encode(header: PayloadHeader): ByteArray {
        require(header.salt.size == 16) { "salt must be 16 bytes" }
        require(header.nonce.size == 12) { "nonce must be 12 bytes" }

        val out = ByteArray(PayloadHeader.SIZE)
        // Magic 0..3
        System.arraycopy(PayloadHeader.MAGIC, 0, out, 0, 4)
        // Version 4
        out[4] = header.version
        // Encryption 5, Compression 6
        out[5] = header.encryption.rawValue
        out[6] = header.compression.rawValue
        // Salt 7..22 (16 bytes)
        System.arraycopy(header.salt, 0, out, 7, 16)
        // Nonce 23..34 (12 bytes)
        System.arraycopy(header.nonce, 0, out, 23, 12)
        // Body length 35..38 (big-endian uint32)
        writeUInt32BE(out, 35, header.bodyLength.toLong() and 0xFFFFFFFFL)
        // CRC32 over bytes 0..38, written at 39..42
        val crc = crc32Of(out, 0, 39)
        writeUInt32BE(out, 39, crc)
        return out
    }

    fun decode(data: ByteArray): PayloadHeader {
        if (data.size != PayloadHeader.SIZE) throw StegoError.HeaderCorrupted

        // Magic
        for (i in 0 until 4) {
            if (data[i] != PayloadHeader.MAGIC[i]) throw StegoError.NoHiddenData
        }

        // CRC verification before trusting any field
        val storedCrc = readUInt32BE(data, 39)
        val computedCrc = crc32Of(data, 0, 39)
        if (storedCrc != computedCrc) throw StegoError.HeaderCorrupted

        val version = data[4]
        if (version !in PayloadHeader.SUPPORTED_VERSIONS) {
            throw StegoError.UnsupportedVersion(version.toInt() and 0xFF)
        }

        val enc = EncryptionAlgorithm.fromRaw(data[5])
            ?: throw StegoError.UnsupportedAlgorithm
        val comp = CompressionAlgorithm.fromRaw(data[6])
            ?: throw StegoError.UnsupportedAlgorithm

        val salt = data.copyOfRange(7, 23)        // 16 bytes
        val nonce = data.copyOfRange(23, 35)      // 12 bytes
        val bodyLength = readUInt32BE(data, 35)   // already 0..2^32-1

        // bodyLength is stored as unsigned uint32; on Android we keep it as
        // Long (range fits, no sign overflow) but expose as Int for parity
        // with iOS. Practical body sizes are << 2^31 so the cast is safe.
        return PayloadHeader(
            version = version,
            encryption = enc,
            compression = comp,
            salt = salt,
            nonce = nonce,
            bodyLength = bodyLength.toInt()
        )
    }

    /** Lightweight magic-only check used by the image-probe path. */
    fun hasMagic(bytes: ByteArray, offset: Int = 0): Boolean {
        if (bytes.size - offset < 4) return false
        for (i in 0 until 4) {
            if (bytes[offset + i] != PayloadHeader.MAGIC[i]) return false
        }
        return true
    }

    // MARK: - Byte helpers

    private fun writeUInt32BE(out: ByteArray, offset: Int, value: Long) {
        out[offset    ] = ((value ushr 24) and 0xFF).toByte()
        out[offset + 1] = ((value ushr 16) and 0xFF).toByte()
        out[offset + 2] = ((value ushr 8)  and 0xFF).toByte()
        out[offset + 3] = ( value          and 0xFF).toByte()
    }

    private fun readUInt32BE(data: ByteArray, offset: Int): Long {
        val b0 = (data[offset    ].toInt() and 0xFF).toLong() shl 24
        val b1 = (data[offset + 1].toInt() and 0xFF).toLong() shl 16
        val b2 = (data[offset + 2].toInt() and 0xFF).toLong() shl 8
        val b3 = (data[offset + 3].toInt() and 0xFF).toLong()
        return b0 or b1 or b2 or b3
    }

    private fun crc32Of(data: ByteArray, offset: Int, length: Int): Long {
        val crc = CRC32()
        crc.update(data, offset, length)
        return crc.value
    }
}
