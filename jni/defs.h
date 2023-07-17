#ifndef DEFS_H
#define DEFS_H

#include "utils/LockFreeQueue.h"

typedef struct Frame {
  float* samples;
  int size;
  int64_t pts;
} Frame;

typedef LockFreeQueue<Frame*, 1 << 10> SharedQueue;

#endif //DEFS_H