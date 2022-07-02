package org.mortalis.homeplayer;

public class Vars {
  
  public static boolean DEBUG_MODE;
  // public static boolean DEBUG_MODE = true;
  
  public static int SNOOZE_TIME_DEBUG = 30;
  
  // -------------------
  
  public enum LogLevel {VERBOSE, DEBUG, INFO, WARN, ERROR};
  public static final LogLevel APP_LOG_LEVEL = LogLevel.DEBUG;
  
  public static final String APP_LOG_TAG = "home_player";
  public static final String NOTIFICATION_TITLE = "HomePlayer";
  public static final String PLAYER_NOTIFICATION_TITLE = "HomePlayer";
  public static final int NOTIFICATION_ID = 111;
  public static final String NOTIFICATIONS_CHANNEL_ID = "homeplayer_channel_id";
  
  public static final int APP_PERMISSION_REQUEST_ACCESS_EXTERNAL_STORAGE = 101;
  
  public static final String EXTRA_AUDIO_PATH = "audio_path";
  public static final String EXTRA_AUDIO_TIME = "extra_audio_time";
  public static final String EXTRA_START_PLAYBACK = "extra_start_playback";
  
  public static final String PREFS_FILE = "home_player_prefs";
  
  public static final String[] AUDIO_EXTS = new String[] {
    "mp3",
    "ogg",
    "wav",
    "m4a",
    "flac",
    "aac",
    "mid",
    "3gp"
  };
  
}
