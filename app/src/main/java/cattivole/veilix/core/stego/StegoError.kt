package cattivole.veilix.core.stego

/**
 * All errors that can be produced by the Veilix core. Modeled as a sealed
 * exception hierarchy (Kotlin-idiomatic) so callers can `catch` specific
 * cases and pattern-match without losing the original throwable's stack
 * trace. Mirrors the iOS `StegoError` enum.
 */
sealed class StegoError(message: String) : Exception(message) {
    data object ImageConversionFailed : StegoError("Failed to process image")
    data object MessageTooLarge       : StegoError("Payload is too large for this image")
    data object DecodingFailed        : StegoError("Failed to decode data")
    data object NoHiddenData          : StegoError("No hidden data in the image")
    data object HeaderCorrupted       : StegoError("Header is corrupted")
    data class  UnsupportedVersion(val version: Int)
        : StegoError("Unsupported format version: $version")
    data object UnsupportedAlgorithm  : StegoError("Unsupported algorithm")
    data object WrongPasswordOrCorrupted
        : StegoError("Wrong password or data is corrupted")
    data object PasswordRequired      : StegoError("Enter a password or turn off encryption")
    data object CompressionFailed     : StegoError("Failed to compress data")
    data object DecompressionFailed   : StegoError("Failed to decompress data")
    data object EncryptionFailed      : StegoError("Failed to encrypt data")
    data object EmptyMessage          : StegoError("Nothing to embed")
    data object PngExportFailed       : StegoError("Couldn't prepare the image for export")
}
