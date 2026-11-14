package dev.codexsoft.codexcine.ui.browse

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import dev.codexsoft.codexcine.R
import dev.codexsoft.codexcine.ui.compose.components.requestFocusWhenReady
import dev.codexsoft.codexcine.ui.compose.theme.BrandDisplay
import dev.codexsoft.codexcine.ui.compose.theme.BrandText
import dev.codexsoft.codexcine.ui.compose.theme.CodexTheme
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay

private const val SPLASH_HANDOVER_MS = 450L
private const val LOGO_MS = 900
private const val WORD_MS = 420
private const val TITLE_STAGGER_MS = 260L
private const val TAGLINE_WORD_MS = 320
private const val TAGLINE_STAGGER_MS = 170L
private const val HOLD_MS = 900L
private val Smooth = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)

/**
 * The opening animation, in the spirit of a streaming service's: the logo blooms out of the dark with a
 * pulse of light, the name arrives word by word under it, an amber line is drawn, then the tagline follows
 * word by word. When it is over, [onFinished] is called once; OK or Back skips it.
 */
@Composable
fun IntroSplash(onFinished: () -> Unit, modifier: Modifier = Modifier) {
    val colors = CodexTheme.colors
    val title = stringResource(R.string.app_name).split(' ')
    val tagline = stringResource(R.string.intro_tagline).split(' ')

    val logoAlpha = remember { Animatable(0f) }
    val logoScale = remember { Animatable(0.6f) }
    val glow = remember { Animatable(0f) }
    val titleWords = remember(title) { title.map { Animatable(0f) } }
    val line = remember { Animatable(0f) }
    val taglineWords = remember(tagline) { tagline.map { Animatable(0f) } }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) { focus.requestFocusWhenReady() }
    LaunchedEffect(Unit) {
        // The system splash is still fading away as this starts: begin once it has gone.
        delay(SPLASH_HANDOVER_MS)
        coroutineScope {
            // The logo and its light.
            val logo = listOf(
                async { logoAlpha.animateTo(1f, tween(LOGO_MS, easing = Smooth)) },
                async { logoScale.animateTo(1f, tween(LOGO_MS + 200, easing = Smooth)) },
                async {
                    glow.animateTo(1f, tween(LOGO_MS, easing = FastOutSlowInEasing))
                    glow.animateTo(0.35f, tween(1800, easing = FastOutSlowInEasing))
                },
            )
            delay(LOGO_MS - 200L)
            // The name, word by word, and the line under it.
            val name = titleWords.mapIndexed { i, word ->
                async {
                    delay(i * TITLE_STAGGER_MS)
                    word.animateTo(1f, tween(WORD_MS, easing = Smooth))
                }
            }
            delay(titleWords.size * TITLE_STAGGER_MS + 100)
            val underline = async { line.animateTo(1f, tween(600, easing = Smooth)) }
            delay(450)
            // The tagline, word by word.
            val words = taglineWords.mapIndexed { i, word ->
                async {
                    delay(i * TAGLINE_STAGGER_MS)
                    word.animateTo(1f, tween(TAGLINE_WORD_MS, easing = Smooth))
                }
            }
            (logo + name + underline + words).awaitAll()
        }
        delay(HOLD_MS)
        onFinished()
    }

    BackHandler(onBack = onFinished)

    Box(
        modifier
            .fillMaxSize()
            .background(colors.night)
            .focusRequester(focus)
            .onKeyEvent { event ->
                val skip = event.type == KeyEventType.KeyUp &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.Back)
                if (skip) onFinished()
                skip
            }
            .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        // A soft amber light behind the logo.
        Box(
            Modifier
                .size(560.dp)
                .alpha(glow.value * 0.55f)
                .background(Brush.radialGradient(listOf(colors.amber.copy(alpha = 0.55f), Color.Transparent))),
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(R.drawable.ic_logo_mark),
                contentDescription = null,
                modifier = Modifier
                    .size(132.dp)
                    .scale(logoScale.value)
                    .alpha(logoAlpha.value),
            )
            Row(Modifier.padding(top = 28.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                titleWords.forEachIndexed { i, progress ->
                    RevealWord(
                        text = title[i],
                        progress = progress.value,
                        style = TextStyle(fontFamily = BrandDisplay, fontWeight = FontWeight.Bold, fontSize = 64.sp),
                        color = if (i == title.lastIndex) colors.amber else colors.ivory,
                        rise = 24.dp.value,
                    )
                }
            }
            Box(
                Modifier
                    .padding(top = 18.dp)
                    .width(320.dp * line.value)
                    .height(3.dp)
                    .background(colors.amber, RoundedCornerShape(50)),
            )
            Row(
                Modifier.padding(top = 26.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            ) {
                taglineWords.forEachIndexed { i, progress ->
                    RevealWord(
                        text = tagline[i],
                        progress = progress.value,
                        style = TextStyle(fontFamily = BrandText, fontSize = 26.sp, textAlign = TextAlign.Center),
                        color = colors.mist,
                        rise = 14.dp.value,
                    )
                }
            }
        }
    }
}

/** A word that fades in while rising a few dp into place. */
@Composable
private fun RevealWord(text: String, progress: Float, style: TextStyle, color: Color, rise: Float) {
    Text(
        text = text,
        style = style,
        color = color,
        modifier = Modifier.graphicsLayer {
            alpha = progress
            translationY = (1f - progress) * rise * density
        },
    )
}
