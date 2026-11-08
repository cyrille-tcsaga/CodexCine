package dev.codexsoft.codexcine.ui.details

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.fragment.app.viewModels
import androidx.leanback.app.BackgroundManager
import androidx.leanback.app.ProgressBarManager
import androidx.leanback.app.RowsSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.ClassPresenterSelector
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.Presenter
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.codexsoft.codexcine.R
import dev.codexsoft.codexcine.core.ErrorKind
import dev.codexsoft.codexcine.core.Extras
import dev.codexsoft.codexcine.domain.model.CastMember
import dev.codexsoft.codexcine.domain.model.MediaDetails
import dev.codexsoft.codexcine.domain.model.MediaItem
import dev.codexsoft.codexcine.domain.model.MediaType
import dev.codexsoft.codexcine.domain.model.SagaRow
import dev.codexsoft.codexcine.domain.model.Season
import dev.codexsoft.codexcine.ui.browse.CardPresenter
import dev.codexsoft.codexcine.ui.common.appContainer
import dev.codexsoft.codexcine.ui.common.cardRowPresenter
import dev.codexsoft.codexcine.ui.common.collectWhileStarted
import dev.codexsoft.codexcine.ui.common.loadBackgroundBitmap
import dev.codexsoft.codexcine.ui.common.openMediaItem
import dev.codexsoft.codexcine.ui.common.showErrorScreen
import dev.codexsoft.codexcine.ui.person.PersonActivity
import dev.codexsoft.codexcine.ui.playback.PlaybackActivity
import dev.codexsoft.codexcine.ui.season.SeasonActivity
import kotlinx.coroutines.launch

/**
 * A title's page, built like the home billboard: its artwork fills the screen, with the facts and the
 * rounded buttons over it, and the seasons, cast and recommendations in rows underneath.
 *
 * The hero row exists before the network answers: Play only needs the intent extras.
 */
class VideoDetailsFragment : RowsSupportFragment() {

    private val mediaId get() = requireActivity().intent.getIntExtra(Extras.MEDIA_ID, Extras.NO_VALUE)
    private val mediaType get() = MediaType.fromValue(requireActivity().intent.getStringExtra(Extras.MEDIA_TYPE))
    private val initialTitle get() = requireActivity().intent.getStringExtra(Extras.TITLE).orEmpty()

    private val viewModel: DetailsViewModel by viewModels {
        viewModelFactory {
            initializer {
                val container = appContainer
                DetailsViewModel(
                    mediaId = mediaId,
                    mediaType = mediaType ?: MediaType.MOVIE,
                    catalog = container.catalogRepository,
                    myList = container.myListRepository,
                    vidzy = container.vidzyRepository,
                    watchProgress = container.watchProgressRepository,
                )
            }
        }
    }

    private val progressBarManager = ProgressBarManager()
    private lateinit var rows: ArrayObjectAdapter
    private lateinit var hero: DetailsHeroRow
    private var displayedDetails: MediaDetails? = null
    private var sagaRow: ListRow? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val backgroundManager = BackgroundManager.getInstance(requireActivity())
        if (!backgroundManager.isAttached) backgroundManager.attach(requireActivity().window)
        backgroundManager.color = ContextCompat.getColor(requireContext(), R.color.brand_night)

        hero = DetailsHeroRow(initialTitle, mediaType, heroListener)
        val selector = ClassPresenterSelector()
            .addClassPresenter(DetailsHeroRow::class.java, DetailsHeroRowPresenter())
            .addClassPresenter(ListRow::class.java, cardRowPresenter())
        rows = ArrayObjectAdapter(selector)
        rows.add(hero)
        adapter = rows

        onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
            when (item) {
                is MediaItem -> openMediaItem(item)
                is CastMember -> openPerson(item)
                is Season -> openSeason(item)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Between the artwork (the window background) and the text: it keeps both readable.
        view.background = ContextCompat.getDrawable(requireContext(), R.drawable.backdrop_scrim)
        progressBarManager.setRootView(view as ViewGroup)

        if (mediaType == null) {
            showErrorScreen(ErrorKind.GENERIC)
            return
        }
        collectWhileStarted(viewModel.uiState, ::render)
        collectWhileStarted(viewModel.playAvailability) { hero.availability = it }
        collectWhileStarted(viewModel.saga) { showSaga(it) }
    }

    override fun onDestroyView() {
        progressBarManager.hide()
        super.onDestroyView()
    }

    private val heroListener = object : DetailsHeroRow.Listener {
        override fun onPlay() = play()
        override fun onTrailer() {
            displayedDetails?.trailerKey?.let(::openTrailer)
        }
        override fun onToggleMyList() = viewModel.toggleMyList()
    }

    private fun render(state: DetailsUiState) {
        when (state) {
            DetailsUiState.Loading -> progressBarManager.show()
            is DetailsUiState.Content -> {
                progressBarManager.hide()
                if (displayedDetails != state.details) {
                    displayedDetails = state.details
                    bindDetails(state.details)
                }
                hero.isInMyList = state.isInMyList
            }
            is DetailsUiState.Error -> {
                progressBarManager.hide()
                showErrorScreen(state.kind)
            }
        }
    }

    private fun bindDetails(details: MediaDetails) {
        hero.details = details
        loadBackdrop(details.backdropUrl)

        // Rows below the hero are rebuilt, so a second answer never adds them twice.
        if (rows.size() > 1) rows.removeItems(1, rows.size() - 1)
        addRow(R.string.row_seasons, SeasonCardPresenter(), details.seasons)
        addRow(R.string.row_cast, CastCardPresenter(), details.cast)
        addRow(R.string.row_recommendations, CardPresenter(), details.recommendations)
        sagaRow = null
        showSaga(viewModel.saga.value)
    }

    /** Right under the hero: the rest of a saga is what one most often wants next. */
    private fun showSaga(saga: SagaRow?) {
        if (saga == null || displayedDetails == null) return
        sagaRow?.let(rows::remove)
        val title = saga.position?.let { getString(R.string.row_saga_position, saga.name, it, saga.total) }
            ?: saga.name
        val cards = ArrayObjectAdapter(CardPresenter())
        cards.addAll(0, saga.others)
        val row = ListRow(HeaderItem(SAGA_ROW_ID, title), cards)
        rows.add(1, row)
        sagaRow = row
    }

    private fun addRow(titleRes: Int, presenter: Presenter, items: List<Any>) {
        if (items.isEmpty()) return
        val cards = ArrayObjectAdapter(presenter)
        cards.addAll(0, items)
        rows.add(ListRow(HeaderItem(titleRes.toLong(), getString(titleRes)), cards))
    }

    private fun loadBackdrop(url: String?) {
        if (url == null) return
        viewLifecycleOwner.lifecycleScope.launch {
            val metrics = resources.displayMetrics
            val bitmap = loadBackgroundBitmap(requireContext(), url, metrics.widthPixels, metrics.heightPixels)
            if (bitmap != null) BackgroundManager.getInstance(requireActivity()).setBitmap(bitmap)
        }
    }

    private fun play() {
        val type = mediaType ?: return
        val title = displayedDetails?.title ?: initialTitle
        viewLifecycleOwner.lifecycleScope.launch {
            // A show has no single video: Play starts where the viewer left off.
            val episode = if (type == MediaType.TV) viewModel.episodeToStart() else null
            startActivity(
                PlaybackActivity.newIntent(
                    requireContext(),
                    mediaId = mediaId,
                    mediaType = type,
                    title = title,
                    seasonNumber = episode?.first,
                    episodeNumber = episode?.second,
                ),
            )
        }
    }

    private fun openTrailer(key: String) {
        val inApp = Intent(Intent.ACTION_VIEW, "vnd.youtube:$key".toUri())
        val inBrowser = Intent(Intent.ACTION_VIEW, "https://www.youtube.com/watch?v=$key".toUri())
        if (!tryStart(inApp) && !tryStart(inBrowser)) {
            Toast.makeText(requireContext(), R.string.no_trailer_app, Toast.LENGTH_LONG).show()
        }
    }

    private fun tryStart(intent: Intent): Boolean = try {
        startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }

    private fun openPerson(member: CastMember) {
        startActivity(PersonActivity.newIntent(requireContext(), member.id, member.name))
    }

    private fun openSeason(season: Season) {
        val title = displayedDetails?.title ?: initialTitle
        startActivity(SeasonActivity.newIntent(requireContext(), mediaId, season.seasonNumber, title))
    }

    private companion object {
        const val SAGA_ROW_ID = -1L
    }
}
