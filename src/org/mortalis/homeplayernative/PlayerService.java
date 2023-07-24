package org.mortalis.homeplayernative;

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

import org.mortalis.homeplayernative.actions.Action;
import org.mortalis.homeplayernative.actions.DoubleAction;
import org.mortalis.homeplayernative.actions.SimpleAction;

import org.mortalis.homeplayernative.jni.EngineNative;

import static org.mortalis.homeplayernative.Fun.log;
import static org.mortalis.homeplayernative.Fun.logd;
import static org.mortalis.homeplayernative.Fun.logw;
import static org.mortalis.homeplayernative.Fun.loge;

import java.io.File;


public class PlayerService extends Service implements AudioManager.OnAudioFocusChangeListener {

  public static final String ACTION_PLAY = "org.mortalis.homeplayernative.action.PLAY";
  public static final String ACTION_PAUSE = "org.mortalis.homeplayernative.action.PAUSE";
  public static final String ACTION_EXIT = "org.mortalis.homeplayernative.action.EXIT";

  public static final int ACTION_PLAY_ID = 0;
  public static final int ACTION_PAUSE_ID = 1;
  public static final int ACTION_EXIT_ID = 2;

  private final IBinder binder = new PlayerBinder();
  private final HeadphonesPlugReceiver headphonesPlugReceiver = new HeadphonesPlugReceiver();

  private AudioManager audioManager;
  private AudioFocusRequest focusRequest;

  private NotificationManagerCompat notificationManager;
  private NotificationCompat.Builder notifBuilder;
  private NotificationCompat.Action[] notifActions;
  private PlayerServiceReceiver playerServiceReceiver;

  private String audioPath;
  private int audioTime;
  private boolean startPlayback;
  private boolean repeat;

  private boolean updateTimeEnabled;
  private boolean playerLoaded;

  private int totalTime;

  private final Handler progressHandler = new Handler(Looper.getMainLooper());
  private Runnable progressRunnable;

  public SimpleAction exitAction = () -> {};
  public Action<Integer> progressSetupAction = (arg) -> {};
  public Action<Integer> progressUpdateAction = (arg) -> {};
  public DoubleAction<Integer> timeUpdateAction = (arg1, arg2) -> {};
  public SimpleAction onPlayerPreloadedAction = () -> {};
  public SimpleAction onPlayerStartedAction = () -> {};
  public SimpleAction onPlayerPausedAction = () -> {};
  public SimpleAction onPlayerResumedAction = () -> {};
  public SimpleAction onPlayerStoppedAction = () -> {};
  public SimpleAction onPlayerErrorAction = () -> {};
  public SimpleAction onHeadphonesUnplugAction = () -> {};
  public SimpleAction onHeadphonesPlugAction = () -> {};

  @Override
  public void onCreate() {
    logd("PlayerService.onCreate()");
    super.onCreate();
    init();
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    logd("PlayerService.onStartCommand()");

    if (intent == null) {
      loge("intent is null");
      return START_STICKY;
    }

    try {
      audioPath = intent.getStringExtra(Vars.EXTRA_AUDIO_PATH);
      audioTime = intent.getIntExtra(Vars.EXTRA_AUDIO_TIME, 0);  // ms
      startPlayback = intent.getBooleanExtra(Vars.EXTRA_START_PLAYBACK, true);
      repeat = intent.getBooleanExtra(Vars.EXTRA_PLAYBACK_REPEAT, true);

      progressHandler.removeCallbacks(progressRunnable);

      loadAudio(audioPath);
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
    try {
      unregisterReceiver(headphonesPlugReceiver);
    }
    catch (Exception e) {}
    
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
      public void onMsgExit() {
        progressHandler.removeCallbacks(progressRunnable);
        exitAction.execute();
      }
    });

    IntentFilter serviceFilter = new IntentFilter();
    serviceFilter.addAction(ACTION_PLAY);
    serviceFilter.addAction(ACTION_PAUSE);
    serviceFilter.addAction(ACTION_EXIT);
    registerReceiver(playerServiceReceiver, serviceFilter);
    
    registerHeadphonesReceiver();

    notificationManager = NotificationManagerCompat.from(this);

    PendingIntent playIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_PLAY), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    PendingIntent pauseIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_PAUSE), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    PendingIntent exitIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_EXIT), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

    notifActions = new NotificationCompat.Action[] {
      new NotificationCompat.Action(R.drawable.baseline_play_arrow_black_24, "Play", playIntent),
      new NotificationCompat.Action(R.drawable.baseline_pause_black_24, "Pause", pauseIntent),
      new NotificationCompat.Action(R.drawable.round_close_black_24, "Exit", exitIntent)
    };
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

    int result = EngineNative.resumeAudio();
    if (result != 0) {
      loge("Could not resume audio");
      return false;
    }

    if (!progressHandler.hasCallbacks(progressRunnable)) {
      enableUpdateTime();
      startProgress();
    }

    updateNotification(ACTION_PAUSE_ID);
    sendPlayerResumed();
    return true;
  }

  public void stop() {
    progressHandler.removeCallbacks(progressRunnable);
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
    EngineNative.seekTo(getPlayingTime() - s * 1000);
    sendUpdatePlayingTime();
    sendUpdateProgress();
  }

  public void fastForward(int s) {
    EngineNative.seekTo(getPlayingTime() + s * 1000);
    sendUpdatePlayingTime();
    sendUpdateProgress();
  }

  public void setRepeat(boolean repeat) {
    EngineNative.setRepeat(repeat);
  }


  private void loadAudio(String audioPath) {
    logd("loadAudio()");

    try {
      if (EngineNative.isStreamClosed() && !EngineNative.isStreamRestarting()) {
        log("Stream closed. Restarting");
        EngineNative.startEngine();
      }
      
      if (Fun.fileExists(audioPath)) {
        int result = EngineNative.loadAudio(audioPath);

        if (result != 0) {
          onLoadError();
        }
        else {
          setRepeat(this.repeat);
          totalTime = EngineNative.getDuration();

          if (audioTime > 0 && audioTime != getTotalTime()) {
            log("Initial seeking to time: " + audioTime);
            changePlayPosition(audioTime);
          }

          sendUpdatePlayingTime();
          sendInitProgress();
          sendUpdateProgress();
          playerLoaded = true;

          if (startPlayback) {
            enableUpdateTime();
            startProgress();
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
  
  private void startProgress() {
    logd("startProgress()");
    progressHandler.removeCallbacks(progressRunnable);

    progressRunnable = () -> {
      if (isStopped()) {
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

    int result = 0;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {  // 26
      result = audioManager.requestAudioFocus(focusRequest);
    }
    else {
      result = audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
    }

    log("Audio focus request result: " + result);
    return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
  }

  private void removeAudioFocus() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {  // 26
      audioManager.abandonAudioFocusRequest(focusRequest);
    }
    else {
      audioManager.abandonAudioFocus(this);
    }
  }

  private Notification buildPlayerNotification() {
    logd("buildPlayerNotification()");

    try {
      MediaMetadataRetriever metadata = new MediaMetadataRetriever();
      metadata.setDataSource(audioPath);
      String audioArtist = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
      metadata.release();

      String title = new File(audioPath).getName();
      String text = audioArtist;

      Intent intent = new Intent(this, MainActivity.class);
      PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

      notifBuilder = new NotificationCompat.Builder(this, Vars.NOTIFICATIONS_CHANNEL_ID);
      notifBuilder.setContentTitle(title);
      notifBuilder.setContentText(text);

      notifBuilder.setSmallIcon(R.drawable.round_audiotrack_black_24);
      notifBuilder.setOngoing(true);
      notifBuilder.setShowWhen(false);

      notifBuilder.setContentIntent(pendingIntent);

      int actionId = isPlaying() ? ACTION_PAUSE_ID: ACTION_PLAY_ID;
      notifBuilder.addAction(notifActions[actionId]);
      notifBuilder.addAction(notifActions[ACTION_EXIT_ID]);

      MediaStyle style = new MediaStyle();
      style.setShowActionsInCompactView(0, 1);
      notifBuilder.setStyle(style);

      return notifBuilder.build();
    }
    catch (Exception e) {
      e.printStackTrace();
    }

    return null;
  }

  private void updateNotification(int action) {
    Notification notification = null;
    if (notifBuilder == null) {
      notification = buildPlayerNotification();
    }
    else {
      notifBuilder.mActions.set(0, notifActions[action]);
      notification = notifBuilder.build();
    }

    if (notification == null) return;
    notificationManager.notify(Vars.NOTIFICATION_ID, notification);
  }
  
  
  // ----- External calls
  private void sendInitProgress() {
    progressSetupAction.execute(getTotalTime());
  }

  private void sendUpdateStoppedTime() {
    timeUpdateAction.execute(getTotalTime(), getTotalTime());
    progressUpdateAction.execute(getTotalTime());
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
  
  public void changePlayPosition(int time) {
    EngineNative.seekTo(time);
  }
  
  public void seekToEnd() {
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
    return EngineNative.isStopped();
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
  int getChannels() {
    if (!this.playerLoaded) return 0;
    return EngineNative.getChannels();
  }

  int getSampleRate() {
    if (!this.playerLoaded) return 0;
    return EngineNative.getSampleRate();
  }

  String getSampleFormat() {
    if (!this.playerLoaded) return null;
    return EngineNative.getSampleFormat();
  }

  int getBitrate() {
    if (!this.playerLoaded) return 0;
    return EngineNative.getBitrate();
  }

  String getCodecName() {
    if (!this.playerLoaded) return null;
    return EngineNative.getCodecName();
  }
  
  
  public void registerHeadphonesReceiver() {
    logd("registerHeadphonesReceiver()");
    IntentFilter headphonesFilter = new IntentFilter();
    headphonesFilter.addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
    headphonesFilter.addAction(AudioManager.ACTION_HEADSET_PLUG);
    registerReceiver(headphonesPlugReceiver, headphonesFilter);
  }
  
  public void unregisterHeadphonesReceiver() {
    logd("unregisterHeadphonesReceiver()");
    unregisterReceiver(headphonesPlugReceiver);
  }
  
  
  // --> AudioManager.OnAudioFocusChangeListener
  @Override
  public void onAudioFocusChange(int focusChange) {
    logd("onAudioFocusChange()");
    
    switch (focusChange) {
      case AudioManager.AUDIOFOCUS_GAIN:
        log("AUDIOFOCUS_GAIN");
        break;
      
      case AudioManager.AUDIOFOCUS_LOSS:
        log("AUDIOFOCUS_LOSS");
        pause();
        break;
      
      case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
        log("AUDIOFOCUS_LOSS_TRANSIENT");
        pause();
        break;
      
      case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
        log("AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK");
        pause();
        break;
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
