package cattivole.veilix.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cattivole.veilix.R
import cattivole.veilix.ui.theme.BrandColors

/**
 * One entry in the popover's option list — algorithm display name +
 * description + optional cross-platform compatibility warning.
 */
data class InfoOption(
    val name: String,
    val description: String,
    val warning: String? = null
)

/**
 * Modal info dialog summarising a settings section. Mirrors the iOS
 * `InfoPopover` — title + summary + list of options with optional warnings.
 */
@Composable
fun InfoPopover(
    title: String,
    summary: String,
    options: List<InfoOption>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BrandColors.Surface,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = BrandColors.TextPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = BrandColors.TextSecondary
                )
                HorizontalDivider(color = BrandColors.SurfaceElevated)
                options.forEach { option ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = option.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = BrandColors.TextPrimary
                        )
                        Text(
                            text = option.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = BrandColors.TextSecondary
                        )
                        option.warning?.let { warning ->
                            Row(
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Warning,
                                    contentDescription = null,
                                    tint = BrandColors.Warning,
                                    modifier = Modifier
                                        .padding(top = 2.dp)
                                )
                                Text(
                                    text = warning,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = BrandColors.Warning
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.info_close),
                    color = BrandColors.Cyan,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    )
}
