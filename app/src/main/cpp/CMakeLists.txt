cmake_minimum_required(VERSION 3.22.1)
project(native_app)

set(CMAKE_CXX_STANDARD 17)

# Whisper Optionen für Android anpassen
set(BUILD_SHARED_LIBS ON CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)

# whisper.cpp hinzufügen
add_subdirectory(whisper.cpp)
# eigene JNI Library
add_library(native-lib SHARED native-lib.cpp)

target_include_directories(native-lib PRIVATE
        whisper.cpp/include
)


target_link_libraries(
        native-lib
        whisper
        log
)
