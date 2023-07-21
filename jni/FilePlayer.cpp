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


// ==> Audio stream
bool FilePlayer::openStream() {
  LOGD("openStream()");
  AudioStreamBuilder builder;
  builder.setFormat(STREAM_SAMPLE_FORMAT);
  builder.setChannelCount(STREAM_CHANNELS);
  builder.setSampleRate(STREAM_SAMPLE_RATE);

  auto result = builder.openStream(audioStream);
  if (result != Result::OK) {
    LOGE("Failed to open stream. Error: %s", convertToText(result));
    return false;
  }
  return true;
}

bool FilePlayer::startStream() {
  LOGD("startStream()");
  auto result = audioStream->requestStart();
  if (result != Result::OK) {
    LOGE("Failed to start stream. Error: %s", convertToText(result));
    return false;
  }
  return true;
}

bool FilePlayer::stopStream() {
  LOGD("stopStream()");
  auto result = audioStream->requestStop();
  LOGI("Stop stream result: %s", convertToText(result));
  return true;
}

bool FilePlayer::closeStream() {
  LOGD("closeStream()");
  auto result = audioStream->close();
  LOGI("Close stream result: %s", convertToText(result));
  return true;
}


// ==> Decoder
void FilePlayer::initDecoder() {
  LOGD("initDecoder()");
  if (this->decoder != NULL) {
    this->decoder->stop();
  }
  
  this->decoder = make_shared<AudioDecoder>(this);
  this->decoder->setChannelCount(audioStream->getChannelCount());
  this->decoder->setSampleRate(audioStream->getSampleRate());
}

bool FilePlayer::loadAudio(string audioPath) {
  LOGD("loadAudio()");
  this->playing = false;
  this->ended = false;
  
  this->initDecoder();
  
  int result = this->decoder->loadFile(audioPath);
  if (result < 0) return false;
  return true;
}

bool FilePlayer::startAudio() {
  LOGD("startAudio()");
  if (!this->decoder->isLoaded()) {
    LOGE("Trying to start decoder without loading audio first");
    return false;
  }
  
  this->decoder->start();
  this->playing = true;
  return true;
}

void FilePlayer::pause() {
  this->decoder->pause();
  this->playing = false;
}

bool FilePlayer::resume() {
  LOGD("resume()");
  if (this->decoder->isStopped()) {
    if (!this->startAudio()) return false;
  }
  else if (!this->decoder->isPlaying()) {
    this->decoder->resume();
  }
  
  this->playing = true;
  return true;
}

bool FilePlayer::isStopped() {
  bool decoderEnded = this->decoder->isEnded();
  if (decoderEnded) this->playing = false;
  return decoderEnded;
}


int FilePlayer::getCurrentPosition() {
  return this->decoder->getCurrentTime();
}

int FilePlayer::getDuration() {
  return this->decoder->getDuration();
}

void FilePlayer::seekTo(int time_ms) {
  if (time_ms < 0) time_ms = 0;
  this->decoder->seekTo(time_ms);
}


void FilePlayer::writeAudio(uint8_t* stream, int32_t numFrames) {
  audioStream->write(stream, numFrames, 100);
}
