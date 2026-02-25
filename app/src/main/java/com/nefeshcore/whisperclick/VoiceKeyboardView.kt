package com.nefeshcore.whisperclick

import android.annotation.SuppressLint
import android.view.KeyEvent
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.automirrored.outlined.KeyboardReturn
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardVoice
import androidx.compose.material.icons.outlined.Redo
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.nefeshcore.whisperclick.ui.theme.KaiboardTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest


@SuppressLint("ViewConstructor")
class VoiceKeyboardView(private val service: VoiceKeyboardInputMethodService) :
    AbstractComposeView(service) {
    private val btnPad = 2.dp
    private val minSize = 12.dp
    private val contentPad = PaddingValues(horizontal = 12.dp)
    private val shape = RoundedCornerShape(size = 8.dp)

    @Composable
    override fun Content() {
        var showEditRow by remember { mutableStateOf(false) }

        val prefs = service.getSharedPreferences(
            service.getString(R.string.preference_file_key), android.content.Context.MODE_PRIVATE
        )
        val themeMode = prefs.getString("theme_mode", "dark") ?: "dark"
        KaiboardTheme(themeMode = themeMode) {
            Column(modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
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
                                showEditRow = !showEditRow
                            }
                        },
                        onLongPress = { showEditRow = !showEditRow },
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

                // Row 2: Editing toolbar (toggled by long-press ⌨)
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

    // Fire immediately on press, track state for repeat
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    pressing = true
                    currentOnClick() // Fire instantly — don't wait for recomposition
                }
                is PressInteraction.Release -> pressing = false
                is PressInteraction.Cancel -> pressing = false
            }
        }
    }

    // Repeat while held down (first fire already happened above)
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

    // Timer: count seconds while recording
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

    // Long-press detection only when idle (not recording, not transcribing)
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
            // Idle state — show mic icon + cloud/local indicator
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
