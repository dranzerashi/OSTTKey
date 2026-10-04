package com.localdictation.keyboard.ime

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DictationKeyboardView(
    state: DictationKeyboardState,
    onMicrophoneTap: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val isRecording = state is DictationKeyboardState.Recording
    val isProcessing = state is DictationKeyboardState.Processing
    val background = Color(0xFF171C27)
    val accent = if (isRecording) Color(0xFFE75561) else Color(0xFF829BFF)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(272.dp)
            .background(background)
            .padding(horizontal = 22.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("OSTTKey", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text("ON-DEVICE", color = Color(0xFFB9C7FF), fontSize = 10.sp, letterSpacing = 1.2.sp)
        }

        Spacer(Modifier.weight(1f))

        Box(contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier.size(94.dp),
                shape = CircleShape,
                color = accent.copy(alpha = if (isRecording) 0.18f else 0.14f),
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

        Spacer(Modifier.height(13.dp))
        Text(
            text = when (state) {
                DictationKeyboardState.Idle -> "Tap to dictate"
                is DictationKeyboardState.Recording -> "Listening · ${formatDuration(state.elapsedMillis)}"
                DictationKeyboardState.Processing -> "Transcribing on this device…"
                DictationKeyboardState.ModelRequired -> "Whistle model needs to be downloaded"
                DictationKeyboardState.PermissionRequired -> "Allow microphone access to dictate"
                is DictationKeyboardState.Error -> state.message
                DictationKeyboardState.Inserted -> "Added to the text field"
            },
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )

        when (state) {
            DictationKeyboardState.ModelRequired -> Button(onClick = onOpenSettings, modifier = Modifier.padding(top = 7.dp)) {
                Text("Open Dictation Settings")
            }
            DictationKeyboardState.PermissionRequired -> Button(onClick = onOpenSettings, modifier = Modifier.padding(top = 7.dp)) {
                Text("Grant microphone access")
            }
            is DictationKeyboardState.Error -> TextButton(onClick = onMicrophoneTap, modifier = Modifier.padding(top = 1.dp)) {
                Text("Tap to retry", color = Color(0xFFB9C7FF))
            }
            else -> Spacer(Modifier.height(12.dp))
        }

        Spacer(Modifier.weight(1f))
        TextButton(onClick = onOpenSettings, modifier = Modifier.height(32.dp)) {
            Text("Settings", color = Color(0xFFAEB8CC), fontSize = 12.sp)
        }
    }
}

private fun formatDuration(milliseconds: Long): String {
    val seconds = (milliseconds / 1_000).coerceAtMost(30)
    return "%02d:%02d".format(seconds / 60, seconds % 60)
}
