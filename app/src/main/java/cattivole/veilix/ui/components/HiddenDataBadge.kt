package cattivole.veilix.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cattivole.veilix.R
import cattivole.veilix.core.stego.SteganographyService
import cattivole.veilix.ui.theme.BrandColors

@Composable
fun HiddenDataBadge(probe: SteganographyService.ProbeResult, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BrandColors.Cyan.copy(alpha = 0.10f))
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = null,
            tint = BrandColors.Cyan
        )
        Column {
            Text(
                text = stringResource(R.string.status_hidden_detected),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = BrandColors.TextPrimary
            )
            val enc = probe.encryption?.name?.replace('_', '-') ?: "—"
            val comp = probe.compression?.name ?: "—"
            Text(
                text = "$enc · $comp",
                style = MaterialTheme.typography.bodySmall,
                color = BrandColors.TextSecondary
            )
        }
    }
}
