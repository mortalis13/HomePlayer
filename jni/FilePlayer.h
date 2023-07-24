#ifndef FILE_PLAYER_H
#define FILE_PLAYER_H

#include <string>

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

public:
  FilePlayer() {}
  ~FilePlayer() {}
  
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
  
  // Decoder
  bool loadAudio(string audioPath);
  bool startAudio();
  void pause();
  bool resume();
  
  int getDuration();
  int getCurrentPosition();
  void seekTo(int time_ms);
  
  bool isStopped();
  bool isPlaying();
  
  void setRepeat(bool repeat);
  bool isRepeat();
  
  // Audio params
  int getChannels();
  int getSampleRate();
  string getSampleFormat();
  int getBitrate();
  string getCodecName();

  virtual void writeAudio(uint8_t* stream, int32_t numFrames);
  
private:
  bool loadFile(string audioPath);

  void initDecoder();
  void emptyQueue();
  
  bool restartStream();

private:
  
  bool playing = false;
  bool seeking = false;
  bool restarting = false;

  shared_ptr<AudioDecoder> decoder;
  
  shared_ptr<AudioStream> audioStream;

};
#endif //FILE_PLAYER_H