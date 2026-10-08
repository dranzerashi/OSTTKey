package com.localdictation.keyboard

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HowToUseScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F6FA))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onBack, modifier = Modifier.padding(start = 0.dp)) {
            Text("‹ Back to OSTTKey")
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("How to use", fontSize = 27.sp, fontWeight = FontWeight.Bold)
            Text(
                "A quick guide to dictation and keyboard controls.",
                color = Color(0xFF5D6474),
                fontSize = 14.sp,
            )
        }

        GuideCard(
            title = "Get started",
            body = "Download and initialize the Whistle model while online. Enable OSTTKey in Android's keyboard settings and select it as your active keyboard. Focus a text field, then tap the microphone key. Microphone permission is requested the first time you dictate.",
        )

        GuideCard(
            title = "Dictation modes",
            points = listOf(
                "Classic mode is the default. Tap the microphone to start, tap again to stop early, or let recording stop at 30 seconds. The icon warns you during the final 5 seconds.",
                "Turn on Pause-aware continuous dictation in the main screen settings to transcribe speech in segments. After about 2 seconds of audio, OSTTKey looks for a brief natural pause between words or phrases, then sends that segment to Whistle while continuing to listen. It does not split just because 2 seconds elapsed. Transcripts are inserted as they finish.",
                "Pause-aware mode stops after 5 seconds without speech. Segments stay below Whistle's 30-second limit. If no natural pause is found before that limit, recording stops rather than cutting through a word. Tap the microphone to stop at any time.",
            ),
        )

        GuideCard(
            title = "Backspace (⌫)",
            points = listOf(
                "Tap and release to delete the word before the cursor.",
                "If text is already selected in the field, tap backspace to delete that selection.",
                "To select words for deletion, touch backspace and swipe left while keeping your finger down. Each step selects another word. Swipe right to unselect words one at a time, then release to delete the remaining selection.",
            ),
        )

        GuideCard(
            title = "Enter (↵)",
            body = "In a single-line field, Enter performs that field's action when it has one, such as Next, Search, or Done. In a multiline field, it inserts a line break.",
        )

        GuideCard(
            title = "Whistle languages",
            body = "Whistle supports English, German, French, Spanish, Italian, Dutch, and Polish.",
        )
    }
}

@Composable
private fun GuideCard(
    title: String,
    body: String? = null,
    points: List<String> = emptyList(),
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            body?.let { Text(it, color = Color(0xFF545C6E), fontSize = 14.sp) }
            points.forEach { point ->
                Text("• $point", color = Color(0xFF545C6E), fontSize = 14.sp)
            }
        }
    }
}
