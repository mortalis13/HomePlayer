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
      private static final int SWIPE_THRESHOLD = 100;
      private static final int SWIPE_VELOCITY_THRESHOLD = 100;
      
      public void onLongPress(MotionEvent event) {
        var viewHolder = getViewHolder(recyclerView, event.getX(), event.getY());
        if (viewHolder == null) {
          loge("item onLongPress: viewHolder is null");
          return;
        }
        viewHolder.processLongPress();
      }
      
      @Override
      public boolean onFling(MotionEvent event1, MotionEvent event2, float velocityX, float velocityY) {
        try {
          float diffY = event2.getY() - event1.getY();
          float diffX = event2.getX() - event1.getX();
          
          if (Math.abs(diffX) > Math.abs(diffY)) {
            if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
              if (diffX > 0) {
                onSwipeRight();
                
                var viewHolder = getViewHolder(recyclerView, event1.getX(), event1.getY());
                if (viewHolder == null) {
                  loge("item onLongPress: viewHolder is null");
                }
                else {
                  viewHolder.setItemSwiped();
                }
              }
              return true;
            }
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
    gestureDetector.onTouchEvent(event);

    int action = event.getAction();
    if (action == MotionEvent.ACTION_DOWN) {
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
