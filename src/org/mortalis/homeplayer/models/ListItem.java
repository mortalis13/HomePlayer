package org.mortalis.homeplayer.models;

public class ListItem {
  
  public String text;
  public String path;
  public String time;
  
  public boolean isFile;
  public boolean isFolder;
  public boolean isCue;
  public boolean isCueTrack;
  
  public boolean isLastPlayed;
  public boolean isFavorite;
  public boolean isVisited;
  public boolean repeat;
  public boolean hasError;
  
  public int cueStartTime;
  public int cueEndTime;
  public ListItem cueSource;
  public boolean isCurrentCueTrack;

  public ListItem(String text) {
    this.text = text;
  }
  
  public ListItem(String text, String path) {
    this.text = text;
    this.path = path;
  }

  public static ListItem newFile(String text, String path) {
    ListItem item = new ListItem(text, path);
    item.isFile = true;
    return item;
  }

  public static ListItem newDir(String text, String path) {
    ListItem item = new ListItem(text, path);
    item.isFolder = true;
    return item;
  }

  public static ListItem newCueSource(String text, String path) {
    ListItem item = new ListItem(text, path);
    item.isFile = true;
    item.isCue = true;
    return item;
  }

  public static ListItem newCueTrack(String text, ListItem source, String time, int startTime, int endTime) {
    ListItem item = new ListItem(text);
    item.isCueTrack = true;
    item.cueSource = source;
    item.time = time;
    item.cueStartTime = startTime;
    item.cueEndTime = endTime;
    return item;
  }
  
  public String toString() {
    return String.format("ListItem: [\"%s\"] \"%s\", %s, isFile: %b, isFolder: %b, isCue: %b, isCueTrack: %b", text, path, time, isFile, isFolder, isCue, isCueTrack);
  }

}
