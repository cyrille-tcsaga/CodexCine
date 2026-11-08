package dev.codexsoft.codexcine.ui.search

import dev.codexsoft.codexcine.ui.common.cardRowPresenter
import android.app.SearchManager
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.leanback.app.SearchSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ObjectAdapter
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.SearchOrbView
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.codexsoft.codexcine.R
import dev.codexsoft.codexcine.core.ErrorKind
import dev.codexsoft.codexcine.domain.model.MediaItem
import dev.codexsoft.codexcine.domain.model.SearchResults
import dev.codexsoft.codexcine.ui.browse.CardPresenter
import dev.codexsoft.codexcine.ui.common.appContainer
import dev.codexsoft.codexcine.ui.common.collectWhileStarted
import dev.codexsoft.codexcine.ui.common.openMediaItem

/**
 * Voice input is Leanback's built-in recogniser, which asks for RECORD_AUDIO itself. If the permission
 * is denied or no recogniser exists it simply does nothing, and the on-screen keyboard still works.
 */
class SearchFragment : SearchSupportFragment(), SearchSupportFragment.SearchResultProvider {

    private val viewModel: SearchViewModel by viewModels {
        viewModelFactory {
            initializer {
                val catalog = appContainer.catalogRepository
                SearchViewModel { query -> catalog.search(query) }
            }
        }
    }

    private val results = ArrayObjectAdapter(cardRowPresenter())
    private val cardPresenter = CardPresenter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.app_name)
        setSearchAffordanceColors(
            SearchOrbView.Colors(ContextCompat.getColor(requireContext(), R.color.search_opaque)),
        )
        setSearchResultProvider(this)
        setOnItemViewClickedListener(
            OnItemViewClickedListener { _, item, _, _ ->
                if (item is MediaItem) openMediaItem(item)
            },
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        collectWhileStarted(viewModel.uiState, ::render)
        if (savedInstanceState == null) searchExternalQuery()
    }

    /**
     * Opened with a query, by the system search or the Assistant ("cherche Dune sur Codex Ciné"): search it
     * at once instead of listening. Any app can send one, so it is cleaned first; it is only ever searched.
     */
    private fun searchExternalQuery() {
        val intent = requireActivity().intent
        if (intent.action !in EXTERNAL_SEARCH_ACTIONS) return
        val query = SearchSuggestions.cleanQuery(intent.getStringExtra(SearchManager.QUERY)) ?: return
        setSearchQuery(query, true)
    }

    override fun getResultsAdapter(): ObjectAdapter = results

    override fun onQueryTextChange(newQuery: String): Boolean {
        viewModel.onQueryChanged(newQuery)
        return true
    }

    override fun onQueryTextSubmit(query: String): Boolean {
        viewModel.onQuerySubmitted(query)
        return true
    }

    private fun render(state: SearchUiState) {
        results.clear()
        when (state) {
            SearchUiState.Idle -> Unit
            is SearchUiState.Results -> showResults(state.query, state.results)
            is SearchUiState.Failed -> addHeaderOnly(getString(errorMessage(state.kind)))
        }
    }

    private fun showResults(query: String, found: SearchResults) {
        if (found.isEmpty) {
            addHeaderOnly(getString(R.string.search_no_results, query))
            return
        }
        addRow(getString(R.string.row_movies), found.movies)
        addRow(getString(R.string.row_tv_shows), found.shows)
        addRow(getString(R.string.row_people), found.people)
    }

    private fun addRow(title: String, items: List<MediaItem>) {
        if (items.isEmpty()) return
        val cards = ArrayObjectAdapter(cardPresenter)
        cards.addAll(0, items)
        results.add(ListRow(HeaderItem(title), cards))
    }

    private fun addHeaderOnly(text: String) {
        results.add(ListRow(HeaderItem(text), ArrayObjectAdapter(cardPresenter)))
    }

    private fun errorMessage(kind: ErrorKind): Int = when (kind) {
        ErrorKind.INVALID_TOKEN -> R.string.error_invalid_token
        ErrorKind.NO_CONNECTION -> R.string.error_no_connection
        ErrorKind.GENERIC -> R.string.error_generic
    }

    private companion object {
        val EXTERNAL_SEARCH_ACTIONS = setOf(Intent.ACTION_SEARCH, "com.google.android.gms.actions.SEARCH_ACTION")
    }
}
