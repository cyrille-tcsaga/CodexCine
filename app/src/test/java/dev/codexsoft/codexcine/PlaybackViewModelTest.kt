package dev.codexsoft.codexcine

import dev.codexsoft.codexcine.data.playback.NoStreamSourceResolver
import dev.codexsoft.codexcine.data.playback.PlaybackRequest
import dev.codexsoft.codexcine.data.playback.StreamServiceException
import dev.codexsoft.codexcine.data.playback.StreamSource
import dev.codexsoft.codexcine.data.playback.StreamSourceResolver
import dev.codexsoft.codexcine.domain.model.MediaType
import dev.codexsoft.codexcine.ui.playback.PlaybackUiState
import dev.codexsoft.codexcine.ui.playback.PlaybackViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackViewModelTest {

    private val request = PlaybackRequest(
        mediaId = 1396,
        mediaType = MediaType.TV,
        title = "Breaking Bad",
        seasonNumber = 1,
        episodeNumber = 3,
    )

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `no source state when the resolver returns null`() {
        val viewModel = PlaybackViewModel(request, NoStreamSourceResolver())

        assertEquals(PlaybackUiState.NoSource, viewModel.uiState.value)
    }

    @Test
    fun `stays loading until the resolver answers, then is ready`() {
        val answer = CompletableDeferred<StreamSource?>()
        val source = StreamSource.WebEmbed(url = "https://example.org/embed", providerName = "Test")
        val viewModel = PlaybackViewModel(
            request,
            object : StreamSourceResolver {
                override suspend fun resolve(request: PlaybackRequest) = answer.await()
            },
        )
        assertEquals(PlaybackUiState.Loading, viewModel.uiState.value)

        answer.complete(source)

        assertEquals(PlaybackUiState.Ready(source), viewModel.uiState.value)
    }

    @Test
    fun `resolver receives the request unchanged`() {
        var received: PlaybackRequest? = null
        PlaybackViewModel(
            request,
            object : StreamSourceResolver {
                override suspend fun resolve(request: PlaybackRequest): StreamSource? {
                    received = request
                    return null
                }
            },
        )

        assertEquals(request, received)
    }

    @Test
    fun `a failing resolver does not crash`() {
        val viewModel = PlaybackViewModel(
            request,
            object : StreamSourceResolver {
                override suspend fun resolve(request: PlaybackRequest): StreamSource? =
                    throw IllegalStateException("provider is down")
            },
        )

        assertEquals(PlaybackUiState.Failed, viewModel.uiState.value)
    }

    @Test
    fun `an unreachable provider has its own state`() {
        val viewModel = PlaybackViewModel(
            request,
            object : StreamSourceResolver {
                override suspend fun resolve(request: PlaybackRequest): StreamSource? =
                    throw StreamServiceException("offline")
            },
        )

        assertEquals(PlaybackUiState.ServiceUnreachable, viewModel.uiState.value)
    }
}
