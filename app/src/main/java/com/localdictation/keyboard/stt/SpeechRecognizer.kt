package com.localdictation.keyboard.stt

import com.localdictation.keyboard.audio.AudioBuffer

interface SpeechRecognizer {
    suspend fun transcribe(audio: AudioBuffer): TranscriptionResult
}

data class TranscriptionResult(
    val text: String,
    val language: String?,
    val timeToFirstTokenMillis: Double?,
    val decodeTokensPerSecond: Double?,
)
