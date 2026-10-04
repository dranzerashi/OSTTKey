#ifndef NEEDLE_H
#define NEEDLE_H

#ifndef NEEDLE_API
#define NEEDLE_API __attribute__((visibility("default")))
#endif

#define NEEDLE_TEXT 1
#define NEEDLE_SPEECH 2

#ifdef __cplusplus
extern "C" {
#endif

NEEDLE_API int needle_load(const unsigned char* cact, unsigned long long n);
NEEDLE_API int needle_models(void);
NEEDLE_API const char* needle_last_error(void);

NEEDLE_API int needle_transcribe(
    const float* pcm,
    int samples,
    const char* language,
    const char* keywords,
    int word_timestamps,
    char* out,
    int out_capacity);

#ifdef __cplusplus
}
#endif
#endif
