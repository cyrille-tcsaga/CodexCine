package dev.codexsoft.codexcine.ui.browse

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowHeaderPresenter
import dev.codexsoft.codexcine.R

/** Sidebar entries with an icon. The focused one turns into an ivory pill; the others fade with distance. */
class SidebarHeaderPresenter : RowHeaderPresenter() {

    override fun onCreateViewHolder(parent: ViewGroup): Presenter.ViewHolder =
        ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.view_sidebar_item, parent, false))

    override fun onBindViewHolder(viewHolder: Presenter.ViewHolder, item: Any?) {
        val entry = (item as? Row)?.headerItem as? SidebarItem ?: return
        val view = viewHolder.view
        view.findViewById<ImageView>(R.id.sidebar_icon).apply {
            setImageResource(entry.page.iconRes)
            imageTintList = ContextCompat.getColorStateList(context, R.color.on_pill)
        }
        view.findViewById<TextView>(R.id.sidebar_label).text = entry.name
        view.contentDescription = entry.name
    }

    override fun onUnbindViewHolder(viewHolder: Presenter.ViewHolder) = Unit

    override fun onSelectLevelChanged(holder: ViewHolder) {
        holder.view.alpha = UNSELECTED_ALPHA + (1f - UNSELECTED_ALPHA) * holder.selectLevel
    }

    private companion object {
        const val UNSELECTED_ALPHA = 0.6f
    }
}
