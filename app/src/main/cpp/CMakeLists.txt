cmake_minimum_required(VERSION 3.22.1)

project(fenekllm LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# llama.cpp is fetched by Gradle (see app/build.gradle.kts) into ./llama.cpp at a pinned tag.
# Only the `llama` and `ggml` libraries are built: no tools, examples, server or `common`.
# Backends are separate shared libraries chosen at run time (GGML_BACKEND_DL with all CPU variants),
# so one APK uses the fastest instruction set of the phone it runs on and still starts on older ones.
if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(GGML_OPENMP ON CACHE BOOL "" FORCE)
else()
    set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
endif()

set(LLAMA_BUILD_COMMON   OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER   OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_APP      OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL        OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS    ON  CACHE BOOL "" FORCE)

set(GGML_NATIVE           OFF CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL       ON  CACHE BOOL "" FORCE)
set(GGML_CPU_ALL_VARIANTS ON  CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE        OFF CACHE BOOL "" FORCE)

add_subdirectory(${CMAKE_CURRENT_LIST_DIR}/llama.cpp build-llama)

add_library(fenekllm SHARED llm_jni.cpp)

target_include_directories(fenekllm PRIVATE
        ${CMAKE_CURRENT_LIST_DIR}/llama.cpp/include
        ${CMAKE_CURRENT_LIST_DIR}/llama.cpp/ggml/include)

target_link_libraries(fenekllm PRIVATE llama ggml android log)

# 16 KB page size devices (Android 15+) need aligned segments.
target_link_options(fenekllm PRIVATE -Wl,-z,max-page-size=16384)
