#include "FilePlayer.h"
#define LOG_MODULE_NAME "_FilePlayer"

#include "utils/logging.h"


FilePlayer::FilePlayer() {
  this->filters = new PeakingFilter[FILTER_BANDS_NUMBER];
  for (int band = 0; band < FILTER_BANDS_NUMBER; ++band) {
    this->filters[band].setSampleRate(STREAM_SAMPLE_RATE);
    this->filters[band].setQFactor(1.0);
  }
}


bool FilePlayer::init() {
  LOGD("init()");
  if (!this->openStream()) return false;
  if (!this->startStream()) return false;
  return true;
}

bool FilePlayer::destroy() {
  LOGD("destroy()");
  this->playing = false;
  
  if (this->decoder != NULL) {
    this->decoder->pause();
  }
  
  this->stopStream();
  this->closeStream();
  return true;
}

bool FilePlayer::isStreamClosed() {
  if (!this->audioStream) return true;
  return this->audioStream->getState() == StreamState::Closed;
}

bool FilePlayer::isRestarting() {
  return this->restarting;
}


// ==> Audio stream
bool FilePlayer::openStream() {
  LOGD("openStream()");
  AudioStreamBuilder builder;
  builder.setFormat(STREAM_SAMPLE_FORMAT);
  builder.setChannelCount(STREAM_CHANNELS);
  builder.setSampleRate(STREAM_SAMPLE_RATE);

  auto result = builder.openStream(audioStream);
  LOGI("Open stream result: %s", convertToText(result));
  return result == Result::OK;
}

bool FilePlayer::startStream() {
  LOGD("startStream()");
  auto result = audioStream->requestStart();
  LOGI("Start stream result: %s", convertToText(result));
  return result == Result::OK;
}

bool FilePlayer::stopStream() {
  LOGD("stopStream()");
  auto result = audioStream->requestStop();
  LOGI("Stop stream result: %s", convertToText(result));
  return result == Result::OK;
}

bool FilePlayer::closeStream() {
  LOGD("closeStream()");
  auto result = audioStream->close();
  LOGI("Close stream result: %s", convertToText(result));
  return result == Result::OK;
}

bool FilePlayer::restartStream() {
  LOGD("restartStream()");
  this->restarting = true;
  this->closeStream();
  
  if (this->init()) {
    auto nextState = StreamState::Uninitialized;
    int64_t ms = 100 * 1000000;  // value x nanos
    audioStream->waitForStateChange(StreamState::Starting, &nextState, ms);
    this->restarting = false;
    return true;
  }
  
  this->restarting = false;
  return false;
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
  
  this->initDecoder();
  
  int result = this->decoder->loadFile(audioPath);
  if (result < 0) return false;
  return true;
}

bool FilePlayer::startAudio() {
  LOGD("startAudio()");
  if (this->decoder == NULL) return false;
  if (!this->decoder->isLoaded()) {
    LOGE("Trying to start decoder without loading audio first");
    return false;
  }
  
  this->decoder->start();
  this->playing = true;
  return true;
}

void FilePlayer::pause() {
  if (this->decoder == NULL) return;
  this->decoder->pause();
  this->playing = false;
}

bool FilePlayer::resume() {
  LOGD("resume()");
  if (this->decoder == NULL) return false;
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
  if (this->decoder == NULL) return true;
  bool decoderEnded = this->decoder->isEnded();
  if (decoderEnded) this->playing = false;
  return decoderEnded;
}

bool FilePlayer::isPlaying() {
  return playing;
}

void FilePlayer::setRepeat(bool repeat) {
  if (this->decoder == NULL) return;
  this->decoder->setRepeat(repeat);
}

bool FilePlayer::isRepeat() {
  if (this->decoder == NULL) return false;
  return this->decoder->isRepeat();
}


int FilePlayer::getCurrentPosition() {
  if (this->decoder == NULL) return -1;
  return this->decoder->getCurrentTime();
}

int FilePlayer::getDuration() {
  if (this->decoder == NULL) return -1;
  return this->decoder->getDuration();
}

void FilePlayer::seekTo(int time_ms) {
  if (this->decoder == NULL) return;
  if (time_ms < 0) time_ms = 0;
  this->decoder->seekTo(time_ms);
}


// ==> Filter
void FilePlayer::enableFilter() {
  LOGD("enableFilter()");
  for (int band = 0; band < FILTER_BANDS_NUMBER; ++band) {
    this->filters[band].reset();
  }
  this->isFilterEnabled = true;
}

void FilePlayer::disableFilter() {
  LOGD("disableFilter()");
  this->isFilterEnabled = false;
}

void FilePlayer::setFilterFrequency(int band, float frequency) {
  LOGD("setFilterFrequency(): %d => %.0f Hz", band, frequency);
  this->filters[band-1].setFrequency(frequency);
}

void FilePlayer::setFilterGain(int band, float gain) {
  float frequency = this->filters[band-1].getFrequency();
  LOGD("setFilterGain(): %d [%.0f Hz] => %+.1f dB", band, frequency, gain);
  this->filters[band-1].setGainDb(gain);
}


// ==> Audio params
int FilePlayer::getChannels() {
  if (!this->decoder || !this->decoder->isLoaded()) return 0;
  return this->decoder->audioParams.channels;
}

int FilePlayer::getSampleRate() {
  if (!this->decoder || !this->decoder->isLoaded()) return 0;
  return this->decoder->audioParams.sample_rate;
}

string FilePlayer::getSampleFormat() {
  if (!this->decoder || !this->decoder->isLoaded()) return "";
  return this->decoder->audioParams.sample_format;
}

int FilePlayer::getBitrate() {
  if (!this->decoder || !this->decoder->isLoaded()) return 0;
  return this->decoder->audioParams.bitrate;
}

string FilePlayer::getCodecName() {
  if (!this->decoder || !this->decoder->isLoaded()) return "";
  return this->decoder->audioParams.codec_name;
}


void FilePlayer::filterAudio(float* stream, int32_t numFrames, int8_t channels) {
  for (int i = 0; i < numFrames; ++i) {
    for (int ch = 0; ch < channels; ++ch) {
      int id = i * channels + ch;
      
      float sample = stream[id];
      for (int band = 0; band < FILTER_BANDS_NUMBER; ++band) {
        sample = this->filters[band].processAudioSample(sample, ch);
      }
      stream[id] = sample;
    }
  }
}

void FilePlayer::writeAudio(uint8_t* stream, int32_t numFrames) {
  if (this->isFilterEnabled) {
    this->filterAudio((float*) stream, numFrames, this->audioStream->getChannelCount());
  }

  auto result = audioStream->write(stream, numFrames, 100);
  
  if (!result) {
    LOGE("Stream write error: %s", convertToText(result.error()));
    if (result.error() == Result::ErrorDisconnected) {
      LOGW("Stream is disconnected. Restarting");
      if (this->restartStream()) {
        audioStream->write(stream, numFrames, 100);
      }
    }
  }
}
