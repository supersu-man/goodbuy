package dev.sumanth.goodbuy

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.Colors
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import dev.sumanth.goodbuy.repository.ProductRepository
import dev.sumanth.goodbuy.ui.screens.HomeScreen
import dev.sumanth.goodbuy.ui.theme.AppTheme
import dev.sumanth.goodbuy.ui.viewmodel.HomeViewModel
import dev.sumanth.goodbuy.ui.viewmodel.TrackingViewModel

import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()
    private val testViewModels = ViewModelStore()

    @After
    fun clearViewModels() {
        composeRule.runOnIdle { testViewModels.clear() }
    }

    @Test
    fun appThemeUsesLightPalette() {
        assertLightTheme(Configuration.UI_MODE_NIGHT_NO)
    }

    @Test
    fun appThemeStaysLightInSystemDarkMode() {
        assertLightTheme(Configuration.UI_MODE_NIGHT_YES)
    }

    @Test
    fun homeSearchSurvivesTabSwitch() {
        val showHome = mutableStateOf(true)
        val products = List(30) { index ->
            ProductRepository.sampleProducts.first().copy(
                id = "amazon-$index",
                title = "Headphones $index",
                imageUrl = ""
            )
        }
        val homeViewModel = HomeViewModel { Result.success(products) }
        testViewModels.put("home", homeViewModel)
        lateinit var gridState: LazyGridState
        composeRule.setContent {
            gridState = rememberLazyGridState()
            AppTheme {
                AnimatedContent(targetState = showHome.value, label = "TestTabTransition") { home ->
                    if (home) HomeScreen(homeViewModel = homeViewModel, gridState = gridState)
                }
            }
        }

        composeRule.onNodeWithText("Search Flipkart, Amazon & Myntra...")
            .performTextInput("headphones")
        composeRule.onNodeWithContentDescription("Search Flipkart, Amazon & Myntra").performClick()
        composeRule.onNodeWithText("30 of 30 Products").assertExists()
        val amazonFilter = hasText("Amazon") and
            SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)
        composeRule.onNode(amazonFilter).performClick()
        composeRule.onNode(hasScrollToIndexAction()).performScrollToIndex(20)

        var scrollIndex = 0
        var scrollOffset = 0
        composeRule.runOnIdle {
            scrollIndex = gridState.firstVisibleItemIndex
            scrollOffset = gridState.firstVisibleItemScrollOffset
            assertTrue(scrollIndex > 0)
            showHome.value = false
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle { showHome.value = true }

        composeRule.onNodeWithText("headphones").assertExists()
        composeRule.onNode(amazonFilter).assertIsSelected()
        composeRule.onNodeWithText("30 of 30 Products").assertExists()
        composeRule.onNodeWithText("Flipkart, Amazon & Myntra Live").assertExists()
        composeRule.runOnIdle {
            assertEquals(scrollIndex, gridState.firstVisibleItemIndex)
            assertEquals(scrollOffset, gridState.firstVisibleItemScrollOffset)
        }
    }

    @Test
    fun screenViewModelsSurviveActivityRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var homeViewModel: HomeViewModel
            lateinit var trackingViewModel: TrackingViewModel
            scenario.onActivity { activity ->
                homeViewModel = ViewModelProvider(activity)[HomeViewModel::class.java]
                trackingViewModel = ViewModelProvider(activity)[TrackingViewModel::class.java]
                homeViewModel.updateSearchQuery("headphones")
                homeViewModel.selectStore("Amazon")
                trackingViewModel.toggleProductSelection("selected-product")
            }

            scenario.recreate()

            scenario.onActivity { activity ->
                assertSame(homeViewModel, ViewModelProvider(activity)[HomeViewModel::class.java])
                assertSame(trackingViewModel, ViewModelProvider(activity)[TrackingViewModel::class.java])
                assertEquals("headphones", homeViewModel.uiState.value.searchQuery)
                assertEquals("Amazon", homeViewModel.uiState.value.selectedStore)
                assertTrue(trackingViewModel.uiState.value.isSelectionMode)
                assertEquals(setOf("selected-product"), trackingViewModel.uiState.value.selectedProductIds)
            }
        }
    }

    private fun assertLightTheme(nightMode: Int) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val configuration = Configuration(context.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightMode
        }
        lateinit var colors: Colors

        composeRule.setContent {
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                AppTheme {
                    colors = MaterialTheme.colors
                }
            }
        }

        composeRule.runOnIdle {
            assertTrue(colors.isLight)
            assertEquals(Color(0xFFAB81FF), colors.primary)
            assertEquals(Color(0xFF211333), colors.onPrimary)
            assertEquals(Color(0xFFFAFAFA), colors.background)
            assertEquals(Color.White, colors.surface)
        }
    }

    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("dev.sumanth.goodbuy", appContext.packageName)
    }
}