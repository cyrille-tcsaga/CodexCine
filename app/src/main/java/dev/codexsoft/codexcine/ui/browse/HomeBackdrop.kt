package dev.codexsoft.codexcine.ui.browse

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import dev.codexsoft.codexcine.ui.common.loadBackgroundBitmap
import dev.codexsoft.codexcine.ui.compose.components.BackdropScrim
import kotlinx.coroutines.delay

private const val BACKDROP_DEBOUNCE_MS = 300L
private const val CROSSFADE_MS = 500

/** The backdrop a card or the billboard asks the home to show. Null: none. */
val LocalBackdrop = androidx.compose.runtime.staticCompositionLocalOf<(String?) -> Unit> { {} }

/**
 * The home's background: the artwork of the title the focus is on, cross-faded, under the scrim. It waits
 * for the selection to settle, so scrolling through a row does not load every backdrop, and keeps the
 * previous image on screen until the next one is ready.
 */
@Composable
fun HomeBackdrop(requestedUrl: String?, modifier: Modifier = Modifier) {
    var url by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(requestedUrl) {
        if (requestedUrl == null) return@LaunchedEffect
        if (url != null) delay(BACKDROP_DEBOUNCE_MS)
        url = requestedUrl
    }
    val context = LocalContext.current
    BoxWithConstraints(modifier.fillMaxSize()) {
        val widthPx = with(LocalDensity.current) { maxWidth.roundToPx() }
        val heightPx = with(LocalDensity.current) { maxHeight.roundToPx() }
        val bitmap by produceState<ImageBitmap?>(initialValue = null, url, widthPx, heightPx) {
            val wanted = url ?: return@produceState
            // `value` keeps the previous image until the next one is loaded.
            loadBackgroundBitmap(context, wanted, widthPx, heightPx)?.let { value = it.asImageBitmap() }
        }
        Crossfade(targetState = bitmap, animationSpec = tween(CROSSFADE_MS), label = "backdrop") { image ->
            if (image != null) {
                Image(image, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        }
        BackdropScrim()
    }
}
