package com.localdictation.keyboard.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

class AudioRecorder {
    @Volatile
    private var activeRecorder: AudioRecord? = null

    @Volatile
    private var stopRequested = false

    suspend fun record(
        onElapsedMillis: (Long) -> Unit,
    ): AudioBuffer = withContext(Dispatchers.IO) {
        stopRequested = false
        val minBufferBytes = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBufferBytes <= 0) throw IllegalStateException("Microphone audio could not be initialized.")

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBufferBytes, READ_SAMPLES * Short.SIZE_BYTES * 2),
        )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            throw IllegalStateException("Microphone is unavailable.")
        }

        activeRecorder = recorder
        val samples = ShortArray(MAX_SAMPLES)
        val chunk = ShortArray(READ_SAMPLES)
        var totalSamples = 0

        try {
            recorder.startRecording()
            while (totalSamples < MAX_SAMPLES && !stopRequested) {
                coroutineContext.ensureActive()
                val amount = recorder.read(chunk, 0, minOf(chunk.size, MAX_SAMPLES - totalSamples))
                if (amount > 0) {
                    chunk.copyInto(samples, destinationOffset = totalSamples, endIndex = amount)
                    totalSamples += amount
                    onElapsedMillis(totalSamples * 1_000L / SAMPLE_RATE)
                } else if (stopRequested) {
                    break
                } else if (amount == AudioRecord.ERROR_DEAD_OBJECT || amount == AudioRecord.ERROR_INVALID_OPERATION) {
                    throw IllegalStateException("Microphone stopped unexpectedly.")
                } else {
                    throw IllegalStateException("Microphone audio could not be read.")
                }
            }
            AudioBuffer(samples.copyOf(totalSamples))
        } finally {
            activeRecorder = null
            try {
                if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop()
            } catch (_: IllegalStateException) {
                // A manual stop or a cancelled IME session can already have stopped it.
            }
            recorder.release()
        }
    }

    fun requestStop() {
        stopRequested = true
        stopActiveRecorder()
    }

    fun cancel() {
        stopRequested = true
        stopActiveRecorder()
    }

    private fun stopActiveRecorder() {
        try {
            activeRecorder?.let { recorder ->
                if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop()
            }
        } catch (_: IllegalStateException) {
            // The recorder may have finished between checking and stopping.
        }
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        const val MAX_DURATION_MILLIS = 30_000
        private const val MAX_SAMPLES = SAMPLE_RATE * MAX_DURATION_MILLIS / 1_000
        private const val READ_SAMPLES = 2_048
    }
}
