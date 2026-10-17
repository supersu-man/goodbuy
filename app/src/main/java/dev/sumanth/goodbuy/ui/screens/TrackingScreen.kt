package dev.sumanth.goodbuy.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import dev.sumanth.goodbuy.model.TrackedGroup
import dev.sumanth.goodbuy.repository.TrackingRepository
import dev.sumanth.goodbuy.ui.components.TrackedGroupCard
import dev.sumanth.goodbuy.ui.components.TrackedProductCard
import dev.sumanth.goodbuy.ui.components.formatPrice
import dev.sumanth.goodbuy.ui.components.formatRelativeTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackingScreen(
    modifier: Modifier = Modifier,
    isSelectionMode: Boolean = false,
    selectedProductIds: Set<String> = emptySet(),
    selectedGroupIds: Set<String> = emptySet(),
    onSelectionModeChange: (Boolean) -> Unit = {},
    onSelectedProductIdsChange: (Set<String>) -> Unit = {},
    onSelectedGroupIdsChange: (Set<String>) -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onNavigateToSearch: () -> Unit = {}
) {
    val trackedProducts by TrackingRepository.trackedProducts.collectAsState()
    val trackedGroups by TrackingRepository.trackedGroups.collectAsState()
    val lastRefreshedTime by TrackingRepository.lastRefreshedTime.collectAsState()

    var selectedGroupForDetails by remember { mutableStateOf<TrackedGroup?>(null) }
    var isRefreshingPrices by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Auto-refetch when screen is opened and periodically if last tracked time was 30+ min ago
    LaunchedEffect(Unit) {
        while (isActive) {
            if (!isRefreshingPrices) {
                val updatedCount = TrackingRepository.checkAndRefreshTrackedPrices(minIntervalMillis = 30 * 60 * 1000L)
                if (updatedCount > 0) {
                    snackbarHostState.showSnackbar("Updated prices for $updatedCount products!")
                }
            }
            delay(60_000L) // Check every 60 seconds
        }
    }

    val ungroupedProducts = remember(trackedProducts, trackedGroups) {
        val groupedIds = trackedGroups.flatMap { it.productIds }.toSet()
        trackedProducts.filterNot { it.id in groupedIds }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshingPrices,
        onRefresh = {
            if (!isRefreshingPrices) {
                isRefreshingPrices = true
                scope.launch {
                    val updatedCount = TrackingRepository.refreshTrackedPrices()
                    isRefreshingPrices = false
                    if (updatedCount > 0) {
                        snackbarHostState.showSnackbar("Updated prices for $updatedCount products!")
                    } else {
                        snackbarHostState.showSnackbar("All tracked prices are up to date.")
                    }
                }
            }
        },
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Top Helper Hint Bar - Always visible when items exist (even during selection mode)
            if (ungroupedProducts.isNotEmpty() || trackedGroups.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isSelectionMode) {
                                    "Select 2 or more products to group them together and track the lowest price."
                                } else {
                                    "Hold (long-press) products to select and group identical items."
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            if (!isSelectionMode && lastRefreshedTime > 0L) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Last checked: ${formatRelativeTime(lastRefreshedTime)} • Auto-refreshes if >30m old",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            // Empty State if no tracked items
            if (ungroupedProducts.isEmpty() && trackedGroups.isEmpty()) {
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
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = "No Tracked Products",
                            modifier = Modifier.size(72.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No Tracked Products Yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Search for products on Flipkart & Amazon from the Search tab and tap 'Track' to compare and track prices here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = onNavigateToSearch,
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Go to Search")
                        }
                    }
                }
            } else {
                // Main Grid displaying Groups and Single Products
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    // 1. Grouped Product Cards First
                    items(
                        items = trackedGroups,
                        key = { "group_${it.id}" }
                    ) { group ->
                        val groupProducts = TrackingRepository.getProductsForGroup(group)
                        val isSelected = selectedGroupIds.contains(group.id)

                        TrackedGroupCard(
                            group = group,
                            groupedProducts = groupProducts,
                            isSelectionMode = isSelectionMode,
                            isSelected = isSelected,
                            onClick = { clickedGroup ->
                                if (isSelectionMode) {
                                    val updated = if (isSelected) selectedGroupIds - clickedGroup.id else selectedGroupIds + clickedGroup.id
                                    onSelectedGroupIdsChange(updated)
                                } else {
                                    selectedGroupForDetails = clickedGroup
                                }
                            },
                            onLongClick = { clickedGroup ->
                                if (!isSelectionMode) {
                                    onSelectionModeChange(true)
                                    onSelectedGroupIdsChange(setOf(clickedGroup.id))
                                } else {
                                    val updated = if (isSelected) selectedGroupIds - clickedGroup.id else selectedGroupIds + clickedGroup.id
                                    onSelectedGroupIdsChange(updated)
                                }
                            },
                            onUngroupClick = { groupToUngroup ->
                                TrackingRepository.ungroupGroup(groupToUngroup.id)
                                scope.launch {
                                    snackbarHostState.showSnackbar("Group split into individual tracked products")
                                }
                            }
                        )
                    }

                    // 2. Single Ungrouped Product Cards
                    items(
                        items = ungroupedProducts,
                        key = { "product_${it.id}" }
                    ) { product ->
                        val isSelected = selectedProductIds.contains(product.id)

                        TrackedProductCard(
                            product = product,
                            isSelectionMode = isSelectionMode,
                            isSelected = isSelected,
                            onClick = { clickedProduct ->
                                if (isSelectionMode) {
                                    val updated = if (isSelected) selectedProductIds - clickedProduct.id else selectedProductIds + clickedProduct.id
                                    onSelectedProductIdsChange(updated)
                                } else {
                                    if (clickedProduct.link.isNotBlank()) {
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(clickedProduct.link))
                                            context.startActivity(intent)
                                        } catch (_: Exception) {
                                            scope.launch {
                                                snackbarHostState.showSnackbar(clickedProduct.title)
                                            }
                                        }
                                    }
                                }
                            },
                            onLongClick = { clickedProduct ->
                                if (!isSelectionMode) {
                                    onSelectionModeChange(true)
                                    onSelectedProductIdsChange(setOf(clickedProduct.id))
                                } else {
                                    val updated = if (isSelected) selectedProductIds - clickedProduct.id else selectedProductIds + clickedProduct.id
                                    onSelectedProductIdsChange(updated)
                                }
                            },
                            onRemoveClick = { productToRemove ->
                                TrackingRepository.removeTrackedProduct(productToRemove.id)
                                scope.launch {
                                    snackbarHostState.showSnackbar("Removed ${productToRemove.title} from tracking")
                                }
                            }
                        )
                    }
                }
            }
        }

        // Group Details Bottom Sheet
        if (selectedGroupForDetails != null) {
            val group = selectedGroupForDetails!!
            val products = TrackingRepository.getProductsForGroup(group)
            val bestProduct = products.minByOrNull { it.newPrice }

            ModalBottomSheet(
                onDismissRequest = { selectedGroupForDetails = null },
                sheetState = sheetState,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 24.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = group.name.ifBlank { "Grouped Products" },
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Text(
                                text = "${products.size} Products Grouped",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(onClick = { selectedGroupForDetails = null }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Least Price Highlight Card
                    if (bestProduct != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.LocalOffer,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "LEAST CURRENT PRICE DEAL",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 0.5.sp
                                            ),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = formatPrice(bestProduct.newPrice, bestProduct.link),
                                        style = MaterialTheme.typography.headlineMedium.copy(
                                            fontWeight = FontWeight.ExtraBold
                                        ),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }

                                Surface(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = "Best on ${bestProduct.displayStore}",
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Bold
                                        ),
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Group Products Comparison",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(products, key = { it.id }) { item ->
                            val isLowest = bestProduct != null && item.id == bestProduct.id

                            ElevatedCard(
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(
                                        model = item.imageUrl,
                                        contentDescription = item.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                    )

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.title,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.SemiBold
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        Spacer(modifier = Modifier.height(2.dp))

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = "${item.displayStore} • ${formatRelativeTime(item.lastTrackedTime)}",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }

                                            if (isLowest) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    color = MaterialTheme.colorScheme.primaryContainer,
                                                    shape = RoundedCornerShape(6.dp)
                                                ) {
                                                    Text(
                                                        text = "Lowest Price",
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold
                                                        ),
                                                        color = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = formatPrice(item.newPrice, item.link),
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = if (isLowest) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )

                                        if (item.link.isNotBlank()) {
                                            IconButton(
                                                onClick = {
                                                    try {
                                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(item.link))
                                                        context.startActivity(intent)
                                                    } catch (_: Exception) {
                                                    }
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                                    contentDescription = "Open product link",
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                TrackingRepository.ungroupGroup(group.id)
                                selectedGroupForDetails = null
                                scope.launch {
                                    snackbarHostState.showSnackbar("Group split into single products")
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.CallSplit,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Ungroup")
                        }

                        Button(
                            onClick = { selectedGroupForDetails = null },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Text("Done")
                        }
                    }
                }
            }
        }
    }
}
