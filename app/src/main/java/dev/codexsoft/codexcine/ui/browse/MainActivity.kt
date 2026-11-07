package dev.codexsoft.codexcine.ui.browse

import androidx.fragment.app.Fragment
import dev.codexsoft.codexcine.ui.common.FragmentHostActivity

class MainActivity : FragmentHostActivity() {
    override fun createContentFragment(): Fragment = MainFragment()
}
