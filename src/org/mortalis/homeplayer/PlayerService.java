package org.mortalis.homeplayer;

import java.util.List;
import java.util.Collections;
import java.util.Iterator;
import java.io.File;

import android.app.Service;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Binder;
import android.os.Build;
import android.os.PowerManager;
import android.util.Log;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;

import androidx.core.content.ContextCompat;
import androidx.core.app.NotificationCompat;
import androidx.media.app.NotificationCompat.MediaStyle;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;


public class PlayerService extends Service implements MediaPlayer.OnPreparedListener, MediaPlayer.OnErrorListener, MediaPlayer.OnCompletionListener, AudioManager.OnAudioFocusChangeListener {
  
  private final IBinder binder = new PlayerBinder();
  
  private MediaPlayer mediaPlayer;
  private MediaSessionCompat mediaSession;
  
  private AudioManager audioManager;
  private AudioAttributes playbackAttributes;
  private AudioFocusRequest focusRequest;
  private IntentFilter intentFilter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
  private HeadphonesUnpluggedReceiver headphonesUnpluggedReceiver = new HeadphonesUnpluggedReceiver();
  
  private MediaMetadataRetriever metadataRetriever;
  
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
    
    mediaSession = new MediaSessionCompat(this, Vars.APP_LOG_TAG);
    
    audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
    
    playbackAttributes = new AudioAttributes.Builder()
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .setUsage(AudioAttributes.USAGE_GAME)
    .build();
    
    focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(playbackAttributes)
        .setAcceptsDelayedFocusGain(true)
        .setWillPauseWhenDucked(true)
        .setOnAudioFocusChangeListener(this)
    .build();
    
    metadataRetriever = new MediaMetadataRetriever();
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
      mediaPlayer.setAudioAttributes(playbackAttributes);
      // mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
      // mediaPlayer.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
      // mediaPlayer.setWakeMode(getApplicationContext(), PowerManager.FULL_WAKE_LOCK);
      
      mediaPlayer.setOnPreparedListener(this);
      mediaPlayer.setOnCompletionListener(this);
      mediaPlayer.setOnErrorListener(this);
      
      progressHandler = new Handler();
      
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
    
    removeAudioFocus();
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
    
    boolean audioFocusGranted = requestAudioFocus();
    if (!audioFocusGranted) {
      Fun.loge("Audio focus is not granted");
      return;
    }
    
    registerReceiver(headphonesUnpluggedReceiver, intentFilter);
    
    mediaPlayer.start();
    Fun.logd("Playback started");
  }
  
  private void stop() {
    if (progressHandler != null) {
      progressHandler.removeCallbacks(progressRunnable);
    }
    
    sendUpdateStoppedTime();
    unregisterReceiver(headphonesUnpluggedReceiver);

    mediaPlayer.stop();
    mediaPlayer.reset();
    Fun.logd("Playback stopped");
  }
  
  public void pause() {
    mediaPlayer.pause();
    sendPlayerPaused();
    unregisterReceiver(headphonesUnpluggedReceiver);
    Fun.logd("Playback paused");
  }
  
  public void resume() {
    boolean audioFocusGranted = requestAudioFocus();
    if (!audioFocusGranted) {
      Fun.loge("Audio focus is not granted");
      return;
    }
    
    registerReceiver(headphonesUnpluggedReceiver, intentFilter);
    
    mediaPlayer.start();
    Fun.logd("Playback resumed");
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
  
  
  // ----- Utils
  private boolean requestAudioFocus() {
    if (audioManager == null) return false;
    
    int result = 0;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      result = audioManager.requestAudioFocus(focusRequest);
    }
    else {
      result = audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
    }
    
    Fun.log("Audio focus request: " + result);
    return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
  }

  private void removeAudioFocus() {
    audioManager.abandonAudioFocus(this);
  }
  
  private Notification buildPlayerNotification() {
    metadataRetriever.setDataSource(audioPath);
    String audioArtist = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
    
    String title = new File(audioPath).getName();
    String text = audioArtist;
    
    Intent intent = new Intent(this, MainActivity.class);
    PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    
    // ----------
    NotificationCompat.Builder builder = new NotificationCompat.Builder(this, Vars.NOTIFICATIONS_CHANNEL_ID);
    builder.setContentTitle(title);
    builder.setContentText(text);
    
    builder.setSmallIcon(R.drawable.round_audiotrack_black_24);
    builder.setOngoing(true);
    builder.setShowWhen(false);
    
    builder.setContentIntent(pendingIntent);
    
    return builder.build();
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
  
  private void sendPlayerPaused() {
    MainService.get().onPlayerPaused();
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
    
    startForeground(Vars.NOTIFICATION_ID, buildPlayerNotification());
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
  
  
  // --> AudioManager.OnAudioFocusChangeListener
  @Override
  public void onAudioFocusChange(int focusChange) {
    Fun.logd("onAudioFocusChange()");
    
    switch (focusChange) {
      case AudioManager.AUDIOFOCUS_GAIN:
        Fun.log("AUDIOFOCUS_GAIN");
        break;
      
      case AudioManager.AUDIOFOCUS_LOSS:
        Fun.log("AUDIOFOCUS_LOSS");
        pause();
        break;
      
      case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
        Fun.log("AUDIOFOCUS_LOSS_TRANSIENT");
        pause();
        break;
      
      case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
        Fun.log("AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK");
        pause();
        break;
    }
  }
  
  
  public class PlayerBinder extends Binder {
    PlayerService getService() {
      return PlayerService.this;
    }
  }
  
  private class HeadphonesUnpluggedReceiver extends BroadcastReceiver {
    public void onReceive(Context context, Intent intent) {
      if (intent.getAction().equals(AudioManager.ACTION_AUDIO_BECOMING_NOISY)) {
        Fun.log("Headphones unplugged");
        pause();
      }
    }
  }
  
}
