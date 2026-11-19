package cattivole.veilix.core.format

import cattivole.veilix.core.stego.StegoError

/**
 * Higher-level payload model. v3 carrier images carry a [PayloadEnvelope]
 * wrapping either text or a named file; v1/v2 carriers (legacy) store raw
 * UTF-8 text without an envelope.
 */
sealed class Payload {
    abstract val rawByteCount: Int

    data class Text(val text: String) : Payload() {
        override val rawByteCount: Int = text.toByteArray(Charsets.UTF_8).size
    }

    data class File(val name: String, val data: ByteArray) : Payload() {
        override val rawByteCount: Int = data.size

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is File) return false
            return name == other.name && data.contentEquals(other.data)
        }

        override fun hashCode(): Int = 31 * name.hashCode() + data.contentHashCode()
    }
}

/**
 * Wire format for the inner payload (placed inside the compressed/encrypted
 * body). Byte 0 picks the kind:
 *  - `0x00` → text: rest of buffer is UTF-8 bytes
 *  - `0x01` → file: byte 1 is name length, then UTF-8 name bytes, then file data
 *
 * iOS implementation: `PayloadEnvelope` in `Service/Payload.swift`.
 */
object PayloadEnvelope {
    private const val TEXT_KIND: Byte = 0x00
    private const val FILE_KIND: Byte = 0x01

    /** File names longer than this are silently truncated. */
    const val MAX_NAME_BYTES: Int = 255

    /** Used when a stored file name has nothing usable left after sanitizing. */
    const val FALLBACK_FILE_NAME = "veilix-payload"

    /**
     * The stored name comes from the image, so it is untrusted input that ends
     * up in a file path and on screen. Keeps it to a single plain path
     * component: no separators, control characters or text-direction
     * overrides (which can disguise an extension), and no leading dots.
     */
    fun sanitizedFileName(raw: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < raw.length) {
            val cp = raw.codePointAt(i)
            i += Character.charCount(cp)
            val isDirectionControl = cp in 0x202A..0x202E || cp in 0x2066..0x2069 ||
                cp == 0x200E || cp == 0x200F
            if (isDirectionControl) continue
            val isSeparator = cp == '/'.code || cp == '\\'.code || cp == ':'.code
            val isControl = Character.getType(cp) == Character.CONTROL.toInt()
            if (isSeparator || isControl) out.append('_') else out.appendCodePoint(cp)
        }
        val trimmed = out.toString().trim().trim('.')
        return trimmed.ifEmpty { FALLBACK_FILE_NAME }
    }

    fun encode(payload: Payload): ByteArray = when (payload) {
        is Payload.Text -> {
            val textBytes = payload.text.toByteArray(Charsets.UTF_8)
            val out = ByteArray(1 + textBytes.size)
            out[0] = TEXT_KIND
            System.arraycopy(textBytes, 0, out, 1, textBytes.size)
            out
        }

        is Payload.File -> {
            val nameBytes = payload.name.toByteArray(Charsets.UTF_8)
                .let { if (it.size > MAX_NAME_BYTES) it.copyOf(MAX_NAME_BYTES) else it }
            val out = ByteArray(2 + nameBytes.size + payload.data.size)
            out[0] = FILE_KIND
            out[1] = nameBytes.size.toByte()
            System.arraycopy(nameBytes, 0, out, 2, nameBytes.size)
            System.arraycopy(payload.data, 0, out, 2 + nameBytes.size, payload.data.size)
            out
        }
    }

    fun decode(data: ByteArray): Payload {
        if (data.isEmpty()) throw StegoError.DecodingFailed

        return when (data[0]) {
            TEXT_KIND -> {
                val text = String(data, 1, data.size - 1, Charsets.UTF_8)
                Payload.Text(text)
            }

            FILE_KIND -> {
                if (data.size < 2) throw StegoError.DecodingFailed
                val nameLen = data[1].toInt() and 0xFF
                if (data.size < 2 + nameLen) throw StegoError.DecodingFailed
                val name = String(data, 2, nameLen, Charsets.UTF_8)
                val fileData = data.copyOfRange(2 + nameLen, data.size)
                Payload.File(name = sanitizedFileName(name), data = fileData)
            }

            else -> throw StegoError.DecodingFailed
        }
    }
}
