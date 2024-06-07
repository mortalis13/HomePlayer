package org.mortalis.homeplayer;

import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.view.GestureDetectorCompat;
import androidx.recyclerview.widget.RecyclerView;

import static org.mortalis.homeplayer.Fun.log;
import static org.mortalis.homeplayer.Fun.loge;


public class RecyclerTouchListener extends RecyclerView.SimpleOnItemTouchListener {
  
  private final GestureDetectorCompat gestureDetector;
  
  public void onSwipeRight() {}
  
  public RecyclerTouchListener(final RecyclerView recyclerView) {
    gestureDetector = new GestureDetectorCompat(recyclerView.getContext(), new GestureDetector.SimpleOnGestureListener() {
      public void onLongPress(MotionEvent event) {
        var viewHolder = getViewHolder(recyclerView, event.getX(), event.getY());
        if (viewHolder == null) {
          loge("item onLongPress: viewHolder is null");
          return;
        }
        viewHolder.processLongPress();
      }
      
      public boolean onFling(MotionEvent event1, MotionEvent event2, float velocityX, float velocityY) {
        try {
          if (Gestures.isSwipedRight(event1, event2, velocityX, velocityY)) {
            onSwipeRight();
            return true;
          }
        }
        catch (Exception e) {
          e.printStackTrace();
        }
        
        return false;
      }
    });
  }
  
  public boolean onInterceptTouchEvent(RecyclerView recyclerView, MotionEvent event) {
    boolean gestureDone = gestureDetector.onTouchEvent(event);
    if (gestureDone) return true;

    int action = event.getAction();
    
    if (action == MotionEvent.ACTION_DOWN) {
      // Hide item item on any item touch
      boolean hideMenu = true;

      var viewHolder = getViewHolder(recyclerView, event.getX(), event.getY());
      if (viewHolder == null) {
        loge("item ACTION_DOWN: viewHolder is null");
        return false;
      }

      if (viewHolder.itemMenuPanel.getVisibility() == View.VISIBLE) {
        float menuPanelX = viewHolder.itemMenuPanel.getChildAt(0).getX();
        if (event.getX() >= menuPanelX) hideMenu = false;
      }
      
      if (hideMenu) {
        FilesAdapter adapter = (FilesAdapter) recyclerView.getAdapter();
        if (adapter != null) adapter.hideActiveItemMenu();
      }
    }
    
    return false;
  }
  
  private FilesAdapter.ItemViewHolder getViewHolder(RecyclerView recyclerView, float x, float y) {
    View view = recyclerView.findChildViewUnder(x, y);
    if (view == null) return null;
    var viewHolder = (FilesAdapter.ItemViewHolder) recyclerView.findContainingViewHolder(view);
    return viewHolder;
  }

}
