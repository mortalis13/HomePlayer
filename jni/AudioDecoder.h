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
  AudioDecoder(SharedQueue* dataQ) {
    this->dataQ = dataQ;
  }
  
  ~AudioDecoder() {
    this->cleanup();
  }
  
  int loadFile(string filePath);
  void start();
  void stop();
  
  void setChannelCount(int32_t channelCount) {
    this->channelCount = channelCount;
  }
  
  void setSampleRate(int32_t sampleRate) {
    this->sampleRate = sampleRate;
  }
  
  int32_t getDataChannels() {
    return dataChannels;
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
  void saveFrame(short* buffer, int64_t bytesWritten, int64_t bytesToWrite);
  
  bool playing = false;
  bool ended = false;
  
  int32_t channelCount = 0;
  int32_t sampleRate = 0;
  int32_t dataChannels = 0;
  
  int64_t currentPTS = 0;
  
  AVFormatContext* formatContext = NULL;
  AVCodecContext* codecContext = NULL;
  SwrContext* swrContext = NULL;
  
  AVStream* audioStream = NULL;
  const AVCodec* audioCodec = NULL;
  
  SharedQueue* dataQ = NULL;
  
  future<void> runThread;
  
};
#endif //AUDIO_DECODER_H