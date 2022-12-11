#include <jni.h>
#include <android/log.h>

#include <string>
#include <vector>
#include <cmath>

extern "C" {
#include "libavutil/timestamp.h"
#include "libavutil/samplefmt.h"
#include "libavformat/avformat.h"
}

#ifndef CPP_LOG_TAG
#define CPP_LOG_TAG "hp_cpp"
#endif

struct Status {
  int status;
  std::string error;
};


static AVFormatContext *fmt_ctx = NULL;
static AVCodecContext *audio_dec_ctx;
static AVStream *audio_stream = NULL;

static int audio_stream_idx = -1;
static AVFrame *frame = NULL;
static AVPacket *pkt = NULL;


int frame_id = 0;
float pixel_sum = 0;
int pixel_index = 0;


static int decode_packet(AVCodecContext *dec_ctx, const AVPacket *pkt, std::vector<float> &buffer, int block_size) {
  int ret = 0;
  int pixel_size = block_size;
  
  bool is_planar = av_sample_fmt_is_planar(dec_ctx->sample_fmt);

  // submit the packet to the decoder
  ret = avcodec_send_packet(dec_ctx, pkt);
  if (ret < 0) {
    __android_log_print(ANDROID_LOG_ERROR, CPP_LOG_TAG, "PACKET_SUBMITTING_PROC_CODE");
    return ret;
  }
  
  // get all the available frames from the decoder
  while (ret >= 0) {
    ret = avcodec_receive_frame(dec_ctx, frame);
    if (ret < 0) {
      if (ret == AVERROR_EOF || ret == AVERROR(EAGAIN)) return 0;
      __android_log_print(ANDROID_LOG_ERROR, CPP_LOG_TAG, "DECODING_PROC_CODE");
      return ret;
    }
    
    // if (frame_id++ % SKIP_FRAME_STEP != 0) continue;
    // frame_id++;
    
    uint8_t* base;
    int offset;
    
    if (block_size == 0) pixel_size = frame->nb_samples;
    
    for (int sid = 0; sid < frame->nb_samples; sid++) {
      float sample = 0;
      
      for (int ch = 0; ch < frame->channels; ch++) {
        float channelSample = 0;
        
        if (is_planar) {
          base = frame->extended_data[ch];
          offset = sid;
        }
        else {
          base = frame->data[0];
          offset = sid * frame->channels + ch;
        }
        
        switch (dec_ctx->sample_fmt) {
          case AV_SAMPLE_FMT_U8:
          case AV_SAMPLE_FMT_U8P:
            channelSample = static_cast<float>(reinterpret_cast<uint8_t*>(base)[offset]);
            break;
          
          case AV_SAMPLE_FMT_S16:
          case AV_SAMPLE_FMT_S16P:
            channelSample = static_cast<float>(reinterpret_cast<int16_t*>(base)[offset]);
            break;
          
          case AV_SAMPLE_FMT_S32:
          case AV_SAMPLE_FMT_S32P:
            channelSample = static_cast<float>(reinterpret_cast<int32_t*>(base)[offset]);
            break;
          
          case AV_SAMPLE_FMT_FLT:
          case AV_SAMPLE_FMT_FLTP:
            channelSample = reinterpret_cast<float*>(base)[offset];
            break;
          
          default:
            break;
        }
        
        sample += channelSample;
      }
      
      sample /= frame->channels;
      pixel_sum += std::abs(sample);
      
      // pack samples
      pixel_index++;
      if (pixel_index >= pixel_size) {
        float pixel = pixel_sum / pixel_size;
        buffer.push_back(pixel);
        
        pixel_sum = 0;
        pixel_index = 0;
      }
    } // for nb_samples
    
    av_frame_unref(frame);
    if (ret < 0) return ret;
  }

  return 0;
}


static int open_codec_context(int *stream_idx, AVCodecContext **dec_ctx, AVFormatContext *fmt_ctx, enum AVMediaType type) {
  int ret, stream_index;
  AVStream *st;
  const AVCodec *dec = NULL;
  AVDictionary *opts = NULL;

  ret = av_find_best_stream(fmt_ctx, type, -1, -1, NULL, 0);
  if (ret < 0) {
    __android_log_print(ANDROID_LOG_ERROR, CPP_LOG_TAG, "STREAM_NOT_FOUND_PROC_CODE");
    return ret;
  }
  else {
    stream_index = ret;
    st = fmt_ctx->streams[stream_index];

    // find decoder for the stream
    dec = avcodec_find_decoder(st->codecpar->codec_id);
    if (!dec) {
      __android_log_print(ANDROID_LOG_ERROR, CPP_LOG_TAG, "CODEC_NOT_FOUND_PROC_CODE");
      return AVERROR(EINVAL);
    }

    // Allocate a codec context for the decoder
    *dec_ctx = avcodec_alloc_context3(dec);
    if (!*dec_ctx) {
      __android_log_print(ANDROID_LOG_ERROR, CPP_LOG_TAG, "CODEC_CONTEXT_ALLOC_CODE");
      return AVERROR(ENOMEM);
    }

    // Copy codec parameters from input stream to output codec context
    if ((ret = avcodec_parameters_to_context(*dec_ctx, st->codecpar)) < 0) {
      __android_log_print(ANDROID_LOG_ERROR, CPP_LOG_TAG, "CODEC_PARAMETERS_COPY_PROC_CODE");
      return ret;
    }

    // Init the decoders
    if ((ret = avcodec_open2(*dec_ctx, dec, &opts)) < 0) {
      __android_log_print(ANDROID_LOG_ERROR, CPP_LOG_TAG, "CODEC_OPEN_PROC_CODE");
      return ret;
    }
    *stream_idx = stream_index;
  }

  return 0;
}


extern "C" JNIEXPORT jobject JNICALL

Java_org_mortalis_homeplayer_decoder_DecoderNative_decodeSamples(JNIEnv* env, jobject, jstring jaudio_path, jint view_width, jint view_height) {
  int ret = 0;
  clock_t start_time = clock();
  __android_log_print(ANDROID_LOG_INFO, CPP_LOG_TAG, "--> Decode Start");
  
  frame_id = 0;
  pixel_sum = 0;
  pixel_index = 0;
  
  // input params
  const char* input_audio_path = env->GetStringUTFChars(jaudio_path, 0);

  jclass resultClass = (env)->FindClass("org/mortalis/homeplayer/decoder/DecoderResult");
  jmethodID constructor = (env)->GetMethodID(resultClass, "<init>", "()V");
  jfieldID samples_field = (env)->GetFieldID(resultClass, "samples", "[S");
  jobject resultObject = (env)->NewObject(resultClass, constructor);

  // prepare result containers
  std::vector<float> pixel_data;
  std::vector<float> pixel_buffer;

  // open input file, and allocate format context
  if (avformat_open_input(&fmt_ctx, input_audio_path, NULL, NULL) < 0) {
    __android_log_print(ANDROID_LOG_ERROR, CPP_LOG_TAG, "FILE_OPEN_IO_CODE");
    // goto end_return;
  }

  // retrieve stream information
  if (avformat_find_stream_info(fmt_ctx, NULL) < 0) {
    __android_log_print(ANDROID_LOG_ERROR, CPP_LOG_TAG, "STREAM_INFO_NOT_FOUND_PROC_CODE");
    // goto end_return;
  }

  if (open_codec_context(&audio_stream_idx, &audio_dec_ctx, fmt_ctx, AVMEDIA_TYPE_AUDIO) >= 0) {
    audio_stream = fmt_ctx->streams[audio_stream_idx];
  }
  
  if (!audio_stream) {
    ret = 1;
    __android_log_print(ANDROID_LOG_ERROR, CPP_LOG_TAG, "STREAM_NOT_FOUND_PROC_CODE");
    // goto end_cleanup;
  }

  frame = av_frame_alloc();
  if (!frame) {
    ret = AVERROR(ENOMEM);
    __android_log_print(ANDROID_LOG_ERROR, CPP_LOG_TAG, "FRAME_ALLOC_CODE");
    // goto end_cleanup;
  }

  pkt = av_packet_alloc();
  if (!pkt) {
    ret = AVERROR(ENOMEM);
    __android_log_print(ANDROID_LOG_ERROR, CPP_LOG_TAG, "PACKET_ALLOC_CODE");
    // goto end_cleanup;
  }
  
  // estimate if it's enough to group each frame or a custom block size should be used
  int total_samples = (int) ((fmt_ctx->duration / (float) AV_TIME_BASE) * audio_dec_ctx->sample_rate);
  int estimated_frames = total_samples / audio_dec_ctx->frame_size;
  
  // __android_log_print(ANDROID_LOG_INFO, CPP_LOG_TAG, "frame_size: %d", audio_dec_ctx->frame_size);
  // __android_log_print(ANDROID_LOG_INFO, CPP_LOG_TAG, "total_samples: %d", total_samples);
  __android_log_print(ANDROID_LOG_INFO, CPP_LOG_TAG, "estimated_frames: %d", estimated_frames);
  
  int block_size = 0;
  if (estimated_frames < view_width) {
    block_size = 10;
    // for short audios
    if (total_samples < view_width * block_size) block_size = 1;
  }
  
  // read frames from the file
  while (av_read_frame(fmt_ctx, pkt) >= 0) {
    bool is_audio_stream = pkt->stream_index == audio_stream_idx;
    
    if (is_audio_stream) {
      ret = decode_packet(audio_dec_ctx, pkt, pixel_buffer, block_size);
    }
    else {
      __android_log_print(ANDROID_LOG_INFO, CPP_LOG_TAG, "not audio stream: %d", pkt->stream_index);
    }
    
    av_packet_unref(pkt);
    if (ret < 0) break;
  }

  // flush the decoders
  if (audio_dec_ctx) {
    decode_packet(audio_dec_ctx, NULL, pixel_buffer, block_size);
  }
  
  // normalize data to fit in view_width
  int total_buf_size = pixel_buffer.size();
  int pixel_size = (int) ((float) total_buf_size / view_width);
  int over_size = total_buf_size % view_width;
  
  // __android_log_print(ANDROID_LOG_INFO, CPP_LOG_TAG, "pixel_size: %d", pixel_size);
  // __android_log_print(ANDROID_LOG_INFO, CPP_LOG_TAG, "total_buf_size: %d", total_buf_size);
  // __android_log_print(ANDROID_LOG_INFO, CPP_LOG_TAG, "over_size: %f", over_size);
  
  pixel_sum = 0;
  float max_pixel = 0;

  int block_counter = 0;
  
  // step for resizing arrays: (old_len - 1) / (new_len - 1)
  // only elements that are 'step' away will be taken
  float step = ((float) total_buf_size - 1) / (total_buf_size - over_size - 1);
  __android_log_print(ANDROID_LOG_INFO, CPP_LOG_TAG, "step: %f", step);
  
  // [alt rescale index calc]
  // float scale_ratio = (float) total_buf_size / (total_buf_size - over_size);
  // float scale_ratio_2 = (float) total_buf_size / (2 * (total_buf_size - over_size));
  
  for (int i = 0; i < total_buf_size - over_size; ++i) {
    // [alt rescale index calc]
    // pixel_sum += pixel_buffer[std::round(i * scale_ratio + scale_ratio_2)];
    
    pixel_sum += pixel_buffer[std::round(i * step)];
    block_counter++;
    
    if (block_counter == pixel_size) {
      float pixel = pixel_sum / pixel_size;
      if (pixel > max_pixel) max_pixel = pixel;
      
      pixel_data.push_back(pixel);
      
      pixel_sum = 0;
      block_counter = 0;
    }
  }
  pixel_buffer.clear();
  
  // release ffmpeg data
  end_cleanup:

  avcodec_free_context(&audio_dec_ctx);
  avformat_close_input(&fmt_ctx);
  av_packet_free(&pkt);
  av_frame_free(&frame);

  // return without ffmpeg release
  end_return:

  env->ReleaseStringUTFChars(jaudio_path, input_audio_path);
  
  int total_pixels = pixel_data.size();
  __android_log_print(ANDROID_LOG_INFO, CPP_LOG_TAG, "pixel_data: %d", total_pixels);
  
  // ----------
  jshortArray samplesBytes = env->NewShortArray(total_pixels);
  jshort* pArray = env->GetShortArrayElements(samplesBytes, NULL);
  for (size_t i = 0; i < total_pixels; i++) {
    short value = (short) (pixel_data[i] * view_height / 2 / max_pixel);
    pArray[i] = value;
  }
  pixel_data.clear();
  env->ReleaseShortArrayElements(samplesBytes, pArray, 0);
  env->SetObjectField(resultObject, samples_field, samplesBytes);

  __android_log_print(ANDROID_LOG_INFO, CPP_LOG_TAG, "--> Decode End: %.2f", double(clock() - start_time) / CLOCKS_PER_SEC);

  return resultObject;
}
