package cattivole.veilix.ui

import androidx.compose.ui.graphics.Color
import cattivole.veilix.ui.theme.BrandColors

/**
 * Categorical UI banner (info / success / error). Carrying the kind lets us
 * tint the icon and outline differently, so errors don't read like polite
 * hints.
 */
data class StatusBanner(
    val kind: Kind,
    val text: String
) {
    enum class Kind {
        Info, Success, Error;

        val tint: Color
            get() = when (this) {
                Info    -> BrandColors.Cyan
                Success -> BrandColors.Success
                Error   -> BrandColors.Error
            }
    }
}
