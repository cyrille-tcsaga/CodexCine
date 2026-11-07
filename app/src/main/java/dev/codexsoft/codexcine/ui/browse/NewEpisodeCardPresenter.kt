package dev.codexsoft.codexcine.ui.browse

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.util.TypedValue
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.leanback.widget.ImageCardView
import dev.codexsoft.codexcine.R
import dev.codexsoft.codexcine.domain.model.NewEpisode
import dev.codexsoft.codexcine.ui.common.BaseCardPresenter

/** The episode's still, with an amber "Nouveau" tag in its corner, the show's name and the episode under it. */
class NewEpisodeCardPresenter : BaseCardPresenter(widthDp = 264, heightDp = 148) {

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val newEpisode = item as? NewEpisode ?: return
        val card = viewHolder.view as ImageCardView
        val context = card.context
        val episode = newEpisode.episode
        card.titleText = newEpisode.show.title
        card.contentText = listOfNotNull(
            context.getString(R.string.playback_episode_subtitle, episode.seasonNumber, episode.episodeNumber),
            // Without a French title TMDB sends "Épisode 5", which only repeats the number.
            episode.name.takeIf { it.isNotBlank() && !GENERIC_NAME.matches(it.trim()) },
        ).joinToString(context.getString(R.string.separator))
        card.loadImage(episode.stillUrl ?: newEpisode.show.backdropUrl ?: newEpisode.show.posterUrl)
        card.setImageOverlay(NewTagDrawable(context))
    }

    private companion object {
        val GENERIC_NAME = Regex("""(?i)[ée]pisode\s*\d+""")
    }

    /** Drawn rather than laid out: ImageCardView has no room for an extra view over its image. */
    private class NewTagDrawable(context: Context) : Drawable() {
        private val density = context.resources.displayMetrics.density
        private val label = context.getString(R.string.card_new_tag)
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.brand_amber)
        }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.brand_night)
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, TEXT_SP, context.resources.displayMetrics)
            typeface = Typeface.create(ResourcesCompat.getFont(context, R.font.brand_display), BOLD, false)
            letterSpacing = LETTER_SPACING
        }
        private val box = RectF()

        override fun draw(canvas: Canvas) {
            val margin = MARGIN_DP * density
            val paddingH = PADDING_H_DP * density
            val paddingV = PADDING_V_DP * density
            val metrics = textPaint.fontMetrics
            val textHeight = metrics.descent - metrics.ascent
            box.set(
                bounds.left + margin,
                bounds.top + margin,
                bounds.left + margin + textPaint.measureText(label) + 2 * paddingH,
                bounds.top + margin + textHeight + 2 * paddingV,
            )
            val radius = box.height() / 2
            canvas.drawRoundRect(box, radius, radius, fillPaint)
            canvas.drawText(label, box.left + paddingH, box.top + paddingV - metrics.ascent, textPaint)
        }

        override fun setAlpha(alpha: Int) = Unit

        override fun setColorFilter(colorFilter: ColorFilter?) = Unit

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

        private companion object {
            const val TEXT_SP = 13f
            const val BOLD = 700
            const val LETTER_SPACING = 0.06f
            const val MARGIN_DP = 10
            const val PADDING_H_DP = 10
            const val PADDING_V_DP = 3
        }
    }
}
