package com.localdictation.keyboard.stt

internal object WhistleNative {
    init {
        System.loadLibrary("whistle_jni")
    }

    external fun loadModel(modelPath: String): String?
    external fun transcribe(pcmFloat32: FloatArray): String
}
