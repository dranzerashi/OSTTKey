package com.localdictation.keyboard

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.localdictation.keyboard.ime.DictationInputMethodService

class MainActivity : ComponentActivity() {
    private var keyboardStatus by mutableStateOf(KeyboardStatus())
    private var microphonePermissionRequestId by mutableIntStateOf(0)
    private var showCredits by mutableStateOf(false)
    private var showHowToUse by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        keyboardStatus = readKeyboardStatus()
        if (intent.getBooleanExtra(EXTRA_REQUEST_MICROPHONE_PERMISSION, false)) {
            microphonePermissionRequestId++
        }
        intent.removeExtra(EXTRA_REQUEST_MICROPHONE_PERMISSION)
        setContent {
            LocalDictationTheme {
                if (showCredits) {
                    CreditsScreen(onBack = { showCredits = false })
                } else if (showHowToUse) {
                    HowToUseScreen(onBack = { showHowToUse = false })
                } else {
                    SettingsScreen(
                        modelManager = (application as LocalDictationApplication).modelManager,
                        keyboardStatus = keyboardStatus,
                        microphonePermissionRequestId = microphonePermissionRequestId,
                        onKeyboardSettings = {
                            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                        },
                        onChooseKeyboard = {
                            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
                        },
                        onOpenHowToUse = { showHowToUse = true },
                        onOpenCredits = { showCredits = true },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_REQUEST_MICROPHONE_PERMISSION, false)) {
            microphonePermissionRequestId++
        }
        intent.removeExtra(EXTRA_REQUEST_MICROPHONE_PERMISSION)
    }

    override fun onResume() {
        super.onResume()
        refreshKeyboardStatus()
    }

    private fun refreshKeyboardStatus() {
        keyboardStatus = readKeyboardStatus()
    }

    private fun readKeyboardStatus(): KeyboardStatus {
        val component = ComponentName(this, DictationInputMethodService::class.java)
        val flattened = component.flattenToString()
        val enabled = (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
            .enabledInputMethodList
            .any { it.packageName == component.packageName && it.serviceName == component.className }
        val selected = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) == flattened
        return KeyboardStatus(enabled, selected)
    }

    companion object {
        const val EXTRA_REQUEST_MICROPHONE_PERMISSION = "com.localdictation.keyboard.REQUEST_MICROPHONE_PERMISSION"
    }
}

data class KeyboardStatus(
    val enabled: Boolean = false,
    val selected: Boolean = false,
)
