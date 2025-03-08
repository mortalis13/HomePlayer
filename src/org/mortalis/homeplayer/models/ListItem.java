package org.mortalis.homeplayer.models;

import org.mortalis.homeplayer.R;


public class ListItem {
  
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
  }

}
