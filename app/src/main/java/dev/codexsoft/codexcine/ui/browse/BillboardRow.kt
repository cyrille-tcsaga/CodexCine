package dev.codexsoft.codexcine.ui.browse

import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowPresenter
import dev.codexsoft.codexcine.R
import dev.codexsoft.codexcine.core.Formatters
import dev.codexsoft.codexcine.domain.model.MediaItem
import dev.codexsoft.codexcine.domain.model.MediaType

/** The titles featured at the top of the home page, in turn. */
class BillboardRow(val items: List<MediaItem>, val listener: Listener) : Row() {
    interface Listener {
        fun onPlay(item: MediaItem)
        fun onMoreInfo(item: MediaItem)

        /** A new title is featured; its artwork should become the backdrop. */
        fun onFeatured(item: MediaItem)
    }
}

/**
 * The home "billboard": a featured title's name, year, rating, overview and two rounded buttons, over
 * its artwork (the screen backdrop). It moves on to the next title every few seconds, except while
 * the viewer is on its buttons, so the button they are about to press never changes title.
 */
class BillboardRowPresenter : RowPresenter() {

    init {
        headerPresenter = null
        selectEffectEnabled = false
    }

    override fun isUsingDefaultSelectEffect() = false

    override fun createRowViewHolder(parent: ViewGroup): RowPresenter.ViewHolder =
        BillboardViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.view_home_hero, parent, false))

    override fun onBindRowViewHolder(vh: RowPresenter.ViewHolder, item: Any) {
        super.onBindRowViewHolder(vh, item)
        (vh as BillboardViewHolder).bind(item as BillboardRow)
    }

    override fun onUnbindRowViewHolder(vh: RowPresenter.ViewHolder) {
        (vh as BillboardViewHolder).stop()
        super.onUnbindRowViewHolder(vh)
    }

    override fun onRowViewAttachedToWindow(vh: RowPresenter.ViewHolder) {
        super.onRowViewAttachedToWindow(vh)
        (vh as BillboardViewHolder).start()
    }

    override fun onRowViewDetachedFromWindow(vh: RowPresenter.ViewHolder) {
        (vh as BillboardViewHolder).stop()
        super.onRowViewDetachedFromWindow(vh)
    }

    private class BillboardViewHolder(view: View) : RowPresenter.ViewHolder(view) {
        private val content: View = view.findViewById(R.id.hero_content)
        private val label: TextView = view.findViewById(R.id.hero_label)
        private val title: TextView = view.findViewById(R.id.hero_title)
        private val meta: TextView = view.findViewById(R.id.hero_meta)
        private val overview: TextView = view.findViewById(R.id.hero_overview)
        private val play: Button = view.findViewById(R.id.hero_play)
        private val moreInfo: Button = view.findViewById(R.id.hero_info)
        private val dots: LinearLayout = view.findViewById(R.id.hero_dots)

        private val handler = Handler(Looper.getMainLooper())
        private val animate = Settings.Global.getFloat(
            view.context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) != 0f
        private var row: BillboardRow? = null
        private var index = 0

        private val advance = object : Runnable {
            override fun run() {
                val current = row ?: return
                if (!view.hasFocus() && current.items.size > 1) show((index + 1) % current.items.size)
                handler.postDelayed(this, ROTATION_MS)
            }
        }

        fun bind(newRow: BillboardRow) {
            row = newRow
            play.setOnClickListener { currentItem()?.let(newRow.listener::onPlay) }
            moreInfo.setOnClickListener { currentItem()?.let(newRow.listener::onMoreInfo) }
            buildDots(newRow.items.size)
            show(index.coerceAtMost(newRow.items.lastIndex.coerceAtLeast(0)), fade = false)
        }

        fun start() {
            handler.removeCallbacks(advance)
            handler.postDelayed(advance, ROTATION_MS)
        }

        fun stop() {
            handler.removeCallbacks(advance)
            content.animate().cancel()
            content.alpha = 1f
        }

        private fun currentItem() = row?.items?.getOrNull(index)

        private fun show(newIndex: Int, fade: Boolean = animate) {
            val current = row ?: return
            val item = current.items.getOrNull(newIndex) ?: return
            index = newIndex
            fill(item)
            // Fades in only: an interrupted fade-in still ends visible, which a fade-out could not promise.
            content.animate().cancel()
            if (fade) {
                content.alpha = 0f
                content.animate().alpha(1f).setDuration(FADE_MS).start()
            } else {
                content.alpha = 1f
            }
            updateDots()
            current.listener.onFeatured(item)
        }

        private fun fill(item: MediaItem) {
            val context = view.context
            label.setText(if (item.mediaType == MediaType.TV) R.string.hero_label_tv else R.string.hero_label_movie)
            title.text = item.title
            val rating = item.voteAverage.takeIf { it > 0 }
                ?.let { context.getString(R.string.rating_stars, Formatters.rating(it)) }
            meta.text = listOfNotNull(item.releaseYear, rating).joinToString(context.getString(R.string.separator))
            overview.text = item.overview
            overview.isVisible = !item.overview.isNullOrBlank()
            play.contentDescription = "${context.getString(R.string.action_play)} ${item.title}"
            moreInfo.contentDescription = "${context.getString(R.string.hero_more_info)} ${item.title}"
        }

        private fun buildDots(count: Int) {
            dots.removeAllViews()
            if (count < 2) return
            val density = view.resources.displayMetrics.density
            repeat(count) {
                dots.addView(
                    View(view.context).apply { setBackgroundResource(R.drawable.bg_dot) },
                    LinearLayout.LayoutParams((DOT_WIDTH_DP * density).toInt(), (DOT_HEIGHT_DP * density).toInt())
                        .apply { marginEnd = (DOT_GAP_DP * density).toInt() },
                )
            }
        }

        private fun updateDots() {
            val density = view.resources.displayMetrics.density
            for (i in 0 until dots.childCount) {
                val dot = dots.getChildAt(i)
                val isCurrent = i == index
                dot.alpha = if (isCurrent) 1f else INACTIVE_DOT_ALPHA
                dot.background.setTint(view.context.getColor(if (isCurrent) R.color.brand_amber else R.color.brand_ivory))
                dot.layoutParams = dot.layoutParams.apply {
                    width = ((if (isCurrent) ACTIVE_DOT_WIDTH_DP else DOT_WIDTH_DP) * density).toInt()
                }
            }
        }

        private companion object {
            const val ROTATION_MS = 8_000L
            const val FADE_MS = 400L
            const val DOT_WIDTH_DP = 14
            const val ACTIVE_DOT_WIDTH_DP = 32
            const val DOT_HEIGHT_DP = 5
            const val DOT_GAP_DP = 8
            const val INACTIVE_DOT_ALPHA = 0.4f
        }
    }
}
