package cattivole.veilix.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Thin SharedPreferences wrapper for the handful of values that need to
 * survive an app restart. Mirrors the iOS `@AppStorage` keys 1:1.
 *
 * Brute-force protection state lives here too — closing the app must NOT
 * bypass the decode lockout.
 */
class UserPrefs(context: Context) {

    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var hideExportWarning: Boolean
        get() = sp.getBoolean(KEY_HIDE_EXPORT_WARNING, false)
        set(value) { sp.edit().putBoolean(KEY_HIDE_EXPORT_WARNING, value).apply() }

    var imageSizeRaw: Int
        get() = sp.getInt(KEY_IMAGE_SIZE_RAW, 2048)
        set(value) { sp.edit().putInt(KEY_IMAGE_SIZE_RAW, value).apply() }

    var decodeFailures: Int
        get() = sp.getInt(KEY_DECODE_FAILURES, 0)
        set(value) { sp.edit().putInt(KEY_DECODE_FAILURES, value).apply() }

    /** Unix ms until which decoding is locked. 0 == not locked. */
    var decodeLockedUntilMs: Long
        get() = sp.getLong(KEY_DECODE_LOCKED_UNTIL_MS, 0L)
        set(value) { sp.edit().putLong(KEY_DECODE_LOCKED_UNTIL_MS, value).apply() }

    /** Header salt of the image the decode-failure counter belongs to. */
    var decodeFailureImageId: String
        get() = sp.getString(KEY_DECODE_FAILURE_IMAGE_ID, "") ?: ""
        set(value) { sp.edit().putString(KEY_DECODE_FAILURE_IMAGE_ID, value).apply() }

    /** Unix ms of the last wrong password; old failures expire. */
    var decodeLastFailureMs: Long
        get() = sp.getLong(KEY_DECODE_LAST_FAILURE_MS, 0L)
        set(value) { sp.edit().putLong(KEY_DECODE_LAST_FAILURE_MS, value).apply() }

    var hasSeenOnboarding: Boolean
        get() = sp.getBoolean(KEY_HAS_SEEN_ONBOARDING, false)
        set(value) { sp.edit().putBoolean(KEY_HAS_SEEN_ONBOARDING, value).apply() }

    companion object {
        private const val FILE = "veilix-prefs"
        private const val KEY_HIDE_EXPORT_WARNING   = "hideExportWarning"
        private const val KEY_IMAGE_SIZE_RAW        = "imageSizePolicy"
        private const val KEY_DECODE_FAILURES       = "decodeFailures"
        private const val KEY_DECODE_LOCKED_UNTIL_MS = "decodeLockedUntilMs"
        private const val KEY_HAS_SEEN_ONBOARDING   = "hasSeenOnboarding"
        private const val KEY_DECODE_FAILURE_IMAGE_ID = "decodeFailureImageId"
        private const val KEY_DECODE_LAST_FAILURE_MS  = "decodeLastFailureMs"
    }
}
