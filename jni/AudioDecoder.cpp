#define LOG_MODULE_NAME "AudioDecoder_"

#include "AudioDecoder.h"

#include "utils/logging.h"


void AudioDecoder::start() {
  LOGD("AudioDecoder::start()");
  this->enabled = true;
  this->playing = true;
  
  runThread = std::async(&AudioDecoder::run, this);
  // runThread = thread(&AudioDecoder::run, this);
  LOGI("Decoder thread stated");
}


void AudioDecoder::stop() {
  LOGD("AudioDecoder::stop()");
  
  this->playing = false;
  this->enabled = false;
  
  if (runThread.valid()) {
    LOGI("--wating for thread");
    runThread.wait();
    LOGI("--after wait");
  }
  
  this->cleanup();
  LOGI("Decoder stopped");
}


void AudioDecoder::run() {
  // Decoder thread
  LOGD("AudioDecoder::run()");
  this->decodeFrames();
  LOGI("Decoder thread ended");
}


int64_t AudioDecoder::decodeFrames() {
  int result = -1;
  int bytesPerSample;
  int bytesWritten = 0;
  
  AVPacket* audioPacket;
  AVFrame* audioFrame;
  
  audioPacket = av_packet_alloc();
  if (!audioPacket) {
    LOGE("Could not allocate audio packet");
    goto end;
  }
  
  audioFrame = av_frame_alloc();
  if (!audioFrame) {
    LOGE("Could not allocate audio frame");
    goto end;
  }

  bytesPerSample = av_get_bytes_per_sample((AVSampleFormat) audioStream->codecpar->format);
  LOGD("Bytes per sample %d", bytesPerSample);
  
  // Seek to start
  result = av_seek_frame(formatContext, audioStream->index, 0, AVSEEK_FLAG_BYTE);
  if (result < 0) {
    LOGE("Error when seeking to the start of the stream");
  }
  
  LOGD("DECODE START");
  
  while (this->enabled) {
    if (this->playing) {
      result = av_read_frame(formatContext, audioPacket);
      if (result != 0) break;
      
      if (audioPacket->stream_index == audioStream->index && audioPacket->size > 0) {
        result = avcodec_send_packet(codecContext, audioPacket);
        if (result != 0) {
          LOGE("avcodec_send_packet error: %s", av_err2str(result));
          goto end;
        }

        result = avcodec_receive_frame(codecContext, audioFrame);
        if (result == AVERROR(EAGAIN)) {
          // The codec needs more data before it can decode
          LOGI("avcodec_receive_frame returned EAGAIN");
          av_packet_unref(audioPacket);
          continue;
        }
        else if (result != 0) {
          LOGE("avcodec_receive_frame error: %s", av_err2str(result));
          goto end;
        }

        // Resample
        int64_t swr_delay = swr_get_delay(swrContext, audioFrame->sample_rate);
        int32_t dst_nb_samples = (int32_t) av_rescale_rnd(swr_delay + audioFrame->nb_samples, sampleRate, audioFrame->sample_rate, AV_ROUND_UP);
        
        short* buffer;
        av_samples_alloc((uint8_t **) &buffer, nullptr, this->channelCount, dst_nb_samples, AV_SAMPLE_FMT_FLT, 0);
        int frame_count = swr_convert(swrContext, (uint8_t **) &buffer, dst_nb_samples, (const uint8_t **) audioFrame->data, audioFrame->nb_samples);

        int64_t bytesToWrite = frame_count * sizeof(float) * this->channelCount;
        saveFrame(buffer, bytesWritten, bytesToWrite);
        bytesWritten += bytesToWrite;

        av_freep(&buffer);
        av_frame_unref(audioFrame);
      }
      
      av_packet_unref(audioPacket);
    }
  }
  
  result = bytesWritten;
  
  end:
  this->enabled = false;
  this->playing = false;
  
  av_frame_free(&audioFrame);
  av_packet_free(&audioPacket);
  
  return result;
}


void AudioDecoder::saveFrame(short* buffer, int64_t bytesWritten, int64_t bytesToWrite) {
  if (this->decodeMode == MODE_STATIC_BUFFER) {
    memcpy(targetData + bytesWritten, buffer, (size_t) bytesToWrite);
  }
  else if (this->decodeMode == MODE_BUFFER_QUEUE) {
    int pushedBytes = 0;
    
    while (pushedBytes < bytesToWrite) {
      if (!this->enabled) break;
      
      float sample;
      memcpy(&sample, (uint8_t*) buffer + pushedBytes, sizeof(float));
      
      bool pushed = this->dataQ->push(sample);
      if (!pushed) continue;
      
      pushedBytes += sizeof(float);
    }
  }
}


int AudioDecoder::loadFile(string filePath) {
  LOGI("Decoder: FFMpeg => %s", filePath.c_str());
  int result = -1;
  
  result = avformat_open_input(&formatContext, filePath.c_str(), NULL, NULL);
  if (result < 0) {
    LOGE("Failed to open file. Error code: %s", av_err2str(result));
    this->cleanup();
    return result;
  }
  
  result = avformat_find_stream_info(formatContext, NULL);
  if (result < 0) {
    LOGE("Failed to find stream info. Error code: %s", av_err2str(result));
    this->cleanup();
    return result;
  }
  
  int streamIndex = av_find_best_stream(formatContext, AVMEDIA_TYPE_AUDIO, -1, -1, nullptr, 0);
  audioStream = nullptr;
  if (streamIndex >= 0) {
    audioStream = formatContext->streams[streamIndex];
  }
  
  if (audioStream == nullptr || audioStream->codecpar == nullptr) {
    LOGE("Could not find a suitable audio stream to decode");
    this->cleanup();
    return -1;
  }

  printCodecParameters(audioStream->codecpar);
  this->dataChannels = audioStream->codecpar->ch_layout.nb_channels;

  audioCodec = avcodec_find_decoder(audioStream->codecpar->codec_id);
  if (!audioCodec){
    LOGE("Could not find codec with ID: %d", audioStream->codecpar->codec_id);
    this->cleanup();
    return -1;
  }
  
  codecContext = avcodec_alloc_context3(audioCodec);
  if (!codecContext){
    LOGE("Failed to allocate codec context");
    this->cleanup();
    return -1;
  }

  result = avcodec_parameters_to_context(codecContext, audioStream->codecpar);
  if (result < 0) {
    LOGE("Failed to copy codec parameters to codec context");
    this->cleanup();
    return result;
  }

  result = avcodec_open2(codecContext, audioCodec, nullptr);
  if (result < 0) {
    LOGE("Could not open codec");
    this->cleanup();
    return result;
  }

  swrContext = swr_alloc();
  if (!swrContext) {
    LOGE("Could not allocate resampler context");
    this->cleanup();
    return -1;
  }
  
  AVChannelLayout outChannelLayout;
  av_channel_layout_default(&outChannelLayout, this->channelCount);
  int32_t outSampleRate = this->sampleRate;
  AVSampleFormat outSampleFormat = AV_SAMPLE_FMT_FLT;
  
  printResamplerParameters(audioStream, outChannelLayout, outSampleRate, outSampleFormat);
  
  av_opt_set_chlayout(swrContext, "in_chlayout", &audioStream->codecpar->ch_layout, 0);
  av_opt_set_int(swrContext, "in_sample_rate", audioStream->codecpar->sample_rate, 0);
  av_opt_set_sample_fmt(swrContext, "in_sample_fmt", (AVSampleFormat) audioStream->codecpar->format, 0);
  
  av_opt_set_chlayout(swrContext, "out_chlayout", &outChannelLayout, 0);
  av_opt_set_int(swrContext, "out_sample_rate", outSampleRate, 0);
  av_opt_set_sample_fmt(swrContext, "out_sample_fmt", outSampleFormat, 0);

  av_opt_set_int(swrContext, "force_resampling", 1, 0);

  result = swr_init(swrContext);
  if (result < 0){
    LOGE("Failed to initialize the resampling context. Error: %s", av_err2str(result));
    this->cleanup();
    return result;
  }

  return 0;
}


void AudioDecoder::cleanup() {
  LOGD("AudioDecoder::cleanup()");
  avformat_close_input(&formatContext);
  avcodec_free_context(&codecContext);
  swr_free(&swrContext);
}


int64_t AudioDecoder::decodeStatic(string filePath) {
  int result = loadFile(filePath);
  if (result == -1) return -1;
  
  int64_t bytesWritten = decodeFrames();
  cleanup();
  return bytesWritten;
}


void AudioDecoder::printResamplerParameters(AVStream* audioStream, AVChannelLayout outChannelLayout, int32_t outSampleRate, AVSampleFormat outSampleFormat) {
  LOGD("===Resampler params===");
  LOGD("Channels: %d => %d", audioStream->codecpar->ch_layout.nb_channels, outChannelLayout.nb_channels);
  LOGD("Sample rate: %d => %d", audioStream->codecpar->sample_rate, outSampleRate);
  LOGD("Sample format: %s => %s", av_get_sample_fmt_name((AVSampleFormat) audioStream->codecpar->format), av_get_sample_fmt_name(outSampleFormat));
  LOGD("===END Resampler params===");
}

void AudioDecoder::printCodecParameters(AVCodecParameters* codecParams) {
  LOGD("===Codec params===");
  LOGD("Channels: %d", codecParams->ch_layout.nb_channels);
  LOGD("Channel layout: order %d, mask %d", codecParams->ch_layout.order, (int) codecParams->ch_layout.u.mask);
  LOGD("Sample rate: %d", codecParams->sample_rate);
  LOGD("Format: %s", av_get_sample_fmt_name((AVSampleFormat) codecParams->format));
  LOGD("Frame size: %d", codecParams->frame_size);
  LOGD("===END Codec params===");
}
