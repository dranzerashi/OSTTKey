package com.localdictation.keyboard.model

import android.content.Context
import com.localdictation.keyboard.stt.WhistleNative
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class WhistleModelManager(context: Context) {
    private val appContext = context.applicationContext
    private val modelDirectory = File(appContext.filesDir, "models/whistle").apply { mkdirs() }
    private val modelFile = File(modelDirectory, MODEL_FILENAME)
    private val operationMutex = Mutex()

    private val mutableSnapshot = MutableStateFlow(
        ModelSnapshot(if (modelFile.isFile && modelFile.length() > 0) ModelState.AVAILABLE else ModelState.NOT_AVAILABLE)
    )
    val snapshot: StateFlow<ModelSnapshot> = mutableSnapshot.asStateFlow()

    suspend fun downloadModel() = operationMutex.withLock {
        if (modelFile.isFile && modelFile.length() > 0) {
            if (mutableSnapshot.value.state != ModelState.READY) {
                mutableSnapshot.value = ModelSnapshot(ModelState.AVAILABLE)
            }
            return@withLock
        }

        mutableSnapshot.value = ModelSnapshot(ModelState.DOWNLOADING)
        val partialFile = File(modelDirectory, "$MODEL_FILENAME.part")
        try {
            withContext(Dispatchers.IO) {
                downloadAtomically(partialFile)
            }
            mutableSnapshot.value = ModelSnapshot(ModelState.AVAILABLE)
        } catch (error: Exception) {
            partialFile.delete()
            mutableSnapshot.value = ModelSnapshot(
                state = ModelState.ERROR,
                error = error.message ?: "The Whistle model could not be downloaded.",
            )
            throw error
        }
    }

    suspend fun ensureReady() = operationMutex.withLock {
        if (mutableSnapshot.value.state == ModelState.READY) return@withLock
        if (!modelFile.isFile || modelFile.length() == 0L) {
            mutableSnapshot.value = ModelSnapshot(ModelState.NOT_AVAILABLE)
            throw IllegalStateException("Whistle model isn't installed yet. Open Dictation Settings to download it.")
        }

        mutableSnapshot.value = ModelSnapshot(ModelState.LOADING)
        try {
            val error = withContext(Dispatchers.Default) {
                WhistleNative.loadModel(modelFile.absolutePath)
            }
            if (error != null) throw IllegalStateException(error)
            mutableSnapshot.value = ModelSnapshot(ModelState.READY)
        } catch (error: Exception) {
            mutableSnapshot.value = ModelSnapshot(
                state = ModelState.ERROR,
                error = error.message ?: "Whistle could not be initialized.",
            )
            throw error
        } catch (error: LinkageError) {
            mutableSnapshot.value = ModelSnapshot(
                state = ModelState.ERROR,
                error = "The Whistle runtime is unavailable for this device.",
            )
            throw IllegalStateException("The Whistle runtime could not be loaded.", error)
        }
    }

    fun isModelAvailable(): Boolean = modelFile.isFile && modelFile.length() > 0

    private suspend fun downloadAtomically(partialFile: File) {
        val connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            requestMethod = "GET"
        }

        try {
            connection.connect()
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("Model download failed (${connection.responseCode}).")
            }

            val expectedLength = connection.contentLengthLong.takeIf { it > 0 } ?: MODEL_SIZE_BYTES
            val digest = MessageDigest.getInstance("SHA-256")
            var downloaded = 0L
            connection.inputStream.buffered().use { input ->
                FileOutputStream(partialFile).buffered().use { output ->
                    val buffer = ByteArray(DOWNLOAD_BUFFER_BYTES)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                        downloaded += count
                        if (downloaded > MAX_MODEL_DOWNLOAD_BYTES) {
                            throw IllegalStateException("The model download exceeded its expected size.")
                        }
                        mutableSnapshot.value = ModelSnapshot(
                            state = ModelState.DOWNLOADING,
                            bytesDownloaded = downloaded,
                            totalBytes = expectedLength,
                        )
                    }
                }
            }

            val hash = digest.digest().joinToString("") {
                (it.toInt() and 0xff).toString(16).padStart(2, '0')
            }
            if (!hash.equals(MODEL_SHA256, ignoreCase = true)) {
                throw IllegalStateException("The downloaded model failed its integrity check.")
            }
            if (!partialFile.renameTo(modelFile)) {
                partialFile.copyTo(modelFile, overwrite = true)
                partialFile.delete()
            }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val MODEL_SIZE_BYTES = 16_900_000L
        const val MODEL_SIZE_LABEL = "16.9 MB"

        private const val DOWNLOAD_BUFFER_BYTES = 64 * 1024
        private const val MAX_MODEL_DOWNLOAD_BYTES = 20_000_000L
        private const val MODEL_FILENAME = "whistle.cact"
        private const val MODEL_URL =
            "https://huggingface.co/Cactus-Compute/whistle/resolve/d3ea19e/whistle.cact?download=true"
        private const val MODEL_SHA256 = "b6e02f048568ac5d01a2042556c658061e699acbc0aa2a1439f52f3d461dffeb"
    }
}
