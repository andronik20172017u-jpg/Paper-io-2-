package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val CyberDarkColorScheme =
  darkColorScheme(
    primary = ElectricCyan,
    onPrimary = DeepSpace,
    secondary = NeonCrimson,
    onSecondary = TextPrimary,
    tertiary = SolarAmber,
    background = DeepSpace,
    onBackground = TextPrimary,
    surface = CyberSlate,
    onSurface = TextPrimary,
    surfaceVariant = CyberSurface,
    onSurfaceVariant = TextMuted,
  )

@Composable
fun MyApplicationTheme(
  content: @Composable () -> Unit,
) {
  MaterialTheme(
    colorScheme = CyberDarkColorScheme,
    typography = Typography,
    content = content,
  )
}
