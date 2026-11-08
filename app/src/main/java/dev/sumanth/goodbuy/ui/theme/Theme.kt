package dev.sumanth.goodbuy.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.example.compose.backgroundLight
import com.example.compose.errorContainerLight
import com.example.compose.errorLight
import com.example.compose.inverseOnSurfaceLight
import com.example.compose.inversePrimaryLight
import com.example.compose.inverseSurfaceLight
import com.example.compose.onBackgroundLight
import com.example.compose.onErrorContainerLight
import com.example.compose.onErrorLight
import com.example.compose.onPrimaryContainerLight
import com.example.compose.onPrimaryLight
import com.example.compose.onSecondaryContainerLight
import com.example.compose.onSecondaryLight
import com.example.compose.onSurfaceLight
import com.example.compose.onSurfaceVariantLight
import com.example.compose.onTertiaryContainerLight
import com.example.compose.onTertiaryLight
import com.example.compose.outlineLight
import com.example.compose.outlineVariantLight
import com.example.compose.primaryContainerLight
import com.example.compose.primaryLight
import com.example.compose.scrimLight
import com.example.compose.secondaryContainerLight
import com.example.compose.secondaryLight
import com.example.compose.surfaceBrightLight
import com.example.compose.surfaceContainerHighLight
import com.example.compose.surfaceContainerHighestLight
import com.example.compose.surfaceContainerLight
import com.example.compose.surfaceContainerLowLight
import com.example.compose.surfaceContainerLowestLight
import com.example.compose.surfaceDimLight
import com.example.compose.surfaceLight
import com.example.compose.surfaceVariantLight
import com.example.compose.tertiaryContainerLight
import com.example.compose.tertiaryLight

private val lightScheme = lightColorScheme(
    primary = primaryLight,
    onPrimary = onPrimaryLight,
    primaryContainer = primaryContainerLight,
    onPrimaryContainer = onPrimaryContainerLight,
    secondary = secondaryLight,
    onSecondary = onSecondaryLight,
    secondaryContainer = secondaryContainerLight,
    onSecondaryContainer = onSecondaryContainerLight,
    tertiary = tertiaryLight,
    onTertiary = onTertiaryLight,
    tertiaryContainer = tertiaryContainerLight,
    onTertiaryContainer = onTertiaryContainerLight,
    error = errorLight,
    onError = onErrorLight,
    errorContainer = errorContainerLight,
    onErrorContainer = onErrorContainerLight,
    background = backgroundLight,
    onBackground = onBackgroundLight,
    surface = surfaceLight,
    onSurface = onSurfaceLight,
    surfaceVariant = surfaceVariantLight,
    onSurfaceVariant = onSurfaceVariantLight,
    outline = outlineLight,
    outlineVariant = outlineVariantLight,
    scrim = scrimLight,
    inverseSurface = inverseSurfaceLight,
    inverseOnSurface = inverseOnSurfaceLight,
    inversePrimary = inversePrimaryLight,
    surfaceDim = surfaceDimLight,
    surfaceBright = surfaceBrightLight,
    surfaceContainerLowest = surfaceContainerLowestLight,
    surfaceContainerLow = surfaceContainerLowLight,
    surfaceContainer = surfaceContainerLight,
    surfaceContainerHigh = surfaceContainerHighLight,
    surfaceContainerHighest = surfaceContainerHighestLight,
)

@Composable
fun AppTheme(
    content: @Composable () -> Unit
) {
  MaterialTheme(
    colorScheme = lightScheme,
    typography = AppTypography,
    content = content
  )
}
