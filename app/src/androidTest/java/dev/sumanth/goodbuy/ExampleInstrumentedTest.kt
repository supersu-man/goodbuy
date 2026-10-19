package dev.sumanth.goodbuy

import android.content.res.Configuration
import androidx.compose.material.Colors
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sumanth.goodbuy.ui.theme.AppTheme

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

    @Test
    fun appThemeUsesLightPalette() {
        assertLightTheme(Configuration.UI_MODE_NIGHT_NO)
    }

    @Test
    fun appThemeStaysLightInSystemDarkMode() {
        assertLightTheme(Configuration.UI_MODE_NIGHT_YES)
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