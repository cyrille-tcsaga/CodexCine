package dev.codexsoft.codexcine.ui.common

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.commit

/**
 * Hosts one screen fragment, which an error screen can replace.
 *
 * Retrying replaces the error with a new content fragment. The old fragment's ViewModel was cleared
 * with it, so the new one starts loading from scratch and no screen needs its own retry logic.
 */
abstract class FragmentHostActivity : FragmentActivity() {

    protected abstract fun createContentFragment(): Fragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            showContent()
        }
    }

    fun showContent() {
        supportFragmentManager.commit {
            replace(android.R.id.content, createContentFragment())
        }
    }

    fun showError(fragment: Fragment) {
        supportFragmentManager.commit {
            replace(android.R.id.content, fragment)
        }
    }
}
