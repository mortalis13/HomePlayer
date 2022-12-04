LOCAL_PATH := $(call my-dir)

# set ANDROID_NDK env var: path to NDK home
$(info __MAKEFILE: ANDROID_NDK: $(ANDROID_NDK))
$(info __MAKEFILE: NDK_TOOLCHAIN_VERSION: $(NDK_TOOLCHAIN_VERSION))

# -- From mobile-ffmpeg v4.4 LTS (audio and min)

# include $(CLEAR_VARS)
# LOCAL_MODULE := libavutil
# LOCAL_MODULE_FILENAME := libavutil
# LOCAL_SRC_FILES := ffmpeg/$(TARGET_ARCH_ABI)/lib/libavutil-audio.so
# LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/ffmpeg/$(TARGET_ARCH_ABI)/include
# include $(PREBUILT_SHARED_LIBRARY)

# include $(CLEAR_VARS)
# LOCAL_MODULE := libavformat
# LOCAL_MODULE_FILENAME := libavformat
# LOCAL_SRC_FILES := ffmpeg/$(TARGET_ARCH_ABI)/lib/libavformat-audio.so
# LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/ffmpeg/$(TARGET_ARCH_ABI)/include
# include $(PREBUILT_SHARED_LIBRARY)

# include $(CLEAR_VARS)
# LOCAL_MODULE := libavcodec
# LOCAL_MODULE_FILENAME := libavcodec
# LOCAL_SRC_FILES := ffmpeg/$(TARGET_ARCH_ABI)/lib/libavcodec-audio.so
# LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/ffmpeg/$(TARGET_ARCH_ABI)/include
# include $(PREBUILT_SHARED_LIBRARY)

# include $(CLEAR_VARS)
# LOCAL_MODULE := libswresample
# LOCAL_MODULE_FILENAME := libswresample
# LOCAL_SRC_FILES := ffmpeg/$(TARGET_ARCH_ABI)/lib/libswresample-audio.so
# LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/ffmpeg/$(TARGET_ARCH_ABI)/include
# include $(PREBUILT_SHARED_LIBRARY)


include $(CLEAR_VARS)
LOCAL_MODULE := libavutil
LOCAL_MODULE_FILENAME := libavutil
LOCAL_SRC_FILES := ffmpeg/$(TARGET_ARCH_ABI)/lib/libavutil-min.so
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/ffmpeg/$(TARGET_ARCH_ABI)/include
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := libavformat
LOCAL_MODULE_FILENAME := libavformat
LOCAL_SRC_FILES := ffmpeg/$(TARGET_ARCH_ABI)/lib/libavformat-min.so
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/ffmpeg/$(TARGET_ARCH_ABI)/include
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := libavcodec
LOCAL_MODULE_FILENAME := libavcodec
LOCAL_SRC_FILES := ffmpeg/$(TARGET_ARCH_ABI)/lib/libavcodec-min.so
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/ffmpeg/$(TARGET_ARCH_ABI)/include
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := libswresample
LOCAL_MODULE_FILENAME := libswresample
LOCAL_SRC_FILES := ffmpeg/$(TARGET_ARCH_ABI)/lib/libswresample-min.so
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/ffmpeg/$(TARGET_ARCH_ABI)/include
include $(PREBUILT_SHARED_LIBRARY)


# include $(CLEAR_VARS)
# LOCAL_MODULE := libavutil
# LOCAL_SRC_FILES := ffmpeg/$(TARGET_ARCH_ABI)/lib/libavutil-amplituda.so
# LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/ffmpeg/$(TARGET_ARCH_ABI)/include
# include $(PREBUILT_SHARED_LIBRARY)

# include $(CLEAR_VARS)
# LOCAL_MODULE := libavformat
# LOCAL_SRC_FILES := ffmpeg/$(TARGET_ARCH_ABI)/lib/libavformat-amplituda.so
# LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/ffmpeg/$(TARGET_ARCH_ABI)/include
# include $(PREBUILT_SHARED_LIBRARY)

# include $(CLEAR_VARS)
# LOCAL_MODULE := libavcodec
# LOCAL_SRC_FILES := ffmpeg/$(TARGET_ARCH_ABI)/lib/libavcodec-amplituda.so
# LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/ffmpeg/$(TARGET_ARCH_ABI)/include
# include $(PREBUILT_SHARED_LIBRARY)

# include $(CLEAR_VARS)
# LOCAL_MODULE := libswresample
# LOCAL_SRC_FILES := ffmpeg/$(TARGET_ARCH_ABI)/lib/libswresample-amplituda.so
# LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/ffmpeg/$(TARGET_ARCH_ABI)/include
# include $(PREBUILT_SHARED_LIBRARY)


include $(CLEAR_VARS)
LOCAL_MODULE := adecoder
LOCAL_SRC_FILES := DecoderNative.cpp
LOCAL_SHARED_LIBRARIES := libavutil libswresample libavformat libavcodec
LOCAL_LDLIBS := -llog
include $(BUILD_SHARED_LIBRARY)
