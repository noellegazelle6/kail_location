cmake_minimum_required(VERSION 3.10)
project(kail_gait_sim C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

find_library(log-lib log)

find_package(dobby CONFIG REQUIRED)

# ============================================================
# Native hook library for system_server (sensor simulation)
# ============================================================
add_library(kail_native_hook SHARED
    native_hook/sensor_simulator.cpp
    native_hook/hook.cpp
)

target_include_directories(kail_native_hook PUBLIC ${CMAKE_CURRENT_SOURCE_DIR})
target_link_libraries(kail_native_hook PRIVATE ${log-lib} dobby::dobby)

set_target_properties(kail_native_hook PROPERTIES
    OUTPUT_NAME "kail_native_hook"
    LIBRARY_OUTPUT_DIRECTORY "${CMAKE_LIBRARY_OUTPUT_DIRECTORY}"
)

# ============================================================
# Ptrace injector (kail_inject)
#
# arm64-v8a uses root/inject64.cpp; armeabi-v7a uses root/inject.cpp;
# x86_64 uses root/injectx64.cpp.
# Each ABI sees one CMake configure pass with ANDROID_ABI fixed, so we
# select the source file accordingly.
# ============================================================
if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(KAIL_INJECT_SRC root/inject64.cpp)
elseif(ANDROID_ABI STREQUAL "x86_64")
    set(KAIL_INJECT_SRC root/injectx64.cpp)
else()
    set(KAIL_INJECT_SRC root/inject.cpp)
endif()

add_executable(kail_inject ${KAIL_INJECT_SRC})

target_include_directories(kail_inject PUBLIC ${CMAKE_CURRENT_SOURCE_DIR})
target_link_libraries(kail_inject PRIVATE ${log-lib} dl)
target_compile_options(kail_inject PRIVATE -fPIE)
target_link_options(kail_inject PRIVATE -pie)

set_target_properties(kail_inject PROPERTIES
    OUTPUT_NAME "kail_inject"
    RUNTIME_OUTPUT_DIRECTORY "${CMAKE_RUNTIME_OUTPUT_DIRECTORY}"
)

# ============================================================
# FakeLocation loader entry libraries (rebranded for kail).
#
# Each loader is a small shared object exporting `doRun(JavaVM**, char*)`,
# meant to be ptrace-loaded by kail_inject into a target process.  All three
# are ABI-portable; CMake builds them once per ABI configured by gradle.
# ============================================================
foreach(loader fakeloc_init fakeloc_initzygote fakeloc_apphook)
    add_library(${loader} SHARED root/lib${loader}.cpp)
    target_include_directories(${loader} PUBLIC ${CMAKE_CURRENT_SOURCE_DIR})
    target_link_libraries(${loader} PRIVATE ${log-lib})
    set_target_properties(${loader} PROPERTIES
        OUTPUT_NAME "${loader}"
        POSITION_INDEPENDENT_CODE ON
    )
endforeach()

# ============================================================
# FakeLocation hook engines (rebranded for kail).
#
# These libraries are loaded inside the injected target via System.load().
# They expose JNI surfaces under com.kail.location.lib.lhooker and
# com.kail.location.inject.utils.
# ============================================================
foreach(engine lhooker StepSensor antidetect)
    add_library(kail_${engine} SHARED root/lib${engine}.cpp)
    target_include_directories(kail_${engine} PUBLIC ${CMAKE_CURRENT_SOURCE_DIR})
    target_link_libraries(kail_${engine} PRIVATE ${log-lib})
    set_target_properties(kail_${engine} PROPERTIES
        OUTPUT_NAME "${engine}"
        POSITION_INDEPENDENT_CODE ON
    )
endforeach()
