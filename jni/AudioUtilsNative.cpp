#define LOG_MODULE_NAME "_AudioUtilsNative"

#include <jni.h>

#include <string>
#include <vector>
#include <cmath>

#include "utils/logging.h"
#include "AudioDecoder.h"

#ifdef __cplusplus
extern "C" {
#endif

using namespace std;


static AudioDecoder audioDecoder;


JNIEXPORT jobject JNICALL Java_org_mortalis_homeplayer_jni_AudioUtilsNative_buildWaveform(JNIEnv *env, jclass obj, jstring jaudio_path, jint view_width, jint view_height) {
  LOGD(__func__);
  
  int result;
  
  const char* input_audio_path = env->GetStringUTFChars(jaudio_path, 0);
  string audioPath(input_audio_path);
  env->ReleaseStringUTFChars(jaudio_path, input_audio_path);
  
  result = audioDecoder.loadCodec(audioPath);
  if (result != 0) return NULL;
  
  float* pixel_data = new float[view_width];
  result = audioDecoder.compressSamples(pixel_data, view_width);
  audioDecoder.cleanup();
  
  if (result != 0) {
    delete[] pixel_data;
    return NULL;
  }
  
  jclass resultClass = (env)->FindClass("org/mortalis/homeplayer/jni/DecoderResult");
  jmethodID constructor = (env)->GetMethodID(resultClass, "<init>", "()V");
  jfieldID samples_field = (env)->GetFieldID(resultClass, "samples", "[S");
  jobject resultObject = (env)->NewObject(resultClass, constructor);
  
  jshortArray samplesBytes = env->NewShortArray(view_width);
  jshort* pArray = env->GetShortArrayElements(samplesBytes, NULL);
  for (size_t i = 0; i < view_width; i++) {
    short value = (short) (pixel_data[i] * view_height / 2);
    pArray[i] = value;
  }
  delete[] pixel_data;
  
  env->ReleaseShortArrayElements(samplesBytes, pArray, 0);
  env->SetObjectField(resultObject, samples_field, samplesBytes);

  return resultObject;
}


JNIEXPORT void JNICALL Java_org_mortalis_homeplayer_jni_AudioUtilsNative_cancelWaveform(JNIEnv *env, jclass obj) {
  audioDecoder.stopCompression();
}

#ifdef __cplusplus
}
#endif
