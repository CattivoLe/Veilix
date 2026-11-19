package cattivole.veilix.core.format

import cattivole.veilix.core.compression.CompressionService
import cattivole.veilix.core.stego.CompressionAlgorithm
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32

/**
 * Messengers recompress images but deliver other files untouched, so a ZIP
 * gets the PNG through intact. PNG data barely compresses further, which makes
 * plain storage the right choice for writing. Reading also handles DEFLATE,
 * for archives made elsewhere.
 *
 * Archives arrive from other people, so every offset is treated as untrusted.
 * Mirrors iOS `ZipArchive.swift`.
 */
object ZipArchive {
    private const val LOCAL_HEADER = 0x04034B50L
    private const val CENTRAL_HEADER = 0x02014B50L
    private const val END_RECORD = 0x06054B50L
    private const val END_RECORD_SIZE = 22
    private const val MAX_COMMENT_SIZE = 0xFFFF
    /** Enough for a PNG plus the extras macOS adds. Bounds hostile archives. */
    private const val MAX_ENTRIES = 64
    private const val METHOD_STORED = 0
    private const val METHOD_DEFLATE = 8

    fun isArchive(data: ByteArray): Boolean =
        data.size >= 4 && data[0] == 0x50.toByte() && data[1] == 0x4B.toByte() &&
            data[2] == 0x03.toByte() && data[3] == 0x04.toByte()

    // ─── Writing ──────────────────────────────────────────────────────────

    /**
     * An archive holding one stored file. Timestamps are fixed at the
     * format's earliest date, so the file doesn't record when it was made.
     */
    fun makeStored(fileName: String, contents: ByteArray): ByteArray {
        val name = fileName.toByteArray(Charsets.UTF_8)
        require(name.size <= 0xFFFF)
        val crc = CRC32().apply { update(contents) }.value

        val out = ByteArrayOutputStream(contents.size + 128)
        fun u16(v: Int) { out.write(v and 0xFF); out.write((v ushr 8) and 0xFF) }
        fun u32(v: Long) { for (i in 0 until 4) out.write(((v ushr (8 * i)) and 0xFF).toInt()) }
        fun shared() {
            u16(20)             // version needed
            u16(0x0800)         // UTF-8 names
            u16(METHOD_STORED)
            u16(0)              // time
            u16(0x0021)         // date: 1980-01-01
            u32(crc)
            u32(contents.size.toLong())
            u32(contents.size.toLong())
            u16(name.size)
        }

        u32(LOCAL_HEADER); shared(); u16(0); out.write(name); out.write(contents)
        val centralOffset = out.size()
        u32(CENTRAL_HEADER); u16(20); shared()
        u16(0); u16(0); u16(0); u16(0); u32(0); u32(0)
        out.write(name)
        val centralSize = out.size() - centralOffset
        u32(END_RECORD); u16(0); u16(0); u16(1); u16(1)
        u32(centralSize.toLong()); u32(centralOffset.toLong()); u16(0)
        return out.toByteArray()
    }

    // ─── Reading ──────────────────────────────────────────────────────────

    /**
     * The first PNG with a correct checksum, or null if there is none. Only
     * the bytes are returned: names inside an archive are never used as paths.
     */
    fun firstPng(archive: ByteArray): ByteArray? {
        val r = Reader(archive)
        val end = r.findEndRecord() ?: return null
        val entryCount = r.u16(end + 10) ?: return null
        val centralOffset = r.u32(end + 16) ?: return null
        if (centralOffset > Int.MAX_VALUE) return null

        var position = centralOffset.toInt()
        repeat(minOf(entryCount, MAX_ENTRIES)) {
            val entry = r.centralEntry(position) ?: return null
            r.png(entry)?.let { return it }
            position = entry.nextEntry
        }
        return null
    }

    private class Entry(
        val method: Int,
        val crc: Long,
        val compressedSize: Long,
        val uncompressedSize: Long,
        val name: String,
        val localOffset: Long,
        val nextEntry: Int
    )

    private class Reader(val bytes: ByteArray) {
        fun u16(offset: Int): Int? {
            if (offset < 0 || offset + 2 > bytes.size) return null
            return (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)
        }

        fun u32(offset: Int): Long? {
            if (offset < 0 || offset + 4 > bytes.size) return null
            var v = 0L
            for (i in 3 downTo 0) v = (v shl 8) or (bytes[offset + i].toLong() and 0xFF)
            return v
        }

        /** The end record sits at the end, followed only by an optional comment. */
        fun findEndRecord(): Int? {
            val last = bytes.size - END_RECORD_SIZE
            if (last < 0) return null
            val first = maxOf(0, last - MAX_COMMENT_SIZE)
            for (offset in last downTo first) if (u32(offset) == END_RECORD) return offset
            return null
        }

        fun centralEntry(position: Int): Entry? {
            if (u32(position) != CENTRAL_HEADER) return null
            val method = u16(position + 10) ?: return null
            val crc = u32(position + 16) ?: return null
            val compressed = u32(position + 20) ?: return null
            val uncompressed = u32(position + 24) ?: return null
            val nameLength = u16(position + 28) ?: return null
            val extraLength = u16(position + 30) ?: return null
            val commentLength = u16(position + 32) ?: return null
            val localOffset = u32(position + 42) ?: return null
            val nameStart = position + 46
            val nameEnd = nameStart + nameLength
            if (nameEnd > bytes.size) return null
            return Entry(
                method, crc, compressed, uncompressed,
                String(bytes, nameStart, nameLength, Charsets.UTF_8),
                localOffset, nameEnd + extraLength + commentLength
            )
        }

        fun png(entry: Entry): ByteArray? {
            if (!entry.name.lowercase().endsWith(".png")) return null
            if (entry.uncompressedSize > CompressionService.MAX_DECOMPRESSED_SIZE) return null
            if (entry.localOffset > Int.MAX_VALUE) return null
            val local = entry.localOffset.toInt()
            if (u32(local) != LOCAL_HEADER) return null
            val nameLength = u16(local + 26) ?: return null
            val extraLength = u16(local + 28) ?: return null
            val start = local.toLong() + 30 + nameLength + extraLength
            val end = start + entry.compressedSize
            if (end > bytes.size) return null
            val stored = bytes.copyOfRange(start.toInt(), end.toInt())

            val contents = when (entry.method) {
                METHOD_STORED -> stored
                METHOD_DEFLATE -> try {
                    CompressionService.decompress(stored, CompressionAlgorithm.ZLIB)
                } catch (_: Exception) {
                    null
                }
                else -> null
            } ?: return null

            if (contents.size.toLong() != entry.uncompressedSize) return null
            if (CRC32().apply { update(contents) }.value != entry.crc) return null
            if (ImageSourceFormat.sniff(contents) != ImageSourceFormat.PNG) return null
            return contents
        }
    }
}
