package cattivole.veilix.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import cattivole.veilix.R
import cattivole.veilix.ui.SensitiveClipboard
import cattivole.veilix.ui.theme.BrandColors
import kotlinx.coroutines.delay

@Composable
fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** Hide the strength meter when decrypting (it grades a known password). */
    showStrengthMeter: Boolean = true,
    visible: Boolean = false,
    onVisibleChange: (Boolean) -> Unit = {},
    /** Shows the generator button. Null where the password already exists. */
    onGenerate: (() -> Unit)? = null
) {
    var copied by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(copied) {
        if (copied) { delay(1_200); copied = false }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.field_password)) },
            singleLine = true,
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (onGenerate != null) {
                        IconButton(onClick = onGenerate) {
                            Icon(
                                imageVector = Icons.Filled.Casino,
                                contentDescription = stringResource(R.string.action_generate_password),
                                tint = BrandColors.Cyan
                            )
                        }
                    }
                    IconButton(onClick = { onVisibleChange(!visible) }) {
                        Icon(
                            imageVector = if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = stringResource(
                                if (visible) R.string.action_hide_password else R.string.action_show_password
                            ),
                            tint = BrandColors.TextSecondary
                        )
                    }
                    IconButton(
                        onClick = {
                            SensitiveClipboard.copy(context, value)
                            copied = true
                        },
                        enabled = value.isNotEmpty()
                    ) {
                        Icon(
                            imageVector = if (copied) Icons.Filled.Check else Icons.Filled.ContentCopy,
                            contentDescription = stringResource(R.string.action_copy_password),
                            tint = if (copied) BrandColors.Success else BrandColors.TextSecondary
                        )
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = BrandColors.SurfaceElevated,
                unfocusedContainerColor = BrandColors.SurfaceElevated,
                focusedBorderColor = BrandColors.Cyan,
                unfocusedBorderColor = BrandColors.Surface,
                cursorColor = BrandColors.Cyan,
                focusedTextColor = BrandColors.TextPrimary,
                unfocusedTextColor = BrandColors.TextPrimary
            )
        )

        if (showStrengthMeter && value.isNotEmpty()) {
            PasswordStrengthMeter(
                password = value,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}
