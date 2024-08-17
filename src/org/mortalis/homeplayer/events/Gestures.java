package org.mortalis.homeplayer.events;

import android.view.MotionEvent;

import static org.mortalis.homeplayer.Fun.log;

public class Gestures {
  
  private static final int SWIPE_THRESHOLD = 100;
  private static final int SWIPE_VELOCITY_THRESHOLD = 100;
  
  public static final int SWIPE_LIMIT_WINDOW = 100;  // vertical limit for horizontal swipe and horizontal limit for vertical swipe
  
  public static boolean isSwipedLeft(MotionEvent event1, MotionEvent event2, float velocityX, float velocityY) {
    float diffY = event2.getY() - event1.getY();
    float diffX = event2.getX() - event1.getX();
    
    boolean result = (
      Math.abs(diffX) > Math.abs(diffY) &&
      Math.abs(diffX) > SWIPE_THRESHOLD &&
      Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD &&
      diffX < 0
    );
    return result;
  }
  
  public static boolean isSwipedRight(MotionEvent event1, MotionEvent event2, float velocityX, float velocityY) {
    float diffY = event2.getY() - event1.getY();
    float diffX = event2.getX() - event1.getX();
    
    boolean result = (
      Math.abs(diffX) > Math.abs(diffY) &&
      Math.abs(diffX) > SWIPE_THRESHOLD &&
      Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD &&
      Math.abs(diffY) < SWIPE_LIMIT_WINDOW &&
      diffX > 0
    );
    return result;
  }
  
  public static boolean isSwipedUp(MotionEvent event1, MotionEvent event2, float velocityX, float velocityY) {
    float diffY = event2.getY() - event1.getY();
    float diffX = event2.getX() - event1.getX();
    
    boolean result = (
      Math.abs(diffX) < Math.abs(diffY) &&
      Math.abs(diffY) > SWIPE_THRESHOLD &&
      Math.abs(velocityY) > SWIPE_VELOCITY_THRESHOLD &&
      diffY < 0
    );
    return result;
  }
  
  public static boolean isSwipedDown(MotionEvent event1, MotionEvent event2, float velocityX, float velocityY) {
    float diffY = event2.getY() - event1.getY();
    float diffX = event2.getX() - event1.getX();
    
    boolean result = (
      Math.abs(diffX) < Math.abs(diffY) &&
      Math.abs(diffY) > SWIPE_THRESHOLD &&
      Math.abs(velocityY) > SWIPE_VELOCITY_THRESHOLD &&
      diffY > 0
    );
    return result;
  }

}
