package com.nefeshcore.whisperclick.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Orange,
    onPrimary = Color.White,
    primaryContainer = OrangeMuted,
    onPrimaryContainer = OrangeLight,
    secondaryContainer = SurfaceBright,
    onSecondaryContainer = OnSurface,
    surface = Surface,
    surfaceVariant = SurfaceVariant,
    onSurface = OnSurface,
    onSurfaceVariant = OnSurfaceVariant,
    outline = Outline,
    outlineVariant = Outline,
    background = SurfaceDim,
    onBackground = OnSurface,
)

private val OledColorScheme = darkColorScheme(
    primary = Orange,
    onPrimary = Color.White,
    primaryContainer = OrangeMuted,
    onPrimaryContainer = OrangeLight,
    secondaryContainer = Color(0xFF1A1A1A),
    onSecondaryContainer = OnSurface,
    surface = Color.Black,
    surfaceVariant = Color(0xFF121212),
    onSurface = OnSurface,
    onSurfaceVariant = OnSurfaceVariant,
    outline = Color(0xFF2A2A2A),
    outlineVariant = Color(0xFF2A2A2A),
    background = Color.Black,
    onBackground = OnSurface,
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFFC4603E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBCF),
    onPrimaryContainer = Color(0xFF3A0B00),
    secondaryContainer = Color(0xFFE8E0DD),
    onSecondaryContainer = Color(0xFF2B2220),
    surface = Color(0xFFFFFBFF),
    surfaceVariant = Color(0xFFF5DED6),
    onSurface = Color(0xFF201A18),
    onSurfaceVariant = Color(0xFF53433E),
    outline = Color(0xFF85736D),
    outlineVariant = Color(0xFFD8C2BA),
    background = Color(0xFFFFFBFF),
    onBackground = Color(0xFF201A18),
)

@Composable
fun WhisperClickTheme(themeMode: String = "dark", content: @Composable () -> Unit) {
    val colorScheme = when (themeMode) {
        "light" -> LightColorScheme
        "oled" -> OledColorScheme
        else -> DarkColorScheme
    }
    MaterialTheme(
        colorScheme = colorScheme, typography = Typography, content = content
    )
}
