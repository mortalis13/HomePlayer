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
import android.view.GestureDetector;
import androidx.core.content.ContextCompat;
import androidx.core.view.GestureDetectorCompat;
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

  private static final int ITEM_LAYOUT = R.layout.browser_list_item;
  private static final String ROOT_DIR_TITLE = "storage";
  private static final File ROOT_STORAGE = Environment.getExternalStorageDirectory();
  private static final File START_DIR = new File(Environment.getExternalStorageDirectory(), "_music");
  
  private int item_icon_color_default;
  private int item_icon_color_lastplayed;
  
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
  
  private String lastFolder;
  private String lastAudio;
  private int lastAudioTime;
  private Set<String> favoritesList;
  private boolean playbackRepeat;
  
  private boolean playbackShuffle;
  private List<File> shuffleList;
  private Random randShuffle = new Random();
  
  private GestureDetector gestureDetector;
  private boolean itemSwipedLeft;
  private boolean itemSwiping;
  
  private AudioInfo currrentExtraInfo;
  
  private LoadCurrentDirTimeTask loadCurrentDirTimeTask;
  private LoadPLayingDirTimeTask loadPLayingDirTimeTask;
  private Queue<Integer> itemsQueue = new ArrayDeque<>(50);
  
  // -- Views
  private HorizontalScrollView titleScroller;
  private TextView activeTitle;
  
  private SliderView progressSlider;
  private RecyclerView listItems;
  
  private TextView textTimeLeft;
  private TextView textTimePlaying;
  private TextView textTimeTotal;
  
  private TextView textCurrentFolderTime;
  private TextView textPlayingStats;
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
  

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    Fun.logd("MainActivity.onCreate()");
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    
    context = this;
    MainService.init(this);
    
    requestAppPermissions(context);
    Fun.createNotificationChannel(context);
    
    init();
    configUI();
    restoreState();
  }
  
  @Override
  protected void onStart() {
    Fun.logd("MainActivity.onStart()");
    super.onStart();
    MainService.init(this);
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
    
    item_icon_color_default = ContextCompat.getColor(context, R.color.list_item_icon);
    item_icon_color_lastplayed = ContextCompat.getColor(context, R.color.list_item_is_last_played_file);
    
    gestureDetector = new GestureDetector(context, new ListSwipeManager());
    
    serviceConnection = new ServiceConnection() {
      public void onServiceConnected(ComponentName className, IBinder service) {
        PlayerService.PlayerBinder binder = (PlayerService.PlayerBinder) service;
        playerService = binder.getService();
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
    
    textCurrentFolderTime = findViewById(R.id.textCurrentFolderTime);
    textPlayingStats = findViewById(R.id.textPlayingStats);
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
    
    
    titleScroller.setSmoothScrollingEnabled(false);
    
    fileList = new ArrayList<>();
    filesAdapter = new FilesAdapter(fileList);
    listItems.setAdapter(filesAdapter);
    
    listLayoutManager = new LinearLayoutManager(context) {
      public void onLayoutCompleted(final RecyclerView.State state) {
        // All visible items are shown and loaded
        super.onLayoutCompleted(state);
        Fun.log("-- onLayoutCompleted");
        if (!itemsQueue.isEmpty()) {
          new LoadItemsInfoTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
      }
    };
    listItems.setLayoutManager(listLayoutManager);
    
    Typeface typeface = Typeface.createFromAsset(getAssets(), "fonts/consolas.ttf");
    textTimePlaying.setTypeface(typeface);
    textTimeLeft.setTypeface(typeface);
    textTimeTotal.setTypeface(typeface);
    textCurrentFolderTime.setTypeface(typeface);
    textPlayingStats.setTypeface(typeface);
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
        if (currrentExtraInfo != null && currrentExtraInfo.file != null) {
          showExtraAudioInfo(getNextFile(currrentExtraInfo.file).getPath());
        }
      }
      public void onSwipeRight() {
        if (currrentExtraInfo != null && currrentExtraInfo.file != null) {
          showExtraAudioInfo(getPrevFile(currrentExtraInfo.file).getPath());
        }
      }
      public void onSwipeUp() {
        hideExtraInfoPanel(true);
      }
    });
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
      
      File dir = lastFolder == null ? START_DIR: new File(lastFolder);
      changeDir(dir);
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }
  
  
  // ------------------------------ Actions ------------------------------
  public void playPauseAction() {
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
  
  public void playPrevFileAction() {
    if (playerService == null || !playerService.isPlayerLoaded()) return;
    boolean startPlayback = playerService.isPlaying();
    playPrevFile(startPlayback);
  }
  
  public void playNextFileAction() {
    if (playerService == null || !playerService.isPlayerLoaded()) return;
    boolean startPlayback = playerService.isPlaying();
    playNextFile(startPlayback);
  }
  
  public void fastRewindAction() {
    if (playerService == null || !playerService.isPlayerLoaded()) return;
    playerService.fastRewind(5);
  }
  
  public void fastForwardAction() {
    if (playerService == null || !playerService.isPlayerLoaded()) return;
    playerService.fastForward(5);
  }
  
  public void playbackShuffleAction() {
    playbackShuffle = !playbackShuffle;
    Fun.saveSharedPref(context, "PLAYBACK_SHUFFLE", playbackShuffle);
    playExtraIconShuffle.setVisibility(playbackShuffle ? View.VISIBLE: View.GONE);
    
    if (shuffleList != null) shuffleList.clear();
  }
  
  public void playbackRepeatAction() {
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
    
    if (playerService.hasAudio()) {
      var playingParent = new File(playerService.getAudioPath()).getParent();
      var isSameDirectory = playingFile.getParent().equals(playingParent);
      if (!isSameDirectory) {
        Fun.saveSharedPref(context, "TIME_" + playingParent, lastAudioTime);
        Fun.log(String.format("Saved %d time to TIME_%s", lastAudioTime, playingParent));
      }
    }
    
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
    
    String title = currentPath.equals(ROOT_STORAGE) ? ROOT_DIR_TITLE: currentPath.getName();
    activeTitle.setText(title);
    
    titleScroller.fullScroll(View.FOCUS_LEFT);

    filesAdapter.resetSelection();
    filesAdapter.notifyDataSetChanged();
    
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
  public void onPlayerStarted() {
    onPlayerPreloaded();
    setPlayButtonAsPause();
  }
  
  public void onPlayerPreloaded() {
    progressSlider.enable();
    updatePlayingStats();
    if (extraInfoPanel.getVisibility() == View.VISIBLE) {
      showExtraAudioInfo();
    }
  }
  
  public void onPlayerPaused() {
    setPlayButtonDefault();
  }
  
  public void onPlayerResumed() {
    setPlayButtonAsPause();
  }
  
  public void onPlayerStopped() {
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
    
    File[] files = parent.listFiles(Fun.fileFilter);
    Arrays.sort(files, Fun.nocaseComp);
    
    int len = files.length;
    for (int i = 0; i < len; i++) {
      if (files[i].getName().equals(file.getName())) {
        if (next) {
          file = files[i == len-1 ? 0: i+1];
        }
        else {
          file = files[i == 0 ? len-1: i-1];
        }
        
        return file;
      }
    }
    
    return null;
  }
  
  public File getNextRandomFile(File file) {
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
    
    File currentFile = new File(playerService.getAudioPath());
    File[] files = currentFile.getParentFile().listFiles(Fun.fileFilter);
    Arrays.sort(files, Fun.nocaseComp);
    
    int len = files.length;
    if (len == 0) return true;
    
    return currentFile.equals(files[len-1]);
  }
  
  public int extractAudioTime(String filePath) {
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
    File parent = audioFile.getParentFile();
    
    if (!parent.exists()) {
      Fun.loge("The parent does not exist for file " + audioFile);
      return;
    }
    
    File[] files = parent.listFiles(Fun.fileFilter);
    Arrays.sort(files, Fun.nocaseComp);
    
    shuffleList = new ArrayList<>(Arrays.asList(files));
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
  private void resetCurrentDirTime() {
    textCurrentFolderTime.setText("00:00:00");
  }
  
  private void updatePlayingStats() {
    File playingFile = new File(playerService.getAudioPath());
    File[] files = playingFile.getParentFile().listFiles(Fun.fileFilter);
    Arrays.sort(files, Fun.nocaseComp);
    
    int playingItemPos = Arrays.binarySearch(files, playingFile);

    if (playingItemPos != -1) {
      String stats = String.format("%d/%d", playingItemPos + 1, files.length);
      textPlayingStats.setText(stats);
    }
    
    if (playingFile.getParentFile().equals(currentPath)) {
      filesAdapter.selectItem(playingItemPos);
    }
    else {
      selectPlayingDirOrFile();
    }
    
    loadPlayingDirTime(files);
  }
  
  public void loadPlayingDirTime(File[] files) {
    textPlayingFolderTime.setText("00:00:00");
    
    File playingFile = new File(playerService.getAudioPath());
    if (playingFile.getParentFile().equals(currentPath) && loadCurrentDirTimeTask != null) {
      // Reuse already calculated time value or request to copy that value when the current dir task is completed
      loadCurrentDirTimeTask.copyToPlayingDirTime();
    }
    else {
      if (loadPLayingDirTimeTask != null) loadPLayingDirTimeTask.cancel(true);
      loadPLayingDirTimeTask = new LoadPLayingDirTimeTask(files);
      loadPLayingDirTimeTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }
  }
  
  public void updatePlayingTime(int playingPos, int totalTime) {
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
    currrentExtraInfo = info;
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
  
  public void initProgress(int time) {
    progressSlider.setMax(time);
    progressSlider.setProgress(0);
  }
  
  public void updateProgress(int time) {
    progressSlider.setProgress(time);
  }
  
  public void setPlayButtonDefault() {
    bPlayPause.setImageResource(R.drawable.baseline_play_arrow_black_36);
  }
  
  public void setPlayButtonAsPause() {
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
  
  public void exitApp() {
    Fun.logd("exitApp()");
    finishAndRemoveTask();
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
  
  private void updateListOverscroll() {
    listItems.post(() -> {
      int lastPos = listLayoutManager.findLastCompletelyVisibleItemPosition();
      int mode = (lastPos == filesAdapter.getItemCount() - 1) ? View.OVER_SCROLL_NEVER: View.OVER_SCROLL_IF_CONTENT_SCROLLS;
      listItems.setOverScrollMode(mode);
    });
  }
  

  // ---------------------- Classes ----------------------
  private class FilesAdapter extends RecyclerView.Adapter<FilesAdapter.ItemViewHolder> {
    private List<ListItem> fileList;
    
    private int lastItemSelectedPos = -1;
    private int selectedItemPos = -1;
    
    private int holderWithMenu = -1;
    
    public FilesAdapter(List<ListItem> fileList) {
      this.fileList = fileList;
    }
    
    @Override
    public ItemViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
      Context context = parent.getContext();
      LayoutInflater inflater = LayoutInflater.from(context);
      
      View rootView = inflater.inflate(ITEM_LAYOUT, parent, false);
      
      ItemViewHolder viewHolder = new ItemViewHolder(rootView);
      return viewHolder;
    }
    
    @Override
    public void onBindViewHolder(ItemViewHolder holder, int position) {
      ListItem item = this.fileList.get(position);
      
      if (item.isFile && item.time == null) {
        if (!itemsQueue.contains(position)) {
          itemsQueue.add(position);
        }
        else {
          Fun.logw("itemsQueue already contains pos " + position);
        }
      }
      
      holder.bind(item);
      if (position == selectedItemPos) {
        holder.select();
      }
      else {
        holder.unselect();
      }
    }
    
    @Override
    public int getItemCount() {
      return this.fileList.size();
    }
    
    private int getDirsCount() {
      int result = 0;
      for (ListItem item: this.fileList) if (!item.isFile) result ++; else break;
      return result;
    }
    
    public void resetSelection() {
      lastItemSelectedPos = -1;
      selectedItemPos = -1;
    }
    
    public int getPositionForSubpath(String filePath) {
      // Finds position of the file in the current list, if the curretn directory path is a subpath of the file
      if (filePath == null) return -1;
      int size = this.fileList.size();
      if (size == 0) return -1;
      
      // Check if current directory is not a subpath of the requested file
      String listParent = new File(this.fileList.get(0).path).getParent();
      if (listParent != null && !filePath.startsWith(listParent)) return -1;
      
      for (int i = 0; i < size; i++) {
        if (this.fileList.get(i).path.equals(filePath)) {
          return i;
        }
      }
      return getPositionForSubpath(new File(filePath).getParent());
    }
    
    public void selectItem(int itemPos) {
      selectedItemPos = itemPos;
      if (lastItemSelectedPos == -1) {
        lastItemSelectedPos = selectedItemPos;
      }
      else {
        notifyItemChanged(lastItemSelectedPos);
        lastItemSelectedPos = selectedItemPos;
      }
      
      notifyItemChanged(selectedItemPos);
    }
    
    public void markLastPlayedItem(String filePath) {
      if (filePath == null) return;
      
      for (int i = 0; i < this.fileList.size(); i++) {
        ListItem item = this.fileList.get(i);
        if (!item.isFile) continue;
      
        if (item.path.equals(filePath)) {
          item.isLastPlayed = true;
          notifyItemChanged(i);
        }
        else if (item.isLastPlayed) {
          item.isLastPlayed = false;
          notifyItemChanged(i);
        }
      }
    }
    
    public void markAsFavorite(String filePath) {
      for (int i = 0; i < this.fileList.size(); i++) {
        ListItem item = this.fileList.get(i);
        if (!item.isFile) continue;
        if (item.path.equals(filePath)) {
          item.isFavorite = true;
          notifyItemChanged(i);
        }
      }
    }
    
    public int getItemPosition(String path) {
      for (int i = 0; i < this.fileList.size(); i++) {
        ListItem item = this.fileList.get(i);
        if (item.path.equals(path)) return i;
      }
      return -1;
    }
    
    // private void hideActiveMenu(int currentPos) {
    //   if (holderWithMenu != -1) {
    //     int pos = holderWithMenu;
    //     holderWithMenu = -1;
        
    //     ItemViewHolder viewHolder = (ItemViewHolder) listItems.findViewHolderForAdapterPosition(pos);
    //     if (viewHolder != null) {
    //       viewHolder.hideItemMenu();
    //     }
        
    //     if (pos != currentPos) {
    //       notifyItemChanged(pos);
    //     }
    //   }
    // }
    
    
    public class ItemViewHolder extends RecyclerView.ViewHolder {
      ImageView itemIcon;
      SimplePaintView itemIndicator;
      TextView itemText;
      TextView itemTime;
      FrameLayout iconContainer;
      RelativeLayout itemMenuPanel;
      
      ImageButton bRemoveFile;
      ImageButton bFileInfo;
      
      ListItem item;
      
      boolean isRemovePressed;
      
      public ItemViewHolder(View rootView) {
        super(rootView);
        
        itemIcon = rootView.findViewById(R.id.itemIcon);
        itemIndicator = rootView.findViewById(R.id.itemIndicator);
        itemText = rootView.findViewById(R.id.itemText);
        itemTime = rootView.findViewById(R.id.itemTime);
        iconContainer = rootView.findViewById(R.id.iconContainer);
        
        itemMenuPanel = rootView.findViewById(R.id.itemMenuPanel);
        bRemoveFile = rootView.findViewById(R.id.bRemoveFile);
        bFileInfo = rootView.findViewById(R.id.bFileInfo);
        
        iconContainer.setOnClickListener(v -> {
          this.item.isFavorite = !this.item.isFavorite;
          itemIndicator.setVisibility(this.item.isFavorite ? View.VISIBLE: View.GONE);
          updateItemFavorite(this.item.path, this.item.isFavorite);
        });
        
        bRemoveFile.setOnClickListener(v -> {
          if (!isRemovePressed) {
            setRemoveState();
          }
          else {
            if (Fun.removeFile(this.item.path)) {
              refreshCurrentDir();
            }
            else {
              resetRemoveState();
            }
          }
        });
        
        bFileInfo.setOnClickListener(v -> {
          showExtraAudioInfo(this.item.path);
          hideItemMenu();
        });
        
        itemView.setOnTouchListener((view, event) -> {
          if (this.item.isFile) {
            float x = event.getX();
            float y = view.getTop() + event.getY();
            event = MotionEvent.obtain(event.getDownTime(), event.getEventTime(), event.getAction(), x, y, event.getMetaState());
            
            boolean result = gestureDetector.onTouchEvent(event);
            if (result) return true;
          }
          
          return this.processOnTouch(view, event);
        });
      }
      
      private boolean processOnTouch(View view, MotionEvent event) {
        if (this.item == null) return false;
        int action = event.getAction();
        
        // if (action == MotionEvent.ACTION_DOWN) Fun.log("ACTION_DOWN");
        // else if (action == MotionEvent.ACTION_CANCEL) Fun.log("ACTION_CANCEL");
        // else if (action == MotionEvent.ACTION_UP) Fun.log("ACTION_UP");
        
        if (action == MotionEvent.ACTION_DOWN) {
          view.setPressed(true);
          hideActiveMenu();
        }
        else if (action == MotionEvent.ACTION_CANCEL) {
          view.setPressed(false);
          itemSwipedLeft = false;
          itemSwiping = false;
        }
        else if (action == MotionEvent.ACTION_UP) {
          view.setPressed(false);
          
          if (itemSwipedLeft) {
            showItemMenu();
            holderWithMenu = getBindingAdapterPosition();
          }
          else if (!itemSwiping) {
            itemClick(this.item);
          }
          
          itemSwipedLeft = false;
          itemSwiping = false;
        }
        
        return true;
      }
      
      private void hideActiveMenu() {
        int currentPos = getBindingAdapterPosition();
        
        if (holderWithMenu != -1) {
          int pos = holderWithMenu;
          holderWithMenu = -1;
          hideItemMenu();
          
          if (pos != currentPos) {
            notifyItemChanged(pos);
          }
        }
      }
      
      public void select() {
        itemView.setSelected(true);
        itemView.setPressed(false);
      }
      
      public void unselect() {
        itemView.setSelected(false);
        itemView.setPressed(false);
      }
      
      private void showItemMenu() {
        resetRemoveState();
        
        if (itemMenuPanel == null) return;
        if (itemMenuPanel.getVisibility() != View.VISIBLE) {
          itemMenuPanel.setVisibility(View.VISIBLE);
          
          float menuWidth = Fun.dpToPx(141);
          TranslateAnimation animation = new TranslateAnimation(menuWidth, 0, 0, 0);
          animation.setDuration(150);
          itemMenuPanel.startAnimation(animation);
        }
      }
      
      private void hideItemMenu() {
        if (itemMenuPanel == null) return;
        if (itemMenuPanel.getVisibility() != View.GONE) {
          itemMenuPanel.setVisibility(View.GONE);
        }
      }
      
      public void setRemoveState() {
        isRemovePressed = true;
        bRemoveFile.setBackgroundResource(R.color.remove_file_confirm);
      }
      
      public void resetRemoveState() {
        isRemovePressed = false;
        bRemoveFile.setBackgroundResource(R.color.list_item_button_background_default);
      }
      
      public void bind(ListItem item) {
        hideItemMenu();
        resetRemoveState();
        
        this.item = item;
        if (item == null) return;
        
        iconContainer.setClickable(item.isFile);
        itemIndicator.setVisibility(item.isFavorite ? View.VISIBLE: View.GONE);
        
        itemText.setText(item.text);
        itemTime.setText(item.time);
        itemTime.setVisibility(item.isFile ? View.VISIBLE: View.GONE);
        
        int iconColor = item_icon_color_default;
        if (item.isLastPlayed) iconColor = item_icon_color_lastplayed;
        itemIcon.setImageResource(item.icon);
        itemIcon.setColorFilter(iconColor);
      }
    } // ItemViewHolder
  }
  
  
  private class LoadCurrentDirTimeTask extends AsyncTask<Void, Void, Void> {
    List<ListItem> items;
    int totalTime;
    boolean updatePlayingDir;
    
    public LoadCurrentDirTimeTask(List<ListItem> items) {
      this.items = List.copyOf(items);
    }
    
    public void copyToPlayingDirTime() {
      if (getStatus() == AsyncTask.Status.RUNNING) {
        updatePlayingDir = true;
      }
      else {
        String folderTime = Fun.formatTime(totalTime / 1000, true);
        textPlayingFolderTime.setText(folderTime);
      }
    }
    
    protected Void doInBackground(Void... params) {
      for (int i = 0; i < items.size(); i++) {
        ListItem item = items.get(i);
        if (!item.isFile) continue;
        
        int time = extractAudioTime(item.path);
        totalTime += time;
        item.time = Fun.formatTime(time / 1000, false);
        
        if (isCancelled()) {
          Fun.logw("Task is cancelled: " + hashCode());
          break;
        }
      }

      return null;
    }
    
    protected void onPostExecute(Void result) {
      String folderTime = Fun.formatTime(totalTime / 1000, true);
      textCurrentFolderTime.setText(folderTime);
      if (updatePlayingDir) {
        textPlayingFolderTime.setText(folderTime);
      }
    }
  }
  

  private class LoadPLayingDirTimeTask extends AsyncTask<Void, Void, Void> {
    File[] files;
    int totalTime;
    
    public LoadPLayingDirTimeTask(File[] files) {
      this.files = files;
    }
    
    protected Void doInBackground(Void... params) {
      for (int i = 0; i < files.length; i++) {
        File file = files[i];
        if (!file.isFile()) continue;
        
        int time = extractAudioTime(file.getPath());
        totalTime += time;
        
        if (isCancelled()) {
          Fun.logw("Task is cancelled: " + hashCode());
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
  

  private class LoadItemsInfoTask extends AsyncTask<Void, Void, Void> {
    protected Void doInBackground(Void... params) {
      while (!itemsQueue.isEmpty()) {
        int pos = itemsQueue.remove();
        if (pos >= fileList.size()) continue;
        
        ListItem item = fileList.get(pos);
        
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
  
  
  private class ListSwipeManager extends GestureDetector.SimpleOnGestureListener {
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
      View view = listItems.findChildViewUnder(e1.getX(), e1.getY());
      
      float moveDiff = e2.getX() - e1.getX();
      int direction = moveDiff < 0 ? 0: 1;
      float swipedRatio = Math.abs(moveDiff) / view.getWidth();
      
      if (direction == 0) {
        if (!itemSwiping) {
          listItems.requestDisallowInterceptTouchEvent(true);
          itemSwiping = true;
          view.setPressed(true);
        }
        
        // LEFT
        if (swipedRatio >= 0.25f) {
          itemSwipedLeft = true;
        }
      }
      return true;
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
