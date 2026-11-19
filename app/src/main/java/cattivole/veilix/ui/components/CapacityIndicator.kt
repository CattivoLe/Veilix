package cattivole.veilix.ui.components

import android.text.format.Formatter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cattivole.veilix.R
import cattivole.veilix.ui.theme.BrandColors

/**
 * Live bar showing how much of the image's LSB capacity the current payload
 * (after compression + AEAD tag overhead) would consume. Mirrors iOS
 * `CapacityIndicator` — same colour ramp (green → orange → red).
 */
@Composable
fun CapacityIndicator(
    usedBytes: Int,
    capacityBytes: Int,
    approxCharsRemaining: Int?,
    modifier: Modifier = Modifier,
    /**
     * Whether the embed is meant to stay undetected. Without a password the
     * image carries a readable marker, so fill level doesn't matter.
     */
    warnsAboutDetectability: Boolean = false
) {
    val context = LocalContext.current
    val ratio = if (capacityBytes > 0) usedBytes.toFloat() / capacityBytes else 0f
    val clamped = ratio.coerceIn(0f, 1f)
    val animated by animateFloatAsState(clamped, label = "capacity")

    val color = when {
        ratio > 0.9f -> BrandColors.Error
        ratio > 0.7f -> BrandColors.Warning
        else         -> BrandColors.Success
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        LinearProgressIndicator(
            progress = { animated },
            color = color,
            trackColor = BrandColors.SurfaceElevated,
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val used = Formatter.formatShortFileSize(context, usedBytes.toLong())
            val total = Formatter.formatShortFileSize(context, capacityBytes.toLong())
            Text(
                text = stringResource(R.string.capacity_used, used, total),
                style = MaterialTheme.typography.labelSmall,
                color = BrandColors.TextSecondary
            )

            when {
                ratio > 1f -> Text(
                    text = stringResource(R.string.capacity_exceeded),
                    style = MaterialTheme.typography.labelSmall,
                    color = BrandColors.Error,
                    fontWeight = FontWeight.SemiBold
                )
                approxCharsRemaining != null && approxCharsRemaining > 0 -> Text(
                    text = stringResource(R.string.capacity_remaining_chars, approxCharsRemaining),
                    style = MaterialTheme.typography.labelSmall,
                    color = BrandColors.TextSecondary,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1
                )
            }
        }

        // Capacity is 90% of all carrier bits, so this is roughly 10% of
        // them — past it, statistical detection gets noticeably easier.
        if (warnsAboutDetectability && ratio > 0.11f && ratio <= 1f) {
            Text(
                text = stringResource(R.string.capacity_detectable),
                style = MaterialTheme.typography.labelSmall,
                color = BrandColors.Warning
            )
        }
    }
}
