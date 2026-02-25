package com.nefeshcore.whisperclick.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val ColorScheme = darkColorScheme(
    primary = Orange,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = OrangeLight,
    onPrimaryContainer = androidx.compose.ui.graphics.Color.White,
    secondary = OnSurfaceVariant,
    onSecondary = androidx.compose.ui.graphics.Color.White,
    secondaryContainer = SurfaceVariant,
    onSecondaryContainer = OnSurface,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    outline = Outline,
    outlineVariant = Outline,
)

@Composable
fun KaiboardTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ColorScheme, typography = Typography, content = content
    )
}
