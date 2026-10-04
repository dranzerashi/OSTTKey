package com.localdictation.keyboard.stt

import com.localdictation.keyboard.audio.AudioBuffer
import com.localdictation.keyboard.model.WhistleModelManager
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WhistleSpeechRecognizer(
    private val modelManager: WhistleModelManager,
) : SpeechRecognizer {
    override suspend fun transcribe(audio: AudioBuffer): TranscriptionResult {
        require(audio.sampleRate == 16_000) { "Whistle requires 16 kHz audio." }
        require(audio.samples.isNotEmpty()) { "No audio was captured." }

        modelManager.ensureReady()
        return withContext(Dispatchers.Default) {
            val normalizedAudio = FloatArray(audio.samples.size) { index ->
                audio.samples[index] / PCM16_SCALE
            }
            val response = try {
                JSONObject(WhistleNative.transcribe(normalizedAudio))
            } catch (error: Exception) {
                throw IllegalStateException(error.message ?: "Whistle could not transcribe this audio.", error)
            }
            TranscriptionResult(
                text = response.optString("text"),
                language = response.optString("language").takeIf { it.isNotBlank() },
                timeToFirstTokenMillis = response.optDouble("ttft_ms").takeUnless { it.isNaN() },
                decodeTokensPerSecond = response.optDouble("decode_tps").takeUnless { it.isNaN() },
            )
        }
    }

    companion object {
        private const val PCM16_SCALE = 32_768f
    }
}
