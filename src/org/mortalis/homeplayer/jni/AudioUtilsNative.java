package org.mortalis.homeplayer.jni;

public class AudioUtilsNative {
  
  native static public DecoderResult buildWaveform(String audioPath, int view_with, int view_height);
  native static public void cancelWaveform();
  
  native static public DecoderResult decodeSamples(String audioPath, int view_with, int view_height);
  native static public void stopDecoding();
  
}
