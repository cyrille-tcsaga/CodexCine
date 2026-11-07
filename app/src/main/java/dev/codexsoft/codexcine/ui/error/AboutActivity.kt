package dev.codexsoft.codexcine.ui.error

import androidx.fragment.app.Fragment
import dev.codexsoft.codexcine.BuildConfig
import dev.codexsoft.codexcine.R
import dev.codexsoft.codexcine.ui.common.FragmentHostActivity

/** TMDB attribution required by their terms, shown with the same layout as an error screen. */
class AboutActivity : FragmentHostActivity() {

    override fun createContentFragment(): Fragment = ErrorFragment.newInstance(
        message = getString(R.string.about_message, BuildConfig.VERSION_NAME),
        retryable = false,
        title = getString(R.string.row_about),
        image = R.drawable.ic_info,
    )
}
