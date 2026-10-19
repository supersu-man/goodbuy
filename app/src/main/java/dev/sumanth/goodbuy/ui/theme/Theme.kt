package dev.sumanth.goodbuy.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Shapes
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColors(
  primary = Color(0xFFAB81FF),
  primaryVariant = Color(0xFF6740A8),
  secondary = Color(0xFF286A60),
  secondaryVariant = Color(0xFF1C5048),
  background = Color(0xFFFAFAFA),
  surface = Color.White,
  error = Color(0xFFB00020),
  onPrimary = Color(0xFF211333),
  onSecondary = Color.White,
  onBackground = Color(0xFF202124),
  onSurface = Color(0xFF202124),
  onError = Color.White
)

private val AppShapes = Shapes(
  small = RoundedCornerShape(4.dp),
  medium = RoundedCornerShape(8.dp),
  large = RoundedCornerShape(8.dp)
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
  MaterialTheme(
    colors = LightColors,
    typography = AppTypography,
    shapes = AppShapes,
    content = content
  )
}