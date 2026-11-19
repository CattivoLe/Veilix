package cattivole.veilix.ui

import android.app.Application
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.webkit.MimeTypeMap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cattivole.veilix.R
import cattivole.veilix.core.PasswordGenerator
import cattivole.veilix.core.bitmap.BitmapPixels
import cattivole.veilix.core.bitmap.RgbaBuffer
import cattivole.veilix.core.compression.CompressionService
import cattivole.veilix.core.format.ImageSourceFormat
import cattivole.veilix.core.format.Payload
import cattivole.veilix.core.format.PayloadEnvelope
import cattivole.veilix.core.format.PayloadHeader
import cattivole.veilix.core.format.ZipArchive
import cattivole.veilix.core.stego.CompressionAlgorithm
import cattivole.veilix.core.stego.EncryptionAlgorithm
import cattivole.veilix.core.stego.ImageSizePolicy
import cattivole.veilix.core.stego.StegoError
import cattivole.veilix.core.stego.StegoOptions
import cattivole.veilix.core.stego.SteganographyService
import cattivole.veilix.data.UserPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.util.UUID

/**
 * Single source of truth for the main screen. Holds Compose-observable
 * state, runs all stego/io work off the main thread, and persists user
 * preferences and decode back-off state through [UserPrefs].
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = UserPrefs(application)

    // ─── Image state ──────────────────────────────────────────────────
    var selectedBitmap by mutableStateOf<Bitmap?>(null)
        private set
    /** What the picked file really is; drives the "compression destroyed it" notice. */
    var selectedFormat by mutableStateOf(ImageSourceFormat.UNKNOWN)
        private set
    var encodedBitmap by mutableStateOf<Bitmap?>(null)
        private set
    /** The exact PNG bytes checked after embedding — exported as is. */
    private var encodedPng: ByteArray? = null
    var probe by mutableStateOf<SteganographyService.ProbeResult?>(null)
        private set

    // ─── Form state ───────────────────────────────────────────────────
    var message by mutableStateOf("")
    var password by mutableStateOf("")
    var isPasswordVisible by mutableStateOf(false)
    var encryption by mutableStateOf(EncryptionAlgorithm.AES_GCM)
    var compression by mutableStateOf(CompressionAlgorithm.ZLIB)
    var imageSize by mutableStateOf(ImageSizePolicy.fromRaw(prefs.imageSizeRaw))
        private set
    var settingsExpanded by mutableStateOf(true)

    // A typo in a hidden password would make the message unrecoverable, so
    // it's typed twice unless the user could see it or it was generated.
    var showPasswordConfirmation by mutableStateOf(false)
        private set
    var repeatedPassword by mutableStateOf("")
    private var confirmedPassword: String? = null

    // ─── Attachment (file payload) ────────────────────────────────────
    var attachedFile by mutableStateOf<AttachedFile?>(null)
        private set

    // ─── Result + status ──────────────────────────────────────────────
    var extractedPayload by mutableStateOf<Payload?>(null)
        private set
    var statusBanner by mutableStateOf<StatusBanner?>(null)
        private set

    // ─── Loader ───────────────────────────────────────────────────────
    var isLoading by mutableStateOf(false)
        private set
    var loadingTitleRes by mutableStateOf(R.string.status_processing)
        private set

    // ─── Capacity & used bytes ────────────────────────────────────────
    var capacityBytes by mutableStateOf(0)
        private set
    var usedBytes by mutableStateOf(0)
        private set

    // ─── Decode back-off (persisted) ──────────────────────────────────
    // This slows down guessing on this device only: anyone who has the image
    // can attack it elsewhere, so the real protection against brute force is
    // the key derivation. It therefore applies per image and forgets old
    // failures rather than locking the user out of everything.
    var decodeLockedUntilMs by mutableStateOf(prefs.decodeLockedUntilMs)
        private set
    val isDecodeLocked: Boolean
        get() = decodeLockedUntilMs > System.currentTimeMillis() &&
            currentImageId == prefs.decodeFailureImageId

    // ─── Export ───────────────────────────────────────────────────────
    var showExportWarning by mutableStateOf(false)
        private set
    /** System share sheet for the ZIP; the UI launches it and reports back. */
    var pendingShareIntent by mutableStateOf<Intent?>(null)
        private set
    var hideExportWarning by mutableStateOf(prefs.hideExportWarning)

    private var probeJob: Job? = null
    private var usageJob: Job? = null
    private var backgroundedAtMs: Long? = null

    init {
        // Nothing can be mid-share at launch, so whatever is here was orphaned
        // when the app was killed with a share sheet open.
        viewModelScope.launch(Dispatchers.IO) { exportsDir().deleteRecursively() }
        forgetExpiredDecodeFailures()
    }

    // ─── Derived UI state ─────────────────────────────────────────────

    /** An unmarked image while the user isn't composing anything of their own. */
    private val isIdleOnUnmarkedImage: Boolean
        get() = probe?.hasHiddenData == false && message.isEmpty() &&
            attachedFile == null && extractedPayload == null

    /**
     * A password-protected image gives no sign of what it holds, so any image
     * in a format that could carry data might be one.
     */
    val mayHoldProtectedData: Boolean
        get() = isIdleOnUnmarkedImage && encryption.requiresPassword && selectedFormat.canCarryData

    val canDecode: Boolean
        get() = probe?.hasHiddenData == true || mayHoldProtectedData

    val decodeNeedsPassword: Boolean
        get() = probe?.hasHiddenData != true || probe?.requiresPassword == true

    /** Text shown under Decrypt, or null. */
    fun decodeHint(): String? {
        if (!isIdleOnUnmarkedImage) return null
        if (mayHoldProtectedData) return ctxStr(R.string.status_stealth_hint)
        return selectedFormat.lossyName?.let {
            getApplication<Application>().getString(R.string.status_cannot_carry_data, it)
        }
    }

    private val currentImageId: String
        get() = probe?.salt?.let { Base64.encodeToString(it, Base64.NO_WRAP) } ?: ""

    // ─── Setters that persist or recompute side-effects ───────────────

    fun updateImageSize(p: ImageSizePolicy) {
        imageSize = p
        prefs.imageSizeRaw = p.rawValue
        recomputeCapacity()
    }

    fun updateHideExportWarning(value: Boolean) {
        hideExportWarning = value
        prefs.hideExportWarning = value
    }

    fun onClearStatus()    { statusBanner = null }
    fun onClearExtracted() { extractedPayload = null }
    fun onLockoutExpired() {
        prefs.decodeLockedUntilMs = 0L
        decodeLockedUntilMs = 0L
    }

    // ─── App lifecycle ────────────────────────────────────────────────

    /** Secrets are wiped if the app stays in the background this long. */
    fun onAppBackgrounded() {
        if (backgroundedAtMs == null) backgroundedAtMs = System.currentTimeMillis()
    }

    fun onAppForegrounded() {
        val since = backgroundedAtMs
        backgroundedAtMs = null
        if (since != null && System.currentTimeMillis() - since >= SECRETS_CLEAR_DELAY_MS) clearSecrets()
        // Back from the share sheet: the ZIP has been handed over or dropped.
        if (pendingShareIntent == null) viewModelScope.launch(Dispatchers.IO) { exportsDir().deleteRecursively() }
    }

    private fun clearSecrets() {
        val hadSecrets = password.isNotEmpty() || message.isNotEmpty() ||
            attachedFile != null || extractedPayload != null
        if (!hadSecrets) return
        password = ""
        confirmedPassword = null
        repeatedPassword = ""
        isPasswordVisible = false
        message = ""
        attachedFile = null
        extractedPayload = null
        statusBanner = StatusBanner(StatusBanner.Kind.Info, ctxStr(R.string.status_secrets_cleared))
        recomputeUsedBytes()
    }

    // ─── Intents from UI ──────────────────────────────────────────────

    /** Photo Picker / SAF result OR incoming Intent VIEW/SEND uri. */
    fun onImageSelected(uri: Uri, fromCamera: Boolean = false) {
        probeJob?.cancel()
        val ctx = getApplication<Application>()
        encodedBitmap = null
        encodedPng = null
        extractedPayload = null
        statusBanner = null
        probe = null

        probeJob = viewModelScope.launch {
            startLoading(R.string.status_analyzing)
            val loaded = loadImage(ctx.contentResolver, uri)
            selectedBitmap = loaded?.first
            selectedFormat = when {
                fromCamera -> ImageSourceFormat.CAMERA
                else -> loaded?.second ?: ImageSourceFormat.UNKNOWN
            }
            recomputeCapacity()
            recomputeUsedBytes()
            val bmp = loaded?.first
            if (bmp == null) {
                stopLoading()
                statusBanner = StatusBanner(StatusBanner.Kind.Error, ctxStr(R.string.error_image_process))
                return@launch
            }

            val result = withContext(Dispatchers.Default) {
                val rgba = BitmapPixels.toRgba(bmp)
                SteganographyService.probe(rgba.pixels, rgba.width, rgba.height)
            }
            stopLoading()
            probe = result

            if (result.hasHiddenData && (result.encryption?.requiresPassword == true)) {
                statusBanner = StatusBanner(StatusBanner.Kind.Info, ctxStr(R.string.status_enter_password))
            }
            // Any image without a readable marker may be a protected one, so
            // the password field should be reachable for those too.
            if (result.requiresPassword || !result.hasHiddenData) settingsExpanded = true
        }
    }

    /** File-picker result for attachment. */
    fun onAttachmentSelected(uri: Uri, displayName: String) {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            startLoading(R.string.status_processing)
            val bytes = withContext(Dispatchers.IO) {
                try {
                    ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                } catch (_: Throwable) { null }
            }
            stopLoading()
            if (bytes == null) {
                statusBanner = StatusBanner(StatusBanner.Kind.Error, ctxStr(R.string.error_image_process))
                return@launch
            }
            attachedFile = AttachedFile(displayName, bytes)
            message = ""    // mutually exclusive with text
            recomputeUsedBytes()
        }
    }

    fun onRemoveAttachment() {
        attachedFile = null
        recomputeUsedBytes()
    }

    fun onMessageChange(value: String) {
        message = value
        recomputeUsedBytes()
    }

    fun onEncryptionChange(value: EncryptionAlgorithm) {
        encryption = value
        recomputeUsedBytes()
    }

    fun onCompressionChange(value: CompressionAlgorithm) {
        compression = value
        recomputeUsedBytes()
    }

    // ─── Passwords ────────────────────────────────────────────────────

    fun generatePassword() {
        val generated = PasswordGenerator.generate()
        password = generated
        confirmedPassword = generated
        // Shown so it can be read and copied, because nobody else knows it yet.
        isPasswordVisible = true
        statusBanner = StatusBanner(StatusBanner.Kind.Info, ctxStr(R.string.status_save_password))
    }

    fun onEmbedTapped() {
        val needsConfirmation = encryption.requiresPassword && password.isNotEmpty() &&
            !isPasswordVisible && password != confirmedPassword
        if (needsConfirmation) {
            repeatedPassword = ""
            showPasswordConfirmation = true
        } else {
            onEncode()
        }
    }

    fun onPasswordRepeated() {
        val matches = repeatedPassword == password
        repeatedPassword = ""
        showPasswordConfirmation = false
        if (!matches) {
            statusBanner = StatusBanner(StatusBanner.Kind.Error, ctxStr(R.string.error_passwords_dont_match))
            return
        }
        confirmedPassword = password
        onEncode()
    }

    fun onPasswordConfirmationCancelled() {
        repeatedPassword = ""
        showPasswordConfirmation = false
    }

    // ─── Embed / extract ──────────────────────────────────────────────

    private fun onEncode() {
        val src = selectedBitmap ?: return
        val payload = currentPayload() ?: run {
            statusBanner = StatusBanner(StatusBanner.Kind.Error, ctxStr(R.string.error_empty))
            return
        }
        val options = StegoOptions(encryption, compression, password, maxImageEdge = null)

        viewModelScope.launch {
            startLoading(R.string.status_embedding)
            try {
                val (bitmap, png) = withContext(Dispatchers.Default) {
                    val carrier = BitmapPixels.prepareCarrier(src, imageSize.maxEdge)
                    val rgba = BitmapPixels.toRgba(carrier)
                    val out = SteganographyService.encode(payload, rgba.pixels, rgba.width, rgba.height, options)
                    val bmp = BitmapPixels.fromRgba(RgbaBuffer(out, rgba.width, rgba.height))
                    bmp to verifiedPng(bmp, out)
                }
                encodedBitmap = bitmap
                encodedPng = png
                message = ""
                attachedFile = null
                statusBanner = StatusBanner(StatusBanner.Kind.Success, ctxStr(R.string.status_done_embedded))
                // A protected image can't be recognised by reading it, but
                // here we know what was just embedded.
                val read = withContext(Dispatchers.Default) {
                    val rgba = BitmapPixels.toRgba(bitmap)
                    SteganographyService.probe(rgba.pixels, rgba.width, rgba.height)
                }
                probe = read.copy(hasHiddenData = true, encryption = options.encryption, compression = options.compression)
                settingsExpanded = false
                recomputeUsedBytes()
            } catch (e: StegoError) {
                statusBanner = StatusBanner(StatusBanner.Kind.Error, errorText(e))
            } catch (t: Throwable) {
                statusBanner = StatusBanner(StatusBanner.Kind.Error, t.message ?: ctxStr(R.string.error_decode))
            } finally {
                stopLoading()
            }
        }
    }

    /**
     * Encodes PNG and reads it back, comparing every carrier bit, so a result
     * the recipient couldn't read is reported here rather than discovered
     * after it has been sent.
     */
    private fun verifiedPng(bitmap: Bitmap, expected: ByteArray): ByteArray {
        val bytes = ByteArrayOutputStream().use { os ->
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, os)) throw StegoError.PngExportFailed
            os.toByteArray()
        }
        val reloaded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: throw StegoError.PngExportFailed
        val rgba = BitmapPixels.toRgba(reloaded).pixels
        if (rgba.size != expected.size) throw StegoError.PngExportFailed
        for (i in rgba.indices) {
            if (i and 3 != 3 && (rgba[i].toInt() xor expected[i].toInt()) and 1 != 0) {
                throw StegoError.PngExportFailed
            }
        }
        return bytes
    }

    fun onDecode() {
        if (isDecodeLocked) return     // UI also disables the button; defensive
        val src = encodedBitmap ?: selectedBitmap ?: return

        viewModelScope.launch {
            startLoading(R.string.status_extracting)
            extractedPayload = null
            try {
                val payload = withContext(Dispatchers.Default) {
                    val rgba = BitmapPixels.toRgba(src)
                    SteganographyService.decode(rgba.pixels, rgba.width, rgba.height, password)
                }
                extractedPayload = payload
                statusBanner = null
                resetDecodeFailures()
            } catch (_: StegoError.NoHiddenData) {
                statusBanner = StatusBanner(StatusBanner.Kind.Info, ctxStr(R.string.error_no_hidden_data))
            } catch (_: StegoError.WrongPasswordOrCorrupted) {
                applyDecodeFailure()
                statusBanner = StatusBanner(
                    StatusBanner.Kind.Error,
                    ctxStr(if (probe?.hasHiddenData == true) R.string.error_wrong_password else R.string.error_wrong_password_or_no_data)
                )
            } catch (e: StegoError) {
                statusBanner = StatusBanner(StatusBanner.Kind.Error, errorText(e))
            } catch (t: Throwable) {
                statusBanner = StatusBanner(StatusBanner.Kind.Error, t.message ?: ctxStr(R.string.error_decode))
            } finally {
                stopLoading()
            }
        }
    }

    // ─── Export ───────────────────────────────────────────────────────

    fun onExportRequested() {
        if (hideExportWarning) launchExport() else showExportWarning = true
    }

    fun onExportConfirmed() {
        showExportWarning = false
        launchExport()
    }

    fun onExportCancelled() {
        showExportWarning = false
    }

    /**
     * Direct save into the Downloads/Veilix/ collection via MediaStore. The
     * file name travels with the file, so it mustn't announce what made it.
     */
    private fun launchExport() {
        val png = encodedPng ?: return
        val ctx = getApplication<Application>()

        viewModelScope.launch {
            startLoading(R.string.status_processing)
            val uri = withContext(Dispatchers.IO) {
                saveBytesToDownloads(ctx, png, neutralFileName("png"), "image/png")
            }
            stopLoading()
            statusBanner = if (uri != null) {
                StatusBanner(StatusBanner.Kind.Success, ctxStr(R.string.status_saved_to_downloads))
            } else {
                StatusBanner(StatusBanner.Kind.Error, ctxStr(R.string.error_png_export))
            }
        }
    }

    /**
     * Messengers recompress images but deliver files untouched, so the PNG
     * goes inside a ZIP and out through the system share sheet.
     */
    fun onShareZip() {
        val png = encodedPng ?: return
        val ctx = getApplication<Application>()

        viewModelScope.launch {
            startLoading(R.string.status_processing)
            val uri = withContext(Dispatchers.IO) {
                try {
                    val zip = ZipArchive.makeStored(neutralFileName("png"), png)
                    val dir = File(exportsDir(), UUID.randomUUID().toString()).apply { mkdirs() }
                    val file = File(dir, neutralFileName("zip")).apply { writeBytes(zip) }
                    FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
                } catch (_: Throwable) { null }
            }
            stopLoading()
            if (uri == null) {
                statusBanner = StatusBanner(StatusBanner.Kind.Error, ctxStr(R.string.error_png_export))
                return@launch
            }
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            pendingShareIntent = Intent.createChooser(send, null)
        }
    }

    fun onShareIntentLaunched() {
        pendingShareIntent = null
    }

    /** Direct save of an extracted file payload into Downloads/Veilix/. */
    fun saveExtractedFileToDownloads() {
        val file = (extractedPayload as? Payload.File) ?: return
        val ctx = getApplication<Application>()
        // Already sanitized when decoded; again here because it becomes a path.
        val name = PayloadEnvelope.sanitizedFileName(file.name)
        val mime = guessMimeType(name)

        viewModelScope.launch {
            startLoading(R.string.status_processing)
            val uri = withContext(Dispatchers.IO) {
                saveBytesToDownloads(ctx, file.data, name, mime)
            }
            stopLoading()
            statusBanner = if (uri != null) {
                StatusBanner(StatusBanner.Kind.Success, ctxStr(R.string.status_saved_to_downloads))
            } else {
                StatusBanner(StatusBanner.Kind.Error, ctxStr(R.string.error_png_export))
            }
        }
    }

    fun currentExtractedFileName(): String? =
        (extractedPayload as? Payload.File)?.name

    // ─── Camera capture URI ───────────────────────────────────────────

    /**
     * Pre-allocates a writable FileProvider URI the camera Intent can dump
     * the captured image into. The Activity result handler then feeds this
     * URI back into [onImageSelected].
     */
    fun prepareCameraOutputUri(): Uri? {
        val ctx = getApplication<Application>()
        return try {
            val dir = File(ctx.cacheDir, "captures").apply { mkdirs() }
            val file = File(dir, "capture-${System.currentTimeMillis()}.jpg")
            file.createNewFile()
            FileProvider.getUriForFile(
                ctx,
                "${ctx.packageName}.fileprovider",
                file
            )
        } catch (_: Throwable) { null }
    }

    fun onCameraPermissionDenied() {
        statusBanner = StatusBanner(StatusBanner.Kind.Error, ctxStr(R.string.error_camera_denied))
    }

    // ─── Internals ────────────────────────────────────────────────────

    private fun currentPayload(): Payload? {
        attachedFile?.let { return Payload.File(it.name, it.data) }
        if (message.isNotEmpty()) return Payload.Text(message)
        return null
    }

    private fun resetDecodeFailures() {
        prefs.decodeFailures = 0
        prefs.decodeLockedUntilMs = 0L
        prefs.decodeLastFailureMs = 0L
        prefs.decodeFailureImageId = ""
        decodeLockedUntilMs = 0L
    }

    private fun forgetExpiredDecodeFailures() {
        val now = System.currentTimeMillis()
        if (now - prefs.decodeLastFailureMs > DECODE_FAILURE_WINDOW_MS && prefs.decodeLockedUntilMs < now) {
            resetDecodeFailures()
        }
    }

    private fun applyDecodeFailure() {
        forgetExpiredDecodeFailures()
        val imageId = currentImageId
        if (imageId != prefs.decodeFailureImageId) {
            prefs.decodeFailures = 0
            prefs.decodeFailureImageId = imageId
        }
        prefs.decodeLastFailureMs = System.currentTimeMillis()
        val failures = prefs.decodeFailures + 1
        prefs.decodeFailures = failures
        // 1st: 0s — 2nd: 0s — 3rd: 30s — 4th: 1m — 5th+: 10m
        val delaySeconds = when (failures) {
            1, 2 -> 0L
            3    -> 30L
            4    -> 60L
            else -> 600L
        }
        val until = if (delaySeconds > 0) System.currentTimeMillis() + delaySeconds * 1000L else 0L
        prefs.decodeLockedUntilMs = until
        decodeLockedUntilMs = until
    }

    private fun recomputeCapacity() {
        val bmp = selectedBitmap
        if (bmp == null) {
            capacityBytes = 0; return
        }
        val maxEdge = imageSize.maxEdge
        val longEdge = maxOf(bmp.width, bmp.height)
        val scale = if (maxEdge != null && longEdge > maxEdge)
            maxEdge.toDouble() / longEdge else 1.0
        val w = (bmp.width * scale).toInt()
        val h = (bmp.height * scale).toInt()
        val rgbBits = w * h * 3
        val bodyBits = (rgbBits - PayloadHeader.BIT_SIZE).coerceAtLeast(0) * 9 / 10
        capacityBytes = bodyBits / 8
    }

    private fun recomputeUsedBytes() {
        usageJob?.cancel()
        val payload = currentPayload()
        if (payload == null || payload.rawByteCount == 0) {
            usedBytes = 0
            return
        }
        val comp = compression
        val enc = encryption
        usageJob = viewModelScope.launch(Dispatchers.Default) {
            val raw = PayloadEnvelope.encode(payload)
            val compressed = try { CompressionService.compress(raw, comp) } catch (_: Throwable) { raw }
            val overhead = if (enc.requiresPassword) 16 else 0
            val total = compressed.size + overhead
            withContext(Dispatchers.Main) { usedBytes = total }
        }
    }

    // Conservative remaining-character estimate (UTF-8 bytes per char from message).
    fun approxCharsRemaining(): Int? {
        if (message.isEmpty() || usedBytes >= capacityBytes) return null
        val avg = (message.toByteArray(Charsets.UTF_8).size.toDouble() / message.length).coerceAtLeast(1.0)
        val remaining = (capacityBytes - usedBytes).toDouble()
        return (remaining / avg).toInt()
    }

    private fun startLoading(titleRes: Int) {
        loadingTitleRes = titleRes
        isLoading = true
    }
    private fun stopLoading() { isLoading = false }

    private fun ctxStr(resId: Int): String =
        getApplication<Application>().getString(resId)

    private fun errorText(e: StegoError): String = when (e) {
        StegoError.MessageTooLarge -> ctxStr(R.string.error_message_too_large)
        StegoError.PasswordRequired -> ctxStr(R.string.error_password_required)
        StegoError.UnsupportedAlgorithm -> ctxStr(R.string.error_unsupported_algorithm)
        StegoError.DecompressionFailed -> ctxStr(R.string.error_decompression)
        StegoError.PngExportFailed -> ctxStr(R.string.error_png_export)
        StegoError.HeaderCorrupted -> ctxStr(R.string.error_header)
        else -> e.message ?: ctxStr(R.string.error_decode)
    }

    private fun exportsDir(): File = File(getApplication<Application>().cacheDir, "exports")

    /** A camera-like name: the file leaves the device and its name goes with it. */
    private fun neutralFileName(extension: String) = "IMG_${(1000..9999).random()}.$extension"

    /**
     * Reads the picked file, takes the PNG out of a ZIP if needed, and
     * reports what format the image bytes really are.
     */
    private suspend fun loadImage(resolver: ContentResolver, uri: Uri): Pair<Bitmap, ImageSourceFormat>? =
        withContext(Dispatchers.IO) {
            try {
                val raw = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext null
                val bytes = if (ZipArchive.isArchive(raw)) ZipArchive.firstPng(raw) ?: return@withContext null else raw
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@withContext null
                bitmap to ImageSourceFormat.sniff(bytes)
            } catch (_: Throwable) { null }
        }

    /**
     * Writes a byte buffer into Downloads/Veilix/ as [displayName] with the
     * given MIME type.
     */
    private fun saveBytesToDownloads(
        context: Context,
        data: ByteArray,
        displayName: String,
        mimeType: String
    ): Uri? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, displayName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(
                MediaStore.Downloads.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS + "/Veilix"
            )
            put(MediaStore.Downloads.IS_PENDING, 1)
        }

        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: return null

        return try {
            resolver.openOutputStream(uri).use { os: OutputStream? ->
                if (os == null) error("openOutputStream returned null")
                os.write(data)
            }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } catch (_: Throwable) {
            resolver.delete(uri, null, null)
            null
        }
    }

    /** Maps a file extension to a MIME type, falling back to octet-stream. */
    private fun guessMimeType(filename: String): String {
        val ext = filename.substringAfterLast('.', "").lowercase()
        if (ext.isEmpty()) return "application/octet-stream"
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            ?: "application/octet-stream"
    }

    data class AttachedFile(val name: String, val data: ByteArray) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is AttachedFile) return false
            return name == other.name && data.contentEquals(other.data)
        }
        override fun hashCode(): Int = 31 * name.hashCode() + data.contentHashCode()
    }

    private companion object {
        const val SECRETS_CLEAR_DELAY_MS = 60_000L
        const val DECODE_FAILURE_WINDOW_MS = 60 * 60 * 1000L
    }
}
