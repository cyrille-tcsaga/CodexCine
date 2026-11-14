package dev.codexsoft.codexcine.ui.season

import android.content.Context
import android.content.Intent
import androidx.fragment.app.Fragment
import dev.codexsoft.codexcine.core.Extras
import dev.codexsoft.codexcine.ui.common.FragmentHostActivity

class SeasonActivity : FragmentHostActivity() {

    override fun createContentFragment(): Fragment = ComposeSeasonFragment()

    companion object {
        fun newIntent(context: Context, showId: Int, seasonNumber: Int, showTitle: String): Intent =
            Intent(context, SeasonActivity::class.java)
                .putExtra(Extras.MEDIA_ID, showId)
                .putExtra(Extras.SEASON_NUMBER, seasonNumber)
                .putExtra(Extras.TITLE, showTitle)
    }
}
