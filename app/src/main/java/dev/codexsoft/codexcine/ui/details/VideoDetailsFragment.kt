package dev.codexsoft.codexcine.ui.details

import dev.codexsoft.codexcine.ui.common.cardRowPresenter
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.fragment.app.viewModels
import androidx.leanback.app.DetailsSupportFragment
import androidx.leanback.app.DetailsSupportFragmentBackgroundController
import androidx.leanback.widget.Action
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.ClassPresenterSelector
import androidx.leanback.widget.DetailsOverviewRow
import androidx.leanback.widget.FullWidthDetailsOverviewRowPresenter
import androidx.leanback.widget.FullWidthDetailsOverviewSharedElementHelper
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.OnActionClickedListener
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.Presenter
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import dev.codexsoft.codexcine.R
import dev.codexsoft.codexcine.core.ErrorKind
import dev.codexsoft.codexcine.core.Extras
import dev.codexsoft.codexcine.domain.model.CastMember
import dev.codexsoft.codexcine.domain.model.MediaDetails
import dev.codexsoft.codexcine.domain.model.MediaItem
import dev.codexsoft.codexcine.domain.model.MediaType
import dev.codexsoft.codexcine.domain.model.Season
import dev.codexsoft.codexcine.ui.browse.CardPresenter
import dev.codexsoft.codexcine.ui.common.appContainer
import dev.codexsoft.codexcine.ui.common.collectWhileStarted
import dev.codexsoft.codexcine.ui.common.loadBackgroundBitmap
import dev.codexsoft.codexcine.ui.common.openMediaItem
import dev.codexsoft.codexcine.ui.common.showErrorScreen
import dev.codexsoft.codexcine.ui.person.PersonActivity
import dev.codexsoft.codexcine.ui.playback.PlaybackActivity
import dev.codexsoft.codexcine.ui.season.SeasonActivity
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The overview row and the Play action exist before the network answers: Play only needs the intent
 * extras, and the shared element transition needs a target to land on right away.
 */
class VideoDetailsFragment : DetailsSupportFragment() {

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

    private lateinit var backgroundController: DetailsSupportFragmentBackgroundController
    private lateinit var rows: ArrayObjectAdapter
    private lateinit var overviewRow: DetailsOverviewRow
    private lateinit var actions: ArrayObjectAdapter

    private lateinit var playAction: Action
    private var myListAction: Action? = null
    private var displayedDetails: MediaDetails? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        backgroundController = DetailsSupportFragmentBackgroundController(this)
        backgroundController.enableParallax()

        actions = ArrayObjectAdapter()
        playAction = Action(ACTION_PLAY, getString(R.string.action_play))
        playAction.icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_player_play)
        actions.add(playAction)

        overviewRow = DetailsOverviewRow(initialTitle)
        overviewRow.imageDrawable = posterPlaceholder()
        overviewRow.actionsAdapter = actions

        val selector = ClassPresenterSelector()
            .addClassPresenter(DetailsOverviewRow::class.java, createOverviewPresenter())
            .addClassPresenter(ListRow::class.java, cardRowPresenter())
        rows = ArrayObjectAdapter(selector)
        rows.add(overviewRow)
        adapter = rows

        onItemViewClickedListener = OnItemViewClickedListener { card, item, _, _ ->
            when (item) {
                is MediaItem -> openMediaItem(item, card)
                is CastMember -> openPerson(item)
                is Season -> openSeason(item)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        progressBarManager.setRootView(view as ViewGroup)
        super.onViewCreated(view, savedInstanceState)
        backgroundController.solidColor = ContextCompat.getColor(requireContext(), R.color.brand_night)

        if (mediaType == null) {
            showDetailsError(ErrorKind.GENERIC)
            return
        }
        collectWhileStarted(viewModel.uiState, ::render)
        collectWhileStarted(viewModel.playAvailability, ::renderPlayAvailability)
    }

    private fun renderPlayAvailability(availability: PlayAvailability) {
        playAction.label2 = when (availability) {
            PlayAvailability.Unknown -> null
            PlayAvailability.Unavailable -> getString(R.string.action_play_unavailable)
            is PlayAvailability.Available ->
                availability.languages.joinToString(getString(R.string.separator)) { it.uppercase() }.ifEmpty { null }
        }
        actions.notifyArrayItemRangeChanged(actions.indexOf(playAction), 1)
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
                updateMyListAction(state.isInMyList)
            }
            is DetailsUiState.Error -> {
                progressBarManager.hide()
                showDetailsError(state.kind)
            }
        }
    }

    private fun showDetailsError(kind: ErrorKind) {
        // The enter transition was postponed for the poster, which will now never be displayed.
        ActivityCompat.startPostponedEnterTransition(requireActivity())
        showErrorScreen(kind)
    }

    private fun bindDetails(details: MediaDetails) {
        overviewRow.item = details
        loadPoster(details.posterUrl)
        loadCover(details.backdropUrl)

        if (details.trailerKey != null) {
            actions.add(Action(ACTION_TRAILER, getString(R.string.action_trailer)))
        }

        addRow(R.string.row_seasons, SeasonCardPresenter(), details.seasons)
        addRow(R.string.row_cast, CastCardPresenter(), details.cast)
        addRow(R.string.row_recommendations, CardPresenter(), details.recommendations)
    }

    private fun addRow(titleRes: Int, presenter: Presenter, items: List<Any>) {
        if (items.isEmpty()) return
        val cards = ArrayObjectAdapter(presenter)
        cards.addAll(0, items)
        rows.add(ListRow(HeaderItem(titleRes.toLong(), getString(titleRes)), cards))
    }

    private fun updateMyListAction(isInMyList: Boolean) {
        val label = getString(if (isInMyList) R.string.action_remove_my_list else R.string.action_add_my_list)
        val current = myListAction
        if (current == null) {
            myListAction = Action(ACTION_MY_LIST, label).also { actions.add(it) }
        } else if (current.label1 != label) {
            current.label1 = label
            actions.notifyArrayItemRangeChanged(actions.indexOf(current), 1)
        }
    }

    private fun createOverviewPresenter(): FullWidthDetailsOverviewRowPresenter {
        val presenter = FullWidthDetailsOverviewRowPresenter(DetailsDescriptionPresenter())
        presenter.backgroundColor = ContextCompat.getColor(requireContext(), R.color.details_background)
        presenter.actionsBackgroundColor = ContextCompat.getColor(requireContext(), R.color.details_actions_background)

        val sharedElementHelper = FullWidthDetailsOverviewSharedElementHelper()
        sharedElementHelper.setSharedElementEnterTransition(activity, DetailsActivity.SHARED_ELEMENT_NAME)
        presenter.setListener(sharedElementHelper)
        presenter.isParticipatingEntranceTransition = true

        presenter.onActionClickedListener = OnActionClickedListener { action ->
            when (action.id) {
                ACTION_PLAY -> play()
                ACTION_TRAILER -> displayedDetails?.trailerKey?.let(::openTrailer)
                ACTION_MY_LIST -> viewModel.toggleMyList()
            }
        }
        return presenter
    }

    private fun posterPlaceholder(): Drawable = GradientDrawable().apply {
        setColor(ContextCompat.getColor(requireContext(), R.color.card_background))
        setSize(posterWidthPx(), posterHeightPx())
    }

    private fun posterWidthPx() = (POSTER_WIDTH_DP * resources.displayMetrics.density).roundToInt()
    private fun posterHeightPx() = (POSTER_HEIGHT_DP * resources.displayMetrics.density).roundToInt()

    private fun loadPoster(url: String?) {
        if (url == null) return
        Glide.with(this)
            .load(url)
            .centerCrop()
            .into(object : CustomTarget<Drawable>(posterWidthPx(), posterHeightPx()) {
                override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                    overviewRow.imageDrawable = resource
                }

                override fun onLoadCleared(placeholder: Drawable?) {
                    overviewRow.imageDrawable = posterPlaceholder()
                }
            })
    }

    private fun loadCover(url: String?) {
        if (url == null) return
        viewLifecycleOwner.lifecycleScope.launch {
            val metrics = resources.displayMetrics
            val bitmap = loadBackgroundBitmap(requireContext(), url, metrics.widthPixels, metrics.heightPixels)
            if (bitmap != null) backgroundController.coverBitmap = bitmap
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
        const val ACTION_PLAY = 1L
        const val ACTION_TRAILER = 2L
        const val ACTION_MY_LIST = 3L

        const val POSTER_WIDTH_DP = 180
        const val POSTER_HEIGHT_DP = 270
    }
}
