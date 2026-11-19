package cattivole.veilix.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Palette derived from the app icon (deep indigo background with violet→cyan
 * accent gradients and a pixelated dissolve aesthetic). Mirrors iOS
 * `BrandColors` byte-for-byte so the two platforms feel identical.
 */
object BrandColors {
    // ─── Backgrounds ──────────────────────────────────────────────────
    val BgTop    = Color(0xFF14093A)
    val BgBottom = Color(0xFF08031A)

    // ─── Surfaces ─────────────────────────────────────────────────────
    val Surface         = Color(0xFF1F123D)
    val SurfaceElevated = Color(0xFF2C1A52)

    // ─── Accents ──────────────────────────────────────────────────────
    val Violet = Color(0xFF8A47D6)
    val Cyan   = Color(0xFF5DD2E8)

    /** Standard horizontal violet → cyan gradient brush. */
    val AccentGradient: Brush = Brush.linearGradient(
        colors = listOf(Violet, Cyan)
    )

    // ─── Text ─────────────────────────────────────────────────────────
    val TextPrimary   = Color(0xFFEEEEEE)   // ≈ white 94%
    val TextSecondary = Color(0x9EFFFFFF)   // ≈ white 62%
    val TextDim       = Color(0x61FFFFFF)   // ≈ white 38%

    // ─── Semantic ─────────────────────────────────────────────────────
    val Success = Color(0xFF22C55E)
    val Warning = Color(0xFFF59E0B)
    val Error   = Color(0xFFEF4444)
}
