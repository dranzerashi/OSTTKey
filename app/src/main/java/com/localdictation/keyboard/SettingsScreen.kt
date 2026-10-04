package com.localdictation.keyboard

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.localdictation.keyboard.model.ModelSnapshot
import com.localdictation.keyboard.model.ModelState
import com.localdictation.keyboard.model.WhistleModelManager
import kotlinx.coroutines.launch

private val DictationColors = lightColorScheme(
    primary = Color(0xFF3E56C4),
    onPrimary = Color.White,
    secondary = Color(0xFF53617F),
    background = Color(0xFFF5F6FA),
    surface = Color.White,
    onSurface = Color(0xFF1B2030),
)

@Composable
fun LocalDictationTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DictationColors, content = content)
}

@Composable
fun SettingsScreen(
    modelManager: WhistleModelManager,
    keyboardStatus: KeyboardStatus,
    microphonePermissionRequestId: Int,
    onKeyboardSettings: () -> Unit,
    onChooseKeyboard: () -> Unit,
    onOpenCredits: () -> Unit,
) {
    val context = LocalContext.current
    val modelSnapshot by modelManager.snapshot.collectAsState()
    val scope = rememberCoroutineScope()
    var microphonePermissionGranted by remember { mutableStateOf(hasMicrophonePermission(context)) }
    var testText by remember { mutableStateOf("") }

    val microphonePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> microphonePermissionGranted = granted }

    LaunchedEffect(microphonePermissionRequestId) {
        if (microphonePermissionRequestId > 0 && !microphonePermissionGranted) {
            microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F6FA))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("OSTTKey", fontSize = 29.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B2030))
            Text("Offline speech to text keyboard", color = Color(0xFF5D6474), fontSize = 15.sp)
        }

        Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Whistle model", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text(WhistleModelManager.MODEL_SIZE_LABEL, fontSize = 12.sp, color = Color(0xFF6E7482))
                }
                Text(modelStatusText(modelSnapshot), color = Color(0xFF545C6E), fontSize = 14.sp)

                if (modelSnapshot.state == ModelState.DOWNLOADING) {
                    val progress = if (modelSnapshot.totalBytes > 0) {
                        (modelSnapshot.bytesDownloaded.toFloat() / modelSnapshot.totalBytes).coerceIn(0f, 1f)
                    } else 0f
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    Text(
                        "${formatMegabytes(modelSnapshot.bytesDownloaded)} / ${WhistleModelManager.MODEL_SIZE_LABEL}",
                        color = Color(0xFF6E7482),
                        fontSize = 12.sp,
                    )
                }

                modelSnapshot.error?.let {
                    Text(it, color = Color(0xFFB3261E), fontSize = 13.sp)
                }

                when {
                    modelSnapshot.state == ModelState.READY -> Text("Ready for local dictation", color = Color(0xFF28734B))
                    modelSnapshot.state == ModelState.DOWNLOADING || modelSnapshot.state == ModelState.LOADING -> Unit
                    modelSnapshot.state == ModelState.AVAILABLE || modelManager.isModelAvailable() -> {
                        Button(onClick = { scope.launch { runCatching { modelManager.ensureReady() } } }) {
                            Text("Initialize Whistle")
                        }
                    }
                    else -> Button(onClick = {
                        scope.launch {
                            runCatching {
                                modelManager.downloadModel()
                                modelManager.ensureReady()
                            }
                        }
                    }) {
                        Text(if (modelSnapshot.state == ModelState.ERROR) "Retry model download" else "Download Whistle model")
                    }
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("Keyboard", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    when {
                        keyboardStatus.selected -> "OSTTKey is enabled and selected."
                        keyboardStatus.enabled -> "Enabled. Select OSTTKey when you want to dictate."
                        else -> "Enable OSTTKey in Android keyboard settings."
                    },
                    color = Color(0xFF545C6E),
                    fontSize = 14.sp,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Button(onClick = onKeyboardSettings) {
                        Text(if (keyboardStatus.enabled) "Keyboard settings" else "Enable as keyboard")
                    }
                    if (keyboardStatus.enabled) {
                        OutlinedButton(onClick = onChooseKeyboard) { Text("Choose keyboard") }
                    }
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("Microphone", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    if (microphonePermissionGranted) "Microphone access is allowed."
                    else "Access is requested only when you start dictation.",
                    color = Color(0xFF545C6E),
                    fontSize = 14.sp,
                )
                if (!microphonePermissionGranted) {
                    OutlinedButton(onClick = { microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }) {
                        Text("Grant microphone access")
                    }
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Try the keyboard", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text("Tap this field, switch to OSTTKey, then use the microphone.", color = Color(0xFF545C6E), fontSize = 14.sp)
                BasicTextField(
                    value = testText,
                    onValueChange = { testText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF1F2F7), RoundedCornerShape(12.dp))
                        .padding(14.dp),
                    decorationBox = { inner ->
                        if (testText.isEmpty()) Text("Tap here to try dictation", color = Color(0xFF777E8D))
                        inner()
                    },
                )
            }
        }

        Surface(color = Color(0xFFE8EBF8), shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Private by design", fontWeight = FontWeight.SemiBold, color = Color(0xFF29345D))
                Text(
                    "Audio is processed on this device. The only download is the Whistle model; audio and transcripts are not uploaded or saved.",
                    color = Color(0xFF48516C),
                    fontSize = 13.sp,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Credits & licenses",
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clickable(role = Role.Button, onClick = onOpenCredits)
                .padding(vertical = 6.dp, horizontal = 10.dp),
            color = Color(0xFF5D6474),
            fontSize = 12.sp,
            textDecoration = TextDecoration.Underline,
        )
    }
}

private fun hasMicrophonePermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

private fun modelStatusText(snapshot: ModelSnapshot): String = when (snapshot.state) {
    ModelState.NOT_AVAILABLE -> "Not downloaded. It is fetched from Cactus Compute on first setup."
    ModelState.DOWNLOADING -> "Downloading the on-device model…"
    ModelState.AVAILABLE -> "Downloaded. Initialize it before your first dictation."
    ModelState.LOADING -> "Loading Whistle into memory…"
    ModelState.READY -> "Loaded and ready. Transcription runs locally."
    ModelState.ERROR -> "Whistle needs attention. Retry the previous step."
}

private fun formatMegabytes(bytes: Long): String = "%.1f MB".format(bytes / 1_000_000f)
