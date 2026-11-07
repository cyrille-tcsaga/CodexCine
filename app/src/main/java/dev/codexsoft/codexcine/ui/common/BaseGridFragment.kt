package dev.codexsoft.codexcine.ui.common

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.leanback.app.VerticalGridSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.DiffCallback
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.OnItemViewSelectedListener
import androidx.leanback.widget.Presenter

/** A vertical grid of cards with a loading indicator. Items are appended, or replaced in place. */
abstract class BaseGridFragment(
    private val columns: Int,
    protected val cardPresenter: Presenter,
) : VerticalGridSupportFragment() {

    private val cards = ArrayObjectAdapter(cardPresenter)

    protected abstract fun onCardClicked(item: Any)

    /** Called with the position of the newly selected card. */
    protected open fun onCardSelected(position: Int) = Unit

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setGridPresenter(cardGridPresenter(columns))
        adapter = cards
        onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
            if (item != null) onCardClicked(item)
        }
        setOnItemViewSelectedListener(
            OnItemViewSelectedListener { _, item, _, _ ->
                if (item != null) onCardSelected(cards.indexOf(item))
            },
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        progressBarManager.setRootView(view as ViewGroup)
        super.onViewCreated(view, savedInstanceState)
    }

    protected fun setLoading(loading: Boolean) {
        if (loading) progressBarManager.show() else progressBarManager.hide()
    }

    /** Shows [items], the full list so far, by appending what the grid does not have yet. */
    protected fun showItems(items: List<Any>) {
        if (items.size > cards.size()) {
            cards.addAll(cards.size(), items.drop(cards.size()))
        }
    }

    /** Shows [items] in place of the current ones, rebinding only the cards that changed, so focus stays put. */
    protected fun <T : Any> replaceItems(items: List<T>, diff: DiffCallback<T>) {
        cards.setItems(items, diff)
    }
}
