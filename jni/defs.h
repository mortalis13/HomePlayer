#ifndef DEFS_H
#define DEFS_H

class AudioStreamWriter {
public:
  virtual void writeAudio(uint8_t* stream, int32_t numFrames) = 0;
};

#endif //DEFS_H