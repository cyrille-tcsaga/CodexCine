package dev.codexsoft.codexcine.ui.grid

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.leanback.widget.TitleViewAdapter
import dev.codexsoft.codexcine.R

/**
 * The grid's title bar. To Leanback, the filters button is the search affordance: it then shows it, and
 * moves the focus to it when the viewer goes up from the first row of the grid.
 */
class FilterTitleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs), TitleViewAdapter.Provider {

    private val filters: Button by lazy { findViewById(R.id.grid_filters) }
    private val summary: TextView by lazy { findViewById(R.id.grid_filters_summary) }
    private val titleLabel: TextView by lazy { findViewById(R.id.grid_title) }
    private var hasFilters = false

    private val adapter = object : TitleViewAdapter() {
        override fun getSearchAffordanceView(): View = filters

        override fun setTitle(titleText: CharSequence?) {
            titleLabel.text = titleText
        }

        override fun setBadgeDrawable(drawable: Drawable?) = Unit

        override fun setOnSearchClickedListener(listener: OnClickListener?) {
            hasFilters = listener != null
            filters.setOnClickListener(listener)
            filters.isVisible = hasFilters
        }

        override fun updateComponentsVisibility(flags: Int) {
            filters.isVisible = hasFilters && flags and SEARCH_VIEW_VISIBLE != 0
        }
    }

    override fun getTitleViewAdapter(): TitleViewAdapter = adapter

    /** "Filtres" alone, or "Filtres (2)" with what they are next to it. */
    fun showFilters(activeCount: Int, description: CharSequence?) {
        filters.text = if (activeCount > 0) {
            context.getString(R.string.grid_filters_count, activeCount)
        } else {
            context.getString(R.string.grid_filters)
        }
        summary.text = description
        summary.isVisible = !description.isNullOrEmpty()
    }
}
