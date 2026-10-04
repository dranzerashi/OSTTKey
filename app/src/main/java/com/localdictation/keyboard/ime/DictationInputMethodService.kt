package com.localdictation.keyboard.ime

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.text.InputType
import android.view.View
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import android.inputmethodservice.InputMethodService
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.localdictation.keyboard.LocalDictationApplication
import com.localdictation.keyboard.MainActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class DictationInputMethodService : InputMethodService(), SavedStateRegistryOwner {
    private val lifecycleRegistry by lazy { LifecycleRegistry(this) }
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    private val savedStateRegistryController by lazy { SavedStateRegistryController.create(this) }
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val keyboardState = MutableStateFlow<DictationKeyboardState>(DictationKeyboardState.Idle)
    private val inputController = InputConnectionController { currentInputConnection }

    private var dictationJob: Job? = null
    private var sessionGeneration = 0

    private val app: LocalDictationApplication
        get() = application as LocalDictationApplication

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        // Compose resolves its window recomposer from the IME window's decor view. The
        // InputMethodService window does not install lifecycle tree owners like an Activity,
        // so setting the owner only on ComposeView is not enough for that lookup.
        getWindow().window?.decorView?.apply {
            setViewTreeLifecycleOwner(this@DictationInputMethodService)
            setViewTreeSavedStateRegistryOwner(this@DictationInputMethodService)
        }
    }

    override fun onCreateInputView(): View {
        return ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setViewTreeLifecycleOwner(this@DictationInputMethodService)
            setViewTreeSavedStateRegistryOwner(this@DictationInputMethodService)
            setContent {
                val state by keyboardState.collectAsState()
                DictationKeyboardView(
                    state = state,
                    onMicrophoneTap = ::onMicrophoneTap,
                    onEnterTap = ::onEnterTap,
                    onBackspaceTap = { inputController.deleteLastWord() },
                    onWordSelectionStarted = { inputController.beginWordSelection() },
                    onWordSelectionChanged = { inputController.updateWordSelection(it) },
                    onWordSelectionFinished = { inputController.finishWordSelection(it) },
                    onOpenSettings = ::openSettings,
                )
            }
        }
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        stopActiveWork()
        sessionGeneration++
        keyboardState.value = DictationKeyboardState.Idle
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (lifecycleRegistry.currentState == Lifecycle.State.CREATED ||
            lifecycleRegistry.currentState == Lifecycle.State.DESTROYED
        ) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        }
        if (lifecycleRegistry.currentState == Lifecycle.State.STARTED) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        stopActiveWork()
        if (lifecycleRegistry.currentState == Lifecycle.State.RESUMED) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        }
        if (lifecycleRegistry.currentState == Lifecycle.State.STARTED) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        }
        super.onFinishInputView(finishingInput)
    }

    override fun onFinishInput() {
        stopActiveWork()
        sessionGeneration++
        super.onFinishInput()
    }

    override fun onWindowHidden() {
        stopActiveWork()
        super.onWindowHidden()
    }

    override fun onDestroy() {
        stopActiveWork()
        if (lifecycleRegistry.currentState != Lifecycle.State.DESTROYED) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun onMicrophoneTap() {
        when (keyboardState.value) {
            is DictationKeyboardState.Recording -> app.audioRecorder.requestStop()
            DictationKeyboardState.Processing -> Unit
            else -> startDictation()
        }
    }

    private fun onEnterTap() {
        val editorInfo = currentInputEditorInfo
        val action = editorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
        val isMultiline = editorInfo?.let {
            (it.inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0
        } == true
        val shouldPerformEditorAction = editorInfo != null &&
            !isMultiline &&
            (editorInfo.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) == 0 &&
            action != EditorInfo.IME_ACTION_NONE &&
            action != EditorInfo.IME_ACTION_UNSPECIFIED

        if (shouldPerformEditorAction) {
            currentInputConnection?.performEditorAction(action!!)
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        }
    }

    private fun startDictation() {
        if (currentInputConnection == null) {
            keyboardState.value = DictationKeyboardState.Error("Focus a text field to dictate.")
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            keyboardState.value = DictationKeyboardState.PermissionRequired
            requestMicrophonePermission()
            return
        }
        if (!app.modelManager.isModelAvailable()) {
            keyboardState.value = DictationKeyboardState.ModelRequired
            return
        }

        val generation = ++sessionGeneration
        keyboardState.value = DictationKeyboardState.Recording(0)
        dictationJob = serviceScope.launch {
            try {
                val audio = app.audioRecorder.record { elapsed ->
                    if (generation == sessionGeneration) {
                        keyboardState.value = DictationKeyboardState.Recording(elapsed)
                    }
                }
                currentCoroutineContext().ensureActive()
                if (generation != sessionGeneration) return@launch
                if (audio.samples.isEmpty()) throw IllegalStateException("No speech was captured.")

                keyboardState.value = DictationKeyboardState.Processing
                val result = app.speechRecognizer.transcribe(audio)
                currentCoroutineContext().ensureActive()
                if (generation != sessionGeneration) return@launch
                if (result.text.isBlank()) {
                    keyboardState.value = DictationKeyboardState.Error("No speech detected. Tap to try again.")
                } else if (inputController.insertAtCursor(result.text)) {
                    keyboardState.value = DictationKeyboardState.Inserted
                } else {
                    keyboardState.value = DictationKeyboardState.Error("The text field is no longer available.")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (generation == sessionGeneration) {
                    keyboardState.value = DictationKeyboardState.Error(userMessage(error))
                }
            } catch (error: LinkageError) {
                if (generation == sessionGeneration) {
                    keyboardState.value = DictationKeyboardState.Error("Whistle is unavailable on this device.")
                }
            }
        }
    }

    private fun requestMicrophonePermission() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_REQUEST_MICROPHONE_PERMISSION, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
    }

    private fun openSettings() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
    }

    private fun stopActiveWork() {
        sessionGeneration++
        inputController.cancelWordSelection()
        if (keyboardState.value is DictationKeyboardState.Recording) {
            app.audioRecorder.cancel()
        }
        dictationJob?.cancel()
        dictationJob = null
        if (keyboardState.value !is DictationKeyboardState.Idle) {
            keyboardState.value = DictationKeyboardState.Idle
        }
    }

    private fun userMessage(error: Exception): String {
        val text = error.message.orEmpty()
        return when {
            text.contains("model isn't installed", ignoreCase = true) -> "Download Whistle in Dictation Settings first."
            text.contains("microphone", ignoreCase = true) -> "Microphone unavailable. Check microphone access."
            else -> "Unable to transcribe. Tap to retry."
        }
    }
}
