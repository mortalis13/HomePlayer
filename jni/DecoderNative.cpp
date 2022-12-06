#include <jni.h>
#include <android/log.h>

#include <string>
#include <vector>

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

std::vector<float> pixel_buffer;
float pixel_sum;
float max_pixel;
int pixel_index;


static int decode_packet(AVCodecContext *dec_ctx, const AVPacket *pkt, std::vector<float> &samples, int pixel_size, std::string* errors) {
    int ret = 0;

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
            // those two return values are special and mean there is no output
            // frame available, but there were no errors during decoding
            if (ret == AVERROR_EOF || ret == AVERROR(EAGAIN)) {
                return 0;
            }
            __android_log_print(ANDROID_LOG_ERROR, CPP_LOG_TAG, "DECODING_PROC_CODE");
            return ret;
        }
        
        // read_samples(frame, dec_ctx->sample_fmt, samples);
        uint8_t* base;
        int offset;
        
        bool is_planar = av_sample_fmt_is_planar(dec_ctx->sample_fmt);
        
        for (int sid = 0; sid < frame->nb_samples; sid++) {
          float sample = 0;
          
          for (int ch = 0; ch < frame->channels; ch++) {
            float channelSample = 0;
            
            base = frame->data[0];
            offset = sid * frame->channels + ch;
            if (is_planar) {
                base = frame->extended_data[ch];
                offset = sid;
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
          
          // pack samples
          pixel_sum += std::abs(sample);
          pixel_index++;
          
          if (pixel_index >= pixel_size) {
              float pixel = pixel_sum / pixel_size;
              if (pixel > max_pixel) max_pixel = pixel;
            
              samples.push_back(pixel);
              pixel_sum = 0;
              pixel_index = 0;
          }
        }
        
        av_frame_unref(frame);
        if (ret < 0) {
            return ret;
        }
    }

    return 0;
}


static int open_codec_context(int *stream_idx, AVCodecContext **dec_ctx, AVFormatContext *fmt_ctx, enum AVMediaType type, std::string* errors) {
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
    
    pixel_sum = 0;
    max_pixel = 0;
    pixel_index = 0;
    pixel_buffer.clear();
    
    // input params
    const char* input_audio_path = env->GetStringUTFChars(jaudio_path, 0);

    jclass resultClass = (env)->FindClass("org/mortalis/homeplayer/decoder/DecoderResult");
    jmethodID constructor = (env)->GetMethodID(resultClass, "<init>", "()V");
    jfieldID samples_field = (env)->GetFieldID(resultClass, "samples", "[S");
    jobject resultObject = (env)->NewObject(resultClass, constructor);

    // prepare result containers
    std::vector<float> samples_data;
    std::string errors_data;

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

    if (open_codec_context(&audio_stream_idx, &audio_dec_ctx, fmt_ctx, AVMEDIA_TYPE_AUDIO, &errors_data) >= 0) {
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
    
    int total_samples = (int) ((fmt_ctx->duration / (float) AV_TIME_BASE) * audio_dec_ctx->sample_rate);
    int pixel_size = (int) ((float) total_samples / view_width);
    
    // __android_log_print(ANDROID_LOG_INFO, CPP_LOG_TAG, "duration: %d", fmt_ctx->duration);
    // __android_log_print(ANDROID_LOG_INFO, CPP_LOG_TAG, "sample_rate: %d", audio_dec_ctx->sample_rate);
    // __android_log_print(ANDROID_LOG_INFO, CPP_LOG_TAG, "total_samples: %d", total_samples);
    
    // read frames from the file
    while (av_read_frame(fmt_ctx, pkt) >= 0) {
        bool is_audio_stream = pkt->stream_index == audio_stream_idx;
        
        if (is_audio_stream) {
            ret = decode_packet(audio_dec_ctx, pkt, samples_data, pixel_size, &errors_data);
        }
        else {
            __android_log_print(ANDROID_LOG_INFO, CPP_LOG_TAG, "not audio stream: %d", pkt->stream_index);
        }
        
        av_packet_unref(pkt);
        if (ret < 0) break;
    }

    // flush the decoders
    if (audio_dec_ctx) {
        decode_packet(audio_dec_ctx, NULL, samples_data, pixel_size, &errors_data);
    }
    
    if (pixel_index > 0) {
        __android_log_print(ANDROID_LOG_INFO, CPP_LOG_TAG, "flushing end data, pixel_index: %d", pixel_index);
        float pixel = pixel_sum / pixel_index;
        if (pixel > max_pixel) max_pixel = pixel;
        samples_data.push_back(pixel);
    }
    
    // release ffmpeg data
    end_cleanup:

    avcodec_free_context(&audio_dec_ctx);
    avformat_close_input(&fmt_ctx);
    av_packet_free(&pkt);
    av_frame_free(&frame);

    // return without ffmpeg release
    end_return:

    env->ReleaseStringUTFChars(jaudio_path, input_audio_path);
    
    __android_log_print(ANDROID_LOG_INFO, CPP_LOG_TAG, "samples_data: %d", samples_data.size());
    
    // ----------
    jshortArray samplesBytes = env->NewShortArray(samples_data.size());
    jshort* pArray = env->GetShortArrayElements(samplesBytes, NULL);
    for (size_t i = 0; i < samples_data.size(); i++) {
      short value = (short) (samples_data[i] * view_height / 2 / max_pixel);
      pArray[i] = value;
    }
    env->ReleaseShortArrayElements(samplesBytes, pArray, 0);
    env->SetObjectField(resultObject, samples_field, samplesBytes);

    return resultObject;
}
