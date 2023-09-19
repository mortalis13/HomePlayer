package org.mortalis.homeplayer.jni;

import static org.mortalis.homeplayer.Fun.logd;


public class EngineNative {
  public static native void initEngine();
  public static native boolean startEngine();
  public static native void stopEngine();
  
  public static native boolean isStreamClosed();
  public static native boolean isStreamRestarting();
  public static native boolean isPlaying();
  public static native void setGain(float gain);

  public static native boolean loadAudio(String audioPath);
  public static native boolean bufferNextAudio(String audioPath);
  public static native boolean playAudio();
  public static native boolean pauseAudio();
  public static native boolean resumeAudio();
  
  public static native int getDuration();
  public static native int getCurrentPosition();
  public static native String getAudioPath();
  
  public static native void seekTo(int time);
  public static native void setRepeat(boolean repeat);
  public static native void setLoop(boolean loop);
  public static native void setLoopStart(int time);
  public static native void setLoopEnd(int time);
  
  public static native void enableFilter();
  public static native void disableFilter();
  public static native void setFilterFrequency(int band, float frequency);
  public static native void setFilterGain(int band, float gain);
  public static native void setFilterQ(float q);
  
  public static native int getChannels();
  public static native int getSampleRate();
  public static native String getSampleFormat();
  public static native int getBitrate();
  public static native String getCodecName();
  
  public static NativeChangeListener changeListener;
  
  public static void notifyAudioStopped() {
    logd("notifyAudioStopped()");
    if (changeListener != null) changeListener.onAudioStopped();
  }
  
  public interface NativeChangeListener {
    public void onAudioStopped();
  }
}
