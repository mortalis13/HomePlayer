package org.mortalis.homeplayer.models;

public class CueTrack extends Track {
  
  public int startTime;
  public int endTime;

  public CueTrack(String path, String name, int startTime, int endTime) {
    super(path, name);
    this.startTime = startTime;
    this.endTime = endTime;
  }

}
