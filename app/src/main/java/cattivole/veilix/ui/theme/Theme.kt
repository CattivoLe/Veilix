package cattivole.veilix.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * Veilix is intentionally dark-only — the app icon and the entire visual
 * language assume a black/indigo background. Dynamic colour is disabled too
 * (it would override the brand palette).
 */
private val VeilixDarkScheme = darkColorScheme(
    primary           = BrandColors.Violet,
    onPrimary         = BrandColors.TextPrimary,
    secondary         = BrandColors.Cyan,
    onSecondary       = BrandColors.TextPrimary,
    tertiary          = BrandColors.Cyan,
    background        = BrandColors.BgBottom,
    onBackground      = BrandColors.TextPrimary,
    surface           = BrandColors.Surface,
    onSurface         = BrandColors.TextPrimary,
    surfaceVariant    = BrandColors.SurfaceElevated,
    onSurfaceVariant  = BrandColors.TextSecondary,
    error             = BrandColors.Error,
    onError           = BrandColors.TextPrimary
)

@Composable
fun VeilixTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = VeilixDarkScheme,
        typography  = Typography,
        content     = content
    )
}
