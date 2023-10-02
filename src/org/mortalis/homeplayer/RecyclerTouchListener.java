package org.mortalis.homeplayer;

import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.view.GestureDetectorCompat;
import androidx.recyclerview.widget.RecyclerView;

import static org.mortalis.homeplayer.Fun.log;


public class RecyclerTouchListener extends RecyclerView.SimpleOnItemTouchListener {
  
  private final GestureDetectorCompat gestureDetector;
  
  public RecyclerTouchListener(final RecyclerView recyclerView) {
    gestureDetector = new GestureDetectorCompat(recyclerView.getContext(), new GestureDetector.SimpleOnGestureListener() {
      public void onLongPress(MotionEvent event) {
        var viewHolder = getViewHolder(recyclerView, event.getX(), event.getY());
        if (viewHolder == null) {
          log("item onLongPress: viewHolder is null");
          return;
        }
        viewHolder.processLongPress();
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
        log("item ACTION_DOWN: viewHolder is null");
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
