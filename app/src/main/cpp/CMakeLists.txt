cmake_minimum_required(VERSION 3.22.1)
project("llamaagent")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ---------------------------------------------------------------------------
# Prebuilt llama.cpp biblioteki (b10665) - arm64-v8a
# Zamiast kompilować od zera, używamy gotowych .so z GitHub Releases.
# Aktualizacja: zmień PREBUILT_DIR i nagłówki gdy zmieniasz wersję.
# ---------------------------------------------------------------------------

set(PREBUILT_DIR "${CMAKE_SOURCE_DIR}/../jniLibs/arm64-v8a")
set(HEADERS_DIR  "${CMAKE_SOURCE_DIR}/include")

# Zadeklaruj importowane biblioteki
add_library(llama SHARED IMPORTED)
set_target_properties(llama PROPERTIES
    IMPORTED_LOCATION "${PREBUILT_DIR}/libllama.so")

add_library(ggml SHARED IMPORTED)
set_target_properties(ggml PROPERTIES
    IMPORTED_LOCATION "${PREBUILT_DIR}/libggml.so")

add_library(ggml_base SHARED IMPORTED)
set_target_properties(ggml_base PROPERTIES
    IMPORTED_LOCATION "${PREBUILT_DIR}/libggml-base.so")

# ---------------------------------------------------------------------------
# Nasza biblioteka JNI
# ---------------------------------------------------------------------------
add_library(llamaagent SHARED llama_jni.cpp)

target_include_directories(llamaagent PRIVATE
    ${HEADERS_DIR}
    ${HEADERS_DIR}/ggml
)

find_library(log-lib log)

target_link_libraries(llamaagent
    llama
    ggml
    ggml_base
    ${log-lib}
    android
)
