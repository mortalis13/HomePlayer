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


JNIEXPORT jint JNICALL Java_org_mortalis_homeplayer_jni_AudioUtilsNative_buildWaveform(JNIEnv *env, jclass cls, jstring jaudio_path, jint view_width, jint view_height) {
  LOGD(__func__);
  
  int result;
  
  const char* path_bytes = env->GetStringUTFChars(jaudio_path, 0);
  string audioPath(path_bytes);
  env->ReleaseStringUTFChars(jaudio_path, path_bytes);
  
  result = audioDecoder.loadCodec(audioPath);
  if (result != 0) return result;
  
  float* pixel_data = new float[view_width];
  result = audioDecoder.compressSamples(pixel_data, view_width);
  audioDecoder.cleanup();
  
  if (result != 0) {
    delete[] pixel_data;
    return result;
  }
  
  jfieldID data_field = env->GetStaticFieldID(cls, "waveformData", "[S");
  jshortArray data_array = static_cast<jshortArray>(env->GetStaticObjectField(cls, data_field));

  jshort* waveformData = env->GetShortArrayElements(data_array, NULL);
  for (size_t i = 0; i < view_width; i++) {
    short value = (short) (pixel_data[i] * view_height / 2);
    waveformData[i] = value;
  }
  env->ReleaseShortArrayElements(data_array, waveformData, 0);
  
  delete[] pixel_data;
  return 0;
}


JNIEXPORT void JNICALL Java_org_mortalis_homeplayer_jni_AudioUtilsNative_cancelWaveform(JNIEnv *env, jclass cls) {
  audioDecoder.stopCompression();
}

#ifdef __cplusplus
}
#endif
