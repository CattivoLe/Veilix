package cattivole.veilix.core

import cattivole.veilix.core.format.Payload
import cattivole.veilix.core.format.PayloadCodec
import cattivole.veilix.core.format.PayloadEnvelope
import cattivole.veilix.core.format.PayloadHeader
import cattivole.veilix.core.stego.CompressionAlgorithm
import cattivole.veilix.core.stego.EncryptionAlgorithm
import cattivole.veilix.core.stego.StegoError
import org.junit.Assert.*
import org.junit.Test

class EnvelopeAndHeaderTest {

    // ─── PayloadEnvelope ───

    @Test
    fun envelope_round_trip_text() {
        val msg = "Привет — здесь сообщение с emoji 🎉 и спецсимволами."
        val encoded = PayloadEnvelope.encode(Payload.Text(msg))
        val decoded = PayloadEnvelope.decode(encoded)
        assertTrue(decoded is Payload.Text)
        assertEquals(msg, (decoded as Payload.Text).text)
    }

    @Test
    fun envelope_round_trip_file() {
        val name = "report-final.pdf"
        val data = ByteArray(1024) { (it and 0xFF).toByte() }
        val encoded = PayloadEnvelope.encode(Payload.File(name, data))
        val decoded = PayloadEnvelope.decode(encoded)
        assertTrue(decoded is Payload.File)
        decoded as Payload.File
        assertEquals(name, decoded.name)
        assertArrayEquals(data, decoded.data)
    }

    @Test
    fun envelope_text_kind_byte_is_zero() {
        // Cross-platform: iOS textKind is 0x00. First byte MUST match.
        val encoded = PayloadEnvelope.encode(Payload.Text("hello"))
        assertEquals(0x00.toByte(), encoded[0])
    }

    @Test
    fun envelope_file_kind_byte_is_one() {
        val encoded = PayloadEnvelope.encode(Payload.File("x.txt", byteArrayOf(0)))
        assertEquals(0x01.toByte(), encoded[0])
    }

    // ─── PayloadHeader ───

    @Test
    fun header_round_trip() {
        val header = PayloadHeader(
            version = 3,
            encryption = EncryptionAlgorithm.AES_GCM,
            compression = CompressionAlgorithm.ZLIB,
            salt = ByteArray(16) { it.toByte() },
            nonce = ByteArray(12) { (it + 100).toByte() },
            bodyLength = 1234
        )
        val bytes = PayloadCodec.encode(header)
        assertEquals(43, bytes.size)
        // Magic bytes
        assertArrayEquals(byteArrayOf(0x56, 0x4C, 0x58, 0x31), bytes.copyOfRange(0, 4))
        // Round-trip
        val decoded = PayloadCodec.decode(bytes)
        assertEquals(header.version, decoded.version)
        assertEquals(header.encryption, decoded.encryption)
        assertEquals(header.compression, decoded.compression)
        assertArrayEquals(header.salt, decoded.salt)
        assertArrayEquals(header.nonce, decoded.nonce)
        assertEquals(header.bodyLength, decoded.bodyLength)
    }

    @Test
    fun header_rejects_bad_magic_as_no_hidden_data() {
        val header = PayloadHeader(
            version = 3,
            encryption = EncryptionAlgorithm.NONE,
            compression = CompressionAlgorithm.NONE,
            salt = ByteArray(16),
            nonce = ByteArray(12),
            bodyLength = 0
        )
        val bytes = PayloadCodec.encode(header)
        bytes[0] = 0x00     // break magic
        try {
            PayloadCodec.decode(bytes)
            fail("expected NoHiddenData")
        } catch (e: StegoError.NoHiddenData) { /* ok */ }
    }

    @Test
    fun header_rejects_corrupted_crc() {
        val header = PayloadHeader(
            version = 3,
            encryption = EncryptionAlgorithm.NONE,
            compression = CompressionAlgorithm.NONE,
            salt = ByteArray(16),
            nonce = ByteArray(12),
            bodyLength = 0
        )
        val bytes = PayloadCodec.encode(header)
        bytes[20] = (bytes[20].toInt() xor 0x01).toByte()    // flip a bit inside salt
        try {
            PayloadCodec.decode(bytes)
            fail("expected HeaderCorrupted")
        } catch (e: StegoError.HeaderCorrupted) { /* ok */ }
    }

    @Test
    fun header_rejects_unsupported_version() {
        val header = PayloadHeader(
            version = 3,
            encryption = EncryptionAlgorithm.NONE,
            compression = CompressionAlgorithm.NONE,
            salt = ByteArray(16),
            nonce = ByteArray(12),
            bodyLength = 0
        )
        val bytes = PayloadCodec.encode(header)
        bytes[4] = 99      // bogus version
        // Re-compute CRC for the new content so we hit the version check
        // rather than the CRC check.
        val crc = java.util.zip.CRC32().also { it.update(bytes, 0, 39) }.value
        bytes[39] = ((crc ushr 24) and 0xFF).toByte()
        bytes[40] = ((crc ushr 16) and 0xFF).toByte()
        bytes[41] = ((crc ushr  8) and 0xFF).toByte()
        bytes[42] = ( crc          and 0xFF).toByte()
        try {
            PayloadCodec.decode(bytes)
            fail("expected UnsupportedVersion")
        } catch (e: StegoError.UnsupportedVersion) {
            assertEquals(99, e.version)
        }
    }
}
