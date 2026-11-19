package cattivole.veilix.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import cattivole.veilix.ui.theme.BrandColors

/**
 * Veilix backdrop — three layers, all intentionally subtle:
 *  1. diagonal deep-indigo gradient
 *  2. radial violet glow in the top-right (echoing the iris in the icon)
 *  3. sparse violet/cyan "pixel dust" in the bottom-left (the dissolve effect)
 *
 * Mirrors the iOS `AppBackground` SwiftUI view.
 */
@Composable
fun AppBackground(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize()) {
        // Layer 1 — diagonal gradient
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(BrandColors.BgTop, BrandColors.BgBottom),
                        start = Offset.Zero,
                        end = Offset.Infinite
                    )
                )
        )

        // Layer 2 — radial violet glow (top-right)
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0f to BrandColors.Violet.copy(alpha = 0.28f),
                        1f to Color.Transparent
                    ),
                    center = Offset(size.width * 0.75f, size.height * 0.05f),
                    radius = size.maxDimension * 0.5f
                ),
                size = size
            )
        }

        // Layer 3 — deterministic pixel dust (bottom-left quadrant)
        Canvas(Modifier.fillMaxSize()) {
            val rng = MiniRng(seed = 0xC0FFEEDEAD)
            val pixelSize = 4.dp.toPx()
            repeat(70) {
                val x = rng.nextFloat() * size.width * 0.55f
                val y = size.height * 0.45f + rng.nextFloat() * size.height * 0.55f
                val pickCyan = rng.nextInt(2) == 0
                val alpha = 0.05f + rng.nextFloat() * 0.10f     // very subtle
                drawRect(
                    color = (if (pickCyan) BrandColors.Cyan else BrandColors.Violet)
                        .copy(alpha = alpha),
                    topLeft = Offset(x, y),
                    size = Size(pixelSize, pixelSize)
                )
            }
        }
    }
}

/** Tiny LCG just for deterministic decorative noise — not crypto. */
private class MiniRng(seed: Long) {
    private var state: Long = seed
    fun nextInt(upper: Int): Int {
        state = state * 6364136223846793005L + 1442695040888963407L
        val u = (state ushr 33).toInt() and Int.MAX_VALUE
        return u % upper
    }
    fun nextFloat(): Float = nextInt(1_000_000) / 1_000_000f
}
