package dev.codexsoft.codexcine.ui.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.codexsoft.codexcine.ui.compose.theme.CodexTheme

/**
 * Laid over a backdrop (`backdrop_scrim`): dark on the left and at the bottom, where the text and the rows
 * are, light on the right, where the artwork shows, and a soft band at the top for the buttons and the logo.
 */
@Composable
fun BackdropScrim(modifier: Modifier = Modifier) {
    val colors = CodexTheme.colors
    Box(modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(listOf(colors.scrimStrong, colors.scrimSoft, Color.Transparent)),
            ),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, colors.scrimSoft, colors.night)),
            ),
        )
        Box(
            Modifier.fillMaxWidth().height(160.dp).background(
                Brush.verticalGradient(listOf(colors.scrimSoft, Color.Transparent)),
            ),
        )
    }
}
