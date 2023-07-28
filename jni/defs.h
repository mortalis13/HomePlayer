#ifndef DEFS_H
#define DEFS_H

#include <string>

using namespace std;

typedef struct AudioParams {
  int channels;
  int sample_rate;
  int frame_size;
  string sample_format;
  bool is_planar;
  int bytes_per_sample;
  int bitrate;
  string codec_type;
  string codec_name;
} AudioParams;

class AudioStreamWriter {
public:
  virtual void writeAudio(uint8_t* stream, int32_t numFrames) = 0;
};

class DecoderEndListener {
public:
  virtual void decoderEnded() = 0;
};

class EngineChangeListener {
public:
  virtual void audioEnded() = 0;
};

#endif //DEFS_H