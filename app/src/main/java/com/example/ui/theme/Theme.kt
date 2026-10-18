package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = CrimsonRed,
    onPrimary = Color.White,
    primaryContainer = DarkRedContainer,
    onPrimaryContainer = Color(0xFFFFCDD2),
    secondary = DarkCrimson,
    onSecondary = Color.White,
    secondaryContainer = DarkRedContainerHigh,
    onSecondaryContainer = Color(0xFFFF8A80),
    tertiary = NeonRedGlow,
    onTertiary = Color.Black,
    background = DarkBackground,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = DarkRedBorder,
    outlineVariant = DarkSurfaceBorder,
    error = CoralAlert,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Sleek Pitch-Black & Dark Red Logo Theme
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
