package cattivole.veilix.core.stego

/**
 * Deterministic 64-bit PRNG used for pseudo-random LSB distribution. The
 * state is derived from password+salt, so the same seed produces the same
 * permutation on encode and decode.
 *
 * Constants and algorithm must match the iOS implementation byte-for-byte
 * (cross-platform decode depends on it). All arithmetic uses Long with
 * unchecked overflow — Kotlin's `*` and `+` on Long wrap naturally.
 */
class SplitMix64(seed: Long) {

    private var state: Long = if (seed == 0L) FALLBACK_SEED else seed

    /** Next pseudo-random 64-bit value. */
    fun next(): Long {
        state += GAMMA               // unchecked add (wraps)
        var z = state
        z = (z xor (z ushr 30)) * MIX_1
        z = (z xor (z ushr 27)) * MIX_2
        return z xor (z ushr 31)
    }

    /**
     * Returns a non-negative `Int` strictly less than [upper]. Equivalent to
     * iOS's `Int(next() % UInt64(upper))` — i.e. UNSIGNED modulo over the
     * full 64-bit `next()` output.
     *
     * NB: an earlier version did `(next() ushr 1) % upper` which silently
     * differs from iOS whenever `next()` has its high bit set (about half
     * the time). That broke cross-platform decode by shifting all body-bit
     * positions to wrong pixels. Use ULong here to mimic UInt64 exactly.
     */
    fun nextInt(upper: Int): Int {
        require(upper > 0) { "upper must be positive" }
        return (next().toULong() % upper.toULong()).toInt()
    }

    companion object {
        // Hex literals are written as ULong then cast to Long so the bit
        // pattern is unambiguous (Kotlin signed-Long hex literals can't
        // represent values with the high bit set without verbose two's
        // complement arithmetic).
        private val GAMMA: Long         = 0x9E3779B97F4A7C15uL.toLong()
        private val MIX_1: Long         = 0xBF58476D1CE4E5B9uL.toLong()
        private val MIX_2: Long         = 0x94D049BB133111EBuL.toLong()
        private val FALLBACK_SEED: Long = 0xDEADBEEFCAFEBABEuL.toLong()
    }
}
