#define LOG_MODULE_NAME "_EngineNative"

#include <jni.h>

#include <string>

#include "utils/logging.h"
#include "defs.h"
#include "FilePlayer.h"

#ifdef __cplusplus
extern "C" {
#endif

using namespace std;


static JavaVM* gvm;
static jclass EngineClassRef;
static jmethodID NotifyAudioStopMethodID;

static FilePlayer player;


bool GetJniEnv(JavaVM *vm, JNIEnv **env) {
  LOGD("GetJniEnv()");
  if (!vm) {
    LOGE("VM instance is NULL");
    return false;
  }
  
  bool thread_attached = false;
  *env = nullptr;

  // Check if the current thread is attached to the VM
  auto get_env_result = vm->GetEnv((void**)env, JNI_VERSION_1_6);
  if (get_env_result == JNI_EDETACHED) {
    if (vm->AttachCurrentThread(env, NULL) == JNI_OK) {
      thread_attached = true;
    }
    else {
      LOGE("Failed to attach thread");
    }
  }
  else if (get_env_result == JNI_EVERSION) {
    LOGE("Unsupported JNI version");
  }
  
  return thread_attached;
}


JNIEXPORT jint JNI_OnLoad(JavaVM* pVM, void* reserved) {
  LOGD("JNI_OnLoad()");
  gvm = pVM;
  return JNI_VERSION_1_6;
}


class ChangeListener : public EngineChangeListener {
  virtual void audioEnded() {
    // --> Decoder wait thread
    LOGD("audioEnded()");
    
    JNIEnv *env;
    bool thread_attached = GetJniEnv(gvm, &env);
    LOGI("JNI env attached to the current thread: %d", thread_attached);
    
    if (thread_attached) {
      if (EngineClassRef && NotifyAudioStopMethodID) {
        env->CallStaticVoidMethod(EngineClassRef, NotifyAudioStopMethodID);
      }
      gvm->DetachCurrentThread();
    }
  }
};


// Engine
JNIEXPORT void JNICALL Java_org_mortalis_homeplayer_jni_EngineNative_initEngine(JNIEnv *env, jclass obj) {
  LOGD(__func__);
  player.engineChangeListener = new ChangeListener();
  
  if (!EngineClassRef) {
    EngineClassRef = (jclass) env->NewGlobalRef(obj);
    NotifyAudioStopMethodID = env->GetStaticMethodID(EngineClassRef, "notifyAudioStopped", "()V");
  }
}

JNIEXPORT jboolean JNICALL Java_org_mortalis_homeplayer_jni_EngineNative_startEngine(JNIEnv *env, jclass obj) {
  LOGD(__func__);
  bool result = player.init();
  
  if (!result) {
    LOGE("Could not start audio engine. Check the previous logs.");
  }
  return result;
}

JNIEXPORT void JNICALL Java_org_mortalis_homeplayer_jni_EngineNative_stopEngine(JNIEnv *env, jclass obj) {
  LOGD(__func__);
  player.destroy();
}


// Stream
JNIEXPORT jboolean JNICALL Java_org_mortalis_homeplayer_jni_EngineNative_isStreamClosed(JNIEnv *env, jclass obj) {
  LOGD(__func__);
  return player.isStreamClosed();
}

JNIEXPORT jboolean JNICALL Java_org_mortalis_homeplayer_jni_EngineNative_isStreamRestarting(JNIEnv *env, jclass obj) {
  LOGD(__func__);
  return player.isRestarting();
}

JNIEXPORT bool JNICALL Java_org_mortalis_homeplayer_jni_EngineNative_isPlaying(JNIEnv *env, jclass obj) {
  return player.isPlaying();
}

JNIEXPORT void JNICALL Java_org_mortalis_homeplayer_jni_EngineNative_setGain(JNIEnv *env, jclass obj, jfloat gain) {
  player.setGain(gain);
}


// Decoder
JNIEXPORT jboolean JNICALL Java_org_mortalis_homeplayer_jni_EngineNative_loadAudio(JNIEnv *env, jclass obj, jstring jaudioPath) {
  LOGD(__func__);
  const char* audioPathBytes = env->GetStringUTFChars(jaudioPath, 0);
  string audioPath(audioPathBytes);
  env->ReleaseStringUTFChars(jaudioPath, audioPathBytes);
  
  bool result = player.loadAudio(audioPath);
  
  if (!result) {
    LOGE("Could not properly load audio file. Check the previous logs.");
  }
  return result;
}

JNIEXPORT jboolean JNICALL Java_org_mortalis_homeplayer_jni_EngineNative_bufferNextAudio(JNIEnv *env, jclass obj, jstring jaudioPath) {
  LOGD(__func__);
  const char* audioPathBytes = env->GetStringUTFChars(jaudioPath, 0);
  string audioPath(audioPathBytes);
  env->ReleaseStringUTFChars(jaudioPath, audioPathBytes);
  
  bool result = player.bufferNextAudio(audioPath);
  
  if (!result) {
    LOGE("Could not properly load audio file. Check the previous logs.");
  }
  return result;
}

JNIEXPORT jboolean JNICALL Java_org_mortalis_homeplayer_jni_EngineNative_playAudio(JNIEnv *env, jclass obj) {
  LOGD(__func__);
  bool result = player.startAudio();
  return result;
}

JNIEXPORT jboolean JNICALL Java_org_mortalis_homeplayer_jni_EngineNative_pauseAudio(JNIEnv *env, jclass obj) {
  LOGD(__func__);
  bool result = player.pause();
  return result;
}

JNIEXPORT jboolean JNICALL Java_org_mortalis_homeplayer_jni_EngineNative_resumeAudio(JNIEnv *env, jclass obj) {
  LOGD(__func__);
  bool result = player.resume();
  return result;
}

JNIEXPORT jint JNICALL Java_org_mortalis_homeplayer_jni_EngineNative_getDuration(JNIEnv *env, jclass obj) {
  LOGD(__func__);
  return player.getDuration();
}

JNIEXPORT jint JNICALL Java_org_mortalis_homeplayer_jni_EngineNative_getCurrentPosition(JNIEnv *env, jclass obj) {
  return player.getCurrentPosition();
}

JNIEXPORT jstring JNICALL Java_org_mortalis_homeplayer_jni_EngineNative_getAudioPath(JNIEnv *env, jclass obj) {
  string audioPath = player.getAudioPath();
  return env->NewStringUTF(audioPath.c_str());
}

JNIEXPORT void JNICALL Java_org_mortalis_homeplayer_jni_EngineNative_seekTo(JNIEnv *env, jclass obj, jint time) {
  LOGD(__func__);
  player.seekTo(time);
}

JNIEXPORT void JNICALL Java_org_mortalis_homeplayer_jni_EngineNative_setRepeat(JNIEnv *env, jclass obj, jboolean repeat) {
  player.setRepeat(repeat);
}

JNIEXPORT void JNICALL Java_org_mortalis_homeplayer_jni_EngineNative_setLoop(JNIEnv *env, jclass obj, jboolean loop) {
  player.setLoop(loop);
}

JNIEXPORT void JNICALL Java_org_mortalis_homeplayer_jni_EngineNative_setLoopStart(JNIEnv *env, jclass obj, jint time) {
  player.setLoopStart(time);
}

JNIEXPORT void JNICALL Java_org_mortalis_homeplayer_jni_EngineNative_setLoopEnd(JNIEnv *env, jclass obj, jint time) {
  player.setLoopEnd(time);
}


// Filter
JNIEXPORT void JNICALL Java_org_mortalis_homeplayer_jni_EngineNative_enableFilter(JNIEnv *env, jclass obj) {
  LOGD(__func__);
  player.enableFilter();
}

JNIEXPORT void JNICALL Java_org_mortalis_homeplayer_jni_EngineNative_disableFilter(JNIEnv *env, jclass obj) {
  LOGD(__func__);
  player.disableFilter();
}

JNIEXPORT void JNICALL Java_org_mortalis_homeplayer_jni_EngineNative_setFilterFrequency(JNIEnv *env, jclass obj, jint band, jfloat frequency) {
  player.setFilterFrequency(band, frequency);
}

JNIEXPORT void JNICALL Java_org_mortalis_homeplayer_jni_EngineNative_setFilterGain(JNIEnv *env, jclass obj, jint band, jfloat gain) {
  player.setFilterGain(band, gain);
}

JNIEXPORT void JNICALL Java_org_mortalis_homeplayer_jni_EngineNative_setFilterQ(JNIEnv *env, jclass obj, jfloat q) {
  LOGD(__func__);
  player.setFilterQ(q);
}


// Audio params
JNIEXPORT jint JNICALL Java_org_mortalis_homeplayer_jni_EngineNative_getChannels(JNIEnv *env, jclass obj) {
  LOGD(__func__);
  return player.getChannels();
}

JNIEXPORT jint JNICALL Java_org_mortalis_homeplayer_jni_EngineNative_getSampleRate(JNIEnv *env, jclass obj) {
  LOGD(__func__);
  return player.getSampleRate();
}

JNIEXPORT jstring JNICALL Java_org_mortalis_homeplayer_jni_EngineNative_getSampleFormat(JNIEnv *env, jclass obj) {
  LOGD(__func__);
  return env->NewStringUTF(player.getSampleFormat().c_str());
}

JNIEXPORT jint JNICALL Java_org_mortalis_homeplayer_jni_EngineNative_getBitrate(JNIEnv *env, jclass obj) {
  LOGD(__func__);
  return player.getBitrate();
}

JNIEXPORT jstring JNICALL Java_org_mortalis_homeplayer_jni_EngineNative_getCodecName(JNIEnv *env, jclass obj) {
  LOGD(__func__);
  return env->NewStringUTF(player.getCodecName().c_str());
}

#ifdef __cplusplus
}
#endif
