package cattivole.veilix.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cattivole.veilix.R
import cattivole.veilix.core.format.Payload
import cattivole.veilix.ui.components.ActionButtons
import cattivole.veilix.ui.components.AppBackground
import cattivole.veilix.ui.components.AttachmentRow
import cattivole.veilix.ui.components.CapacityIndicator
import cattivole.veilix.ui.components.ExportActionButton
import cattivole.veilix.ui.components.ExportWarningSheet
import cattivole.veilix.ui.components.ExtractedFileSection
import cattivole.veilix.ui.components.ExtractedTextSection
import cattivole.veilix.ui.components.HiddenDataBadge
import cattivole.veilix.ui.components.ImagePickerCard
import cattivole.veilix.ui.components.ImageSourceChooser
import cattivole.veilix.ui.components.LoadingOverlay
import cattivole.veilix.ui.components.LockoutBanner
import cattivole.veilix.ui.components.MessageField
import cattivole.veilix.ui.components.SettingsCard
import cattivole.veilix.ui.components.StatusBannerView
import cattivole.veilix.ui.theme.BrandColors

@Composable
fun MainScreen(vm: MainViewModel = viewModel()) {
    val context = LocalContext.current

    // Photo Picker — no permission needed, runs out-of-process (Android's
    // equivalent of iOS PHPickerViewController).
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) vm.onImageSelected(uri)
    }

    // SAF picker for opening an image from arbitrary storage (Drive, Files).
    val imageFilePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) vm.onImageSelected(uri)
    }

    // SAF file picker for attachment payload (any file type).
    val attachmentPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val name = queryDisplayName(context, uri) ?: "attachment"
            vm.onAttachmentSelected(uri, name)
        }
    }

    // Camera capture: take photo into a pre-allocated FileProvider URI.
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCameraUri
        if (success && uri != null) vm.onImageSelected(uri, fromCamera = true)
        pendingCameraUri = null
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val uri = vm.prepareCameraOutputUri()
            if (uri != null) {
                pendingCameraUri = uri
                cameraLauncher.launch(uri)
            }
        } else {
            vm.onCameraPermissionDenied()
        }
    }

    fun startCameraCapture() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        if (granted) {
            val uri = vm.prepareCameraOutputUri()
            if (uri != null) {
                pendingCameraUri = uri
                cameraLauncher.launch(uri)
            }
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var showSourceChooser by remember { mutableStateOf(false) }

    // Image export and extracted-file save both write directly through
    // MediaStore.Downloads in the view-model. Only the ZIP for messengers
    // goes out through the system share sheet.
    val shareLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* ignore result */ }

    LaunchedEffect(vm.pendingShareIntent) {
        val intent = vm.pendingShareIntent ?: return@LaunchedEffect
        shareLauncher.launch(intent)
        vm.onShareIntentLaunched()
    }

    Box(Modifier.fillMaxSize()) {
        AppBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ImagePickerCard(
                bitmap = vm.encodedBitmap ?: vm.selectedBitmap,
                onTap = { showSourceChooser = true }
            )

            vm.probe?.takeIf { it.hasHiddenData }?.let { probe ->
                HiddenDataBadge(probe = probe)
            }

            SettingsCard(
                encryption = vm.encryption,
                onEncryptionChange = vm::onEncryptionChange,
                compression = vm.compression,
                onCompressionChange = vm::onCompressionChange,
                imageSize = vm.imageSize,
                onImageSizeChange = vm::updateImageSize,
                password = vm.password,
                onPasswordChange = { vm.password = it },
                expanded = vm.settingsExpanded,
                onToggleExpanded = { vm.settingsExpanded = !vm.settingsExpanded },
                requiresPasswordFromProbe = vm.probe?.requiresPassword == true,
                isPasswordVisible = vm.isPasswordVisible,
                onPasswordVisibleChange = { vm.isPasswordVisible = it },
                onGeneratePassword = vm::generatePassword
            )

            // In decrypt mode (image already carries hidden data), skip the
            // embed-side UI entirely.
            if (vm.probe?.hasHiddenData != true) {
                if (vm.attachedFile != null) {
                    AttachmentRow(
                        name = vm.attachedFile!!.name,
                        sizeBytes = vm.attachedFile!!.data.size.toLong(),
                        onRemove = vm::onRemoveAttachment
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        MessageField(
                            value = vm.message,
                            onValueChange = vm::onMessageChange,
                            modifier = Modifier.fillMaxSize()
                        )
                        TextButton(
                            onClick = {
                                attachmentPicker.launch(arrayOf("*/*"))
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AttachFile,
                                contentDescription = null,
                                tint = BrandColors.Cyan
                            )
                            Text(
                                text = stringResource(R.string.action_attach),
                                color = BrandColors.Cyan,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(start = 6.dp)
                            )
                        }
                    }
                }

                if (vm.capacityBytes > 0) {
                    CapacityIndicator(
                        usedBytes = vm.usedBytes,
                        capacityBytes = vm.capacityBytes,
                        approxCharsRemaining = vm.approxCharsRemaining(),
                        warnsAboutDetectability = vm.encryption.requiresPassword
                    )
                }
            }

            val canEncode = vm.selectedBitmap != null &&
                (vm.attachedFile != null || vm.message.isNotEmpty()) &&
                vm.probe?.hasHiddenData != true

            ActionButtons(
                canEncode = canEncode,
                canDecode = vm.canDecode,
                isLoading = vm.isLoading,
                decryptEnabled =
                    !(vm.decodeNeedsPassword && vm.password.isEmpty())
                    && !vm.isDecodeLocked,
                embedLabel = stringResource(
                    if (vm.attachedFile != null) R.string.action_embed_file
                    else R.string.action_embed_text
                ),
                decryptLabel = stringResource(R.string.action_decrypt),
                onEncode = vm::onEmbedTapped,
                onDecode = vm::onDecode,
                decodeHint = vm.decodeHint()
            )

            // Live lockout countdown — replaces the regular statusBanner
            // while a decode lock is in effect.
            if (vm.isDecodeLocked) {
                LockoutBanner(
                    untilMs = vm.decodeLockedUntilMs,
                    onExpire = vm::onLockoutExpired
                )
            } else {
                vm.statusBanner?.let { banner -> StatusBannerView(banner = banner) }
            }

            when (val payload = vm.extractedPayload) {
                is Payload.Text -> ExtractedTextSection(
                    text = payload.text,
                    onClear = vm::onClearExtracted
                )
                is Payload.File -> ExtractedFileSection(
                    name = payload.name,
                    sizeBytes = payload.data.size.toLong(),
                    onSave = vm::saveExtractedFileToDownloads,
                    onClear = vm::onClearExtracted
                )
                null -> Unit
            }

            if (vm.encodedBitmap != null) {
                ExportActionButton(
                    label = stringResource(R.string.action_export),
                    onClick = vm::onExportRequested
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ExportActionButton(
                        label = stringResource(R.string.action_share_zip),
                        onClick = vm::onShareZip
                    )
                    Text(
                        text = stringResource(R.string.export_zip_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = BrandColors.TextSecondary
                    )
                }
            }
        }

        if (vm.isLoading) {
            LoadingOverlay(title = stringResource(vm.loadingTitleRes))
        }
    }

    if (vm.showExportWarning) {
        ExportWarningSheet(
            dontShowAgain = vm.hideExportWarning,
            onDontShowAgainChange = vm::updateHideExportWarning,
            onCancel = vm::onExportCancelled,
            onContinue = vm::onExportConfirmed
        )
    }

    if (vm.showPasswordConfirmation) {
        AlertDialog(
            onDismissRequest = vm::onPasswordConfirmationCancelled,
            containerColor = BrandColors.Surface,
            title = { Text(stringResource(R.string.field_repeat_password), color = BrandColors.TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.field_repeat_password_reason), color = BrandColors.TextSecondary)
                    OutlinedTextField(
                        value = vm.repeatedPassword,
                        onValueChange = { vm.repeatedPassword = it },
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.field_password)) },
                        visualTransformation = PasswordVisualTransformation()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = vm::onPasswordRepeated) {
                    Text(stringResource(R.string.export_continue), color = BrandColors.Cyan)
                }
            },
            dismissButton = {
                TextButton(onClick = vm::onPasswordConfirmationCancelled) {
                    Text(stringResource(R.string.export_cancel), color = BrandColors.TextSecondary)
                }
            }
        )
    }

    if (showSourceChooser) {
        ImageSourceChooser(
            onPickGallery = {
                photoPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onTakePhoto = { startCameraCapture() },
            onPickFile = { imageFilePicker.launch(arrayOf("image/*")) },
            onDismiss = { showSourceChooser = false }
        )
    }
}

/** Look up the user-visible filename for an arbitrary content:// URI. */
private fun queryDisplayName(context: android.content.Context, uri: Uri): String? {
    return try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
    } catch (_: Throwable) { null }
}
