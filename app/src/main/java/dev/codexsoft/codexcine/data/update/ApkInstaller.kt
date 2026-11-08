package dev.codexsoft.codexcine.data.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.content.IntentCompat
import dev.codexsoft.codexcine.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Hands a downloaded APK to the system installer. Android asks the viewer to confirm, and refuses an APK
 * not signed with the same key as the installed app: a tampered download cannot replace it.
 */
class ApkInstaller(context: Context) {

    private val appContext = context.applicationContext

    /** False until the viewer allows this app to install apps (a system setting, asked for once). */
    fun canInstall(): Boolean = appContext.packageManager.canRequestPackageInstalls()

    suspend fun install(apk: File): Boolean = withContext(Dispatchers.IO) {
        val installer = appContext.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(appContext.packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
            }
        }
        var sessionId = -1
        try {
            sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                apk.inputStream().use { input ->
                    session.openWrite(SESSION_ENTRY, 0, apk.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }
                val callback = PendingIntent.getBroadcast(
                    appContext,
                    sessionId,
                    Intent(appContext, InstallResultReceiver::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
                session.commit(callback.intentSender)
            }
            true
        } catch (e: IOException) {
            Log.w(TAG, "Could not hand the update to the installer", e)
            if (sessionId >= 0) installer.abandonSession(sessionId)
            false
        } catch (e: SecurityException) {
            Log.w(TAG, "Installer refused the update", e)
            if (sessionId >= 0) installer.abandonSession(sessionId)
            false
        }
    }

    private companion object {
        const val TAG = "ApkInstaller"
        const val SESSION_ENTRY = "codexcine.apk"
    }
}

/** The installer's answer. Its confirmation screen is shown from here; a failure is said in a toast. */
class InstallResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                    ?: return
                context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            PackageInstaller.STATUS_SUCCESS -> Unit
            else -> {
                Log.w("ApkInstaller", "Update failed: ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)}")
                Toast.makeText(context, R.string.update_failed, Toast.LENGTH_LONG).show()
            }
        }
    }
}
