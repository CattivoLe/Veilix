package cattivole.veilix.core.compression

import cattivole.veilix.core.stego.CompressionAlgorithm
import cattivole.veilix.core.stego.StegoError
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * Symmetric compress/decompress matching the iOS Compression framework.
 *
 *  - `NONE` is a no-op.
 *  - `ZLIB` uses raw DEFLATE (no zlib header/trailer): the Java side flips
 *    `nowrap = true`, the iOS side uses `COMPRESSION_ZLIB` which also writes
 *    raw DEFLATE. Bytes are interchangeable.
 *  - `LZFSE` and `LZMA` are not yet implemented on Android. Decoding a v3
 *    image that was compressed with one of these throws
 *    [StegoError.UnsupportedAlgorithm]. (TODO: JNI port of `lzfse`, and an
 *    `org.tukaani:xz` dependency for `LZMA` with format-compatibility
 *    testing against Apple's stream.)
 */
object CompressionService {

    private const val CHUNK_SIZE = 64 * 1024

    /**
     * Upper bound for an unpacked payload. A few hundred KB hidden in an image
     * can otherwise expand to gigabytes and get the app killed for memory use.
     */
    const val MAX_DECOMPRESSED_SIZE = 256 * 1024 * 1024

    fun compress(data: ByteArray, algo: CompressionAlgorithm): ByteArray = when (algo) {
        CompressionAlgorithm.NONE  -> data
        CompressionAlgorithm.ZLIB  -> deflateRaw(data)
        CompressionAlgorithm.LZFSE,
        CompressionAlgorithm.LZMA  -> throw StegoError.UnsupportedAlgorithm
    }

    fun decompress(data: ByteArray, algo: CompressionAlgorithm): ByteArray = when (algo) {
        CompressionAlgorithm.NONE  -> data
        CompressionAlgorithm.ZLIB  -> inflateRaw(data)
        CompressionAlgorithm.LZFSE,
        CompressionAlgorithm.LZMA  -> throw StegoError.UnsupportedAlgorithm
    }

    // MARK: - DEFLATE (raw, no zlib wrapper)

    private fun deflateRaw(input: ByteArray): ByteArray {
        if (input.isEmpty()) return input
        val deflater = Deflater(Deflater.DEFAULT_COMPRESSION, /* nowrap = */ true)
        try {
            deflater.setInput(input)
            deflater.finish()
            val out = ByteArrayOutputStream()
            val buf = ByteArray(CHUNK_SIZE)
            while (!deflater.finished()) {
                val n = deflater.deflate(buf)
                if (n > 0) out.write(buf, 0, n) else break
            }
            return out.toByteArray()
        } catch (t: Throwable) {
            throw StegoError.CompressionFailed
        } finally {
            deflater.end()
        }
    }

    private fun inflateRaw(input: ByteArray): ByteArray {
        if (input.isEmpty()) return input
        val inflater = Inflater(/* nowrap = */ true)
        try {
            inflater.setInput(input)
            val out = ByteArrayOutputStream()
            val buf = ByteArray(CHUNK_SIZE)
            while (!inflater.finished()) {
                val n = inflater.inflate(buf)
                if (n == 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) {
                        // Malformed stream — no more input but not finished.
                        throw StegoError.DecompressionFailed
                    }
                    break
                }
                if (out.size() + n > MAX_DECOMPRESSED_SIZE) throw StegoError.DecompressionFailed
                out.write(buf, 0, n)
            }
            return out.toByteArray()
        } catch (e: StegoError) {
            throw e
        } catch (t: Throwable) {
            throw StegoError.DecompressionFailed
        } finally {
            inflater.end()
        }
    }
}
