package cattivole.veilix.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cattivole.veilix.R
import cattivole.veilix.ui.theme.BrandColors

/**
 * Carrier-image source chooser — bottom sheet with three rows. Mirrors
 * iOS's `confirmationDialog` for "Pick photo / Take photo / Load file".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageSourceChooser(
    onPickGallery: () -> Unit,
    onTakePhoto: () -> Unit,
    onPickFile: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = BrandColors.Surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp)
        ) {
            SourceRow(
                icon = Icons.Filled.PhotoLibrary,
                label = stringResource(R.string.picker_from_gallery),
                onClick = { onDismiss(); onPickGallery() }
            )
            SourceRow(
                icon = Icons.Filled.PhotoCamera,
                label = stringResource(R.string.picker_take_photo),
                onClick = { onDismiss(); onTakePhoto() }
            )
            SourceRow(
                icon = Icons.Filled.AttachFile,
                label = stringResource(R.string.picker_load_file),
                onClick = { onDismiss(); onPickFile() }
            )
        }
    }
}

@Composable
private fun SourceRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = BrandColors.Cyan,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = BrandColors.TextPrimary
        )
    }
}
