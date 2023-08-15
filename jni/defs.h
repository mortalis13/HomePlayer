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
  // stream: buffer with audio samples to read from, byte pointer
  //   (for float samples, 1 sample is 4 bytes)
  // numFrames: number of audio frames to read from the stream
  //   (1 frame contains N samples for N channels, for float samples, 1 frame 4*N bytes are read from the stream)
  // skipFrames: number of audio frames to skip from the stream beginning
  //   (the number of frames that has to be read from the end of the stream is numFrames-skipFrames)
  virtual void writeAudio(uint8_t* stream, int32_t numFrames, int32_t skipFrames) = 0;
};

class EngineChangeListener {
public:
  virtual void audioEnded() = 0;
};

#endif //DEFS_H