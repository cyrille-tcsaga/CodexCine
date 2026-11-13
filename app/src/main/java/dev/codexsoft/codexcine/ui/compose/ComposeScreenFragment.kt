package dev.codexsoft.codexcine.ui.compose

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import dev.codexsoft.codexcine.ui.compose.theme.CodexCineTheme

/**
 * Hosts a Compose screen in a fragment, so that it lives inside `FragmentHostActivity` like the Leanback
 * screens: the error screen can replace it, and retrying recreates it. The composition ends with the
 * fragment's view, not with the window.
 */
abstract class ComposeScreenFragment : Fragment() {

    @Composable
    protected abstract fun ScreenContent()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent { CodexCineTheme { ScreenContent() } }
        }
}
