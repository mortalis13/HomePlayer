package org.mortalis.homeplayernative.jni;

public class EngineNative {
  public static native int startEngine();
  public static native int stopEngine();
  public static native boolean isStreamClosed();
  public static native boolean isStreamRestarting();

  public static native int loadAudio(String audioPath);
  public static native int playAudio();
  public static native int pauseAudio();
  public static native int resumeAudio();
  public static native void setGain(float gain);
  
  public static native int getDuration();
  public static native int getCurrentPosition();
  public static native void seekTo(int time);
  
  public static native boolean isPlaying();
  public static native boolean isStopped();
  public static native void setRepeat(boolean repeat);
  
  public static native void enableFilter();
  public static native void disableFilter();
  public static native void setFilterFrequency(int band, float frequency);
  public static native void setFilterGain(int band, float gain);
  
  public static native int getChannels();
  public static native int getSampleRate();
  public static native String getSampleFormat();
  public static native int getBitrate();
  public static native String getCodecName();
}
