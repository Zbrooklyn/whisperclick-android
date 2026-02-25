package com.nefeshcore.whisperclick

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
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
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardVoice
import androidx.compose.material.icons.outlined.Redo
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.ui.unit.Dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

        KaiboardTheme {
            Column(modifier = Modifier.fillMaxWidth()) {
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
                    }
                }

                // Row 1: Main keyboard bar
                Row(modifier = Modifier.fillMaxWidth()) {
                    RecordButton(
                        enabled = service.canTranscribe,
                        isRecording = service.isRecording,
                        isTranscribing = service.isTranscribing,
                        onClick = service::toggleRecord,
                        onCancel = service::cancelTranscription,
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
                    FilledTonalButton(
                        onClick = { service.sendKeyPress(KeyEvent.KEYCODE_ENTER) },
                        modifier = Modifier
                            .weight(1f)
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
            .padding(2.dp),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 8.dp),
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
    repeatDelayMs: Long = 50,
    content: @Composable () -> Unit
) {
    var mHandler: Handler? = null
    val mAction: Runnable = object : Runnable {
        override fun run() {
            onClick()
            mHandler!!.postDelayed(this, repeatDelayMs)
        }
    }

    val interactionSource = remember { MutableInteractionSource() }
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collectLatest { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    if (mHandler != null) return@collectLatest
                    mHandler = Handler(Looper.getMainLooper())
                    mHandler!!.post(mAction)
                }

                is PressInteraction.Release -> {
                    if (mHandler == null) return@collectLatest
                    mHandler!!.removeCallbacks(mAction)
                    mHandler = null
                }
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
    val interactionSource = remember { MutableInteractionSource() }
    var longPressTriggered by remember { mutableStateOf(false) }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collectLatest { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    longPressTriggered = false
                    delay(longPressMs)
                    longPressTriggered = true
                    onLongPress()
                }

                is PressInteraction.Release -> {}
            }
        }
    }

    OutlinedButton(
        onClick = { if (!longPressTriggered) onTap() },
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
    onClick: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier,
    shape: Shape
) {
    var firstRender by remember { mutableStateOf(true) }
    var seconds by remember { mutableIntStateOf(0) }
    var handler: Handler? by remember { mutableStateOf(null) }
    var start by remember { mutableLongStateOf(0) }

    val runnable: Runnable = object : Runnable {
        override fun run() {
            seconds = ((System.currentTimeMillis() - start) / 1000).toInt()
            handler?.postDelayed(this, 1_000)
        }
    }

    Button(onClick = {
        if (isTranscribing) {
            onCancel()
            return@Button
        }
        if (!isRecording) {
            start = System.currentTimeMillis()
            seconds = 0
            handler = Handler(Looper.getMainLooper())
            handler!!.postDelayed(runnable, 1_000)
        } else {
            handler?.removeCallbacks(runnable)
            handler = null
        }
        onClick()
    }, enabled = enabled || isTranscribing, modifier = modifier, shape = shape) {
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
            Icon(
                Icons.Outlined.KeyboardVoice, stringResource(R.string.start_recording),
                modifier = Modifier.defaultMinSize(iconSize, iconSize)
            )
        }
    }
}
