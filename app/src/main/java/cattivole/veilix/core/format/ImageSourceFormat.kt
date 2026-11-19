package cattivole.veilix.core.format

/**
 * What a picked file actually is. Hidden data lives in the exact pixel values,
 * so a lossy format can't hold it: seeing one means any message that was in
 * the image is already gone. That is usually what happens when a messenger
 * recompresses an image sent as a photo, and it's worth saying so instead of
 * letting the user guess at passwords. Mirrors iOS `PickedImage.swift`.
 */
enum class ImageSourceFormat {
    PNG, JPEG, HEIF, LOSSY_WEBP, GIF,
    /** Just taken with the camera, so there can't be anything in it. */
    CAMERA,
    /** Lossless formats we don't single out, or no file bytes to look at. */
    UNKNOWN;

    /** Whether the pixels could still hold a payload. */
    val canCarryData: Boolean
        get() = this == PNG || this == UNKNOWN

    /** Format name for messages explaining why the data can't be there. */
    val lossyName: String?
        get() = when (this) {
            JPEG -> "JPEG"
            HEIF -> "HEIC"
            LOSSY_WEBP -> "WebP"
            GIF -> "GIF"
            else -> null
        }

    companion object {
        fun sniff(data: ByteArray): ImageSourceFormat {
            fun matches(signature: ByteArray, at: Int = 0): Boolean =
                at + signature.size <= data.size &&
                    signature.indices.all { data[at + it] == signature[it] }
            fun matches(text: String, at: Int = 0) = matches(text.toByteArray(Charsets.US_ASCII), at)

            return when {
                matches(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) -> PNG
                matches(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())) -> JPEG
                matches("GIF8") -> GIF
                matches("RIFF") && matches("WEBP", 8) && matches("VP8 ", 12) -> LOSSY_WEBP
                matches("ftyp", 4) &&
                    listOf("heic", "heix", "hevc", "hevx", "mif1", "msf1").any { matches(it, 8) } -> HEIF
                else -> UNKNOWN
            }
        }
    }
}
