package dev.codexsoft.codexcine.ui.compose

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.codexsoft.codexcine.data.progress.WatchProgress
import dev.codexsoft.codexcine.CodexCineApp
import kotlinx.coroutines.launch

/**
 * Debug only. Adds a "Continuer à regarder" entry, to test the row and its menu without playing anything:
 * `adb shell am broadcast -n dev.codexsoft.codexcine/.ui.compose.SeedProgressReceiver --ei id 550 --es title "Fight Club"`
 * (`--es type tv --ei season 1 --ei episode 2` for an episode, `--ei minutes 40` for how far it got).
 */
class SeedProgressReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as CodexCineApp).container
        val type = intent.getStringExtra("type") ?: "movie"
        val entry = WatchProgress(
            mediaType = type,
            tmdbId = intent.getIntExtra("id", 550),
            season = intent.getIntExtra("season", 0),
            episode = intent.getIntExtra("episode", 0),
            positionSeconds = intent.getIntExtra("minutes", 40) * 60.0,
            durationSeconds = 120 * 60.0,
            updatedAtMs = System.currentTimeMillis(),
            title = intent.getStringExtra("title") ?: "Titre de test",
            posterUrl = null,
            backdropUrl = intent.getStringExtra("backdrop"),
        )
        val pending = goAsync()
        container.persistenceScope.launch {
            try {
                container.watchProgressRepository.save(entry)
            } finally {
                pending.finish()
            }
        }
    }
}
