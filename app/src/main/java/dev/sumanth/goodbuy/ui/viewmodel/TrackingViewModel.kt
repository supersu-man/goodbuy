package dev.sumanth.goodbuy.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.sumanth.goodbuy.model.Product
import dev.sumanth.goodbuy.model.TrackedGroup
import dev.sumanth.goodbuy.repository.TrackingRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class TrackingUiState(
    val trackedProducts: List<Product> = emptyList(),
    val trackedGroups: List<TrackedGroup> = emptyList(),
    val lastRefreshedTime: Long = 0L,
    val isSelectionMode: Boolean = false,
    val selectedProductIds: Set<String> = emptySet(),
    val selectedGroupIds: Set<String> = emptySet(),
    val selectedGroupId: String? = null,
    val isRefreshingPrices: Boolean = false
) {
    val totalSelectedCount: Int
        get() = selectedProductIds.size + selectedGroupIds.size

    val ungroupedProducts: List<Product>
        get() {
            val groupedIds = trackedGroups.flatMap { it.productIds }.toSet()
            return trackedProducts.filterNot { it.id in groupedIds }
        }

    val selectedGroupForDetails: TrackedGroup?
        get() = trackedGroups.find { it.id == selectedGroupId }

    fun productsForGroup(group: TrackedGroup): List<Product> {
        val productIds = group.productIds.toSet()
        return trackedProducts.filter { it.id in productIds }
    }
}

class TrackingViewModel(
    private val refreshAllPrices: suspend () -> Int = TrackingRepository::refreshTrackedPrices,
    private val refreshStalePrices: suspend () -> Int = {
        TrackingRepository.checkAndRefreshTrackedPrices(minIntervalMillis = 30 * 60 * 1000L)
    }
) : ViewModel() {
    private val screenState = MutableStateFlow(TrackingUiState())
    val uiState = combine(
        TrackingRepository.trackedProducts,
        TrackingRepository.trackedGroups,
        TrackingRepository.lastRefreshedTime,
        screenState
    ) { products, groups, lastRefreshed, state ->
        state.copy(trackedProducts = products, trackedGroups = groups, lastRefreshedTime = lastRefreshed)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, TrackingUiState())

    private val messageChannel = Channel<String>(Channel.BUFFERED)
    val messages = messageChannel.receiveAsFlow()
    private var refreshJob: Job? = null

    suspend fun monitorPrices() {
        while (currentCoroutineContext().isActive) {
            refreshPrices(force = false)
            delay(60_000L)
        }
    }

    fun refreshPrices(force: Boolean = true) {
        if (refreshJob?.isActive == true) return
        screenState.update { it.copy(isRefreshingPrices = true) }
        refreshJob = viewModelScope.launch {
            try {
                val updatedCount = if (force) refreshAllPrices() else refreshStalePrices()
                if (updatedCount > 0) {
                    messageChannel.trySend("Updated prices for $updatedCount products!")
                } else if (force) {
                    messageChannel.trySend("All tracked prices are up to date.")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                messageChannel.trySend("Error refreshing tracked prices")
            } finally {
                screenState.update { it.copy(isRefreshingPrices = false) }
            }
        }
    }

    fun toggleProductSelection(productId: String) {
        screenState.update {
            it.copy(
                isSelectionMode = true,
                selectedProductIds = if (productId in it.selectedProductIds) {
                    it.selectedProductIds - productId
                } else it.selectedProductIds + productId
            )
        }
    }

    fun toggleGroupSelection(groupId: String) {
        screenState.update {
            it.copy(
                isSelectionMode = true,
                selectedGroupIds = if (groupId in it.selectedGroupIds) {
                    it.selectedGroupIds - groupId
                } else it.selectedGroupIds + groupId
            )
        }
    }

    fun exitSelectionMode() {
        screenState.update {
            it.copy(isSelectionMode = false, selectedProductIds = emptySet(), selectedGroupIds = emptySet())
        }
    }

    fun groupSelected() {
        val state = screenState.value
        if (state.totalSelectedCount < 2) {
            messageChannel.trySend("Select at least 2 items to group them")
            return
        }
        val group = TrackingRepository.groupProducts(
            state.selectedProductIds.toList(), state.selectedGroupIds.toList()
        )
        exitSelectionMode()
        if (group != null) messageChannel.trySend("Products grouped together successfully!")
    }

    fun showGroupDetails(groupId: String) {
        screenState.update { it.copy(selectedGroupId = groupId) }
    }

    fun dismissGroupDetails() {
        screenState.update { it.copy(selectedGroupId = null) }
    }

    fun ungroup(groupId: String) {
        TrackingRepository.ungroupGroup(groupId)
        screenState.update {
            it.copy(
                selectedGroupId = if (it.selectedGroupId == groupId) null else it.selectedGroupId,
                selectedGroupIds = it.selectedGroupIds - groupId
            )
        }
        messageChannel.trySend("Group split into individual tracked products")
    }

    fun removeProduct(product: Product) {
        TrackingRepository.removeTrackedProduct(product.id)
        screenState.update { it.copy(selectedProductIds = it.selectedProductIds - product.id) }
        messageChannel.trySend("Removed ${product.title} from tracking")
    }

    fun clearAll() {
        TrackingRepository.clearAll()
        exitSelectionMode()
        dismissGroupDetails()
        messageChannel.trySend("All tracked items cleared")
    }
}