package cattivole.veilix.core

import java.security.SecureRandom
import kotlin.math.log2
import kotlin.math.min

/**
 * A v4 image can be attacked offline, so the password is the real protection.
 * These two help people end up with a good one. Mirrors iOS `PasswordTools.swift`.
 */
object PasswordGenerator {
    /**
     * No i, l, o, 0 or 1, which are easy to confuse when the password is read
     * out or copied by hand. Lowercase, because it has to be typed on a phone.
     */
    private const val ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789"
    private val random = SecureRandom()

    /** For example `k7qmx-2pa9t-zrw4n-8hjcv`: about 99 bits of entropy. */
    fun generate(): String = (0 until 4).joinToString("-") {
        (0 until 5).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")
    }
}

object PasswordStrength {
    /**
     * Rough estimate of how hard a password is to guess, in bits. Charset size
     * alone calls twenty a's excellent, so this also discounts what guessing
     * tools try first: common words, keyboard walks, runs and repetition.
     */
    fun estimatedBits(password: String): Double {
        val characters = password.lowercase().codePoints().toArray()
        if (characters.isEmpty()) return 0.0

        val perCharacter = log2(charsetSize(password).toDouble())

        // Characters a guesser would predict from the previous one cost next
        // to nothing.
        var bits = perCharacter
        for (i in 1 until characters.size) {
            bits += if (isPredictable(characters[i], characters[i - 1])) 1.0 else perCharacter
        }

        // Whatever the length, a few distinct characters repeated don't add up
        // to much: each repeat is only a choice among the ones already used.
        val distinct = characters.toSet().size
        val repeats = characters.size - distinct
        bits = min(bits, distinct * perCharacter + repeats * log2(distinct.toDouble()))

        return min(bits, bitsAllowingForCommonWords(characters, perCharacter, bits))
    }

    // ─── Charset ──────────────────────────────────────────────────────────

    private fun charsetSize(password: String): Int {
        var size = 0
        val cps = password.codePoints().toArray()
        fun has(test: (Int) -> Boolean) = cps.any(test)
        fun isLatin(c: Int) = c < 128 && Character.isLetter(c)

        if (has { isLatin(it) && Character.isLowerCase(it) }) size += 26
        if (has { isLatin(it) && Character.isUpperCase(it) }) size += 26
        // Mostly Cyrillic in practice.
        if (has { !isLatin(it) && Character.isLetter(it) && Character.isLowerCase(it) }) size += 33
        if (has { !isLatin(it) && Character.isLetter(it) && Character.isUpperCase(it) }) size += 33
        if (has { Character.isDigit(it) }) size += 10
        if (has { !Character.isLetterOrDigit(it) }) size += 32
        return maxOf(size, 2)
    }

    // ─── Runs and keyboard walks ──────────────────────────────────────────

    private val keyboardRows = listOf(
        "1234567890", "qwertyuiop", "asdfghjkl", "zxcvbnm",
        "йцукенгшщзхъ", "фывапролджэ", "ячсмитьбю"
    ).map { it.codePoints().toArray() }

    private fun isPredictable(current: Int, previous: Int): Boolean {
        if (current == previous) return true
        if (current == previous + 1 || previous == current + 1) return true
        return keyboardRows.any { row ->
            val a = row.indexOf(previous)
            val b = row.indexOf(current)
            a >= 0 && b >= 0 && kotlin.math.abs(a - b) == 1
        }
    }

    // ─── Common words ─────────────────────────────────────────────────────

    /**
     * Not meant to be complete: the first things tried, in the languages the
     * app ships in, including transliterations.
     */
    private val commonWords = setOf(
        "password", "passwort", "qwerty", "letmein", "welcome", "admin", "login", "iloveyou",
        "monkey", "dragon", "master", "football", "baseball", "sunshine", "princess",
        "shadow", "superman", "batman", "michael", "secret", "trustno", "abc", "test", "hello",
        "пароль", "привет", "люблю", "любовь", "солнышко", "секрет", "мама", "наташа",
        "parol", "privet", "lubov", "lyubov", "sekret", "natasha",
        "лозинка", "шифра", "lozinka", "sifra", "volimte", "zdravo"
    )

    private val lookalikes = mapOf(
        '@' to 'a', '4' to 'a', '3' to 'e', '1' to 'i', '!' to 'i',
        '0' to 'o', '$' to 's', '5' to 's', '7' to 't'
    )

    private fun bitsAllowingForCommonWords(characters: IntArray, perCharacter: Double, bits: Double): Double {
        val original = String(characters, 0, characters.size)
        val plain = original.map { lookalikes[it] ?: it }.joinToString("")
        val word = commonWords
            .filter { plain.contains(it) || original.contains(it) }
            .maxByOrNull { it.length } ?: return bits
        // The word counts as one guess from a short list, plus what's around it.
        val rest = maxOf(0, characters.size - word.codePointCount(0, word.length))
        return min(bits, 10 + rest * perCharacter)
    }
}
