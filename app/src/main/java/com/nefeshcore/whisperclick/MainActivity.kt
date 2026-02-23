package com.nefeshcore.whisperclick

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.nefeshcore.whisperclick.ui.main.MainScreen
import com.nefeshcore.whisperclick.ui.main.MainScreenViewModel
import com.nefeshcore.whisperclick.ui.theme.KaiboardTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainScreenViewModel by viewModels { MainScreenViewModel.factory() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KaiboardTheme {
                MainScreen(viewModel)
            }
        }
    }
}