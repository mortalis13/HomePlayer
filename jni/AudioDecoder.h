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
  AudioDecoder(AudioStreamWriter* streamWriter);
  AudioDecoder();
  ~AudioDecoder();
  
  int loadCodec(string filePath);
  int loadFile(string filePath);
  void cleanup();
  
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
  
  void setLoop(bool loop);
  void setLoopStart(int time);
  void setLoopEnd(int time);
  
  void seekTo(int time_ms);
  int getCurrentTime();
  int getDuration();
  
  string getAudioPath() {
    return audioPath;
  }

  bool waitDecoderThread();
  
  int compressSamples(float* samples, int dest_size);
  void stopCompression();

public:
  AudioParams audioParams;


private:
  static void printCodecParameters(AVCodecParameters* codecParams);
  static void printResamplerParameters(AVCodecParameters* codecParams, AVChannelLayout outChannelLayout, int32_t outSampleRate, AVSampleFormat outSampleFormat);
  
  void run();
  int decodeFrames();
  void processAVFrame(uint8_t* buffer, int32_t numFrames, int32_t skipFrames);
  
  int findDelayedSamples();
  void fillAudioParams(AVCodecParameters* codecParams);

private:
  atomic<bool> loaded = false;  // File is loaded in the decoder and format context, codec context and resampler are created
  atomic<bool> stopped = true;  // Decoder is stopped, after finishing decoding or was forces to stop from outside
  atomic<bool> playing = false; // Decoder is decoding the current file and writes samples to the output stream
  atomic<bool> ended = false;   // Decoding reached EOF and doesn't have more data to decode from the current file
  atomic<bool> error = false;   // Critical error when decoding data that stops the decoder thread
  
  atomic<bool> repeat = false;
  atomic<bool> loop = false;
  
  bool compressing = false;
  
  int loopStart = 0;
  int loopEnd = 0;
  bool loopSeekPending = false;
  
  int32_t outChannelCount = 0;
  int32_t outSampleRate = 0;
  int32_t dataChannels = 0;
  
  int64_t currentPTS = 0;  // in samples
  int delayedSamples = 0;
  
  bool seekPending = false;
  int64_t seekTimestamp = 0;
  
  string audioPath;
  
  int audioStreamIndex = -1;
  
  AVFormatContext* formatContext = NULL;
  AVCodecContext* codecContext = NULL;
  SwrContext* swrContext = NULL;
  
  future<void> runThread;
  promise<void>* threadEndSignal = NULL;
  
  AudioStreamWriter* streamWriter = NULL;
  
};
#endif //AUDIO_DECODER_H