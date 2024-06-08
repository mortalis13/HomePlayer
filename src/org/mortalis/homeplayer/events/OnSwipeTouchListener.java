package org.mortalis.homeplayer.events;

import android.content.Context;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;


public class OnSwipeTouchListener implements OnTouchListener {
  
  private final GestureDetector gestureDetector;
  
  public OnSwipeTouchListener(Context context) {
    gestureDetector = new GestureDetector(context, new GestureListener());
  }
  
  @Override
  public boolean onTouch(View view, MotionEvent event) {
    return gestureDetector.onTouchEvent(event);
  }
  
  protected void processLongPress() {}
  protected void processDoubleTap(MotionEvent e) {}
  protected void processDown() {}
  
  protected void onSwipeRight() {}
  protected void onSwipeLeft() {}
  protected void onSwipeUp() {}
  protected void onSwipeDown() {}
  
  
  private final class GestureListener extends SimpleOnGestureListener {
    @Override
    public void onLongPress(MotionEvent event) {
      processLongPress();
    }
    
    @Override
    public boolean onDown(MotionEvent event) {
      processDown();
      return true;
    }
    
    @Override
    public boolean onDoubleTap(MotionEvent event) {
      processDoubleTap(event);
      return true;
    }
    
    @Override
    public boolean onFling(MotionEvent event1, MotionEvent event2, float velocityX, float velocityY) {
      try {
        if (Gestures.isSwipedLeft(event1, event2, velocityX, velocityY)) {
          onSwipeLeft();
          return true;
        }
        
        if (Gestures.isSwipedRight(event1, event2, velocityX, velocityY)) {
          onSwipeRight();
          return true;
        }
        
        if (Gestures.isSwipedUp(event1, event2, velocityX, velocityY)) {
          onSwipeUp();
          return true;
        }
        
        if (Gestures.isSwipedDown(event1, event2, velocityX, velocityY)) {
          onSwipeDown();
          return true;
        }
      }
      catch (Exception e) {
        e.printStackTrace();
      }
      
      return false;
    }
  }
  
}
