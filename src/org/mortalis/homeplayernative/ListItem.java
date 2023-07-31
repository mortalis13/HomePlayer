package org.mortalis.homeplayernative;

public class ListItem {
  
  int icon;
  String text;
  String path;
  String time;
  
  boolean isFile;
  boolean isLastPlayed;
  boolean isFavorite;
  boolean isVisited;
  boolean repeat;
  boolean hasError;

  ListItem(String text, String path, boolean isFile) {
    this.text = text;
    this.path = path;
    this.isFile = isFile;
    
    if (isFile && path != null) {
      icon = R.drawable.round_audio_file_black_36;
    }
    else if (!isFile) {
      icon = R.drawable.round_folder_black_36;
    }
  }

}
