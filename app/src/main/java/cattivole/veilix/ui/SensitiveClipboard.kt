package cattivole.veilix.ui

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle

/**
 * Clipboard writes for secrets: marked sensitive, so Android 13+ doesn't show
 * them in the paste preview, and wiped after a minute instead of lingering
 * for every other app to read.
 */
object SensitiveClipboard {
    private const val LIFETIME_MS = 60_000L
    private const val LABEL = "veilix-secret"
    private val handler = Handler(Looper.getMainLooper())

    fun copy(context: Context, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        val clip = ClipData.newPlainText(LABEL, text)
        clip.description.extras = PersistableBundle().apply {
            val key = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ClipDescription.EXTRA_IS_SENSITIVE
            } else {
                "android.content.extra.IS_SENSITIVE"
            }
            putBoolean(key, true)
        }
        cm.setPrimaryClip(clip)

        handler.removeCallbacksAndMessages(LABEL)
        handler.postAtTime({
            // Only clear our own clip, not something copied since.
            val current = runCatching { cm.primaryClipDescription?.label }.getOrNull()
            if (current == LABEL) runCatching { cm.clearPrimaryClip() }
        }, LABEL, android.os.SystemClock.uptimeMillis() + LIFETIME_MS)
    }
}
