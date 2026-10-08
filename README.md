# OSTTKey - (Offline Speech to text Keyboard)

An Android system keyboard for on-device speech-to-text dictation. The app captures 16 kHz mono PCM16 audio with `AudioRecord`, converts samples to the Cactus runtime's float PCM format, transcribes through Whistle, and inserts the result with the active IME `InputConnection`.

## How to use

1. Install OSTTKey from the [GitHub Releases](https://github.com/dranzerashi/OSTTKey/releases) page and open the app.
2. Download and initialize the Whistle model. The download requires an internet connection; transcription runs on the device afterward.
3. Tap **Enable as keyboard**, enable OSTTKey in Android's keyboard settings, then select OSTTKey as the active keyboard.
4. Focus a text field, tap the microphone key, and speak. Classic mode stops at 30 seconds. Enable pause-aware continuous dictation in app settings to transcribe segments after pauses while continuing to listen.

Whistle supports **English, German, French, Spanish, Italian, Dutch, and Polish**.

## Open and build

Open this folder in Android Studio and sync the Gradle project. The project targets ARM64 (`arm64-v8a`) and needs Android SDK 37, NDK 28.2.13676358, and CMake 3.22.1. The first native build downloads Cactus's published Android ARM64 static runtime and verifies its SHA-256 before linking it into the app's JNI bridge. Keep network access available during that build.

From a shell with a working Android SDK installation:

```sh
./gradlew assembleDebug
```

Install the debug APK, open OSTTKey, download and initialize Whistle, then enable it from **Enable as keyboard**. Android requires the user to select the IME; the app never changes the default keyboard silently. The settings screen includes a text field for trying the IME.

Microphone permission is requested only after the user taps the microphone or the permission control in settings. Classic dictation stops after 30 seconds. Pause-aware mode begins checking for a short natural pause after about 2 seconds of audio, transcribes at that pause while continuing to listen, and stops after 5 seconds without speech. Segments stay below the model's 30-second limit; if no safe pause is found before the limit, recording stops rather than splitting active speech. Either mode stops if its input session is hidden or finished.

## Cactus and Whistle

- Runtime: the published `android-arm64/libneedle.a` and `needle.h` from [Cactus-Compute/needle3](https://huggingface.co/Cactus-Compute/needle3), pinned to runtime revision `f84005f` and checked by SHA-256 at CMake configuration.
- Model: `whistle.cact` from [Cactus-Compute/whistle](https://huggingface.co/Cactus-Compute/whistle), pinned to model revision `d3ea19e` and verified by SHA-256 after download.
- C API: `needle_load`, `needle_models`, and `needle_transcribe`. The IME and audio code do not call Cactus directly; native interop is kept in `WhistleNative`, `WhistleSpeechRecognizer`, and `WhistleModelManager`.
- Licensing: the runtime and model are published under Apache-2.0. Third-party attributions and license texts are also available in the app under **Credits & licenses** and in `app/src/main/assets/licenses/`.

Model download is the only app network operation. Captured audio and transcripts are not uploaded or persisted. The Cactus inference runtime itself performs transcription locally.
