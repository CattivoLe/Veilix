package cattivole.veilix.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RemoveRedEye
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cattivole.veilix.R
import cattivole.veilix.ui.theme.BrandColors

/**
 * Top card with the carrier image. When empty it shows a brand-themed
 * placeholder at a fixed height. When an image is picked, the container
 * grows to match the image's aspect ratio at full screen width — so a
 * portrait photo gets a tall card, a landscape photo a short one, and the
 * image fills the entire card without letterboxing.
 */
@Composable
fun ImagePickerCard(
    bitmap: Bitmap?,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    placeholderHeightDp: Int = 220
) {
    val shape = RoundedCornerShape(16.dp)
    val border = Modifier.border(
        width = 1.dp,
        brush = Brush.linearGradient(
            colors = listOf(
                BrandColors.Violet.copy(alpha = 0.45f),
                BrandColors.Cyan.copy(alpha = 0.25f)
            )
        ),
        shape = shape
    )

    if (bitmap != null) {
        // Container adapts to image aspect ratio at full width.
        val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
        Box(
            modifier = modifier
                .fillMaxWidth()
                .aspectRatio(ratio)
                .clip(shape)
                .then(border)
                .clickable(onClick = onTap)
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(ratio)
            )
        }
    } else {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(placeholderHeightDp.dp)
                .clip(shape)
                .background(BrandColors.Surface)
                .then(border)
                .clickable(onClick = onTap),
            contentAlignment = Alignment.Center
        ) {
            Placeholder()
        }
    }
}

@Composable
private fun Placeholder() {
    Box(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.RemoveRedEye,
                contentDescription = null,
                tint = BrandColors.Violet,
                modifier = Modifier.size(40.dp)
            )
            androidx.compose.foundation.layout.Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.picker_choose),
                color = BrandColors.TextSecondary,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
