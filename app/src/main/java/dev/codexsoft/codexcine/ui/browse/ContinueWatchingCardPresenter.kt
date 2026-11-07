package dev.codexsoft.codexcine.ui.browse

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import androidx.leanback.widget.ImageCardView
import dev.codexsoft.codexcine.R
import dev.codexsoft.codexcine.data.progress.WatchProgress
import dev.codexsoft.codexcine.ui.common.BaseCardPresenter
import dev.codexsoft.codexcine.ui.common.runtimeText
import kotlin.math.ceil

/** A wide still with how far the viewer got, drawn as a thin bar along the bottom of the image. */
class ContinueWatchingCardPresenter : BaseCardPresenter(widthDp = 264, heightDp = 148) {

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val progress = item as? WatchProgress ?: return
        val card = viewHolder.view as ImageCardView
        val context = card.context
        card.titleText = progress.title

        val minutesLeft = ceil((progress.durationSeconds - progress.positionSeconds) / 60).toInt()
        val timeLeft = context.runtimeText(minutesLeft)?.let { context.getString(R.string.card_time_left, it) }
        val episode = if (progress.episode > 0) {
            context.getString(R.string.playback_episode_subtitle, progress.season, progress.episode)
        } else {
            null
        }
        card.contentText = listOfNotNull(episode, timeLeft).joinToString(context.getString(R.string.separator))
        card.loadImage(progress.backdropUrl ?: progress.posterUrl)
        card.setImageOverlay(
            ProgressBarDrawable(
                fraction = progress.fraction,
                barColor = ContextCompat.getColor(context, R.color.selected_background),
                trackColor = ContextCompat.getColor(context, R.color.player_track),
                heightPx = BAR_HEIGHT_DP * context.resources.displayMetrics.density,
            ),
        )
    }

    private class ProgressBarDrawable(
        private val fraction: Float,
        barColor: Int,
        trackColor: Int,
        private val heightPx: Float,
    ) : Drawable() {
        private val barPaint = Paint().apply { color = barColor }
        private val trackPaint = Paint().apply { color = trackColor }

        override fun draw(canvas: Canvas) {
            val box = bounds
            val top = box.bottom - heightPx
            val end = box.left + box.width() * fraction
            canvas.drawRect(end, top, box.right.toFloat(), box.bottom.toFloat(), trackPaint)
            canvas.drawRect(box.left.toFloat(), top, end, box.bottom.toFloat(), barPaint)
        }

        override fun setAlpha(alpha: Int) = Unit

        override fun setColorFilter(colorFilter: ColorFilter?) = Unit

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    private companion object {
        const val BAR_HEIGHT_DP = 5
    }
}
