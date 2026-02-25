package com.nefeshcore.whisperclick.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ColorScheme = darkColorScheme(
    // Primary — record button, switches, sliders, active chips
    primary = Orange,
    onPrimary = Color.White,
    primaryContainer = OrangeMuted,
    onPrimaryContainer = OrangeLight,

    // Secondary — tonal buttons (backspace, enter)
    secondaryContainer = SurfaceBright,
    onSecondaryContainer = OnSurface,

    // Surface — backgrounds, cards, list items
    surface = Surface,
    surfaceDim = SurfaceDim,
    surfaceVariant = SurfaceVariant,
    surfaceBright = SurfaceBright,
    onSurface = OnSurface,
    onSurfaceVariant = OnSurfaceVariant,

    // Outline — outlined buttons, dividers, text field borders
    outline = Outline,
    outlineVariant = Outline,

    // TopAppBar, Scaffold background
    background = SurfaceDim,
    onBackground = OnSurface,
)

@Composable
fun KaiboardTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ColorScheme, typography = Typography, content = content
    )
}
