package dev.sumanth.goodbuy

import androidx.lifecycle.ViewModelStore
import dev.sumanth.goodbuy.repository.ProductRepository
import dev.sumanth.goodbuy.repository.TrackingRepository
import dev.sumanth.goodbuy.ui.viewmodel.HomeViewModel
import dev.sumanth.goodbuy.ui.viewmodel.TrackingViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScreenViewModelsTest {
    private val dispatcher = StandardTestDispatcher()
    private val viewModels = ViewModelStore()
    private val amazonProduct = ProductRepository.sampleProducts.first()
    private val flipkartProduct = ProductRepository.sampleProducts[1]

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        TrackingRepository.clearAll()
    }

    @After
    fun tearDown() {
        viewModels.clear()
        dispatcher.scheduler.runCurrent()
        TrackingRepository.clearAll()
        Dispatchers.resetMain()
    }

    @Test
    fun searchDeduplicatesResultsAndFiltersByStore() = runTest {
        val model = HomeViewModel { query ->
            assertEquals("headphones", query)
            Result.success(listOf(amazonProduct, amazonProduct, flipkartProduct))
        }.also { viewModels.put("home", it) }

        model.updateSearchQuery(" headphones ")
        model.search()
        assertTrue(model.uiState.value.isLoading)
        advanceUntilIdle()
        model.selectStore("Flipkart")

        val state = model.uiState.value
        assertEquals(2, state.products.size)
        assertEquals(listOf(flipkartProduct), state.filteredProducts)
        assertFalse(state.isLoading)
        assertTrue(state.isScrapedData)
        assertNull(state.searchError)
    }

    @Test
    fun searchFinishesWithoutScreenCollectors() = runTest {
        val response = CompletableDeferred<Unit>()
        val model = HomeViewModel {
            response.await()
            Result.success(listOf(amazonProduct))
        }.also { viewModels.put("home", it) }

        model.updateSearchQuery("headphones")
        model.search()
        runCurrent()
        assertTrue(model.uiState.value.isLoading)

        response.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf(amazonProduct), model.uiState.value.products)
        assertFalse(model.uiState.value.isLoading)
    }

    @Test
    fun clearingSearchCancelsPendingRequest() = runTest {
        val response = CompletableDeferred<Unit>()
        val model = HomeViewModel {
            response.await()
            Result.success(listOf(amazonProduct))
        }.also { viewModels.put("home", it) }

        model.updateSearchQuery("headphones")
        model.selectStore("Amazon")
        model.search()
        runCurrent()
        model.clearSearch()
        response.complete(Unit)
        advanceUntilIdle()

        val state = model.uiState.value
        assertEquals("", state.searchQuery)
        assertEquals("All", state.selectedStore)
        assertTrue(state.products.isEmpty())
        assertFalse(state.isLoading)
        assertNull(state.searchError)
    }

    @Test
    fun newerSearchReplacesPendingRequest() = runTest {
        val response = CompletableDeferred<Unit>()
        val model = HomeViewModel { query ->
            if (query == "old") {
                response.await()
                Result.success(listOf(amazonProduct))
            } else Result.success(listOf(flipkartProduct))
        }.also { viewModels.put("home", it) }

        model.updateSearchQuery("old")
        model.search()
        runCurrent()
        model.updateSearchQuery("new")
        model.search()
        response.complete(Unit)
        advanceUntilIdle()

        assertEquals("new", model.uiState.value.searchQuery)
        assertEquals(listOf(flipkartProduct), model.uiState.value.products)
        assertFalse(model.uiState.value.isLoading)
    }

    @Test
    fun failedSearchStopsLoadingAndExposesError() = runTest {
        val model = HomeViewModel {
            Result.failure(IllegalStateException("Offline"))
        }.also { viewModels.put("home", it) }

        model.updateSearchQuery("headphones")
        model.search()
        advanceUntilIdle()

        assertEquals("Search error: Offline", model.uiState.value.searchError)
        assertTrue(model.uiState.value.products.isEmpty())
        assertFalse(model.uiState.value.isLoading)
        assertEquals("Error fetching search results", model.messages.first())
    }

    @Test
    fun trackerGroupsAndUngroupsSelectedProducts() = runTest {
        TrackingRepository.addTrackedProduct(amazonProduct)
        TrackingRepository.addTrackedProduct(flipkartProduct)
        val model = TrackingViewModel().also { viewModels.put("tracker", it) }

        model.toggleProductSelection(amazonProduct.id)
        model.toggleProductSelection(flipkartProduct.id)
        advanceUntilIdle()
        assertEquals(2, model.uiState.value.totalSelectedCount)
        model.groupSelected()
        advanceUntilIdle()

        val groupedState = model.uiState.value
        assertFalse(groupedState.isSelectionMode)
        assertEquals(0, groupedState.totalSelectedCount)
        assertTrue(groupedState.ungroupedProducts.isEmpty())
        val group = groupedState.trackedGroups.single()
        assertEquals(2, groupedState.productsForGroup(group).size)

        model.showGroupDetails(group.id)
        advanceUntilIdle()
        assertEquals(group, model.uiState.value.selectedGroupForDetails)
        model.ungroup(group.id)
        advanceUntilIdle()
        assertTrue(model.uiState.value.trackedGroups.isEmpty())
        assertEquals(2, model.uiState.value.ungroupedProducts.size)
        assertNull(model.uiState.value.selectedGroupForDetails)

        model.removeProduct(amazonProduct)
        advanceUntilIdle()
        assertEquals(listOf(flipkartProduct.id), model.uiState.value.trackedProducts.map { it.id })
        model.clearAll()
        advanceUntilIdle()
        assertTrue(model.uiState.value.trackedProducts.isEmpty())
    }

    @Test
    fun trackerRejectsGroupingFewerThanTwoSelections() = runTest {
        val model = TrackingViewModel().also { viewModels.put("tracker", it) }
        model.toggleProductSelection(amazonProduct.id)
        model.groupSelected()
        advanceUntilIdle()

        assertTrue(model.uiState.value.trackedGroups.isEmpty())
        assertTrue(model.uiState.value.isSelectionMode)
        assertEquals("Select at least 2 items to group them", model.messages.first())
    }

    @Test
    fun trackerPreventsOverlappingRefreshes() = runTest {
        val response = CompletableDeferred<Int>()
        var calls = 0
        val model = TrackingViewModel(
            refreshAllPrices = { calls++; response.await() },
            refreshStalePrices = { calls++; 0 }
        ).also { viewModels.put("tracker", it) }

        model.refreshPrices()
        runCurrent()
        assertTrue(model.uiState.value.isRefreshingPrices)
        model.refreshPrices(force = false)
        runCurrent()
        assertEquals(1, calls)

        response.complete(2)
        advanceUntilIdle()
        assertFalse(model.uiState.value.isRefreshingPrices)
        assertEquals("Updated prices for 2 products!", model.messages.first())
    }

    @Test
    fun trackerRefreshFailureResetsLoadingAndAllowsRetry() = runTest {
        var calls = 0
        val model = TrackingViewModel(refreshAllPrices = {
            calls++
            if (calls == 1) throw IllegalStateException("Offline")
            0
        }).also { viewModels.put("tracker", it) }

        model.refreshPrices()
        advanceUntilIdle()
        assertFalse(model.uiState.value.isRefreshingPrices)
        assertEquals("Error refreshing tracked prices", model.messages.first())

        model.refreshPrices()
        advanceUntilIdle()
        assertEquals(2, calls)
        assertFalse(model.uiState.value.isRefreshingPrices)
        assertEquals("All tracked prices are up to date.", model.messages.first())
    }
}