package dev.codexsoft.codexcine.ui.launch

import android.app.Activity
import android.app.TaskStackBuilder
import android.content.Intent
import android.os.Bundle
import dev.codexsoft.codexcine.core.PlayLink
import dev.codexsoft.codexcine.ui.browse.MainActivity
import dev.codexsoft.codexcine.ui.playback.PlaybackActivity

/**
 * The only screen other apps can open, for the system home screen's "Play next" row. It checks the link
 * (see [PlayLink.parse]) and hands over to the player, with the app's home behind it for Back. A link
 * it does not accept opens the home.
 */
class PlayLinkActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val home = Intent(this, MainActivity::class.java)
        val link = PlayLink.parse(intent?.dataString)
        if (link == null) {
            startActivity(home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        } else {
            TaskStackBuilder.create(this)
                .addNextIntent(home)
                .addNextIntent(
                    PlaybackActivity.newIntent(
                        this,
                        mediaId = link.mediaId,
                        mediaType = link.mediaType,
                        title = link.title,
                        seasonNumber = link.season,
                        episodeNumber = link.episode,
                        autoResume = true,
                    ),
                )
                .startActivities()
        }
        finish()
    }
}
