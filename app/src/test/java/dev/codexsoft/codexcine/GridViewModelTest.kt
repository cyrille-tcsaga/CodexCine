package dev.codexsoft.codexcine

import dev.codexsoft.codexcine.core.AppResult
import dev.codexsoft.codexcine.core.ErrorKind
import dev.codexsoft.codexcine.domain.model.GridFilters
import dev.codexsoft.codexcine.domain.model.MediaItem
import dev.codexsoft.codexcine.domain.model.MediaPage
import dev.codexsoft.codexcine.domain.model.MediaType
import dev.codexsoft.codexcine.ui.grid.GridUiState
import dev.codexsoft.codexcine.ui.grid.GridViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GridViewModelTest {

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun item(id: Int) = MediaItem(id, "T$id", null, null, null, 0.0, MediaType.MOVIE)

    /** 20 items per page, numbered so that pages never overlap. */
    private fun page(number: Int, totalPages: Int = 5): AppResult<MediaPage> = AppResult.Success(
        MediaPage(((number - 1) * 20 until number * 20).map(::item), number, totalPages),
    )

    private val GridViewModel.size get() = (uiState.value as GridUiState.Content).items.size

    @Test
    fun `first page is loaded on creation`() {
        val requested = mutableListOf<Int>()
        val viewModel = GridViewModel { _, it -> requested += it; page(it) }

        assertEquals(listOf(1), requested)
        assertEquals(20, viewModel.size)
    }

    @Test
    fun `next page loads only within the last ten items`() {
        val requested = mutableListOf<Int>()
        val viewModel = GridViewModel { _, it -> requested += it; page(it) }

        viewModel.onItemSelected(9)
        assertEquals(listOf(1), requested)

        viewModel.onItemSelected(10)
        assertEquals(listOf(1, 2), requested)
        assertEquals(40, viewModel.size)
    }

    @Test
    fun `selections during a load do not start a second one`() {
        val requested = mutableListOf<Int>()
        val pending = CompletableDeferred<AppResult<MediaPage>>()
        val viewModel = GridViewModel { _, it ->
            requested += it
            if (it == 1) page(1) else pending.await()
        }

        viewModel.onItemSelected(15)
        viewModel.onItemSelected(16)
        viewModel.onItemSelected(17)
        assertEquals(listOf(1, 2), requested)

        pending.complete(page(2))
        assertEquals(40, viewModel.size)
    }

    @Test
    fun `paging stops at total pages`() {
        val requested = mutableListOf<Int>()
        val viewModel = GridViewModel { _, it -> requested += it; page(it, totalPages = 1) }

        viewModel.onItemSelected(19)

        assertEquals(listOf(1), requested)
    }

    @Test
    fun `failure on the first page is an error`() {
        val viewModel = GridViewModel { _, _ -> AppResult.Error(ErrorKind.NO_CONNECTION) }

        assertEquals(GridUiState.Error(ErrorKind.NO_CONNECTION), viewModel.uiState.value)
    }

    @Test
    fun `failure on a later page keeps the items and can be retried`() {
        var failNext = true
        val requested = mutableListOf<Int>()
        val viewModel = GridViewModel { _, it ->
            requested += it
            when {
                it == 1 -> page(1)
                failNext -> { failNext = false; AppResult.Error(ErrorKind.NO_CONNECTION) }
                else -> page(it)
            }
        }

        viewModel.onItemSelected(15)
        assertEquals(20, viewModel.size)

        viewModel.onItemSelected(16)
        assertEquals(listOf(1, 2, 2), requested)
        assertEquals(40, viewModel.size)
    }

    @Test
    fun `items repeated across pages are dropped`() {
        val viewModel = GridViewModel { _, it ->
            if (it == 1) {
                AppResult.Success(MediaPage(listOf(item(1), item(2)), 1, 2))
            } else {
                AppResult.Success(MediaPage(listOf(item(2), item(3)), 2, 2))
            }
        }

        viewModel.onItemSelected(1)

        val items = (viewModel.uiState.value as GridUiState.Content).items
        assertEquals(listOf(1, 2, 3), items.map { it.id })
    }

    @Test
    fun `new filters start again from the first page, with them`() {
        val requested = mutableListOf<Pair<GridFilters, Int>>()
        val viewModel = GridViewModel { filters, number -> requested += filters to number; page(number) }
        viewModel.onItemSelected(15)
        assertEquals(40, viewModel.size)

        val action = GridFilters(genreId = 28)
        viewModel.setFilters(action)
        assertEquals(action to 1, requested.last())
        assertEquals(20, viewModel.size)
        assertEquals(action, viewModel.filters.value)

        viewModel.setFilters(action)
        assertEquals(3, requested.size)
    }

    @Test
    fun `a page still loading for the old filters is ignored`() {
        val slow = CompletableDeferred<AppResult<MediaPage>>()
        val viewModel = GridViewModel { filters, number ->
            if (filters.isActive) page(number) else slow.await()
        }
        viewModel.setFilters(GridFilters(minRating = 7))
        assertEquals(20, viewModel.size)

        slow.complete(AppResult.Success(MediaPage(listOf(item(999)), 1, 1)))
        val items = (viewModel.uiState.value as GridUiState.Content).items
        assertEquals(20, items.size)
        assertFalse(items.any { it.id == 999 })
    }
}
