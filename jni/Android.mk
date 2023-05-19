LOCAL_PATH := $(call my-dir)

# set ANDROID_NDK env var: path to NDK home
$(info __MAKEFILE: ANDROID_NDK: $(ANDROID_NDK))
$(info __MAKEFILE: NDK_TOOLCHAIN_VERSION: $(NDK_TOOLCHAIN_VERSION))

# ffmpeg-5.1

include $(CLEAR_VARS)
LOCAL_MODULE := libavutil
LOCAL_SRC_FILES := ffmpeg/$(TARGET_ARCH_ABI)/lib/libavutil.so
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/ffmpeg/$(TARGET_ARCH_ABI)/include
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := libavformat
LOCAL_SRC_FILES := ffmpeg/$(TARGET_ARCH_ABI)/lib/libavformat.so
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/ffmpeg/$(TARGET_ARCH_ABI)/include
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := libavcodec
LOCAL_SRC_FILES := ffmpeg/$(TARGET_ARCH_ABI)/lib/libavcodec.so
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/ffmpeg/$(TARGET_ARCH_ABI)/include
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := libswresample
LOCAL_SRC_FILES := ffmpeg/$(TARGET_ARCH_ABI)/lib/libswresample.so
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/ffmpeg/$(TARGET_ARCH_ABI)/include
include $(PREBUILT_SHARED_LIBRARY)


include $(CLEAR_VARS)
LOCAL_MODULE := decoder
LOCAL_SRC_FILES := DecoderNative.cpp
LOCAL_SHARED_LIBRARIES := libavutil libswresample libavformat libavcodec
LOCAL_LDLIBS := -llog
include $(BUILD_SHARED_LIBRARY)
