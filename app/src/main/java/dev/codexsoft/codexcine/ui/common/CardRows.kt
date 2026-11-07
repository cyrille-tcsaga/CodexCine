package dev.codexsoft.codexcine.ui.common

import androidx.leanback.widget.FocusHighlight
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.RowHeaderPresenter
import androidx.leanback.widget.VerticalGridPresenter

/*
 * The selection effect shared by every row and grid of cards: a slight zoom and the ivory ring drawn by
 * BaseCardPresenter. Leanback's defaults (a strong zoom, drop shadows, darkened rows around the focus)
 * felt heavy on a TV.
 */

fun cardRowPresenter(): ListRowPresenter =
    ListRowPresenter(FocusHighlight.ZOOM_FACTOR_XSMALL, /* useFocusDimmer = */ false).apply {
        shadowEnabled = false
        selectEffectEnabled = false
        headerPresenter = OpaqueRowHeaderPresenter()
    }

fun cardGridPresenter(columns: Int): VerticalGridPresenter =
    VerticalGridPresenter(FocusHighlight.ZOOM_FACTOR_XSMALL, /* useFocusDimmer = */ false).apply {
        numberOfColumns = columns
        shadowEnabled = false
    }

/** Row titles stay fully readable: without the select effect, Leanback would leave them all faded. */
private class OpaqueRowHeaderPresenter : RowHeaderPresenter() {
    override fun onSelectLevelChanged(holder: ViewHolder) {
        holder.view.alpha = 1f
    }
}
