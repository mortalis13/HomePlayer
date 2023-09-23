package org.mortalis.homeplayer.jni;

public class AudioUtilsNative {
  
  native static public int buildWaveform(String audioPath, int view_with, int view_height);
  native static public void cancelWaveform();
  
  public static short[] waveformData;
  
}
