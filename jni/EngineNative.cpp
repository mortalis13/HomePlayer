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


JNIEXPORT jint JNICALL Java_org_mortalis_homeplayer_jni_EngineNative_startEngine(JNIEnv *env, jclass obj) {
  LOGD(__func__);
  bool result = player.init();
  
  if (!result) {
    LOGE("Could not start audio engine. Check the previous logs.");
  }
  return result ? 0: -1;
}


JNIEXPORT void JNICALL Java_org_mortalis_homeplayer_jni_EngineNative_stopEngine(JNIEnv *env, jclass obj) {
  LOGD(__func__);
  player.destroy();
}


JNIEXPORT jint JNICALL Java_org_mortalis_homeplayer_jni_EngineNative_loadAudio(JNIEnv *env, jclass obj, jstring jaudioPath) {
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


JNIEXPORT jint JNICALL Java_org_mortalis_homeplayer_jni_EngineNative_playAudio(JNIEnv *env, jclass obj) {
  LOGD(__func__);
  bool result = player.startAudio();
  return result ? 0: -1;
}


JNIEXPORT jint JNICALL Java_org_mortalis_homeplayer_jni_EngineNative_pauseAudio(JNIEnv *env, jclass obj) {
  LOGD(__func__);
  player.pause();
  return 0;
}


JNIEXPORT jint JNICALL Java_org_mortalis_homeplayer_jni_EngineNative_resumeAudio(JNIEnv *env, jclass obj) {
  LOGD(__func__);
  player.resume();
  return 0;
}


JNIEXPORT bool JNICALL Java_org_mortalis_homeplayer_jni_EngineNative_isPlaying(JNIEnv *env, jclass obj) {
  return player.isPlaying();
}

JNIEXPORT jint JNICALL Java_org_mortalis_homeplayer_jni_EngineNative_getDuration(JNIEnv *env, jclass obj) {
  return player.getDuration();
}

JNIEXPORT jint JNICALL Java_org_mortalis_homeplayer_jni_EngineNative_getCurrentPosition(JNIEnv *env, jclass obj) {
  return player.getCurrentPosition();
}

JNIEXPORT void JNICALL Java_org_mortalis_homeplayer_jni_EngineNative_seekTo(JNIEnv *env, jclass obj, jint time) {
  player.seekTo(time);
}

#ifdef __cplusplus
}
#endif
