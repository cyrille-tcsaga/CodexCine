package dev.codexsoft.codexcine.ui.browse

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowPresenter
import dev.codexsoft.codexcine.R

/** A line of text where a page has no rows, such as an empty "Ma liste". */
class MessageRow(val message: String) : Row()

class MessageRowPresenter : RowPresenter() {

    init {
        headerPresenter = null
        selectEffectEnabled = false
    }

    override fun isUsingDefaultSelectEffect() = false

    override fun createRowViewHolder(parent: ViewGroup): ViewHolder =
        ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.view_message_row, parent, false))

    override fun onBindRowViewHolder(vh: ViewHolder, item: Any) {
        super.onBindRowViewHolder(vh, item)
        (vh.view as TextView).text = (item as MessageRow).message
    }
}
