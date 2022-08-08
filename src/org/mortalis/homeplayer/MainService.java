package org.mortalis.homeplayer;

import java.util.Optional;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

@SuppressLint("NewApi")
public class MainService {
  
  private static MainService instance = new MainService();
  
  private Optional<MainActivity> mainActivity;
  
  public static int notif_current_id;
  
  
  public static void init(MainActivity mainActivity) {
    instance.mainActivity = Optional.ofNullable(mainActivity);
  }
  
  public static void release() {
    instance.mainActivity = null;
  }
  
  public static MainService get() {
    return instance;
  }
  
  
  // ------ Connection
  public void updatePlayingTime(int playingPos, int totalTime) {
    mainActivity.ifPresent(activity ->
      activity.updatePlayingTime(playingPos, totalTime));
  }
  
  public void initProgress(int time) {
    mainActivity.ifPresent(activity ->
      activity.initProgress(time));
  }
  
  public void updateProgress(int time) {
    mainActivity.ifPresent(activity ->
      activity.updateProgress(time));
  }
  
  public void onPlayerStarted() {
    mainActivity.ifPresent(activity ->
      activity.onPlayerStarted());
  }
  
  public void onPlayerPaused() {
    mainActivity.ifPresent(activity ->
      activity.onPlayerPaused());
  }
  
  public void onPlayerResumed() {
    mainActivity.ifPresent(activity ->
      activity.onPlayerResumed());
  }
  
  public void onPlayerPreloaded() {
    mainActivity.ifPresent(activity ->
      activity.onPlayerPreloaded());
  }
  
  public void onPlayerStopped() {
    mainActivity.ifPresent(activity ->
      activity.onPlayerStopped());
  }
  
  public void exitApp() {
    mainActivity.ifPresent(activity ->
      activity.exitApp());
  }
  
}
