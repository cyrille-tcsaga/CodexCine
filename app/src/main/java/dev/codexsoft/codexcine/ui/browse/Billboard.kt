package dev.codexsoft.codexcine.ui.browse

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.codexsoft.codexcine.R
import dev.codexsoft.codexcine.core.Formatters
import dev.codexsoft.codexcine.domain.model.MediaItem
import dev.codexsoft.codexcine.domain.model.MediaType
import dev.codexsoft.codexcine.ui.compose.components.LocalLeftEdge
import dev.codexsoft.codexcine.ui.compose.components.PillButton
import dev.codexsoft.codexcine.ui.compose.theme.CodexTheme
import kotlinx.coroutines.delay

private const val ROTATION_MS = 8_000L
private const val FADE_MS = 400
private const val BILLBOARD_TEXT_WIDTH = 640

/**
 * The home "billboard": a featured title's name, year, rating, overview and two rounded buttons, over its
 * artwork (the screen backdrop). It moves on to the next title every few seconds, except while the viewer
 * is on its buttons, so the button they are about to press never changes title.
 *
 * @param onFeatured a title is featured: its artwork should become the backdrop.
 */
@Composable
fun Billboard(
    items: List<MediaItem>,
    onPlay: (MediaItem) -> Unit,
    onMoreInfo: (MediaItem) -> Unit,
    onFeatured: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    var index by rememberSaveable { mutableIntStateOf(0) }
    var hasFocus by remember { mutableStateOf(false) }
    val current = items[index.coerceIn(0, items.lastIndex)]
    val onLeftEdge = LocalLeftEdge.current

    LaunchedEffect(items.size, hasFocus) {
        if (hasFocus || items.size < 2) return@LaunchedEffect
        while (true) {
            delay(ROTATION_MS)
            index = (index + 1) % items.size
        }
    }
    LaunchedEffect(current.id, current.mediaType) { onFeatured(current) }

    // Fades in only: an interrupted fade-in still ends visible, which a fade-out could not promise.
    val fade = remember { Animatable(1f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(index) {
        if (first) {
            first = false
        } else {
            fade.snapTo(0f)
            fade.animateTo(1f, tween(FADE_MS))
        }
    }

    val colors = CodexTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = CodexTheme.dimens.heroPaddingStart, end = CodexTheme.dimens.heroPaddingEnd, bottom = 24.dp)
            .onFocusChanged { hasFocus = it.hasFocus },
        verticalArrangement = Arrangement.Bottom,
    ) {
        Column(Modifier.width(BILLBOARD_TEXT_WIDTH.dp).alpha(fade.value)) {
            Text(
                text = stringResource(if (current.mediaType == MediaType.TV) R.string.hero_label_tv else R.string.hero_label_movie)
                    .uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = colors.amber,
            )
            Text(
                text = current.title,
                style = MaterialTheme.typography.displayLarge.merge(TextStyle(fontSize = 48.sp)),
                color = colors.ivory,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
            val rating = current.voteAverage.takeIf { it > 0 }
                ?.let { stringResource(R.string.rating_stars, Formatters.rating(it)) }
            val meta = listOfNotNull(current.releaseYear, rating).joinToString(stringResource(R.string.separator))
            Text(
                text = meta,
                style = MaterialTheme.typography.bodyLarge.merge(TextStyle(fontWeight = FontWeight.Medium)),
                color = colors.ivory,
                modifier = Modifier.padding(top = 10.dp),
            )
            if (!current.overview.isNullOrBlank()) {
                Text(
                    text = current.overview,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.mist,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            Row(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(
                    Modifier.onKeyEvent { event ->
                        val isLeft = event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft
                        if (isLeft) onLeftEdge?.invoke()
                        isLeft && onLeftEdge != null
                    },
                ) {
                    PillButton(
                        text = stringResource(R.string.action_play),
                        onClick = { onPlay(current) },
                        primary = true,
                        icon = R.drawable.ic_player_play,
                        description = "${stringResource(R.string.action_play)} ${current.title}",
                    )
                }
                PillButton(
                    text = stringResource(R.string.hero_more_info),
                    onClick = { onMoreInfo(current) },
                    icon = R.drawable.ic_more_info,
                    description = "${stringResource(R.string.hero_more_info)} ${current.title}",
                )
            }
        }
        if (items.size > 1) Dots(count = items.size, active = index.coerceIn(0, items.lastIndex))
    }
}

/** One pill per featured title; the current one is longer and amber. */
@Composable
private fun Dots(count: Int, active: Int) {
    val colors = CodexTheme.colors
    Row(Modifier.padding(top = 22.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(count) { i ->
            val isActive = i == active
            Box(
                Modifier
                    .width(if (isActive) 32.dp else 14.dp)
                    .height(5.dp)
                    .alpha(if (isActive) 1f else 0.4f)
                    .background(if (isActive) colors.amber else colors.ivory, RoundedCornerShape(50)),
            )
        }
    }
}
