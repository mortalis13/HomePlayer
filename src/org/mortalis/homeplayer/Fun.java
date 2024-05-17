package org.mortalis.homeplayer;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Build;
import android.os.Process;
import android.util.DisplayMetrics;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.util.Date;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Stream;
import java.text.RuleBasedCollator;

import net.greypanther.natsort.CaseInsensitiveSimpleNaturalComparator;


public class Fun {
  
  private static final String[] units = {"B", "KB", "MB", "GB", "TB"};
  
  
  public static FileFilter dirFilter = (file) -> {
    return (file.isDirectory() && file.canRead() && !file.isHidden());
  };
  
  public static FileFilter fileFilter = (file) -> {
    boolean isAudio = Stream.of(Vars.AUDIO_EXTS).anyMatch(ext -> file.getName().toLowerCase().endsWith(ext));
    return (file.isFile() && !file.isHidden() && isAudio);
  };
  
  public static Comparator<File> nocaseComp = (item1, item2) -> {
    // try {
    //   return new RuleBasedCollator("&'_'< a< z").compare(item1.getName(), item2.getName());
    //   // return new RuleBasedCollator("&a< b< c< d").compare(item1.getName(), item2.getName());
    // }
    // catch (Exception e) {
    //   e.printStackTrace();
    //   if (item1 == null || item2 == null) return 0;
    //   return item1.getName().compareToIgnoreCase(item2.getName());
    // }
    
    // if (item1 == null || item2 == null) return 0;
    // return item1.getName().compareToIgnoreCase(item2.getName());
    
    // Custom comparator to sort the names with numbers in natural readable order
    Comparator<String> comparator = CaseInsensitiveSimpleNaturalComparator.getInstance();
    return comparator.compare(item1.getName(), item2.getName());
  };
  
  
  public static boolean fileExists(String filePath) {
    return filePath != null && new File(filePath).exists();
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
  
  public static String getPrevFilePath(File file) {
    file = getPrevFile(file);
    return file != null ? file.getPath(): null;
  }
  
  public static String getNextFilePath(File file) {
    file = getNextFile(file);
    return file != null ? file.getPath(): null;
  }
  
  public static File getPrevFile(File file) {
    return getPrevNextFile(file, false);
  }
  
  public static File getNextFile(File file) {
    return getPrevNextFile(file, true);
  }
  
  private static File getPrevNextFile(File file, boolean next) {
    if (file == null) {
      loge("The file is null");
      return null;
    }
    
    File parent = file.getParentFile();
    if (parent == null || !parent.exists()) {
      loge("The parent does not exist for file " + file);
      return null;
    }
    
    File[] files = parent.listFiles(fileFilter);
    if (files == null) return null;
    Arrays.sort(files, nocaseComp);
    
    int len = files.length;
    for (int i = 0; i < len; i++) {
      if (files[i].equals(file)) {
        if (next) {
          return files[i == len-1 ? 0: i+1];
        }
        return file = files[i == 0 ? len-1: i-1];
      }
    }
    
    return null;
  }
  
  public static File getNearestExistingParent(File file) {
    if (file == null) return null;
    
    File parent = file.getParentFile();
    while (parent != null && !parent.exists()) {
      parent = parent.getParentFile();
    }
    
    return parent;
  }
  
  public static String getFolder(String file) {
    return new File(file).getParent();
  }
  
  public static String formatTime(int time, boolean withHours, boolean withMs) {  // time in ms
    int _time  = time < 0 ? -time: time;
    String sign = time < 0 ? "-": "";
    
    int h  = _time / 1000 / 3600;
    int m  = _time / 1000 / 60;
    int s  = _time / 1000 % 60;
    int ms = _time % 1000;
    
    String result;
    
    if (h > 0) withHours = true;
    
    if (withHours && withMs) {
      String format = sign + "%02d:%02d:%02d.%03d";
      result = String.format(format, h, m % 60, s, ms);
    }
    else if (withHours && !withMs) {
      String format = sign + "%02d:%02d:%02d";
      result = String.format(format, h, m % 60, s);
    }
    else if (!withHours && withMs) {
      String format = sign + "%02d:%02d.%03d";
      result = String.format(format, m, s, ms);
    }
    else {
      String format = sign + "%02d:%02d";
      result = String.format(format, m, s);
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
    var editor = sharedPreferences.edit();
    editor.putString(key, value);
    editor.commit();
  }
  
  public static void saveSharedPref(Context context, String key, long value) {
    if (context == null) return;
    SharedPreferences sharedPreferences = context.getSharedPreferences(Vars.PREFS_FILE, 0);
    var editor = sharedPreferences.edit();
    editor.putLong(key, value);
    editor.commit();
  }
  
  public static void saveSharedPref(Context context, String key, int value) {
    if (context == null) return;
    SharedPreferences sharedPreferences = context.getSharedPreferences(Vars.PREFS_FILE, 0);
    var editor = sharedPreferences.edit();
    editor.putInt(key, value);
    editor.commit();
  }
  
  public static void saveSharedPref(Context context, String key, float value) {
    if (context == null) return;
    SharedPreferences sharedPreferences = context.getSharedPreferences(Vars.PREFS_FILE, 0);
    var editor = sharedPreferences.edit();
    editor.putFloat(key, value);
    editor.commit();
  }
  
  public static void saveSharedPref(Context context, String key, boolean value) {
    if (context == null) return;
    SharedPreferences sharedPreferences = context.getSharedPreferences(Vars.PREFS_FILE, 0);
    var editor = sharedPreferences.edit();
    editor.putBoolean(key, value);
    editor.commit();
  }
  
  public static void saveSharedPref(Context context, String key, Set<String> values) {
    if (context == null) return;
    SharedPreferences sharedPreferences = context.getSharedPreferences(Vars.PREFS_FILE, 0);
    var editor = sharedPreferences.edit();
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
  
  public static float getSharedPrefFloat(Context context, String key) {
    if (context == null) return 0;
    SharedPreferences sharedPreferences = context.getSharedPreferences(Vars.PREFS_FILE, 0);
    return sharedPreferences.getFloat(key, -1);
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
  
  public static Map<String, ?> getAllSharedPrefs(Context context) {
    if (context == null) return null;
    SharedPreferences sharedPreferences = context.getSharedPreferences(Vars.PREFS_FILE, 0);
    return sharedPreferences.getAll();
  }
  
  public static void removeSharedPref(Context context, String key) {
    if (context == null) return;
    SharedPreferences sharedPreferences = context.getSharedPreferences(Vars.PREFS_FILE, 0);
    var editor = sharedPreferences.edit();
    editor.remove(key);
    editor.commit();
  }
  
  
  public static void toast(Context context, String msg) {
    if (context == null) return;
    Toast.makeText(context, msg, Toast.LENGTH_LONG).show();
  }
  
  
  private static void log(Object value, Vars.LogLevel level) {
    String msg = "";
    if (value != null) {
      msg = value.toString();
      if (Vars.APP_LOG_LEVEL == Vars.LogLevel.VERBOSE) {
        msg += " " + getCallerLogInfo();
      }
    }
    
    try {
      if (Vars.APP_LOG_LEVEL.compareTo(level) <= 0) {
        switch (level) {
          case INFO -> Log.i(Vars.APP_LOG_TAG, msg);
          case DEBUG -> Log.d(Vars.APP_LOG_TAG, msg);
          case WARN -> Log.w(Vars.APP_LOG_TAG, msg);
          case ERROR -> Log.e(Vars.APP_LOG_TAG, msg);
        }
      }
      
      // log_file(msg, level);
    }
    catch (Exception e) {
      System.out.println(Vars.APP_LOG_TAG + " :: " + msg);
    }
  }
  
  private static void log_file(String msg, Vars.LogLevel level) {
    try {
      File f = new File("/sdcard/_test/temp_log.log");
      FileOutputStream fout = new FileOutputStream(f, true);
      
      fout.write(new Date().toString().getBytes());
      fout.write((" "+Process.myPid() + " ").getBytes());
      fout.write((Process.myTid() + " ").getBytes());
      fout.write(level.name().getBytes());
      fout.write(" :: ".getBytes());
      fout.write(msg.getBytes());
      fout.write(0x0a);
      fout.close();
    }
    catch (Exception e){
      e.printStackTrace();
    }
  }
  
  public static void log(String format, Object... values) {
    try {
      log(String.format(format, values));
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }
  
  public static void logd(String format, Object... values) {
    try {
      logd(String.format(format, values));
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }
  
  public static void loge(String format, Object... values) {
    try {
      loge(String.format(format, values));
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }

  public static void logw(String format, Object... values) {
    try {
      logw(String.format(format, values));
    }
    catch (Exception e) {
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
    StackTraceElement[] stackTrace = new Throwable().getStackTrace();
    
    if (stackTrace.length == 0) return result;
    String callingClassName = stackTrace[0].getClassName();
    
    var callerElement = Arrays.stream(stackTrace)
      .filter(e -> !e.getClassName().equals(callingClassName))
      .findFirst()
      .orElse(null);

    if (callerElement != null) {
      result = String.format("[%s:%s():%d]", callerElement.getClassName(), callerElement.getMethodName(), callerElement.getLineNumber());
    }
    
    return result;
  }

  public static void createNotificationChannel(Context context) {
    if (Build.VERSION.SDK_INT >= 26) {
      CharSequence name = context.getString(R.string.notification_channel_name);
      String description = context.getString(R.string.notification_channel_description);

      NotificationChannel channel = new NotificationChannel(Vars.NOTIFICATION_CHANNEL_ID, name, NotificationManager.IMPORTANCE_LOW);
      channel.setDescription(description);
      channel.setShowBadge(false);
      
      NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
      notificationManager.createNotificationChannel(channel);
    }
  }

  public static int getScreenDpi() {
    return Resources.getSystem().getDisplayMetrics().densityDpi;
  }
  
  public static float dpToPx(float dp) {
    DisplayMetrics metrics = Resources.getSystem().getDisplayMetrics();
    float px = dp * ((float) metrics.densityDpi / DisplayMetrics.DENSITY_DEFAULT);
    return px;
  }
  
}
