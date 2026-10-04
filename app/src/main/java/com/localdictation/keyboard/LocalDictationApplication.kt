package com.localdictation.keyboard

import android.app.Application
import com.localdictation.keyboard.audio.AudioRecorder
import com.localdictation.keyboard.model.WhistleModelManager
import com.localdictation.keyboard.stt.WhistleSpeechRecognizer

class LocalDictationApplication : Application() {
    lateinit var modelManager: WhistleModelManager
        private set

    lateinit var audioRecorder: AudioRecorder
        private set

    lateinit var speechRecognizer: WhistleSpeechRecognizer
        private set

    override fun onCreate() {
        super.onCreate()
        modelManager = WhistleModelManager(this)
        audioRecorder = AudioRecorder()
        speechRecognizer = WhistleSpeechRecognizer(modelManager)
    }
}
