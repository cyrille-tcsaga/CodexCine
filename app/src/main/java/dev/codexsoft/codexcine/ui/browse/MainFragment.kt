package dev.codexsoft.codexcine.ui.browse

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.leanback.app.BackgroundManager
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.PageRow
import androidx.leanback.widget.PresenterSelector
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.codexsoft.codexcine.R
import dev.codexsoft.codexcine.ui.common.appContainer
import dev.codexsoft.codexcine.ui.common.collectWhileStarted
import dev.codexsoft.codexcine.ui.common.showErrorScreen
import dev.codexsoft.codexcine.ui.search.SearchActivity

/**
 * The home screen: a sidebar (Leanback's headers) whose entries open pages of rows. The catalogue is
 * loaded once, here, and every page reads the same [MainViewModel].
 */
class MainFragment : BrowseSupportFragment() {

    private val viewModel: MainViewModel by viewModels { mainViewModelFactory() }

    private lateinit var backgroundManager: BackgroundManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mainFragmentRegistry.registerFragment(PageRow::class.java, PageFactory())
        val sidebarPresenter = SidebarHeaderPresenter()
        setHeaderPresenterSelector(
            object : PresenterSelector() {
                override fun getPresenter(item: Any?) = sidebarPresenter
            },
        )
        adapter = ArrayObjectAdapter(ListRowPresenter()).apply {
            HomePage.entries.forEach { page -> add(PageRow(SidebarItem(page, getString(page.titleRes)))) }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        backgroundManager = BackgroundManager.getInstance(requireActivity())
        if (!backgroundManager.isAttached) {
            backgroundManager.attach(requireActivity().window)
        }

        title = getString(R.string.app_name)
        badgeDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.logo_lockup)
        // Between the backdrop (the window background) and the rows: the artwork shows top right, like a
        // streaming "billboard", and the rows stay readable over any image.
        view.background = ContextCompat.getDrawable(requireContext(), R.drawable.backdrop_scrim)
        headersState = HEADERS_ENABLED
        isHeadersTransitionOnBackEnabled = true
        brandColor = ContextCompat.getColor(requireContext(), R.color.fastlane_background)
        searchAffordanceColor = ContextCompat.getColor(requireContext(), R.color.search_opaque)

        setOnSearchClickedListener {
            startActivity(Intent(requireContext(), SearchActivity::class.java))
        }

        collectWhileStarted(viewModel.uiState, ::render)
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshLocalRows()
    }

    private fun render(state: MainUiState) {
        when (state) {
            MainUiState.Loading -> progressBarManager.show()
            is MainUiState.Content -> {
                progressBarManager.hide()
                (activity as? MainActivity)?.onHomeReady()
            }
            is MainUiState.Error -> {
                progressBarManager.hide()
                (activity as? MainActivity)?.onHomeReady()
                showErrorScreen(state.kind)
            }
        }
    }

    private class PageFactory : FragmentFactory<Fragment>() {
        override fun createFragment(row: Any?): Fragment {
            val page = ((row as? PageRow)?.headerItem as? SidebarItem)?.page ?: HomePage.HOME
            return HomePageFragment.newInstance(page)
        }
    }
}

/** The one factory for [MainViewModel]: pages obtain the same instance through the home fragment. */
internal fun Fragment.mainViewModelFactory(): ViewModelProvider.Factory = viewModelFactory {
    initializer {
        val container = appContainer
        MainViewModel(
            container.catalogRepository,
            container.myListRepository,
            container.watchProgressRepository,
            container.watchedEpisodesRepository,
            container.watchNextPublisher,
        )
    }
}
