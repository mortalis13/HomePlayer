package org.mortalis.homeplayer;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

@SuppressLint("NewApi")
public class MainService {
  
  private static MainService instance = new MainService();
  
  private MainActivity mainActivity;
  
  public static Context context;
  public static int notif_current_id;
  
  
  public static void init(MainActivity mainActivity) {
    instance.mainActivity = mainActivity;
    instance.context = mainActivity;
  }
  
  public static void release() {
    instance.mainActivity = null;
  }
  
  public static MainService get() {
    return instance;
  }
  
  
  // ------ Connection
  public void updatePlayingTime(int playingPos, int totalTime) {
    mainActivity.updatePlayingTime(playingPos, totalTime);
  }
  
  public void initProgress(int time) {
    mainActivity.initProgress(time);
  }
  
  public void updateProgress(int time) {
    mainActivity.updateProgress(time);
  }
  
  public void onPlayerStarted() {
    mainActivity.onPlayerStarted();
  }
  
  public void onPlayerPaused() {
    mainActivity.onPlayerPaused();
  }
  
  public void onPlayerResumed() {
    mainActivity.onPlayerResumed();
  }
  
  public void onPlayerPreloaded() {
    mainActivity.onPlayerPreloaded();
  }
  
  public void onPlayerStopped() {
    mainActivity.onPlayerStopped();
  }
  
}
