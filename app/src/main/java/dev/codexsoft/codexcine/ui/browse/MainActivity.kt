package dev.codexsoft.codexcine.ui.browse

import android.os.Bundle
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.Fragment
import dev.codexsoft.codexcine.ui.common.FragmentHostActivity
import dev.codexsoft.codexcine.ui.update.UpdatePrompt

class MainActivity : FragmentHostActivity() {

    private var isHomeReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // The logo stays up until the first row is ready, so the home never appears empty, but never
        // longer than SPLASH_MAX_MS: a slow network then shows the home's own loading indicator.
        installSplashScreen().setKeepOnScreenCondition { !isHomeReady }
        super.onCreate(savedInstanceState)
        window.decorView.postDelayed(::onHomeReady, SPLASH_MAX_MS)
    }

    override fun createContentFragment(): Fragment = ComposeMainFragment()

    fun onHomeReady() {
        if (isHomeReady) return
        isHomeReady = true
        // Once the home is up, so that an update offer never sits on the splash screen.
        UpdatePrompt.offerIfAny(this)
    }

    private companion object {
        const val SPLASH_MAX_MS = 2_500L
    }
}
