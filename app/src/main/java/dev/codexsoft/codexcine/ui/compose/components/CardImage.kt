package dev.codexsoft.codexcine.ui.compose.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import dev.codexsoft.codexcine.R
import dev.codexsoft.codexcine.ui.compose.theme.CodexTheme

/**
 * A remote image at exactly the card size, through Glide like the Leanback cards: same cache, same
 * `centerCrop`, same RGB_565 decoding. A missing or failed image shows the card colour with a small icon.
 * Glide's Compose integration is still beta, so a plain target is used instead.
 */
@Composable
fun CardImage(
    url: String?,
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier,
    @androidx.annotation.DrawableRes errorIcon: Int? = R.drawable.ic_movie,
    placeholder: androidx.compose.ui.graphics.Color? = null,
) {
    val context = LocalContext.current.applicationContext
    val density = LocalDensity.current
    val widthPx = with(density) { width.roundToPx() }
    val heightPx = with(density) { height.roundToPx() }
    var bitmap by remember(url, widthPx, heightPx) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(url, widthPx, heightPx) { mutableStateOf(url == null) }

    DisposableEffect(url, widthPx, heightPx) {
        if (url == null) return@DisposableEffect onDispose {}
        val target = object : CustomTarget<Bitmap>(widthPx, heightPx) {
            override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                bitmap = resource.asImageBitmap()
            }

            override fun onLoadFailed(errorDrawable: android.graphics.drawable.Drawable?) {
                failed = true
            }

            override fun onLoadCleared(placeholder: android.graphics.drawable.Drawable?) {
                bitmap = null
            }
        }
        Glide.with(context)
            .asBitmap()
            .load(url)
            .centerCrop()
            .format(DecodeFormat.PREFER_RGB_565)
            .into(target)
        // Clearing hands the bitmap back to Glide's pool: by then it is no longer drawn.
        onDispose { Glide.with(context).clear(target) }
    }

    Box(modifier.background(placeholder ?: CodexTheme.colors.card)) {
        val loaded = bitmap
        when {
            loaded != null -> Image(
                bitmap = loaded,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            failed && errorIcon != null -> Image(
                painter = painterResource(errorIcon),
                contentDescription = null,
                modifier = Modifier.size(48.dp).align(Alignment.Center),
            )
        }
    }
}
