package dev.codexsoft.codexcine.ui.browse

import android.os.Bundle
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.Fragment
import dev.codexsoft.codexcine.ui.common.FragmentHostActivity
import dev.codexsoft.codexcine.ui.update.UpdatePrompt

class MainActivity : FragmentHostActivity() {

    private var isSplashDone = false
    private var isIntroFinished = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // The system splash stays up until the first screen (the intro animation) is drawn, but never longer
        // than SPLASH_MAX_MS.
        installSplashScreen().setKeepOnScreenCondition { !isSplashDone }
        super.onCreate(savedInstanceState)
        window.decorView.postDelayed(::onFirstScreenShown, SPLASH_MAX_MS)
    }

    override fun createContentFragment(): Fragment = ComposeMainFragment()

    /** The first screen of the app is on display: the system splash can go. */
    fun onFirstScreenShown() {
        isSplashDone = true
    }

    /** The opening animation is over (or skipped): the home is in view, so an update may be offered. */
    fun onIntroFinished() {
        if (isIntroFinished) return
        isIntroFinished = true
        onFirstScreenShown()
        UpdatePrompt.offerIfAny(this)
    }

    private companion object {
        const val SPLASH_MAX_MS = 2_500L
    }
}
