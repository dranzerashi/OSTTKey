package com.localdictation.keyboard.audio

data class AudioBuffer(
    val samples: ShortArray,
    val sampleRate: Int = AudioRecorder.SAMPLE_RATE,
)
