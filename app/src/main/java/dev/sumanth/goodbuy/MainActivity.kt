package dev.sumanth.goodbuy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.AlertDialog
import androidx.compose.material.Badge
import androidx.compose.material.BadgedBox
import androidx.compose.material.Button
import androidx.compose.material.TopAppBar
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.BottomNavigation
import androidx.compose.material.BottomNavigationItem
import androidx.compose.material.Scaffold
import androidx.compose.material.SnackbarHost
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.sumanth.goodbuy.repository.TrackingRepository
import dev.sumanth.goodbuy.ui.screens.HomeScreen
import dev.sumanth.goodbuy.ui.screens.TrackingScreen
import dev.sumanth.goodbuy.ui.theme.AppTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TrackingRepository.init(applicationContext)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        )
        setContent {
            AppTheme {
                MainApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun MainApp() {
    var selectedTab by remember { mutableIntStateOf(0) }
    val trackedProducts by TrackingRepository.trackedProducts.collectAsState()
    val trackedGroups by TrackingRepository.trackedGroups.collectAsState()

    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedProductIds by remember { mutableStateOf(setOf<String>()) }
    var selectedGroupIds by remember { mutableStateOf(setOf<String>()) }

    var showClearAllDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(selectedTab) {
        if (selectedTab == 1) {
            val updatedCount = TrackingRepository.checkAndRefreshTrackedPrices(minIntervalMillis = 30 * 60 * 1000L)
            if (updatedCount > 0) {
                snackbarHostState.showSnackbar("Updated prices for $updatedCount products!")
            }
        }
    }

    val totalSelectedCount = selectedProductIds.size + selectedGroupIds.size

    val exitSelectionMode = {
        isSelectionMode = false
        selectedProductIds = emptySet()
        selectedGroupIds = emptySet()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().safeDrawingPadding(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (selectedTab == 0) {
                // Common Top Bar for HomeScreen (Search)
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.ShoppingBag,
                                contentDescription = "GoodBuy Logo",
                                tint = MaterialTheme.colors.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "GoodBuy",
                                style = MaterialTheme.typography.h6.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colors.onSurface
                            )
                        }
                    },
                    backgroundColor = MaterialTheme.colors.surface,
                    contentColor = MaterialTheme.colors.onSurface
                )
            } else {
                // Common Top Bar for TrackingScreen
                if (isSelectionMode) {
                    TopAppBar(
                        title = {
                            Text(
                                text = "$totalSelectedCount Selected to Group",
                                style = MaterialTheme.typography.subtitle1,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = { exitSelectionMode() }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Cancel selection"
                                )
                            }
                        },
                        actions = {
                            Button(
                                onClick = {
                                    if (totalSelectedCount >= 2) {
                                        val newGroup = TrackingRepository.groupProducts(
                                            selectedProductIds.toList(),
                                            selectedGroupIds.toList()
                                        )
                                        exitSelectionMode()
                                        if (newGroup != null) {
                                            scope.launch {
                                                snackbarHostState.showSnackbar("Products grouped together successfully!")
                                            }
                                        }
                                    } else {
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Select at least 2 items to group them")
                                        }
                                    }
                                },
                                enabled = totalSelectedCount >= 2,
                                modifier = Modifier.padding(end = 8.dp),
                                shape = MaterialTheme.shapes.small
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Layers,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Group")
                            }
                        },
                        backgroundColor = MaterialTheme.colors.primary,
                        contentColor = MaterialTheme.colors.onPrimary
                    )
                } else {
                    TopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.NotificationsActive,
                                    contentDescription = "Price Tracker",
                                    tint = MaterialTheme.colors.primary,
                                    modifier = Modifier.size(26.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Price Tracker",
                                    style = MaterialTheme.typography.h6.copy(
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        },
                        actions = {
                            if (trackedProducts.isNotEmpty() || trackedGroups.isNotEmpty()) {
                                IconButton(onClick = { showClearAllDialog = true }) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Clear all tracked items",
                                        tint = MaterialTheme.colors.error
                                    )
                                }
                            }
                        },
                        backgroundColor = MaterialTheme.colors.surface,
                        contentColor = MaterialTheme.colors.onSurface
                    )
                }
            }
        },
        bottomBar = {
            BottomNavigation(
                backgroundColor = MaterialTheme.colors.surface,
                contentColor = MaterialTheme.colors.onSurface
            ) {
                BottomNavigationItem(
                    selectedContentColor = MaterialTheme.colors.primaryVariant,
                    unselectedContentColor = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                    selected = selectedTab == 0,
                    onClick = {
                        if (isSelectionMode) exitSelectionMode()
                        selectedTab = 0
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search"
                        )
                    },
                    label = { Text("Search") }
                )

                BottomNavigationItem(
                    selectedContentColor = MaterialTheme.colors.primaryVariant,
                    unselectedContentColor = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                    selected = selectedTab == 1,
                    onClick = {
                        selectedTab = 1
                    },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (trackedProducts.isNotEmpty()) {
                                    Badge {
                                        Text("${trackedProducts.size}")
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = "Price Tracker"
                            )
                        }
                    },
                    label = { Text("Price Tracker") }
                )
            }
        }
    ) { innerPadding ->
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = {
                if (targetState > initialState) {
                    (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                        slideOutHorizontally { width -> -width } + fadeOut()
                    )
                } else {
                    (slideInHorizontally { width -> -width } + fadeIn()).togetherWith(
                        slideOutHorizontally { width -> width } + fadeOut()
                    )
                }
            },
            label = "ScreenTransition",
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) { tab ->
            when (tab) {
                0 -> HomeScreen(
                    snackbarHostState = snackbarHostState,
                    modifier = Modifier.fillMaxSize()
                )
                1 -> TrackingScreen(
                    isSelectionMode = isSelectionMode,
                    selectedProductIds = selectedProductIds,
                    selectedGroupIds = selectedGroupIds,
                    onSelectionModeChange = { isSelectionMode = it },
                    onSelectedProductIdsChange = { selectedProductIds = it },
                    onSelectedGroupIdsChange = { selectedGroupIds = it },
                    snackbarHostState = snackbarHostState,
                    onNavigateToSearch = { selectedTab = 0 },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Clear All Confirmation Dialog
        if (showClearAllDialog) {
            AlertDialog(
                onDismissRequest = { showClearAllDialog = false },
                title = {
                    Text(
                        text = "Clear All Tracked Products?",
                        style = MaterialTheme.typography.subtitle1.copy(fontWeight = FontWeight.Bold)
                    )
                },
                text = {
                    Text(
                        text = "Are you sure you want to remove all products and groups from price tracking? This action cannot be undone.",
                        style = MaterialTheme.typography.body2
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showClearAllDialog = false
                            TrackingRepository.clearAll()
                            exitSelectionMode()
                            scope.launch {
                                snackbarHostState.showSnackbar("All tracked items cleared")
                            }
                        }
                    ) {
                        Text(
                            text = "Clear All",
                            color = MaterialTheme.colors.error,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearAllDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
