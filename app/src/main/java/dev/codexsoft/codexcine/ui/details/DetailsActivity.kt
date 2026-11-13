package dev.codexsoft.codexcine.ui.details

import android.content.Context
import android.content.Intent
import androidx.fragment.app.Fragment
import dev.codexsoft.codexcine.core.Extras
import dev.codexsoft.codexcine.domain.model.MediaType
import dev.codexsoft.codexcine.ui.common.FragmentHostActivity
import dev.codexsoft.codexcine.ui.compose.ComposeFlags
import dev.codexsoft.codexcine.ui.compose.ComposeScreen

class DetailsActivity : FragmentHostActivity() {

    override fun createContentFragment(): Fragment =
        if (ComposeFlags.isEnabled(this, ComposeScreen.DETAILS)) ComposeDetailsFragment() else VideoDetailsFragment()

    companion object {
        fun newIntent(context: Context, mediaId: Int, mediaType: MediaType, title: String): Intent =
            Intent(context, DetailsActivity::class.java)
                .putExtra(Extras.MEDIA_ID, mediaId)
                .putExtra(Extras.MEDIA_TYPE, mediaType.value)
                .putExtra(Extras.TITLE, title)
    }
}
