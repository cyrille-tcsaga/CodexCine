package dev.codexsoft.codexcine.data.watchnext

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import dev.codexsoft.codexcine.CodexCineApp

/** Told by the system when the viewer removes one of our cards from the "Play next" row. */
class WatchNextRemovedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TvContract.ACTION_WATCH_NEXT_PROGRAM_BROWSABLE_DISABLED) return
        val programId = intent.getLongExtra(TvContract.EXTRA_WATCH_NEXT_PROGRAM_ID, -1L)
        if (programId < 0) return
        val app = context.applicationContext as? CodexCineApp ?: return
        app.container.watchNextPublisher.onRemovedByViewer(programId)
    }
}
