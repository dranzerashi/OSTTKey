package com.localdictation.keyboard.ime

import android.os.SystemClock
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localdictation.keyboard.audio.AudioRecorder
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToLong

@Composable
fun DictationKeyboardView(
    state: DictationKeyboardState,
    onMicrophoneTap: () -> Unit,
    onEnterTap: () -> Unit,
    onBackspaceTap: () -> Unit,
    onBackspaceDoubleTap: () -> Unit,
    onWordSelectionStarted: () -> Boolean,
    onWordSelectionChanged: (Int) -> Int,
    onWordSelectionFinished: (Boolean) -> Boolean,
    onOpenSettings: () -> Unit,
) {
    val isRecording = state is DictationKeyboardState.Recording
    val isProcessing = state is DictationKeyboardState.Processing
    val elapsedMillis = (state as? DictationKeyboardState.Recording)?.elapsedMillis ?: 0L
    val warningStartsAtMillis = AudioRecorder.MAX_DURATION_MILLIS - WARNING_BEFORE_STOP_MILLIS
    val isAboutToStop = isRecording && elapsedMillis >= warningStartsAtMillis
    val secondsUntilStop = ((AudioRecorder.MAX_DURATION_MILLIS - elapsedMillis + 999L) / 1_000L)
        .coerceAtLeast(0L)
    val background = Color(0xFF171C27)
    val accent = if (isRecording) Color(0xFFE75561) else Color(0xFF829BFF)
    val warningPulseScale by animateFloatAsState(
        targetValue = if (isAboutToStop) 1.16f else 1f,
        animationSpec = if (isAboutToStop) {
            infiniteRepeatable(tween(durationMillis = 550), repeatMode = RepeatMode.Reverse)
        } else {
            tween(durationMillis = 220)
        },
        label = "recording-warning-pulse",
    )
    val warningPulseProgress = ((warningPulseScale - 1f) / 0.16f).coerceIn(0f, 1f)
    val warningColor = Color(0xFFFF5964)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(272.dp)
            .background(background)
            .padding(horizontal = 22.dp, vertical = 14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("OSTTKey", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text("ON-DEVICE", color = Color(0xFFB9C7FF), fontSize = 10.sp, letterSpacing = 1.2.sp)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(horizontal = 58.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(110.dp)) {
                    if (isAboutToStop) {
                        Box(
                            modifier = Modifier
                                .size(94.dp)
                                .scale(warningPulseScale)
                                .background(
                                    warningColor.copy(alpha = 0.12f + warningPulseProgress * 0.18f),
                                    CircleShape,
                                ),
                        )
                    }

                    Surface(
                        modifier = Modifier.size(88.dp),
                        shape = CircleShape,
                        color = if (isAboutToStop) {
                            warningColor.copy(alpha = 0.26f)
                        } else {
                            accent.copy(alpha = if (isRecording) 0.18f else 0.14f)
                        },
                        onClick = onMicrophoneTap,
                        enabled = !isProcessing,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (isProcessing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(34.dp),
                                    color = Color.White,
                                    strokeWidth = 3.dp,
                                )
                            } else {
                                Text(if (isRecording) "■" else "🎙", fontSize = if (isRecording) 31.sp else 39.sp)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
                Text(
                    text = when (state) {
                        DictationKeyboardState.Idle -> "Tap to dictate"
                        is DictationKeyboardState.Recording -> if (isAboutToStop) {
                            "Stopping in ${secondsUntilStop}s · ${formatDuration(state.elapsedMillis)}"
                        } else {
                            "Listening · ${formatDuration(state.elapsedMillis)}"
                        }
                        DictationKeyboardState.Processing -> "Transcribing on this device…"
                        DictationKeyboardState.ModelRequired -> "Whistle model needs to be downloaded"
                        DictationKeyboardState.PermissionRequired -> "Allow microphone access to dictate"
                        is DictationKeyboardState.Error -> state.message
                        DictationKeyboardState.Inserted -> "Added to the text field"
                    },
                    color = if (isAboutToStop) Color(0xFFFF9EA5) else Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )

                when (state) {
                    DictationKeyboardState.ModelRequired -> Button(
                        onClick = onOpenSettings,
                        modifier = Modifier.padding(top = 7.dp),
                    ) {
                        Text("Open Dictation Settings")
                    }
                    DictationKeyboardState.PermissionRequired -> Button(
                        onClick = onOpenSettings,
                        modifier = Modifier.padding(top = 7.dp),
                    ) {
                        Text("Grant microphone access")
                    }
                    is DictationKeyboardState.Error -> TextButton(
                        onClick = onMicrophoneTap,
                        modifier = Modifier.padding(top = 1.dp),
                    ) {
                        Text("Tap to retry", color = Color(0xFFB9C7FF))
                    }
                    else -> Spacer(Modifier.height(12.dp))
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(50.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                KeyboardActionKey(
                    glyph = "⌫",
                    description = "Backspace key",
                    onClick = onBackspaceTap,
                    onLongClick = {},
                    onDoubleClick = onBackspaceDoubleTap,
                    repeatWhilePressed = true,
                    onWordSelectionStarted = onWordSelectionStarted,
                    onWordSelectionChanged = onWordSelectionChanged,
                    onWordSelectionFinished = onWordSelectionFinished,
                    supportsWordSelection = true,
                )
                KeyboardActionKey("↵", "Enter key", onEnterTap)
            }
        }

        TextButton(onClick = onOpenSettings, modifier = Modifier.align(Alignment.CenterHorizontally).height(32.dp)) {
            Text("Settings", color = Color(0xFFAEB8CC), fontSize = 12.sp)
        }
    }
}

@Composable
private fun KeyboardActionKey(
    glyph: String,
    description: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onDoubleClick: (() -> Unit)? = null,
    repeatWhilePressed: Boolean = false,
    onWordSelectionStarted: () -> Boolean = { false },
    onWordSelectionChanged: (Int) -> Int = { it },
    onWordSelectionFinished: (Boolean) -> Boolean = { false },
    supportsWordSelection: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnWordSelectionStarted = rememberUpdatedState(onWordSelectionStarted)
    val currentOnWordSelectionChanged = rememberUpdatedState(onWordSelectionChanged)
    val currentOnWordSelectionFinished = rememberUpdatedState(onWordSelectionFinished)
    val currentHapticView = rememberUpdatedState(LocalView.current)
    val longPressRecognized = remember { mutableStateOf(false) }
    val horizontalDragActive = remember { mutableStateOf(false) }
    val wordSelectionActive = remember { mutableStateOf(false) }
    val longPressTimeoutMillis = LocalViewConfiguration.current.longPressTimeoutMillis
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val wordSelectionStepPx = with(LocalDensity.current) { WORD_SELECTION_STEP_DP.dp.toPx() }

    LaunchedEffect(
        isPressed,
        repeatWhilePressed,
        longPressTimeoutMillis,
        horizontalDragActive.value,
        wordSelectionActive.value,
    ) {
        if (!isPressed || !repeatWhilePressed || horizontalDragActive.value || wordSelectionActive.value) {
            return@LaunchedEffect
        }

        delay(longPressTimeoutMillis + BACKSPACE_DRAG_START_GRACE_MILLIS)
        currentOnClick()

        val repeatStartedAt = SystemClock.uptimeMillis()
        while (isActive) {
            val heldMillis = SystemClock.uptimeMillis() - repeatStartedAt
            val progress = (heldMillis.toFloat() / BACKSPACE_ACCELERATION_DURATION_MILLIS)
                .coerceIn(0f, 1f)
            val easedProgress = 1f - (1f - progress) * (1f - progress)
            val repeatDelayMillis = (
                BACKSPACE_INITIAL_REPEAT_DELAY_MILLIS -
                    (BACKSPACE_INITIAL_REPEAT_DELAY_MILLIS - BACKSPACE_FAST_REPEAT_DELAY_MILLIS) * easedProgress
                ).roundToLong()
            delay(repeatDelayMillis)
            currentOnClick()
        }
    }

    val observeWordSelectionDrag = Modifier.pointerInput(supportsWordSelection, wordSelectionStepPx) {
        if (!supportsWordSelection) return@pointerInput

        awaitEachGesture {
            val down = awaitFirstDown(
                requireUnconsumed = false,
                pass = PointerEventPass.Initial,
            )
            var selectionStarted = false
            var selectedWords = 0
            var completedNormally = false

            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) {
                        if (selectionStarted) {
                            currentOnWordSelectionFinished.value(selectedWords > 0)
                        }
                        completedNormally = true
                        break
                    }

                    if (!longPressRecognized.value) continue

                    val horizontalDistance = change.position.x - down.position.x
                    val verticalDistance = change.position.y - down.position.y
                    if (!horizontalDragActive.value &&
                        abs(horizontalDistance) >= touchSlop &&
                        abs(horizontalDistance) > abs(verticalDistance)
                    ) {
                        horizontalDragActive.value = true
                        currentHapticView.value.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    }
                    if (!horizontalDragActive.value) continue

                    val targetWordCount = floor((-horizontalDistance).coerceAtLeast(0f) / wordSelectionStepPx).toInt()
                    if (targetWordCount == selectedWords) continue

                    if (targetWordCount > 0 && !selectionStarted) {
                        selectionStarted = currentOnWordSelectionStarted.value()
                    }
                    if (selectionStarted) {
                        val previousSelectedWords = selectedWords
                        selectedWords = currentOnWordSelectionChanged.value(targetWordCount)
                        repeat(abs(selectedWords - previousSelectedWords)) {
                            currentHapticView.value.performHapticFeedback(HapticFeedbackConstants.TEXT_HANDLE_MOVE)
                        }
                        wordSelectionActive.value = selectedWords > 0
                    }
                }
            } finally {
                if (selectionStarted && !completedNormally) {
                    currentOnWordSelectionFinished.value(false)
                }
                longPressRecognized.value = false
                horizontalDragActive.value = false
                wordSelectionActive.value = false
            }
        }
    }

    Surface(
        modifier = Modifier
            .size(48.dp)
            .semantics { contentDescription = description }
            .combinedClickable(
                interactionSource = interactionSource,
                onClick = onClick,
                onLongClick = if (repeatWhilePressed || onLongClick != null) {
                    {
                        longPressRecognized.value = true
                        onLongClick?.invoke()
                    }
                } else {
                    null
                },
                onDoubleClick = onDoubleClick?.let { action ->
                    {
                        currentHapticView.value.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        action()
                    }
                },
            )
            .then(observeWordSelectionDrag),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF2B344A),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(glyph, color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Medium)
        }
    }
}

private fun formatDuration(milliseconds: Long): String {
    val seconds = (milliseconds / 1_000).coerceAtMost(AudioRecorder.MAX_DURATION_MILLIS.toLong() / 1_000)
    return "%02d:%02d".format(seconds / 60, seconds % 60)
}

private const val WARNING_BEFORE_STOP_MILLIS = 5_000L
private const val BACKSPACE_ACCELERATION_DURATION_MILLIS = 2_800f
private const val BACKSPACE_INITIAL_REPEAT_DELAY_MILLIS = 180f
private const val BACKSPACE_FAST_REPEAT_DELAY_MILLIS = 42f
private const val BACKSPACE_DRAG_START_GRACE_MILLIS = 180L
private const val WORD_SELECTION_STEP_DP = 38f
