package com.localdictation.keyboard.model

enum class ModelState {
    NOT_AVAILABLE,
    DOWNLOADING,
    AVAILABLE,
    LOADING,
    READY,
    ERROR,
}

data class ModelSnapshot(
    val state: ModelState,
    val bytesDownloaded: Long = 0,
    val totalBytes: Long = WhistleModelManager.MODEL_SIZE_BYTES,
    val error: String? = null,
)
