package dev.codexsoft.codexcine.ui.launch

import android.app.Activity
import android.app.TaskStackBuilder
import android.content.Intent
import android.os.Bundle
import dev.codexsoft.codexcine.core.AppLink
import dev.codexsoft.codexcine.core.DetailsLink
import dev.codexsoft.codexcine.core.PlayLink
import dev.codexsoft.codexcine.ui.browse.MainActivity
import dev.codexsoft.codexcine.ui.details.DetailsActivity
import dev.codexsoft.codexcine.ui.playback.PlaybackActivity

/**
 * The only screen other apps can open with a link: the home screen's "Play next" row, and the system
 * search. It checks the link (see [AppLink]) and hands over to the player or a title's page, with the
 * app's home behind for Back. A link it does not accept opens the home.
 */
class PlayLinkActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val home = Intent(this, MainActivity::class.java)
        val target = when (val link = AppLink.parse(intent?.dataString)) {
            is PlayLink -> PlaybackActivity.newIntent(
                this,
                mediaId = link.mediaId,
                mediaType = link.mediaType,
                title = link.title,
                seasonNumber = link.season,
                episodeNumber = link.episode,
                autoResume = true,
            )
            is DetailsLink -> DetailsActivity.newIntent(this, link.mediaId, link.mediaType, link.title)
            null -> null
        }
        if (target == null) {
            startActivity(home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        } else {
            TaskStackBuilder.create(this).addNextIntent(home).addNextIntent(target).startActivities()
        }
        finish()
    }
}
