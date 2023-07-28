#include "FilePlayer.h"
#define LOG_MODULE_NAME "_FilePlayer"

#include "utils/logging.h"


FilePlayer::FilePlayer() {
  this->filters = new PeakingFilter[FILTER_BANDS_NUMBER];
  for (int band = 0; band < FILTER_BANDS_NUMBER; ++band) {
    this->filters[band].setSampleRate(STREAM_SAMPLE_RATE);
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
  
  if (this->decoder) {
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
  bool result = false;
  this->restarting = true;
  this->closeStream();
  
  if (this->init()) {
    auto nextState = StreamState::Uninitialized;
    int64_t ms = 100 * 1000000;  // value x nanos
    audioStream->waitForStateChange(StreamState::Starting, &nextState, ms);
    result = true;
  }
  
  this->restarting = false;
  return result;
}

void FilePlayer::setGain(float gainDb) {
  this->gain = pow(10, gainDb / 20);
  LOGD("setGain(): %.1f dB => %f", gainDb, gain);
}


// ==> Decoder
void FilePlayer::initDecoder() {
  LOGD("initDecoder()");
  if (this->decoder && !this->decoder->isStopped()) {
    this->decoder->stop();
  }

  this->decoder = make_shared<AudioDecoder>(this, this);
  this->decoder->setChannelCount(audioStream->getChannelCount());
  this->decoder->setSampleRate(audioStream->getSampleRate());
}

bool FilePlayer::preloadAudio(string audioPath) {
  LOGD("preloadNextAudio()");

  nextDecoder = make_shared<AudioDecoder>(this, this);
  nextDecoder->setChannelCount(audioStream->getChannelCount());
  nextDecoder->setSampleRate(audioStream->getSampleRate());
  int result = nextDecoder->loadFile(audioPath);
  nextPreloaded = true;
  
  if (result < 0) return false;
  return true;
}

bool FilePlayer::loadAudio(string audioPath) {
  LOGD("loadAudio()");
  this->playing = false;
  this->initDecoder();
  
  int result = 0;
  if (!this->decoder->isLoaded()) {
    result = this->decoder->loadFile(audioPath);
  }
  else {
    LOGI("decoder is already loaded");
  }

  if (result < 0) return false;
  return true;
}

bool FilePlayer::startAudio() {
  LOGD("startAudio()");
  if (!this->decoder) return false;
  if (!this->decoder->isLoaded()) {
    LOGE("Trying to start decoder without loading audio first");
    return false;
  }
  
  LOGI("Start playback for %s", this->decoder->getAudioPath().c_str());
  
  this->decoder->start();
  
  std::thread t(&FilePlayer::waitDec, this);
  t.detach();
  
  this->playing = true;
  return true;
}

void FilePlayer::pause() {
  if (!this->decoder) return;
  this->decoder->pause();
  this->playing = false;
}

bool FilePlayer::resume() {
  LOGD("resume()");
  if (!this->decoder) return false;
  
  if (this->decoder->isStopped()) {
    if (!this->startAudio()) return false;
  }
  else if (!this->decoder->isPlaying()) {
    this->decoder->resume();
  }
  
  this->playing = true;
  return true;
}

bool FilePlayer::fileChanged(string audioPath) {
  bool result = this->decoder->getAudioPath().compare(audioPath) != 0;
  if (result) {
    LOGW("-- file changed: %s => %s", this->decoder->getAudioPath().c_str(), audioPath.c_str());
  }
  return result;
}

bool FilePlayer::isStopped() {
  if (!this->decoder) return true;
  bool decoderEnded = this->decoder->isEnded();
  if (decoderEnded) this->playing = false;
  return decoderEnded;
}

bool FilePlayer::isPlaying() {
  return playing;
}

void FilePlayer::setRepeat(bool repeat) {
  if (!this->decoder) return;
  this->decoder->setRepeat(repeat);
}

bool FilePlayer::isRepeat() {
  if (!this->decoder) return false;
  return this->decoder->isRepeat();
}


int FilePlayer::getCurrentPosition() {
  if (!this->decoder) return -1;
  return this->decoder->getCurrentTime();
}

int FilePlayer::getDuration() {
  if (!this->decoder) return -1;
  return this->decoder->getDuration();
}

void FilePlayer::seekTo(int time_ms) {
  if (!this->decoder) return;
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
  // band is order number from 1 to total bands
  if (band < 1 || band > FILTER_BANDS_NUMBER) return;
  this->filters[band-1].setFrequency(frequency);
}

void FilePlayer::setFilterGain(int band, float gain) {
  float frequency = this->filters[band-1].getFrequency();
  LOGD("setFilterGain(): %d [%.0f Hz] => %+.1f dB", band, frequency, gain);
  if (band < 1 || band > FILTER_BANDS_NUMBER) return;
  this->filters[band-1].setGainDb(gain);
}

void FilePlayer::setFilterQ(float q) {
  LOGD("setFilterQ(): %.1f", q);
  for (int band = 0; band < FILTER_BANDS_NUMBER; ++band) {
    this->filters[band].setQFactor(q);
  }
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


void FilePlayer::processAudio(float* stream, int32_t numFrames, int8_t channels) {
  if (this->gain == 1.0f) return;

  for (int i = 0; i < numFrames * channels; ++i) {
    stream[i] = this->gain * stream[i];
  }
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
  this->processAudio((float*) stream, numFrames, audioStream->getChannelCount());
  
  if (this->isFilterEnabled) {
    this->filterAudio((float*) stream, numFrames, audioStream->getChannelCount());
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



void FilePlayer::assignNextDecoder() {
  LOGI("assignNextDecoder()");
  // if (loadAudio("dummy")) startAudio();
  // LOGI("--after load-start");
}

void FilePlayer::decoderEnded() {
  LOGI("decode ended");
  // std::async(&FilePlayer::assignNextDecoder, this);
}


void FilePlayer::playWithPreloadedDecoder() {
  if (!nextPreloaded || !nextDecoder) {
    nextPreloaded = false;
    return;
  }
  
  LOGD("playWithPreloadedDecoder()");
  
  nextPreloaded = false;
  this->playing = false;
  
  this->decoder.swap(nextDecoder);
  nextDecoder.reset();
  
  if (!this->decoder->isLoaded()) {
    LOGW("nextDecoder not loaded yet, waiting 100 ms...");
    this_thread::sleep_for(chrono::milliseconds(100));
  }
  
  startAudio();
}

void FilePlayer::waitDec() {
  // lock_guard<mutex> guard(decoderWaitMutex);
  
  LOGI("--> wait for ended");
  bool eof = this->decoder->waitRun();
  LOGI("--> ended");
  
  // this->playing = false;
  
  if (eof) {
    this->playWithPreloadedDecoder();
    if (engineChangeListener) engineChangeListener->audioEnded();
  }
  
  nextPreloaded = false;
}
