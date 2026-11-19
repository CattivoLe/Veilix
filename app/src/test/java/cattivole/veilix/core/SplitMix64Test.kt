package cattivole.veilix.core

import cattivole.veilix.core.stego.SplitMix64
import org.junit.Assert.*
import org.junit.Test

class SplitMix64Test {

    @Test
    fun same_seed_produces_identical_sequence() {
        val a = SplitMix64(1L)
        val b = SplitMix64(1L)
        repeat(64) {
            assertEquals("Index $it", a.next(), b.next())
        }
    }

    @Test
    fun zero_seed_uses_fallback() {
        val a = SplitMix64(0L)
        val b = SplitMix64(0L)
        // Zero seed maps to FALLBACK_SEED — sequences match.
        repeat(8) { assertEquals(a.next(), b.next()) }
    }

    @Test
    fun nextInt_stays_in_range() {
        val rng = SplitMix64(42L)
        repeat(50_000) {
            val v = rng.nextInt(100)
            assertTrue("nextInt(100) returned $v", v in 0 until 100)
        }
    }

    @Test
    fun different_seeds_diverge() {
        val a = SplitMix64(1L)
        val b = SplitMix64(2L)
        val differing = (0 until 16).count { a.next() != b.next() }
        // Practically all 16 first values should differ between seeds 1 and 2.
        assertTrue("Sequences should differ broadly, only $differing/16 differ", differing >= 14)
    }
}
