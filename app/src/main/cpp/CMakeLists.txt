cmake_minimum_required(VERSION 3.22.1)

project("card_tracker_native")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Optimization and ARM NEON flags
if(${ANDROID_ABI} STREQUAL "armeabi-v7a")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -mfpu=neon -mfloat-abi=softfp -O3")
elseif(${ANDROID_ABI} STREQUAL "arm64-v8a")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -fvectorize")
else()
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3")
endif()

include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}
)

add_library(
    card_tracker_native
    SHARED
    NativeOpticalFlowTracker.cpp
)

find_library(
    log-lib
    log
)

target_link_libraries(
    card_tracker_native
    ${log-lib}
)
