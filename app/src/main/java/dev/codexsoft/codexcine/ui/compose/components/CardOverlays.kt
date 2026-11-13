package dev.codexsoft.codexcine.ui.compose.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.codexsoft.codexcine.R
import dev.codexsoft.codexcine.ui.compose.theme.CodexTheme
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ColorFilter

/** How far the viewer got: a thin bar along the bottom of the image, on a pale track. */
@Composable
fun BoxScope.ProgressBarOverlay(fraction: Float) {
    val track = colorResource(R.color.player_track)
    val bar = colorResource(R.color.selected_background)
    Canvas(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(5.dp)) {
        val end = size.width * fraction.coerceIn(0f, 1f)
        drawRect(track, Offset(end, 0f), Size(size.width - end, size.height))
        drawRect(bar, Offset.Zero, Size(end, size.height))
    }
}

/** An amber "NOUVEAU" pill in the image's top-left corner. */
@Composable
fun BoxScope.NewTagOverlay() {
    Text(
        text = stringResource(R.string.card_new_tag),
        style = MaterialTheme.typography.labelMedium,
        color = CodexTheme.colors.night,
        modifier = Modifier
            .align(Alignment.TopStart)
            .padding(10.dp)
            .clip(RoundedCornerShape(50))
            .background(CodexTheme.colors.amber)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

/** A watched episode: the image dimmed, with an ivory disc and a check in the top-right corner. */
@Composable
fun BoxScope.WatchedOverlay() {
    Box(Modifier.fillMaxSize().background(CodexTheme.colors.scrimSoft))
    Box(
        Modifier
            .align(Alignment.TopEnd)
            .padding(10.dp)
            .size(32.dp)
            .clip(CircleShape)
            .background(CodexTheme.colors.ivory),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_check),
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            colorFilter = ColorFilter.tint(CodexTheme.colors.night),
        )
    }
}

