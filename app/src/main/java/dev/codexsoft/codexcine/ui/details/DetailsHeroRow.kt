package dev.codexsoft.codexcine.ui.details

import android.content.Context
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isNotEmpty
import androidx.core.view.isVisible
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowPresenter
import dev.codexsoft.codexcine.R
import dev.codexsoft.codexcine.core.Formatters
import dev.codexsoft.codexcine.domain.model.MediaDetails
import dev.codexsoft.codexcine.domain.model.MediaType
import dev.codexsoft.codexcine.ui.common.runtimeText
import kotlin.math.roundToInt

/**
 * The top of the details screen. It exists before the network answers, with the title from the
 * intent, so that Play works at once. Later changes are drawn in place by the bound view, which keeps
 * the focused button focused.
 */
class DetailsHeroRow(
    val initialTitle: String,
    val mediaType: MediaType?,
    val listener: Listener,
) : Row() {

    interface Listener {
        fun onPlay()
        fun onTrailer()
        fun onToggleMyList()
    }

    var details: MediaDetails? = null
        set(value) {
            field = value
            observer?.invoke()
        }
    var isInMyList: Boolean? = null
        set(value) {
            field = value
            observer?.invoke()
        }
    var availability: PlayAvailability = PlayAvailability.Unknown
        set(value) {
            field = value
            observer?.invoke()
        }

    internal var observer: (() -> Unit)? = null
}

/** Draws a [DetailsHeroRow]: label, title, facts, genres, overview and rounded buttons over the artwork. */
class DetailsHeroRowPresenter : RowPresenter() {

    init {
        headerPresenter = null
        selectEffectEnabled = false
    }

    override fun isUsingDefaultSelectEffect() = false

    override fun createRowViewHolder(parent: ViewGroup): RowPresenter.ViewHolder =
        HeroViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.view_details_hero, parent, false))

    override fun onBindRowViewHolder(vh: RowPresenter.ViewHolder, item: Any) {
        super.onBindRowViewHolder(vh, item)
        (vh as HeroViewHolder).bind(item as DetailsHeroRow)
    }

    override fun onUnbindRowViewHolder(vh: RowPresenter.ViewHolder) {
        (vh as HeroViewHolder).unbind()
        super.onUnbindRowViewHolder(vh)
    }

    private class HeroViewHolder(view: View) : RowPresenter.ViewHolder(view) {
        private val label: TextView = view.findViewById(R.id.details_label)
        private val title: TextView = view.findViewById(R.id.details_title)
        private val meta: LinearLayout = view.findViewById(R.id.details_meta)
        private val genres: TextView = view.findViewById(R.id.details_genres)
        private val overview: TextView = view.findViewById(R.id.details_overview)
        private val play: Button = view.findViewById(R.id.details_play)
        private val trailer: Button = view.findViewById(R.id.details_trailer)
        private val myList: Button = view.findViewById(R.id.details_my_list)

        private var row: DetailsHeroRow? = null

        fun bind(newRow: DetailsHeroRow) {
            row?.observer = null
            row = newRow
            play.setOnClickListener { newRow.listener.onPlay() }
            trailer.setOnClickListener { newRow.listener.onTrailer() }
            myList.setOnClickListener { newRow.listener.onToggleMyList() }
            newRow.observer = ::fill
            fill()
        }

        fun unbind() {
            row?.observer = null
            row = null
        }

        private fun fill() {
            val current = row ?: return
            val context = view.context
            val details = current.details
            val type = details?.mediaType ?: current.mediaType

            label.isVisible = type == MediaType.MOVIE || type == MediaType.TV
            label.setText(if (type == MediaType.TV) R.string.details_label_tv else R.string.details_label_movie)
            title.text = details?.title ?: current.initialTitle
            fillMeta(context, details, current.availability)
            genres.text = details?.genres?.joinToString(context.getString(R.string.separator))
            genres.isVisible = !details?.genres.isNullOrEmpty()
            overview.text = details?.let { it.overview ?: context.getString(R.string.details_no_overview) }
            overview.isVisible = details != null

            play.contentDescription = "${context.getString(R.string.action_play)} ${title.text}"
            trailer.isVisible = details?.trailerKey != null

            val inList = current.isInMyList
            myList.isVisible = inList != null
            myList.setCompoundDrawablesRelativeWithIntrinsicBounds(
                if (inList == true) R.drawable.ic_check else R.drawable.ic_add, 0, 0, 0,
            )
            myList.contentDescription = context.getString(
                if (inList == true) R.string.action_remove_my_list else R.string.action_add_my_list,
            )
        }

        /** Year · length [age rating] ★ rating [VF] [VOSTFR]: plain facts, then outlined tags. */
        private fun fillMeta(context: Context, details: MediaDetails?, availability: PlayAvailability) {
            meta.removeAllViews()
            if (details != null) {
                val length = when (details.mediaType) {
                    MediaType.TV -> details.seasonCount?.takeIf { it > 0 }?.let {
                        context.resources.getQuantityString(R.plurals.season_count, it, it)
                    }
                    else -> context.runtimeText(details.runtimeMinutes)
                }
                val facts = listOfNotNull(details.releaseYear, length).joinToString(context.getString(R.string.separator))
                if (facts.isNotEmpty()) meta.addView(fact(context, facts))
                details.certification?.takeIf { it.isNotBlank() }?.let { meta.addView(badge(context, it)) }
                details.voteAverage.takeIf { it > 0 }?.let { meta.addView(fact(context, rating(context, it))) }
            }
            when (availability) {
                PlayAvailability.Unknown -> Unit
                PlayAvailability.Unavailable ->
                    meta.addView(badge(context, context.getString(R.string.action_play_unavailable), warn = true))
                is PlayAvailability.Available ->
                    availability.languages.forEach { meta.addView(badge(context, it.uppercase())) }
            }
            meta.isVisible = meta.isNotEmpty()
        }

        private fun rating(context: Context, value: Double): CharSequence {
            val text = context.getString(R.string.rating_stars, Formatters.rating(value))
            return SpannableString(text).apply {
                val star = text.indexOf('★')
                if (star >= 0) {
                    setSpan(
                        ForegroundColorSpan(context.getColor(R.color.brand_amber)),
                        star, star + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                }
            }
        }

        private fun fact(context: Context, text: CharSequence) = TextView(context).apply {
            this.text = text
            setTextColor(context.getColor(R.color.brand_ivory))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, META_TEXT_SP)
            layoutParams = spacedParams(context)
        }

        private fun badge(context: Context, text: String, warn: Boolean = false) = TextView(context).apply {
            this.text = text
            setBackgroundResource(R.drawable.bg_badge)
            setTextColor(context.getColor(if (warn) R.color.brand_amber else R.color.brand_ivory))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, BADGE_TEXT_SP)
            val horizontal = context.dp(BADGE_PADDING_H_DP)
            val vertical = context.dp(BADGE_PADDING_V_DP)
            setPadding(horizontal, vertical, horizontal, vertical)
            layoutParams = spacedParams(context)
        }

        private fun spacedParams(context: Context) = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { marginEnd = context.dp(META_GAP_DP) }

        private fun Context.dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()

        private companion object {
            const val META_TEXT_SP = 18f
            const val BADGE_TEXT_SP = 15f
            const val BADGE_PADDING_H_DP = 8
            const val BADGE_PADDING_V_DP = 2
            const val META_GAP_DP = 14
        }
    }
}
