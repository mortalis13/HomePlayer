package org.mortalis.homeplayer.jni;

public class AudioUtilsNative {
  
  native static public int buildWaveform(String audioPath, int viewWidth, int viewHeight);
  native static public void cancelWaveform();
  
  public static short[] waveformData;
  
}
