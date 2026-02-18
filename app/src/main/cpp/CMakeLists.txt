cmake_minimum_required(VERSION 3.22.1)
project(native_app)

set(CMAKE_CXX_STANDARD 17)

# Configure whisper.cpp build
set(BUILD_SHARED_LIBS ON CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)

# Disable unsupported backends
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_ACCELERATE OFF CACHE BOOL "" FORCE)
set(GGML_BLAS OFF CACHE BOOL "" FORCE)
set(GGML_METAL OFF CACHE BOOL "" FORCE)
set(GGML_CUDA OFF CACHE BOOL "" FORCE)
set(GGML_VULKAN OFF CACHE BOOL "" FORCE)

# Add whisper.cpp
add_subdirectory(whisper.cpp)

# Our JNI Library
add_library(native-lib SHARED native-lib.cpp)

target_include_directories(native-lib PRIVATE
        whisper.cpp/include
)

# Enable ARM NEON optimizations for proper SIMD support
if(ANDROID_ABI STREQUAL "arm64-v8a")
    # Exynos 1280 optimization flags (ARMv8.2-A with dotprod)
    target_compile_options(native-lib PRIVATE
        -march=armv8.2-a+fp+simd+crypto+dotprod+fp16
        -mtune=cortex-a78
        -O3
        -ffast-math
        -fno-finite-math-only
        -funroll-loops
        -fomit-frame-pointer
    )
    target_compile_options(whisper PRIVATE
        -march=armv8.2-a+fp+simd+crypto+dotprod+fp16
        -mtune=cortex-a78
        -O3
        -ffast-math
        -fno-finite-math-only
        -funroll-loops
    )
elseif(ANDROID_ABI STREQUAL "armeabi-v7a")
    target_compile_options(native-lib PRIVATE -mfpu=neon -mfloat-abi=softfp)
    target_compile_options(whisper PRIVATE -mfpu=neon -mfloat-abi=softfp)
endif()

# Add optimization flags for release
if(NOT CMAKE_BUILD_TYPE STREQUAL "Debug")
    target_compile_options(native-lib PRIVATE -O3 -ffast-math -fno-finite-math-only -funroll-loops)
endif()

target_link_libraries(
        native-lib
        whisper
        log
        android
)
