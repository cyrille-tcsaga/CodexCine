package dev.codexsoft.codexcine.ui.compose.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.codexsoft.codexcine.ui.compose.theme.CodexTheme

/** Zoom of the selected card: Leanback's `ZOOM_FACTOR_XSMALL`. */
private const val FOCUS_SCALE = 1.06f

/**
 * Every card of the app: a rounded image of a fixed size, with its title and details underneath. The
 * selected card zooms slightly and gets an ivory ring around its image (no shadow, no glow). A [circular]
 * card (a person) has a round image and its text centred. OK clicks, a long press on OK long-clicks.
 *
 * @param image draws the picture; it fills the image area.
 * @param overlay is drawn over the image, below the ring (progress bar, tags).
 */
@Composable
fun CodexCard(
    title: String,
    subtitle: String?,
    width: Int,
    height: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    circular: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    overlay: @Composable BoxScope.() -> Unit = {},
    image: @Composable (Modifier) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) FOCUS_SCALE else 1f, tween(durationMillis = 150), label = "cardScale")
    val shape = if (circular) CircleShape else RoundedCornerShape(CodexTheme.dimens.cardCornerRadius)

    Column(
        modifier = modifier
            .width(width.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .dpadClickable(onClick, onLongClick)
            .onFocusChanged { focused = it.isFocused }
            .focusable(),
    ) {
        Box(Modifier.size(width.dp, height.dp).clip(shape)) {
            image(Modifier.fillMaxSize())
            overlay()
            if (focused) {
                Box(Modifier.fillMaxSize().border(3.dp, CodexTheme.colors.ivory, shape))
            }
        }
        val textAlign = if (circular) TextAlign.Center else TextAlign.Start
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = CodexTheme.colors.ivory,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = textAlign,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        if (!subtitle.isNullOrEmpty()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = CodexTheme.colors.mist,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = textAlign,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
