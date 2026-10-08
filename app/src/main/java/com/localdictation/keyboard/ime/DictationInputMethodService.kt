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
import com.localdictation.keyboard.DictationModePreferences
import com.localdictation.keyboard.audio.AudioBuffer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

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
        val pauseAwareMode = DictationModePreferences.isPauseAwareEnabled(this)
        keyboardState.value = DictationKeyboardState.Recording(
            elapsedMillis = 0,
            isPauseAware = pauseAwareMode,
        )
        dictationJob = serviceScope.launch {
            try {
                if (pauseAwareMode) runPauseAwareDictation(generation)
                else runClassicDictation(generation)
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

    private suspend fun runClassicDictation(generation: Int) {
        val audio = app.audioRecorder.record { elapsed ->
            if (generation == sessionGeneration) {
                keyboardState.value = DictationKeyboardState.Recording(elapsed)
            }
        }
        currentCoroutineContext().ensureActive()
        if (generation != sessionGeneration) return
        if (audio.samples.isEmpty()) throw IllegalStateException("No speech was captured.")

        keyboardState.value = DictationKeyboardState.Processing
        val result = app.speechRecognizer.transcribe(audio)
        currentCoroutineContext().ensureActive()
        if (generation != sessionGeneration) return
        if (result.text.isBlank()) {
            keyboardState.value = DictationKeyboardState.Error("No speech detected. Tap to try again.")
        } else if (inputController.insertAtCursor(result.text)) {
            keyboardState.value = DictationKeyboardState.Inserted
        } else {
            keyboardState.value = DictationKeyboardState.Error("The text field is no longer available.")
        }
    }

    private suspend fun runPauseAwareDictation(generation: Int) = coroutineScope {
        val audioSegments = Channel<AudioBuffer>(capacity = MAX_QUEUED_AUDIO_SEGMENTS)
        val transcriptionRunning = AtomicBoolean(false)
        val recorderJob = launch {
            try {
                app.audioRecorder.recordPauseAware(
                    onElapsedMillis = { elapsed ->
                        if (generation == sessionGeneration) {
                            keyboardState.value = DictationKeyboardState.Recording(
                                elapsedMillis = elapsed,
                                isTranscribing = transcriptionRunning.get(),
                                isPauseAware = true,
                            )
                        }
                    },
                    onSegment = { segment -> audioSegments.trySend(segment).isSuccess },
                )
                audioSegments.close()
            } catch (cancelled: CancellationException) {
                audioSegments.cancel(cancelled)
                throw cancelled
            } catch (error: Exception) {
                audioSegments.close(error)
            } catch (error: LinkageError) {
                audioSegments.close(error)
            }
        }

        var insertedTranscript = false
        try {
            for (segment in audioSegments) {
                currentCoroutineContext().ensureActive()
                if (generation != sessionGeneration) return@coroutineScope

                transcriptionRunning.set(true)
                updatePauseAwareTranscribingState(generation, true)
                val result = app.speechRecognizer.transcribe(segment)
                currentCoroutineContext().ensureActive()
                if (generation != sessionGeneration) return@coroutineScope

                val text = result.text.trim()
                if (text.isNotEmpty()) {
                    val insertion = if (insertedTranscript && text.first().isLetterOrDigit()) " $text" else text
                    if (!inputController.insertAtCursor(insertion)) {
                        throw IllegalStateException("The text field is no longer available.")
                    }
                    insertedTranscript = true
                }
                transcriptionRunning.set(false)
                updatePauseAwareTranscribingState(generation, false)
            }

            recorderJob.join()
            currentCoroutineContext().ensureActive()
            if (generation != sessionGeneration) return@coroutineScope
            keyboardState.value = if (insertedTranscript) {
                DictationKeyboardState.Inserted
            } else {
                DictationKeyboardState.Error("No speech detected. Tap to try again.")
            }
        } catch (cancelled: CancellationException) {
            app.audioRecorder.cancel()
            recorderJob.cancelAndJoin()
            throw cancelled
        } catch (error: Exception) {
            app.audioRecorder.cancel()
            recorderJob.cancelAndJoin()
            throw error
        } catch (error: LinkageError) {
            app.audioRecorder.cancel()
            recorderJob.cancelAndJoin()
            throw error
        } finally {
            transcriptionRunning.set(false)
            audioSegments.cancel()
        }
    }

    private fun updatePauseAwareTranscribingState(generation: Int, isTranscribing: Boolean) {
        if (generation != sessionGeneration) return
        val recording = keyboardState.value as? DictationKeyboardState.Recording ?: return
        keyboardState.value = recording.copy(isTranscribing = isTranscribing, isPauseAware = true)
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
            text.contains("could not keep up", ignoreCase = true) -> "Transcription fell behind. Tap to start a new recording."
            text.contains("no natural pause", ignoreCase = true) -> "No safe speech pause before Whistle's limit. The last segment was not transcribed; tap to record again."
            else -> "Unable to transcribe. Tap to retry."
        }
    }

    private companion object {
        const val MAX_QUEUED_AUDIO_SEGMENTS = 4
    }
}
