package cattivole.veilix

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import cattivole.veilix.ui.MainScreen
import cattivole.veilix.ui.MainViewModel
import cattivole.veilix.ui.theme.VeilixTheme

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hideFromRecents()
        // Secrets are cleared after a minute in the background.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) = vm.onAppBackgrounded()
            override fun onStart(owner: LifecycleOwner) = vm.onAppForegrounded()
        })
        handleIncomingIntent(intent)
        setContent {
            VeilixTheme {
                MainScreen(vm = vm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    /**
     * Keeps the recent-apps thumbnail from showing a password or a decrypted
     * message. Android 13+ can drop just the thumbnail; before that the only
     * way is FLAG_SECURE, which also blocks screenshots of the app.
     */
    private fun hideFromRecents() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setRecentsScreenshotEnabled(false)
        } else {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    /**
     * If we were launched via "Open with…" (ACTION_VIEW) or share-sheet
     * (ACTION_SEND), grab the image URI and feed it into the view-model so
     * the same flow as the in-app photo picker kicks in.
     */
    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        val uri: Uri? = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> extractStream(intent)
            else -> null
        }
        if (uri != null) {
            vm.onImageSelected(uri)
        }
    }

    @Suppress("DEPRECATION")
    private fun extractStream(intent: Intent): Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
        }
}
