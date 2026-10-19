package dev.sumanth.goodbuy.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.sumanth.goodbuy.model.Product
import dev.sumanth.goodbuy.repository.ProductRepository
import dev.sumanth.goodbuy.repository.TrackingRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val searchQuery: String = "",
    val products: List<Product> = emptyList(),
    val selectedStore: String = "All",
    val isLoading: Boolean = false,
    val isScrapedData: Boolean = false,
    val searchError: String? = null
) {
    val filteredProducts: List<Product>
        get() = if (selectedStore == "All") products else products.filter {
            it.displayStore.equals(selectedStore, ignoreCase = true)
        }
}

class HomeViewModel(
    private val searchProducts: suspend (String) -> Result<List<Product>> = ProductRepository::searchAll
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState = _uiState.asStateFlow()
    val trackedProducts = TrackingRepository.trackedProducts

    private val messageChannel = Channel<String>(Channel.BUFFERED)
    val messages = messageChannel.receiveAsFlow()
    private var searchJob: Job? = null

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun selectStore(store: String) {
        _uiState.update { it.copy(selectedStore = store) }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _uiState.value = HomeUiState()
    }

    fun search() {
        searchJob?.cancel()
        val term = _uiState.value.searchQuery.trim()
        if (term.isBlank()) {
            _uiState.update {
                it.copy(products = emptyList(), isLoading = false, isScrapedData = false, searchError = null)
            }
            return
        }
        _uiState.update { it.copy(isLoading = true, searchError = null) }
        searchJob = viewModelScope.launch {
            try {
                val products = searchProducts(term).getOrThrow().distinctBy { it.id }
                ensureActive()
                _uiState.update {
                    it.copy(
                        products = products,
                        isLoading = false,
                        isScrapedData = products.isNotEmpty(),
                        searchError = if (products.isEmpty()) "No products found for '$term'" else null
                    )
                }
                messageChannel.trySend(
                    if (products.isEmpty()) "No live results found for '$term'"
                    else "Found ${products.size} live results from Flipkart, Amazon & Myntra"
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                ensureActive()
                _uiState.update {
                    it.copy(
                        products = emptyList(),
                        isLoading = false,
                        isScrapedData = false,
                        searchError = "Search error: ${error.localizedMessage ?: "Failed to connect"}"
                    )
                }
                messageChannel.trySend("Error fetching search results")
            }
        }
    }

    fun toggleTracking(product: Product) {
        if (TrackingRepository.isTracked(product.id)) {
            TrackingRepository.removeTrackedProduct(product.id)
            messageChannel.trySend("Removed from price tracking")
        } else {
            TrackingRepository.addTrackedProduct(product)
            messageChannel.trySend("Added '${product.title}' to price tracking!")
        }
    }
}