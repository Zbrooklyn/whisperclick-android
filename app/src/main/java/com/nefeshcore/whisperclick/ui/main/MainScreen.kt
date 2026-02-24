package com.nefeshcore.whisperclick.ui.main

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.NavigateNext
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Mood
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.nefeshcore.whisperclick.R
import com.nefeshcore.whisperclick.utils.AppLog

@Composable
fun MainScreen(viewModel: MainScreenViewModel) {
    MainScreen(
        canTranscribe = viewModel.canTranscribe,
        isRecording = viewModel.isRecording,
        messageLog = viewModel.dataLog,
        onBenchmarkTapped = viewModel::benchmark,
        onRecordTapped = viewModel::toggleRecord
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(
    canTranscribe: Boolean,
    isRecording: Boolean,
    messageLog: String,
    onBenchmarkTapped: () -> Unit,
    onRecordTapped: () -> Unit
) {
    val sharedPref = LocalContext.current.getSharedPreferences(
        stringResource(R.string.preference_file_key), Context.MODE_PRIVATE
    )
    val maxThreads = Runtime.getRuntime().availableProcessors()
    val threadsStr = stringResource(R.string.num_threads)
    val casualMode = stringResource(R.string.casual_mode)
    val pauseMediaStr = stringResource(R.string.pause_media)
    var nThreads by remember {
        mutableFloatStateOf(
            sharedPref.getInt(threadsStr, maxThreads).toFloat()
        )
    }
    var lowercase by remember {
        mutableStateOf(
            sharedPref.getBoolean(casualMode, false)
        )
    }
    var pauseMedia by remember {
        mutableStateOf(
            sharedPref.getBoolean(pauseMediaStr, false)
        )
    }
    
    var apiKey by remember {
        mutableStateOf(
            sharedPref.getString("gemini_api_key", "") ?: ""
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Text(
                    stringResource(R.string.settings),
                    fontSize = 30.sp, fontWeight = FontWeight.Bold
                )
            })
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(innerPadding)
        ) {
            item { SectionHeader(stringResource(R.string.setup_header)) }
            item { InputMethodButton() }
            item { PermissionButton() }
            item { KeyboardPickerItem(sharedPref) }
            item { MoreButton() }
            item { SectionHeader(stringResource(R.string.advanced_header), bp = 4.dp) }
            
            // API Key Input
            item {
                ListItem(
                    headlineContent = { Text("Gemini API Key") },
                    leadingContent = { Icon(Icons.Outlined.Key, null) },
                    supportingContent = {
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { 
                                apiKey = it
                                with(sharedPref.edit()) {
                                    putString("gemini_api_key", it)
                                    apply()
                                }
                            },
                            label = { Text("Enter API Key") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        )
                    }
                )
            }

            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.num_threads_option)) },
                    leadingContent = { Icon(Icons.Outlined.Memory, null) },
                    supportingContent = {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Slider(
                                value = nThreads,
                                onValueChange = { nThreads = it },
                                onValueChangeFinished = {
                                    with(sharedPref.edit()) {
                                        putInt(threadsStr, nThreads.toInt())
                                        apply()
                                    }
                                },
                                // subtract 2 bc the start and end count as steps
                                steps = maxThreads - 2,
                                valueRange = 1f..maxThreads.toFloat(),
                                modifier = Modifier.weight(1f, true)
                            )
                            Text(
                                nThreads.toInt().toString(),
                                modifier = Modifier.padding(4.dp),
                                fontSize = 16.sp,
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(0.dp),
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.pause_media_option)) },
                    leadingContent = { Icon(Icons.Outlined.Pause, null) },
                    supportingContent = {
                        Text(stringResource(R.string.pause_media_description))
                    },
                    trailingContent = {
                        Switch(checked = pauseMedia, onCheckedChange = {
                            pauseMedia = it
                            with(sharedPref.edit()) {
                                putBoolean(pauseMediaStr, it)
                                apply()
                            }
                        })
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.casual_option)) },
                    leadingContent = { Icon(Icons.Outlined.Mood, null) },
                    supportingContent = {
                        Text(stringResource(R.string.casual_description))
                    },
                    trailingContent = {
                        Switch(checked = lowercase, onCheckedChange = {
                            lowercase = it
                            with(sharedPref.edit()) {
                                putBoolean(casualMode, it)
                                apply()
                            }
                        })
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                BenchmarkButton(enabled = canTranscribe, onClick = onBenchmarkTapped)
            }
            item {
                RecordButton(
                    enabled = canTranscribe, isRecording = isRecording, onClick = onRecordTapped
                )
            }
            item { AppLogSection() }
        }
    }
}

@Composable
private fun SectionHeader(header: String, tp: Dp = 16.dp, bp: Dp = 0.dp) {
    Text(
        header,
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = tp, bottom = bp, start = 16.dp, end = 0.dp)
    )
}


@Composable
private fun AppLogSection() {
    val logText by AppLog.log.collectAsState()
    val context = LocalContext.current

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Log", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Row {
            IconButton(onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("WhisperClick Log", logText))
                Toast.makeText(context, "Log copied", Toast.LENGTH_SHORT).show()
            }) {
                Icon(Icons.Outlined.ContentCopy, "Copy log")
            }
            IconButton(onClick = { AppLog.clear() }) {
                Icon(Icons.Outlined.Delete, "Clear log")
            }
        }
    }
    SelectionContainer(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            text = logText.ifEmpty { "(no log entries yet)" },
            fontSize = 12.sp,
            lineHeight = 16.sp
        )
    }
}

@Composable
private fun InputMethodButton() {
    val context = LocalContext.current
    ListItem(
        headlineContent = { Text(stringResource(R.string.input_method_button)) },
        leadingContent = {
            Icon(
                Icons.Outlined.Keyboard, null
            )
        },
        supportingContent = { Text(stringResource(R.string.input_method_description)) },
        trailingContent = {
            Icon(
                Icons.AutoMirrored.Outlined.NavigateNext,
                stringResource(R.string.input_method_button)
            )
        },
        modifier = Modifier.clickable(onClick = {
            context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        })
    )
}

@Composable
private fun MoreButton() {
    val uriHandler = LocalUriHandler.current
    ListItem(
        headlineContent = { Text(stringResource(R.string.more_button)) },
        supportingContent = { Text(stringResource(R.string.support_text)) },
        trailingContent = {
            Icon(
                Icons.AutoMirrored.Outlined.NavigateNext, null
            )
        },
        leadingContent = { Icon(Icons.Outlined.Star, null) },
        modifier = Modifier.clickable(onClick = { uriHandler.openUri("https://github.com/Zbrooklyn/whisperclick-android") })
    )
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun PermissionButton() {
    val micPermissionState = rememberPermissionState(
        permission = android.Manifest.permission.RECORD_AUDIO
    )
    ListItem(
        headlineContent = { Text(stringResource(R.string.permission_button)) },
        leadingContent = {
            Icon(
                Icons.Outlined.Mic, null
            )
        },
        supportingContent = {
            if (micPermissionState.status.isGranted) {
                Text(stringResource(R.string.permission_granted))
            }
        },
        trailingContent = {
            Icon(
                Icons.AutoMirrored.Outlined.NavigateNext,
                stringResource(R.string.input_method_button)
            )
        },
        modifier = Modifier.clickable(onClick = {
            if (!micPermissionState.status.isGranted) {
                micPermissionState.launchPermissionRequest()
            }
        })
    )
}

@Composable
private fun KeyboardPickerItem(sharedPref: android.content.SharedPreferences) {
    val context = LocalContext.current
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    val enabledImes = imm.enabledInputMethodList
    val ownPackage = context.packageName

    // Filter out WhisperClick itself
    val otherImes = enabledImes.filter { it.packageName != ownPackage }

    var selectedId by remember {
        mutableStateOf(sharedPref.getString("preferred_keyboard", "") ?: "")
    }
    var expanded by remember { mutableStateOf(false) }

    val selectedLabel = otherImes.find { it.id == selectedId }
        ?.loadLabel(context.packageManager)?.toString() ?: "Auto (next keyboard)"

    ListItem(
        headlineContent = { Text("Switch-to Keyboard") },
        leadingContent = { Icon(Icons.Outlined.Keyboard, null) },
        supportingContent = { Text(selectedLabel) },
        trailingContent = {
            Icon(Icons.AutoMirrored.Outlined.NavigateNext, null)
        },
        modifier = Modifier.clickable { expanded = true }
    )

    if (expanded) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { expanded = false },
            title = { Text("Choose keyboard") },
            text = {
                androidx.compose.foundation.lazy.LazyColumn {
                    item {
                        ListItem(
                            headlineContent = { Text("Auto (next keyboard)") },
                            modifier = Modifier.clickable {
                                selectedId = ""
                                with(sharedPref.edit()) {
                                    putString("preferred_keyboard", "")
                                    apply()
                                }
                                expanded = false
                            }
                        )
                    }
                    items(otherImes.size) { index ->
                        val ime = otherImes[index]
                        val label = ime.loadLabel(context.packageManager).toString()
                        ListItem(
                            headlineContent = { Text(label) },
                            modifier = Modifier.clickable {
                                selectedId = ime.id
                                with(sharedPref.edit()) {
                                    putString("preferred_keyboard", ime.id)
                                    apply()
                                }
                                expanded = false
                            }
                        )
                    }
                }
            },
            confirmButton = {}
        )
    }
}

@Composable
private fun BenchmarkButton(enabled: Boolean, onClick: () -> Unit) {
    ListItem(
        leadingContent = {
            Icon(
                Icons.Outlined.Speed, stringResource(R.string.start_recording)
            )
        },
        headlineContent = { Text(stringResource(R.string.benchmark)) },
        supportingContent = { Text(stringResource(R.string.benchmark_description)) },
        modifier = Modifier.clickable(onClick = onClick, enabled = enabled)
    )
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun RecordButton(enabled: Boolean, isRecording: Boolean, onClick: () -> Unit) {
    val micPermissionState = rememberPermissionState(
        permission = android.Manifest.permission.RECORD_AUDIO,
        onPermissionResult = { granted ->
            if (granted) {
                onClick()
            }
        })
    ListItem(
        leadingContent = {
            Icon(
                if (isRecording) {
                    Icons.Outlined.Stop
                } else {
                    Icons.Outlined.PlayArrow
                }, stringResource(R.string.start_recording)
            )
        },
        headlineContent = {
            Text(
                if (isRecording) {
                    stringResource(R.string.stop_test)
                } else {
                    stringResource(R.string.start_test)
                }
            )
        },
        supportingContent = { Text(stringResource(R.string.test_description)) },
        modifier = Modifier.clickable(onClick = {
            if (micPermissionState.status.isGranted) {
                onClick()
            } else {
                micPermissionState.launchPermissionRequest()
            }
        }, enabled = enabled)
    )
}