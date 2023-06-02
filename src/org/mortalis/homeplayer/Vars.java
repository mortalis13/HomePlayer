package org.mortalis.homeplayer;

public class Vars {
  
  public enum LogLevel {VERBOSE, DEBUG, INFO, WARN, ERROR};
  public static final LogLevel APP_LOG_LEVEL = LogLevel.DEBUG;
  
  public static final String APP_LOG_TAG = "home_player";
  public static final String NOTIFICATION_TITLE = "HomePlayer";
  public static final String PLAYER_NOTIFICATION_TITLE = "HomePlayer";
  public static final int NOTIFICATION_ID = 111;
  public static final String NOTIFICATIONS_CHANNEL_ID = "homeplayer_channel_id";
  
  public static final int APP_PERMISSION_REQUEST_ACCESS_EXTERNAL_STORAGE = 101;
  
  public static final String EXTRA_AUDIO_PATH = "extra_audio_path";
  public static final String EXTRA_AUDIO_TIME = "extra_audio_time";  // ms
  public static final String EXTRA_START_PLAYBACK = "extra_start_playback";
  public static final String EXTRA_PLAYBACK_REPEAT = "extra_playback_repeat";
  
  public static final String PREFS_FILE = "home_player_prefs";
  public static final String PREF_LAST_FILE_IN_FOLDER = "PREF_LAST_FILE_";
  public static final String PREF_LAST_TIME_IN_FOLDER = "PREF_LAST_TIME_";
  
  public static final int MIN_PLAYABLE_TIME = 2000;  // ms
  public static final int MAX_TRIM = 300;  // s
  
  public static final boolean SHOW_TIME_IN_MS = true;
  
  public static final String[] AUDIO_EXTS = new String[] {
    "flac",
    "mp3",
    "ogg",
    "wav",
    "mid",
    
    "3g2",
    "3gp",
    "aac",
    "afc",
    "aif",
    "aifc",
    "aiff",
    "amr",
    "asf",
    "m4a",
    "m4b",
    "wma",
    
    // Video with ffmpeg support
    "f4v",
    "m4v",
    "mov",
    "mp4",
    "wmv",
    
    // Other video
    "avi",
    "mkv",
    "webm",
  };
  
}
