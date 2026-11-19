package cattivole.veilix.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cattivole.veilix.R
import cattivole.veilix.core.PasswordStrength
import cattivole.veilix.ui.theme.BrandColors

/**
 * Informational meter — [PasswordStrength]'s estimate, bucketed into four
 * tiers. Does NOT block weak passwords (matches iOS behaviour).
 */
@Composable
fun PasswordStrengthMeter(password: String, modifier: Modifier = Modifier) {
    if (password.isEmpty()) return

    val strength = remember(password) { strengthOf(password) }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            for (i in 0 until 4) {
                val filled = i < strength.filledSegments
                Row(
                    Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (filled) strength.color else Color.White.copy(alpha = 0.12f))
                ) {}
            }
        }
        Text(
            text = stringResource(strength.labelRes),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = strength.color
        )
    }
}

private enum class Strength(
    val filledSegments: Int,
    val color: Color,
    val labelRes: Int
) {
    Weak(1, BrandColors.Error,   R.string.strength_weak),
    Fair(2, BrandColors.Warning, R.string.strength_fair),
    Strong(3, BrandColors.Cyan,  R.string.strength_strong),
    Excellent(4, BrandColors.Success, R.string.strength_excellent)
}

private fun strengthOf(password: String): Strength {
    val bits = PasswordStrength.estimatedBits(password)
    return when {
        bits < 35  -> Strength.Weak
        bits < 60  -> Strength.Fair
        bits < 90  -> Strength.Strong
        else       -> Strength.Excellent
    }
}

/** Local `remember` shim so the file doesn't need a separate import line. */
@Composable
private fun <T> remember(key: Any, calc: () -> T): T =
    androidx.compose.runtime.remember(key, calc)
