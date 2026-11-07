package dev.codexsoft.codexcine

import dev.codexsoft.codexcine.core.AppResult
import dev.codexsoft.codexcine.core.ErrorKind
import dev.codexsoft.codexcine.domain.model.SearchResults
import dev.codexsoft.codexcine.ui.search.SearchUiState
import dev.codexsoft.codexcine.ui.search.SearchViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val empty = SearchResults(emptyList(), emptyList(), emptyList())
    private val searched = mutableListOf<String>()

    private fun viewModel() = SearchViewModel { query ->
        searched += query
        AppResult.Success(empty)
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `search only starts after a 400 ms pause`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.onQueryChanged("batman")

        advanceTimeBy(399)
        runCurrent()
        assertEquals(emptyList<String>(), searched)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf("batman"), searched)
        assertEquals(SearchUiState.Results("batman", empty), viewModel.uiState.value)
    }

    @Test
    fun `typing restarts the wait, so only the last query is searched`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.onQueryChanged("bat")
        advanceTimeBy(300)
        viewModel.onQueryChanged("batm")
        advanceTimeBy(300)
        viewModel.onQueryChanged("batman")
        advanceTimeBy(400)
        runCurrent()

        assertEquals(listOf("batman"), searched)
    }

    @Test
    fun `a new query cancels a search already in flight`() = runTest(dispatcher) {
        val viewModel = SearchViewModel { query ->
            searched += query
            if (query == "slow") awaitCancellation()
            AppResult.Success(empty)
        }
        viewModel.onQuerySubmitted("slow")
        runCurrent()
        viewModel.onQuerySubmitted("fast")
        runCurrent()

        assertEquals(listOf("slow", "fast"), searched)
        assertEquals(SearchUiState.Results("fast", empty), viewModel.uiState.value)
    }

    @Test
    fun `queries shorter than two characters are ignored`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.onQueryChanged("b")
        advanceTimeBy(1_000)
        runCurrent()
        viewModel.onQueryChanged(" b ")
        advanceTimeBy(1_000)
        runCurrent()

        assertEquals(emptyList<String>(), searched)
        assertEquals(SearchUiState.Idle, viewModel.uiState.value)
    }

    @Test
    fun `clearing the query clears the results`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.onQuerySubmitted("batman")
        runCurrent()
        assertTrue(viewModel.uiState.value is SearchUiState.Results)

        viewModel.onQueryChanged("")
        assertEquals(SearchUiState.Idle, viewModel.uiState.value)
    }

    @Test
    fun `submitting skips the wait and does not repeat an answered query`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.onQueryChanged("batman")
        advanceTimeBy(100)
        viewModel.onQuerySubmitted("batman")
        runCurrent()
        assertEquals(listOf("batman"), searched)

        viewModel.onQuerySubmitted("batman")
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(listOf("batman"), searched)
    }

    @Test
    fun `failure is reported`() = runTest(dispatcher) {
        val viewModel = SearchViewModel { AppResult.Error(ErrorKind.NO_CONNECTION) }
        viewModel.onQuerySubmitted("batman")
        runCurrent()

        assertEquals(SearchUiState.Failed(ErrorKind.NO_CONNECTION), viewModel.uiState.value)
    }
}
