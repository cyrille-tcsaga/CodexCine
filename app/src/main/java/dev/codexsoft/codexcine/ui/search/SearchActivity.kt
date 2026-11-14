package dev.codexsoft.codexcine.ui.search

import androidx.fragment.app.Fragment
import dev.codexsoft.codexcine.ui.common.FragmentHostActivity

class SearchActivity : FragmentHostActivity() {
    override fun createContentFragment(): Fragment = ComposeSearchFragment()
}
