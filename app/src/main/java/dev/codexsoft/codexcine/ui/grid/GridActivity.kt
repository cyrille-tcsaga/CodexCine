package dev.codexsoft.codexcine.ui.grid

import android.content.Context
import android.content.Intent
import androidx.fragment.app.Fragment
import dev.codexsoft.codexcine.core.Extras
import dev.codexsoft.codexcine.domain.model.CategoryType
import dev.codexsoft.codexcine.ui.common.FragmentHostActivity
import dev.codexsoft.codexcine.ui.compose.ComposeFlags
import dev.codexsoft.codexcine.ui.compose.ComposeScreen

class GridActivity : FragmentHostActivity() {

    override fun createContentFragment(): Fragment =
        if (ComposeFlags.isEnabled(this, ComposeScreen.GRID)) ComposeGridFragment() else MediaGridFragment()

    companion object {
        fun newIntent(context: Context, category: CategoryType, title: String): Intent =
            Intent(context, GridActivity::class.java)
                .putExtra(Extras.CATEGORY, category.name)
                .putExtra(Extras.TITLE, title)
    }
}
