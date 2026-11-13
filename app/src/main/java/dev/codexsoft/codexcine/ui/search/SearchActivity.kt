package dev.codexsoft.codexcine.ui.search

import androidx.fragment.app.Fragment
import dev.codexsoft.codexcine.ui.common.FragmentHostActivity
import dev.codexsoft.codexcine.ui.compose.ComposeFlags
import dev.codexsoft.codexcine.ui.compose.ComposeScreen

class SearchActivity : FragmentHostActivity() {
    override fun createContentFragment(): Fragment =
        if (ComposeFlags.isEnabled(this, ComposeScreen.SEARCH)) ComposeSearchFragment() else SearchFragment()
}
