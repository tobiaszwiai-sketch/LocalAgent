cmake_minimum_required(VERSION 3.22.1)
project("llamaagent")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ---------------------------------------------------------------------------
# Pobierz llama.cpp przy pierwszym buildzie (FetchContent).
# Tag b3456 jest sprawdzony pod kątem API użytego w llama_jni.cpp.
# ---------------------------------------------------------------------------
include(FetchContent)

# Wyłącz zbędne komponenty llama.cpp PRZED FetchContent_MakeAvailable
set(LLAMA_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER   OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL           OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP          OFF CACHE BOOL "" FORCE)
# Wektoryzacja ARM (NEON) dla arm64-v8a
set(GGML_NATIVE          OFF CACHE BOOL "" FORCE)

FetchContent_Declare(
    llama_cpp
    GIT_REPOSITORY https://github.com/ggerganov/llama.cpp.git
    GIT_TAG        b3456
    GIT_SHALLOW    TRUE
)
FetchContent_MakeAvailable(llama_cpp)

# ---------------------------------------------------------------------------
# Nasza biblioteka natywna
# ---------------------------------------------------------------------------
add_library(llamaagent SHARED llama_jni.cpp)

target_include_directories(llamaagent PRIVATE
    ${llama_cpp_SOURCE_DIR}
    ${llama_cpp_SOURCE_DIR}/include
    ${llama_cpp_SOURCE_DIR}/ggml/include
)

find_library(log-lib log)

target_link_libraries(llamaagent
    llama
    ${log-lib}
    android
)
