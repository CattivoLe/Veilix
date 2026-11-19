package cattivole.veilix.ui.components

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import cattivole.veilix.R
import cattivole.veilix.ui.theme.BrandColors

@Composable
fun MessageField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(stringResource(R.string.field_message)) },
        modifier = modifier,
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(
                        imageVector = Icons.Filled.Clear,
                        contentDescription = stringResource(R.string.action_clear_message),
                        tint = BrandColors.TextSecondary
                    )
                }
            }
        },
        // No autocorrect: keeps secret text out of the keyboard's learned vocabulary.
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            autoCorrectEnabled = false
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = BrandColors.SurfaceElevated,
            unfocusedContainerColor = BrandColors.SurfaceElevated,
            focusedBorderColor = BrandColors.Cyan,
            unfocusedBorderColor = BrandColors.Surface,
            cursorColor = BrandColors.Cyan,
            focusedTextColor = BrandColors.TextPrimary,
            unfocusedTextColor = BrandColors.TextPrimary
        ),
        minLines = 3,
        maxLines = 8
    )
}
