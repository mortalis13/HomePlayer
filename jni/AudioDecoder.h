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
  
  void setChannelCount(int32_t channelCount) {
    this->channelCount = channelCount;
  }
  
  void setSampleRate(int32_t sampleRate) {
    this->sampleRate = sampleRate;
  }
  
  int32_t getDataChannels() {
    return dataChannels;
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
  
  void seekTo(int time_ms);
  int getCurrentTime();
  int getDuration();


private:
  static void printCodecParameters(AVCodecParameters* codecParams);
  static void printResamplerParameters(AVStream* audioStream, AVChannelLayout outChannelLayout, int32_t outSampleRate, AVSampleFormat outSampleFormat);
  
  void run();
  void cleanup();
  int decodeFrames();
  void writeFrame(uint8_t* buffer, int32_t numFrames);
  

private:
  bool stopped = true;
  bool playing = false;
  bool ended = false;
  bool is_eof = false;
  
  int32_t channelCount = 0;
  int32_t sampleRate = 0;
  int32_t dataChannels = 0;
  
  int64_t currentPTS = 0;
  int delayedSamples = 0;
  
  AVFormatContext* formatContext = NULL;
  AVCodecContext* codecContext = NULL;
  SwrContext* swrContext = NULL;
  
  int audioStreamIndex = -1;
  
  future<void> runThread;
  
  bool seekPending = false;
  int64_t seekTimestamp = 0;
  
  AudioStreamWriter* streamWriter = NULL;
  
};
#endif //AUDIO_DECODER_H