package org.mortalis.homeplayer;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.media.app.NotificationCompat.MediaStyle;
import androidx.core.content.ContextCompat;

import org.mortalis.homeplayer.actions.SingleAction;
import org.mortalis.homeplayer.actions.DoubleAction;
import org.mortalis.homeplayer.actions.Action;

import org.mortalis.homeplayer.jni.EngineNative;

import static org.mortalis.homeplayer.Fun.log;
import static org.mortalis.homeplayer.Fun.logd;
import static org.mortalis.homeplayer.Fun.logw;
import static org.mortalis.homeplayer.Fun.loge;

import java.io.File;
import java.io.IOException;


public class PlayerService extends Service implements AudioManager.OnAudioFocusChangeListener, EngineNative.NativeChangeListener {
  
  public static final String ACTION_PLAY = "org.mortalis.homeplayer.action.PLAY";
  public static final String ACTION_PAUSE = "org.mortalis.homeplayer.action.PAUSE";
  public static final String ACTION_NEXT = "org.mortalis.homeplayer.action.NEXT";
  public static final String ACTION_EXIT = "org.mortalis.homeplayer.action.EXIT";

  private static final int ACTION_PLAY_ID = 0;
  private static final int ACTION_PAUSE_ID = 1;
  private static final int ACTION_NEXT_ID = 2;
  private static final int ACTION_EXIT_ID = 3;

  private final IBinder binder = new PlayerBinder();
  private final HeadphonesPlugReceiver headphonesPlugReceiver = new HeadphonesPlugReceiver();

  private AudioManager audioManager;
  private AudioFocusRequest focusRequest;

  private NotificationManagerCompat notificationManager;
  private NotificationCompat.Builder notificationBuilder;
  private NotificationCompat.Action[] notificationActions;
  private PlayerServiceReceiver playerServiceReceiver;

  private String audioPath;
  private int audioTime;
  private boolean startPlayback;
  private boolean repeat;
  private boolean action_SyncAudioFile;

  private boolean updateTimeEnabled;
  private boolean playerLoaded;
  private boolean stopped;

  private int totalTime;

  private final Handler progressHandler = new Handler(Looper.getMainLooper());
  private Runnable progressRunnable;

  public Action exitAction = () -> {};
  public Action playNextAction = () -> {};
  public SingleAction<Integer> progressSetupAction = (arg) -> {};
  public SingleAction<Integer> progressUpdateAction = (arg) -> {};
  public DoubleAction<Integer> timeInitAction = (arg1, arg2) -> {};
  public DoubleAction<Integer> timeUpdateAction = (arg1, arg2) -> {};
  public Action onPlayerPreloadedAction = () -> {};
  public Action onPlayerStartedAction = () -> {};
  public Action onPlayerPausedAction = () -> {};
  public Action onPlayerResumedAction = () -> {};
  public Action onPlayerStoppedAction = () -> {};
  public Action onPlayerErrorAction = () -> {};
  public Action onHeadphonesUnplugAction = () -> {};
  public Action onHeadphonesPlugAction = () -> {};


  @Override
  public void onCreate() {
    logd("PlayerService.onCreate()");
    super.onCreate();
    init();
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    logd("PlayerService.onStartCommand()");
    stopped = false;

    if (intent == null) {
      loge("intent is null");
      return START_STICKY;
    }

    try {
      action_SyncAudioFile = intent.getBooleanExtra(Vars.EXTRA_SYNC_FILE, false);
      audioPath = intent.getStringExtra(Vars.EXTRA_AUDIO_PATH);

      stopProgress();

      if (action_SyncAudioFile) {
        syncAudioFile(audioPath);
      }
      else {
        audioTime = intent.getIntExtra(Vars.EXTRA_AUDIO_TIME, 0);  // ms
        startPlayback = intent.getBooleanExtra(Vars.EXTRA_START_PLAYBACK, true);
        repeat = intent.getBooleanExtra(Vars.EXTRA_PLAYBACK_REPEAT, false);
        
        loadAudio(audioPath);
      }
    }
    catch (Exception e) {
      e.printStackTrace();
    }

    return START_STICKY;
  }

  @Override
  public void onDestroy() {
    logd("PlayerService.onDestroy()");
    super.onDestroy();

    unregisterReceiver(playerServiceReceiver);
    unregisterReceiver(headphonesPlugReceiver);
    
    removeAudioFocus();
    EngineNative.stopEngine();
  }

  @Override
  public IBinder onBind(Intent intent) {
    logd("PlayerService.onBind()");
    return binder;
  }

  @Override
  public boolean onUnbind(Intent intent) {
    logd("PlayerService.onUnbind()");
    stopForeground(true);
    stopSelf();
    return super.onUnbind(intent);
  }

  @Override
  public void onRebind(Intent intent) {
    logd("PlayerService.onRebind()");
  }


  private void init() {
    audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

    AudioAttributes playbackAttributes = new AudioAttributes.Builder()
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .setUsage(AudioAttributes.USAGE_GAME)
    .build();

    focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(playbackAttributes)
        .setAcceptsDelayedFocusGain(true)
        .setWillPauseWhenDucked(true)
        .setOnAudioFocusChangeListener(this)
    .build();

    playerServiceReceiver = new PlayerServiceReceiver();
    playerServiceReceiver.setReceiverListener(new PlayerServiceReceiver.ReceiverListener() {
      public void onMsgPlay() {
        resume();
      }
      public void onMsgPause() {
        pause();
      }
      public void onMsgNext() {
        playNextAction.execute();
      }
      public void onMsgExit() {
        stopProgress();
        exitAction.execute();
      }
    });

    IntentFilter serviceFilter = new IntentFilter();
    serviceFilter.addAction(ACTION_PLAY);
    serviceFilter.addAction(ACTION_PAUSE);
    serviceFilter.addAction(ACTION_NEXT);
    serviceFilter.addAction(ACTION_EXIT);
    ContextCompat.registerReceiver(this, playerServiceReceiver, serviceFilter, ContextCompat.RECEIVER_NOT_EXPORTED);

    registerHeadphonesReceiver();

    notificationManager = NotificationManagerCompat.from(this);

    PendingIntent playIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_PLAY), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    PendingIntent pauseIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_PAUSE), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    PendingIntent nextIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_NEXT), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    PendingIntent exitIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_EXIT), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

    notificationActions = new NotificationCompat.Action[] {
      new NotificationCompat.Action(R.drawable.baseline_play_arrow_black_24, "Play", playIntent),
      new NotificationCompat.Action(R.drawable.baseline_pause_black_24, "Pause", pauseIntent),
      new NotificationCompat.Action(R.drawable.baseline_navigate_next_black_24, "Next", nextIntent),
      new NotificationCompat.Action(R.drawable.round_close_black_24, "Exit", exitIntent)
    };
    
    EngineNative.changeListener = this;
  }


  // ----------------------- Actions
  private void play() {
    boolean audioFocusGranted = requestAudioFocus();
    if (!audioFocusGranted) {
      loge("Audio focus is not granted");
      return;
    }

    EngineNative.playAudio();
    updateNotification(ACTION_PAUSE_ID);
  }

  public boolean resume() {
    boolean audioFocusGranted = requestAudioFocus();
    if (!audioFocusGranted) {
      loge("Audio focus is not granted");
      return false;
    }
    
    if (EngineNative.isStreamClosed() && !EngineNative.isStreamRestarting()) {
      log("Stream closed. Restarting");
      EngineNative.startEngine();
    }

    boolean result = EngineNative.resumeAudio();
    if (!result) {
      loge("Could not resume audio");
      return false;
    }

    if (!progressHandler.hasCallbacks(progressRunnable)) {
      startProgress();
      enableUpdateTime();
    }

    updateNotification(ACTION_PAUSE_ID);
    sendPlayerResumed();
    return true;
  }

  public void stop() {
    stopProgress();
    sendUpdateStoppedTime();

    EngineNative.stopEngine();
    updateNotification(ACTION_PLAY_ID);
  }

  public void pause() {
    logd("pause()");
    if (!this.isPlaying()) return;
    EngineNative.pauseAudio();
    sendPlayerPaused();
    updateNotification(ACTION_PLAY_ID);
  }

  public void fastRewind(int s) {
    seekTo(getPlayingTime() - s * 1000);
    sendUpdatePlayingTime();
    sendUpdateProgress();
  }

  public void fastForward(int s) {
    seekTo(getPlayingTime() + s * 1000);
    sendUpdatePlayingTime();
    sendUpdateProgress();
  }


  private void loadAudio(String audioPath) {
    logd("loadAudio() " + audioPath);
    
    try {
      if (EngineNative.isStreamClosed() && !EngineNative.isStreamRestarting()) {
        log("Stream closed. Restarting");
        EngineNative.startEngine();
      }
      
      if (Fun.fileExists(audioPath)) {
        boolean result = EngineNative.loadAudio(audioPath);

        if (!result) {
          onLoadError();
        }
        else {
          EngineNative.setRepeat(this.repeat);
          totalTime = EngineNative.getDuration();

          if (audioTime > 0 && audioTime != getTotalTime()) {
            log("Initial seeking to time: " + audioTime);
            seekTo(audioTime);
          }

          sendInitPlayingTime();
          sendInitProgress();
          sendUpdateProgress();
          playerLoaded = true;

          if (startPlayback) {
            startProgress();
            enableUpdateTime();
            play();
            sendPlayerStarted();
          }
          else {
            sendPlayerPreloaded();
          }
        }

        var notification = buildPlayerNotification();
        if (notification == null) return;
        startForeground(Vars.NOTIFICATION_ID, notification);
      }
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }
  
  private void syncAudioFile(String audioPath) {
    logd("syncAudioFile() " + audioPath);
    
    try {
      if (Fun.fileExists(audioPath)) {
        totalTime = EngineNative.getDuration();

        sendInitProgress();
        playerLoaded = true;

        startProgress();
        enableUpdateTime();
        sendPlayerStarted();

        var notification = buildPlayerNotification();
        if (notification == null) return;
        startForeground(Vars.NOTIFICATION_ID, notification);
      }
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }
  
  private void startProgress() {
    logd("startProgress()");
    stopProgress();

    progressRunnable = () -> {
      // Stopped on EOF
      if (isStopped()) {
        stopped = false;
        onCompleted();
        return;
      }
      if (updateTimeEnabled && isPlaying()) {
        sendUpdatePlayingTime();
        sendUpdateProgress();
      }
      progressHandler.postDelayed(progressRunnable, 100);
    };

    progressHandler.post(progressRunnable);
  }
  
  public void stopProgress() {
    logd("stopProgress()");
    disableUpdateTime();
    progressHandler.removeCallbacks(progressRunnable);
  }

  private void onCompleted() {
    logd("onCompleted()");
    sendUpdateStoppedTime();
    updateNotification(ACTION_PLAY_ID);
    
    playerLoaded = false;
    stopSelf();
    sendPlayerStopped();
  }

  private void onLoadError() {
    logd("onLoadError()");
    playerLoaded = false;
    updateNotification(ACTION_PLAY_ID);
    sendPlayerError();
  }


  // ----- Utils
  private boolean requestAudioFocus() {
    logd("requestAudioFocus()");
    if (audioManager == null) return false;

    int result;
    if (Build.VERSION.SDK_INT >= 26) {
      result = audioManager.requestAudioFocus(focusRequest);
    }
    else {
      result = audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
    }

    log("Audio focus request result: " + result);
    return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
  }

  private void removeAudioFocus() {
    if (Build.VERSION.SDK_INT >= 26) {
      audioManager.abandonAudioFocusRequest(focusRequest);
    }
    else {
      audioManager.abandonAudioFocus(this);
    }
  }

  private Notification buildPlayerNotification() {
    logd("buildPlayerNotification()");

    try {
      String title = new File(audioPath).getName();
      String text = getArtist(audioPath);

      Intent intent = new Intent(this, MainActivity.class);
      PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

      notificationBuilder = new NotificationCompat.Builder(this, Vars.NOTIFICATION_CHANNEL_ID);
      notificationBuilder.setContentTitle(title);
      notificationBuilder.setContentText(text);

      notificationBuilder.setSmallIcon(R.drawable.round_audiotrack_black_24);
      notificationBuilder.setOngoing(true);
      notificationBuilder.setShowWhen(false);

      notificationBuilder.setContentIntent(pendingIntent);

      int actionId = isPlaying() ? ACTION_PAUSE_ID: ACTION_PLAY_ID;
      notificationBuilder.addAction(notificationActions[actionId]);
      notificationBuilder.addAction(notificationActions[ACTION_NEXT_ID]);
      notificationBuilder.addAction(notificationActions[ACTION_EXIT_ID]);

      MediaStyle style = new MediaStyle();
      style.setShowActionsInCompactView(0, 1);
      notificationBuilder.setStyle(style);

      return notificationBuilder.build();
    }
    catch (Exception e) {
      e.printStackTrace();
    }

    return null;
  }

  private void updateNotification(int action) {
    Notification notification;
    if (notificationBuilder == null) {
      notification = buildPlayerNotification();
    }
    else {
      notificationBuilder.mActions.set(0, notificationActions[action]);
      notification = notificationBuilder.build();
    }

    if (notification == null) return;
    notificationManager.notify(Vars.NOTIFICATION_ID, notification);
  }
  
  private String getArtist(String audioPath) {
    String artist = "";
    
    var metadata = new MediaMetadataRetriever();
    try {
      metadata.setDataSource(audioPath);
      artist = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
    }
    catch (Exception e) {
      logw("Could not get artist for audio %s", audioPath);
    }
    finally {
      try {metadata.release();} catch (IOException e) {}
    }
    
    return artist;
  }
  
  
  // ----- External calls
  private void sendInitProgress() {
    progressSetupAction.execute(getTotalTime());
  }

  private void sendUpdateStoppedTime() {
    timeUpdateAction.execute(getTotalTime(), getTotalTime());
    progressUpdateAction.execute(getTotalTime());
  }
  
  private void sendInitPlayingTime() {
    timeInitAction.execute(getPlayingTime(), getTotalTime());
  }
  
  private void sendUpdatePlayingTime() {
    timeUpdateAction.execute(getPlayingTime(), getTotalTime());
  }
  
  private void sendUpdateProgress() {
    progressUpdateAction.execute(getPlayingTime());
  }
  
  private void sendPlayerPreloaded() {
    onPlayerPreloadedAction.execute();
  }
  
  private void sendPlayerStarted() {
    onPlayerStartedAction.execute();
  }
  
  private void sendPlayerPaused() {
    onPlayerPausedAction.execute();
  }
  
  private void sendPlayerResumed() {
    onPlayerResumedAction.execute();
  }
  
  private void sendPlayerStopped() {
    onPlayerStoppedAction.execute();
  }
  
  private void sendPlayerError() {
    onPlayerErrorAction.execute();
  }
  
  
  public void enableUpdateTime() {
    updateTimeEnabled = true;
  }
  
  public void disableUpdateTime() {
    updateTimeEnabled = false;
  }
  
  public void changePlayPosition(int time) {  // ms
    logd("changePlayPosition() " + time);
    seekTo(time);
    sendUpdatePlayingTime();
    sendUpdateProgress();
  }
  
  public void seekTo(int time) {  // ms
    logd("seekTo() " + time);
    EngineNative.seekTo(time);
  }
  
  public void seekToEnd() {
    logd("seekToEnd() " + totalTime);
    EngineNative.seekTo(totalTime);
  }
  
  public int getTotalTime() {  // ms
    return totalTime;
  }
  
  public int getPlayingTime() {  // ms
    return EngineNative.getCurrentPosition();
  }
  
  public boolean isPlaying() {
    return EngineNative.isPlaying();
  }
  
  public boolean isStopped() {
    return stopped;
  }
  
  public boolean hasAudio() {
    return audioPath != null;
  }
  
  public boolean isPlayerLoaded() {
    return playerLoaded;
  }
  
  public boolean hasProgress() {
    return EngineNative.getCurrentPosition() != 0;
  }
  
  public String getAudioPath() {
    return audioPath;
  }
  
  public void resetService() {
    log("resetService()");
    playerLoaded = false;
  }
  
  
  // ----- Audio params
  String getSampleFormat() {
    if (!this.playerLoaded) return null;
    return EngineNative.getSampleFormat();
  }

  String getCodecName() {
    if (!this.playerLoaded) return null;
    return EngineNative.getCodecName();
  }
  
  int getBitrate() {
    if (!this.hasAudio()) return 0;
    int result = 0;
    
    var metadata = new MediaMetadataRetriever();
    try {
      metadata.setDataSource(this.audioPath);
      String bitrate = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE);
      result = bitrate != null ? Integer.parseInt(bitrate): 0;
    }
    catch (Exception e) {
      logw("Could not get audio metadata for: %s => %s", this.audioPath, e);
    }
    finally {
      try {metadata.release();} catch (IOException e) {}
    }
    
    return result;
  }
  
  
  public void registerHeadphonesReceiver() {
    logd("registerHeadphonesReceiver()");
    IntentFilter headphonesFilter = new IntentFilter();
    headphonesFilter.addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
    headphonesFilter.addAction(AudioManager.ACTION_HEADSET_PLUG);
    ContextCompat.registerReceiver(this, headphonesPlugReceiver, headphonesFilter, ContextCompat.RECEIVER_NOT_EXPORTED);
  }
  

  // --> EngineNative.NativeChangeListener
  @Override
  public void onAudioStopped() {
    stopped = true;
  }
  
  
  // --> AudioManager.OnAudioFocusChangeListener
  @Override
  public void onAudioFocusChange(int focusChange) {
    logd("onAudioFocusChange()");
    
    switch (focusChange) {
      case AudioManager.AUDIOFOCUS_GAIN -> log("AUDIOFOCUS_GAIN");
      case AudioManager.AUDIOFOCUS_LOSS -> {log("AUDIOFOCUS_LOSS"); pause();}
      case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {log("AUDIOFOCUS_LOSS_TRANSIENT"); pause();}
      case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {log("AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK"); pause();}
    }
  }
  
  
  public class PlayerBinder extends Binder {
    PlayerService getService() {
      return PlayerService.this;
    }
  }
  
  private class HeadphonesPlugReceiver extends BroadcastReceiver {
    
    public void onReceive(Context context, Intent intent) {
      String action = intent.getAction();
      if (action == null) return;
      
      if (action.equals(AudioManager.ACTION_AUDIO_BECOMING_NOISY)) {
        log("Headphones unplugged [noisy]");
        pause();
        try {Thread.sleep(100);} catch (Exception e) {}
        EngineNative.stopEngine();
      }
      
      else if (action.equals(AudioManager.ACTION_HEADSET_PLUG)) {
        if (isInitialStickyBroadcast()) return;
        int state = intent.getIntExtra("state", 0);
        
        if (state == 0) {
          log("Headphones unplugged");
          onHeadphonesUnplugAction.execute();
        }
        
        else if (state == 1) {
          log("Headphones plugged");
          pause();
          if (EngineNative.isStreamClosed() && !EngineNative.isStreamRestarting()) {
            logw("Stream closed. Restarting");
            EngineNative.startEngine();
          }
          onHeadphonesPlugAction.execute();
        }
      }
    }
    
  }
  
}
