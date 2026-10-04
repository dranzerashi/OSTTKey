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
            title = "Dictation",
            body = "Tap the microphone to start recording. Tap it again to stop early. Recording stops automatically after 30 seconds. At 25 seconds, the microphone icon pulses and a countdown appears to warn you. Whistle then transcribes on this device and inserts the result at the cursor.",
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
