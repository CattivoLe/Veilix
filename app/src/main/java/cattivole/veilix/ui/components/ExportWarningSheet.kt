package cattivole.veilix.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cattivole.veilix.R
import cattivole.veilix.ui.theme.BrandColors

/**
 * "Send as a file" pre-share reminder. Holds a "don't show again" checkbox
 * persisted via the parent's UserPrefs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportWarningSheet(
    dontShowAgain: Boolean,
    onDontShowAgainChange: (Boolean) -> Unit,
    onCancel: () -> Unit,
    onContinue: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onCancel,
        sheetState = sheetState,
        containerColor = BrandColors.Surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = BrandColors.Warning,
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    text = stringResource(R.string.export_warning_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = BrandColors.TextPrimary
                )
            }

            Text(
                text = stringResource(R.string.export_warning_body),
                style = MaterialTheme.typography.bodyMedium,
                color = BrandColors.TextSecondary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Checkbox(
                    checked = dontShowAgain,
                    onCheckedChange = onDontShowAgainChange,
                    colors = CheckboxDefaults.colors(
                        checkedColor = BrandColors.Violet,
                        uncheckedColor = BrandColors.TextSecondary
                    )
                )
                Text(
                    text = stringResource(R.string.export_dont_show_again),
                    style = MaterialTheme.typography.bodyMedium,
                    color = BrandColors.TextPrimary
                )
            }

            // `height(IntrinsicSize.Min)` makes the row collapse to its
            // tallest child, then `fillMaxHeight` syncs both buttons to that
            // same height — otherwise Primary (gradient, 14dp vpad) is
            // visibly taller than Export (ghost, 11dp vpad).
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ExportActionButton(
                    label = stringResource(R.string.export_cancel),
                    onClick = onCancel,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
                PrimaryActionButton(
                    label = stringResource(R.string.export_continue),
                    onClick = onContinue,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }
        }
    }
}
