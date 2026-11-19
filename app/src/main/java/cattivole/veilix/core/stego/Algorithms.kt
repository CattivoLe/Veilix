package cattivole.veilix.core.stego

/**
 * Authenticated encryption algorithms supported by Veilix. Raw values are
 * written into the payload header byte 5, so they MUST match the iOS
 * implementation exactly for cross-platform decode.
 */
enum class EncryptionAlgorithm(val rawValue: Byte) {
    NONE(0),
    AES_GCM(1),
    CHACHA_POLY(2);

    val requiresPassword: Boolean
        get() = this != NONE

    companion object {
        fun fromRaw(raw: Byte): EncryptionAlgorithm? =
            entries.firstOrNull { it.rawValue == raw }
    }
}

/**
 * Payload compression algorithms. Raw values are written into the payload
 * header byte 6. iOS uses Apple's `Compression` framework, Android uses
 * `java.util.zip` for ZLIB. LZFSE and LZMA are not yet implemented on
 * Android — they are kept here so [fromRaw] can still recognise an
 * iOS-encoded header and produce a clear "unsupported algorithm" error,
 * but they're omitted from [encodableEntries] so the encoder UI doesn't
 * offer choices that would fail later.
 */
enum class CompressionAlgorithm(val rawValue: Byte) {
    NONE(0),
    ZLIB(1),
    LZFSE(2),
    LZMA(3);

    /** Algorithms this platform can actually compress with. */
    val isEncodable: Boolean
        get() = this == NONE || this == ZLIB

    companion object {
        fun fromRaw(raw: Byte): CompressionAlgorithm? =
            entries.firstOrNull { it.rawValue == raw }

        /** Subset of [entries] suitable for the encoder UI. */
        val encodableEntries: List<CompressionAlgorithm> =
            entries.filter { it.isEncodable }
    }
}

/**
 * Maximum long-edge pixel size of the carrier image before LSB embedding.
 * Smaller = smaller PNG export and faster operation, but lower capacity.
 * `null` means keep the original resolution (no downscale).
 */
enum class ImageSizePolicy(val maxEdge: Int?) {
    COMPACT(1024),
    STANDARD(2048),
    LARGE(4096),
    ORIGINAL(null);

    val rawValue: Int get() = maxEdge ?: 0

    companion object {
        fun fromRaw(raw: Int): ImageSizePolicy =
            entries.firstOrNull { it.maxEdge == raw || (raw == 0 && it == ORIGINAL) }
                ?: STANDARD
    }
}

/**
 * Options passed to [SteganographyService.encode].
 *
 * @param maxImageEdge `null` keeps the original resolution.
 */
data class StegoOptions(
    val encryption: EncryptionAlgorithm,
    val compression: CompressionAlgorithm,
    val password: String,
    val maxImageEdge: Int? = ImageSizePolicy.STANDARD.maxEdge
) {
    companion object {
        val DEFAULT = StegoOptions(
            encryption = EncryptionAlgorithm.AES_GCM,
            compression = CompressionAlgorithm.ZLIB,
            password = "",
            maxImageEdge = ImageSizePolicy.STANDARD.maxEdge
        )
    }
}
