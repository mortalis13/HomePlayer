package org.mortalis.homeplayer;

import java.util.List;
import java.util.Collections;
import java.util.Iterator;
import java.io.File;

import android.os.Handler;
import android.app.Service;
import android.app.Notification;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.IBinder;
import android.os.Binder;
import android.util.Log;
import android.os.PowerManager;


public class PlayerService extends Service implements MediaPlayer.OnPreparedListener, MediaPlayer.OnErrorListener, MediaPlayer.OnCompletionListener {
  
  private final IBinder binder = new PlayerBinder();
  
  private MediaPlayer mediaPlayer;
  
  private String audioPath;
  private int audioTime;
  private boolean updateTimeEnabled;
  private boolean playerLoaded;
  private boolean startPlayback;
  
  private Handler progressHandler;
  private Runnable progressRunnable;
  
  @Override
  public void onCreate() {
    Fun.logd("PlayerService.onCreate()");
    super.onCreate();
  }
  
  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    Fun.logd("PlayerService.onStartCommand()");
    
    try {
      audioPath = intent.getStringExtra(Vars.EXTRA_AUDIO_PATH);
      audioTime = intent.getIntExtra(Vars.EXTRA_AUDIO_TIME, 0);
      startPlayback = intent.getBooleanExtra(Vars.EXTRA_START_PLAYBACK, true);
      
      if (mediaPlayer != null) mediaPlayer.release();
      mediaPlayer = new MediaPlayer();
      mediaPlayer.setAudioAttributes(
        new AudioAttributes.Builder()
          .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
          .setUsage(AudioAttributes.USAGE_MEDIA)
          .build()
      );
      
      // mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
      // mediaPlayer.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
      // mediaPlayer.setWakeMode(getApplicationContext(), PowerManager.FULL_WAKE_LOCK);
      
      mediaPlayer.setOnPreparedListener(this);
      mediaPlayer.setOnCompletionListener(this);
      mediaPlayer.setOnErrorListener(this);
      
      progressHandler = new Handler();
      
      Fun.log("PS: audioPath: " + audioPath);
      Fun.log("PS: audioTime: " + audioTime);
      Fun.log("PS: startPlayback: " + startPlayback);
      startAudio(audioPath);
      playerLoaded = true;
    }
    catch (Exception e) {
      e.printStackTrace();
    }
    
    return START_STICKY;
  }
  
  @Override
  public void onDestroy() {
    Fun.logd("PlayerService.onDestroy()");
    super.onDestroy();
    
    if (mediaPlayer != null) mediaPlayer.release();
  }
  
  @Override
  public IBinder onBind(Intent intent) {
    Fun.logd("PlayerService.onBind()");
    return binder;
  }
  
  @Override
  public boolean onUnbind(Intent intent) {
    Fun.logd("PlayerService.onUnbind()");
    stopForeground(true);
    return super.onUnbind(intent);
  }
  
  @Override
  public void onRebind(Intent intent) {
    Fun.logd("PlayerService.onRebind()");
  }
  
  
  private void preload() {
    sendUpdatePlayingTime();
    sendInitProgress();
    sendUpdateProgress();
    
    enableUpdateTime();
    startProgress();
  }
  
  private void play() {
    preload();
    mediaPlayer.start();
    Fun.logd("Playback started");
  }
  
  private void stop() {
    if (progressHandler != null) {
      progressHandler.removeCallbacks(progressRunnable);
    }
    
    sendUpdateStoppedTime();
    
    mediaPlayer.stop();
    mediaPlayer.reset();
    Fun.logd("Playback stopped");
  }
  
  public void pause() {
    mediaPlayer.pause();
  }
  
  public void resume() {
    mediaPlayer.start();
  }
  
  public void restartAudio() {
    startAudio(audioPath);
  }
  
  private void startAudio(String audioPath) {
    Fun.logd("startAudio()");
    try {
      if (audioPath != null && Fun.fileExists(audioPath)) {
        mediaPlayer.setDataSource(audioPath);
        mediaPlayer.prepareAsync();
      }
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }
  
  private void startProgress() {
    if (progressHandler != null) {
      progressHandler.removeCallbacks(progressRunnable);
    }
    
    progressRunnable = new Runnable() {
      public void run() {
        if (updateTimeEnabled && mediaPlayer.isPlaying()) {
          sendUpdatePlayingTime();
          sendUpdateProgress();
        }
        progressHandler.postDelayed(progressRunnable, 100);
      }
    };
    
    progressHandler.postDelayed(progressRunnable, 100);
  }
  
  
  // ----- External calls
  private void sendInitProgress() {
    MainService.get().initProgress(mediaPlayer.getDuration());
  }

  private void sendUpdateStoppedTime() {
    MainService.get().updatePlayingTime(mediaPlayer.getDuration(), mediaPlayer.getDuration());
    MainService.get().updateProgress(mediaPlayer.getDuration());
  }
  
  private void sendUpdatePlayingTime() {
    MainService.get().updatePlayingTime(mediaPlayer.getCurrentPosition(), mediaPlayer.getDuration());
  }
  
  private void sendUpdateProgress() {
    MainService.get().updateProgress(mediaPlayer.getCurrentPosition());
  }
  
  private void sendPlayerPreloaded() {
    MainService.get().onPlayerPreloaded();
  }
  
  private void sendPlayerStarted() {
    MainService.get().onPlayerStarted();
  }
  
  private void sendPlayerStopped() {
    MainService.get().onPlayerStopped();
  }
  
  
  public void enableUpdateTime() {
    updateTimeEnabled = true;
  }
  
  public void disableUpdateTime() {
    updateTimeEnabled = false;
  }
  
  public void changePlayPosition(int time) {
    mediaPlayer.seekTo(time);
  }
  
  public int getTotalTime() {
    if (mediaPlayer == null) return -1;
    return mediaPlayer.getDuration();
  }
  
  public boolean isPlaying() {
    return mediaPlayer != null && mediaPlayer.isPlaying();
  }
  
  public boolean hasAudio() {
    return audioPath != null;
  }
  
  public boolean isPlayerLoaded() {
    return playerLoaded;
  }
  
  public String getAudioPath() {
    return audioPath;
  }
  
  
  // --> MediaPlayer.OnPreparedListener
  @Override
  public void onPrepared(MediaPlayer player) {
    Fun.logd("MediaPlayer.onPrepared()");
    
    if (audioTime > 0) {
      Fun.log("Seeking to time: " + audioTime);
      changePlayPosition(audioTime);
    }
    
    if (startPlayback) {
      play();
      sendPlayerStarted();
    }
    else {
      preload();
      sendPlayerPreloaded();
    }
    
    String title = new File(audioPath).getName();
    String text = "text";
    startForeground(Vars.NOTIFICATION_ID, Fun.buildNotification(this, title, text));
  }
  
  // --> MediaPlayer.OnCompletionListener
  @Override
  public void onCompletion(MediaPlayer player) {
    Fun.logd("MediaPlayer.onCompletion()");
    stop();
    playerLoaded = false;
    stopSelf();
    sendPlayerStopped();
  }
  
  // --> MediaPlayer.OnErrorListener
  @Override
  public boolean onError(MediaPlayer player, int what, int extra) {
    Fun.logd("MediaPlayer.onError()");
    Fun.loge("MediaPlayer Error: " + what + "; " + extra);
    return true;
  }
  
  
  public class PlayerBinder extends Binder {
    PlayerService getService() {
      return PlayerService.this;
    }
  }
  
}
