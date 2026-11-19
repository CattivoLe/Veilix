package cattivole.veilix.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cattivole.veilix.ui.theme.BrandColors

/**
 * Primary CTA — violet→cyan gradient background, white text. Used for
 * "Embed text" / "Embed file" since those are the main actions.
 */
@Composable
fun PrimaryActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(BrandColors.AccentGradient)
            .alpha(if (enabled) 1f else 0.55f)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = BrandColors.TextPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * Tonal CTA — surface-elevated background with a cyan stroke. Used for
 * "Decrypt": parallel-importance with primary but less visually loud.
 */
@Composable
fun SecondaryActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(BrandColors.SurfaceElevated)
            .border(1.dp, BrandColors.Cyan.copy(alpha = 0.55f), shape)
            .alpha(if (enabled) 1f else 0.55f)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = BrandColors.TextPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * Outlined ghost button for "Export image" / "Save file" — cyan-tinted,
 * minimal visual weight.
 */
@Composable
fun ExportActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(BrandColors.Cyan.copy(alpha = 0.06f))
            .border(1.dp, BrandColors.Cyan.copy(alpha = 0.55f), shape)
            .alpha(if (enabled) 1f else 0.55f)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = BrandColors.Cyan,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
    }
}

/** Stack of primary + secondary CTAs (Embed + Decrypt). */
@Composable
fun ActionButtons(
    canEncode: Boolean,
    canDecode: Boolean,
    isLoading: Boolean,
    decryptEnabled: Boolean,
    embedLabel: String,
    decryptLabel: String,
    onEncode: () -> Unit,
    onDecode: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Shown under Decrypt when the image gives no sign of hidden data, so the
     * recipient of a password-protected image knows the button is for them.
     */
    decodeHint: String? = null
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (canEncode) {
            PrimaryActionButton(
                label = embedLabel,
                onClick = onEncode,
                enabled = !isLoading
            )
        }
        if (canDecode) {
            SecondaryActionButton(
                label = decryptLabel,
                onClick = onDecode,
                enabled = !isLoading && decryptEnabled
            )
        }
        if (decodeHint != null) {
            Text(
                text = decodeHint,
                style = MaterialTheme.typography.bodySmall,
                color = BrandColors.TextSecondary
            )
        }
    }
}
