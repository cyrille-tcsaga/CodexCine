package dev.codexsoft.codexcine.ui.compose.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.codexsoft.codexcine.R
import dev.codexsoft.codexcine.ui.compose.theme.CodexTheme

/** The window background of the grid screens (`default_background`): lighter at the top. */
@Composable
fun ScreenBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val top = colorResource(R.color.background_gradient_end)
    val bottom = colorResource(R.color.background_gradient_start)
    Box(modifier.fillMaxSize().background(Brush.verticalGradient(listOf(top, bottom))), content = content)
}

/** Top of the content area on a grid screen, under the title and the hint. */
val ScreenContentTop = 165.dp

/**
 * A screen of cards: its [title] at the top right, an optional [hint] under it, a [loading] indicator, and
 * the [content] below, on the gradient background.
 */
@Composable
fun ScreenFrame(
    title: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    loading: Boolean = false,
    titleBar: (@Composable () -> Unit)? = null,
    contentTop: Dp = ScreenContentTop,
    content: @Composable BoxScope.() -> Unit,
) {
    ScreenBackground(modifier) {
        Box(Modifier.fillMaxSize().padding(top = contentTop), content = content)
        if (titleBar != null) {
            Box(Modifier.align(Alignment.TopStart)) { titleBar() }
        } else Text(
            text = title,
            style = MaterialTheme.typography.displayLarge.merge(TextStyle(fontSize = 45.sp, textAlign = TextAlign.End)),
            color = CodexTheme.colors.ivory,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 32.dp, end = CodexTheme.dimens.heroPaddingStart, start = 300.dp),
        )
        if (hint != null) {
            Text(
                text = hint,
                style = MaterialTheme.typography.bodyLarge,
                color = CodexTheme.colors.mist,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 108.dp, end = CodexTheme.dimens.heroPaddingStart),
            )
        }
        if (loading) LoadingIndicator(Modifier.align(Alignment.Center))
    }
}

/** A spinning amber arc, shown while a screen loads. */
@Composable
fun LoadingIndicator(modifier: Modifier = Modifier) {
    val amber = CodexTheme.colors.amber
    val turn by rememberInfiniteTransition(label = "spinner").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 900, easing = LinearEasing), RepeatMode.Restart),
        label = "spinnerAngle",
    )
    Canvas(modifier.size(48.dp).graphicsLayer { rotationZ = turn }) {
        val stroke = 5.dp.toPx()
        drawArc(
            color = amber,
            startAngle = 0f,
            sweepAngle = 270f,
            useCenter = false,
            topLeft = Offset(stroke / 2, stroke / 2),
            size = Size(size.width - stroke, size.height - stroke),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}
