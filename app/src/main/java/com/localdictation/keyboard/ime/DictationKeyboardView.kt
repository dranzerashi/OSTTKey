package com.localdictation.keyboard.ime

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localdictation.keyboard.audio.AudioRecorder

@Composable
fun DictationKeyboardView(
    state: DictationKeyboardState,
    onMicrophoneTap: () -> Unit,
    onEnterTap: () -> Unit,
    onBackspaceTap: () -> Unit,
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
                KeyboardActionKey("⌫", "Backspace key", onBackspaceTap)
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
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .semantics { contentDescription = description },
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
