package cattivole.veilix.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import cattivole.veilix.ui.StatusBanner
import cattivole.veilix.ui.theme.BrandColors

/**
 * Single-row banner that surfaces info/success/error feedback. Carries a
 * semantic tint and matching icon so the same UI slot reads correctly
 * regardless of severity.
 */
@Composable
fun StatusBannerView(banner: StatusBanner, modifier: Modifier = Modifier) {
    val tint = banner.kind.tint
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(tint.copy(alpha = 0.10f))
            .border(1.dp, tint.copy(alpha = 0.35f), shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = when (banner.kind) {
                StatusBanner.Kind.Info    -> Icons.Filled.Info
                StatusBanner.Kind.Success -> Icons.Filled.CheckCircle
                StatusBanner.Kind.Error   -> Icons.Filled.Warning
            },
            contentDescription = null,
            tint = tint
        )
        Text(
            text = banner.text,
            color = BrandColors.TextPrimary,
            style = MaterialTheme.typography.bodySmall
        )
    }
}
