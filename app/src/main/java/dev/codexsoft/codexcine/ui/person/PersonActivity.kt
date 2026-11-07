package dev.codexsoft.codexcine.ui.person

import android.content.Context
import android.content.Intent
import androidx.fragment.app.Fragment
import dev.codexsoft.codexcine.core.Extras
import dev.codexsoft.codexcine.ui.common.FragmentHostActivity

class PersonActivity : FragmentHostActivity() {

    override fun createContentFragment(): Fragment = PersonFragment()

    companion object {
        fun newIntent(context: Context, personId: Int, name: String): Intent =
            Intent(context, PersonActivity::class.java)
                .putExtra(Extras.MEDIA_ID, personId)
                .putExtra(Extras.TITLE, name)
    }
}
