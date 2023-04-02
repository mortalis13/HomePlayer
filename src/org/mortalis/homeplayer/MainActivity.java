package org.mortalis.homeplayer;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.provider.Settings;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.mortalis.homeplayer.components.ProgressSliderView;
import org.mortalis.homeplayer.components.VolumeSliderView;
import org.mortalis.homeplayer.decoder.DecoderNative;
import org.mortalis.homeplayer.decoder.DecoderResult;

import static org.mortalis.homeplayer.Fun.log;
import static org.mortalis.homeplayer.Fun.logd;
import static org.mortalis.homeplayer.Fun.loge;
import static org.mortalis.homeplayer.Fun.logw;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.stream.Stream;


public class MainActivity extends AppCompatActivity {

  private static final String ROOT_DIR_TITLE = "storage";
  private static final String VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION";
  private static final String EXTRA_VOLUME_STREAM_TYPE = "android.media.EXTRA_VOLUME_STREAM_TYPE";
  
  private static final File ROOT_STORAGE = Environment.getExternalStorageDirectory();
  private static final File START_DIR = new File(Environment.getExternalStorageDirectory(), "_music");
  
  private Context context;
  
  private PlayerService playerService;
  private ServiceConnection serviceConnection;
  private boolean serviceBound;
  
  private FilesAdapter filesAdapter;
  private LinearLayoutManager listLayoutManager;
  private List<ListItem> fileList;
  private File currentPath;
  
  private File[] playingList;
  private String lastFolder;
  private String lastAudio;
  private int lastAudioTime;
  private Set<String> favoritesList;
  private boolean playbackRepeat;
  
  private boolean playbackShuffle;
  private List<File> shuffleList;
  private Random randShuffle = new Random();
  
  private AudioInfo currentExtraInfo;
  
  private LoadCurrentDirTimeTask loadCurrentDirTimeTask;
  private LoadPLayingDirTimeTask loadPLayingDirTimeTask;
  private Queue<Integer> itemsQueue = new ArrayDeque<>(50);
  
  private AudioManager audioManager;
  private VolumeReceiver volumeReceiver = new VolumeReceiver();
  
  private Object lock = new Object();
  private Thread waveformDecodeThread;
  private String currentWaveformFile;
  
  // -- Views
  private HorizontalScrollView titleScroller;
  private TextView activeTitle;
  
  private ProgressSliderView progressSlider;
  private RecyclerView listItems;
  
  private TextView textTimeLeft;
  private TextView textTimePlaying;
  private TextView textTimeTotal;
  
  private TextView textCurrentFileSize;
  private TextView textPlayingPosition;
  private TextView textPlayingFolderTime;
  
  private ImageButton bPrevFile;
  private ImageButton bPlayPause;
  private ImageButton bNextFile;
  private ImageButton bFastRewind;
  private ImageButton bFastForward;
  private ImageView playExtraIconShuffle;
  private ImageView playExtraIconRepeat;
  
  private LinearLayout panelInfoLeft;
  private LinearLayout panelInfoCenter;
  private LinearLayout panelInfoRight;
  
  private RelativeLayout extraControlPanel;
  private ImageButton bShuffle;
  private ImageButton bRepeat;
  
  private LinearLayout extraInfoPanel;
  private TextView textExtraFileName;
  private TextView textExtraTitle;
  private TextView textExtraArtist;
  private TextView textExtraAlbum;
  private TextView textExtraYear;
  private TextView textExtraLength;
  private TextView textExtraBitrate;
  private TextView textExtraFrequency;
  private TextView textExtraChannels;
  private TextView textExtraSize;
  private TextView textExtraPath;
  
  private TextView textTotalFiles;
  private TextView textTotalSize;
  private TextView textTotalTime;
  private TextView textVolumeLevel;
  
  private VolumeSliderView volumeSlider;
  
  
  static {
    Fun.log("Loading Decoder native library");
    System.loadLibrary("decoder");
  }
  

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    logd("MainActivity.onCreate()");
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    
    context = this;
    
    requestAppPermissions(context);
    Fun.createNotificationChannel(context);
    
    init();
    configUI();
    restoreState();
    
    File dir = lastFolder == null ? START_DIR: new File(lastFolder);
    changeDir(dir);
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
    if (serviceBound && !playerService.isPlaying() && !playerService.hasProgress()) playerService.resetService();
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
    changeToParentDir();
  }
  
  
  // -----------------------------------------------------------
  private void bindPlayerService() {
    if (serviceBound) return;
    log("Binding PlayerService");
    Intent intent = new Intent(this, PlayerService.class);
    bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
  }
  
  private void requestAppPermissions(Context context) {
    if (Build.VERSION.SDK_INT < 30) {
      boolean isWriteGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
      
      if (!isWriteGranted) {
        requestPermissions(new String[] {
          Manifest.permission.WRITE_EXTERNAL_STORAGE
        }, Vars.APP_PERMISSION_REQUEST_ACCESS_EXTERNAL_STORAGE);
      }
    }
    else {
      if (!Environment.isExternalStorageManager()) {
        Uri uri = Uri.parse("package:" + BuildConfig.APPLICATION_ID);
        Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, uri);
        startActivity(intent);
      }
    }
  }
  
  private void init() {
    setVolumeControlStream(AudioManager.STREAM_MUSIC);
    audioManager = context.getSystemService(AudioManager.class);
    registerReceiver(volumeReceiver, new IntentFilter(VOLUME_CHANGED_ACTION));
    
    serviceConnection = new ServiceConnection() {
      public void onServiceConnected(ComponentName name, IBinder service) {
        logd("onServiceConnected()");
        
        var binder = (PlayerService.PlayerBinder) service;
        playerService = binder.getService();
        
        playerService.exitAction = () -> exitApp();
        playerService.progressSetupAction = (time) -> initProgress(time);
        playerService.progressUpdateAction = (time) -> updateProgress(time);
        playerService.timeUpdateAction = (time, timeTotal) -> updatePlayingTime(time, timeTotal);
        playerService.onPlayerPreloadedAction = () -> onPlayerPreloaded();
        playerService.onPlayerStartedAction = () -> onPlayerStarted();
        playerService.onPlayerPausedAction = () -> onPlayerPaused();
        playerService.onPlayerResumedAction = () -> onPlayerResumed();
        playerService.onPlayerStoppedAction = () -> onPlayerStopped();
        playerService.onPlayerErrorAction = () -> onPlayerError();
        playerService.onHeadphonesPlugAction = (state) -> onHeadphonesPlug(state);
        
        serviceBound = true;
        
        if (lastAudio != null) {
          log(String.format("lastAudio: %s; %d", lastAudio, lastAudioTime));
          preloadAudio(lastAudio, lastAudioTime);
        }
      }

      public void onServiceDisconnected(ComponentName name) {
        playerService = null;
        serviceBound = false;
      }
    };
    
    bindPlayerService();
  }
  
  private void configUI() {
    titleScroller = findViewById(R.id.titleScroller);
    activeTitle = findViewById(R.id.activeTitle);
    
    listItems = findViewById(R.id.listItems);
    progressSlider = findViewById(R.id.progressSlider);
    
    textTimePlaying = findViewById(R.id.textTimePlaying);
    textTimeLeft = findViewById(R.id.textTimeLeft);
    textTimeTotal = findViewById(R.id.textTimeTotal);
    
    textCurrentFileSize = findViewById(R.id.textCurrentFileSize);
    textPlayingPosition = findViewById(R.id.textPlayingPosition);
    textPlayingFolderTime = findViewById(R.id.textPlayingFolderTime);
    
    panelInfoLeft = findViewById(R.id.panelInfoLeft);
    panelInfoCenter = findViewById(R.id.panelInfoCenter);
    panelInfoRight = findViewById(R.id.panelInfoRight);
    
    extraControlPanel = findViewById(R.id.extraControlPanel);
    bShuffle = findViewById(R.id.bShuffle);
    bRepeat = findViewById(R.id.bRepeat);
    
    extraInfoPanel = findViewById(R.id.extraInfoPanel);
    textExtraFileName = findViewById(R.id.textExtraFileName);
    textExtraTitle = findViewById(R.id.textExtraTitle);
    textExtraArtist = findViewById(R.id.textExtraArtist);
    textExtraAlbum = findViewById(R.id.textExtraAlbum);
    textExtraYear = findViewById(R.id.textExtraYear);
    textExtraLength = findViewById(R.id.textExtraLength);
    textExtraBitrate = findViewById(R.id.textExtraBitrate);
    textExtraFrequency = findViewById(R.id.textExtraFrequency);
    textExtraChannels = findViewById(R.id.textExtraChannels);
    textExtraSize = findViewById(R.id.textExtraSize);
    textExtraPath = findViewById(R.id.textExtraPath);
    
    bPrevFile = findViewById(R.id.bPrevFile);
    bPlayPause = findViewById(R.id.bPlayPause);
    bNextFile = findViewById(R.id.bNextFile);
    bFastRewind = findViewById(R.id.bFastRewind);
    bFastForward = findViewById(R.id.bFastForward);
    
    playExtraIconShuffle = findViewById(R.id.playExtraIconShuffle);
    playExtraIconRepeat = findViewById(R.id.playExtraIconRepeat);
    
    textTotalFiles = findViewById(R.id.textTotalFiles);
    textTotalSize = findViewById(R.id.textTotalSize);
    textTotalTime = findViewById(R.id.textTotalTime);
    textVolumeLevel = findViewById(R.id.textVolumeLevel);
    
    volumeSlider = findViewById(R.id.volumeSlider);
    

    titleScroller.setSmoothScrollingEnabled(false);
    
    fileList = new ArrayList<>();
    filesAdapter = new FilesAdapter(fileList, this);
    
    filesAdapter.itemClickAction = (item) -> itemClick(item);
    filesAdapter.iconClickAction = (item) -> updateItemFavorite(item.path, item.isFavorite);
    filesAdapter.afterFileRemovedAction = (path) -> onItemRemoved(path);
    filesAdapter.infoClickAction = (path) -> showExtraAudioInfo(path);
    filesAdapter.itemBeforeBindAction = (position) -> {
      if (!itemsQueue.contains(position)) {
        itemsQueue.add(position);
      }
      else {
        logw("itemsQueue already contains pos " + position);
      }
    };
    
    listLayoutManager = new LinearLayoutManager(context) {
      public void onLayoutCompleted(final RecyclerView.State state) {
        // All visible items are shown and loaded
        super.onLayoutCompleted(state);
        if (!itemsQueue.isEmpty()) {
          new ProcessItemsQueueTask(fileList).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
      }
    };
    
    listItems.setAdapter(filesAdapter);
    listItems.setLayoutManager(listLayoutManager);
    listItems.addOnItemTouchListener(new RecyclerTouchListener(listItems));
    
    Typeface typeface = Typeface.createFromAsset(getAssets(), "fonts/consolas.ttf");
    textTimePlaying.setTypeface(typeface);
    textTimeLeft.setTypeface(typeface);
    textTimeTotal.setTypeface(typeface);
    textCurrentFileSize.setTypeface(typeface);
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
    
    bRepeat.setOnClickListener(v -> {
      v.setSelected(!v.isSelected());
      playbackRepeatAction();
    });
    
    
    bPrevFile.setOnClickListener(v -> playPrevFileAction());
    bPlayPause.setOnClickListener(v -> playPauseAction());
    bNextFile.setOnClickListener(v -> playNextFileAction());
    bFastRewind.setOnClickListener(v -> fastRewindAction());
    bFastForward.setOnClickListener(v -> fastForwardAction());
    
    extraInfoPanel.setOnTouchListener(new OnSwipeTouchListener(this) {
      public void onSwipeLeft() {
        if (currentExtraInfo != null && currentExtraInfo.file != null) {
          showExtraAudioInfo(Fun.getNextFilePath(currentExtraInfo.file));
        }
      }
      public void onSwipeRight() {
        if (currentExtraInfo != null && currentExtraInfo.file != null) {
          showExtraAudioInfo(Fun.getPrevFilePath(currentExtraInfo.file));
        }
      }
      public void onSwipeUp() {
        hideExtraInfoPanel(true);
      }
    });
    
    volumeSlider.setProgressChangeListener(value -> {
      audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0);
    });
    
    volumeSlider.setMax(audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
    updateVolumeLevel();
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
      
      cleanPrefs();
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }
  
  
  // ------------------------------ Actions ------------------------------
  private void playPauseAction() {
    if (playerService == null || !playerService.hasAudio()) return;
    
    if (playerService.isPlaying()) {
      playerService.pause();
    }
    else if (playerService.isPlayerLoaded()) {
      playerService.resume();
      setPlayButtonAsPause();
    }
    else {
      // If stopped or service is reset, restart audio
      playAudio(playerService.getAudioPath(), 0, true);
    }
  }
  
  private void playPrevFileAction() {
    if (playerService == null) return;
    boolean startPlayback = playerService.isPlaying();
    playPrevFile(startPlayback);
  }
  
  private void playNextFileAction() {
    if (playerService == null) return;
    boolean startPlayback = playerService.isPlaying();
    playNextFile(startPlayback);
  }
  
  private void fastRewindAction() {
    if (playerService == null || !playerService.isPlayerLoaded()) return;
    playerService.fastRewind(5);
  }
  
  private void fastForwardAction() {
    if (playerService == null || !playerService.isPlayerLoaded()) return;
    playerService.fastForward(5);
  }
  
  private void playbackShuffleAction() {
    playbackShuffle = !playbackShuffle;
    Fun.saveSharedPref(context, "PLAYBACK_SHUFFLE", playbackShuffle);
    playExtraIconShuffle.setVisibility(playbackShuffle ? View.VISIBLE: View.GONE);
    
    if (shuffleList != null) shuffleList.clear();
  }
  
  private void playbackRepeatAction() {
    playbackRepeat = !playbackRepeat;
    Fun.saveSharedPref(context, "PLAYBACK_REPEAT", playbackRepeat);
    
    playExtraIconRepeat.setVisibility(playbackRepeat ? View.VISIBLE: View.GONE);
    
    if (playerService == null || !playerService.isPlayerLoaded()) return;
    playerService.setRepeat(playbackRepeat);
  }
  
  
  // ------------------------------ Audio ------------------------------
  private void playAudio(String filePath, int time, boolean startPlayback) {
    logd("playAudio() \"" + filePath + "\"");
    if (!serviceBound || playerService == null) {
      loge("Player service is not initialized");
      return;
    }
    
    File playingFile = new File(filePath);
    if (!playingFile.exists()) {
      loge("The file does not exist: " + filePath);
      return;
    }
    
    progressSlider.reset();
    updateWaveform(filePath);
    
    processPlayingDirChange(playingFile);
    updateShuffleList(playingFile);
    
    Intent playerIntent = new Intent(this, PlayerService.class);
    playerIntent.putExtra(Vars.EXTRA_AUDIO_PATH, filePath);
    playerIntent.putExtra(Vars.EXTRA_AUDIO_TIME, time);
    playerIntent.putExtra(Vars.EXTRA_START_PLAYBACK, startPlayback);
    playerIntent.putExtra(Vars.EXTRA_PLAYBACK_REPEAT, playbackRepeat);
    
    startService(playerIntent);
    
    removeFromShuffleList(playingFile);
    
    Fun.saveSharedPref(context, "PREF_LAST_AUDIO", filePath);
    Fun.saveSharedPref(context, Vars.PREF_LAST_FILE_IN_FOLDER + playingFile.getParent(), filePath);

    markLastPlayedFile(currentPath);
    selectItem(filePath);
    
    if (!startPlayback) setPlayButtonDefault();
  }
  
  private void playAudio(String filePath, boolean startPlayback) {
    playAudio(filePath, 0, startPlayback);
  }
  
  private void preloadAudio(String filePath, int time) {
    logd("preloadAudio()");
    playAudio(filePath, time, false);
  }
  
  private void playNextFile(boolean startPlayback) {
    logd("playNextFile()");
    if (playerService == null || !playerService.hasAudio()) return;
    
    File currentFile = new File(playerService.getAudioPath());
    File file = null;
    if (playbackShuffle) {
      file = getNextRandomFile(currentFile);
    }
    
    if (file == null) {
      file = getNextPlaylistFile(currentFile);
    }
    
    if (file != null) {
      log("Next file: " + file);
      playAudio(file.getPath(), startPlayback);
    }
    else {
      loge("Next file is null");
    }
  }
  
  private void playPrevFile(boolean startPlayback) {
    logd("playPrevFile()");
    if (playerService == null || !playerService.hasAudio()) return;
    
    File currentFile = new File(playerService.getAudioPath());
    File file = getPrevPlaylistFile(currentFile);
    
    if (file != null) {
      log("Previous file: " + file);
      playAudio(file.getPath(), startPlayback);
    }
    else {
      loge("Previous file is null");
    }
  }
  
  
  // ------------------------------ Navigation ------------------------------
  private void changeDir(File path) {
    logd("changeDir(): " + path);
    if (loadCurrentDirTimeTask != null) loadCurrentDirTimeTask.cancel(true);
    
    fileList.clear();
    itemsQueue.clear();
    
    if (!path.exists()) path = ROOT_STORAGE;
    currentPath = path;
    
    File[] dirs = path.listFiles(Fun.dirFilter);
    Stream.of(dirs).sorted(Fun.nocaseComp)
      .forEach(file -> fileList.add(new ListItem(file.getName(), file.getAbsolutePath(), false)));
    
    File[] files = path.listFiles(Fun.fileFilter);
    Stream.of(files).sorted(Fun.nocaseComp)
      .forEach(file -> fileList.add(new ListItem(file.getName(), file.getAbsolutePath(), true)));
    
    filesAdapter.resetSelection();
    filesAdapter.notifyDataSetChanged();

    String title = currentPath.equals(ROOT_STORAGE) ? ROOT_DIR_TITLE: currentPath.getName();
    activeTitle.setText(title);
    
    titleScroller.fullScroll(View.FOCUS_LEFT);
    
    textTotalFiles.setText(String.valueOf(fileList.size()));
    
    long totalSize = Stream.of(files)
      .mapToLong(file -> file.length())
      .reduce(0, (total, size) -> total + size);
    textTotalSize.setText(Fun.formatSize(totalSize));
    
    loadCurrentDirTimeTask = new LoadCurrentDirTimeTask(fileList);
    loadCurrentDirTimeTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    
    updateListOverscroll();
    listLayoutManager.scrollToPositionWithOffset(0, 0);
    
    Fun.saveSharedPref(context, "PREF_LAST_FOLDER", path.getPath());
    
    selectPlayingDirOrFile();
    resetCurrentDirTime();

    markLastPlayedFile(currentPath);
    markFavorites();
  }
  
  private void changeToParentDir() {
    if (currentPath.equals(ROOT_STORAGE)) {
      log("In the root folder, cannot go to parent");
      listLayoutManager.scrollToPositionWithOffset(0, 0);
      return;
    }
    
    String prevPath = currentPath.getPath();
    changeDir(currentPath.getParentFile());
    
    int scrollPos = filesAdapter.getItemPosition(prevPath);
    listLayoutManager.scrollToPosition(scrollPos);
    
    hideExtraPanels();
  }
  
  private File getPlayingFile() {
    if (playerService == null || !playerService.hasAudio()) return null;
    File currentFile = new File(playerService.getAudioPath());
    return currentFile;
  }
  
  private boolean belongsToCurrentDir(File file) {
    if (file == null) return false;
    return file.getParentFile().equals(currentPath);
  }
  
  private void changeToPlayingDir() {
    logd("changeToPlayingDir()");
    
    File playingFile = getPlayingFile();
    if (playingFile == null) return;

    if (belongsToCurrentDir(playingFile)) {
      int scrollPos = filesAdapter.getItemPosition(playingFile.getPath());
      if (scrollPos != -1) {
        listLayoutManager.scrollToPosition(scrollPos);
      }
    }
    else {
      changeDir(playingFile.getParentFile());
    }
  }
  
  private void markLastPlayedFile(File dir) {
    String lastFile = Fun.getSharedPref(this, Vars.PREF_LAST_FILE_IN_FOLDER + dir.getPath());
    
    if (lastFile != null) {
      String lastFileName = new File(lastFile).getName();
      int lastTime = Fun.getSharedPrefInt(this, Vars.PREF_LAST_TIME_IN_FOLDER + dir.getPath());
      
      log(String.format("Last played file in dir '%s': '%s', Time: %d", dir, lastFileName, lastTime));
      filesAdapter.markLastPlayedItem(lastFile);
    }
  }
  
  private void markFavorites() {
    fileList.stream()
      .filter(item -> favoritesList.contains(item.path))
      .forEach(item -> filesAdapter.markAsFavorite(item.path));
  }
  
  
  // ------------------------------ Events ------------------------------
  private void onPlayerStarted() {
    onPlayerPreloaded();
    setPlayButtonAsPause();
  }
  
  private void onPlayerPreloaded() {
    progressSlider.enable();
    updatePlayingStats();
    if (extraInfoPanel.getVisibility() == View.VISIBLE) {
      showExtraAudioInfo();
    }
    
    if (progressSlider.atMaxProgress()) {
      progressSlider.disable();
    }
  }
  
  private void onPlayerPaused() {
    setPlayButtonDefault();
  }
  
  private void onPlayerResumed() {
    setPlayButtonAsPause();
  }
  
  private void onPlayerStopped() {
    if (!playbackShuffle && isPlayingLastFile()) {
      progressSlider.disable();
      setPlayButtonDefault();
    }
    else {
      playNextFile(true);
    }
  }
  
  private void onPlayerError() {
    resetPlayer();
    
    if (playerService == null) return;
    filesAdapter.markError(playerService.getAudioPath());
  }
  
  private void onHeadphonesPlug(int state) {
    updateVolumeLevel();
  }
  
  private void onItemRemoved(String filePath) {
    log("File removed: " + filePath);
    changeDir(currentPath);
    
    // Check if the removed file was in the current playlist
    File playingFile = getPlayingFile();
    
    if (belongsToCurrentDir(playingFile)) {
      if (playingFile.getPath().equals(filePath)) {
        log("Playing file has been deleted, selecting next file");
        playNextFile(false);
      }
      
      reloadPlayingListForDir(playingFile.getParentFile());
      updatePlayingStats();
      
      if (playingList.length == 0) {
        log("Playing list is empty, resetting the player UI");
        if (playerService != null) {
          playerService.stop();
          playerService.resetService();
          playerService.stopForeground(true);
        }
        
        progressSlider.clearWaveform();
        resetPlayer();
      }
    }
  }
  
  
  // ------------------------------ Audio Utils ------------------------------
  private File getPrevPlaylistFile(File file) {
    return getPrevNextFile(file, false);
  }
  
  private File getNextPlaylistFile(File file) {
    return getPrevNextFile(file, true);
  }
  
  private File getPrevNextFile(File file, boolean next) {
    int len = playingList.length;
    for (int i = 0; i < len; i++) {
      if (playingList[i].equals(file)) {
        if (next) {
          return playingList[i == len-1 ? 0: i+1];
        }
        return file = playingList[i == 0 ? len-1: i-1];
      }
    }
    
    return null;
  }
  
  private File getNextRandomFile(File file) {
    logd("getNextRandomFile(): " + file);
    
    if (shuffleList == null || shuffleList.size() == 0) {
      generateShuffleList(file);
    }
    
    if (shuffleList == null || shuffleList.size() == 0) {
      return null;
    }
    
    int nextId = randShuffle.nextInt(shuffleList.size());
    file = shuffleList.get(nextId);
    return file;
  }
  
  private void selectPlayingDirOrFile() {
    if (playerService == null || !playerService.hasAudio()) return;
    selectItem(playerService.getAudioPath());
  }
  
  private void selectItem(String filePath) {
    int playingItemPos = filesAdapter.getPositionForSubpath(filePath);
    
    if (playingItemPos != -1) {
      log("Selecting item or its folder: " + filePath);
      filesAdapter.selectItem(playingItemPos);
    }
  }
  
  private boolean isPlayingLastFile() {
    if (playerService == null || !playerService.hasAudio()) return true;
    File lastFile = playingList[playingList.length - 1];
    return playerService.getAudioPath().equals(lastFile.getPath());
  }
  
  private int extractAudioTime(String filePath) {
    int time = 0;
    
    try {
      MediaExtractor mediaExtractor = new MediaExtractor();
      mediaExtractor.setDataSource(filePath);

      MediaFormat format = mediaExtractor.getTrackFormat(0);
      long duration = format.getLong(MediaFormat.KEY_DURATION);
      time = (int) (duration / 1000);
      mediaExtractor.release();
    }
    catch (Exception e) {
      logw("Could not load media extractor for: " + filePath);
    }
    
    return time;
  }
  
  private void generateShuffleList(File audioFile) {
    logd("generateShuffleList(): " + audioFile);
    if (playingList == null) return;
    shuffleList = new ArrayList<>(Arrays.asList(playingList));
    removeFromShuffleList(audioFile);
  }
  
  private void removeFromShuffleList(File audioFile) {
    if (!playbackShuffle) return;
    logd("removeFromShuffleList(): " + audioFile);
    if (shuffleList == null) return;

    shuffleList.remove(audioFile);
  }
  
  private void updateShuffleList(File audioFile) {
    if (!playbackShuffle) return;
    logd("updateShuffleList(): " + audioFile);
    
    String currentAudioPath = playerService.getAudioPath();
    if (currentAudioPath == null || !audioFile.getParent().equals(new File(currentAudioPath).getParent())) {
      generateShuffleList(audioFile);
    }
  }
  
  private void resetPlayer() {
    progressSlider.reset();
    progressSlider.disable();
    setPlayButtonDefault();
    
    updatePlayingStats();
    updatePlayingTime(0, 0);
  }
  
  
  // ------------------------------ Utils ------------------------------
  private void cachePlayingList(File dir) {
    logd("cachePlayingList(): " + dir);
    playingList = dir.listFiles(Fun.fileFilter);
    Arrays.sort(playingList, Fun.nocaseComp);
  }
  
  private void reloadPlayingListForDir(File dir) {
    cachePlayingList(dir);
    resetPlayingDirTime();
    
    if (loadPLayingDirTimeTask != null) loadPLayingDirTimeTask.cancel(true);
    loadPLayingDirTimeTask = new LoadPLayingDirTimeTask(playingList);
    loadPLayingDirTimeTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
  }
  
  private void itemClick(ListItem item) {
    try {
      hideExtraPanels();
      
      File clickedFile = new File(item.path);
      if (clickedFile.isDirectory()) {
        changeDir(clickedFile);
      }
      else {
        int time = 0;
        if (item.isLastPlayed) {
          if (playerService != null && !playerService.getAudioPath().equals(item.path)) {
            int lastTime = Fun.getSharedPrefInt(this, Vars.PREF_LAST_TIME_IN_FOLDER + clickedFile.getParent());
            if (lastTime != -1) time = lastTime;
          }
        }
        
        playAudio(item.path, time, true);
      }
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }
  
  private void processPlayingDirChange(File newAudioFile) {
    logd("processPlayingDirChange(): " + newAudioFile);
    if (playerService == null) return;
    
    if (playerService.hasAudio()) {
      var currentAudio = new File(playerService.getAudioPath());
      var currentAudioParent = currentAudio.getParent();
      var isDirectoryChanged = !newAudioFile.getParent().equals(currentAudioParent);
      
      if (isDirectoryChanged) {
        log("Directory changed");
        Fun.saveSharedPref(context, Vars.PREF_LAST_TIME_IN_FOLDER + currentAudioParent, lastAudioTime);
        log(String.format("Saved %d to TIME_%s", lastAudioTime, currentAudioParent));
        
        cachePlayingList(newAudioFile.getParentFile());
        resetPlayingDirTime();
        if (loadCurrentDirTimeTask != null) loadCurrentDirTimeTask.copyToPlayingDirTime();
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
          log(String.format("Removing prefs for dir '%s', as dir or file doesn't exist", dir));
          
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
    
    for (String favPath: listCopy) {
      if (!new File(favPath).exists()) {
        favoritesList.remove(favPath);
      }
    }
    
    Fun.saveSharedPref(context, "PREF_FAVORITES_LIST", favoritesList);
  }
  
  private void updateItemFavorite(String filePath, boolean isFavorite) {
    if (isFavorite) {
      favoritesList.add(filePath);
    }
    else {
      favoritesList.remove(filePath);
    }
    Fun.saveSharedPref(context, "PREF_FAVORITES_LIST", favoritesList);
  }
  
  private void updatePlayingStats() {
    if (playerService == null || !playerService.hasAudio()) return;
    File playingFile = new File(playerService.getAudioPath());
    
    int playingItemPos = -1;
    for (int i = 0; i < playingList.length; i++) {
      if (playingFile.equals(playingList[i])) {
        playingItemPos = i;
        break;
      }
    }

    String stats = String.format("%d/%d", playingItemPos + 1, playingList.length);
    textPlayingPosition.setText(stats);
    
    String fileSize = Fun.formatSize(playingFile.length());
    textCurrentFileSize.setText(fileSize);
  }
  
  private void updatePlayingTime(int playingPos, int totalTime) {
    if (playingPos == -1 || totalTime == -1) {
      loge(String.format("updatePlayingTime(): time is -1, playingPos: %d, totalTime: %d", playingPos, totalTime));
      return;
    }
    
    Fun.saveSharedPref(context, "PREF_LAST_AUDIO_TIME", playingPos);
    lastAudioTime = playingPos;
    
    playingPos /= 1000;
    totalTime  /= 1000;
    String timePlaying = Fun.formatTime(playingPos, false);
    String timeTotal   = Fun.formatTime(totalTime, false);
    int timeDiff = totalTime - playingPos;
    if (timeDiff < 0) timeDiff = 0;
    String timeLeft    = "-" + Fun.formatTime(timeDiff, false);
    
    textTimePlaying.setText(timePlaying);
    textTimeLeft.setText(timeLeft);
    textTimeTotal.setText(timeTotal);
  }
  
  private void showExtraAudioInfo() {
    if (playerService == null || !playerService.hasAudio()) return;
    showExtraAudioInfo(playerService.getAudioPath());
  }
  
  private void showExtraAudioInfo(String filePath) {
    logd("showExtraAudioInfo()");
    
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
    
    try {
      MediaMetadataRetriever metadata = new MediaMetadataRetriever();
      metadata.setDataSource(filePath);
      
      info.title = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
      info.artist = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
      info.album = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
      info.year = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR);
      info.bitrate = Integer.parseInt(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)) / 1000;
      info.frequency = Integer.parseInt(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE));
      info.time = Integer.parseInt(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
    
      MediaExtractor mediaExtractor = new MediaExtractor();
      mediaExtractor.setDataSource(filePath);
      
      MediaFormat format = mediaExtractor.getTrackFormat(0);
      info.channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
    }
    catch (Exception e) {
      loge("Could not get audio metadata for: " + filePath);
    }
    
    fillAudioInfo(info);
    extraInfoPanel.setVisibility(View.VISIBLE);
    currentExtraInfo = info;
  }
  
  private void fillAudioInfo(AudioInfo info) {
    textExtraFileName.setText(info.file.getName());
    textExtraTitle.setText(info.title);
    textExtraArtist.setText(info.artist);
    textExtraAlbum.setText(info.album);
    textExtraYear.setText(info.year);
    textExtraBitrate.setText(info.bitrate + " kbps");
    textExtraFrequency.setText(String.format("%.1f kHz", (float) info.frequency / 1000));
    textExtraSize.setText(Fun.formatSize(info.file.length()));
    textExtraPath.setText(info.file.getPath());
    textExtraLength.setText(Fun.formatTime(info.time / 1000, false));
    textExtraChannels.setText(String.valueOf(info.channels));
  }
  
  private void exitApp() {
    logd("exitApp()");
    finishAndRemoveTask();
  }
  
  private void initProgress(int time) {
    progressSlider.reset();
    progressSlider.setMax(time);
  }
  
  private void updateProgress(int time) {
    progressSlider.setProgress(time);
  }
  
  private void setPlayButtonDefault() {
    bPlayPause.setImageResource(R.drawable.baseline_play_arrow_black_36);
  }
  
  private void setPlayButtonAsPause() {
    bPlayPause.setImageResource(R.drawable.baseline_pause_black_36);
  }
  
  private void hideExtraPanels() {
    if (extraControlPanel.getVisibility() == View.VISIBLE) extraControlPanel.setVisibility(View.GONE);
    if (extraInfoPanel.getVisibility() == View.VISIBLE)  extraInfoPanel.setVisibility(View.GONE);
  }
  
  private void toggleExtraControlPanel() {
    int visibility = extraControlPanel.getVisibility() == View.GONE ? View.VISIBLE: View.GONE;
    extraControlPanel.setVisibility(visibility);
  }
  
  private void toggleExtraInfoPanel() {
    if (extraInfoPanel.getVisibility() == View.GONE) {
      showExtraAudioInfo();
    }
    else {
      hideExtraInfoPanel();
    }
  }
  
  private void hideExtraInfoPanel(boolean animate) {
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
  
  private void updateListOverscroll() {
    listItems.post(() -> {
      int lastPos = listLayoutManager.findLastCompletelyVisibleItemPosition();
      int mode = (lastPos == filesAdapter.getItemCount() - 1) ? View.OVER_SCROLL_NEVER: View.OVER_SCROLL_IF_CONTENT_SCROLLS;
      listItems.setOverScrollMode(mode);
    });
  }
  
  private void resetCurrentDirTime() {
    textTotalTime.setText("00:00:00");
  }
  
  private void resetPlayingDirTime() {
    textPlayingFolderTime.setText("00:00:00");
  }
  
  private void updateVolumeLevel() {
    int volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
    int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
    float volumePercent = (float) volume / maxVolume * 100;
   
    String volumeLevel = String.format("%d%%", (int) volumePercent);
    textVolumeLevel.setText(volumeLevel);
    
    volumeSlider.setProgress(volume);
  }
  
  private void updateWaveform(String audioPath) {
    if (currentWaveformFile != null && currentWaveformFile.equals(audioPath)) {
      log(String.format("The waveform is already built for the audio %s", audioPath));
      return;
    }
    
    int sliderWidth = progressSlider.getWaveformWidth();
    int sliderHeight = progressSlider.getWaveformHeight();
    
    if (sliderWidth <= 0 || sliderHeight <= 0) {
      loge(String.format("Incorrect values for waveform %d x %d", sliderWidth, sliderHeight));
      return;
    }
    
    log(String.format("Updating waveform for \"%s\" and size %d x %d", audioPath, sliderWidth, sliderHeight));
    
    DecoderNative.stopDecoding();
    if (waveformDecodeThread != null) waveformDecodeThread.interrupt();
    
    waveformDecodeThread = new Thread(() -> {
      synchronized (lock) {
        if (Thread.interrupted()) return;
        
        DecoderResult result = DecoderNative.decodeSamples(audioPath, sliderWidth, sliderHeight);
        log("Decode result: " + result);
        
        if (result == null) return;
        if (Thread.interrupted()) return;
        
        progressSlider.updateWaveform(result.samples);
        currentWaveformFile = audioPath;
      }
    });
    
    waveformDecodeThread.start();
    progressSlider.clearWaveform();
  }
  

  // ---------------------- Classes ----------------------
  private class LoadCurrentDirTimeTask extends AsyncTask<Void, Void, Void> {
    private List<ListItem> items;
    private int totalTime;
    private boolean updatePlayingDir;
    
    public LoadCurrentDirTimeTask(List<ListItem> items) {
      this.items = List.copyOf(items);
    }
    
    public boolean isRunning() {
      return getStatus() == AsyncTask.Status.RUNNING;
    }
    
    public void copyToPlayingDirTime() {
      if (isRunning()) {
        updatePlayingDir = true;
      }
      else {
        String folderTime = Fun.formatTime(totalTime / 1000, true);
        textPlayingFolderTime.setText(folderTime);
      }
    }
    
    protected Void doInBackground(Void... params) {
      for (var item: items) {
        if (!item.isFile) continue;
        
        int time = extractAudioTime(item.path);
        totalTime += time;
        item.time = Fun.formatTime(time / 1000, false);
        
        if (isCancelled()) {
          logw("Task is cancelled: " + this);
          break;
        }
      }

      return null;
    }
    
    protected void onPostExecute(Void result) {
      String folderTime = Fun.formatTime(totalTime / 1000, true);
      textTotalTime.setText(folderTime);
      
      if (updatePlayingDir) {
        textPlayingFolderTime.setText(folderTime);
      }
    }
  }
  

  private class LoadPLayingDirTimeTask extends AsyncTask<Void, Void, Void> {
    private File[] files;
    private int totalTime;
    
    public LoadPLayingDirTimeTask(File[] files) {
      this.files = files;
    }
    
    protected Void doInBackground(Void... params) {
      for (var file: files) {
        if (!file.isFile()) continue;
        
        int time = extractAudioTime(file.getPath());
        totalTime += time;
        
        if (isCancelled()) {
          logw("Task is cancelled: " + this);
          break;
        }
      }

      return null;
    }
    
    protected void onPostExecute(Void result) {
      String folderTime = Fun.formatTime(totalTime / 1000, true);
      textPlayingFolderTime.setText(folderTime);
    }
  }
  

  private class ProcessItemsQueueTask extends AsyncTask<Void, Void, Void> {
    private List<ListItem> items;
    
    public ProcessItemsQueueTask(List<ListItem> items) {
      this.items = List.copyOf(items);
    }
    
    protected Void doInBackground(Void... params) {
      while (!itemsQueue.isEmpty()) {
        int pos = itemsQueue.remove();
        if (pos >= this.items.size()) continue;
        
        ListItem item = this.items.get(pos);
        
        if (item.isFile) {
          int time = extractAudioTime(item.path);
          item.time = Fun.formatTime(time / 1000, false);
        }
      }
      
      return null;
    }
    
    protected void onPostExecute(Void result) {
      itemsQueue.clear();
      filesAdapter.notifyDataSetChanged();
    }
  }
  
  
  private class VolumeReceiver extends BroadcastReceiver {
    public void onReceive(Context context, Intent intent) {
      if (intent.getAction().equals(VOLUME_CHANGED_ACTION) && intent.getIntExtra(EXTRA_VOLUME_STREAM_TYPE, 0) == AudioManager.STREAM_MUSIC) {
        log("Volume changed");
        updateVolumeLevel();
      }
    }
  }
  
  
  // --------------------
  @Override
  protected void onPause() {
    logd("MainActivity.onPause()");
    super.onPause();
  }
  
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
