package org.mortalis.homeplayer;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import java.util.Set;
import java.util.HashSet;
import java.util.Date;
import java.util.Random;
import java.io.FileOutputStream;
import android.app.Notification;
import java.util.Comparator;
import java.io.FilenameFilter;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FilenameUtils;

import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import androidx.core.app.NotificationCompat;
import androidx.media.app.NotificationCompat.MediaStyle;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import android.util.Log;
import android.widget.Toast;
import android.os.PowerManager;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.util.DisplayMetrics;
import android.content.res.Resources;
import android.app.NotificationChannel;
import android.os.Build;


public class Fun {
  
  private static String[] units = {"B", "KB", "MB", "GB", "TB"};
  
  
  public static FileFilter dirFilter = (file) -> {
    return (file.isDirectory() && file.canRead() && !file.isHidden());
  };
  
  public static FilenameFilter fileFilter = (dirName, fileName) -> {
    return Arrays.stream(Vars.AUDIO_EXTS).anyMatch(ext -> fileName.toLowerCase().endsWith(ext));
  };
  
  public static Comparator<File> nocaseComp = (item1, item2) -> {
    if (item1 == null || item2 == null) return 0;
    return item1.getName().compareToIgnoreCase(item2.getName());
  };
  
  public static Comparator<String> nocaseCompStr = (item1, item2) -> {
    if (item1 == null || item2 == null) return 0;
    return item1.compareToIgnoreCase(item2);
  };
  
  
  public static boolean fileExists(String filePath) {
    return new File(filePath).exists();
  }
  
  public static String getParentFolder(String filePath) {
    return new File(filePath).getParentFile().getAbsolutePath();
  }
  
  public static boolean removeFile(String filePath) {
    try {
      return new File(filePath).delete();
    }
    catch (Exception e) {
      e.printStackTrace();
    }
    
    return false;
  }
  
  public static String getBaseFileName(String filePath) {
    return FilenameUtils.getBaseName(filePath);
  }
  
  public static String getFileExt(String filePath) {
    return FilenameUtils.getExtension(filePath);
  }
  
  
  public static String formatTime(int time, boolean includeHours) {  // time in s
    int _time  = time < 0 ? -time: time;
    String fmt = time < 0 ? "-%02d:%02d": "%02d:%02d";
    
    int h = _time / 3600;
    int m = _time / 60;
    int s = _time % 60;
    
    String result;
    if (h > 0 || includeHours) {
      fmt += ":%02d";
      m %= 60;
      result = String.format(fmt, h, m, s);
    }
    else {
      result = String.format(fmt, m, s);
    }
    
    return result;
  }
  
  public static String formatSize(long size) {  // size in bytes
    float result = (float) size;
    int units_index = 0;
    while (result > 1024) {
      result /= 1024;
      units_index++;
    }
    return String.format("%.1f %s", result, units[units_index]);
  }
    
  public static int getRandomInt(int from, int to) {
    return from + new Random().nextInt(to - from + 1);
  }
  
  public static void saveSharedPref(Context context, String key, String value) {
    if (context == null) return;
    SharedPreferences sharedPreferences = context.getSharedPreferences(Vars.PREFS_FILE, 0);
    SharedPreferences.Editor editor = sharedPreferences.edit();
    editor.putString(key, value);
    editor.commit();
  }
  
  public static void saveSharedPref(Context context, String key, long value) {
    if (context == null) return;
    SharedPreferences sharedPreferences = context.getSharedPreferences(Vars.PREFS_FILE, 0);
    SharedPreferences.Editor editor = sharedPreferences.edit();
    editor.putLong(key, value);
    editor.commit();
  }
  
  public static void saveSharedPref(Context context, String key, int value) {
    if (context == null) return;
    SharedPreferences sharedPreferences = context.getSharedPreferences(Vars.PREFS_FILE, 0);
    SharedPreferences.Editor editor = sharedPreferences.edit();
    editor.putInt(key, value);
    editor.commit();
  }
  
  public static void saveSharedPref(Context context, String key, boolean value) {
    if (context == null) return;
    SharedPreferences sharedPreferences = context.getSharedPreferences(Vars.PREFS_FILE, 0);
    SharedPreferences.Editor editor = sharedPreferences.edit();
    editor.putBoolean(key, value);
    editor.commit();
  }
  
  public static void saveSharedPref(Context context, String key, Set<String> values) {
    if (context == null) return;
    SharedPreferences sharedPreferences = context.getSharedPreferences(Vars.PREFS_FILE, 0);
    SharedPreferences.Editor editor = sharedPreferences.edit();
    editor.remove(key).commit();
    editor.putStringSet(key, values);
    editor.commit();
  }
  
  public static String getSharedPref(Context context, String key) {
    if (context == null) return null;
    SharedPreferences sharedPreferences = context.getSharedPreferences(Vars.PREFS_FILE, 0);
    return sharedPreferences.getString(key, null);
  }
  
  public static long getSharedPrefLong(Context context, String key) {
    if (context == null) return 0;
    SharedPreferences sharedPreferences = context.getSharedPreferences(Vars.PREFS_FILE, 0);
    return sharedPreferences.getLong(key, -1);
  }
  
  public static int getSharedPrefInt(Context context, String key) {
    if (context == null) return 0;
    SharedPreferences sharedPreferences = context.getSharedPreferences(Vars.PREFS_FILE, 0);
    return sharedPreferences.getInt(key, -1);
  }
  
  public static boolean getSharedPrefBool(Context context, String key) {
    if (context == null) return false;
    SharedPreferences sharedPreferences = context.getSharedPreferences(Vars.PREFS_FILE, 0);
    return sharedPreferences.getBoolean(key, false);
  }
  
  public static Set<String> getSharedPrefList(Context context, String key) {
    if (context == null) return null;
    SharedPreferences sharedPreferences = context.getSharedPreferences(Vars.PREFS_FILE, 0);
    return sharedPreferences.getStringSet(key, new HashSet<String>());
  }
  
  
  public static void toast(Context context, String msg) {
    if (context == null) return;
    Toast.makeText(context, msg, Toast.LENGTH_LONG).show();
  }
  
  
  private static void log(Object value, Vars.LogLevel level) {
    String msg = null;
    if (value != null) {
      msg = value.toString();
      if (Vars.APP_LOG_LEVEL == Vars.LogLevel.VERBOSE) {
        msg += " " + getCallerLogInfo();
      }
    }
    
    try {
      if (Vars.APP_LOG_LEVEL.compareTo(level) <= 0) {
        switch (level) {
        case INFO:
          Log.i(Vars.APP_LOG_TAG, msg);
          break;
        case DEBUG:
          Log.d(Vars.APP_LOG_TAG, msg);
          break;
        case WARN:
          Log.w(Vars.APP_LOG_TAG, msg);
          break;
        case ERROR:
          Log.e(Vars.APP_LOG_TAG, msg);
          break;
        }
      }
      
      // log_file(msg);
    }
    catch (Exception e) {
      System.out.println(Vars.APP_LOG_TAG + " :: " + msg);
    }
  }
  
  
  public static void log(String format, Object... values) {
    try {
      log(String.format(format, values));
    }
    catch (Exception e) {
      loge("Fun.log(format, values) Exception, " + e.getMessage());
      e.printStackTrace();
    }
  }
  
  public static void logd(String format, Object... values) {
    try {
      logd(String.format(format, values));
    }
    catch (Exception e) {
      loge("Fun.logd(format, values) Exception, " + e.getMessage());
      e.printStackTrace();
    }
  }
  
  public static void loge(String format, Object... values) {
    try {
      loge(String.format(format, values));
    }
    catch (Exception e) {
      loge("Fun.loge(format, values) Exception, " + e.getMessage());
      e.printStackTrace();
    }
  }
  
  public static void log(Object value) {
    log(value, Vars.LogLevel.INFO);
  }
  
  public static void logd(Object value) {
    log(value, Vars.LogLevel.DEBUG);
  }
  
  public static void logw(Object value) {
    log(value, Vars.LogLevel.WARN);
  }
  
  public static void loge(Object value) {
    log(value, Vars.LogLevel.ERROR);
  }
  
  
  private static String getCallerLogInfo() {
    String result = "";
    StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
    
    if (stackTrace != null && stackTrace.length > 1){
      boolean currentFound = false;
      
      int len = stackTrace.length;
      for (int i = 0; i < len; i++) {
        StackTraceElement stackElement = stackTrace[i];
        String className = stackElement.getClassName();
        
        if (className != null && className.equals(Fun.class.getName())) {
          currentFound = true;
        }
        
        if (currentFound && className != null && !className.equals(Fun.class.getName())) {
          String resultClass = stackElement.getClassName();
          String method = stackElement.getMethodName();
          int line = stackElement.getLineNumber();
          result = "[" + resultClass + ":" + method + "():" + line + "]";
          break;
        }
      }
    }
    
    return result;
  }
  
  public static void createNotificationChannel(Context context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      String id = Vars.NOTIFICATIONS_CHANNEL_ID;
      CharSequence name = context.getString(R.string.notification_channel_name);
      String description = context.getString(R.string.notification_channel_description);
      int importance = NotificationManager.IMPORTANCE_LOW;

      NotificationChannel channel = new NotificationChannel(id, name, importance);
      channel.setDescription(description);
      channel.setShowBadge(false);
      NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
      notificationManager.createNotificationChannel(channel);
    }
  }
  
  public static Notification buildNotification(Context context, String title, String text) {
    NotificationCompat.Builder builder = new NotificationCompat.Builder(context, Vars.NOTIFICATIONS_CHANNEL_ID);
    Bitmap largeIcon = BitmapFactory.decodeResource(context.getResources(), R.drawable.round_audiotrack_white_24);
    
    builder.setSmallIcon(R.drawable.round_audiotrack_black_24);
    builder.setLargeIcon(largeIcon);
    builder.setColor(Color.RED);
    builder.setShowWhen(false);
    builder.setOngoing(false);
    builder.setVibrate(null);
    
    builder.setContentTitle(title);
    builder.setContentText(text);
    
    return builder.build();
  }
  
  public static void cancelNotification(Context context, int id) {
    NotificationManager mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    mNotificationManager.cancel(id);
  }
  
  
  public static int measureViewHeight(View view) {
    int result = 0;
    
    int widthMeasureSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
    int heightMeasureSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
    view.measure(widthMeasureSpec, heightMeasureSpec);
    result = view.getMeasuredHeight();
    
    return result;
  }
  
  public static int measureViewWidth(View view) {
    int result = 0;
    
    int widthMeasureSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
    int heightMeasureSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
    view.measure(widthMeasureSpec, heightMeasureSpec);
    result = view.getMeasuredWidth();
    
    return result;
  }
  
  public static float dpToPx(float dp) {
    DisplayMetrics metrics = Resources.getSystem().getDisplayMetrics();
    float px = dp * ((float) metrics.densityDpi / DisplayMetrics.DENSITY_DEFAULT);
    return px;
  }
  
}
