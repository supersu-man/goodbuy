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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.sumanth.goodbuy.repository.TrackingRepository
import dev.sumanth.goodbuy.ui.screens.HomeScreen
import dev.sumanth.goodbuy.ui.screens.TrackingScreen
import dev.sumanth.goodbuy.ui.theme.AppTheme
import dev.sumanth.goodbuy.ui.viewmodel.HomeViewModel
import dev.sumanth.goodbuy.ui.viewmodel.TrackingViewModel
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
fun MainApp(
    homeViewModel: HomeViewModel = viewModel(),
    trackingViewModel: TrackingViewModel = viewModel()
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val trackingState by trackingViewModel.uiState.collectAsStateWithLifecycle()
    val trackedProducts = trackingState.trackedProducts
    val trackedGroups = trackingState.trackedGroups
    val isSelectionMode = trackingState.isSelectionMode

    var showClearAllDialog by rememberSaveable { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val homeGridState = rememberLazyGridState()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(homeViewModel, trackingViewModel, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            launch {
                homeViewModel.messages.collect { snackbarHostState.showSnackbar(it) }
            }
            launch {
                trackingViewModel.messages.collect { snackbarHostState.showSnackbar(it) }
            }
        }
    }

    LaunchedEffect(selectedTab, trackingViewModel, lifecycleOwner) {
        if (selectedTab == 1) {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                trackingViewModel.monitorPrices()
            }
        }
    }

    val totalSelectedCount = trackingState.totalSelectedCount
    val exitSelectionMode = trackingViewModel::exitSelectionMode

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
                                onClick = trackingViewModel::groupSelected,
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
                    homeViewModel = homeViewModel,
                    gridState = homeGridState,
                    modifier = Modifier.fillMaxSize()
                )
                1 -> TrackingScreen(
                    trackingViewModel = trackingViewModel,
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
                            trackingViewModel.clearAll()
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
