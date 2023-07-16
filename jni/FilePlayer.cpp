#include "FilePlayer.h"
#define LOG_MODULE_NAME "_FilePlayer"

#include "utils/logging.h"


bool FilePlayer::init() {
  this->playing = false;
  
  if (!this->openStream()) return false;
  if (!this->startStream()) return false;
  
  return true;
}

bool FilePlayer::destroy() {
  this->playing = false;
  this->decoder->stop();
  
  this->stopStream();
  this->closeStream();
  return true;
}

bool FilePlayer::openStream() {
  LOGD("openStream()");
  mDataCallback = make_shared<MyDataCallback>(this);
  mErrorCallback = make_shared<MyErrorCallback>(this);

  AudioStreamBuilder builder;

  // builder.setSharingMode(SharingMode::Exclusive);
  // builder.setPerformanceMode(PerformanceMode::LowLatency);
  builder.setFormat(AudioFormat::Float);
  // builder.setFormatConversionAllowed(true);
  builder.setChannelCount(kChannelCount);
  builder.setDataCallback(mDataCallback);
  builder.setErrorCallback(mErrorCallback);
  builder.setSampleRate(44100);
  // builder.setSampleRateConversionQuality(SampleRateConversionQuality::Medium);

  auto result = builder.openStream(mStream);
  if (result != Result::OK) {
    LOGE("Failed to open stream. Error: %s", convertToText(result));
    return false;
  }
  return true;
}

bool FilePlayer::startStream() {
  LOGD("startStream()");
  auto result = mStream->requestStart();
  if (result != Result::OK) {
    LOGE("Failed to start stream. Error: %s", convertToText(result));
    return false;
  }
  return true;
}

bool FilePlayer::stopStream() {
  LOGD("stopStream()");
  auto result = mStream->requestStop();
  LOGI("Stop stream result: %s", convertToText(result));
  return true;
}

bool FilePlayer::closeStream() {
  LOGD("closeStream()");
  auto result = mStream->close();
  LOGI("Close stream result: %s", convertToText(result));
  return true;
}


void FilePlayer::initDecoder() {
  LOGD("initDecoder()");
  if (this->decoder != NULL) {
    this->decoder->stop();
    delete this->decoder;
  }
  
  this->decoder = new AudioDecoder(&dataQ);
  this->decoder->setChannelCount(mStream->getChannelCount());
  this->decoder->setSampleRate(mStream->getSampleRate());
}


bool FilePlayer::loadAudio(string audioPath) {
  LOGD("loadAudio()");
  this->playing = false;
  this->ended = false;
  
  this->initDecoder();
  this->emptyQueue();
  
  bool result = loadFile(audioPath);
  if (!result) return result;
  return true;
}


bool FilePlayer::startAudio() {
  LOGD("startAudio()");
  this->playing = true;
  this->decoder->start();
  return true;
}

void FilePlayer::pause() {
  LOGD("pause()");
  this->playing = false;
  // this->decoder->stop();
}

void FilePlayer::resume() {
  LOGD("resume()");
  this->playing = true;
  if (!this->decoder->isPlaying()) {
    this->decoder->start();
  }
}

int FilePlayer::getDuration() {
  return this->decoder->getDuration();
}

int FilePlayer::getCurrentPosition() {
  return this->decoder->getCurrentTime();
}

void FilePlayer::seekTo(int time_ms) {
  this->playing = false;
  
  if (time_ms < 0) time_ms = 0;
  this->decoder->seekTo(time_ms);

  this->emptyQueue();
  this->playing = true;
}


bool FilePlayer::loadFile(string audioPath) {
  int result = this->decoder->loadFile(audioPath);
  if (result < 0) return false;
  return true;
}


void FilePlayer::emptyQueue() {
  this->dataQ.reset();
  LOGI("Queue emptied");
}


void FilePlayer::writeAudio(float* stream, int32_t numFrames) {
  // --> Audio thread
  for (int i = 0; i < numFrames; i++) {
    for (int ch = 0; ch < kChannelCount; ch++) {
      float sample = 0;
      if (this->playing) {
        bool result = this->dataQ.pop(sample);
        
        if (!result && this->decoder->isEnded()) {
          this->playing = false;
          this->ended = true;
          LOGI("Audio stream: playback ended");
        }
      }
      
      *stream++ = sample;
    }
  }
}


DataCallbackResult FilePlayer::MyDataCallback::onAudioReady(AudioStream* audioStream, void* audioData, int32_t numFrames) {
  if (!mParent->playing) {
    memset(audioData, 0, numFrames * kChannelCount * sizeof(float));
    return DataCallbackResult::Continue;
  }
  
  float* stream = (float*) audioData;
  mParent->writeAudio(stream, numFrames);
  return DataCallbackResult::Continue;
}

void FilePlayer::MyErrorCallback::onErrorAfterClose(AudioStream* oboeStream, oboe::Result error) {
  LOGE("%s() - error = %s", __func__, oboe::convertToText(error));
  mParent->init();
}
