package cattivole.veilix.core.stego

/**
 * Tracks which carrier positions are taken, one bit per position. A
 * `HashSet<Int>` of the same positions needs hundreds of MB for a full 12 MP
 * image. Membership answers are identical, so the embedding order — and with
 * it the image format — stays the same.
 */
class PositionSet(count: Int) {
    private val words = LongArray(count / 64 + 1)

    /** Returns `true` if the position was free and is now taken. */
    fun insert(position: Int): Boolean {
        val word = position ushr 6
        val mask = 1L shl (position and 63)
        if (words[word] and mask != 0L) return false
        words[word] = words[word] or mask
        return true
    }
}
