#define LOG_MODULE_NAME "FilePlayer_"

#include "FilePlayer.h"

#include "utils/logging.h"


bool FilePlayer::init() {
  isPlaying = false;
  
  if (!this->open()) return false;
  if (!this->start()) return false;
  
  if (this->decoder != NULL) {
    this->decoder->stop();
    delete this->decoder;
  }
  this->decoder = new AudioDecoder(&dataQ);
  this->decoder->setChannelCount(mStream->getChannelCount());
  this->decoder->setSampleRate(mStream->getSampleRate());
  
  this->audioFilter = new PeakingFilter();
  this->audioFilter->setSampleRate(mStream->getSampleRate());
  this->audioFilter->setQFactor(1.0);
  this->audioFilter->setFrequency(100.0);
  this->audioFilter->setGainDb(-10.0);
  
  LOGI("Filter is set up for %.2f Hz, %.2f dB, %.2f Q",
    this->audioFilter->getFrequency(),
    this->audioFilter->getGainDb(),
    this->audioFilter->getQFactor());
  
  return true;
}

bool FilePlayer::open() {
  mDataCallback = make_shared<MyDataCallback>(this);
  mErrorCallback = make_shared<MyErrorCallback>(this);

  AudioStreamBuilder builder;

  builder.setSharingMode(SharingMode::Exclusive);
  builder.setPerformanceMode(PerformanceMode::LowLatency);
  builder.setFormat(AudioFormat::Float);
  builder.setFormatConversionAllowed(true);
  builder.setChannelCount(kChannelCount);
  builder.setDataCallback(mDataCallback);
  builder.setErrorCallback(mErrorCallback);
  builder.setSampleRate(48000);
  builder.setSampleRateConversionQuality(SampleRateConversionQuality::Medium);

  auto result = builder.openStream(mStream);
  if (result != Result::OK) {
    LOGE("Failed to open stream. Error: %s", convertToText(result));
    return false;
  }
  return true;
}

bool FilePlayer::start() {
  auto result = mStream->requestStart();
  if (result != Result::OK) {
    LOGE("Failed to start stream. Error: %s", convertToText(result));
    return false;
  }
  return true;
}

oboe::Result FilePlayer::stop() {
  this->isPlaying = false;
  this->decoder->stop();
  return mStream->requestStop();
}

oboe::Result FilePlayer::close() {
  return mStream->close();
}

void FilePlayer::enableFilter() {
  this->audioFilter->reset();
  this->isFilterEnabled = true;
}

void FilePlayer::disableFilter() {
  this->isFilterEnabled = false;
}

void FilePlayer::addFilterFrequency(float hz) {
  double freq = this->audioFilter->getFrequency() + hz;
  this->audioFilter->setFrequency(freq);
}

void FilePlayer::addFilterGain(float db) {
  double gain = this->audioFilter->getGainDb() + db;
  this->audioFilter->setGainDb(gain);
}


bool FilePlayer::play(string audioPath) {
  this->isPlaying = false;
  this->decoder->stop();
  this->emptyQueue();
  
  this->audioFilter->reset();
  
  bool result = loadFile(audioPath);
  if (!result) return result;
  
  this->isPlaying = true;
  
  this->decoder->start();
  LOGI("Decoder started");

  return true;
}


void FilePlayer::emptyQueue() {
  float sample;
  while (this->dataQ.pop(sample)) {}
  LOGI("Queue emptied");
}


bool FilePlayer::loadFile(string audioPath) {
  int result = this->decoder->loadFile(audioPath);
  if (result < 0) return false;
  return true;
}


void FilePlayer::writeAudio(float* stream, int32_t numFrames) {
  // Audio thread
  for (int i = 0; i < numFrames; i++) {
    for (int ch = 0; ch < kChannelCount; ch++) {
      float sample = 0;
      if (this->isPlaying) {
        this->dataQ.pop(sample);
      }
      
      if (this->isFilterEnabled && this->audioFilter) {
        sample = this->audioFilter->processAudioSample(sample, ch);
      }
      
      *stream++ = sample;
    }
  }
}


DataCallbackResult FilePlayer::MyDataCallback::onAudioReady(AudioStream* audioStream, void* audioData, int32_t numFrames) {
  if (!mParent->isPlaying) {
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
