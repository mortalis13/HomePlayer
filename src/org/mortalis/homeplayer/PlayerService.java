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
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.media.app.NotificationCompat.MediaStyle;

import org.mortalis.homeplayer.actions.Action;
import org.mortalis.homeplayer.actions.DoubleAction;
import org.mortalis.homeplayer.actions.SimpleAction;

import static org.mortalis.homeplayer.Fun.log;
import static org.mortalis.homeplayer.Fun.logd;
import static org.mortalis.homeplayer.Fun.loge;

import java.io.File;


public class PlayerService extends Service implements MediaPlayer.OnPreparedListener, MediaPlayer.OnErrorListener, MediaPlayer.OnCompletionListener, AudioManager.OnAudioFocusChangeListener {
  
  public static final String ACTION_PLAY = "org.mortalis.homeplayer.action.PLAY";
  public static final String ACTION_PAUSE = "org.mortalis.homeplayer.action.PAUSE";
  public static final String ACTION_EXIT = "org.mortalis.homeplayer.action.EXIT";
  
  public static final int ACTION_PLAY_ID = 0;
  public static final int ACTION_PAUSE_ID = 1;
  public static final int ACTION_EXIT_ID = 2;
  
  private final IBinder binder = new PlayerBinder();
  
  private MediaPlayer mediaPlayer;
  
  private AudioManager audioManager;
  private AudioAttributes playbackAttributes;
  private AudioFocusRequest focusRequest;
  private HeadphonesPlugReceiver headphonesPlugReceiver = new HeadphonesPlugReceiver();
  
  private NotificationManagerCompat notificationManager;
  private NotificationCompat.Builder notifBuilder;
  private NotificationCompat.Action[] notifActions;
  private PlayerServiceReceiver playerServiceReceiver;
  
  private MediaMetadataRetriever metadata;
  
  private String audioPath;
  private int audioTime;
  private boolean updateTimeEnabled;
  private boolean playerLoaded;
  private boolean startPlayback;
  
  private Handler progressHandler = new Handler();
  private Runnable progressRunnable;
  
  public SimpleAction exitAction;
  public Action<Integer> progressSetupAction;
  public Action<Integer> progressUpdateAction;
  public DoubleAction<Integer> timeUpdateAction;
  public SimpleAction onPlayerPreloadedAction;
  public SimpleAction onPlayerStartedAction;
  public SimpleAction onPlayerPausedAction;
  public SimpleAction onPlayerResumedAction;
  public SimpleAction onPlayerStoppedAction;
  public Action<Integer> onHeadphonesPlugAction = (state) -> {};
  
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
      audioTime = intent.getIntExtra(Vars.EXTRA_AUDIO_TIME, 0);
      startPlayback = intent.getBooleanExtra(Vars.EXTRA_START_PLAYBACK, true);
      boolean repeat = intent.getBooleanExtra(Vars.EXTRA_PLAYBACK_REPEAT, true);

      if (mediaPlayer != null) mediaPlayer.release();
      mediaPlayer = new MediaPlayer();
      mediaPlayer.setAudioAttributes(playbackAttributes);
      this.setRepeat(repeat);
      
      mediaPlayer.setOnPreparedListener(this);
      mediaPlayer.setOnCompletionListener(this);
      mediaPlayer.setOnErrorListener(this);
      
      progressHandler.removeCallbacks(progressRunnable);
      
      startAudio(audioPath);
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
    if (mediaPlayer != null) mediaPlayer.release();
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
    
    metadata = new MediaMetadataRetriever();
    
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
        sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
      }
    });
    
    IntentFilter serviceFilter = new IntentFilter();
    serviceFilter.addAction(ACTION_PLAY);
    serviceFilter.addAction(ACTION_PAUSE);
    serviceFilter.addAction(ACTION_EXIT);
    registerReceiver(playerServiceReceiver, serviceFilter);
    
    IntentFilter headphonesFilter = new IntentFilter();
    headphonesFilter.addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
    headphonesFilter.addAction(AudioManager.ACTION_HEADSET_PLUG);
    registerReceiver(headphonesPlugReceiver, headphonesFilter);
    
    notificationManager = NotificationManagerCompat.from(this);
    
    PendingIntent playIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_PLAY), PendingIntent.FLAG_UPDATE_CURRENT);
    PendingIntent pauseIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_PAUSE), PendingIntent.FLAG_UPDATE_CURRENT);
    PendingIntent exitIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_EXIT), PendingIntent.FLAG_UPDATE_CURRENT);
    
    notifActions = new NotificationCompat.Action[] {
      new NotificationCompat.Action(R.drawable.baseline_play_arrow_black_24, "Play", playIntent),
      new NotificationCompat.Action(R.drawable.baseline_pause_black_24, "Pause", pauseIntent),
      new NotificationCompat.Action(R.drawable.round_close_black_24, "Exit", exitIntent)
    };
  }


  // ----------------------- Actions
  private void preload() {
    sendUpdatePlayingTime();
    sendInitProgress();
    sendUpdateProgress();
  }
  
  private void play() {
    boolean audioFocusGranted = requestAudioFocus();
    if (!audioFocusGranted) {
      loge("Audio focus is not granted");
      return;
    }
    
    mediaPlayer.start();
    updateNotification(ACTION_PAUSE_ID);
    log("Playback started");
  }
  
  public void resume() {
    if (!progressHandler.hasCallbacks(progressRunnable)) {
      enableUpdateTime();
      startProgress();
    }
    
    play();
    sendPlayerResumed();
  }
  
  private void stop() {
    progressHandler.removeCallbacks(progressRunnable);
    sendUpdateStoppedTime();

    mediaPlayer.stop();
    mediaPlayer.reset();
    updateNotification(ACTION_PLAY_ID);
    log("Playback stopped");
  }
  
  public void pause() {
    mediaPlayer.pause();
    sendPlayerPaused();
    updateNotification(ACTION_PLAY_ID);
    log("Playback paused");
  }
  
  public void fastRewind(int s) {
    mediaPlayer.seekTo(mediaPlayer.getCurrentPosition() - s * 1000);
    sendUpdatePlayingTime();
    sendUpdateProgress();
  }
  
  public void fastForward(int s) {
    mediaPlayer.seekTo(mediaPlayer.getCurrentPosition() + s * 1000);
    sendUpdatePlayingTime();
    sendUpdateProgress();
  }
  
  public void setRepeat(boolean repeat) {
    mediaPlayer.setLooping(repeat);
  }
  
  
  public void restartAudio() {
    startAudio(audioPath);
  }
  
  private void startAudio(String audioPath) {
    logd("startAudio()");
    
    try {
      if (Fun.fileExists(audioPath)) {
        mediaPlayer.setDataSource(audioPath);
        mediaPlayer.prepareAsync();
        playerLoaded = true;
      }
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }
  
  private void startProgress() {
    logd("startProgress()");
    progressHandler.removeCallbacks(progressRunnable);
    
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
    
    log("Audio focus request: " + result);
    return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
  }

  private void removeAudioFocus() {
    audioManager.abandonAudioFocus(this);
  }
  
  private Notification buildPlayerNotification() {
    logd("buildPlayerNotification()");
    
    String audioArtist = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
    
    String title = new File(audioPath).getName();
    String text = audioArtist;
    
    Intent intent = new Intent(this, MainActivity.class);
    PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    
    // ----------
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
  
  private void updateNotification(int action) {
    Notification notification = null;
    if (notifBuilder == null) {
      notification = buildPlayerNotification();
    }
    else {
      notifBuilder.mActions.set(0, notifActions[action]);
      notification = notifBuilder.build();
    }
    
    notificationManager.notify(Vars.NOTIFICATION_ID, notification);
  }
  
  
  // ----- External calls
  private void sendInitProgress() {
    progressSetupAction.execute(mediaPlayer.getDuration());
  }

  private void sendUpdateStoppedTime() {
    timeUpdateAction.execute(mediaPlayer.getDuration(), mediaPlayer.getDuration());
    progressUpdateAction.execute(mediaPlayer.getDuration());
  }
  
  private void sendUpdatePlayingTime() {
    timeUpdateAction.execute(mediaPlayer.getCurrentPosition(), mediaPlayer.getDuration());
  }
  
  private void sendUpdateProgress() {
    progressUpdateAction.execute(mediaPlayer.getCurrentPosition());
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
    logd("MediaPlayer.onPrepared()");
    
    if (audioTime > 0) {
      log("Seeking to time: " + audioTime);
      changePlayPosition(audioTime);
    }
    
    metadata.setDataSource(audioPath);
    
    if (startPlayback) {
      preload();
      enableUpdateTime();
      startProgress();
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
    logd("MediaPlayer.onCompletion()");
    stop();
    playerLoaded = false;
    stopSelf();
    sendPlayerStopped();
  }
  
  
  // --> MediaPlayer.OnErrorListener
  @Override
  public boolean onError(MediaPlayer player, int what, int extra) {
    logd("MediaPlayer.onError(): " + what + "; " + extra);
    return true;
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
        log("Headphones unplugged");
        pause();
      }
      else if (action.equals(AudioManager.ACTION_HEADSET_PLUG)) {
        int state = intent.getIntExtra("state", 0);
        if (state == 1) {
          log("Headphones plugged");
        }
        onHeadphonesPlugAction.execute(state);
      }
    }
  }
  
}
