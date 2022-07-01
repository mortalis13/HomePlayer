package org.mortalis.homeplayer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileDescriptor;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Arrays;
import java.util.Set;

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

import org.mortalis.homeplayer.components.SliderView;
import org.mortalis.homeplayer.components.SimplePaintView;


public class MainActivity extends AppCompatActivity {
  
  private static final int ITEM_LAYOUT = R.layout.browser_list_item;
  private static final String ROOT_DIR_TITLE = "storage";
  
  private int item_icon_color_default;
  private int item_icon_color_lastplayed;
  
  private File ROOT_STORAGE = Environment.getExternalStorageDirectory();
  private File startDir = new File(Environment.getExternalStorageDirectory(), "_music");
  
  private Context context;
  private LinearLayoutManager listLayoutManager;
  
  private PlayerService playerService;
  private boolean serviceBound;
  
  private FilesAdapter filesAdapter;
  private List<ListItem> fileList;
  private List<AudioInfo> dirAudioData;
  private List<AudioInfo> playingDirAudioData;
  
  private File currentPath;
  private File previouslyPlayedFile;
  private int scrollPos;
  
  private HorizontalScrollView titleScroller;
  private TextView activeTitle;
  private ImageButton bGoBack;
  
  private SliderView progressSlider;
  private RecyclerView listItems;
  
  private TextView textTimeLeft;
  private TextView textTimePlaying;
  private TextView textTimeTotal;
  
  private TextView textCurrentFolderStats;
  private TextView textCurrentFolderTime;
  private TextView textPlayingStats;
  private TextView textPlayingFolderTime;
  
  private ImageButton bPrevFile;
  private ImageButton bPlayPause;
  private ImageButton bNextFile;
  
  private String lastFolder;
  private String lastAudio;
  private int lastAudioTime;
  private Set<String> favoritesList;
  
  
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    Fun.logd("MainActivity.onCreate()");
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    
    context = this;
    MainService.init(this);
    
    requestAppPermissions(context);
    Fun.createNotificationChannel(context);
    
    bindPlayerService();
    
    init();
    configUI();
    restoreState();
    
    setVolumeControlStream(AudioManager.STREAM_MUSIC);
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
    item_icon_color_default = ContextCompat.getColor(context, R.color.list_item_icon);
    item_icon_color_lastplayed = ContextCompat.getColor(context, R.color.list_item_is_last_played_file);
  }
  
  private void configUI() {
    titleScroller = findViewById(R.id.titleScroller);
    activeTitle = findViewById(R.id.activeTitle);
    bGoBack = findViewById(R.id.bGoBack);
    
    listItems = findViewById(R.id.listItems);
    progressSlider = findViewById(R.id.progressSlider);
    
    textTimePlaying = findViewById(R.id.textTimePlaying);
    textTimeLeft = findViewById(R.id.textTimeLeft);
    textTimeTotal = findViewById(R.id.textTimeTotal);
    
    textCurrentFolderStats = findViewById(R.id.textCurrentFolderStats);
    textCurrentFolderTime = findViewById(R.id.textCurrentFolderTime);
    textPlayingStats = findViewById(R.id.textPlayingStats);
    textPlayingFolderTime = findViewById(R.id.textPlayingFolderTime);
    
    bPrevFile = findViewById(R.id.bPrevFile);
    bPlayPause = findViewById(R.id.bPlayPause);
    bNextFile = findViewById(R.id.bNextFile);
    
    
    titleScroller.setSmoothScrollingEnabled(false);
    
    fileList = new ArrayList<>();
    filesAdapter = new FilesAdapter(fileList);
    listItems.setAdapter(filesAdapter);
    
    Typeface typeface = Typeface.createFromAsset(getAssets(), "fonts/consolas.ttf");
    textTimePlaying.setTypeface(typeface);
    textTimeLeft.setTypeface(typeface);
    textTimeTotal.setTypeface(typeface);
    textCurrentFolderStats.setTypeface(typeface);
    textCurrentFolderTime.setTypeface(typeface);
    textPlayingStats.setTypeface(typeface);
    textPlayingFolderTime.setTypeface(typeface);
    
    listLayoutManager = new LinearLayoutManager(context);
    listItems.setLayoutManager(listLayoutManager);
    
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
    
    bGoBack.setOnClickListener(v -> {
      changeToParentDir();
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
      
      File dir = lastFolder == null ? startDir: new File(lastFolder);
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
      // setPlayButtonDefault();
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
  
  
  // ------------------------------ Audio ------------------------------
  private void playAudio(String filePath, boolean startPlayback) {
    playAudio(filePath, 0, startPlayback);
  }
  
  private void playAudio(String filePath, int time, boolean startPlayback) {
    Fun.logd("playAudio()");
    File playingFile = new File(filePath);
    if (!playingFile.exists()) {
      Fun.loge("The file does not exist: " + filePath);
      return;
    }
    
    String currentAudioPath = playerService.getAudioPath();
    if (currentAudioPath != null && !new File(filePath).getParent().equals(new File(currentAudioPath).getParent())) {
      Fun.saveSharedPref(context, "TIME_" + new File(currentAudioPath).getParent(), lastAudioTime);
      Fun.log(String.format("Saved %d time to TIME_%s", lastAudioTime, new File(currentAudioPath).getParent()));
    }
    
    Intent playerIntent = new Intent(this, PlayerService.class);
    playerIntent.putExtra(Vars.EXTRA_AUDIO_PATH, filePath);
    playerIntent.putExtra(Vars.EXTRA_AUDIO_TIME, time);
    playerIntent.putExtra(Vars.EXTRA_START_PLAYBACK, startPlayback);
    
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      startForegroundService(playerIntent);
    }
    else {
      startService(playerIntent);
    }
    
    Fun.saveSharedPref(context, "PREF_LAST_AUDIO", playingFile.getPath());
    Fun.saveSharedPref(context, "FILE_" + playingFile.getParent(), playingFile.getPath());
    markLastPlayedFile(currentPath);
    
    updatePlayingAudioInfo(playingFile);
  }
  
  private void preloadAudio(String filePath, int time) {
    Fun.logd("preloadAudio()");
    playAudio(filePath, time, false);
  }
  
  private void playNextFile(boolean startPlayback) {
    Fun.logd("playNextFile()");
    if (playerService == null || playerService.getAudioPath() == null) return;
    
    File currentFile = new File(playerService.getAudioPath());
    File file = getNextFile(currentFile);
    if (file != null) {
      Fun.log("Next file: " + file.toString());
      playAudio(file.getPath(), startPlayback);
    }
    else {
      Fun.loge("Next file is null");
    }
  }
  
  private void playPrevFile(boolean startPlayback) {
    Fun.logd("playPrevFile()");
    if (playerService == null || playerService.getAudioPath() == null) return;
    
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
  private void changeToParentDir() {
    File parent = currentPath.getParentFile();
    if (currentPath.equals(ROOT_STORAGE)) {
      Fun.log("In the root folder, cannot go to parent");
      return;
    }
    
    File prevPath = new File(currentPath.getPath());
    changeDir(parent);
    
    File[] dirs = parent.listFiles(Fun.dirFilter);
    Arrays.sort(dirs, Fun.nocaseComp);
    
    int scrollPos = 0;
    for (int i = 0; i < dirs.length; i++) {
      if (dirs[i].equals(prevPath)) {
        scrollPos = i;
        break;
      }
    }
    
    listLayoutManager.scrollToPosition(scrollPos);
  }
  
  private void changeDir(File path) {
    if (!path.exists()) path = ROOT_STORAGE;
    currentPath = path;
    
    fileList.clear();
    
    File[] dirs = path.listFiles(Fun.dirFilter);
    if (dirs == null) dirs = new File[0];
    Arrays.sort(dirs, Fun.nocaseComp);
    
    for (File dir: dirs) {
      fileList.add(new ListItem(dir.getName()));
    }
    
    File[] files = path.listFiles(Fun.fileFilter);
    if (files == null) files = new File[0];
    Arrays.sort(files, Fun.nocaseComp);
    
    dirAudioData = getAudioDataForDirectory(files);
    
    for (File file: files) {
      String time = Fun.formatTime(getAudioTime(file, dirAudioData) / 1000, false);
      fileList.add(new ListItem(file.getName(), file.getAbsolutePath(), time));
    }
    
    String title = currentPath.getName();
    if (currentPath.equals(ROOT_STORAGE)) title = ROOT_DIR_TITLE;
    activeTitle.setText(title);
    
    titleScroller.fullScroll(View.FOCUS_LEFT);
    filesAdapter.resetSelection();
    filesAdapter.notifyDataSetChanged();
    
    listItems.post(() -> {
      int lastPos = listLayoutManager.findLastCompletelyVisibleItemPosition();
      int mode = View.OVER_SCROLL_IF_CONTENT_SCROLLS;
      if (lastPos == filesAdapter.getItemCount()-1) {
        mode = View.OVER_SCROLL_NEVER;
      }
      listItems.setOverScrollMode(mode);
    });
    
    Fun.saveSharedPref(context, "PREF_LAST_FOLDER", path.getPath());
    
    selectPlayingDirOrFile(files, dirs);
    updateCurrentFolderStats(files, dirs.length);

    markLastPlayedFile(currentPath);
    markFavorites(files);
  }
  
  private void markLastPlayedFile(File dir) {
    String lastFile = Fun.getSharedPref(this, "FILE_" + dir.getPath());
    int lastTime = Fun.getSharedPrefInt(this, "TIME_" + dir.getPath());
    if (lastFile != null) {
      Fun.log(String.format("Last played file in dir '%s': '%s'. Time: %d", dir, lastFile, lastTime));
      filesAdapter.markLastPlayedItem(lastFile);
    }
  }
  
  private void markFavorites(File[] files) {
    for (File file: files) {
      for (String favPath: favoritesList) {
        if (favPath.equals(file.getPath())) {
          filesAdapter.markAsFavorite(favPath);
          break;
        }
      }
    }
  }
  
  // ------------------------------ Events ------------------------------
  public void onPlayerStarted() {
    setPlayButtonAsPause();
    progressSlider.enable();
    updatePlayingStats();
  }
  
  public void onPlayerPaused() {
    setPlayButtonDefault();
  }
  
  public void onPlayerPreloaded() {
    progressSlider.enable();
    updatePlayingStats();
  }
  
  public void onPlayerStopped() {
    if (isPlayingLastFile()) {
      progressSlider.disable();
      setPlayButtonDefault();
    }
    else {
      playNextFile(true);
    }
  }
  
  
  // ------------------------------ Audio Utils ------------------------------
  public File getPrevFile(File file) {
    return getNearestFile(file, false);
  }
  
  public File getNextFile(File file) {
    return getNearestFile(file, true);
  }
  
  public File getNearestFile(File file, boolean next) {
    File parent = file.getParentFile();
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
  
  private void selectPlayingDirOrFile(String filePath) {
    if (filePath == null) return;
    
    File currentFile = new File(filePath);
    if (!currentFile.getPath().startsWith(currentPath.getPath())) return;
    
    File[] dirs = currentPath.listFiles(Fun.dirFilter);
    if (dirs == null) return;
    Arrays.sort(dirs, Fun.nocaseComp);
    
    int playingItemPos = getCurrentPlayingItemIndex(currentFile, dirs);
    
    if (playingItemPos != -1) {
      Fun.log("Selecting playing folder or file: " + dirs[playingItemPos]);
      boolean isFile = false;
      filesAdapter.selectItem(playingItemPos, isFile);
    }
    else {
      Fun.loge("Playing file or folder position is -1");
    }
  }
  
  private void selectPlayingDirOrFile(File[] files, File[] dirs) {
    if (playerService == null || playerService.getAudioPath() == null) return;
    
    File currentFile = new File(playerService.getAudioPath());
    if (!currentFile.getPath().startsWith(currentPath.getPath())) return;
    
    File[] items = currentPath.equals(currentFile.getParentFile()) ? files: dirs;
    if (items == null) return;
    
    int playingItemPos = getCurrentPlayingItemIndex(currentFile, items);
    
    if (playingItemPos != -1) {
      Fun.log("Selecting playing folder or file: " + items[playingItemPos]);
      boolean isFile = items == files;
      filesAdapter.selectItem(playingItemPos, isFile);
    }
    else {
      Fun.loge("Playing file or folder position is -1");
    }
  }
  
  private int getCurrentPlayingItemIndex(File file, File[] items) {
    if (file == null) return -1;
    
    for (int i = 0; i < items.length; i++) {
      if (items[i].equals(file)) {
        return i;
      }
    }
    return getCurrentPlayingItemIndex(file.getParentFile(), items);
  }
  
  private boolean isPlayingLastFile() {
    if (playerService == null || playerService.getAudioPath() == null) return true;

    File currentFile = new File(playerService.getAudioPath());
    File[] files = currentFile.getParentFile().listFiles(Fun.fileFilter);
    Arrays.sort(files, Fun.nocaseComp);
    
    for (int i = 0; i < files.length; i++) {
      if (files[i].equals(currentFile)) {
        return i == files.length - 1;
      }
    }
    
    return false;
  }
  
  public AudioInfo extractAudioInfo(File file) {
    AudioInfo info = new AudioInfo();
    info.file = file;
    
    try {
      MediaExtractor mediaExtractor = new MediaExtractor();
      mediaExtractor.setDataSource(file.getPath());

      MediaFormat format = mediaExtractor.getTrackFormat(0);
      long duration = format.getLong(MediaFormat.KEY_DURATION);
      info.time = (int) (duration / 1000);
      mediaExtractor.release();
    }
    catch (Exception e) {
      e.printStackTrace();
    }
    
    return info;
  }
  
  public List<AudioInfo> getAudioDataForDirectory(File[] files) {
    Fun.logd("getAudioDataForDirectory(File[])");
    if (files.length == 0) return null;
    
    List<AudioInfo> data = new ArrayList<>();
    for (File file: files) {
      data.add(extractAudioInfo(file));
    }
    return data;
  }
  
  public List<AudioInfo> getAudioDataForDirectory(File dir) {
    Fun.logd("getAudioDataForDirectory(File): " + dir);
    File[] files = dir.listFiles(Fun.fileFilter);
    Arrays.sort(files, Fun.nocaseComp);
    return getAudioDataForDirectory(files);
  }
  
  public int getAudioTime(File file, List<AudioInfo> audioInfo) {
    if (!file.exists()) {
      Fun.loge("getAudioTime() : File does not exist: " + file);
      return 0;
    }
    
    if (audioInfo == null) return 0;
    
    for (AudioInfo info: audioInfo) {
      if (info.file.equals(file)) return info.time;
    }
    
    return 0;
  }
  
  public String getTotalTimeInDirectory(File[] files, List<AudioInfo> audioInfo) {
    int totalTime = 0;
    for (File file: files) {
      totalTime += getAudioTime(file, audioInfo);
    }
    return Fun.formatTime(totalTime / 1000, true);
  }
  
  private void updatePlayingAudioInfo(File playingFile) {
    boolean updateInfo = false;
    File playingDirectory = playingFile.getParentFile();
    
    if (playingDirAudioData == null || playingDirAudioData.isEmpty()) {
      if (dirAudioData != null && !dirAudioData.isEmpty() && doesDirBelongToAudioInfo(playingDirectory, dirAudioData)) {
        playingDirAudioData = copyAudioInfo(dirAudioData);
      }
      else {
        updateInfo = true;
      }
    }
    else if (playingDirAudioData != null && !doesDirBelongToAudioInfo(playingDirectory, playingDirAudioData)) {
      updateInfo = true;
    }
    
    // Force update if the audio info is not for the current playing directory or if it's empty and there is no cache from the navigation
    // playingDirAudioData should always reflect the audio info for the playing directory
    if (updateInfo) {
      playingDirAudioData = getAudioDataForDirectory(playingDirectory);
    }
  }
  
  private boolean doesDirBelongToAudioInfo(File dir, List<AudioInfo> data) {
    return data.get(0).file.getParentFile().equals(dir);
  }
  
  private List<AudioInfo> copyAudioInfo(List<AudioInfo> data) {
    List<AudioInfo> result = new ArrayList<>();
    for (AudioInfo info: data) {
      AudioInfo newInfo = new AudioInfo();
      newInfo.file = info.file;
      newInfo.time = info.time;
      result.add(newInfo);
    }
    return result;
  }
  
  
  // ------------------------------ Utils ------------------------------
  private void updateCurrentFolderStats(File[] files, int numDirs) {
    if (files.length == 0) {
      textCurrentFolderStats.setText("");
      textCurrentFolderTime.setText("");
      return;
    }
    
    String stats = String.format("%d", files.length);
    textCurrentFolderStats.setText(stats);
    
    String totalTime = getTotalTimeInDirectory(files, dirAudioData);
    textCurrentFolderTime.setText(totalTime);
  }
  
  private void updatePlayingStats() {
    File currentFile = new File(playerService.getAudioPath());
    File[] files = currentFile.getParentFile().listFiles(Fun.fileFilter);
    Arrays.sort(files, Fun.nocaseComp);
    
    // Search for playing file in its folder only
    selectPlayingDirOrFile(files, null);
    
    int currentFilePos = getCurrentPlayingItemIndex(currentFile, files);
    if (currentFilePos != -1) {
      String stats = String.format("%d/%d", ++currentFilePos, files.length);
      textPlayingStats.setText(stats);
    }
    
    String totalTime = getTotalTimeInDirectory(files, playingDirAudioData);
    textPlayingFolderTime.setText(totalTime);
  }
  
  public void updatePlayingTime(int playingPos, int totalTime) {
    Fun.saveSharedPref(context, "PREF_LAST_AUDIO_TIME", playingPos);
    lastAudioTime = playingPos;
    
    playingPos /= 1000;
    totalTime  /= 1000;
    String timePlaying = Fun.formatTime(playingPos, false);
    String timeTotal   = Fun.formatTime(totalTime, false);
    String timeLeft    = "-" + Fun.formatTime(totalTime - playingPos, false);
    
    textTimePlaying.setText(timePlaying);
    textTimeTotal.setText(timeTotal);
    textTimeLeft.setText(timeLeft);
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
  
  private void cleanFavorites() {
    // Removes files that don't exist
    if (favoritesList == null) return;
    String[] originalList = favoritesList.toArray(new String[0]);
    
    for (String favPath: originalList) {
      if (!new File(favPath).exists()) {
        favoritesList.remove(favPath);
      }
    }
    
    Fun.saveSharedPref(context, "PREF_FAVORITES_LIST", favoritesList);
  }
  
  
  private void itemClick(ListItem item) {
    try {
      File chosenFile = new File(currentPath, item.text);
      
      if (chosenFile.isDirectory()) {
        changeDir(chosenFile);
        listLayoutManager.scrollToPositionWithOffset(0, 0);
      }
      else {
        int time = 0;
        if (item.isLastPlayed && !item.path.equals(playerService.getAudioPath())) {
          int lastTime = Fun.getSharedPrefInt(this, "TIME_" + new File(item.path).getParent());
          if (lastTime != -1) time = lastTime;
        }
        playAudio(item.path, time, true);
      }
    }
    catch (Exception e) {
      e.printStackTrace();
    }
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
  

  // ---------------------- Classes ----------------------
  private class ListItem {
    int imgId;
    String text;
    String path;
    String time;
    
    boolean isFile;
    boolean isLastPlayed;
    boolean isFavorite;
    
    ListItem(String text) {
      this(text, null, null, false);
    }
    
    ListItem(String text, String path, String time) {
      this(text, path, time, true);
    }
    
    ListItem(String text, String path, String time, boolean isFile) {
      this.text = text;
      this.path = path;
      this.time = time;
      this.isFile = isFile;
      
      if (isFile && path != null) {
        imgId = getFileIconByType(path);
      }
      else if (!isFile) {
        imgId = R.drawable.round_folder_black_36;
      }
    }
    
    private int getFileIconByType(String path) {
      return R.drawable.round_audio_file_black_36;
      // return R.drawable.round_speaker_black_36;
      // return R.drawable.round_album_black_36;
      // return R.drawable.round_volume_up_black_36;
      // return R.drawable.round_audiotrack_black_36;
    }
  }
  
  
  private class FilesAdapter extends RecyclerView.Adapter<FilesAdapter.ItemViewHolder> {
    public List<ListItem> fileList;
    
    int lastItemSelectedPos = -1;
    int selectedItemPos = -1;
    
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
      ListItem item = fileList.get(position);
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
      return fileList.size();
    }
    
    private int getDirsCount() {
      int result = 0;
      for (ListItem item: fileList) if (!item.isFile) result ++; else break;
      return result;
    }
    
    public void resetSelection() {
      lastItemSelectedPos = -1;
      selectedItemPos = -1;
    }
    
    public void selectItem(int itemPos, boolean isFile) {
      if (isFile) itemPos += getDirsCount();
      
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
      for (int i = 0; i < fileList.size(); i++) {
        ListItem item = fileList.get(i);
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
      for (int i = 0; i < fileList.size(); i++) {
        ListItem item = fileList.get(i);
        if (!item.isFile) continue;
        if (item.path.equals(filePath)) {
          item.isFavorite = true;
          notifyItemChanged(i);
        }
      }
    }
    
    
    public class ItemViewHolder extends RecyclerView.ViewHolder {
      ImageView itemIcon;
      SimplePaintView itemIndicator;
      TextView itemText;
      TextView itemTime;
      FrameLayout iconContainer;
      
      ListItem item;
      
      public ItemViewHolder(View rootView) {
        super(rootView);
        
        itemIcon = rootView.findViewById(R.id.itemIcon);
        itemIndicator = rootView.findViewById(R.id.itemIndicator);
        itemText = rootView.findViewById(R.id.itemText);
        itemTime = rootView.findViewById(R.id.itemTime);
        iconContainer = rootView.findViewById(R.id.iconContainer);
        
        iconContainer.setOnClickListener((view) -> {
          this.item.isFavorite = !this.item.isFavorite;
          itemIndicator.setVisibility(this.item.isFavorite ? View.VISIBLE: View.GONE);
          updateItemFavorite(this.item.path, this.item.isFavorite);
        });
        
        itemView.setOnTouchListener((view, event) -> {
          if (this.item == null) return false;

          int action = event.getAction();
          if (action == MotionEvent.ACTION_DOWN) {
            view.setPressed(true);
          }
          else if (action == MotionEvent.ACTION_CANCEL) {
            view.setPressed(false);
          }
          else if (action == MotionEvent.ACTION_UP) {
            view.setPressed(false);
            itemClick(this.item);
          }
          
          return true;
        });
      }
      
      public void select() {
        itemView.setSelected(true);
        itemView.setPressed(false);
      }
      
      public void unselect() {
        itemView.setSelected(false);
        itemView.setPressed(false);
      }
      
      public void bind(ListItem item) {
        this.item = item;
        if (item == null) return;
        
        itemIcon.setImageResource(item.imgId);
        itemText.setText(item.text);
        itemTime.setText(item.time);
        itemTime.setVisibility(item.isFile ? View.VISIBLE: View.GONE);
        itemIndicator.setVisibility(item.isFavorite ? View.VISIBLE: View.GONE);

        int iconColor = item_icon_color_default;
        if (item.isLastPlayed) iconColor = item_icon_color_lastplayed;
        itemIcon.setColorFilter(iconColor);
      }
    } // ItemViewHolder
  }
  
  
  public class CircularOutlineProvider extends ViewOutlineProvider {  
      @Override
      public void getOutline(View view, Outline outline) {
          outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), (view.getWidth() / 2F));
      }
  }
  
  
  private ServiceConnection serviceConnection = new ServiceConnection() {
    @Override
    public void onServiceConnected(ComponentName className, IBinder service) {
      PlayerService.PlayerBinder binder = (PlayerService.PlayerBinder) service;
      playerService = binder.getService();
      serviceBound = true;
      
      if (lastAudio != null) {
        Fun.log(String.format("lastAudio: %s; %d", lastAudio, lastAudioTime));
        preloadAudio(lastAudio, lastAudioTime);
        selectPlayingDirOrFile(lastAudio);
      }
    }

    @Override
    public void onServiceDisconnected(ComponentName arg0) {
      playerService = null;
      serviceBound = false;
    }
  };
  
  
  // --------------------
  @Override
  protected void onStart() {
    Fun.logd("MainActivity.onStart()");
    super.onStart();
    MainService.init(this);
  }
  
  @Override
  protected void onPause() {
    Fun.logd("MainActivity.onPause()");
    super.onPause();
  }
  
  @Override
  protected void onResume() {
    Fun.logd("MainActivity.onResume()");
    super.onResume();
    bindPlayerService();
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
  
  @Override
  protected void onDestroy() {
    Fun.logd("MainActivity.onDestroy()");
    super.onDestroy();

    if (serviceBound) {
      unbindService(serviceConnection);
    }
  }
  // --------------------
  
}
