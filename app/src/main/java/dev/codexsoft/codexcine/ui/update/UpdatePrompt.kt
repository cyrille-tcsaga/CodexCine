package dev.codexsoft.codexcine.ui.update

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import dev.codexsoft.codexcine.R
import dev.codexsoft.codexcine.data.update.AvailableUpdate
import dev.codexsoft.codexcine.ui.common.appContainer
import kotlinx.coroutines.launch
import java.io.File

/**
 * Offers a newer release from GitHub, from the home screen. It checks at most every [CHECK_INTERVAL_MS],
 * and a version the viewer put off is not offered again.
 */
object UpdatePrompt {

    private const val PREFS = "updates"
    private const val LAST_CHECK = "last_check"
    private const val SKIPPED = "skipped_version"
    private const val CHECK_INTERVAL_MS = 12L * 60 * 60 * 1000

    fun offerIfAny(activity: FragmentActivity) {
        val repository = activity.appContainer.updateRepository
        if (!repository.isEnabled) return
        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(LAST_CHECK, 0) < CHECK_INTERVAL_MS) return
        activity.lifecycleScope.launch {
            val update = repository.check()
            prefs.edit { putLong(LAST_CHECK, now) }
            if (update == null) return@launch
            if (prefs.getString(SKIPPED, null) == update.version.toString()) return@launch
            if (!activity.isFinishing) show(activity, update)
        }
    }

    private fun show(activity: FragmentActivity, update: AvailableUpdate) {
        val message = buildString {
            append(activity.getString(R.string.update_message, update.version.toString()))
            if (update.notes.isNotEmpty()) append("\n\n").append(update.notes)
        }
        AlertDialog.Builder(activity, R.style.CodexCine_Dialog)
            .setTitle(R.string.update_title)
            .setMessage(message)
            .setPositiveButton(R.string.update_install) { _, _ -> install(activity, update) }
            .setNegativeButton(R.string.update_later) { _, _ ->
                activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit { putString(SKIPPED, update.version.toString()) }
            }
            .show()
    }

    private fun install(activity: FragmentActivity, update: AvailableUpdate) {
        val container = activity.appContainer
        if (!container.apkInstaller.canInstall()) {
            // A system setting, once: the app may install apps. The offer comes back on the next start.
            Toast.makeText(activity, R.string.update_allow_install, Toast.LENGTH_LONG).show()
            activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { remove(LAST_CHECK) }
            openInstallSetting(activity)
            return
        }
        Toast.makeText(activity, R.string.update_downloading, Toast.LENGTH_LONG).show()
        activity.lifecycleScope.launch {
            val apk = container.updateRepository.download(update, File(activity.cacheDir, "updates"))
            val handedOver = apk != null && container.apkInstaller.install(apk)
            if (!handedOver) Toast.makeText(activity, R.string.update_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun openInstallSetting(activity: FragmentActivity) {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${activity.packageName}".toUri())
        try {
            activity.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            // Some TV boxes lack that screen: the toast already says what to allow.
        }
    }
}
