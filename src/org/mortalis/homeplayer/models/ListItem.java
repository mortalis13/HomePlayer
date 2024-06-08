package org.mortalis.homeplayer.models;

import org.mortalis.homeplayer.R;


public class ListItem {
  
  public int icon;
  public String text;
  public String path;
  public String time;
  
  public boolean isFile;
  public boolean isLastPlayed;
  public boolean isFavorite;
  public boolean isVisited;
  public boolean repeat;
  public boolean hasError;

  public ListItem(String text, String path, boolean isFile) {
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
