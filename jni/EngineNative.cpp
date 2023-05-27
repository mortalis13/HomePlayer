#define LOG_MODULE_NAME "EngineNative_"

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
  LOGI(__func__);
  bool result = player.init();
  
  if (!result) {
    LOGE("Could not start audio engine. Check the previous logs.");
  }
  return result ? 0: -1;
}


JNIEXPORT void JNICALL Java_org_mortalis_homeplayer_jni_EngineNative_stopEngine(JNIEnv *env, jclass obj) {
  LOGI(__func__);
  
}


JNIEXPORT jint JNICALL Java_org_mortalis_homeplayer_jni_EngineNative_playAudio(JNIEnv *env, jclass obj, jstring jaudioPath) {
  LOGI(__func__);
  const char* audioPathBytes = env->GetStringUTFChars(jaudioPath, 0);
  string audioPath(audioPathBytes);
  env->ReleaseStringUTFChars(jaudioPath, audioPathBytes);
  
  bool result = player.play(audioPath);
  
  if (!result) {
    LOGE("Could not properly load audio file. Check the previous logs.");
  }
  return result ? 0: -1;
}


JNIEXPORT jint JNICALL Java_org_mortalis_homeplayer_jni_EngineNative_pauseAudio(JNIEnv *env, jclass obj) {
  LOGI(__func__);
  return 0;
}


JNIEXPORT jint JNICALL Java_org_mortalis_homeplayer_jni_EngineNative_resumeAudio(JNIEnv *env, jclass obj) {
  LOGI(__func__);
  return 0;
}

#ifdef __cplusplus
}
#endif
