cmake_minimum_required(VERSION 3.22.1)
project(whistle_jni LANGUAGES CXX)

# Cactus publishes its Android ARM64 runtime as a static library and C header.
# Pin the exact published runtime revision and verify its content before linking.
set(NEEDLE_REVISION "f84005f")
set(NEEDLE_BASE_URL "https://huggingface.co/Cactus-Compute/needle3/resolve/${NEEDLE_REVISION}/android-arm64")
set(NEEDLE_SDK_DIR "${CMAKE_CURRENT_BINARY_DIR}/cactus-needle")
set(NEEDLE_LIBRARY "${NEEDLE_SDK_DIR}/libneedle.a")
file(MAKE_DIRECTORY "${NEEDLE_SDK_DIR}")

if(NOT EXISTS "${NEEDLE_LIBRARY}")
    file(DOWNLOAD
        "${NEEDLE_BASE_URL}/libneedle.a"
        "${NEEDLE_LIBRARY}"
        EXPECTED_HASH SHA256=16752fb75adea7af89bbc435a97d1cda7e71bc74d04578d551d0d3bbd5f9e633
        TLS_VERIFY ON
        SHOW_PROGRESS
        STATUS NEEDLE_DOWNLOAD_STATUS)
    list(GET NEEDLE_DOWNLOAD_STATUS 0 NEEDLE_DOWNLOAD_CODE)
    if(NOT NEEDLE_DOWNLOAD_CODE EQUAL 0)
        file(REMOVE "${NEEDLE_LIBRARY}")
        list(GET NEEDLE_DOWNLOAD_STATUS 1 NEEDLE_DOWNLOAD_MESSAGE)
        message(FATAL_ERROR "Could not download the official Cactus Android runtime: ${NEEDLE_DOWNLOAD_MESSAGE}")
    endif()
endif()

add_library(needle_engine STATIC IMPORTED GLOBAL)
set_target_properties(needle_engine PROPERTIES IMPORTED_LOCATION "${NEEDLE_LIBRARY}")

add_library(whistle_jni SHARED whistle_jni.cpp)
target_include_directories(whistle_jni PRIVATE "${CMAKE_CURRENT_SOURCE_DIR}")
target_compile_features(whistle_jni PRIVATE cxx_std_17)
find_library(ANDROID_LOG_LIB log)
target_link_libraries(whistle_jni PRIVATE needle_engine ${ANDROID_LOG_LIB} android m dl)
