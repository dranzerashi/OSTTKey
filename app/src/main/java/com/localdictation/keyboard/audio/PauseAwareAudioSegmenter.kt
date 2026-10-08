package com.localdictation.keyboard.audio

import java.util.ArrayDeque
import kotlin.math.max
import kotlin.math.sqrt

/** RMS-based voice activity and pause segmentation for 16 kHz mono PCM. */
internal class PauseAwareAudioSegmenter {
    private val preRollFrames = ArrayDeque<ShortArray>()
    private val segmentSamples = ShortArrayAccumulator()

    private var noiseFloorRms = INITIAL_NOISE_FLOOR_RMS
    private var consecutiveVoiceFrames = 0
    private var silenceSamples = 0L
    private var segmentElapsedSamples = 0L
    private var speechActive = false

    var shouldStop: Boolean = false
        private set

    var stoppedWithoutSafePause: Boolean = false
        private set

    /** Returns a segment after enough audio has accumulated and a short natural pause is detected. */
    fun acceptFrame(frame: ShortArray, sampleCount: Int): AudioBuffer? {
        if (sampleCount <= 0 || shouldStop) return null

        val rms = calculateRms(frame, sampleCount)
        val isVoice = rms >= max(MIN_VOICE_RMS, noiseFloorRms * NOISE_MULTIPLIER)

        if (speechActive) {
            segmentElapsedSamples += sampleCount
            if (isVoice) {
                silenceSamples = 0L
                segmentSamples.append(frame, sampleCount)
            } else {
                silenceSamples += sampleCount
                if (silenceSamples <= TRAILING_AUDIO_SAMPLES) {
                    segmentSamples.append(frame, sampleCount)
                }
            }
            rememberPreRoll(frame, sampleCount)

            if (segmentElapsedSamples >= MIN_SEGMENT_SAMPLES &&
                silenceSamples >= SEGMENT_PAUSE_SAMPLES
            ) return closeSegment()

            if (segmentElapsedSamples >= HARD_SEGMENT_LIMIT_SAMPLES) {
                // Never submit an over-limit chunk or cut an active word at an arbitrary boundary.
                shouldStop = true
                stoppedWithoutSafePause = true
                speechActive = false
                segmentSamples.clear()
                segmentElapsedSamples = 0L
            }
            return null
        }

        rememberPreRoll(frame, sampleCount)
        if (isVoice) {
            silenceSamples = 0L
            consecutiveVoiceFrames++
            if (consecutiveVoiceFrames >= SPEECH_START_FRAMES) {
                speechActive = true
                consecutiveVoiceFrames = 0
                segmentSamples.clear()
                preRollFrames.forEach { segmentSamples.append(it, it.size) }
                segmentElapsedSamples = preRollFrames.sumOf { it.size.toLong() }
            }
        } else {
            consecutiveVoiceFrames = 0
            silenceSamples += sampleCount
            noiseFloorRms += (rms - noiseFloorRms) * NOISE_FLOOR_ADAPTATION
            noiseFloorRms = noiseFloorRms.coerceIn(MIN_NOISE_FLOOR_RMS, MAX_NOISE_FLOOR_RMS)
            if (silenceSamples >= STOP_SILENCE_SAMPLES) shouldStop = true
        }
        return null
    }

    /** Flushes the final partial utterance when the user taps the mic to stop. */
    fun finish(): AudioBuffer? = if (speechActive && !stoppedWithoutSafePause) closeSegment() else null

    private fun closeSegment(): AudioBuffer? {
        speechActive = false
        consecutiveVoiceFrames = 0
        segmentElapsedSamples = 0L
        if (segmentSamples.size == 0) return null
        val audio = AudioBuffer(segmentSamples.toShortArray())
        segmentSamples.clear()
        return audio
    }

    private fun rememberPreRoll(frame: ShortArray, sampleCount: Int) {
        preRollFrames.addLast(frame.copyOf(sampleCount))
        while (preRollFrames.size > PRE_ROLL_FRAME_COUNT) preRollFrames.removeFirst()
    }

    private fun calculateRms(frame: ShortArray, sampleCount: Int): Float {
        var sumSquares = 0.0
        for (index in 0 until sampleCount) {
            val value = frame[index].toDouble()
            sumSquares += value * value
        }
        return sqrt(sumSquares / sampleCount).toFloat()
    }

    companion object {
        const val FRAME_SAMPLES = AudioRecorder.SAMPLE_RATE / 50 // 20 ms frames

        private const val PRE_ROLL_FRAME_COUNT = 10 // 200 ms
        private const val SPEECH_START_FRAMES = 3
        private const val MIN_VOICE_RMS = 500f
        private const val INITIAL_NOISE_FLOOR_RMS = 120f
        private const val MIN_NOISE_FLOOR_RMS = 20f
        private const val MAX_NOISE_FLOOR_RMS = 6_000f
        private const val NOISE_MULTIPLIER = 2.2f
        private const val NOISE_FLOOR_ADAPTATION = 0.08f
        private const val TRAILING_AUDIO_MILLIS = 250L
        private const val MIN_SEGMENT_MILLIS = 2_000L
        private const val SEGMENT_PAUSE_MILLIS = 220L
        private const val STOP_SILENCE_MILLIS = 5_000L
        private const val HARD_SEGMENT_LIMIT_MILLIS = 29_960L

        private val TRAILING_AUDIO_SAMPLES = millisecondsToSamples(TRAILING_AUDIO_MILLIS)
        private val MIN_SEGMENT_SAMPLES = millisecondsToSamples(MIN_SEGMENT_MILLIS)
        private val SEGMENT_PAUSE_SAMPLES = millisecondsToSamples(SEGMENT_PAUSE_MILLIS)
        private val STOP_SILENCE_SAMPLES = millisecondsToSamples(STOP_SILENCE_MILLIS)
        private val HARD_SEGMENT_LIMIT_SAMPLES = millisecondsToSamples(HARD_SEGMENT_LIMIT_MILLIS)

        private fun millisecondsToSamples(milliseconds: Long): Long =
            milliseconds * AudioRecorder.SAMPLE_RATE / 1_000L
    }
}

private class ShortArrayAccumulator {
    private var storage = ShortArray(PauseAwareAudioSegmenter.FRAME_SAMPLES * 32)
    var size: Int = 0
        private set

    fun append(samples: ShortArray, count: Int) {
        if (count <= 0) return
        ensureCapacity(size + count)
        samples.copyInto(storage, destinationOffset = size, endIndex = count)
        size += count
    }

    fun toShortArray(): ShortArray = storage.copyOf(size)

    fun clear() {
        size = 0
    }

    private fun ensureCapacity(required: Int) {
        if (required <= storage.size) return
        var capacity = storage.size
        while (capacity < required) capacity = (capacity * 2).coerceAtLeast(required)
        storage = storage.copyOf(capacity)
    }
}
