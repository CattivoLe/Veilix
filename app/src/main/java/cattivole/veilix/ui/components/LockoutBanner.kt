package cattivole.veilix.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import cattivole.veilix.R
import cattivole.veilix.ui.StatusBanner
import kotlinx.coroutines.delay

/**
 * Live countdown shown while the user is in decode lock-out. Uses a
 * coroutine `delay` loop to refresh the rendered second value every 500 ms.
 * When the deadline passes, calls [onExpire] so the parent can clear the
 * persisted lockout state.
 */
@Composable
fun LockoutBanner(
    untilMs: Long,
    onExpire: () -> Unit,
    modifier: Modifier = Modifier
) {
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(untilMs) {
        while (true) {
            nowMs = System.currentTimeMillis()
            if (nowMs >= untilMs) {
                onExpire()
                break
            }
            delay(500)
        }
    }

    val secondsLeft = ((untilMs - nowMs) / 1000L).toInt().coerceAtLeast(0)
    if (secondsLeft <= 0) return

    val text = stringResource(R.string.error_lockout, formatDuration(secondsLeft))
    StatusBannerView(
        banner = StatusBanner(StatusBanner.Kind.Error, text),
        modifier = modifier
    )
}

/** Compact human-friendly duration: "42s" / "1m" / "1m 30s" / "10m". */
private fun formatDuration(seconds: Int): String = when {
    seconds < 60         -> "${seconds}s"
    seconds % 60 == 0    -> "${seconds / 60}m"
    else                 -> "${seconds / 60}m ${seconds % 60}s"
}
