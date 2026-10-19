package dev.sumanth.goodbuy.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.FilterChip
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.sumanth.goodbuy.model.Product
import dev.sumanth.goodbuy.repository.ProductRepository
import dev.sumanth.goodbuy.repository.TrackingRepository
import dev.sumanth.goodbuy.ui.components.ProductCard
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    var searchQuery by remember { mutableStateOf("") }
    var rawProductList by remember { mutableStateOf<List<Product>>(emptyList()) }
    var selectedStore by remember { mutableStateOf("All") }
    var isLoading by remember { mutableStateOf(false) }
    var isScrapedData by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }

    val trackedProducts by TrackingRepository.trackedProducts.collectAsState()

    val stores = listOf("All", "Amazon", "Flipkart", "Myntra")

    val filteredProductList = remember(rawProductList, selectedStore) {
        when (selectedStore) {
            "Amazon" -> rawProductList.filter { it.displayStore.equals("Amazon", ignoreCase = true) }
            "Flipkart" -> rawProductList.filter { it.displayStore.equals("Flipkart", ignoreCase = true) }
            "Myntra" -> rawProductList.filter { it.displayStore.equals("Myntra", ignoreCase = true) }
            else -> rawProductList
        }
    }

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    // Helper to perform live Flipkart and Amazon scraping
    val performSearch: (String) -> Unit = { queryTerm ->
        focusManager.clearFocus()
        val term = queryTerm.trim()
        if (term.isBlank()) {
            rawProductList = emptyList()
            isScrapedData = false
            searchError = null
        } else {
            isLoading = true
            searchError = null
            scope.launch {
                val result = ProductRepository.searchAll(term)
                isLoading = false
                result.onSuccess { items ->
                    val distinctItems = items.distinctBy { it.id }
                    if (distinctItems.isNotEmpty()) {
                        rawProductList = distinctItems
                        isScrapedData = true
                        snackbarHostState.showSnackbar("Found ${distinctItems.size} live results from Flipkart, Amazon & Myntra")
                    } else {
                        rawProductList = emptyList()
                        searchError = "No products found for '$term'"
                        snackbarHostState.showSnackbar("No live results found for '$term'")
                    }
                }.onFailure { err ->
                    rawProductList = emptyList()
                    searchError = "Search error: ${err.localizedMessage ?: "Failed to connect"}"
                    snackbarHostState.showSnackbar("Error fetching search results")
                }
            }
        }
    }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Search Bar & Search Button on Top
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        text = "Search Flipkart, Amazon & Myntra...",
                        style = MaterialTheme.typography.body2
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search Icon"
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = {
                            searchQuery = ""
                            rawProductList = emptyList()
                            selectedStore = "All"
                            isScrapedData = false
                            searchError = null
                        }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear search"
                            )
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { performSearch(searchQuery) }),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = MaterialTheme.colors.primary,
                    unfocusedBorderColor = MaterialTheme.colors.onSurface.copy(alpha = 0.38f)
                )
            )

            Spacer(modifier = Modifier.width(10.dp))

            // Search Icon Button next to search bar
            Surface(
                onClick = { performSearch(searchQuery) },
                modifier = Modifier.size(52.dp),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colors.primary,
                contentColor = MaterialTheme.colors.onPrimary
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search Flipkart, Amazon & Myntra"
                    )
                }
            }
        }

        // Store Filter Chips Row (Under Search Bar)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            stores.forEach { store ->
                FilterChip(
                    selected = selectedStore == store,
                    onClick = { selectedStore = store },
                ) { Text(store) }
            }
        }

        // Loading Progress Bar
        AnimatedVisibility(visible = isLoading) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Scraping live products from Flipkart, Amazon & Myntra...",
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.primary
                )
            }
        }

        // Results Header Info Bar (Only shown when rawProductList is loaded)
        if (rawProductList.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${filteredProductList.size} of ${rawProductList.size} Products",
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                        fontWeight = FontWeight.Medium
                    )

                    if (isScrapedData) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = MaterialTheme.colors.secondary.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudDownload,
                                    contentDescription = "Flipkart, Amazon & Myntra Live",
                                    tint = MaterialTheme.colors.onSurface,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Flipkart, Amazon & Myntra Live",
                                    style = MaterialTheme.typography.caption,
                                    color = MaterialTheme.colors.onSurface,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                TextButton(
                    onClick = {
                        searchQuery = ""
                        rawProductList = emptyList()
                        selectedStore = "All"
                        isScrapedData = false
                        searchError = null
                    }
                ) {
                    Text(
                        text = "Clear Results",
                        style = MaterialTheme.typography.caption
                    )
                }
            }
        }

        // Product Grid or Search Prompt / Empty State
        if (rawProductList.isEmpty() && !isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (searchError != null) Icons.Default.SearchOff else Icons.Default.Search,
                        contentDescription = "Search Products",
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colors.primary.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (searchError != null) "No results found" else "Search for products",
                        style = MaterialTheme.typography.subtitle1,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colors.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = searchError
                            ?: "Enter product name above (e.g. 'laptop', 'shirt', 'headphones') to scrape live results from Flipkart, Amazon & Myntra.",
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else if (filteredProductList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No products found for store '$selectedStore'",
                    style = MaterialTheme.typography.subtitle1,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(
                    items = filteredProductList,
                    key = { it.id }
                ) { product ->
                    val isTracked = trackedProducts.any { it.id == product.id }

                    ProductCard(
                        product = product,
                        isTracked = isTracked,
                        onProductClick = { clickedProduct ->
                            if (clickedProduct.link.isNotBlank()) {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(clickedProduct.link))
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar(clickedProduct.title)
                                    }
                                }
                            } else {
                                scope.launch {
                                    snackbarHostState.showSnackbar(clickedProduct.title)
                                }
                            }
                        },
                        onTrackClick = { productToTrack ->
                            if (isTracked) {
                                TrackingRepository.removeTrackedProduct(productToTrack.id)
                                scope.launch {
                                    snackbarHostState.showSnackbar("Removed from price tracking")
                                }
                            } else {
                                TrackingRepository.addTrackedProduct(productToTrack)
                                scope.launch {
                                    snackbarHostState.showSnackbar("Added '${productToTrack.title}' to price tracking!")
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}
