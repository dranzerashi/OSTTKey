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

    /** The existing fixed-length recording path, kept for Classic mode. */
    suspend fun record(
        onElapsedMillis: (Long) -> Unit,
    ): AudioBuffer = withContext(Dispatchers.IO) {
        stopRequested = false
        val recorder = createRecorder()
        val samples = ShortArray(MAX_DURATION_MILLIS * SAMPLE_RATE / 1_000)
        val chunk = ShortArray(READ_SAMPLES)
        var totalSamples = 0

        try {
            recorder.startRecording()
            while (totalSamples < samples.size && !stopRequested) {
                coroutineContext.ensureActive()
                val amount = recorder.read(chunk, 0, minOf(chunk.size, samples.size - totalSamples))
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
            releaseRecorder(recorder)
        }
    }

    /**
     * Captures continuously, publishing VAD-delimited speech buffers while the mic remains active.
     * Returning false from [onSegment] stops capture instead of silently dropping speech.
     */
    suspend fun recordPauseAware(
        onElapsedMillis: (Long) -> Unit,
        onSegment: (AudioBuffer) -> Boolean,
    ) {
        stopRequested = false
        withContext(Dispatchers.IO) {
            val recorder = createRecorder()
            val segmenter = PauseAwareAudioSegmenter()
            val chunk = ShortArray(READ_SAMPLES)
            val frame = ShortArray(PauseAwareAudioSegmenter.FRAME_SAMPLES)
            var frameSize = 0
            var totalSamples = 0

            fun publish(buffer: AudioBuffer?) {
                if (buffer != null && !onSegment(buffer)) {
                    throw IllegalStateException("Transcription could not keep up with live audio.")
                }
            }

            try {
                recorder.startRecording()
                recordingLoop@ while (!stopRequested && !segmenter.shouldStop) {
                    coroutineContext.ensureActive()
                    val amount = recorder.read(chunk, 0, chunk.size)
                    if (amount > 0) {
                        totalSamples += amount
                        onElapsedMillis(totalSamples * 1_000L / SAMPLE_RATE)
                        for (index in 0 until amount) {
                            frame[frameSize++] = chunk[index]
                            if (frameSize == frame.size) {
                                publish(segmenter.acceptFrame(frame, frameSize))
                                frameSize = 0
                                if (segmenter.shouldStop) break@recordingLoop
                            }
                        }
                    } else if (stopRequested) {
                        break
                    } else if (amount == AudioRecord.ERROR_DEAD_OBJECT || amount == AudioRecord.ERROR_INVALID_OPERATION) {
                        throw IllegalStateException("Microphone stopped unexpectedly.")
                    } else {
                        throw IllegalStateException("Microphone audio could not be read.")
                    }
                }

                if (frameSize > 0) publish(segmenter.acceptFrame(frame, frameSize))
                publish(segmenter.finish())
                if (segmenter.stoppedWithoutSafePause) {
                    throw IllegalStateException("No natural pause was found before Whistle's segment limit.")
                }
            } finally {
                releaseRecorder(recorder)
            }
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

    private fun createRecorder(): AudioRecord {
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
        return recorder
    }

    private fun releaseRecorder(recorder: AudioRecord) {
        if (activeRecorder === recorder) activeRecorder = null
        try {
            if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop()
        } catch (_: IllegalStateException) {
            // A manual stop or a cancelled IME session can already have stopped it.
        }
        recorder.release()
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
        private const val READ_SAMPLES = 2_048
    }
}
