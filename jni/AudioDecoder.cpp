#include "AudioDecoder.h"
#define LOG_MODULE_NAME "_AudioDecoder"

#include <thread>
#include <chrono>
#include <algorithm>

#include "utils/logging.h"


AudioDecoder::AudioDecoder(AudioStreamWriter* streamWriter) {
  this->streamWriter = streamWriter;
  threadEndSignal = new promise<void>();
}

AudioDecoder::~AudioDecoder() {
  LOGD("~AudioDecoder()");
  if (!isStopped()) {
    stop();
  }
  this->cleanup();
  delete threadEndSignal;
}


void AudioDecoder::start() {
  LOGD("start()");
  this->playing = true;
  this->stopped = false;
  this->ended = false;
  
  runThread = std::async(launch::async, &AudioDecoder::run, this);
  LOGI("Decoder thread started");
}


void AudioDecoder::stop() {
  LOGD("stop()");
  this->playing = false;
  if (stopped) {
    LOGI("--already stopped");
    return;
  }
  this->stopped = true;
  
  if (runThread.valid()) {
    LOGI("--wating for thread");
    runThread.wait();
    LOGI("--after wait");
  }
  
  LOGI("Decoder stopped");
}

void AudioDecoder::pause() {
  LOGD("pause()");
  this->playing = false;
}

void AudioDecoder::resume() {
  LOGD("resume()");
  this->playing = true;
}

void AudioDecoder::setRepeat(bool repeat) {
  LOGD("repeat => %d", repeat);
  this->repeat = repeat;
}

bool AudioDecoder::isRepeat() {
  return repeat;
}


bool AudioDecoder::waitDecoderThread() {
  // --> Decoder wait thread
  // Returns true if decoding thread was ended after eof, not forced
  LOGD("--> waitDecoderThread() -start-");
  // Block until set_value is called
  threadEndSignal->get_future().wait();
  LOGD("--> waitDecoderThread() -end-, EOF %sreached", this->ended ? "": "not ");
  return this->ended;
}

void AudioDecoder::run() {
  // --> Decoder thread
  LOGD("run()");
  int result = this->decodeFrames();

  this->playing = false;
  this->stopped = true;
  
  if (result == 0) {
    this->ended = true;
    LOGI("File decoding completed after EOF");
  }
  
  LOGI("Decoder thread ended");
  // Notify about decoding thread end
  threadEndSignal->set_value();
}

int AudioDecoder::decodeFrames() {
  // --> Decoder thread
  int result = -1;
  bool is_eof = false;
  
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

  LOGI("Decode start");
  
  while (!this->stopped) {
    if (this->seekPending) {
      this->seekPending = false;
      is_eof = false;
      
      int64_t seek_ts = this->seekTimestamp - 0.05 * AV_TIME_BASE;
      if (seek_ts < 0) seek_ts = 0;
      int seek_result = avformat_seek_file(formatContext, -1, 0, seek_ts, INT64_MAX, 0);
      
      if (seek_result >= 0) {
        avcodec_flush_buffers(codecContext);
      }
      else {
        LOGE("Error when seeking to %ld", this->seekTimestamp);
      }
    }
    
    if (!this->playing) {
      this_thread::sleep_for(chrono::milliseconds(10));
      continue;
    }
    
    result = av_read_frame(formatContext, audioPacket);
    if (result < 0) {
      LOGW("av_read_frame error: %s", av_err2str(result));
      if (result == AVERROR_EOF || avio_feof(formatContext->pb)) {
        is_eof = true;
        avcodec_send_packet(codecContext, audioPacket);
        av_packet_unref(audioPacket);
        
        if (this->repeat) {
          this->seekTo(0);
          continue;
        }
        break;
      }
    }
    
    if (audioPacket->stream_index != this->audioStreamIndex) {
      av_packet_unref(audioPacket);
      continue;
    }

    result = avcodec_send_packet(codecContext, audioPacket);
    if (result != 0) {
      LOGE("avcodec_send_packet error: %s", av_err2str(result));
      goto end;
    }
    av_packet_unref(audioPacket);

    while (result >= 0) {
      if (this->seekPending || this->stopped) break;
      result = avcodec_receive_frame(codecContext, audioFrame);
      
      if (result == AVERROR_EOF) {
        LOGW("avcodec_receive_frame EOF");
        avcodec_flush_buffers(codecContext);
        continue;
      }
      else if (result == AVERROR(EAGAIN)) continue;
      else if (result < 0) {
        LOGE("avcodec_receive_frame error: %s", av_err2str(result));
        continue;
      }
      
      AVRational audio_time_base = (AVRational){1, this->outSampleRate * this->outChannelCount};
      audioFrame->pts = av_rescale_q(audioFrame->pts, codecContext->pkt_timebase, audio_time_base);
      this->currentPTS = audioFrame->pts - this->delayedSamples * this->outChannelCount;
      
      // Resample
      int64_t swr_delay = swr_get_delay(swrContext, audioFrame->sample_rate);
      int32_t dst_nb_samples = (int32_t) av_rescale_rnd(swr_delay + audioFrame->nb_samples, this->outSampleRate, audioFrame->sample_rate, AV_ROUND_UP);
      
      uint8_t* audio_buffer;
      av_samples_alloc((uint8_t**) &audio_buffer, nullptr, this->outChannelCount, dst_nb_samples, AV_SAMPLE_FMT_FLT, 0);
      int frame_count = swr_convert(swrContext, (uint8_t**) &audio_buffer, dst_nb_samples, (const uint8_t**) audioFrame->data, audioFrame->nb_samples);
      
      // Loop
      int skip_frames = 0;
      
      if (this->loop && this->loopSeekPending) {
        // Block used after seek to loop start is done, need to search for the exact frame/sample position to write audio from
        LOGI("After seek, frame pts is: %d", audioFrame->pts);
        
        double seek_s = (double) this->seekTimestamp / AV_TIME_BASE;
        int64_t seekPTS = seek_s * this->outChannelCount * this->outSampleRate;
        int64_t nextPTS = this->currentPTS + dst_nb_samples * this->outChannelCount;
        
        if (nextPTS < seekPTS) {
          LOGI("skipping av frame %d, it's before the target time, nextPTS %d < seekPTS %d (%.3f s)", currentPTS, nextPTS, seekPTS, seek_s);
          av_freep(&audio_buffer);
          av_frame_unref(audioFrame);
          continue;
        }
        else {
          skip_frames = (seekPTS - currentPTS) / this->outChannelCount;
          LOGI("av frame %d has the target time, skipping %d audio frames to the pts %d (%.3f s)", currentPTS, skip_frames, seekPTS, seek_s);
        }
      }

      this->loopSeekPending = false;
      
      if (this->loop) {
        // Block used to detect the loop end is reached, write extact samples that belong to the loop and rewind to the loop start
        int64_t loopEndPTS = (double) loopEnd / 1000.0 * this->outChannelCount * this->outSampleRate;
        int64_t nextPTS = currentPTS + dst_nb_samples * this->outChannelCount;
        int64_t currentPTS_cached = currentPTS;
        
        if (nextPTS >= loopEndPTS) {
          seekTo(loopStart);
          this->loopSeekPending = true;
          
          if (currentPTS_cached > loopEndPTS) {
            LOGI("Current frame is after the loop end (%d > %d), skipping the entire frame and rewinding to the loop start", currentPTS_cached, loopEndPTS);
            av_freep(&audio_buffer);
            av_frame_unref(audioFrame);
            break;
          }

          int diff = (nextPTS - loopEndPTS) / this->outChannelCount;
          frame_count -= diff;
          LOGI("last av frame in loop %d, frame_count is cutted: %d => %d", currentPTS, frame_count + diff, frame_count);
        }
      }
      
      // Write
      processAVFrame(audio_buffer, frame_count, skip_frames);

      av_freep(&audio_buffer);
      av_frame_unref(audioFrame);
    }
  } // while not stopped
  
  result = 0;  // stream ended
  if (this->stopped) result = 1;  // decoder forced to stop
  
end:
  av_frame_free(&audioFrame);
  av_packet_free(&audioPacket);
  
  return result;
}

void AudioDecoder::processAVFrame(uint8_t* buffer, int32_t numFrames, int32_t skipFrames) {
  // --> Decoder thread
  if (this->streamWriter) {
    // Pass the samples outside
    streamWriter->writeAudio(buffer, numFrames, skipFrames);
  }
}


int AudioDecoder::loadFile(string filePath) {
  LOGD("loadFile() -start- => %s", filePath.c_str());
  this->audioPath = filePath;
  int result = -1;
  
  this->loaded = false;
  this->currentPTS = 0;
  this->delayedSamples = 0;
  this->seekPending = false;
  this->loopSeekPending = false;
  
  result = avformat_open_input(&formatContext, filePath.c_str(), NULL, NULL);
  if (result < 0) {
    LOGE("Failed to open file. Error code: %s", av_err2str(result));
    this->cleanup();
    return result;
  }
  LOGD("AV: input opened");
  
  result = avformat_find_stream_info(formatContext, NULL);
  if (result < 0) {
    LOGE("Failed to find stream info. Error code: %s", av_err2str(result));
    this->cleanup();
    return result;
  }
  LOGD("AV: stream info found");
  
  AVStream* audioStream = nullptr;
  this->audioStreamIndex = av_find_best_stream(formatContext, AVMEDIA_TYPE_AUDIO, -1, -1, nullptr, 0);
  if (audioStreamIndex >= 0) {
    audioStream = formatContext->streams[audioStreamIndex];
  }
  
  if (audioStream == nullptr || audioStream->codecpar == nullptr) {
    LOGE("Could not find a suitable audio stream to decode");
    this->cleanup();
    return -1;
  }
  LOGD("AV: audio stream found");

  fillAudioParams(audioStream->codecpar);
  printCodecParameters(audioStream->codecpar);
  
  this->dataChannels = audioStream->codecpar->ch_layout.nb_channels;
  
  const AVCodec* audioCodec = avcodec_find_decoder(audioStream->codecpar->codec_id);
  if (!audioCodec){
    LOGE("Could not find codec with ID: %d (%s)", audioStream->codecpar->codec_id, avcodec_get_name(audioStream->codecpar->codec_id));
    this->cleanup();
    return -1;
  }
  LOGD("AV: codec found");
  
  codecContext = avcodec_alloc_context3(audioCodec);
  if (!codecContext){
    LOGE("Failed to allocate codec context");
    this->cleanup();
    return -1;
  }
  LOGD("AV: codec context allocated");

  codecContext->pkt_timebase = audioStream->time_base;
  
  result = avcodec_parameters_to_context(codecContext, audioStream->codecpar);
  if (result < 0) {
    LOGE("Failed to copy codec parameters to codec context");
    this->cleanup();
    return result;
  }
  LOGD("AV: codec parameters copied");

  result = avcodec_open2(codecContext, audioCodec, nullptr);
  if (result < 0) {
    LOGE("Could not open codec");
    this->cleanup();
    return result;
  }
  LOGD("AV: codec opened");
  
  swrContext = swr_alloc();
  if (!swrContext) {
    LOGE("Could not allocate resampler context");
    this->cleanup();
    return -1;
  }
  LOGD("AV: resampler context allocated");
  
  AVChannelLayout outChannelLayout;
  av_channel_layout_default(&outChannelLayout, this->outChannelCount);
  
  printResamplerParameters(audioStream->codecpar, outChannelLayout, outSampleRate, OUTPUT_SAMPLE_FORMAT);
  
  av_opt_set_chlayout(swrContext, "in_chlayout", &audioStream->codecpar->ch_layout, 0);
  av_opt_set_int(swrContext, "in_sample_rate", audioStream->codecpar->sample_rate, 0);
  av_opt_set_sample_fmt(swrContext, "in_sample_fmt", (AVSampleFormat) audioStream->codecpar->format, 0);
  
  av_opt_set_chlayout(swrContext, "out_chlayout", &outChannelLayout, 0);
  av_opt_set_int(swrContext, "out_sample_rate", outSampleRate, 0);
  av_opt_set_sample_fmt(swrContext, "out_sample_fmt", OUTPUT_SAMPLE_FORMAT, 0);

  av_opt_set_int(swrContext, "force_resampling", 1, 0);

  result = swr_init(swrContext);
  if (result < 0) {
    LOGE("Failed to initialize the resampling context. Error: %s", av_err2str(result));
    this->cleanup();
    return result;
  }
  LOGD("AV: resampler initialized");
  
  findDelayedSamples();
  
  this->loaded = true;
  LOGD("loadFile() -end- => %s", filePath.c_str());
  
  return 0;
}


int AudioDecoder::findDelayedSamples() {
  AVPacket* audioPacket;
  AVFrame* audioFrame;
  
  if (!(audioPacket = av_packet_alloc())) return -1;
  if (!(audioFrame = av_frame_alloc())) {av_packet_free(&audioPacket); return -1;}
  
  do {
    if (av_read_frame(formatContext, audioPacket) < 0) {av_packet_free(&audioPacket); av_frame_free(&audioFrame); return -1;}
  }
  while (audioPacket->stream_index != this->audioStreamIndex);

  if (avcodec_send_packet(codecContext, audioPacket) != 0) {av_packet_free(&audioPacket); av_frame_free(&audioFrame); return -1;}
  av_packet_unref(audioPacket);

  if (avcodec_receive_frame(codecContext, audioFrame) < 0) {av_packet_free(&audioPacket); av_frame_free(&audioFrame); return -1;}
  
  if (audioFrame->nb_samples < codecContext->frame_size) {
    // Get compensation samples number from the first frame, in mp3 for example it's ~1105 samples
    this->delayedSamples = codecContext->frame_size - audioFrame->nb_samples;
    LOGI("Initial delayed compensation samples: %d", this->delayedSamples);
  }
  
  avformat_seek_file(formatContext, -1, 0, 0, INT64_MAX, 0);
  avcodec_flush_buffers(codecContext);
  
  av_frame_unref(audioFrame);
  av_packet_free(&audioPacket);
  av_frame_free(&audioFrame);
  return 0;
}


int AudioDecoder::getCurrentTime() {
  double time_s = (double) this->currentPTS / this->outChannelCount / this->outSampleRate;
  int currentTime_ms = (int) (time_s * 1000);
  if (currentTime_ms < 0) currentTime_ms = 0;
  return currentTime_ms;
}

int AudioDecoder::getDuration() {
  if (!formatContext) {
    LOGE("getDuration() :: formatContext is NULL");
    return -1;
  }
  
  int64_t duration_ms = 1000 * formatContext->duration / AV_TIME_BASE;
  LOGI("Context duration: %d, duration: %d ms", (int) formatContext->duration, (int) duration_ms);
  return duration_ms;
}

void AudioDecoder::seekTo(int time_ms) {
  LOGD("seekTo() => %d ms", time_ms);
  if (time_ms < 0) time_ms = 0;
  this->seekTimestamp = (double) time_ms / 1000.0 * AV_TIME_BASE;
  this->seekPending = true;
  this->currentPTS = (double) time_ms / 1000.0 * this->outChannelCount * this->outSampleRate;
}


void AudioDecoder::setLoop(bool loop) {
  LOGD("setLoop() => %d", loop);
  this->loop = loop;
}

void AudioDecoder::setLoopStart(int time) {
  this->loopStart = time;
}

void AudioDecoder::setLoopEnd(int time) {
  this->loopEnd = time;
}


void AudioDecoder::cleanup() {
  LOGD("cleanup()");
  avformat_close_input(&formatContext);
  avcodec_free_context(&codecContext);
  swr_free(&swrContext);
}


void AudioDecoder::fillAudioParams(AVCodecParameters* codecParams) {
  this->audioParams.channels = codecParams->ch_layout.nb_channels;
  this->audioParams.sample_rate = codecParams->sample_rate;
  this->audioParams.frame_size = codecParams->frame_size;
  this->audioParams.sample_format = string(av_get_sample_fmt_name((AVSampleFormat) codecParams->format));
  this->audioParams.is_planar = (bool) av_sample_fmt_is_planar((AVSampleFormat) codecParams->format);
  this->audioParams.bytes_per_sample = av_get_bytes_per_sample((AVSampleFormat) codecParams->format);
  this->audioParams.bitrate = codecParams->bit_rate;
  this->audioParams.codec_type = string(av_get_media_type_string(codecParams->codec_type));
  this->audioParams.codec_name = string(avcodec_get_name(codecParams->codec_id));
}

void AudioDecoder::printResamplerParameters(AVCodecParameters* codecParams, AVChannelLayout outChannelLayout, int32_t outSampleRate, AVSampleFormat outSampleFormat) {
  LOGD("===Resampler params===");
  LOGD("Channels: %d => %d", codecParams->ch_layout.nb_channels, outChannelLayout.nb_channels);
  LOGD("Sample rate: %d => %d", codecParams->sample_rate, outSampleRate);
  LOGD("Sample format: %s => %s", av_get_sample_fmt_name((AVSampleFormat) codecParams->format), av_get_sample_fmt_name(outSampleFormat));
  LOGD("===END Resampler params===");
  LOGD("");
}

void AudioDecoder::printCodecParameters(AVCodecParameters* codecParams) {
  LOGD("===Codec params===");
  LOGD("Channels: %d", codecParams->ch_layout.nb_channels);
  LOGD("Channel layout: order %d, mask %d", codecParams->ch_layout.order, (int) codecParams->ch_layout.u.mask);
  LOGD("Sample rate: %d", codecParams->sample_rate);
  LOGD("Frame size: %d", codecParams->frame_size);
  LOGD("Sample format: %s", av_get_sample_fmt_name((AVSampleFormat) codecParams->format));
  LOGD("Is planar: %d", av_sample_fmt_is_planar((AVSampleFormat) codecParams->format));
  LOGD("Bytes per sample: %d", av_get_bytes_per_sample((AVSampleFormat) codecParams->format));
  LOGD("Bitrate: %d", (int) (codecParams->bit_rate / 1000));
  LOGD("Codec type: %s", av_get_media_type_string(codecParams->codec_type));
  LOGD("Codec ID: %s", avcodec_get_name(codecParams->codec_id));
  LOGD("===END Codec params===");
  LOGD("");
}
