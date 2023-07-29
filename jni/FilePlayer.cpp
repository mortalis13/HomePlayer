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
  LOGD("init() -start-");
  if (!this->openStream()) return false;
  if (!this->startStream()) return false;
  LOGD("init() -end-");
  return true;
}

bool FilePlayer::destroy() {
  LOGD("destroy() -start-");
  this->playing = false;
  
  if (this->decoder) {
    this->decoder->pause();
  }
  
  this->stopStream();
  this->closeStream();
  
  LOGD("destroy() -end-");
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

bool FilePlayer::isStreamClosed() {
  if (!this->audioStream) return true;
  return this->audioStream->getState() == StreamState::Closed;
}

bool FilePlayer::isRestarting() {
  return this->restarting;
}

void FilePlayer::setGain(float gainDb) {
  this->gain = pow(10, gainDb / 20);
  LOGD("setGain(): %.1f dB => %f", gainDb, gain);
}


// ==> Decoder
bool FilePlayer::loadAudio(string audioPath) {
  LOGD("loadAudio() -start- => %s", audioPath.c_str());
  this->playing = false;
  
  if (this->decoder && !this->decoder->isStopped()) {
    this->decoder->stop();
  }

  this->decoder = make_shared<AudioDecoder>(this);
  this->decoder->setChannelCount(audioStream->getChannelCount());
  this->decoder->setSampleRate(audioStream->getSampleRate());
  this->decoder->setRepeat(this->repeat);
  
  int result = 0;
  if (!this->decoder->isLoaded()) {
    result = this->decoder->loadFile(audioPath);
  }
  else {
    LOGI("Decoder is already loaded");
  }

  if (result < 0) return false;
  LOGD("loadAudio() -end- => %s", audioPath.c_str());
  return true;
}

bool FilePlayer::bufferNextAudio(string audioPath) {
  LOGD("bufferNextAudio() -start- => %s", audioPath.c_str());
  
  // Temp decoder used to preload audio that would be played next
  // it will be assigned to the main decoder when the current audio ends normally, with EOF
  bufferedDecoder = make_shared<AudioDecoder>(this);
  bufferedDecoder->setChannelCount(audioStream->getChannelCount());
  bufferedDecoder->setSampleRate(audioStream->getSampleRate());
  bufferedDecoder->setRepeat(repeat);

  int result = bufferedDecoder->loadFile(audioPath);
  nextAudioBuffered = true;
  
  if (result < 0) return false;
  LOGD("bufferNextAudio() -end- => %s", audioPath.c_str());
  return true;
}

bool FilePlayer::startAudio() {
  // --> Main thread || Decoder wait thread
  LOGD("startAudio() -start-");
  if (!this->decoder) return false;
  if (!this->decoder->isLoaded()) {
    LOGE("Trying to start decoder without loading audio first");
    return false;
  }
  
  LOGI("Start playback for %s", this->decoder->getAudioPath().c_str());
  this->decoder->start();
  
  // Wait for thread end to notify the client
  (new std::thread(&FilePlayer::waitDecoderThread, this))->detach();
  
  this->playing = true;
  LOGD("startAudio() -end-");
  return true;
}

void FilePlayer::pause() {
  if (!this->decoder) return;
  this->decoder->pause();
  this->playing = false;
}

bool FilePlayer::resume() {
  LOGD("resume() -start-");
  if (!this->decoder) return false;
  
  if (this->decoder->isStopped()) {
    if (!this->startAudio()) return false;
  }
  else if (!this->decoder->isPlaying()) {
    this->decoder->resume();
  }
  
  this->playing = true;
  LOGD("resume() -end-");
  return true;
}

bool FilePlayer::isPlaying() {
  return playing;
}

void FilePlayer::setRepeat(bool repeat) {
  this->repeat = repeat;
  if (!this->decoder) return;
  this->decoder->setRepeat(repeat);
}

bool FilePlayer::isRepeat() {
  return this->repeat;
}


int FilePlayer::getDuration() {
  if (!this->decoder) return -1;
  return this->decoder->getDuration();
}

int FilePlayer::getCurrentPosition() {
  if (!this->decoder) return -1;
  return this->decoder->getCurrentTime();
}

string FilePlayer::getAudioPath() {
  return this->decoder->getAudioPath();
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

// virtual AudioStreamWriter
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


void FilePlayer::startBufferedDecoder() {
  // --> Decoder wait thread
  LOGD("startBufferedDecoder() -start-");
  if (!nextAudioBuffered || !bufferedDecoder) {
    LOGI("No buffered decoder");
    nextAudioBuffered = false;
    return;
  }
  nextAudioBuffered = false;
  
  LOGI("Changing to buffered decoder and freeing the old decoder");
  this->decoder.swap(bufferedDecoder);
  // bufferedDecoder.reset();
  
  if (!this->decoder->isLoaded()) {
    LOGW("Buffered decoder not loaded yet, waiting 100 ms...");
    this_thread::sleep_for(chrono::milliseconds(100));
  }
  
  startAudio();
  LOGD("startBufferedDecoder() -end-");
}

void FilePlayer::waitDecoderThread() {
  // --> Decoder wait thread
  lock_guard<mutex> guard(decoderWaitMutex);
  LOGD("waitDecoderThread() -start-");
  
  bool isEndedOnEOF = this->decoder->waitDecoderThread();
  this->playing = false;
  
  if (isEndedOnEOF) {
    this->startBufferedDecoder();
    if (engineChangeListener) engineChangeListener->audioEnded();
  }
  
  nextAudioBuffered = false;
  LOGD("waitDecoderThread() -end-");
}
