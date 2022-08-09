package org.mortalis.homeplayer;

public class ListItem {
  int icon;
  String text;
  String path;
  String time;
  
  boolean isFile;
  boolean isLastPlayed;
  boolean isFavorite;
  
  ListItem(String text, String path) {
    this(text, path, false);
  }
  
  ListItem(String text, String path, boolean isFile) {
    this.text = text;
    this.path = path;
    this.isFile = isFile;
    
    if (isFile && path != null) {
      icon = this.getFileIconByType(path);
    }
    else if (!isFile) {
      icon = R.drawable.round_folder_black_36;
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
