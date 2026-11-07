package dev.codexsoft.codexcine.ui.error

import android.annotation.SuppressLint
import android.view.KeyEvent
import androidx.fragment.app.Fragment
import dev.codexsoft.codexcine.BuildConfig
import dev.codexsoft.codexcine.R
import dev.codexsoft.codexcine.core.Extras
import dev.codexsoft.codexcine.domain.model.MediaType
import dev.codexsoft.codexcine.ui.common.FragmentHostActivity
import dev.codexsoft.codexcine.ui.playback.PlaybackActivity

/**
 * TMDB attribution required by their terms, shown with the same layout as an error screen.
 *
 * In debug builds it also hides the player's test harness: quick presses on OK open a local page
 * instead of pressing "Retour", which a single press still does, after a short delay.
 */
class AboutActivity : FragmentHostActivity() {

    private var okPresses = 0
    private val settleOkPresses = Runnable { onOkPressesSettled() }

    override fun createContentFragment(): Fragment = ErrorFragment.newInstance(
        message = getString(R.string.about_message, BuildConfig.VERSION_NAME),
        retryable = false,
        title = getString(R.string.row_about),
        image = R.drawable.ic_logo_mark,
    )

    // Public Activity API. Lint only objects because ComponentActivity redeclares it with a restricted annotation.
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val isOk = event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || event.keyCode == KeyEvent.KEYCODE_ENTER
        if (!BuildConfig.DEBUG || !isOk) return super.dispatchKeyEvent(event)
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            okPresses++
            window.decorView.removeCallbacks(settleOkPresses)
            window.decorView.postDelayed(settleOkPresses, OK_PRESS_WINDOW_MS)
        }
        return true
    }

    private fun onOkPressesSettled() {
        val presses = okPresses
        okPresses = 0
        when (presses) {
            HARNESS_PRESSES -> openHarness(OUTER_PAGE, forceBasicMode = false)
            HARNESS_BROKEN_PRESSES -> openHarness(BROKEN_PAGE, forceBasicMode = false)
            HARNESS_BASIC_PRESSES -> openHarness(OUTER_PAGE, forceBasicMode = true)
            else -> finish()
        }
    }

    private fun openHarness(page: String, forceBasicMode: Boolean) {
        startActivity(
            PlaybackActivity.newIntent(this, HARNESS_MEDIA_ID, MediaType.MOVIE, HARNESS_TITLE)
                .putExtra(Extras.DEBUG_EMBED_URL, HARNESS_BASE_URL + page)
                .putExtra(Extras.DEBUG_FORCE_BASIC_MODE, forceBasicMode),
        )
    }

    private companion object {
        const val OK_PRESS_WINDOW_MS = 700L
        const val HARNESS_PRESSES = 5
        const val HARNESS_BROKEN_PRESSES = 7
        const val HARNESS_BASIC_PRESSES = 9
        const val HARNESS_MEDIA_ID = -1
        const val HARNESS_TITLE = "Test local"
        const val HARNESS_BASE_URL = "https://appassets.androidplatform.net/assets/test/"
        const val OUTER_PAGE = "outer.html"
        const val BROKEN_PAGE = "outer_broken.html"
    }
}
