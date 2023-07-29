#ifndef FILE_PLAYER_H
#define FILE_PLAYER_H

#include <string>
#include <mutex>

#include "oboe/Oboe.h"

#include "AudioDecoder.h"
#include "AudioFilter.h"
#include "defs.h"

using namespace std;
using namespace oboe;


class FilePlayer : public AudioStreamWriter {

static const AudioFormat STREAM_SAMPLE_FORMAT = AudioFormat::Float;
static const int STREAM_CHANNELS = 2;
static const int STREAM_SAMPLE_RATE = 44100;
static const int FILTER_BANDS_NUMBER = 8;

public:
  FilePlayer();
  ~FilePlayer() {
    delete[] this->filters;
  }
  
  // Engine
  bool init();
  bool destroy();
  
  // Stream
  bool openStream();
  bool startStream();
  bool stopStream();
  bool closeStream();
  bool isStreamClosed();
  bool isRestarting();
  void setGain(float gainDb);
  
  // Decoder
  bool loadAudio(string audioPath);
  bool startAudio();
  void pause();
  bool resume();
  
  int getDuration();
  int getCurrentPosition();
  void seekTo(int time_ms);
  
  bool isPlaying();
  
  void setRepeat(bool repeat);
  bool isRepeat();
  
  // Filter
  void enableFilter();
  void disableFilter();
  
  void setFilterFrequency(int band, float frequency);
  void setFilterGain(int band, float gain);
  void setFilterQ(float q);
  
  // Audio params
  int getChannels();
  int getSampleRate();
  string getSampleFormat();
  int getBitrate();
  string getCodecName();

  virtual void writeAudio(uint8_t* stream, int32_t numFrames);
  
  EngineChangeListener* engineChangeListener = NULL;


private:
  bool restartStream();

  void initDecoder();
  bool loadFile(string audioPath);
  
  void processAudio(float* stream, int32_t numFrames, int8_t channels);
  void filterAudio(float* stream, int32_t numFrames, int8_t channels);

  void waitDecoderThread();
private:
  
  bool playing = false;
  bool seeking = false;
  bool restarting = false;
  
  float gain = 1.0f;

  shared_ptr<AudioStream> audioStream;
  shared_ptr<AudioDecoder> decoder;

  PeakingFilter* filters;
  bool isFilterEnabled = false;
  
  mutex decoderWaitMutex;

};
#endif //FILE_PLAYER_H