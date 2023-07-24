#define LOG_MODULE_NAME "_EngineNative"

#include <jni.h>

#include <string>

#include "utils/logging.h"
#include "FilePlayer.h"

#ifdef __cplusplus
extern "C" {
#endif

using namespace std;


static FilePlayer player;


// Engine
JNIEXPORT jint JNICALL Java_org_mortalis_homeplayernative_jni_EngineNative_startEngine(JNIEnv *env, jclass obj) {
  LOGD(__func__);
  bool result = player.init();
  
  if (!result) {
    LOGE("Could not start audio engine. Check the previous logs.");
  }
  return result ? 0: -1;
}

JNIEXPORT void JNICALL Java_org_mortalis_homeplayernative_jni_EngineNative_stopEngine(JNIEnv *env, jclass obj) {
  LOGD(__func__);
  player.destroy();
}

JNIEXPORT jboolean JNICALL Java_org_mortalis_homeplayernative_jni_EngineNative_isStreamClosed(JNIEnv *env, jclass obj) {
  LOGD(__func__);
  return player.isStreamClosed();
}

JNIEXPORT jboolean JNICALL Java_org_mortalis_homeplayernative_jni_EngineNative_isStreamRestarting(JNIEnv *env, jclass obj) {
  LOGD(__func__);
  return player.isRestarting();
}


// Stream
JNIEXPORT jint JNICALL Java_org_mortalis_homeplayernative_jni_EngineNative_loadAudio(JNIEnv *env, jclass obj, jstring jaudioPath) {
  LOGD(__func__);
  const char* audioPathBytes = env->GetStringUTFChars(jaudioPath, 0);
  string audioPath(audioPathBytes);
  env->ReleaseStringUTFChars(jaudioPath, audioPathBytes);
  
  bool result = player.loadAudio(audioPath);
  
  if (!result) {
    LOGE("Could not properly load audio file. Check the previous logs.");
  }
  return result ? 0: -1;
}

JNIEXPORT jint JNICALL Java_org_mortalis_homeplayernative_jni_EngineNative_playAudio(JNIEnv *env, jclass obj) {
  LOGD(__func__);
  bool result = player.startAudio();
  return result ? 0: -1;
}

JNIEXPORT jint JNICALL Java_org_mortalis_homeplayernative_jni_EngineNative_pauseAudio(JNIEnv *env, jclass obj) {
  LOGD(__func__);
  player.pause();
  return 0;
}

JNIEXPORT jint JNICALL Java_org_mortalis_homeplayernative_jni_EngineNative_resumeAudio(JNIEnv *env, jclass obj) {
  LOGD(__func__);
  bool result = player.resume();
  return result ? 0: -1;
}


// Decoder
JNIEXPORT bool JNICALL Java_org_mortalis_homeplayernative_jni_EngineNative_isPlaying(JNIEnv *env, jclass obj) {
  return player.isPlaying();
}

JNIEXPORT bool JNICALL Java_org_mortalis_homeplayernative_jni_EngineNative_isStopped(JNIEnv *env, jclass obj) {
  return player.isStopped();
}

JNIEXPORT void JNICALL Java_org_mortalis_homeplayernative_jni_EngineNative_setRepeat(JNIEnv *env, jclass obj, jboolean repeat) {
  LOGD(__func__);
  player.setRepeat(repeat);
}

JNIEXPORT jint JNICALL Java_org_mortalis_homeplayernative_jni_EngineNative_getDuration(JNIEnv *env, jclass obj) {
  LOGD(__func__);
  return player.getDuration();
}

JNIEXPORT jint JNICALL Java_org_mortalis_homeplayernative_jni_EngineNative_getCurrentPosition(JNIEnv *env, jclass obj) {
  return player.getCurrentPosition();
}

JNIEXPORT void JNICALL Java_org_mortalis_homeplayernative_jni_EngineNative_seekTo(JNIEnv *env, jclass obj, jint time) {
  LOGD(__func__);
  player.seekTo(time);
}


// Audio params
JNIEXPORT jint JNICALL Java_org_mortalis_homeplayernative_jni_EngineNative_getChannels(JNIEnv *env, jclass obj) {
  LOGD(__func__);
  return player.getChannels();
}

JNIEXPORT jint JNICALL Java_org_mortalis_homeplayernative_jni_EngineNative_getSampleRate(JNIEnv *env, jclass obj) {
  LOGD(__func__);
  return player.getSampleRate();
}

JNIEXPORT jstring JNICALL Java_org_mortalis_homeplayernative_jni_EngineNative_getSampleFormat(JNIEnv *env, jclass obj) {
  LOGD(__func__);
  return env->NewStringUTF(player.getSampleFormat().c_str());
}

JNIEXPORT jint JNICALL Java_org_mortalis_homeplayernative_jni_EngineNative_getBitrate(JNIEnv *env, jclass obj) {
  LOGD(__func__);
  return player.getBitrate();
}

JNIEXPORT jstring JNICALL Java_org_mortalis_homeplayernative_jni_EngineNative_getCodecName(JNIEnv *env, jclass obj) {
  LOGD(__func__);
  return env->NewStringUTF(player.getCodecName().c_str());
}

#ifdef __cplusplus
}
#endif
