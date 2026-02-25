package com.nefeshcore.whisperclick

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
            val themeMode by remember { mutableStateOf(prefs.getString("theme_mode", "dark") ?: "dark") }
            KaiboardTheme(themeMode = themeMode) {
                MainScreen(viewModel)
            }
        }
    }
}