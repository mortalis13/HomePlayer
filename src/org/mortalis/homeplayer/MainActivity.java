package org.mortalis.homeplayer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileDescriptor;
import java.io.FileDescriptor;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Arrays;
import java.util.Set;
import java.util.Random;
import java.util.Queue;
import java.util.ArrayDeque;
import java.util.stream.Stream;

import android.os.Environment;
import android.net.Uri;
import android.provider.Settings;
import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.IntentFilter;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.graphics.drawable.Animatable;
import androidx.vectordrawable.graphics.drawable.Animatable2Compat.AnimationCallback;
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat;
import androidx.appcompat.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.text.Editable;
import android.text.InputType;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.method.NumberKeyListener;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.content.BroadcastReceiver;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.widget.ArrayAdapter;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.ImageButton;
import android.view.WindowManager;
import androidx.core.content.ContextCompat;
import android.Manifest;
import android.content.pm.PackageManager;
import android.app.NotificationManager;
import android.view.MotionEvent;
import android.widget.HorizontalScrollView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.animation.TranslateAnimation;
import android.view.animation.Animation;
import android.animation.AnimatorListenerAdapter;

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.SparseBooleanArray;
import android.graphics.Typeface;
import android.media.MediaFormat;
import android.media.MediaExtractor;
import android.graphics.PorterDuff;
import android.graphics.Outline;
import android.view.ViewOutlineProvider;
import android.os.AsyncTask;

import org.mortalis.homeplayer.components.SliderView;
import org.mortalis.homeplayer.components.SimplePaintView;


public class MainActivity extends AppCompatActivity {

  private static final String ROOT_DIR_TITLE = "storage";
  private static final String VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION";
  private static final File ROOT_STORAGE = Environment.getExternalStorageDirectory();
  private static final File START_DIR = new File(Environment.getExternalStorageDirectory(), "_music");
  
  private Context context;
  
  private PlayerService playerService;
  private ServiceConnection serviceConnection;
  private boolean serviceBound;
  
  private FilesAdapter filesAdapter;
  private LinearLayoutManager listLayoutManager;
  private List<ListItem> fileList;
  private List<AudioInfo> dirAudioData;
  private List<AudioInfo> playingDirAudioData;
  
  private File currentPath;
  private File previouslyPlayedFile;
  private int scrollPos;
  
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
  
  // -- Views
  private HorizontalScrollView titleScroller;
  private TextView activeTitle;
  
  private SliderView progressSlider;
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
  

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    Fun.logd("MainActivity.onCreate()");
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
    Fun.logd("MainActivity.onStart()");
    super.onStart();
  }
  
  @Override
  protected void onResume() {
    Fun.logd("MainActivity.onResume()");
    super.onResume();
    bindPlayerService();
  }
  
  @Override
  protected void onDestroy() {
    Fun.logd("MainActivity.onDestroy()");
    super.onDestroy();

    if (serviceBound) {
      unbindService(serviceConnection);
    }
    if (playerService != null) {
      Intent playerIntent = new Intent(this, PlayerService.class);
      playerService.stopService(playerIntent);
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
    Fun.log("Binding PlayerService");
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
      public void onServiceConnected(ComponentName className, IBinder service) {
        PlayerService.PlayerBinder binder = (PlayerService.PlayerBinder) service;
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
        
        serviceBound = true;
        
        if (lastAudio != null) {
          Fun.log(String.format("lastAudio: %s; %d", lastAudio, lastAudioTime));
          preloadAudio(lastAudio, lastAudioTime);
        }
      }

      public void onServiceDisconnected(ComponentName arg0) {
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
    
    
    titleScroller.setSmoothScrollingEnabled(false);
    
    fileList = new ArrayList<>();
    filesAdapter = new FilesAdapter(fileList, this);
    
    filesAdapter.itemClickAction = (item) -> itemClick(item);
    filesAdapter.iconClickAction = (item) -> updateItemFavorite(item.path, item.isFavorite);
    filesAdapter.afterFileRemovedAction = (path) -> refreshCurrentDir();
    filesAdapter.infoClickAction = (path) -> showExtraAudioInfo(path);
    filesAdapter.itemBeforeBindAction = (position) -> {
      if (!itemsQueue.contains(position)) {
        itemsQueue.add(position);
      }
      else {
        Fun.logw("itemsQueue already contains pos " + position);
      }
    };
    
    listItems.setAdapter(filesAdapter);
    
    listLayoutManager = new LinearLayoutManager(context) {
      public void onLayoutCompleted(final RecyclerView.State state) {
        // All visible items are shown and loaded
        super.onLayoutCompleted(state);
        Fun.log("-- onLayoutCompleted");
        if (!itemsQueue.isEmpty()) {
          new ProcessItemsQueueTask(fileList).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
      }
    };
    listItems.setLayoutManager(listLayoutManager);
    
    Typeface typeface = Typeface.createFromAsset(getAssets(), "fonts/consolas.ttf");
    textTimePlaying.setTypeface(typeface);
    textTimeLeft.setTypeface(typeface);
    textTimeTotal.setTypeface(typeface);
    textCurrentFileSize.setTypeface(typeface);
    textPlayingPosition.setTypeface(typeface);
    textPlayingFolderTime.setTypeface(typeface);
    
    activeTitle.setOnClickListener(v -> {
      changeToParentDir();
    });
    
    progressSlider.setProgressChangeListener(new SliderView.ProgressChangeListener() {
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
    });
    
    
    panelInfoLeft.setOnClickListener(v -> {
      toggleExtraControlPanel();
    });
    
    panelInfoCenter.setOnClickListener(v -> {
      toggleExtraInfoPanel();
    });
    
    panelInfoRight.setOnClickListener(v -> {
      changeToPlayingDir();
    });
    
    
    bShuffle.setOnClickListener(v -> {
      v.setSelected(!v.isSelected());
      playbackShuffleAction();
    });
    
    bRepeat.setOnClickListener(v -> {
      v.setSelected(!v.isSelected());
      playbackRepeatAction();
    });
    
    
    bPrevFile.setOnClickListener(v -> {
      playPrevFileAction();
    });
    
    bPlayPause.setOnClickListener(v -> {
      playPauseAction();
    });
    
    bNextFile.setOnClickListener(v -> {
      playNextFileAction();
    });
    
    bFastRewind.setOnClickListener(v -> {
      fastRewindAction();
    });
    
    bFastForward.setOnClickListener(v -> {
      fastForwardAction();
    });
    
    extraInfoPanel.setOnTouchListener(new OnSwipeTouchListener(this) {
      public void onSwipeLeft() {
        if (currentExtraInfo != null && currentExtraInfo.file != null) {
          showExtraAudioInfo(getNextFile(currentExtraInfo.file).getPath());
        }
      }
      public void onSwipeRight() {
        if (currentExtraInfo != null && currentExtraInfo.file != null) {
          showExtraAudioInfo(getPrevFile(currentExtraInfo.file).getPath());
        }
      }
      public void onSwipeUp() {
        hideExtraInfoPanel(true);
      }
    });
    
    updateVolumeLevel();
  }
  
  private void restoreState() {
    try {
      lastFolder = Fun.getSharedPref(context, "PREF_LAST_FOLDER");
      Fun.log("PREF lastFolder: " + lastFolder);
      
      lastAudio = Fun.getSharedPref(context, "PREF_LAST_AUDIO");
      Fun.log("PREF lastAudio: " + lastAudio);
      
      lastAudioTime = Fun.getSharedPrefInt(context, "PREF_LAST_AUDIO_TIME");
      if (lastAudioTime == -1) lastAudioTime = 0;
      Fun.log("PREF lastAudioTime: " + lastAudioTime);
      
      favoritesList = Fun.getSharedPrefList(context, "PREF_FAVORITES_LIST");
      // Fun.log("PREF favoritesList: " + favoritesList);
      cleanFavorites();
      
      playbackRepeat = Fun.getSharedPrefBool(context, "PLAYBACK_REPEAT");
      Fun.log("PREF playbackRepeat: " + playbackRepeat);
      bRepeat.setSelected(playbackRepeat);
      playExtraIconRepeat.setVisibility(playbackRepeat ? View.VISIBLE: View.GONE);
      
      playbackShuffle = Fun.getSharedPrefBool(context, "PLAYBACK_SHUFFLE");
      Fun.log("PREF playbackShuffle: " + playbackShuffle);
      bShuffle.setSelected(playbackShuffle);
      playExtraIconShuffle.setVisibility(playbackShuffle ? View.VISIBLE: View.GONE);
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
      // If stopped
      playerService.restartAudio();
    }
  }
  
  private void playPrevFileAction() {
    if (playerService == null || !playerService.isPlayerLoaded()) return;
    boolean startPlayback = playerService.isPlaying();
    playPrevFile(startPlayback);
  }
  
  private void playNextFileAction() {
    if (playerService == null || !playerService.isPlayerLoaded()) return;
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
    Fun.logd("playAudio()");
    File playingFile = new File(filePath);
    if (!playingFile.exists()) {
      Fun.loge("The file does not exist: " + filePath);
      return;
    }
    
    updateShuffleList(playingFile);
    
    processPlayingDirChange(playingFile);
    
    Intent playerIntent = new Intent(this, PlayerService.class);
    playerIntent.putExtra(Vars.EXTRA_AUDIO_PATH, filePath);
    playerIntent.putExtra(Vars.EXTRA_AUDIO_TIME, time);
    playerIntent.putExtra(Vars.EXTRA_START_PLAYBACK, startPlayback);
    playerIntent.putExtra(Vars.EXTRA_PLAYBACK_REPEAT, playbackRepeat);
    
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      startForegroundService(playerIntent);
    }
    else {
      startService(playerIntent);
    }
    
    removeFromShuffleList(playingFile);
    
    Fun.saveSharedPref(context, "PREF_LAST_AUDIO", playingFile.getPath());
    Fun.saveSharedPref(context, "FILE_" + playingFile.getParent(), playingFile.getPath());
    markLastPlayedFile(currentPath);
  }
  
  private void playAudio(String filePath, boolean startPlayback) {
    playAudio(filePath, 0, startPlayback);
  }
  
  private void preloadAudio(String filePath, int time) {
    Fun.logd("preloadAudio()");
    playAudio(filePath, time, false);
  }
  
  private void playNextFile(boolean startPlayback) {
    Fun.logd("playNextFile()");
    if (playerService == null || !playerService.hasAudio()) return;
    
    File currentFile = new File(playerService.getAudioPath());
    File file = null;
    if (playbackShuffle) {
      file = getNextRandomFile(currentFile);
    }
    
    if (file == null) {
      file = getNextFile(currentFile);
    }
    
    if (file != null) {
      Fun.log("Next file: " + file);
      playAudio(file.getPath(), startPlayback);
    }
    else {
      Fun.loge("Next file is null");
    }
  }
  
  private void playPrevFile(boolean startPlayback) {
    Fun.logd("playPrevFile()");
    if (playerService == null || !playerService.hasAudio()) return;
    
    File currentFile = new File(playerService.getAudioPath());
    File file = getPrevFile(currentFile);
    
    if (file != null) {
      Fun.log("Previous file: " + file.toString());
      playAudio(file.getPath(), startPlayback);
    }
    else {
      Fun.loge("Previous file is null");
    }
  }
  
  
  // ------------------------------ Navigation ------------------------------
  private void changeDir(File path) {
    Fun.logd("changeDir(): " + path);
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
      Fun.log("In the root folder, cannot go to parent");
      return;
    }
    
    String prevPath = currentPath.getPath();
    changeDir(currentPath.getParentFile());
    
    int scrollPos = filesAdapter.getItemPosition(prevPath);
    listLayoutManager.scrollToPosition(scrollPos);
    
    hideExtraPanels();
  }
  
  private void changeToPlayingDir() {
    Fun.logd("changeToPlayingDir()");
    if (playerService == null || !playerService.hasAudio()) return;
    File currentFile = new File(playerService.getAudioPath());
    
    if (currentFile.getParentFile().equals(currentPath)) {
      int scrollPos = filesAdapter.getItemPosition(currentFile.getPath());
      if (scrollPos != -1) {
        listLayoutManager.scrollToPosition(scrollPos);
      }
    }
    else {
      changeDir(currentFile.getParentFile());
    }
  }
  
  private void refreshCurrentDir() {
    changeDir(currentPath);
  }
  
  private void markLastPlayedFile(File dir) {
    String lastFile = Fun.getSharedPref(this, "FILE_" + dir.getPath());
    int lastTime = Fun.getSharedPrefInt(this, "TIME_" + dir.getPath());

    Fun.log(String.format("Last played file in dir '%s': '%s'. Time: %d", dir, lastFile, lastTime));
    filesAdapter.markLastPlayedItem(lastFile);
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
    selectPlayingDirOrFile();
    if (extraInfoPanel.getVisibility() == View.VISIBLE) {
      showExtraAudioInfo();
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
  
  
  // ------------------------------ Audio Utils ------------------------------
  private File getPrevFile(File file) {
    return getPrevNextFile(file, false);
  }
  
  private File getNextFile(File file) {
    return getPrevNextFile(file, true);
  }
  
  private File getPrevNextFile(File file, boolean next) {
    File parent = file.getParentFile();
    if (!parent.exists()) {
      Fun.loge("The parent does not exist for file " + file);
      return null;
    }
    
    int len = playingList.length;
    for (int i = 0; i < len; i++) {
      if (playingList[i].getName().equals(file.getName())) {
        if (next) {
          file = playingList[i == len-1 ? 0: i+1];
        }
        else {
          file = playingList[i == 0 ? len-1: i-1];
        }
        
        return file;
      }
    }
    
    return null;
  }
  
  private File getNextRandomFile(File file) {
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
    int playingItemPos = filesAdapter.getPositionForSubpath(playerService.getAudioPath());
    
    if (playingItemPos != -1) {
      Fun.log("Selecting playing folder or file: " + fileList.get(playingItemPos));
      filesAdapter.selectItem(playingItemPos);
    }
    else {
      Fun.loge("Playing file or folder position is -1");
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
      e.printStackTrace();
    }
    
    return time;
  }
  
  private void generateShuffleList(File audioFile) {
    Fun.logd("generateShuffleList(): " + audioFile);
    shuffleList = new ArrayList<>(Arrays.asList(playingList));
    removeFromShuffleList(audioFile);
  }
  
  private void removeFromShuffleList(File audioFile) {
    if (!playbackShuffle) return;
    Fun.logd("removeFromShuffleList(): " + audioFile);
    if (shuffleList == null) return;

    shuffleList.remove(audioFile);
  }
  
  private void updateShuffleList(File audioFile) {
    if (!playbackShuffle) return;
    Fun.logd("updateShuffleList(): " + audioFile);
    
    String currentAudioPath = playerService.getAudioPath();
    if (currentAudioPath == null || !audioFile.getParent().equals(new File(currentAudioPath).getParent())) {
      generateShuffleList(audioFile);
    }
  }
  
  
  // ------------------------------ Utils ------------------------------
  private void cachePlayingList(File file) {
    playingList = file.getParentFile().listFiles(Fun.fileFilter);
    Arrays.sort(playingList, Fun.nocaseComp);
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
        if (item.isLastPlayed && !item.path.equals(playerService.getAudioPath())) {
          int lastTime = Fun.getSharedPrefInt(this, "TIME_" + clickedFile.getParent());
          if (lastTime != -1) time = lastTime;
        }
        
        playAudio(item.path, time, true);
      }
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }
  
  private void processPlayingDirChange(File newAudioFile) {
    Fun.logd("processPlayingDirChange(): " + newAudioFile);
    
    if (playerService.hasAudio()) {
      var currentAudio = new File(playerService.getAudioPath());
      var currentAudioParent = currentAudio.getParent();
      var isDirectoryChanged = !newAudioFile.getParent().equals(currentAudioParent);
      
      if (isDirectoryChanged) {
        Fun.log("Directory changed");
        Fun.saveSharedPref(context, "TIME_" + currentAudioParent, lastAudioTime);
        Fun.log(String.format("Saved %d time to TIME_%s", lastAudioTime, currentAudioParent));
        
        cachePlayingList(newAudioFile);
        resetPlayingDirTime();
        if (loadCurrentDirTimeTask != null) loadCurrentDirTimeTask.copyToPlayingDirTime();
      }
    }
    else {
      cachePlayingList(newAudioFile);
      resetPlayingDirTime();
      
      if (loadPLayingDirTimeTask != null) loadPLayingDirTimeTask.cancel(true);
      loadPLayingDirTimeTask = new LoadPLayingDirTimeTask(playingList);
      loadPLayingDirTimeTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
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
    File playingFile = new File(playerService.getAudioPath());
    int playingItemPos = Arrays.binarySearch(playingList, playingFile);

    if (playingItemPos != -1) {
      String stats = String.format("%d/%d", playingItemPos + 1, playingList.length);
      textPlayingPosition.setText(stats);
    }
    
    String fileSize = Fun.formatSize(playingFile.length());
    textCurrentFileSize.setText(fileSize);
  }
  
  private void updatePlayingTime(int playingPos, int totalTime) {
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
    textTimeTotal.setText(timeTotal);
    textTimeLeft.setText(timeLeft);
  }
  
  private void showExtraAudioInfo() {
    if (playerService == null || !playerService.hasAudio()) return;
    showExtraAudioInfo(playerService.getAudioPath());
  }
  
  private void showExtraAudioInfo(String filePath) {
    Fun.logd("showExtraAudioInfo()");
    
    if (!Fun.fileExists(filePath)) {
      Fun.loge("File doesn't exist: " + filePath);
      return;
    }
    
    AudioInfo info = new AudioInfo();
    info.file = new File(filePath);
    
    MediaMetadataRetriever metadata = new MediaMetadataRetriever();
    metadata.setDataSource(filePath);
    
    info.title = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
    info.artist = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
    info.album = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
    info.year = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR);
    info.bitrate = Integer.parseInt(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)) / 1000;
    info.frequency = Integer.parseInt(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE));
    info.time = Integer.parseInt(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
    
    try {
      MediaExtractor mediaExtractor = new MediaExtractor();
      mediaExtractor.setDataSource(filePath);
      
      MediaFormat format = mediaExtractor.getTrackFormat(0);
      info.channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
    }
    catch (Exception e) {
      e.printStackTrace();
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
    Fun.logd("exitApp()");
    finishAndRemoveTask();
  }
  
  private void initProgress(int time) {
    progressSlider.setMax(time);
    progressSlider.setProgress(0);
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
          Fun.logw("Task is cancelled: " + this);
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
          Fun.logw("Task is cancelled: " + this);
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
      if (intent.getAction().equals(VOLUME_CHANGED_ACTION)) {
        Fun.log("Volume changed");
        updateVolumeLevel();
      }
    }
  }
  
  
  // --------------------
  @Override
  protected void onPause() {
    Fun.logd("MainActivity.onPause()");
    super.onPause();
  }
  
  @Override
  protected void onStop() {
    Fun.logd("MainActivity.onStop()");
    super.onStop();
  }
  
  @Override
  protected void onRestart() {
    Fun.logd("MainActivity.onRestart()");
    super.onStop();
  }
  // --------------------
  
}
