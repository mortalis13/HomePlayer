#ifndef AUDIO_DECODER_H
#define AUDIO_DECODER_H

extern "C" {
#include <libavcodec/avcodec.h>
#include <libavformat/avformat.h>
#include <libswresample/swresample.h>
#include <libavutil/opt.h>
}

#include <string>
#include <future>
#include <fstream>

#include "defs.h"

using namespace std;


class AudioDecoder {

static const AVSampleFormat OUTPUT_SAMPLE_FORMAT = AV_SAMPLE_FMT_FLT;

public:
  AudioDecoder(AudioStreamWriter* streamWriter) {
    this->streamWriter = streamWriter;
  }
  
  ~AudioDecoder() {
    this->cleanup();
  }
  
  int loadFile(string filePath);
  
  void start();
  void stop();
  void pause();
  void resume();
  
  void setChannelCount(int32_t outChannelCount) {
    this->outChannelCount = outChannelCount;
  }
  
  void setSampleRate(int32_t outSampleRate) {
    this->outSampleRate = outSampleRate;
  }
  
  int32_t getDataChannels() {
    return dataChannels;
  }
  
  bool isLoaded() {
    return loaded;
  }
  
  bool isStopped() {
    return stopped;
  }
  
  bool isPlaying() {
    return playing;
  }
  
  bool isEnded() {
    return ended;
  }
  
  void setRepeat(bool repeat);
  bool isRepeat();
  
  void seekTo(int time_ms);
  int getCurrentTime();
  int getDuration();


public:
  AudioParams audioParams;


private:
  static void printCodecParameters(AVCodecParameters* codecParams);
  static void printResamplerParameters(AVCodecParameters* codecParams, AVChannelLayout outChannelLayout, int32_t outSampleRate, AVSampleFormat outSampleFormat);
  
  void run();
  void cleanup();
  int decodeFrames();
  void writeFrame(uint8_t* buffer, int32_t numFrames);
  
  int findDelayedSamples();
  void fillAudioParams(AVCodecParameters* codecParams);

private:
  bool loaded = false;
  bool stopped = true;
  bool playing = false;
  bool ended = false;
  bool is_eof = false;
  
  bool repeat = false;
  
  int32_t outChannelCount = 0;
  int32_t outSampleRate = 0;
  int32_t dataChannels = 0;
  
  int64_t currentPTS = 0;  // in samples
  int delayedSamples = 0;
  
  bool seekPending = false;
  int64_t seekTimestamp = 0;
  
  int audioStreamIndex = -1;
  
  AVFormatContext* formatContext = NULL;
  AVCodecContext* codecContext = NULL;
  SwrContext* swrContext = NULL;
  
  future<void> runThread;
  
  AudioStreamWriter* streamWriter = NULL;
  
};
#endif //AUDIO_DECODER_H