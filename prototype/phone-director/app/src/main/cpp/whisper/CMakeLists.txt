include(ExternalProject)
set(MF_WHISPER_SOURCE "${CMAKE_CURRENT_SOURCE_DIR}/vendor/whisper.cpp")
execute_process(COMMAND git rev-parse HEAD WORKING_DIRECTORY "${MF_WHISPER_SOURCE}"
    OUTPUT_VARIABLE MF_WHISPER_REV OUTPUT_STRIP_TRAILING_WHITESPACE RESULT_VARIABLE MF_WHISPER_REV_RESULT)
if(NOT MF_WHISPER_REV_RESULT EQUAL 0 OR NOT MF_WHISPER_REV STREQUAL "a8d002cfd879315632a579e73f0148d06959de36")
    message(FATAL_ERROR "Fetch official whisper.cpp v1.7.6 at a8d002cfd879315632a579e73f0148d06959de36")
endif()
set(MF_WHISPER_BINARY "${CMAKE_CURRENT_BINARY_DIR}/isolated")
set(MF_WHISPER_LIB "${MF_WHISPER_BINARY}/lib")
ExternalProject_Add(mf_whisper_external
    SOURCE_DIR "${MF_WHISPER_SOURCE}"
    BINARY_DIR "${MF_WHISPER_BINARY}"
    DOWNLOAD_COMMAND "" UPDATE_COMMAND "" INSTALL_COMMAND ""
    CMAKE_ARGS
        -DCMAKE_TOOLCHAIN_FILE=${CMAKE_TOOLCHAIN_FILE}
        -DANDROID_ABI=${ANDROID_ABI}
        -DANDROID_PLATFORM=${ANDROID_PLATFORM}
        -DCMAKE_BUILD_TYPE=Release
        -DCMAKE_POSITION_INDEPENDENT_CODE=ON
        -DCMAKE_ARCHIVE_OUTPUT_DIRECTORY=${MF_WHISPER_LIB}
        -DCMAKE_C_VISIBILITY_PRESET=hidden
        -DCMAKE_CXX_VISIBILITY_PRESET=hidden
        -DBUILD_SHARED_LIBS=OFF
        -DWHISPER_BUILD_TESTS=OFF -DWHISPER_BUILD_EXAMPLES=OFF -DWHISPER_BUILD_SERVER=OFF
        -DWHISPER_CURL=OFF -DWHISPER_COREML=OFF -DWHISPER_OPENVINO=OFF
        -DGGML_NATIVE=OFF -DGGML_OPENMP=OFF -DGGML_CPU_KLEIDIAI=OFF
        -DGGML_VULKAN=OFF -DGGML_CUDA=OFF -DGGML_METAL=OFF -DGGML_BLAS=OFF
        -DGGML_BACKEND_DL=OFF
    BUILD_COMMAND ${CMAKE_COMMAND} --build <BINARY_DIR> --target whisper --parallel 4
    BUILD_BYPRODUCTS "${MF_WHISPER_LIB}/libwhisper.a" "${MF_WHISPER_LIB}/libggml.a"
        "${MF_WHISPER_LIB}/libggml-base.a" "${MF_WHISPER_LIB}/libggml-cpu.a")
foreach(MF_COMPONENT whisper ggml ggml-base ggml-cpu)
    string(REPLACE "-" "_" MF_TARGET "mf_asr_${MF_COMPONENT}")
    add_library(${MF_TARGET} STATIC IMPORTED)
    set_target_properties(${MF_TARGET} PROPERTIES IMPORTED_LOCATION "${MF_WHISPER_LIB}/lib${MF_COMPONENT}.a")
    add_dependencies(${MF_TARGET} mf_whisper_external)
endforeach()
add_library(minifilm_whisper SHARED transcriber_jni.cpp)
target_include_directories(minifilm_whisper PRIVATE "${MF_WHISPER_SOURCE}/include" "${MF_WHISPER_SOURCE}/ggml/include")
target_link_libraries(minifilm_whisper PRIVATE mf_asr_whisper mf_asr_ggml mf_asr_ggml_cpu mf_asr_ggml_base android log m dl)
target_link_options(minifilm_whisper PRIVATE "-Wl,--exclude-libs,ALL")
add_dependencies(minifilm_whisper mf_whisper_external)
