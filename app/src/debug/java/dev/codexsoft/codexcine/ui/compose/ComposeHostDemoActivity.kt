package dev.codexsoft.codexcine.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import dev.codexsoft.codexcine.core.ErrorKind
import dev.codexsoft.codexcine.ui.common.FragmentHostActivity
import dev.codexsoft.codexcine.ui.common.showErrorScreen
import dev.codexsoft.codexcine.ui.compose.theme.CodexTheme

/** Debug only. Proves that a Compose screen lives in `FragmentHostActivity`: the error screen replaces it and Retry brings it back. */
class ComposeHostDemoActivity : FragmentHostActivity() {
    override fun createContentFragment(): Fragment = Demo()

    class Demo : ComposeScreenFragment() {
        @OptIn(ExperimentalTvMaterial3Api::class)
        @Composable
        override fun ScreenContent() {
            Column(
                Modifier.fillMaxSize().background(CodexTheme.colors.night).padding(48.dp),
            ) {
                Text("Compose screen in FragmentHostActivity")
                Button(onClick = { showErrorScreen(ErrorKind.NO_CONNECTION) }) { Text("Show error") }
            }
        }
    }
}
