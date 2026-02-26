package com.nefeshcore.whisperclick

import android.annotation.SuppressLint
import android.view.KeyEvent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.automirrored.outlined.KeyboardReturn
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.KeyboardVoice
import androidx.compose.material.icons.outlined.Redo
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp
import com.nefeshcore.whisperclick.ui.theme.KaiboardTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch


@SuppressLint("ViewConstructor")
class VoiceKeyboardView(private val service: VoiceKeyboardInputMethodService) :
    AbstractComposeView(service) {
    private val btnPad = 2.dp
    private val minSize = 12.dp
    private val contentPad = PaddingValues(horizontal = 12.dp)
    private val shape = RoundedCornerShape(size = 8.dp)

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    override fun Content() {
        var showEditRow by remember { mutableStateOf(false) }
        val pagerState = rememberPagerState(pageCount = { 3 })
        val coroutineScope = rememberCoroutineScope()

        val prefs = service.getSharedPreferences(
            service.getString(R.string.preference_file_key), android.content.Context.MODE_PRIVATE
        )
        var themeMode by remember { mutableStateOf(prefs.getString("theme_mode", "dark") ?: "dark") }
        DisposableEffect(prefs) {
            val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key == "theme_mode") {
                    themeMode = prefs.getString("theme_mode", "dark") ?: "dark"
                }
            }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }
        KaiboardTheme(themeMode = themeMode) {
            // Height calculated in layout phase (not composition) to avoid per-frame recomposition jitter
            val keyboardHeightDp = if (showEditRow) 96.dp else 56.dp
            val panelHeightDp = 300.dp
            val density = LocalDensity.current
            val keyboardHeightPx = with(density) { keyboardHeightDp.roundToPx() }
            val panelHeightPx = with(density) { panelHeightDp.roundToPx() }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .layout { measurable, constraints ->
                        // Read pager scroll in layout phase — no recomposition triggered
                        val fraction = (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                            .coerceIn(0f, 1f)
                        val h = (keyboardHeightPx + (panelHeightPx - keyboardHeightPx) * fraction).toInt()
                        val placeable = measurable.measure(
                            constraints.copy(minHeight = h, maxHeight = h)
                        )
                        layout(placeable.width, h) {
                            placeable.place(0, 0)
                        }
                    }
                    .background(MaterialTheme.colorScheme.surface)
            ) { page ->
                when (page) {
                    0 -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        KeyboardPage(
                            showEditRow = showEditRow,
                            onEditRowToggle = { showEditRow = it },
                            onNavigateToClipboard = {
                                coroutineScope.launch { pagerState.animateScrollToPage(2) }
                            }
                        )
                    }
                    1 -> RewritePanel(
                        onBack = {
                            coroutineScope.launch { pagerState.animateScrollToPage(0) }
                        }
                    )
                    2 -> ClipboardPanel(
                        onBack = {
                            coroutineScope.launch { pagerState.animateScrollToPage(0) }
                        }
                    )
                }
            }
        }
    }

    @Composable
    private fun KeyboardPage(
        showEditRow: Boolean,
        onEditRowToggle: (Boolean) -> Unit,
        onNavigateToClipboard: () -> Unit
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            // Row 1: Main keyboard bar
            Row(modifier = Modifier.fillMaxWidth()) {
                RecordButton(
                    enabled = service.canTranscribe,
                    isRecording = service.isRecording,
                    isTranscribing = service.isTranscribing,
                    isCloudMode = service.useCloudStt,
                    onClick = service::toggleRecord,
                    onCancel = service::cancelTranscription,
                    onLongPress = service::toggleSttMode,
                    modifier = Modifier
                        .weight(3f)
                        .padding(btnPad),
                    shape = shape,
                )
                RepeatKeyButton(
                    onClick = { service.sendKeyPress(KeyEvent.KEYCODE_DPAD_LEFT) },
                    modifier = Modifier
                        .weight(1f)
                        .padding(btnPad),
                    shape = shape,
                    contentPadding = contentPad,
                ) {
                    Icon(Icons.Outlined.KeyboardArrowLeft, "Left")
                }
                RepeatKeyButton(
                    onClick = { service.sendKeyPress(KeyEvent.KEYCODE_DPAD_RIGHT) },
                    modifier = Modifier
                        .weight(1f)
                        .padding(btnPad),
                    shape = shape,
                    contentPadding = contentPad,
                ) {
                    Icon(Icons.Outlined.KeyboardArrowRight, "Right")
                }
                RepeatKeyButton(
                    onClick = { service.sendKeyPress(KeyEvent.KEYCODE_DEL) },
                    modifier = Modifier
                        .weight(1f)
                        .padding(btnPad),
                    shape = shape,
                    contentPadding = contentPad,
                    tonal = true,
                ) {
                    Icon(
                        Icons.AutoMirrored.Outlined.Backspace,
                        stringResource(R.string.delete_button)
                    )
                }
                Button(
                    onClick = { service.sendEnter() },
                    modifier = Modifier
                        .weight(1.5f)
                        .padding(btnPad),
                    shape = shape,
                    contentPadding = contentPad,
                ) {
                    Icon(
                        Icons.AutoMirrored.Outlined.KeyboardReturn,
                        stringResource(R.string.return_button)
                    )
                }
                LongPressButton(
                    onTap = {
                        if (service.shouldRenderSwitcher()) {
                            service.switchKeyboard()
                        } else {
                            onEditRowToggle(!showEditRow)
                        }
                    },
                    onLongPress = { onEditRowToggle(!showEditRow) },
                    modifier = Modifier
                        .weight(1f)
                        .padding(btnPad),
                    shape = shape,
                    contentPadding = contentPad,
                ) {
                    Icon(
                        painterResource(R.drawable.keyboard_previous_language),
                        stringResource(R.string.switch_keyboard_button)
                    )
                }
            }

            // Row 2: Editing toolbar (toggled by long-press)
            if (showEditRow) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    EditButton(onClick = { service.performEditAction(android.R.id.selectAll) }) {
                        Icon(Icons.Outlined.SelectAll, "Select All")
                    }
                    EditButton(onClick = { service.performEditAction(android.R.id.copy) }) {
                        Icon(Icons.Outlined.ContentCopy, "Copy")
                    }
                    EditButton(onClick = { service.performEditAction(android.R.id.paste) }) {
                        Icon(Icons.Outlined.ContentPaste, "Paste")
                    }
                    EditButton(onClick = {
                        service.sendKeyPress(KeyEvent.KEYCODE_Z, KeyEvent.META_CTRL_ON)
                    }) {
                        Icon(Icons.Outlined.Undo, "Undo")
                    }
                    EditButton(onClick = {
                        service.sendKeyPress(
                            KeyEvent.KEYCODE_Z,
                            KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON
                        )
                    }) {
                        Icon(Icons.Outlined.Redo, "Redo")
                    }
                    // Clipboard history — jumps to page 2
                    EditButton(onClick = { onNavigateToClipboard() }) {
                        Icon(Icons.Outlined.Assignment, "Clipboard History")
                    }
                    // Small settings button
                    OutlinedButton(
                        onClick = { service.openSettings() },
                        modifier = Modifier
                            .weight(0.6f)
                            .height(36.dp)
                            .padding(horizontal = 2.dp),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Icon(Icons.Outlined.Settings, "Settings")
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun RewritePanel(onBack: () -> Unit) {
        val variants = service.rewriteVariants
        val isRewriting = service.isRewriting
        val error = service.rewriteError
        val originalText = service.rewriteOriginalText

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.AutoFixHigh,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Magic Rewrite",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Row {
                    if (variants == null && !isRewriting) {
                        Button(
                            onClick = { service.requestRewriteAll() },
                            modifier = Modifier.height(32.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text("Rewrite", fontSize = 13.sp)
                        }
                        Spacer(Modifier.width(4.dp))
                    }
                    OutlinedButton(
                        onClick = {
                            service.clearRewriteState()
                            onBack()
                        },
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Icon(Icons.Outlined.KeyboardArrowLeft, null, modifier = Modifier.size(16.dp))
                        Text("Back", fontSize = 13.sp)
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // Content area
            when {
                isRewriting -> {
                    // Loading state
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "Generating rewrites...",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                error != null -> {
                    // Error state
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            error,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                variants != null -> {
                    // Carousel of variant cards
                    val variantList = variants.toList()
                    val cardPagerState = rememberPagerState(pageCount = { variantList.size })

                    HorizontalPager(
                        state = cardPagerState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        pageSpacing = 8.dp
                    ) { page ->
                        val (styleName, text) = variantList[page]
                        VariantCard(
                            styleName = styleName,
                            description = variantDescription(styleName),
                            text = text,
                            onApply = {
                                service.applyRewrite(text)
                                onBack()
                            }
                        )
                    }

                    // Dot indicators
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        repeat(variantList.size) { i ->
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 3.dp)
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (i == cardPagerState.currentPage)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                    )
                            )
                        }
                    }
                }
                else -> {
                    // Initial state — prompt to rewrite
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Type or dictate text, then tap Rewrite",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }

    private fun variantDescription(styleName: String): String = when (styleName) {
        "Clean" -> "Grammar & punctuation fix"
        "Professional" -> "Formal tone"
        "Casual" -> "Relaxed, conversational"
        "Concise" -> "Shorter, tighter"
        "Emojify" -> "Adds relevant emojis"
        else -> ""
    }

    @Composable
    private fun VariantCard(
        styleName: String,
        description: String,
        text: String,
        onApply: () -> Unit
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        styleName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (description.isNotEmpty()) {
                        Text(
                            description,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                TextButton(
                    onClick = onApply,
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                ) {
                    Text("Use", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text(
                text,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 10,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
    @Composable
    private fun ClipboardPanel(onBack: () -> Unit) {
        val history = service.clipboardHistory

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Assignment,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Clipboard",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Row {
                    if (history.isNotEmpty()) {
                        TextButton(
                            onClick = { service.clearClipHistory() },
                            modifier = Modifier.height(32.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        ) {
                            Text("Clear All", fontSize = 13.sp)
                        }
                        Spacer(Modifier.width(4.dp))
                    }
                    OutlinedButton(
                        onClick = onBack,
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Icon(Icons.Outlined.KeyboardArrowLeft, null, modifier = Modifier.size(16.dp))
                        Text("Back", fontSize = 13.sp)
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            if (history.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No clipboard history yet",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(history) { index, entry ->
                        ClipEntryRow(
                            entry = entry,
                            onPaste = {
                                service.pasteClipEntry(entry.text)
                                onBack()
                            },
                            onDelete = { service.deleteClipEntry(index) }
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun ClipEntryRow(
        entry: ClipEntry,
        onPaste: () -> Unit,
        onDelete: () -> Unit
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .clickable { onPaste() }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    entry.text,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    relativeTime(entry.timestamp),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Outlined.Close,
                    "Delete",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    private fun relativeTime(timestamp: Long): String {
        val diff = System.currentTimeMillis() - timestamp
        return when {
            diff < 60_000 -> "Just now"
            diff < 3600_000 -> "${diff / 60_000}m ago"
            diff < 86400_000 -> "${diff / 3600_000}h ago"
            else -> "${diff / 86400_000}d ago"
        }
    }
}

// --- Reusable composables ---

@Composable
private fun RowScope.EditButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .weight(1f)
            .height(36.dp)
            .padding(horizontal = 2.dp),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp),
    ) {
        content()
    }
}

@Composable
private fun RepeatKeyButton(
    onClick: () -> Unit,
    modifier: Modifier,
    shape: Shape,
    contentPadding: PaddingValues,
    tonal: Boolean = false,
    initialDelayMs: Long = 400,
    repeatDelayMs: Long = 50,
    content: @Composable () -> Unit
) {
    val currentOnClick by rememberUpdatedState(onClick)
    val interactionSource = remember { MutableInteractionSource() }
    var pressing by remember { mutableStateOf(false) }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    pressing = true
                    currentOnClick()
                }
                is PressInteraction.Release -> pressing = false
                is PressInteraction.Cancel -> pressing = false
            }
        }
    }

    LaunchedEffect(pressing) {
        if (pressing) {
            delay(initialDelayMs)
            while (true) {
                currentOnClick()
                delay(repeatDelayMs)
            }
        }
    }

    if (tonal) {
        FilledTonalButton(
            onClick = {},
            interactionSource = interactionSource,
            modifier = modifier,
            shape = shape,
            contentPadding = contentPadding
        ) { content() }
    } else {
        OutlinedButton(
            onClick = {},
            interactionSource = interactionSource,
            modifier = modifier,
            shape = shape,
            contentPadding = contentPadding
        ) { content() }
    }
}

@Composable
private fun LongPressButton(
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier,
    shape: Shape,
    contentPadding: PaddingValues,
    longPressMs: Long = 500,
    content: @Composable () -> Unit
) {
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    val interactionSource = remember { MutableInteractionSource() }
    var longPressTriggered by remember { mutableStateOf(false) }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collectLatest { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    longPressTriggered = false
                    delay(longPressMs)
                    longPressTriggered = true
                    currentOnLongPress()
                }
                is PressInteraction.Release -> {}
                is PressInteraction.Cancel -> {}
            }
        }
    }

    OutlinedButton(
        onClick = { if (!longPressTriggered) currentOnTap() },
        interactionSource = interactionSource,
        modifier = modifier,
        shape = shape,
        contentPadding = contentPadding,
    ) {
        content()
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun RecordButton(
    enabled: Boolean,
    isRecording: Boolean,
    isTranscribing: Boolean,
    isCloudMode: Boolean,
    onClick: () -> Unit,
    onCancel: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier,
    shape: Shape
) {
    var firstRender by remember { mutableStateOf(true) }
    var seconds by remember { mutableIntStateOf(0) }
    var recordingStartTime by remember { mutableLongStateOf(0L) }

    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnCancel by rememberUpdatedState(onCancel)
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    val currentIsRecording by rememberUpdatedState(isRecording)
    val currentIsTranscribing by rememberUpdatedState(isTranscribing)

    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingStartTime = System.currentTimeMillis()
            seconds = 0
            while (true) {
                delay(1_000)
                seconds = ((System.currentTimeMillis() - recordingStartTime) / 1000).toInt()
            }
        }
    }

    val interactionSource = remember { MutableInteractionSource() }
    var longPressTriggered by remember { mutableStateOf(false) }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collectLatest { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    longPressTriggered = false
                    if (!currentIsRecording && !currentIsTranscribing) {
                        delay(500)
                        longPressTriggered = true
                        currentOnLongPress()
                    }
                }
                is PressInteraction.Release -> {}
                is PressInteraction.Cancel -> {}
            }
        }
    }

    Button(
        onClick = {
            if (longPressTriggered) return@Button
            if (currentIsTranscribing) {
                currentOnCancel()
                return@Button
            }
            currentOnClick()
        },
        enabled = enabled || isTranscribing,
        interactionSource = interactionSource,
        modifier = modifier,
        shape = shape
    ) {
        val iconSize = 28.dp
        val textSize = 18.sp
        if (isTranscribing) {
            firstRender = false
            Icon(Icons.Outlined.Close, "Cancel", modifier = Modifier.defaultMinSize(iconSize, iconSize))
            Text(" Cancel", fontSize = textSize, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip,
                modifier = Modifier.basicMarquee())
        } else if (!enabled && firstRender) {
            Icon(
                Icons.Outlined.KeyboardVoice, stringResource(R.string.start_recording),
                modifier = Modifier.defaultMinSize(iconSize, iconSize)
            )
        } else if (!enabled) {
            firstRender = false
            Text(stringResource(R.string.transcribing), fontSize = textSize, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip,
                modifier = Modifier.basicMarquee())
        } else if (isRecording) {
            firstRender = false
            Icon(Icons.Outlined.Stop, stringResource(R.string.stop_recording),
                modifier = Modifier.defaultMinSize(iconSize, iconSize))
            Text(
                " %d:%02d".format(seconds / 60, seconds % 60),
                fontSize = textSize, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip
            )
        } else {
            if (isCloudMode) {
                Icon(Icons.Outlined.Cloud, "Cloud STT",
                    modifier = Modifier.defaultMinSize(iconSize, iconSize))
            } else {
                Icon(Icons.Outlined.KeyboardVoice, stringResource(R.string.start_recording),
                    modifier = Modifier.defaultMinSize(iconSize, iconSize))
            }
        }
    }
}
