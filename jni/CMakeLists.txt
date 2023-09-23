cmake_minimum_required(VERSION 3.4.1)
project(home-player LANGUAGES CXX)

find_package(oboe REQUIRED CONFIG)

set (APP_SOURCES
  AudioUtilsNative.cpp
  AudioDecoder.cpp
  AudioFilter.cpp
  FilePlayer.cpp
  EngineNative.cpp
)

message("CMAKE_VERSION => \"${CMAKE_VERSION}\"")
message("CMAKE_CURRENT_LIST_DIR => \"${CMAKE_CURRENT_LIST_DIR}\"")

include_directories(.)

add_library(audio SHARED ${APP_SOURCES})


# ffmpeg-6.0
set(FFMPEG_DIR "${CMAKE_CURRENT_LIST_DIR}/ffmpeg/${ANDROID_ABI}")
include_directories(${FFMPEG_DIR}/include)

add_library(avformat SHARED IMPORTED)
set_target_properties(avformat PROPERTIES IMPORTED_LOCATION ${FFMPEG_DIR}/lib/libavformat.so)

add_library(avutil SHARED IMPORTED)
set_target_properties(avutil PROPERTIES IMPORTED_LOCATION ${FFMPEG_DIR}/lib/libavutil.so)

add_library(avcodec SHARED IMPORTED)
set_target_properties(avcodec PROPERTIES IMPORTED_LOCATION ${FFMPEG_DIR}/lib/libavcodec.so)

add_library(swresample SHARED IMPORTED)
set_target_properties(swresample PROPERTIES IMPORTED_LOCATION ${FFMPEG_DIR}/lib/libswresample.so)
# end ffmpeg


target_link_libraries(audio oboe::oboe android log avformat avutil avcodec swresample)

target_compile_options(audio PRIVATE -std=c++17 -frtti -fexceptions -Wall)
