package dev.codexsoft.codexcine.ui.compose

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Debug only. Switches the Compose flags without rebuilding:
 * `adb shell am broadcast -n dev.codexsoft.codexcine/.ui.compose.ComposeFlagsReceiver --es screens details,error`
 * (`all`, `none`, or `--es screens reset` to go back to the build default).
 */
class ComposeFlagsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val screens = intent.getStringExtra("screens") ?: return
        ComposeFlags.override(context, if (screens == "reset") null else screens)
        resultData = "compose flags: ${screens}"
    }
}
