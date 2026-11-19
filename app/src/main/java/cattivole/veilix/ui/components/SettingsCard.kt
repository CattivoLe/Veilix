package cattivole.veilix.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cattivole.veilix.R
import cattivole.veilix.core.stego.CompressionAlgorithm
import cattivole.veilix.core.stego.EncryptionAlgorithm
import cattivole.veilix.core.stego.ImageSizePolicy
import cattivole.veilix.ui.theme.BrandColors

/**
 * Collapsible card holding the three algorithm pickers and the password
 * input (when encryption is on or the probed image requires one).
 *
 * Mirrors iOS `SettingsCard` — header is tap-to-toggle and shows the current
 * algorithm summary when collapsed.
 */
@Composable
fun SettingsCard(
    encryption: EncryptionAlgorithm,
    onEncryptionChange: (EncryptionAlgorithm) -> Unit,
    compression: CompressionAlgorithm,
    onCompressionChange: (CompressionAlgorithm) -> Unit,
    imageSize: ImageSizePolicy,
    onImageSizeChange: (ImageSizePolicy) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    requiresPasswordFromProbe: Boolean,
    modifier: Modifier = Modifier,
    isPasswordVisible: Boolean = false,
    onPasswordVisibleChange: (Boolean) -> Unit = {},
    onGeneratePassword: (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(16.dp)
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
    val showPassword = encryption.requiresPassword || requiresPasswordFromProbe

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(BrandColors.Surface)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleExpanded),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = BrandColors.TextPrimary,
                modifier = Modifier.weight(1f)
            )
            if (!expanded) {
                Text(
                    text = collapsedSummary(encryption, compression),
                    style = MaterialTheme.typography.bodySmall,
                    color = BrandColors.TextSecondary,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
            Icon(
                imageVector = Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = BrandColors.TextSecondary,
                modifier = Modifier.rotate(rotation)
            )
        }

        var infoDialog by remember { mutableStateOf<InfoDialogTarget?>(null) }

        AnimatedVisibility(visible = expanded) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(top = 14.dp)
            ) {
                LabeledPicker(
                    label = stringResource(R.string.settings_encryption),
                    selected = encryption,
                    items = EncryptionAlgorithm.entries,
                    onSelected = onEncryptionChange,
                    labelFor = { encryptionLabel(it) },
                    onInfo = { infoDialog = InfoDialogTarget.Encryption }
                )
                LabeledPicker(
                    label = stringResource(R.string.settings_compression),
                    selected = compression,
                    // Only NONE + ZLIB — LZFSE/LZMA are recognised on the
                    // decode side (so iOS-encoded images surface a clear
                    // "unsupported algorithm" error) but hidden from the
                    // encoder picker because we can't actually produce them.
                    items = CompressionAlgorithm.encodableEntries,
                    onSelected = onCompressionChange,
                    labelFor = { compressionLabel(it) },
                    onInfo = { infoDialog = InfoDialogTarget.Compression }
                )
                LabeledPicker(
                    label = stringResource(R.string.settings_image_size),
                    selected = imageSize,
                    items = ImageSizePolicy.entries,
                    onSelected = onImageSizeChange,
                    labelFor = { imageSizeLabel(it) },
                    onInfo = { infoDialog = InfoDialogTarget.ImageSize }
                )

                if (showPassword) {
                    PasswordField(
                        value = password,
                        onValueChange = onPasswordChange,
                        modifier = Modifier.fillMaxWidth(),
                        // Decryption flow grades a known-existing password —
                        // strength meter is irrelevant and misleading.
                        showStrengthMeter = !requiresPasswordFromProbe,
                        visible = isPasswordVisible,
                        onVisibleChange = onPasswordVisibleChange,
                        // No new password to generate when decrypting.
                        onGenerate = if (requiresPasswordFromProbe) null else onGeneratePassword
                    )
                }
            }
        }

        infoDialog?.let { target ->
            InfoPopover(
                title = stringResource(target.titleRes),
                summary = stringResource(target.summaryRes),
                options = target.options(),
                onDismiss = { infoDialog = null }
            )
        }
    }
}

private enum class InfoDialogTarget(val titleRes: Int, val summaryRes: Int) {
    Encryption(R.string.settings_encryption, R.string.info_encryption_summary),
    Compression(R.string.settings_compression, R.string.info_compression_summary),
    ImageSize(R.string.settings_image_size, R.string.info_image_size_summary);

    @Composable
    fun options(): List<InfoOption> = when (this) {
        Encryption  -> EncryptionAlgorithm.entries.map { it.toInfoOption() }
        // Mirror what the picker shows — don't tease the user with
        // LZFSE/LZMA when they can't actually pick them.
        Compression -> CompressionAlgorithm.encodableEntries.map { it.toInfoOption() }
        ImageSize   -> ImageSizePolicy.entries.map { it.toInfoOption() }
    }
}

@Composable
private fun EncryptionAlgorithm.toInfoOption(): InfoOption = InfoOption(
    name = encryptionLabel(this),
    description = stringResource(
        when (this) {
            EncryptionAlgorithm.NONE        -> R.string.enc_none_desc
            EncryptionAlgorithm.AES_GCM     -> R.string.enc_aes_desc
            EncryptionAlgorithm.CHACHA_POLY -> R.string.enc_chacha_desc
        }
    ),
    warning = when (this) {
        EncryptionAlgorithm.CHACHA_POLY -> stringResource(R.string.enc_chacha_note)
        else -> null
    }
)

@Composable
private fun CompressionAlgorithm.toInfoOption(): InfoOption = InfoOption(
    name = compressionLabel(this),
    description = stringResource(
        when (this) {
            CompressionAlgorithm.NONE  -> R.string.comp_none_desc
            CompressionAlgorithm.ZLIB  -> R.string.comp_zlib_desc
            CompressionAlgorithm.LZFSE -> R.string.comp_lzfse_desc
            CompressionAlgorithm.LZMA  -> R.string.comp_lzma_desc
        }
    ),
    warning = when (this) {
        CompressionAlgorithm.LZFSE -> stringResource(R.string.comp_lzfse_note)
        CompressionAlgorithm.LZMA  -> stringResource(R.string.comp_lzma_note)
        else -> null
    }
)

@Composable
private fun ImageSizePolicy.toInfoOption(): InfoOption = InfoOption(
    name = imageSizeLabel(this),
    description = stringResource(
        when (this) {
            ImageSizePolicy.COMPACT  -> R.string.size_compact_desc
            ImageSizePolicy.STANDARD -> R.string.size_standard_desc
            ImageSizePolicy.LARGE    -> R.string.size_large_desc
            ImageSizePolicy.ORIGINAL -> R.string.size_original_desc
        }
    ),
    warning = null
)

@Composable
private fun <T : Any> LabeledPicker(
    label: String,
    selected: T,
    items: List<T>,
    onSelected: (T) -> Unit,
    labelFor: @Composable (T) -> String,
    onInfo: () -> Unit
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = BrandColors.TextSecondary,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onInfo,
                modifier = Modifier.padding(end = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = stringResource(R.string.info_more),
                    tint = BrandColors.Cyan
                )
            }
        }
        AlgorithmPicker(
            items = items,
            selected = selected,
            onSelected = onSelected,
            label = labelFor
        )
    }
}

@Composable
private fun collapsedSummary(enc: EncryptionAlgorithm, comp: CompressionAlgorithm): String {
    val e = encryptionLabel(enc)
    val c = compressionLabel(comp)
    return "$e · $c"
}

@Composable
fun encryptionLabel(algo: EncryptionAlgorithm): String = stringResource(
    when (algo) {
        EncryptionAlgorithm.NONE        -> R.string.enc_none
        EncryptionAlgorithm.AES_GCM     -> R.string.enc_aes
        EncryptionAlgorithm.CHACHA_POLY -> R.string.enc_chacha
    }
)

@Composable
fun compressionLabel(algo: CompressionAlgorithm): String = stringResource(
    when (algo) {
        CompressionAlgorithm.NONE  -> R.string.comp_none
        CompressionAlgorithm.ZLIB  -> R.string.comp_zlib
        CompressionAlgorithm.LZFSE -> R.string.comp_lzfse
        CompressionAlgorithm.LZMA  -> R.string.comp_lzma
    }
)

@Composable
fun imageSizeLabel(policy: ImageSizePolicy): String = stringResource(
    when (policy) {
        ImageSizePolicy.COMPACT  -> R.string.size_compact
        ImageSizePolicy.STANDARD -> R.string.size_standard
        ImageSizePolicy.LARGE    -> R.string.size_large
        ImageSizePolicy.ORIGINAL -> R.string.size_original
    }
)
