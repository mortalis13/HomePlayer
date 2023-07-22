#ifndef ANDROID_LOGGING_H
#define ANDROID_LOGGING_H

#include <android/log.h>

#if 1

#ifndef LOG_MODULE_NAME
#define LOG_MODULE_NAME "jnicode"
#endif

#define LOGV(...) __android_log_print(ANDROID_LOG_VERBOSE, LOG_MODULE_NAME, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG,   LOG_MODULE_NAME, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,    LOG_MODULE_NAME, __VA_ARGS__)
#define LOG(...)  __android_log_print(ANDROID_LOG_INFO,    LOG_MODULE_NAME, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,    LOG_MODULE_NAME, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR,   LOG_MODULE_NAME, __VA_ARGS__)
#define LOGF(...) __android_log_print(ANDROID_LOG_FATAL,   LOG_MODULE_NAME, __VA_ARGS__)

#endif

#endif // ANDROID_LOGGING_H