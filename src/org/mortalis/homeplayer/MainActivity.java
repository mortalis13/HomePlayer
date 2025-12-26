package org.mortalis.homeplayer;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcelable;
import android.provider.Settings;
import android.view.MotionEvent;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.view.WindowManager;
import android.widget.HorizontalScrollView;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.app.PendingIntent;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.os.HandlerCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.MaterialColors;

import org.mortalis.homeplayer.actions.Action;

import org.mortalis.homeplayer.components.ProgressSliderView;
import org.mortalis.homeplayer.components.VolumeSliderView;
import org.mortalis.homeplayer.components.TrimSliderView;
import org.mortalis.homeplayer.components.EqualizerView;
import org.mortalis.homeplayer.components.RangeSliderView;

import org.mortalis.homeplayer.events.OnSwipeTouchListener;
import org.mortalis.homeplayer.events.RecyclerTouchListener;

import org.mortalis.homeplayer.jni.AudioUtilsNative;
import org.mortalis.homeplayer.jni.EngineNative;

import org.mortalis.homeplayer.models.AudioInfo;
import org.mortalis.homeplayer.models.ListItem;
import org.mortalis.homeplayer.models.Track;
import org.mortalis.homeplayer.models.CueTrack;

import static org.mortalis.homeplayer.Fun.log;
import static org.mortalis.homeplayer.Fun.logd;
import static org.mortalis.homeplayer.Fun.loge;
import static org.mortalis.homeplayer.Fun.logw;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.Stack;
import java.util.HashSet;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.FieldKey;


public class MainActivity extends AppCompatActivity {

  private static final String ROOT_DIR_TITLE = "storage";
  
  // Define constants from AudioManager available in the source but not in the SDK
  private static final String VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION";
  private static final String STREAM_DEVICES_CHANGED_ACTION = "android.media.STREAM_DEVICES_CHANGED_ACTION";
  private static final String EXTRA_VOLUME_STREAM_TYPE = "android.media.EXTRA_VOLUME_STREAM_TYPE";
  
  private static final int MAX_FILES_FOR_SCANNING = 50;
  
  private static final File ROOT_STORAGE = Environment.getExternalStorageDirectory();
  
  private Context context;
  
  private PlayerService playerService;
  private ServiceConnection serviceConnection;
  private boolean serviceBound;
  
  private FilesAdapter filesAdapter;
  private LinearLayoutManager listLayoutManager;
  private List<ListItem> fileList;
  private File currentPath;
  
  private List<Track> playingList = new ArrayList<>();
  private Track currentTrack;
  
  private String lastFolder;
  private String lastAudio;
  private int lastAudioTime;
  private Set<String> favoritesList;
  
  private boolean playbackRepeat;
  private boolean nextFilePreloaded;
  private boolean nextPreloadingFailed;
  private boolean updateTimeEnabled;
  
  private boolean playbackShuffle;
  private List<Track> shuffleList = new ArrayList<>();
  private final Random randShuffle = new Random();
  
  private AudioInfo currentExtraInfo;
  
  private AudioManager audioManager;
  private final VolumeReceiver volumeReceiver = new VolumeReceiver();
  
  private final Object lock = new Object();
  private String currentWaveformFile;
  
  private boolean audioTrimEnabled;
  private int audioTrimSeconds;
  private int trimmedProgressColor;
  
  private boolean loopEnabled;
  private int loopOffsetStep;
  
  private boolean extraInfoIsForCurrentFile;
  
  private Set<String> repeatableFiles;
  
  private Stack<Parcelable> scrollStack;
  
  private final ExecutorService taskExecutor = Executors.newFixedThreadPool(8);
  private final Handler taskHandler = HandlerCompat.createAsync(Looper.getMainLooper());

  private final LoadDirectoryTimeProcess<ListItem> loadDirectoryTimeTask = new LoadDirectoryTimeProcess<>(taskExecutor, taskHandler);
  private final LoadDirectoryTimeProcess<String> loadPlaylistTimeTask = new LoadDirectoryTimeProcess<>(taskExecutor, taskHandler);

  // -- Views
  private HorizontalScrollView titleScroller;
  private TextView activeTitle;
  
  private ProgressSliderView progressSlider;
  
  private RelativeLayout contentContainer;
  private RecyclerView itemsListView;
  
  private TextView textTimeLeft;
  private TextView textTimePlaying;
  private TextView textTimeTotal;
  
  private TextView textFileExtraData;
  private TextView textPlayingPosition;
  private TextView textPlayingFolderTime;
  
  private ImageButton bPrevFile;
  private ImageButton bPlayPause;
  private ImageButton bNextFile;
  private ImageButton bFastRewind;
  private ImageButton bFastForward;
  private ImageView playExtraIconShuffle;
  private ImageView playExtraIconRepeat;
  private ImageView playExtraIconLoop;
  private ImageView playExtraIconTrim;
  private ImageView playExtraIconEQ;
  
  private LinearLayout panelInfoLeft;
  private LinearLayout panelInfoCenter;
  private LinearLayout panelInfoRight;
  
  private LinearLayout extraControlPanel;
  private ImageButton bShuffle;
  private ImageButton bTrimAudio;
  private ImageButton bLoopSetup;
  private ImageButton bEqualizer;
  private ImageButton bRepeat;
  
  private RelativeLayout trimAudioPanel;
  private TrimSliderView trimAudioSlider;
  private TextView textTrimValue;
  private TextView textTrimMax;
  
  private LinearLayout looperPanel;
  private RangeSliderView loopSlider;
  private TextView textLoopStartTime;
  private TextView textLoopLength;
  private TextView textLoopEndTime;
  
  private ImageButton bLooperEnable;
  private ImageButton bSeekLoopStart;
  private ImageButton bSeekLoopEnd;
  private ImageButton bLooperReset;
  
  private ImageButton bLoopStartMinus;
  private ImageButton bLoopStartPlus;
  private Button bLoopCycleOffsetStep;
  private ImageButton bLoopEndMinus;
  private ImageButton bLoopEndPlus;
  
  private LinearLayout equalizerPanel;
  private EqualizerView equalizerView;
  
  private LinearLayout extraInfoPanel;
  private ScrollView mainScroll;
  private LinearLayout mainInfo;
  private LinearLayout imageInfo;
  private ScrollView lyricsScroll;
  private LinearLayout lyricsInfo;
  
  private TextView textExtraFileName;
  private TextView textExtraTitle;
  private TextView textExtraArtist;
  private TextView textExtraAlbum;
  private TextView textExtraYear;
  private TextView textExtraLength;
  private TextView textExtraBitrate;
  private TextView textExtraFrequency;
  private TextView textExtraChannels;
  private LinearLayout extraCodecBlock;
  private TextView textExtraCodec;
  private LinearLayout extraSampleFormatBlock;
  private TextView textExtraSampleFormat;
  private TextView textExtraSize;
  private TextView textExtraPath;
  
  private TextView textImageFileName;
  private TextView textImageInfo;
  private ImageView audioImage;
  
  private TextView textLyricsFileName;
  private TextView textExtraLyrics;
  private TextView textLyricsPlaceholder;
  
  private TextView textTotalFiles;
  private TextView textTotalSize;
  private TextView textTotalTime;
  private TextView textVolumeLevel;
  
  private LinearLayout totalFavoritesBlock;
  private TextView textTotalFavorites;
  
  private LinearLayout statusPanel;
  private VolumeSliderView volumeSlider;
  
  static {
    Fun.log("Loading native library");
    System.loadLibrary("audio");
  }
  
  
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    logd("MainActivity.onCreate()");
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    
    if (Vars.KEEP_SCREEN_ON) {
      logd("Enabling flag to keep screen on");
      getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    
    context = this;
    
    requestAppPermissions(context);
    Fun.createNotificationChannel(context);
    
    init();
    configUI();
    restoreState();
    initEngine();
  }
  
  @Override
  protected void onStart() {
    logd("MainActivity.onStart()");
    super.onStart();
  }
  
  @Override
  protected void onResume() {
    logd("MainActivity.onResume()");
    super.onResume();
    bindPlayerService();
    if (serviceBound && !playerService.isPlaying() && !playerService.hasProgress()) {
      playerService.resetService();
    }
    validateCurrentDir();
    validateActiveWaveform();
  }
  
  @Override
  protected void onPause() {
    logd("MainActivity.onPause()");
    super.onPause();
  }
  
  @Override
  protected void onDestroy() {
    logd("MainActivity.onDestroy()");
    super.onDestroy();

    if (serviceBound) {
      unbindService(serviceConnection);
    }
    if (playerService != null) {
      Intent playerIntent = new Intent(this, PlayerService.class);
      stopService(playerIntent);
    }
    
    unregisterReceiver(volumeReceiver);
  }
  
  @Override
  public void onBackPressed() {
    if (extraPanelsVisible()) {
      hideExtraPanels();
    }
    else {
      changeToParentDir();
    }
  }
  
  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
      int volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
      audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume - 1, 0);
      return true;
    }
    
    if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
      int volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
      audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume + 1, 0);
      return true;
    }
    
    return super.onKeyDown(keyCode, event);
  }
  
  @Override
  protected void onNewIntent(Intent intent) {
    String action = intent.getAction();
    logd("onNewIntent() " + action);
    if (action != null && action.equals(Intent.ACTION_VOICE_COMMAND)) {
      playNextFileAction();
    }
  }
  
  
  // -----------------------------------------------------------
  private void bindPlayerService() {
    if (serviceBound) return;
    log("Binding PlayerService");
    Intent intent = new Intent(this, PlayerService.class);
    bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
  }
  
  private void requestAppPermissions(Context context) {
    logd("requestAppPermissions()");
    if (Build.VERSION.SDK_INT < 30) {
      String permission = Manifest.permission.WRITE_EXTERNAL_STORAGE;
      boolean isGranted = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED;
      if (!isGranted) {
        requestPermissions(new String[] {permission}, Vars.APP_PERMISSION_REQUEST_ACCESS_EXTERNAL_STORAGE);
      }
    }
    else if (!Environment.isExternalStorageManager()) {
      Uri uri = Uri.parse("package:" + BuildConfig.APPLICATION_ID);
      Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, uri);
      startActivity(intent);
    }
    else if (Build.VERSION.SDK_INT >= 33) {
      String permission = Manifest.permission.POST_NOTIFICATIONS;
      boolean isGranted = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED;
      if (!isGranted) {
        requestPermissions(new String[] {permission}, Vars.APP_PERMISSION_REQUEST_POST_NOTIFICATIONS);
      }
    }
  }
  
  private void init() {
    log("Screen DPI: " + Fun.getScreenDpi());
    
    setVolumeControlStream(AudioManager.STREAM_MUSIC);
    audioManager = context.getSystemService(AudioManager.class);
    
    IntentFilter volumeFilter = new IntentFilter();
    volumeFilter.addAction(VOLUME_CHANGED_ACTION);
    volumeFilter.addAction(STREAM_DEVICES_CHANGED_ACTION);
    registerReceiver(volumeReceiver, volumeFilter);
    
    fileList = new ArrayList<>();
    filesAdapter = new FilesAdapter(fileList, this);
    scrollStack = new Stack<>();
    
    repeatableFiles = new HashSet<>();
    
    filesAdapter.itemClickAction = (item) -> itemClick(item);
    filesAdapter.iconClickAction = (item) -> updateItemFavorite(item);
    filesAdapter.afterFileRemovedAction = (path) -> onItemRemoved(path);
    filesAdapter.infoClickAction = (path) -> showExtraAudioInfo(path);
    filesAdapter.repeatSelectAction = (item) -> updateFileRepeat(item.path, item.repeat);
    
    trimmedProgressColor = MaterialColors.getColor(context, R.attr.trimSliderProgressColor, Color.TRANSPARENT);
    
    serviceConnection = new ServiceConnection() {
      public void onServiceConnected(ComponentName name, IBinder service) {
        logd("onServiceConnected()");
        var binder = (PlayerService.PlayerBinder) service;
        playerService = binder.getService();
        
        playerService.exitAction = () -> exitApp();
        playerService.playPrevAction = () -> playPrevFileAction();
        playerService.playNextAction = () -> playNextFileAction();
        playerService.progressSetupAction = (time) -> initProgress(time);
        playerService.progressUpdateAction = (time) -> updateProgress(time);
        playerService.timeInitAction = (time, timeTotal) -> onPlayingTimeSetup(time, timeTotal);
        playerService.timeUpdateAction = (time, timeTotal) -> onPlayingTimeChanged(time, timeTotal);
        playerService.onPlayerPreloadedAction = () -> onPlayerPreloaded();
        playerService.onPlayerStartedAction = () -> onPlayerStarted();
        playerService.onPlayerPausedAction = () -> onPlayerPaused();
        playerService.onPlayerResumedAction = () -> onPlayerResumed();
        playerService.onPlayerStoppedAction = () -> onPlayerStopped();
        playerService.onPlayerErrorAction = () -> onPlayerError();
        playerService.onHeadphonesUnplugAction = () -> onHeadphonesUnplug();
        playerService.onHeadphonesPlugAction = () -> onHeadphonesPlug();
        
        serviceBound = true;
        
        if (lastAudio != null) {
          log("lastAudio: %s; %d", lastAudio, lastAudioTime);
          preloadAudio(lastAudio, lastAudioTime);
        }
      }

      public void onServiceDisconnected(ComponentName name) {
        logd("onServiceDisconnected()");
        playerService = null;
        serviceBound = false;
      }
    };
    
    bindPlayerService();
    
    loopOffsetStep = Vars.LOOP_OFFSET_STEP_DEFAULT;
  }
  
  private void configUI() {
    // Find views
    titleScroller = findViewById(R.id.titleScroller);
    activeTitle = findViewById(R.id.activeTitle);
    
    progressSlider = findViewById(R.id.progressSlider);
    
    contentContainer = findViewById(R.id.contentContainer);
    itemsListView = findViewById(R.id.itemsList);
    
    textTimePlaying = findViewById(R.id.textTimePlaying);
    textTimeLeft = findViewById(R.id.textTimeLeft);
    textTimeTotal = findViewById(R.id.textTimeTotal);
    
    textFileExtraData = findViewById(R.id.textFileExtraData);
    textPlayingPosition = findViewById(R.id.textPlayingPosition);
    textPlayingFolderTime = findViewById(R.id.textPlayingFolderTime);
    
    panelInfoLeft = findViewById(R.id.panelInfoLeft);
    panelInfoCenter = findViewById(R.id.panelInfoCenter);
    panelInfoRight = findViewById(R.id.panelInfoRight);
    
    extraControlPanel = findViewById(R.id.extraControlPanel);
    bShuffle = findViewById(R.id.bShuffle);
    bTrimAudio = findViewById(R.id.bTrimAudio);
    bLoopSetup = findViewById(R.id.bLoopSetup);
    bEqualizer = findViewById(R.id.bEqualizer);
    bRepeat = findViewById(R.id.bRepeat);
    
    trimAudioPanel = findViewById(R.id.trimAudioPanel);
    trimAudioSlider = findViewById(R.id.trimAudioSlider);
    textTrimValue = findViewById(R.id.textTrimValue);
    textTrimMax = findViewById(R.id.textTrimMax);
    
    looperPanel = findViewById(R.id.looperPanel);
    loopSlider = findViewById(R.id.loopSlider);
    textLoopStartTime = findViewById(R.id.textLoopStartTime);
    textLoopLength = findViewById(R.id.textLoopLength);
    textLoopEndTime = findViewById(R.id.textLoopEndTime);
    
    bLooperEnable = findViewById(R.id.bLooperEnable);
    bSeekLoopStart = findViewById(R.id.bSeekLoopStart);
    bSeekLoopEnd = findViewById(R.id.bSeekLoopEnd);
    bLooperReset = findViewById(R.id.bLooperReset);
    bLoopStartMinus = findViewById(R.id.bLoopStartMinus);
    bLoopStartPlus = findViewById(R.id.bLoopStartPlus);
    bLoopCycleOffsetStep = findViewById(R.id.bLoopCycleOffsetStep);
    bLoopEndMinus = findViewById(R.id.bLoopEndMinus);
    bLoopEndPlus = findViewById(R.id.bLoopEndPlus);
    
    equalizerPanel = findViewById(R.id.equalizerPanel);
    equalizerView = findViewById(R.id.equalizerView);
    
    extraInfoPanel = findViewById(R.id.extraInfoPanel);
    mainScroll = findViewById(R.id.mainScroll);
    mainInfo = findViewById(R.id.mainInfo);
    imageInfo = findViewById(R.id.imageInfo);
    lyricsScroll = findViewById(R.id.lyricsScroll);
    lyricsInfo = findViewById(R.id.lyricsInfo);
    
    textExtraFileName = findViewById(R.id.textExtraFileName);
    textExtraTitle = findViewById(R.id.textExtraTitle);
    textExtraArtist = findViewById(R.id.textExtraArtist);
    textExtraAlbum = findViewById(R.id.textExtraAlbum);
    textExtraYear = findViewById(R.id.textExtraYear);
    textExtraLength = findViewById(R.id.textExtraLength);
    textExtraBitrate = findViewById(R.id.textExtraBitrate);
    textExtraFrequency = findViewById(R.id.textExtraFrequency);
    textExtraChannels = findViewById(R.id.textExtraChannels);
    extraCodecBlock = findViewById(R.id.extraCodecBlock);
    textExtraCodec = findViewById(R.id.textExtraCodec);
    extraSampleFormatBlock = findViewById(R.id.extraSampleFormatBlock);
    textExtraSampleFormat = findViewById(R.id.textExtraSampleFormat);
    textExtraSize = findViewById(R.id.textExtraSize);
    textExtraPath = findViewById(R.id.textExtraPath);
    
    textImageFileName = findViewById(R.id.textImageFileName);
    textImageInfo = findViewById(R.id.textImageInfo);
    audioImage = findViewById(R.id.audioImage);
    
    textLyricsFileName = findViewById(R.id.textLyricsFileName);
    textExtraLyrics = findViewById(R.id.textExtraLyrics);
    textLyricsPlaceholder = findViewById(R.id.textLyricsPlaceholder);
    
    bPrevFile = findViewById(R.id.bPrevFile);
    bPlayPause = findViewById(R.id.bPlayPause);
    bNextFile = findViewById(R.id.bNextFile);
    bFastRewind = findViewById(R.id.bFastRewind);
    bFastForward = findViewById(R.id.bFastForward);
    
    playExtraIconShuffle = findViewById(R.id.playExtraIconShuffle);
    playExtraIconRepeat = findViewById(R.id.playExtraIconRepeat);
    playExtraIconLoop = findViewById(R.id.playExtraIconLoop);
    playExtraIconTrim = findViewById(R.id.playExtraIconTrim);
    playExtraIconEQ = findViewById(R.id.playExtraIconEQ);
    
    textTotalFiles = findViewById(R.id.textTotalFiles);
    textTotalSize = findViewById(R.id.textTotalSize);
    textTotalTime = findViewById(R.id.textTotalTime);
    textVolumeLevel = findViewById(R.id.textVolumeLevel);
    
    totalFavoritesBlock = findViewById(R.id.totalFavoritesBlock);
    textTotalFavorites = findViewById(R.id.textTotalFavorites);
    
    statusPanel = findViewById(R.id.statusPanel);
    volumeSlider = findViewById(R.id.volumeSlider);
    
    // Init components
    titleScroller.setSmoothScrollingEnabled(false);
    
    listLayoutManager = new LinearLayoutManager(context);
    
    itemsListView.getItemAnimator().setChangeDuration(0);
    itemsListView.setAdapter(filesAdapter);
    itemsListView.setLayoutManager(listLayoutManager);
    itemsListView.addOnItemTouchListener(new RecyclerTouchListener(itemsListView) {
      public void onSwipeRight() {
        changeToParentDir();
      }
    });
    
    Typeface typeface = Typeface.createFromAsset(getAssets(), "fonts/consolas.ttf");
    textTimePlaying.setTypeface(typeface);
    textTimeLeft.setTypeface(typeface);
    textTimeTotal.setTypeface(typeface);
    textFileExtraData.setTypeface(typeface);
    textPlayingPosition.setTypeface(typeface);
    textPlayingFolderTime.setTypeface(typeface);
    
    activeTitle.setOnClickListener(v -> changeToParentDir());
    
    progressSlider.setProgressChangeListener(new ProgressSliderView.ProgressChangeListener() {
      public void onChanging(int value) {
        if (!serviceBound) return;
        playerService.disableUpdateTime();
        updatePlayingTime(value, playerService.getTotalTime());
      }
      public void onChanged(int value) {
        if (!serviceBound) return;
        disableAudioTrim();
        playerService.changePlayPosition(value);
        playerService.enableUpdateTime();
      }
      public void onCancelled() {
        if (!serviceBound) return;
        playerService.enableUpdateTime();
        
        if (!playerService.isPlaying()) {
          int playingTime = playerService.getPlayingTime();
          updatePlayingTime(playingTime, playerService.getTotalTime());
          updateProgress(playingTime);
        }
      }
    });
    
    panelInfoLeft.setOnClickListener(v -> toggleExtraControlPanel());
    panelInfoCenter.setOnClickListener(v -> toggleExtraInfoPanel());
    panelInfoRight.setOnClickListener(v -> changeToPlayingDir());
    
    bShuffle.setOnClickListener(v -> {
      v.setSelected(!v.isSelected());
      playbackShuffleAction();
    });
    
    bTrimAudio.setOnClickListener(v -> {
      v.setSelected(!v.isSelected());
      toggleTrimAudioPanel();
    });
    
    bLoopSetup.setOnClickListener(v -> {
      v.setSelected(!v.isSelected());
      toggleLooperPanel();
    });
    
    bEqualizer.setOnClickListener(v -> {
      v.setSelected(!v.isSelected());
      toggleEqualizerPanel();
    });
    
    bRepeat.setOnClickListener(v -> {
      v.setSelected(!v.isSelected());
      playbackRepeatAction();
    });
    
    bPrevFile.setOnClickListener(v -> playPrevFileAction());
    bPlayPause.setOnClickListener(v -> playPauseAction());
    bNextFile.setOnClickListener(v -> playNextFileAction());
    bFastRewind.setOnClickListener(v -> fastRewindAction());
    bFastForward.setOnClickListener(v -> fastForwardAction());
    
    bNextFile.setOnLongClickListener(v -> {
      playRandomFile();
      return true;
    });
    
    // Prevent click through the panels
    extraInfoPanel.setOnClickListener(null);
    extraControlPanel.setOnClickListener(null);
    
    contentContainer.setOnTouchListener(new OnSwipeTouchListener(this) {
      public void onSwipeRight() {
        changeToParentDir();
      }
    });
    
    mainInfo.setOnTouchListener(new OnSwipeTouchListener(this) {
      public void onSwipeLeft() {
        if (currentExtraInfo != null) {
          loadExtraAudioInfo(Fun.getNextFilePath(currentExtraInfo.file));
          fillAudioInfo();
        }
      }
      public void onSwipeRight() {
        if (currentExtraInfo != null) {
          loadExtraAudioInfo(Fun.getPrevFilePath(currentExtraInfo.file));
          fillAudioInfo();
        }
      }
      public void onSwipeUp() {
        hideExtraInfoPanel(true);
      }
      public void onSwipeDown() {
        loadAudioImage();
        fillAudioInfo();
        showAudioImagePanel();
      }
      public void processDoubleTap(MotionEvent e) {
        if (e.getX() < Fun.dpToPx(80) && e.getY() < Fun.dpToPx(80)) {
          this.onSwipeDown();
        }
      }
    });
    
    imageInfo.setOnTouchListener(new OnSwipeTouchListener(this) {
      public void onSwipeLeft() {
        if (currentExtraInfo != null) {
          loadExtraAudioInfo(Fun.getNextFilePath(currentExtraInfo.file));
          loadAudioImage();
          fillAudioInfo();
        }
      }
      public void onSwipeRight() {
        if (currentExtraInfo != null) {
          loadExtraAudioInfo(Fun.getPrevFilePath(currentExtraInfo.file));
          loadAudioImage();
          fillAudioInfo();
        }
      }
      public void onSwipeUp() {
        showMainInfoPanel();
      }
      public void onSwipeDown() {
        loadAudioLyrics();
        
        if (audioHasLyrics()) {
          fillAudioInfo();
          showLyricsPanel();
        }
        else {
          hideExtraInfoPanel();
        }
      }
    });
    
    lyricsInfo.setOnTouchListener(new OnSwipeTouchListener(this) {
      public void onSwipeLeft() {
        if (currentExtraInfo != null) {
          loadExtraAudioInfo(Fun.getNextFilePath(currentExtraInfo.file));
          loadAudioLyrics();
          fillAudioInfo();
        }
      }
      public void onSwipeRight() {
        if (currentExtraInfo != null) {
          loadExtraAudioInfo(Fun.getPrevFilePath(currentExtraInfo.file));
          loadAudioLyrics();
          fillAudioInfo();
        }
      }
      public void onSwipeUp() {
        loadAudioImage();
        fillAudioInfo();
        showAudioImagePanel();
      }
      public void onSwipeDown() {
        hideExtraInfoPanel();
      }
    });
    
    volumeSlider.setProgressChangeListener(new VolumeSliderView.ProgressChangeListener() {
      public void onChanging(int value) {
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0);
      }
      public void onCancelled() {}
    });
    
    volumeSlider.setMax(audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
    updateVolumeLevel();
    
    // Trim
    textTrimMax.setText(Fun.formatTime(Vars.MAX_TRIM * 1000, false, false));
    updateAudioTrimText(0);
    
    trimAudioSlider.setMax(Vars.MAX_TRIM);
    trimAudioSlider.setProgress(0);
    trimAudioSlider.enable();
    
    trimAudioSlider.setProgressChangeListener(value -> {
      onAudioTrimChanged(value);
      audioTrimSeconds = value;
      // Reset the trimming configuration until new playback is started
      disableAudioTrim();
    });
    
    // Looper
    Action _updateLoop = () -> {
      textLoopStartTime.setText(Fun.formatTime(loopSlider.getProgressStart(), false, true));
      textLoopLength.setText("[" + Fun.formatTime(loopSlider.getProgressEnd() - loopSlider.getProgressStart(), false, true) + "]");
      textLoopEndTime.setText(Fun.formatTime(loopSlider.getProgressEnd(), false, true));
      progressSlider.setLoopPoints(loopEnabled, loopSlider.getProgressStart(), loopSlider.getProgressEnd());
    };
    
    Action _updateLoopStep = () -> {
      bLoopCycleOffsetStep.setText(String.format("%d ms", loopOffsetStep));
    };
    
    loopSlider.setProgressListener(new RangeSliderView.ProgressListener() {
      public void onChanging(int valueStart, int valueEnd) {
        _updateLoop.execute();
        EngineNative.setLoopStart(valueStart);
        EngineNative.setLoopEnd(valueEnd);
      }
      public void onReset() {
        _updateLoop.execute();
        EngineNative.setLoopStart(loopSlider.getProgressStart());
        EngineNative.setLoopEnd(loopSlider.getProgressEnd());
      }
    });
    
    textLoopStartTime.setOnClickListener(v -> {
      if (playerService == null) return;
      int time = playerService.getPlayingTime();
      if (time == -1) return;
      loopSlider.setProgressStart(time);
      _updateLoop.execute();
      EngineNative.setLoopStart(loopSlider.getProgressStart());
    });
    
    textLoopEndTime.setOnClickListener(v -> {
      if (playerService == null) return;
      int time = playerService.getPlayingTime();
      if (time == -1) return;
      loopSlider.setProgressEnd(time);
      _updateLoop.execute();
      EngineNative.setLoopEnd(loopSlider.getProgressEnd());
    });
    
    bLooperEnable.setOnClickListener(v -> {
      v.setSelected(!v.isSelected());
      loopEnabled = v.isSelected();
      
      log("Looper is set to %b", loopEnabled);
      EngineNative.setLoop(loopEnabled);
      
      playExtraIconLoop.setVisibility(loopEnabled ? View.VISIBLE: View.GONE);
      _updateLoop.execute();
    });
    
    bSeekLoopStart.setOnClickListener(v -> {
      if (playerService == null) return;
      
      int time = loopSlider.getProgressStart();
      if (time < playerService.getTotalTime()) {
        playerService.changePlayPosition(time);
      }
    });
    
    bSeekLoopEnd.setOnClickListener(v -> {
      if (playerService == null) return;
      
      int time = loopSlider.getProgressEnd();
      if (time < playerService.getTotalTime()) {
        playerService.changePlayPosition(time);
      }
    });

    bLooperReset.setOnClickListener(v -> loopSlider.reset());
    
    bLoopCycleOffsetStep.setOnClickListener(v -> {
      for (int i = 0; i < Vars.LOOP_OFFSET_STEPS.length; i++) {
        if (loopOffsetStep == Vars.LOOP_OFFSET_STEPS[i]) {
          int next_id = (i == Vars.LOOP_OFFSET_STEPS.length - 1) ? 0: i + 1;
          loopOffsetStep = Vars.LOOP_OFFSET_STEPS[next_id];
          break;
        }
      }
      _updateLoopStep.execute();
    });
    _updateLoopStep.execute();
    
    bLoopStartMinus.setOnClickListener(v -> {
      loopSlider.setProgressStart(loopSlider.getProgressStart() - loopOffsetStep);
      _updateLoop.execute();
      EngineNative.setLoopStart(loopSlider.getProgressStart());
    });
    bLoopStartPlus.setOnClickListener(v -> {
      loopSlider.setProgressStart(loopSlider.getProgressStart() + loopOffsetStep);
      _updateLoop.execute();
      EngineNative.setLoopStart(loopSlider.getProgressStart());
    });
    bLoopEndMinus.setOnClickListener(v -> {
      loopSlider.setProgressEnd(loopSlider.getProgressEnd() - loopOffsetStep);
      _updateLoop.execute();
      EngineNative.setLoopEnd(loopSlider.getProgressEnd());
    });
    bLoopEndPlus.setOnClickListener(v -> {
      loopSlider.setProgressEnd(loopSlider.getProgressEnd() + loopOffsetStep);
      _updateLoop.execute();
      EngineNative.setLoopEnd(loopSlider.getProgressEnd());
    });
    
    // Equalizer
    equalizerView.setupBands(Vars.EQ_BANDS);
    equalizerView.setChangeListener(new EqualizerView.ChangeListener() {
      public void stateChanged(boolean enabled) {
        if (enabled) {
          EngineNative.enableFilter();
        }
        else {
          EngineNative.disableFilter();
        }
        
        int visibility = enabled ? View.VISIBLE: View.GONE;
        if (playExtraIconEQ.getVisibility() != visibility) {
          playExtraIconEQ.setVisibility(visibility);
        }
      }
      
      public void mainGainChanged(float gain) {
        EngineNative.setGain(gain);
        updateVolumeText();
      }
      
      public void gainChanged(int band, float gain) {
        Fun.saveSharedPref(context, "PREF_EQ_GAIN_BAND_" + band, gain);
        EngineNative.setFilterGain(band, gain);
      }
    });
  }
  
  private void restoreState() {
    try {
      lastFolder = Fun.getSharedPref(context, "PREF_LAST_FOLDER");
      log("PREF lastFolder: " + lastFolder);
      
      lastAudio = Fun.getSharedPref(context, "PREF_LAST_AUDIO");
      log("PREF lastAudio: " + lastAudio);
      
      lastAudioTime = Fun.getSharedPrefInt(context, "PREF_LAST_AUDIO_TIME");
      if (lastAudioTime == -1) lastAudioTime = 0;
      log("PREF lastAudioTime: " + lastAudioTime);
      
      favoritesList = Fun.getSharedPrefList(context, "PREF_FAVORITES_LIST");
      // log("PREF favoritesList: " + favoritesList);
      
      playbackRepeat = Fun.getSharedPrefBool(context, "PLAYBACK_REPEAT");
      log("PREF playbackRepeat: " + playbackRepeat);
      bRepeat.setSelected(playbackRepeat);
      playExtraIconRepeat.setVisibility(playbackRepeat ? View.VISIBLE: View.GONE);
      
      playbackShuffle = Fun.getSharedPrefBool(context, "PLAYBACK_SHUFFLE");
      log("PREF playbackShuffle: " + playbackShuffle);
      bShuffle.setSelected(playbackShuffle);
      playExtraIconShuffle.setVisibility(playbackShuffle ? View.VISIBLE: View.GONE);
      
      for (int band = 0; band < equalizerView.getBandsCount(); band++) {
        float gain = Fun.getSharedPrefFloat(context, "PREF_EQ_GAIN_BAND_" + (band + 1));
        if (gain == -1) gain = 0;
        equalizerView.setBandGain(band, gain);
      }
      
      cleanPrefs();
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }
  
  private void initEngine() {
    new Thread(() -> {
      EngineNative.initEngine();
      EngineNative.startEngine();
      log("Audio engine started");
      
      EngineNative.setFilterQ(Vars.EQ_Q_FACTOR);
      for (int band = 0; band < equalizerView.getBandsCount(); band++) {
        EngineNative.setFilterFrequency(band + 1, equalizerView.getBandFrequency(band));
      }
      for (int band = 0; band < equalizerView.getBandsCount(); band++) {
        float gain = equalizerView.getBandGain(band);
        if (gain != 0) EngineNative.setFilterGain(band + 1, gain);
      }
    }).start();
  }
  
  
  // ------------------------------ Actions ------------------------------
  private void playPauseAction() {
    if (playerService == null || !playerService.hasAudio()) return;
    
    if (playerService.isPlaying()) {
      playerService.pause();
      validatePlayingList();
    }
    else if (playerService.isPlayerLoaded()) {
      boolean result = playerService.resume();
      if (result) setPlayButtonAsPause();
      else setPlayButtonDefault();
      validatePlayingList();
    }
    else {
      // If stopped or service is reset, restart audio
      playAudio(playerService.getAudioPath(), 0, true);
    }
  }
  
  private void playNextFileAction() {
    if (playerService == null) return;
    boolean startPlayback = playerService.isPlaying();
    playNextFile(startPlayback);
  }
  
  private void playPrevFileAction() {
    if (playerService == null) return;
    boolean startPlayback = playerService.isPlaying();
    playPrevFile(startPlayback);
  }
  
  private void playRandomFile() {
    if (playerService == null) return;
    boolean startPlayback = playerService.isPlaying();
    playNextFile(startPlayback, true);
  }
  
  private void fastRewindAction() {
    if (playerService == null || !playerService.isPlayerLoaded()) return;
    disableAudioTrim();
    playerService.fastRewind(5);
  }
  
  private void fastForwardAction() {
    if (playerService == null || !playerService.isPlayerLoaded()) return;
    disableAudioTrim();
    playerService.fastForward(5);
  }
  
  private void playbackShuffleAction() {
    playbackShuffle = !playbackShuffle;
    Fun.saveSharedPref(context, "PLAYBACK_SHUFFLE", playbackShuffle);
    playExtraIconShuffle.setVisibility(playbackShuffle ? View.VISIBLE: View.GONE);
    shuffleList.clear();
  }
  
  private void playbackRepeatAction() {
    playbackRepeat = !playbackRepeat;
    Fun.saveSharedPref(context, "PLAYBACK_REPEAT", playbackRepeat);
    
    playExtraIconRepeat.setVisibility(playbackRepeat ? View.VISIBLE: View.GONE);
    
    if (playerService == null || !playerService.hasAudio()) return;

    boolean fileRepeat = repeatableFiles.contains(playerService.getAudioPath());
    EngineNative.setRepeat(playbackRepeat || fileRepeat);
  }
  
  
  // ------------------------------ Audio ------------------------------
  private void playAudio(String filePath, int time, boolean startPlayback) {
    logd("playAudio(), \"%s\" %d", filePath, time);
    this.updateTimeEnabled = false;
    this.nextFilePreloaded = false;
    this.nextPreloadingFailed = false;
    
    if (!serviceBound || playerService == null) {
      loge("Player service is not initialized");
      this.nextFilePreloaded = false;
      return;
    }
    
    boolean isSameFile = (playerService.hasAudio() && playerService.getAudioPath().equals(filePath));
    if (!isSameFile) {
      playerService.stopProgress();
      progressSlider.reset();
      progressSlider.clearSections();
    }
    
    File playingFile = new File(filePath);
    if (!playingFile.exists()) {
      loge("The file does not exist: " + filePath);
      progressSlider.disable();
      setPlayButtonDefault();
      return;
    }
    
    if (this.audioTrimSeconds > 0 && time == 0) {
      enableAudioTrim();
    }
    else {
      disableAudioTrim();
    }
    
    if (loopEnabled) disableLoop();
    
    updateWaveform(filePath);
    processPlayingDirChange(playingFile);
    
    if (isSameFile) {
      playerService.changePlayPosition(time);
      if (startPlayback && !playerService.isPlaying()) playerService.resume();
      updatePlayingStats();
    }
    else {
      Intent playerIntent = new Intent(this, PlayerService.class);
      playerIntent.putExtra(Vars.EXTRA_AUDIO_PATH, filePath);
      playerIntent.putExtra(Vars.EXTRA_AUDIO_TIME, time);
      playerIntent.putExtra(Vars.EXTRA_START_PLAYBACK, startPlayback);
      
      boolean fileRepeat = repeatableFiles.contains(filePath);
      playerIntent.putExtra(Vars.EXTRA_PLAYBACK_REPEAT, playbackRepeat || fileRepeat);
      
      startService(playerIntent);
      // try {
      // > check loading with screen off
      //   startService(playerIntent);
      // }
      // catch (Exception e) {
      //   // e.printStackTrace();
      // }
      log("playerService started");
    }
    
    this.updateTimeEnabled = true;
    
    updateCurrentTrack(filePath, time);
    updateShuffleList();
    removeFromShuffleList(this.currentTrack);
    
    Fun.saveSharedPref(context, "PREF_LAST_AUDIO", filePath);
    Fun.saveSharedPref(context, Vars.PREF_LAST_FILE_IN_FOLDER + playingFile.getParent(), filePath);

    markItem(filePath);
    selectCurrentTrack();
    
    if (!startPlayback) setPlayButtonDefault();
  }
  
  private void syncNextFile(String filePath) {
    logd("syncNextFile(), \"%s\"", filePath);
    
    nextFilePreloaded = false;
    nextPreloadingFailed = false;
    
    if (filePath == null || filePath.length() == 0) return;
    
    File playingFile = new File(filePath);
    
    if (this.audioTrimSeconds > 0) {
      enableAudioTrim();
    }
    else {
      disableAudioTrim();
    }
    if (loopEnabled) disableLoop();
    
    updateWaveform(filePath);
    
    Intent playerIntent = new Intent(this, PlayerService.class);
    playerIntent.putExtra(Vars.EXTRA_SYNC_FILE, true);
    playerIntent.putExtra(Vars.EXTRA_AUDIO_PATH, filePath);
    startService(playerIntent);
    
    boolean fileRepeat = repeatableFiles.contains(filePath);
    EngineNative.setRepeat(playbackRepeat || fileRepeat);
    
    updateCurrentTrack(filePath);
    
    Fun.saveSharedPref(context, "PREF_LAST_AUDIO", filePath);
    Fun.saveSharedPref(context, Vars.PREF_LAST_FILE_IN_FOLDER + playingFile.getParent(), filePath);

    markItem(filePath);
    filesAdapter.selectItem(filePath);
  }
  
  private void playAudio(String filePath, boolean startPlayback) {
    playAudio(filePath, 0, startPlayback);
  }
  
  private void preloadAudio(String filePath, int time) {
    logd("preloadAudio()");
    if (time < Vars.MIN_PLAYABLE_TIME) time = 0;
    playAudio(filePath, time, false);
  }
  
  private void playNextFile(boolean startPlayback) {
    playNextFile(startPlayback, false);
  }
  
  private void playNextFile(boolean startPlayback, boolean random) {
    logd("playNextFile()");
    if (playerService == null || !playerService.hasAudio()) return;
    
    String currentFile = playerService.getAudioPath();
    Track track = null;
    if (playbackShuffle || random) {
      track = getNextRandomTrack();
    }
    
    if (track == null) {
      track = getNextPlaylistTrack();
    }
    
    if (track != null) {
      log("Next track: " + track.path);
      int time = 0;
      if (track instanceof CueTrack) time = ((CueTrack) track).startTime;
      playAudio(track.path, time, startPlayback);
    }
    else {
      loge("Next track is null");
    }
  }
  
  private void playPrevFile(boolean startPlayback) {
    logd("playPrevFile()");
    if (playerService == null || !playerService.hasAudio()) return;
    
    Track track = getPrevPlaylistTrack();
    
    if (track != null) {
      log("Previous track: " + track.path);
      int time = 0;
      if (track instanceof CueTrack) time = ((CueTrack) track).startTime;
      playAudio(track.path, time, startPlayback);
    }
    else {
      loge("Previous track is null");
    }
  }
  

  // ------------------------------ Navigation ------------------------------
  private void changeDir(File path, boolean scrollTop) {
    logd("changeDir(): " + path);
    loadDirectoryTimeTask.cancel();
    
    if (path == null || !path.exists() || !path.isDirectory()) path = ROOT_STORAGE;

    boolean isChangeToChild = (this.currentPath != null && this.currentPath.equals(path.getParentFile()));
    if (isChangeToChild) {
      if (scrollStack != null) {
        scrollStack.push(listLayoutManager.onSaveInstanceState());
      }
    }

    this.currentPath = path;
    loadFileList(path);
    
    markVisitedFolders();
    markRepeatFiles();
    
    filesAdapter.resetSelection();
    filesAdapter.notifyDataSetChanged();

    String title = currentPath.equals(ROOT_STORAGE) ? ROOT_DIR_TITLE: currentPath.getName();
    activeTitle.setText(title);
    
    titleScroller.fullScroll(View.FOCUS_LEFT);
    
    updateDirStatus();
    loadListTime();
    
    if (scrollTop) {
      listLayoutManager.scrollToPositionWithOffset(0, 0);
    }
    
    lastFolder = path.getPath();
    Fun.saveSharedPref(context, "PREF_LAST_FOLDER", lastFolder);
    
    selectCurrentTrack();
    resetCurrentDirTime();

    markLastPlayedFileInDir(currentPath);
    markFavorites();
    updateFavoritesStats();
    
    hideExtraPanels();
  }
  
  private void changeDir(File path) {
    changeDir(path, true);
  }
  
  private void changeToParentDir() {
    if (currentPath.equals(ROOT_STORAGE)) {
      log("In the root folder, cannot go to parent");
      listLayoutManager.scrollToPositionWithOffset(0, 0);
      hideExtraPanels();
      return;
    }
    
    String prevPath = currentPath.getPath();
    
    boolean scrollTop = true;
    if (scrollStack != null && !scrollStack.empty()) scrollTop = false;
    changeDir(currentPath.getParentFile(), scrollTop);
    
    if (scrollStack != null && !scrollStack.empty()) {
      Parcelable entry = scrollStack.pop();
      listLayoutManager.onRestoreInstanceState(entry);
    }
    else {
      int scrollPos = filesAdapter.getItemPosition(prevPath);
      listLayoutManager.scrollToPosition(scrollPos);
    }
  }
  
  private void refreshCurrentDir(boolean scrollTop) {
    logd("refreshCurrentDir()");
    changeDir(currentPath, scrollTop);
  }
  
  private void validateCurrentDir() {
    if (currentPath == null && lastFolder != null) {
      currentPath = new File(lastFolder);
    }
    
    if (currentPath == null || !currentPath.exists()) {
      logw("The last visited directory doesn't exist (" + currentPath + "), changing to its parent");
      File parent = Fun.getNearestExistingParent(currentPath);
      changeDir(parent);
      return;
    }
    
    if (this.loadDirectoryTimeTask.getListSize() < this.MAX_FILES_FOR_SCANNING) {
      refreshCurrentDir(false);
    }
  }
  
  private void loadFileList(File path) {
    fileList.clear();
    
    File[] dirs = path.listFiles(Fun.dirFilter);
    Stream.of(dirs).sorted(Fun.nocaseComp)
      .forEach(file -> fileList.add(ListItem.newDir(file.getName(), file.getAbsolutePath())));
      
    File[] files = path.listFiles(Fun.fileFilter);
    
    Stream.of(files).sorted(Fun.nocaseComp)
    .forEach(file -> {
      if (file.getName().endsWith(".cue")) {
        List<CueTrack> tracks = parseCue(file);
        if (tracks.isEmpty()) return;
        
        var sourceItem = ListItem.newCueSource(Fun.getName(tracks.get(0).path), tracks.get(0).path);
        fileList.add(sourceItem);
        
        for (var track: tracks) {
          String time = Fun.formatTime(track.endTime - track.startTime, false, false);
          fileList.add(ListItem.newCueTrack(track.name, sourceItem, time, track.startTime, track.endTime));
        }
      }
      else {
        fileList.add(ListItem.newFile(file.getName(), file.getAbsolutePath()));
      }
    });
      
    boolean cueFound = fileList.stream().anyMatch(item -> item.isCue);
    if (!cueFound) return;
    
    // Remove duplicated source file for CUE, that was placed before the CUE tracks
    var cleanList = fileList.stream().filter(item1 -> {
      if (item1.isCueTrack || item1.isCue) return true;
      
      boolean isSourceFile = fileList.stream().anyMatch(item2 -> {
        return item2.isCue && item1.path != null && item1.path.equals(item2.path);
      });
      
      if (isSourceFile) return false;
      return true;
    })
    .collect(Collectors.toList());

    fileList.clear();
    fileList.addAll(cleanList);
  }
  
  private List<CueTrack> parseCue(File file) {
    List<CueTrack> items = new ArrayList<>();
    
    try {
      List<String> lines = Files.readAllLines(file.toPath());
      String audioFileName = null;
      
      for (String line: lines) {
        line = line.trim();
        if (line.toUpperCase().startsWith("FILE")) {
          int firstQuote = line.indexOf('"');
          int lastQuote = line.lastIndexOf('"');
          if (firstQuote != -1 && lastQuote > firstQuote) {
            audioFileName = line.substring(firstQuote + 1, lastQuote);
            break;
          }
        }
      }
      
      if (audioFileName == null) {
        logw("Could not extract CUE source file name");
        return items;
      }
      
      File audioFile = new File(file.getParent(), audioFileName);
      
      if (!audioFile.exists()) {
        logw("Referenced audio file in CUE does not exist: " + audioFile.getAbsolutePath());
        return items;
      }
      
      int trackNumber = 1;
      int lastIndexTime = 0;
      String currentTrackTitle = null;
      List<Integer> trackStartTimes = new ArrayList<>();
      List<String> trackTitles = new ArrayList<>();

      for (String line: lines) {
        line = line.trim();
        if (line.toUpperCase().startsWith("TRACK")) {
          currentTrackTitle = null;
          lastIndexTime = 0;
        }
        
        if (line.toUpperCase().startsWith("TITLE")) {
          int firstQuote = line.indexOf('"');
          int lastQuote = line.lastIndexOf('"');
          if (firstQuote != -1 && lastQuote > firstQuote) {
            currentTrackTitle = line.substring(firstQuote + 1, lastQuote);
          }
        }
        
        if (line.toUpperCase().startsWith("INDEX 01")) {
          String[] parts = line.split(" ");
          String timeStr = parts[parts.length - 1];
          String[] timeParts = timeStr.split(":");
          if (timeParts.length == 3) {
            int min = Integer.parseInt(timeParts[0]);
            int sec = Integer.parseInt(timeParts[1]);
            int frame = Integer.parseInt(timeParts[2]);
            int ms = (min * 60 + sec) * 1000 + (int) (frame * (1000.0 / 75.0));
            
            trackStartTimes.add(ms);
            String formattedTitle = String.format("Track %02d", trackNumber);
            if (currentTrackTitle != null) {
              formattedTitle = String.format("%02d. %s", trackNumber, currentTrackTitle);
            }
            trackTitles.add(formattedTitle);
            
            trackNumber++;
          }
        }
      }
      
      // Get audio file duration
      int audioDuration = extractAudioTime(audioFile.getAbsolutePath());

      for (int i = 0; i < trackStartTimes.size(); i++) {
        String trackName = trackTitles.get(i);
        int startTime = trackStartTimes.get(i);
        int endTime = (i < trackStartTimes.size() - 1) ? trackStartTimes.get(i + 1) : audioDuration;
        
        items.add(new CueTrack(audioFile.getAbsolutePath(), trackName, startTime, endTime));
      }
      
    }
    catch (Exception e) {
      loge("Error parsing CUE file: " + file.getAbsolutePath() + " => " + e);
    }
    
    return items;
  }
  
  private File getPlayingFile() {
    if (playerService == null || !playerService.hasAudio()) return null;
    File currentFile = new File(playerService.getAudioPath());
    return currentFile;
  }
  
  private boolean belongsToCurrentDir(File file) {
    if (file == null || file.getParentFile() == null) return false;
    return file.getParentFile().equals(currentPath);
  }
  
  private void changeToPlayingDir() {
    logd("changeToPlayingDir()");
    
    File playingFile = getPlayingFile();
    if (playingFile == null) return;
    if (!playingFile.exists()) {
      loge("File doesn't exist: " + playingFile);
      return;
    }

    if (belongsToCurrentDir(playingFile)) {
      int scrollPos = filesAdapter.getItemPosition(playingFile.getPath());
      if (scrollPos != -1) {
        listLayoutManager.scrollToPosition(scrollPos);
      }
      hideExtraPanels();
    }
    else {
      changeDir(playingFile.getParentFile());
      Fun.emptyStack(scrollStack);
    }
  }
  
  private void markLastPlayedFileInDir(File dir) {
    String lastFile = Fun.getSharedPref(this, Vars.PREF_LAST_FILE_IN_FOLDER + dir.getPath());
    if (lastFile != null) {
      String lastFileName = new File(lastFile).getName();
      int lastTime = Fun.getSharedPrefInt(this, Vars.PREF_LAST_TIME_IN_FOLDER + dir.getPath());
      
      log("Last played file in dir '%s': '%s', Time: %d", dir, lastFileName, lastTime);
      markItem(lastFile);
    }
  }
  
  private void markFavorites() {
    if (fileList == null) return;
    
    fileList.stream()
      .filter(item -> {
        String key = (item.isCueTrack) ? item.path + ":" + item.text: item.path;
        return favoritesList.contains(key);
      })
      .forEach(item -> filesAdapter.markAsFavorite(item));
  }
  
  private void markVisitedFolders() {
    if (fileList == null) return;
    
    fileList.stream()
      .filter(item -> item.isFolder)
      .forEach(item -> {
        String lastFile = Fun.getSharedPref(this, Vars.PREF_LAST_FILE_IN_FOLDER + item.path);
        item.isVisited = Fun.fileExists(lastFile);
      });
  }
  
  private void markRepeatFiles() {
    if (fileList == null) return;
    
    fileList.stream()
      .filter(item -> item.isFile)
      .forEach(item -> item.repeat = repeatableFiles.contains(item.path));
  }
  
  private void updateFavoritesStats() {
    if (fileList == null) return;
    
    long totalFavorites = fileList.stream()
      .filter(item -> {
        String key = (item.isCueTrack) ? item.path + ":" + item.text: item.path;
        return favoritesList.contains(key);
      })
      .count();
    
    textTotalFavorites.setText(String.valueOf(totalFavorites));
    totalFavoritesBlock.setVisibility(totalFavorites == 0 ? View.GONE: View.VISIBLE);
  }
  
  
  // ------------------------------ Events ------------------------------
  private void onPlayerStarted() {
    logd("onPlayerStarted()");
    onPlayerPreloaded();
    setPlayButtonAsPause();
  }
  
  private void onPlayerPreloaded() {
    logd("onPlayerPreloaded()");
    progressSlider.enable();
    updatePlayingStats();
    
    setupLooper();
    if (loopEnabled) EngineNative.setLoop(true);
    
    if (extraInfoPanel.getVisibility() == View.VISIBLE) {
      if (extraInfoIsForCurrentFile || 
          currentExtraInfo != null && currentExtraInfo.file.getPath().equals(playerService.getAudioPath()))
      {
        // Update audio info for current loaded file
        showExtraAudioInfo();
      }
    }

    if (progressSlider.atMaxProgress()) {
      progressSlider.disable();
    }
    
    if (this.currentTrack != null && this.currentTrack instanceof CueTrack) {
      int[] cueTimes = this.playingList.stream().filter(track -> {
        return track instanceof CueTrack && track.path.equals(this.currentTrack.path);
      }).skip(1).mapToInt(track -> ((CueTrack) track).startTime).toArray();
      progressSlider.setSections(cueTimes);
    }
  }
  
  private void onPlayerPaused() {
    setPlayButtonDefault();
  }
  
  private void onPlayerResumed() {
    setPlayButtonAsPause();
  }
  
  private void onPlayerStopped() {
    logd("onPlayerStopped()");
    
    if (!playbackShuffle && isPlayingLastFile()) {
      progressSlider.disable();
      setPlayButtonDefault();
    }
    else {
      boolean forceLoadNext = true;
      
      // Multiple conditions for preloading, global setting enabled, file previously preloaded and current backend audio path is not the same as the current audio in the player service
      if (Vars.ENABLE_NEXT_FILE_PRELOADING && nextFilePreloaded) {
        nextFilePreloaded = false;
        String filePath = EngineNative.getAudioPath();
        
        if (!filePath.equals(playerService.getAudioPath())) {
          forceLoadNext = false;
          syncNextFile(filePath);
        }
      }
      
      if (forceLoadNext) {
        // The audio is ended but did not change automatically to the next one in the backend
        // (maybe it's too short so the next file is not preloaded)
        // The next file needs to be manually selected
        playNextFile(true);
      }
    }
  }
  
  private void onPlayerError() {
    resetPlayer();
    
    if (playerService == null) return;
    filesAdapter.markError(playerService.getAudioPath());
  }
  
  private void onPlayingTimeSetup(int playingTime, int totalTime) {  // time in ms
    updatePlayingTime(playingTime, totalTime);
  }
  
  private void onPlayingTimeChanged(int playingTime, int totalTime) {  // time in ms
    if (!this.updateTimeEnabled) return;
    updatePlayingTime(playingTime, totalTime);
    
    if (this.isPlayingCueTrack()) {
      var track = getPlayingTrack();
      if (this.currentTrack != track) {
        this.currentTrack = track;
        updatePlayingStats();
        selectCurrentTrack();
      }
    }
    
    // Preload file when 10s or less is left until the current audio end
    if (Vars.ENABLE_NEXT_FILE_PRELOADING && !nextFilePreloaded && !nextPreloadingFailed && !playbackShuffle) {
      int timeLeft = totalTime - playingTime;
      boolean nearAudioEnd = timeLeft < Vars.NEXT_PRELOADING_TIME && timeLeft > 200 && !isPlayingLastFile();
      
      if (nearAudioEnd) {
        Track track = getNextPlaylistTrack();
        
        nextPreloadingFailed = true;
        if (track == null) {
          logw("Next file is null. Cannot preload it");
        }
        else if (!Fun.fileExists(track.path)) {
          logw("Next file doesn't exist. Cannot preload it: " + track.path);
        }
        else {
          log("Preloading next file: " + track.path);
          nextFilePreloaded = EngineNative.bufferNextAudio(track.path);
          nextPreloadingFailed = !nextFilePreloaded;
        }
      }
    }
    
    if (this.audioTrimEnabled && playingTime / 1000 >= this.audioTrimSeconds) {
      if (isPlayingLastFile()) {
        if (playerService != null) playerService.seekToEnd();
      }
      else {
        playNextFile(true);
      }
    }
  }
  
  private void onHeadphonesUnplug() {
    updateVolumeLevel();
  }
  
  private void onHeadphonesPlug() {
    updateVolumeLevel();
  }
  
  private void onItemRemoved(String filePath) {
    if (filePath == null) return;
    log("File removed: " + filePath);
    refreshCurrentDir(false);
    
    // Check if the removed file was in the current playlist
    File playingFile = getPlayingFile();
    
    if (belongsToCurrentDir(playingFile)) {
      if (playingFile.getPath().equals(filePath)) {
        log("Playing file has been deleted, selecting next file");
        playNextFile(playerService.isPlaying());
      }
      
      reloadPlayingListForDir(playingFile.getParentFile());
      updatePlayingStats();
      
      if (playingList.isEmpty()) {
        log("Playing list is empty, resetting the player UI");
        fullStop();
      }
    }
  }
  
  
  // ------------------------------ Audio Utils ------------------------------
  private Track getNextPlaylistTrack() {
    if (this.playingList.isEmpty()) return null;
    if (this.currentTrack == null) return null;
    
    int len = this.playingList.size();
    
    int currentIndex = this.playingList.indexOf(currentTrack);
    int nextIndex = (currentIndex == len - 1) ? 0: currentIndex + 1;
    return this.playingList.get(nextIndex);
  }
  
  private Track getPrevPlaylistTrack() {
    if (this.playingList.isEmpty()) return null;
    if (this.currentTrack == null) return null;
    
    int len = this.playingList.size();
    
    int currentIndex = this.playingList.indexOf(currentTrack);
    int prevIndex = (currentIndex == 0) ? len - 1: currentIndex - 1;
    return this.playingList.get(prevIndex);
  }
  
  private Track getNextRandomTrack() {
    if (shuffleList.isEmpty()) {
      generateShuffleList();
    }
    
    if (shuffleList.isEmpty()) {
      return null;
    }
    
    int nextId = randShuffle.nextInt(shuffleList.size());
    return shuffleList.get(nextId);
  }
  
  private void selectCurrentTrack() {
    logd("selectCurrentTrack()");
    if (this.currentTrack == null) return;
    
    if (this.currentTrack instanceof CueTrack) {
      filesAdapter.selectCueTrack(this.currentTrack.path, ((CueTrack) this.currentTrack).startTime);
    }
    else {
      filesAdapter.selectItem(this.currentTrack.path);
    }
  }
  
  private void markItem(String filePath) {
    if (filePath == null) return;
    // File doesn't belong to the current folder
    if (!currentPath.equals(new File(filePath).getParentFile())) return;
    filesAdapter.markLastPlayedItem(filePath);
  }
  
  private boolean isPlayingLastFile() {
    if (playerService == null || !playerService.hasAudio()) return true;
    if (playingList.isEmpty()) return true;
    String lastFile = playingList.get(playingList.size() - 1).path;
    return playerService.getAudioPath().equals(lastFile);
  }
  
  private int extractAudioTime(String filePath) {
    int time = 0;
    
    var mediaExtractor = new MediaExtractor();
    try {
      mediaExtractor.setDataSource(filePath);
      MediaFormat format = mediaExtractor.getTrackFormat(0);
      
      long duration = format.getLong(MediaFormat.KEY_DURATION);
      time = (int) (duration / 1000);
    }
    catch (Exception e) {
      logw("Could not load media extractor for: " + filePath);
    }
    finally {
      mediaExtractor.release();
    }
    
    return time;
  }
  
  private void generateShuffleList() {
    logd("generateShuffleList()");
    if (this.playingList.isEmpty()) return;
    this.shuffleList = new ArrayList<>(this.playingList);
  }
  
  private void removeFromShuffleList(Track track) {
    if (!playbackShuffle) return;
    if (track == null) return;
    logd("removeFromShuffleList(): " + track.path);
    
    for (Track shuffleTrack: shuffleList) {
      if (shuffleTrack.path.equals(track.path)) {
        shuffleList.remove(shuffleTrack);
        return;
      }
    }
  }
  
  private void updateShuffleList() {
    if (!playbackShuffle) return;
    logd("updateShuffleList()");
    
    if (!belongsToShuffleList(this.currentTrack)) {
      generateShuffleList();
    }
  }
  
  private boolean belongsToShuffleList(Track track) {
    if (shuffleList.isEmpty()) return false;
    if (track == null) return false;
    return Fun.getFolder(track.path).equals(Fun.getFolder(shuffleList.get(0).path));
  }
  
  private void fullStop() {
    if (playerService != null) {
      playerService.stop();
      playerService.resetService();
      playerService.stopForeground(true);
    }
    
    progressSlider.clearWaveform();
    resetPlayer();
  }
  
  private void resetPlayer() {
    progressSlider.reset();
    progressSlider.disable();
    setPlayButtonDefault();
    
    updatePlayingStats();
    updatePlayingTime(0, 0);
  }
  
  
  // ------------------------------ Utils ------------------------------
  
  private int getCurrentCueTime() {
    if (playerService == null) return 0;
    // Adjust CUE track time as seek can offset the real time by some ms
    int time = playerService.getPlayingTime();
    int result = (time == 0) ? 0: time + 50;
    return result;
  }
  
  private boolean isPlayingCueTrack() {
    return this.currentTrack != null && this.currentTrack instanceof CueTrack;
  }
  
  private void cachePlayingList(File dir) {
    logd("cachePlayingList(): " + dir);
    if (dir == null) return;
    if (!dir.exists()) {
      loge("Directory doesn't exist: " + dir);
    }
    
    playingList.clear();
    File[] files = dir.listFiles(Fun.fileFilter);
    
    Stream.of(files).sorted(Fun.nocaseComp)
    .forEach(file -> {
      if (file.getName().endsWith(".cue")) {
        List<CueTrack> tracks = parseCue(file);
        playingList.addAll(tracks);
      }
      else {
        playingList.add(new Track(file.getAbsolutePath(), file.getName()));
      }
    });
    
    boolean cueFound = playingList.stream().anyMatch(track -> track instanceof CueTrack);
    if (!cueFound) return;
    
    var cleanList = playingList.stream().filter(track1 -> {
      if (track1 instanceof CueTrack) return true;
      
      boolean isSourceTrack = playingList.stream().anyMatch(track2 -> {
        return track2 instanceof CueTrack && track1.path.equals(track2.path);
      });
      
      if (isSourceTrack) return false;
      return true;
    })
    .collect(Collectors.toList());
    
    playingList.clear();
    playingList.addAll(cleanList);
  }
  
  private void reloadPlayingListForDir(File dir) {
    logd("reloadPlayingListForDir(): " + dir);
    cachePlayingList(dir);
    resetPlayingDirTime();
    loadPlaylistTime();
  }
  
  private void loadPlaylistTime() {
    if (playingList.isEmpty()) return;
    loadPlaylistTimeTask.cancel();
    
    List<String> list = playingList.stream().map(item -> item.path).distinct().collect(Collectors.toList());
    loadPlaylistTimeTask.setList(list);
    
    loadPlaylistTimeTask.execute(time -> {
      textPlayingFolderTime.setText(Fun.formatTime(time, true, false));
    });
  }
  
  private boolean isPlayingListInconsistent() {
    for (int i = 0; i < playingList.size(); i++) {
      if (!Fun.fileExists(playingList.get(i).path)) return true; 
    }
    return false;
  }
  
  private void validatePlayingList() {
    if (playingList.isEmpty()) return;
    File playingListDir = new File(playingList.get(0).path).getParentFile();
    
    // >> Check if files are added
    
    if (playingList.size() > this.MAX_FILES_FOR_SCANNING) {
      new Thread(() -> {
        if (isPlayingListInconsistent()) {
          runOnUiThread(() -> {
            reloadPlayingListForDir(playingListDir);
            updatePlayingStats();
          });
        }
      }).start();
      
      return;
    }
    
    if (isPlayingListInconsistent()) {
      reloadPlayingListForDir(playingListDir);
      updatePlayingStats();
    }
  }
  
  private void updateCurrentTrack(String path) {
    this.updateCurrentTrack(path, 0);
  }
  
  private void updateCurrentTrack(String path, int time) {
    this.currentTrack = getPlayingTrack(path, time);
    if (this.currentTrack != null && this.currentTrack instanceof CueTrack) {
      logd("updateCurrentTrack(): \"%s\" \"%s\" [%d]", this.currentTrack.name, path, time);
    }
  }
  
  private Track getPlayingTrack() {
    if (playerService == null || !playerService.hasAudio()) return null;
    return getPlayingTrack(playerService.getAudioPath(), 0);
  }
  
  private Track getPlayingTrack(String path, int time) {
    int pos = -1;
    boolean posFound = false;
    
    int size = this.playingList.size();
    for (int i = 0; i < size; i++) {
      var track = this.playingList.get(i);
      if (!path.equals(track.path)) continue;
      
      if (track instanceof CueTrack) {
        var cueTrack = (CueTrack) track;
        var cueTime = (time != 0) ? time: getCurrentCueTime();
        if (cueTime < cueTrack.endTime) posFound = true;
      }
      else {
        posFound = true;
      }
      
      if (posFound) {
        pos = i;
        break;
      }
    }
    
    if (pos == -1) return null;

    return this.playingList.get(pos);
  }
  
  private void itemClick(ListItem item) {
    try {
      String path = item.path;
      logd("itemClick() \"%s\" [%s]", item.text, path);
      
      File clickedFile = new File(path);
      if (clickedFile.isDirectory()) {
        changeDir(clickedFile);
        return;
      }
      
      hideExtraPanels();
      
      int time = 0;
      if (item.isLastPlayed) {
        if (playerService != null && (!playerService.hasAudio() || !playerService.getAudioPath().equals(path)) ) {
          int lastTime = Fun.getSharedPrefInt(this, Vars.PREF_LAST_TIME_IN_FOLDER + clickedFile.getParent());
          if (lastTime != -1) time = lastTime;
          if (time < Vars.MIN_PLAYABLE_TIME) time = 0;
        }
      }
      
      if (item.isCueTrack) {
        time = item.cueStartTime;
      }
      
      playAudio(path, time, true);
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }
  
  private void processPlayingDirChange(File newAudioFile) {
    logd("processPlayingDirChange(): " + newAudioFile);
    if (playerService == null) return;
    if (newAudioFile == null || newAudioFile.getParent() == null) return;

    if (playerService.hasAudio()) {
      var currentAudio = new File(playerService.getAudioPath());
      var currentAudioParent = currentAudio.getParent();
      var isDirectoryChanged = !newAudioFile.getParent().equals(currentAudioParent);
      
      if (isDirectoryChanged) {
        log("Directory changed");
        
        Fun.saveSharedPref(context, Vars.PREF_LAST_TIME_IN_FOLDER + currentAudioParent, lastAudioTime);
        log("Saved %d to TIME_%s", lastAudioTime, currentAudioParent);
        
        cachePlayingList(newAudioFile.getParentFile());
        resetPlayingDirTime();
        
        if (!loadDirectoryTimeTask.isRunning()) {
          int playlistTime = loadDirectoryTimeTask.getResult();
          textPlayingFolderTime.setText(Fun.formatTime(playlistTime, true, false));
        }
        else {
          loadPlaylistTime();
        }
      }
      else {
        validatePlayingList();
      }
    }
    else {
      reloadPlayingListForDir(newAudioFile.getParentFile());
    }
  }
  
  private void cleanPrefs() {
    cleanFavorites();
    
    var allPrefs = Fun.getAllSharedPrefs(context);
    for (var pref: allPrefs.entrySet()) {
      String key = pref.getKey();
      
      if (key.startsWith(Vars.PREF_LAST_FILE_IN_FOLDER)) {
        String dir = key.substring(Vars.PREF_LAST_FILE_IN_FOLDER.length());
        String file = (String) pref.getValue();
        
        if (!Fun.fileExists(dir) || !Fun.fileExists(file)) {
          log("Removing prefs for dir '%s', as dir or file doesn't exist", dir);
          
          Fun.removeSharedPref(context, Vars.PREF_LAST_FILE_IN_FOLDER + dir);
          Fun.removeSharedPref(context, Vars.PREF_LAST_TIME_IN_FOLDER + dir);
        }
      }
      
    }
  }
  
  private void cleanFavorites() {
    // Removes files that don't exist
    if (favoritesList == null) return;
    var listCopy = List.copyOf(favoritesList);
    
    for (String key: listCopy) {
      String path = (key.contains(":")) ? key.split(":")[0]: key;
      if (!Fun.fileExists(path)) {
        favoritesList.remove(key);
      }
    }
    
    Fun.saveSharedPref(context, "PREF_FAVORITES_LIST", favoritesList);
  }
  
  private void updateItemFavorite(ListItem item) {
    if (item == null || item.path == null) return;
    
    String key = (item.isCueTrack) ? item.path + ":" + item.text: item.path;
    if (item.isFavorite) {
      favoritesList.add(key);
    }
    else {
      favoritesList.remove(key);
    }
    
    Fun.saveSharedPref(context, "PREF_FAVORITES_LIST", favoritesList);
    updateFavoritesStats();
  }
  
  private void updateFileRepeat(String filePath, boolean repeat) {
    logd("updateFileRepeat(): '%s' => %b", filePath, repeat);
    if (filePath == null) return;
    
    if (repeat) {
      repeatableFiles.add(filePath);
    }
    else {
      repeatableFiles.remove(filePath);
    }
    
    if (playerService == null || !playerService.hasAudio()) return;
    if (filePath.equals(playerService.getAudioPath())) {
      EngineNative.setRepeat(repeat || playbackRepeat);
    }
  }
  
  private void updatePlayingStats() {
    logd("updatePlayingStats()");
    if (playerService == null || !playerService.hasAudio()) return;
    
    if (this.playingList.isEmpty()) {
      loge("playingList is empty");
      textPlayingPosition.setText("0/0");
      textFileExtraData.setText("0");
      return;
    }
    
    var currentTrack = getPlayingTrack();
    int currentPos = (currentTrack != null) ? this.playingList.indexOf(currentTrack): -1;
    
    String stats = String.format("%d/%d", currentPos + 1, this.playingList.size());
    textPlayingPosition.setText(stats);
    
    int bitrate = playerService.getBitrate() / 1000;
    String extraData = bitrate + (bitrate < 10000 ? " kbps": " k");
    textFileExtraData.setText(extraData);
  }
  
  private void updatePlayingTime(int playingTime, int totalTime) {
    if (playingTime == -1 || totalTime == -1) {
      loge("updatePlayingTime(): time is -1, playingTime: %d, totalTime: %d", playingTime, totalTime);
      return;
    }
    
    Fun.saveSharedPref(context, "PREF_LAST_AUDIO_TIME", playingTime);
    lastAudioTime = playingTime;
    
    String timePlaying;
    String timeLeft;
    String timeTotal;
    
    if (Vars.SHOW_TIME_MS) {
      int timeDiff = totalTime - playingTime;
      if (timeDiff < 0) timeDiff = 0;
      
      timePlaying = String.format("%03d.%03d",  playingTime / 1000, playingTime % 1000);
      timeLeft    = String.format("-%03d.%03d", timeDiff / 1000,    timeDiff % 1000);
      timeTotal   = String.format("%03d.%03d",  totalTime / 1000,   totalTime % 1000);
    }
    else {
      totalTime = totalTime / 1000 * 1000;
      playingTime = playingTime / 1000 * 1000;
      
      int timeDiff = totalTime - playingTime;
      if (timeDiff < 0) timeDiff = 0;
      
      timePlaying = Fun.formatTime(playingTime, false, false);
      timeLeft    = "-" + Fun.formatTime(timeDiff, false, false);
      timeTotal   = Fun.formatTime(totalTime, false, false);
    }
    
    textTimePlaying.setText(timePlaying);
    textTimeLeft.setText(timeLeft);
    textTimeTotal.setText(timeTotal);
  }
  
  private void loadExtraAudioInfo(String filePath) {
    logd("loadExtraAudioInfo(): " + filePath);
    
    if (filePath == null) {
      loge("filePath is null");
      return;
    }
    if (!Fun.fileExists(filePath)) {
      loge("File doesn't exist: " + filePath);
      return;
    }
    
    AudioInfo info = new AudioInfo();
    info.file = new File(filePath);

    var metadata = new MediaMetadataRetriever();
    try {
      metadata.setDataSource(filePath);
      
      info.title = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
      info.artist = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
      info.album = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
      info.year = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR);
      String bitrate = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE);
      info.bitrate = bitrate != null ? Integer.parseInt(bitrate): 0;
      String time = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
      info.time = time != null ? Integer.parseInt(time): 0;
    }
    catch (Exception e) {
      logw("Could not get audio metadata for: %s => %s", filePath, e);
    }
    finally {
      try {metadata.release();} catch (IOException e) {}
    }

    var mediaExtractor = new MediaExtractor();
    try {
      mediaExtractor.setDataSource(filePath);

      MediaFormat format = mediaExtractor.getTrackFormat(0);
      info.channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT, 0);
      info.frequency = format.getInteger(MediaFormat.KEY_SAMPLE_RATE, 0);
    }
    catch (Exception e) {
      logw("Could not get audio parameters for: %s => %s", filePath, e);
    }
    finally {
      mediaExtractor.release();
    }

    extraInfoIsForCurrentFile = (playerService != null && playerService.hasAudio() && playerService.getAudioPath().equals(filePath));

    info.codec = null;
    info.sampleFormat = null;
    if (extraInfoIsForCurrentFile) {
      info.codec = playerService.getCodecName();
      info.sampleFormat = playerService.getSampleFormat();
    }
    
    currentExtraInfo = info;
  }
  
  private void loadAudioImage() {
    if (currentExtraInfo == null || currentExtraInfo.file == null) {
      loge("Current audio info is null");
      return;
    }

    var metadata = new MediaMetadataRetriever();
    try {
      metadata.setDataSource(currentExtraInfo.file.getPath());
      
      byte[] pictureData = metadata.getEmbeddedPicture();
      if (pictureData != null) {
        Bitmap image = BitmapFactory.decodeByteArray(pictureData, 0, pictureData.length);
        currentExtraInfo.image = image;
        
        if (image != null) {
          currentExtraInfo.imageWidth = image.getWidth();
          currentExtraInfo.imageHeight = image.getHeight();
        }
        
        currentExtraInfo.imageSize = pictureData.length;
      }
    }
    catch (Exception e) {
      logw("The file doesn't contain image: " + e);
    }
    finally {
      try {metadata.release();} catch (IOException e) {}
    }
  }
  
  private void loadAudioLyrics() {
    if (currentExtraInfo == null || currentExtraInfo.file == null) {
      loge("Current audio info is null");
      return;
    }
    
    try {
      AudioFile tagger = AudioFileIO.read(currentExtraInfo.file);
      Tag tag = tagger.getTag();
      currentExtraInfo.lyrics = tag.getFirst(FieldKey.LYRICS);
    }
    catch (Exception e) {
      loge("Error retrieving lyrics: " + e);
    }
  }
  
  private boolean audioHasLyrics() {
    return currentExtraInfo != null && currentExtraInfo.lyrics != null && !currentExtraInfo.lyrics.isEmpty();
  }
  
  private void fillAudioInfo() {
    fillAudioInfo(currentExtraInfo);
  }
  
  private void fillAudioInfo(AudioInfo info) {
    if (info == null || info.file == null) {
      logw("Audio info is null, skipping the metadata update");
      return;
    }
    
    textExtraFileName.setText(info.file.getName());
    textExtraTitle.setText(info.title);
    textExtraArtist.setText(info.artist);
    textExtraAlbum.setText(info.album);
    textExtraYear.setText(info.year);
    textExtraBitrate.setText(info.bitrate / 1000 + " kbps");
    textExtraFrequency.setText(String.format("%.1f kHz", (float) info.frequency / 1000));
    textExtraSize.setText(Fun.formatSize(info.file.length()));
    textExtraPath.setText(info.file.getPath());
    textExtraLength.setText(Fun.formatTime(info.time, false, true));
    textExtraChannels.setText(String.valueOf(info.channels));
    
    textExtraCodec.setText(info.codec);
    textExtraSampleFormat.setText(info.sampleFormat);
    extraCodecBlock.setVisibility((info.codec != null) ? View.VISIBLE: View.GONE);
    extraSampleFormatBlock.setVisibility((info.sampleFormat != null) ? View.VISIBLE: View.GONE);
    
    textImageFileName.setText(info.file.getName());
    String imageInfo = String.format("%d x %d, %s", info.imageWidth, info.imageHeight, Fun.formatSize(info.imageSize));
    textImageInfo.setText(imageInfo);
    textImageInfo.setVisibility((info.image != null) ? View.VISIBLE: View.GONE);
    audioImage.setImageBitmap(info.image);
    
    textLyricsFileName.setText(info.file.getName());
    String lyrics = info.lyrics;
    textExtraLyrics.setText(lyrics);
    textLyricsPlaceholder.setVisibility((lyrics == null || lyrics.isEmpty()) ? View.VISIBLE: View.GONE);
  }
  
  private void updateAudioTrimText(int trimTime) {  // s
    String timeStr = "OFF";
    if (trimTime > 0) {
      timeStr = Fun.formatTime(trimTime * 1000, false, false);
    }
    textTrimValue.setText(timeStr);
  }
  
  private void onAudioTrimChanged(int trimTime) {  // s
    boolean trimEnabled = trimTime > 0;
    updateAudioTrimText(trimTime);
    
    int visibility = trimEnabled ? View.VISIBLE: View.GONE;
    if (playExtraIconTrim.getVisibility() != visibility) {
      playExtraIconTrim.setVisibility(visibility);
    }
  }
  
  private void disableAudioTrim() {
    this.audioTrimEnabled = false;
    progressSlider.restoreProgressColor();
  }
  
  private void enableAudioTrim() {
    this.audioTrimEnabled = true;
    progressSlider.changeProgressColor(trimmedProgressColor);
  }
  
  private void exitApp() {
    logd("exitApp()");
    finishAndRemoveTask();
  }
  
  private void initProgress(int time) {
    progressSlider.setMax(time);
    progressSlider.reset();
  }
  
  private void updateProgress(int time) {
    progressSlider.setProgress(time);
  }
  
  private void setupLooper() {
    if (playerService == null) return;
    int totalTime = playerService.getTotalTime();
    loopSlider.setMax(totalTime);
    loopSlider.reset();
  }
  
  private void disableLoop() {
    loopEnabled = false;
    bLooperEnable.setSelected(false);
    EngineNative.setLoop(false);
    playExtraIconLoop.setVisibility(View.GONE);
  }
  
  private void setPlayButtonDefault() {
    bPlayPause.setImageResource(R.drawable.baseline_play_arrow_black_36);
  }
  
  private void setPlayButtonAsPause() {
    bPlayPause.setImageResource(R.drawable.baseline_pause_black_36);
  }
  
  private void showExtraAudioInfo() {
    if (playerService == null || !playerService.hasAudio()) return;
    showExtraAudioInfo(playerService.getAudioPath());
  }
  
  private void showExtraAudioInfo(String filePath) {
    logd("showExtraAudioInfo()");
    if (filePath == null) return;
    
    if (!Fun.fileExists(filePath)) {
      loge("File doesn't exist: " + filePath);
      return;
    }
    
    loadExtraAudioInfo(filePath);
    fillAudioInfo();
    
    showMainInfoPanel();
    extraInfoPanel.setVisibility(View.VISIBLE);
  }
  
  private void showMainInfoPanel() {
    imageInfo.setVisibility(View.GONE);
    lyricsInfo.setVisibility(View.GONE);
    mainInfo.setVisibility(View.VISIBLE);
    mainScroll.scrollTo(0, 0);
  }
  
  private void showAudioImagePanel() {
    mainInfo.setVisibility(View.GONE);
    lyricsInfo.setVisibility(View.GONE);
    imageInfo.setVisibility(View.VISIBLE);
  }
  
  private void showLyricsPanel() {
    mainInfo.setVisibility(View.GONE);
    imageInfo.setVisibility(View.GONE);
    lyricsInfo.setVisibility(View.VISIBLE);
    lyricsScroll.scrollTo(0, 0);
  }
  
  private boolean extraPanelsVisible() {
    return trimAudioPanel.getVisibility() == View.VISIBLE ||
           looperPanel.getVisibility() == View.VISIBLE ||
           equalizerPanel.getVisibility() == View.VISIBLE ||
           extraInfoPanel.getVisibility() == View.VISIBLE;
  }
  
  private void hideExtraPanels() {
    if (trimAudioPanel.getVisibility() == View.VISIBLE) {
      trimAudioPanel.setVisibility(View.GONE);
      bTrimAudio.setSelected(false);
    }
    if (looperPanel.getVisibility() == View.VISIBLE) {
      looperPanel.setVisibility(View.GONE);
      bLoopSetup.setSelected(false);
    }
    if (equalizerPanel.getVisibility() == View.VISIBLE) {
      equalizerPanel.setVisibility(View.GONE);
      bEqualizer.setSelected(false);
    }
    if (extraControlPanel.getVisibility() == View.VISIBLE) {
      extraControlPanel.setVisibility(View.GONE);
    }
    if (extraInfoPanel.getVisibility() == View.VISIBLE) {
      extraInfoPanel.setVisibility(View.GONE);
    }
  }
  
  private void toggleExtraControlPanel() {
    logd("toggleExtraControlPanel()");
    int visibility = extraControlPanel.getVisibility() == View.GONE ? View.VISIBLE: View.GONE;
    extraControlPanel.setVisibility(visibility);
    
    if (visibility == View.GONE) {
      trimAudioPanel.setVisibility(View.GONE);
      bTrimAudio.setSelected(false);
      looperPanel.setVisibility(View.GONE);
      bLoopSetup.setSelected(false);
      equalizerPanel.setVisibility(View.GONE);
      bEqualizer.setSelected(false);
    }
    else {
      hideExtraInfoPanel();
    }
  }
  
  private void toggleTrimAudioPanel() {
    logd("toggleTrimAudioPanel()");
    int visibility = trimAudioPanel.getVisibility() == View.GONE ? View.VISIBLE: View.GONE;
    trimAudioPanel.setVisibility(visibility);
    
    if (visibility == View.VISIBLE) {
      looperPanel.setVisibility(View.GONE);
      bLoopSetup.setSelected(false);
      equalizerPanel.setVisibility(View.GONE);
      bEqualizer.setSelected(false);
    }
  }
  
  private void toggleLooperPanel() {
    logd("toggleLooperPanel()");
    int visibility = looperPanel.getVisibility() == View.GONE ? View.VISIBLE: View.GONE;
    looperPanel.setVisibility(visibility);
   
    if (visibility == View.VISIBLE) {
      trimAudioPanel.setVisibility(View.GONE);
      bTrimAudio.setSelected(false);
      equalizerPanel.setVisibility(View.GONE);
      bEqualizer.setSelected(false);
    }
  }
  
  private void toggleEqualizerPanel() {
    logd("toggleEqualizerPanel()");
    int visibility = equalizerPanel.getVisibility() == View.GONE ? View.VISIBLE: View.GONE;
 
    if (visibility == View.VISIBLE) {
      trimAudioPanel.setVisibility(View.GONE);
      bTrimAudio.setSelected(false);
      looperPanel.setVisibility(View.GONE);
      bLoopSetup.setSelected(false);
      
      extraControlPanel.post(() -> {
        int[] location = new int[2];
        extraControlPanel.getLocationOnScreen(location);
        int control_panel_y = location[1];

        statusPanel.getLocationOnScreen(location);
        int status_panel_y = location[1];
        
        int eqViewHeight = status_panel_y - control_panel_y - extraControlPanel.getHeight();
        if (eqViewHeight < 0) eqViewHeight = 0;
        log("Changing equalizerView height => " + eqViewHeight);
        
        // Dynamically set EQ view height to fill the remaining space between the control panel and status panel
        equalizerView.setLayoutParams(new LinearLayout.LayoutParams(equalizerView.getLayoutParams().width, eqViewHeight));
        
        equalizerPanel.setVisibility(visibility);
      });
    }
    else {
      equalizerPanel.setVisibility(visibility);
    }
  }
  
  private void toggleExtraInfoPanel() {
    int visibility = extraInfoPanel.getVisibility() == View.GONE ? View.VISIBLE: View.GONE;
    if (visibility == View.VISIBLE) {
      hideExtraPanels();
      showExtraAudioInfo();
    }
    else {
      hideExtraInfoPanel();
    }
  }
  
  private void hideExtraInfoPanel(boolean animate) {
    logd("hideExtraInfoPanel()");
    if (animate) {
      TranslateAnimation animation = new TranslateAnimation(0, 0, 0, -extraInfoPanel.getHeight());
      animation.setDuration(200);
      animation.setFillAfter(true);
      
      animation.setAnimationListener(new Animation.AnimationListener() {
        public void onAnimationEnd(Animation animation) {
          extraInfoPanel.setVisibility(View.GONE);
          extraInfoPanel.clearAnimation();
        }
        public void onAnimationRepeat(Animation animation) {}
        public void onAnimationStart(Animation animation) {}
      });
      extraInfoPanel.startAnimation(animation);
    }
    else {
      extraInfoPanel.setVisibility(View.GONE);
    }
  }
  
  private void hideExtraInfoPanel() {
    hideExtraInfoPanel(false);
  }
  
  private void resetCurrentDirTime() {
    textTotalTime.setText("");
  }
  
  private void resetPlayingDirTime() {
    textPlayingFolderTime.setText("00:00:00");
  }
  
  private void loadListTime() {
    loadDirectoryTimeTask.setList(fileList);
    
    loadDirectoryTimeTask.progress((pos, time) -> {
      if (pos >= fileList.size()) return;
      fileList.get(pos).time = Fun.formatTime(time, false, false);
      
      if (pos == listLayoutManager.findLastVisibleItemPosition() + 1 || pos == fileList.size() - 1 || fileList.get(pos).isCue) {
        filesAdapter.notifyDataSetChanged();
      }
    });
    
    loadDirectoryTimeTask.execute(time -> {
      textTotalTime.setText(Fun.formatTime(time, true, false));
      textTotalTime.setVisibility(time > 0 ? View.VISIBLE: View.GONE);
    });
  }
  
  private void updateDirStatus() {
    if (fileList == null) return;
    textTotalFiles.setText(String.valueOf(fileList.size()));
    
    long totalSize = fileList.stream()
      .filter(item -> item.isFile)
      .mapToLong(item -> new File(item.path).length())
      .reduce(0, Long::sum);
    
    textTotalSize.setText(Fun.formatSize(totalSize));
    textTotalSize.setVisibility(totalSize > 0 ? View.VISIBLE: View.GONE);
  }
  
  private void updateVolumeText() {
    int volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
    int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
    float volumePercent = (float) volume / maxVolume * 100;
    
    float equalizerGain = equalizerView.getMainGain();
    String gainAdjustment = "";
    if (equalizerGain != 0) {
      gainAdjustment = String.format("%+.1f dB ", equalizerGain);
    }
    
    String volumeLevel = String.format("%s%d%%", gainAdjustment, (int) volumePercent);
    textVolumeLevel.setText(volumeLevel);
  }
  
  private void updateVolumeLevel() {
    logd("updateVolumeLevel()");
    int volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
    volumeSlider.setProgress(volume);
    updateVolumeText();
  }
  
  private void updateWaveform(String audioPath) {
    if (currentWaveformFile != null && currentWaveformFile.equals(audioPath)) {
      log("The waveform is already built for the audio (%s)", audioPath);
      return;
    }
    
    AudioUtilsNative.waveformData = null;
    
    final int waveformWidth = progressSlider.getWaveformWidth();
    final int waveformHeight = progressSlider.getWaveformHeight();
    
    if (waveformWidth <= 0 || waveformHeight <= 0) {
      loge("Incorrect values for waveform (%d x %d)", waveformWidth, waveformHeight);
      return;
    }
    
    log("Building waveform for (%s) and size (%d x %d)", audioPath, waveformWidth, waveformHeight);
    AudioUtilsNative.cancelWaveform();
    
    Thread waveformDecodeThread = new Thread(() -> {
      synchronized (lock) {
        log("waveformDecodeThread started");
        currentWaveformFile = null;
        
        if (AudioUtilsNative.waveformData == null || AudioUtilsNative.waveformData.length != waveformWidth) {
          log("Creating new array for waveform data of size %d values for each pixel", waveformWidth);
          AudioUtilsNative.waveformData = new short[waveformWidth];
        }
        
        int result = AudioUtilsNative.buildWaveform(audioPath, waveformWidth, waveformHeight);
        
        if (result == 0 && AudioUtilsNative.waveformData != null) {
          log("Waveform build result (%s): %d, %d values", audioPath, result, AudioUtilsNative.waveformData.length);
          progressSlider.updateWaveform(AudioUtilsNative.waveformData);
          currentWaveformFile = audioPath;
        }
        else if (result < 0) {
          loge("Waveform build result (%s): %d", audioPath, result);
          AudioUtilsNative.waveformData = null;
        }
        
        log("waveformDecodeThread ended");
      }
    });
    
    progressSlider.clearWaveform();
    waveformDecodeThread.start();
  }
  
  private void validateActiveWaveform() {
    if (serviceBound && playerService.isPlayerLoaded()) {
      if (AudioUtilsNative.waveformData == null) {
        Fun.toast(this, "Rebuilding waveform on null");
        logw("Waveform data is null for playing file, but player is loaded. Rebuilding waveform.");
        progressSlider.post(() -> {
          updateWaveform(playerService.getAudioPath());
        });
      }
    }
  }
  

  // ---------------------- Classes ----------------------
  // T is ListItem or String
  private class LoadDirectoryTimeProcess<T> extends BackgroundProcess<Integer> {
    private List<T> list;
    private int listSize;
    private int totalTime;

    public LoadDirectoryTimeProcess(Executor executor, Handler handler) {
      super(executor, handler);
    }

    public void setList(List<T> list) {
      this.list = list;
    }
    
    private String getPath(int i) {
      T item = list.get(i);
      
      if (item instanceof ListItem) {
        ListItem _item = (ListItem) item;
        return _item.path;
      }
      
      if (item instanceof String) {
        return (String) item;
      }
      
      return null;
    }
    
    private boolean isProsessable(int i) {
      T item = list.get(i);
      
      if (item instanceof ListItem) {
        ListItem _item = (ListItem) item;
        return _item.isFile;
      }
      
      return true;
    }
    
    public int getResult() {
      return totalTime;
    }
    
    public int getListSize() {
      return listSize;
    }
    
    protected synchronized void run() {
      if (this.list == null) {
        loge("The list of files for total time calc is null");
        return;
      }
      
      this.listSize = this.list.size();
      
      String dir = "[empty]";
      if (this.list.size() > 0) {
        dir = Fun.getFolder(this.getPath(0));
        log("Start process loading total directory time: %s", dir);
      }
      
      this.running = true;
      this.totalTime = 0;
      
      List<T> items = new ArrayList<>(this.list);
      for (int i = 0; i < items.size(); i++) {
        if (!this.isProsessable(i)) continue;
        
        String file = this.getPath(i);
        int fileTime = extractAudioTime(file);
        this.totalTime += fileTime;
        
        if (this.onProgress != null) {
          final int pos = i;
          final int time = fileTime;
          handler.post(() -> this.onProgress.run(pos, time));
        }

        if (!this.running) {
          logw("Task is cancelled [%x]: %s", this.hashCode(), dir);
          break;
        }
      }
      
      if (this.running) {
        handler.post(() -> this.onFinished.run(totalTime));
      }
      
      this.running = false;
    }
  }
  
  
  private class VolumeReceiver extends BroadcastReceiver {
    public void onReceive(Context context, Intent intent) {
      try {
        if (intent.getIntExtra(EXTRA_VOLUME_STREAM_TYPE, 0) == AudioManager.STREAM_MUSIC) {
          int volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
          log("Volume changed => " + volume);
          updateVolumeLevel();
        }
      }
      catch (Exception e) {
        e.printStackTrace();
      }
    }
  }
  
  
  // --------------------
  @Override
  protected void onStop() {
    logd("MainActivity.onStop()");
    super.onStop();
  }
  
  @Override
  protected void onRestart() {
    logd("MainActivity.onRestart()");
    super.onRestart();
  }
  // --------------------
  
}
