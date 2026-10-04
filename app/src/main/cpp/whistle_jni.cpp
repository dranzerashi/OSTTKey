#include <jni.h>

#include "needle.h"

#include <fstream>
#include <mutex>
#include <string>
#include <utility>
#include <vector>

namespace {
std::mutex engineMutex;
// The Cactus engine reads .cact tensors in place, so keep the source bytes alive
// for as long as its process-global speech model can be used.
std::vector<unsigned char> loadedModelBytes;

jstring toJavaString(JNIEnv* env, const std::string& value) {
    return env->NewStringUTF(value.c_str());
}

std::string lastEngineError() {
    const char* error = needle_last_error();
    return error == nullptr || error[0] == '\0' ? "Cactus engine operation failed" : error;
}
}  // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_localdictation_keyboard_stt_WhistleNative_loadModel(JNIEnv* env, jobject, jstring modelPath) {
    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    if (path == nullptr) return nullptr;

    std::ifstream input(path, std::ios::binary | std::ios::ate);
    env->ReleaseStringUTFChars(modelPath, path);
    if (!input) return toJavaString(env, "Whistle model file could not be opened");

    const auto length = input.tellg();
    if (length <= 0) return toJavaString(env, "Whistle model file is empty");
    input.seekg(0, std::ios::beg);
    std::vector<unsigned char> bytes(static_cast<size_t>(length));
    if (!input.read(reinterpret_cast<char*>(bytes.data()), static_cast<std::streamsize>(length))) {
        return toJavaString(env, "Whistle model file could not be read");
    }

    std::lock_guard<std::mutex> lock(engineMutex);
    if ((needle_models() & NEEDLE_SPEECH) != 0) return nullptr;

    loadedModelBytes = std::move(bytes);
    if (needle_load(loadedModelBytes.data(), static_cast<unsigned long long>(loadedModelBytes.size())) < 0) {
        return toJavaString(env, lastEngineError());
    }
    if ((needle_models() & NEEDLE_SPEECH) == 0) {
        return toJavaString(env, "The downloaded model did not contain a speech model");
    }
    return nullptr;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_localdictation_keyboard_stt_WhistleNative_transcribe(JNIEnv* env, jobject, jfloatArray pcmArray) {
    if (pcmArray == nullptr) return toJavaString(env, "No audio was captured");

    const jsize sampleCount = env->GetArrayLength(pcmArray);
    if (sampleCount <= 0) return toJavaString(env, "No audio was captured");

    std::vector<jfloat> pcm(static_cast<size_t>(sampleCount));
    env->GetFloatArrayRegion(pcmArray, 0, sampleCount, pcm.data());
    if (env->ExceptionCheck()) return nullptr;

    std::vector<char> response(64 * 1024, '\0');
    std::lock_guard<std::mutex> lock(engineMutex);
    if ((needle_models() & NEEDLE_SPEECH) == 0) {
        return toJavaString(env, "Whistle is not initialized");
    }

    const int result = needle_transcribe(
        pcm.data(),
        static_cast<int>(sampleCount),
        nullptr,
        nullptr,
        0,
        response.data(),
        static_cast<int>(response.size()));
    if (result < 0) return toJavaString(env, lastEngineError());
    return toJavaString(env, std::string(response.data()));
}
