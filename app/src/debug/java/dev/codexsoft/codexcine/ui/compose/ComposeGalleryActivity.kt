package dev.codexsoft.codexcine.ui.compose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.codexsoft.codexcine.ui.compose.theme.CodexCineTheme
import dev.codexsoft.codexcine.ui.compose.theme.CodexTheme

/** Debug only. Shows the theme and, as stories add them, the shared components. */
class ComposeGalleryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CodexCineTheme { Gallery() } }
    }
}

@Composable
private fun Gallery() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CodexTheme.colors.night)
            .padding(horizontal = CodexTheme.dimens.screenPaddingHorizontal, vertical = CodexTheme.dimens.screenPaddingVertical),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("FILM", style = MaterialTheme.typography.labelMedium, color = CodexTheme.colors.amber)
        Text("Titre du film", style = MaterialTheme.typography.displayLarge, color = CodexTheme.colors.ivory)
        Text("1999 · 2 h 19 min · ★ 8.4", style = MaterialTheme.typography.bodyLarge)
        Text("Drame · Thriller", style = MaterialTheme.typography.bodyLarge, color = CodexTheme.colors.mist)
        Text("Une rangée", style = MaterialTheme.typography.titleLarge)
        Text("Titre de carte", style = MaterialTheme.typography.titleSmall)
        Text("Détail de carte", style = MaterialTheme.typography.bodySmall, color = CodexTheme.colors.mist)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(
                CodexTheme.colors.night, CodexTheme.colors.ink, CodexTheme.colors.amber,
                CodexTheme.colors.ivory, CodexTheme.colors.mist,
            ).forEach { Box(Modifier.size(48.dp).background(it)) }
        }
    }
}
