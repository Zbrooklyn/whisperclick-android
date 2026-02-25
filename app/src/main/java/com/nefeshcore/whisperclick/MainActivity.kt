package com.nefeshcore.whisperclick

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.nefeshcore.whisperclick.ui.main.MainScreen
import com.nefeshcore.whisperclick.ui.main.MainScreenViewModel
import com.nefeshcore.whisperclick.ui.theme.KaiboardTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainScreenViewModel by viewModels { MainScreenViewModel.factory() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val prefs = getSharedPreferences(
                getString(R.string.preference_file_key), Context.MODE_PRIVATE
            )
            var themeMode by remember { mutableStateOf(prefs.getString("theme_mode", "dark") ?: "dark") }

            // Live-update theme when user changes it in settings
            DisposableEffect(prefs) {
                val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                    if (key == "theme_mode") {
                        themeMode = prefs.getString("theme_mode", "dark") ?: "dark"
                    }
                }
                prefs.registerOnSharedPreferenceChangeListener(listener)
                onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
            }

            KaiboardTheme(themeMode = themeMode) {
                // Sync system bar colors with theme
                val view = LocalView.current
                val bgColor = androidx.compose.material3.MaterialTheme.colorScheme.background.toArgb()
                val isLight = themeMode == "light"
                if (!view.isInEditMode) {
                    SideEffect {
                        val window = (view.context as Activity).window
                        window.statusBarColor = bgColor
                        window.navigationBarColor = bgColor
                        WindowCompat.getInsetsController(window, view).apply {
                            isAppearanceLightStatusBars = isLight
                            isAppearanceLightNavigationBars = isLight
                        }
                    }
                }
                MainScreen(viewModel)
            }
        }
    }
}
