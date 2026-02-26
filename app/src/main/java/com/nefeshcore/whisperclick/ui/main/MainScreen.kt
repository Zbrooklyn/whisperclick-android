package com.nefeshcore.whisperclick.ui.main

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.NavigateNext
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
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
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.nefeshcore.whisperclick.BuildConfig
import com.nefeshcore.whisperclick.R
import com.nefeshcore.whisperclick.api.ApiKeyValidator
import com.nefeshcore.whisperclick.model.ModelManager
import com.nefeshcore.whisperclick.utils.AppLog
import com.nefeshcore.whisperclick.utils.CrashLogger
import kotlinx.coroutines.launch

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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
private fun MainScreen(
    canTranscribe: Boolean,
    isRecording: Boolean,
    messageLog: String,
    onBenchmarkTapped: () -> Unit,
    onRecordTapped: () -> Unit
) {
    val context = LocalContext.current
    val sharedPref = context.getSharedPreferences(
        stringResource(R.string.preference_file_key), Context.MODE_PRIVATE
    )
    val maxThreads = Runtime.getRuntime().availableProcessors()
    val threadsStr = stringResource(R.string.num_threads)
    val casualMode = stringResource(R.string.casual_mode)
    val pauseMediaStr = stringResource(R.string.pause_media)
    var nThreads by remember {
        mutableFloatStateOf(sharedPref.getInt(threadsStr, maxThreads).toFloat())
    }
    var lowercase by remember {
        mutableStateOf(sharedPref.getBoolean(casualMode, false))
    }
    var pauseMedia by remember {
        mutableStateOf(sharedPref.getBoolean(pauseMediaStr, false))
    }
    var aiProvider by remember {
        mutableStateOf(sharedPref.getString("ai_provider", "gemini") ?: "gemini")
    }
    var geminiApiKey by remember {
        mutableStateOf(sharedPref.getString("gemini_api_key", "") ?: "")
    }
    var openaiApiKey by remember {
        mutableStateOf(sharedPref.getString("openai_api_key", "") ?: "")
    }
    var sttMode by remember {
        mutableStateOf(sharedPref.getString("stt_mode", "local") ?: "local")
    }
    var themeMode by remember {
        mutableStateOf(sharedPref.getString("theme_mode", "dark") ?: "dark")
    }
    val coroutineScope = rememberCoroutineScope()
    val downloadProgress by ModelManager.progress.collectAsState()
    var downloadedModels by remember { mutableStateOf(ModelManager.getDownloadedModels(context)) }
    val bundledModels = remember { ModelManager.getBundledModels(context) }
    var activeModel by remember { mutableStateOf(ModelManager.getActiveModelName(context)) }
    var showModelDialog by remember { mutableStateOf(false) }
    var debugExpanded by remember { mutableStateOf(false) }

    val versionName = BuildConfig.VERSION_NAME

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.mipmap.ic_launcher),
                            contentDescription = null,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                "WhisperClick",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "v$versionName",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp
            )
        ) {
            // Status chip
            item {
                val activeLabel = if (activeModel.isEmpty()) "tiny.en" else activeModel
                val modeLabel = if (sttMode == "cloud") "Cloud" else "Local"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = true,
                        onClick = {},
                        label = {
                            Text(
                                "$modeLabel  \u00b7  $activeLabel",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        },
                        leadingIcon = {
                            Icon(
                                if (sttMode == "cloud") Icons.Outlined.Cloud else Icons.Outlined.Mic,
                                null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
            }

            // ── Setup (auto-collapses when complete) ──
            item {
                SetupSection(sharedPref)
            }

            // ── Voice & AI (STT mode + AI provider + API keys) ──
            item {
                SectionCard(title = "Voice & AI") {
                    // STT Mode toggle
                    ListItem(
                        headlineContent = { Text("STT Mode") },
                        leadingContent = { Icon(Icons.Outlined.Cloud, null) },
                        supportingContent = {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = sttMode == "local",
                                    onClick = {
                                        sttMode = "local"
                                        with(sharedPref.edit()) {
                                            putString("stt_mode", "local"); apply()
                                        }
                                    },
                                    label = { Text("Local") },
                                    modifier = Modifier.weight(1f)
                                )
                                FilterChip(
                                    selected = sttMode == "cloud",
                                    onClick = {
                                        sttMode = "cloud"
                                        with(sharedPref.edit()) {
                                            putString("stt_mode", "cloud"); apply()
                                        }
                                    },
                                    label = { Text("Cloud") },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    )

                    // Local mode: model management + threads
                    if (sttMode == "local") {
                        val activeLabel = if (activeModel.isEmpty()) {
                            "Default (bundled)"
                        } else {
                            ModelManager.availableModels
                                .firstOrNull { it.fileName == activeModel }?.name
                                ?: activeModel
                        }
                        ListItem(
                            headlineContent = { Text("Active Model") },
                            leadingContent = { Icon(Icons.Outlined.Memory, null) },
                            supportingContent = { Text(activeLabel) },
                            trailingContent = {
                                Icon(Icons.AutoMirrored.Outlined.NavigateNext, null)
                            },
                            modifier = Modifier.clickable { showModelDialog = true }
                        )

                        ListItem(
                            headlineContent = { Text("Manage Models") },
                            leadingContent = { Icon(Icons.Outlined.Download, null) },
                            supportingContent = {
                                Column {
                                    if (downloadProgress.isDownloading) {
                                        val model = downloadProgress.model
                                        Text("Downloading ${model?.name ?: ""}...")
                                        if (downloadProgress.totalBytes > 0) {
                                            LinearProgressIndicator(
                                                progress = downloadProgress.bytesDownloaded.toFloat() / downloadProgress.totalBytes.toFloat(),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(top = 4.dp)
                                            )
                                            Text(
                                                "${downloadProgress.bytesDownloaded / 1024 / 1024}MB / ${downloadProgress.totalBytes / 1024 / 1024}MB",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        TextButton(onClick = { ModelManager.cancelDownload() }) {
                                            Text("Cancel")
                                        }
                                    } else {
                                        ModelManager.availableModels.forEach { model ->
                                            val isDownloaded =
                                                model.fileName in downloadedModels || model.fileName in bundledModels
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 2.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(model.name, fontSize = 14.sp)
                                                    Text(
                                                        "${model.sizeMb}MB \u00b7 ${model.tier}",
                                                        fontSize = 12.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                if (isDownloaded) {
                                                    Text(
                                                        "\u2713",
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                } else {
                                                    TextButton(onClick = {
                                                        coroutineScope.launch {
                                                            val ok = ModelManager.downloadModel(
                                                                context, model
                                                            )
                                                            if (ok) downloadedModels =
                                                                ModelManager.getDownloadedModels(
                                                                    context
                                                                )
                                                        }
                                                    }) {
                                                        Text("Get")
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        )

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
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // AI Provider picker (always visible — powers Cloud STT + Magic Rewrite)
                    ListItem(
                        headlineContent = { Text("AI Provider") },
                        leadingContent = { Icon(Icons.Outlined.Key, null) },
                        supportingContent = {
                            Column {
                                Text(
                                    "Powers Cloud STT and Magic Rewrite",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilterChip(
                                        selected = aiProvider == "gemini",
                                        onClick = {
                                            aiProvider = "gemini"
                                            with(sharedPref.edit()) {
                                                putString("ai_provider", "gemini"); apply()
                                            }
                                        },
                                        label = { Text("Gemini") },
                                        modifier = Modifier.weight(1f)
                                    )
                                    FilterChip(
                                        selected = aiProvider == "openai",
                                        onClick = {
                                            aiProvider = "openai"
                                            with(sharedPref.edit()) {
                                                putString("ai_provider", "openai"); apply()
                                            }
                                        },
                                        label = { Text("OpenAI") },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    )
                    // API key field for selected provider
                    if (aiProvider == "gemini") {
                        ApiKeyField(
                            label = "Gemini API Key",
                            value = geminiApiKey,
                            placeholder = "AIza...",
                            onValueChange = {
                                geminiApiKey = it
                                with(sharedPref.edit()) {
                                    putString("gemini_api_key", it); apply()
                                }
                            },
                            onVerify = { ApiKeyValidator.validateGemini(it) }
                        )
                    } else {
                        ApiKeyField(
                            label = "OpenAI API Key",
                            value = openaiApiKey,
                            placeholder = "sk-...",
                            onValueChange = {
                                openaiApiKey = it
                                with(sharedPref.edit()) {
                                    putString("openai_api_key", it); apply()
                                }
                            },
                            onVerify = { ApiKeyValidator.validateOpenAI(it) }
                        )
                    }
                }

                // Model picker dialog
                if (showModelDialog) {
                    val allModels =
                        bundledModels + downloadedModels.filter { it !in bundledModels }
                    val modelLookup = ModelManager.availableModels.associateBy { it.fileName }
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { showModelDialog = false },
                        title = { Text("Select Model") },
                        text = {
                            androidx.compose.foundation.lazy.LazyColumn {
                                item {
                                    ListItem(
                                        headlineContent = { Text("Default (bundled)") },
                                        supportingContent = { Text("Tiny English \u00b7 Fast") },
                                        modifier = Modifier.clickable {
                                            activeModel = ""
                                            ModelManager.setActiveModel(context, "")
                                            showModelDialog = false
                                        }
                                    )
                                }
                                items(allModels.size) { i ->
                                    val fileName = allModels[i]
                                    val info = modelLookup[fileName]
                                    val displayName = info?.name ?: fileName
                                    val subtitle = if (info != null) {
                                        "${info.sizeMb}MB \u00b7 ${info.tier}" +
                                            if (fileName in bundledModels) " \u00b7 Bundled" else ""
                                    } else {
                                        if (fileName in bundledModels) "Bundled" else "Downloaded"
                                    }
                                    ListItem(
                                        headlineContent = { Text(displayName) },
                                        supportingContent = { Text(subtitle) },
                                        modifier = Modifier.clickable {
                                            activeModel = fileName
                                            ModelManager.setActiveModel(context, fileName)
                                            showModelDialog = false
                                        }
                                    )
                                }
                            }
                        },
                        confirmButton = {}
                    )
                }
            }

            // ── Preferences (merged: behavior + appearance) ──
            item {
                SectionCard(title = "Preferences") {
                    // Casual mode
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
                                    putBoolean(casualMode, it); apply()
                                }
                            })
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    // Pause media
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
                                    putBoolean(pauseMediaStr, it); apply()
                                }
                            })
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    // Theme
                    ListItem(
                        headlineContent = { Text("Theme") },
                        leadingContent = { Icon(Icons.Outlined.DarkMode, null) },
                        supportingContent = {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    "light" to "Light",
                                    "dark" to "Dark",
                                    "oled" to "OLED"
                                ).forEach { (key, label) ->
                                    FilterChip(
                                        selected = themeMode == key,
                                        onClick = {
                                            themeMode = key
                                            with(sharedPref.edit()) {
                                                putString("theme_mode", key); apply()
                                            }
                                        },
                                        label = { Text(label) }
                                    )
                                }
                            }
                        }
                    )
                }
            }

            // ── Debug (collapsed by default) ──
            item {
                CollapsibleSectionCard(
                    title = "Debug",
                    expanded = debugExpanded,
                    onToggle = { debugExpanded = !debugExpanded }
                ) {
                    BenchmarkButton(enabled = canTranscribe, onClick = onBenchmarkTapped)
                    RecordButton(
                        enabled = canTranscribe,
                        isRecording = isRecording,
                        onClick = onRecordTapped
                    )
                    CollapsibleLogSection(
                        title = "App Log",
                        icon = null
                    ) {
                        AppLogContent()
                    }
                    CollapsibleLogSection(
                        title = "Crash Logs",
                        icon = Icons.Outlined.BugReport,
                        iconTint = MaterialTheme.colorScheme.error,
                        badge = CrashLogger.getCrashCount().let { if (it > 0) "($it)" else null }
                    ) {
                        CrashLogContent()
                    }
                }
            }

            // ── Footer ──
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "WhisperClick v$versionName",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Nefeshcore",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(4.dp))
                    val uriHandler = LocalUriHandler.current
                    TextButton(onClick = { uriHandler.openUri("https://github.com/Zbrooklyn/whisperclick-android") }) {
                        Icon(
                            Icons.Outlined.Star, null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Star on GitHub", fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

// ── Section Card wrapper ──

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Text(
            title,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 0.5.sp,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
        )
        content()
        Spacer(Modifier.height(4.dp))
    }
}

// ── Collapsible Section Card ──

@Composable
private fun CollapsibleSectionCard(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle() }
                .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = if (expanded) 4.dp else 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 0.5.sp,
            )
            Icon(
                if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column {
                content()
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

// ── Collapsible Log Sub-section ──

@Composable
private fun CollapsibleLogSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    badge: String? = null,
    content: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, tint = iconTint, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(
                title + (badge?.let { " $it" } ?: ""),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (icon != null) iconTint else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
    }

    AnimatedVisibility(
        visible = expanded,
        enter = expandVertically(),
        exit = shrinkVertically()
    ) {
        content()
    }
}

// ── App Log content ──

@Composable
private fun AppLogContent() {
    val logText by AppLog.log.collectAsState()
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Activity Log",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row {
            IconButton(
                onClick = {
                    val clipboard =
                        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(
                        ClipData.newPlainText("WhisperClick Log", logText)
                    )
                    Toast.makeText(context, "Log copied", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Outlined.ContentCopy, "Copy log",
                    modifier = Modifier.size(16.dp)
                )
            }
            IconButton(
                onClick = { AppLog.clear() },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Outlined.Delete, "Clear log",
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
    SelectionContainer(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(
            text = logText.ifEmpty { "(no log entries yet)" },
            fontSize = 11.sp,
            lineHeight = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ── Crash Log content ──

@Composable
private fun CrashLogContent() {
    val context = LocalContext.current
    var crashCount by remember { mutableStateOf(CrashLogger.getCrashCount()) }
    val crashText = remember { CrashLogger.getLatestCrash() ?: "" }

    if (crashCount == 0) {
        Text(
            "(no crashes recorded)",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        return
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.End
    ) {
        IconButton(onClick = {
            val clipboard =
                context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val text = CrashLogger.getLatestCrash() ?: ""
            clipboard.setPrimaryClip(ClipData.newPlainText("Crash Log", text))
            Toast.makeText(context, "Crash log copied", Toast.LENGTH_SHORT).show()
        }) {
            Icon(Icons.Outlined.ContentCopy, "Copy crash", modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = {
            CrashLogger.clearAll()
            crashCount = 0
        }) {
            Icon(Icons.Outlined.Delete, "Clear crashes", modifier = Modifier.size(18.dp))
        }
    }

    if (crashText.isNotEmpty()) {
        SelectionContainer(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            Text(
                text = crashText,
                fontSize = 10.sp,
                lineHeight = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ── Setup section (auto-collapses when complete) ──

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun SetupSection(sharedPref: android.content.SharedPreferences) {
    val context = LocalContext.current
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    val isKeyboardEnabled =
        imm.enabledInputMethodList.any { it.packageName == context.packageName }
    val micPermissionState = rememberPermissionState(
        permission = android.Manifest.permission.RECORD_AUDIO
    )
    val isMicGranted = micPermissionState.status.isGranted
    val setupComplete = isKeyboardEnabled && isMicGranted
    var expanded by remember { mutableStateOf(!setupComplete) }

    if (setupComplete && !expanded) {
        // Compact: setup complete
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = true }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.CheckCircle,
                        null,
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Setup complete",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 0.5.sp,
                    )
                }
                Icon(
                    Icons.Outlined.ExpandMore,
                    contentDescription = "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    } else {
        SectionCard(title = "Setup") {
            // Enable keyboard
            ListItem(
                headlineContent = { Text(stringResource(R.string.input_method_button)) },
                leadingContent = { Icon(Icons.Outlined.Keyboard, null) },
                supportingContent = {
                    if (isKeyboardEnabled) {
                        Text("Enabled", color = Color(0xFF4CAF50))
                    } else {
                        Text(stringResource(R.string.input_method_description))
                    }
                },
                trailingContent = {
                    if (isKeyboardEnabled) {
                        Icon(
                            Icons.Outlined.CheckCircle,
                            "Enabled",
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Icon(Icons.AutoMirrored.Outlined.NavigateNext, null)
                    }
                },
                modifier = Modifier.clickable(onClick = {
                    context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                })
            )
            // Mic permission
            ListItem(
                headlineContent = { Text(stringResource(R.string.permission_button)) },
                leadingContent = { Icon(Icons.Outlined.Mic, null) },
                supportingContent = {
                    if (isMicGranted) {
                        Text("Granted", color = Color(0xFF4CAF50))
                    }
                },
                trailingContent = {
                    if (isMicGranted) {
                        Icon(
                            Icons.Outlined.CheckCircle,
                            "Granted",
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Icon(Icons.AutoMirrored.Outlined.NavigateNext, null)
                    }
                },
                modifier = Modifier.clickable(onClick = {
                    if (!isMicGranted) {
                        micPermissionState.launchPermissionRequest()
                    }
                })
            )
            // Keyboard picker
            KeyboardPickerItem(sharedPref)

            // Collapse button if setup is done
            if (setupComplete) {
                TextButton(
                    onClick = { expanded = false },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                ) {
                    Text("Collapse", fontSize = 12.sp)
                }
            }
        }
    }
}

// ── Keyboard picker ──

@Composable
private fun KeyboardPickerItem(sharedPref: android.content.SharedPreferences) {
    val context = LocalContext.current
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    val enabledImes = imm.enabledInputMethodList
    val ownPackage = context.packageName
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
                                    putString("preferred_keyboard", ""); apply()
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
                                    putString("preferred_keyboard", ime.id); apply()
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

// ── API Key field with validation ──

@Composable
private fun ApiKeyField(
    label: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    onVerify: suspend (String) -> ApiKeyValidator.Result
) {
    var showKey by remember { mutableStateOf(false) }
    var verifyState by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    ListItem(
        headlineContent = { Text(label) },
        leadingContent = {
            val icon = when {
                verifyState == "valid" -> Icons.Outlined.CheckCircle
                verifyState?.startsWith("error") == true -> Icons.Outlined.Error
                else -> Icons.Outlined.Key
            }
            val tint = when {
                verifyState == "valid" -> Color(0xFF4CAF50)
                verifyState?.startsWith("error") == true -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Icon(icon, null, tint = tint)
        },
        supportingContent = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = {
                        onValueChange(it)
                        verifyState = null
                    },
                    placeholder = {
                        Text(
                            placeholder,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    },
                    label = { Text(label) },
                    singleLine = true,
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(
                                if (showKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                if (showKey) "Hide key" else "Show key",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    when {
                        verifyState == "" -> Text(
                            "Verifying...",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        verifyState == "valid" -> Text(
                            "Key verified",
                            fontSize = 12.sp,
                            color = Color(0xFF4CAF50)
                        )

                        verifyState?.startsWith("error:") == true -> Text(
                            verifyState!!.removePrefix("error:"),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error
                        )

                        else -> Spacer(Modifier)
                    }
                    TextButton(
                        onClick = {
                            verifyState = ""
                            coroutineScope.launch {
                                val result = onVerify(value)
                                verifyState = when (result) {
                                    is ApiKeyValidator.Result.Valid -> "valid"
                                    is ApiKeyValidator.Result.Invalid -> "error:${result.message}"
                                }
                            }
                        },
                        enabled = value.isNotBlank() && verifyState != "",
                        contentPadding = ButtonDefaults.TextButtonContentPadding,
                    ) {
                        if (verifyState == "") {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        Text("Verify", fontSize = 13.sp)
                    }
                }
            }
        }
    )
}

// ── Testing items ──

@Composable
private fun BenchmarkButton(enabled: Boolean, onClick: () -> Unit) {
    ListItem(
        leadingContent = {
            Icon(Icons.Outlined.Speed, stringResource(R.string.start_recording))
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
                if (isRecording) Icons.Outlined.Stop else Icons.Outlined.PlayArrow,
                stringResource(R.string.start_recording)
            )
        },
        headlineContent = {
            Text(
                if (isRecording) stringResource(R.string.stop_test)
                else stringResource(R.string.start_test)
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
