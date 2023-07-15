#ifndef FILE_PLAYER_H
#define FILE_PLAYER_H

#include <string>

#include "oboe/Oboe.h"

#include "AudioDecoder.h"
#include "AudioFilter.h"
#include "defs.h"

using namespace std;
using namespace oboe;


class FilePlayer {

class MyDataCallback;
class MyErrorCallback;

public:
  FilePlayer() {}
  
  ~FilePlayer() {
    if (decoder != NULL) delete decoder;
  }
  
  bool init();
  bool destroy();
  
  bool openStream();
  bool startStream();
  bool stopStream();
  bool closeStream();
  
  bool loadAudio(string audioPath);
  bool startAudio();
  void pause();
  void resume();
  
  int getDuration();
  int getCurrentPosition();
  void seekTo(int time_ms);
  
  bool isPlaying() {
    return playing;
  }

  bool isStopped() {
    return ended;
  }

private:
  bool loadFile(string audioPath);
  void writeAudio(float* stream, int32_t numFrames);

  void initDecoder();
  void emptyQueue();

private:
  static constexpr int kChannelCount = 2;
  
  bool playing = false;
  bool ended = false;

  AudioDecoder* decoder = NULL;
  SharedQueue dataQ;
  
  int64_t currentSamples = 0;
  
  
  shared_ptr<AudioStream> mStream;
  shared_ptr<MyDataCallback> mDataCallback;
  shared_ptr<MyErrorCallback> mErrorCallback;


class MyDataCallback : public AudioStreamDataCallback {
public:
    MyDataCallback(FilePlayer* parent) : mParent(parent) {}
    DataCallbackResult onAudioReady(AudioStream* audioStream, void* audioData, int32_t numFrames) override;
private:
    FilePlayer* mParent;
};


class MyErrorCallback : public AudioStreamErrorCallback {
public:
    MyErrorCallback(FilePlayer* parent) : mParent(parent) {}
    virtual ~MyErrorCallback() {}
    void onErrorAfterClose(AudioStream* oboeStream, oboe::Result error) override;
private:
    FilePlayer* mParent;
};

};
#endif //FILE_PLAYER_H