package org.mortalis.homeplayer;

import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.view.GestureDetectorCompat;
import androidx.recyclerview.widget.RecyclerView;

import static org.mortalis.homeplayer.Fun.log;


public class RecyclerTouchListener extends RecyclerView.SimpleOnItemTouchListener {
  
  private GestureDetectorCompat gestureDetector;
  
  public RecyclerTouchListener(final RecyclerView recycleView) {
    gestureDetector = new GestureDetectorCompat(recycleView.getContext(), new GestureDetector.SimpleOnGestureListener() {
      public void onLongPress(MotionEvent event) {
        View view = recycleView.findChildViewUnder(event.getX(), event.getY());
        FilesAdapter.ItemViewHolder viewHolder = (FilesAdapter.ItemViewHolder) recycleView.findContainingViewHolder(view);
        viewHolder.processLongPress();
      }
    });
  }
  
  public boolean onInterceptTouchEvent(RecyclerView recycleView, MotionEvent event) {
    gestureDetector.onTouchEvent(event);
    int action = event.getAction();
    
    if (action == MotionEvent.ACTION_DOWN) {
      FilesAdapter adapter = (FilesAdapter) recycleView.getAdapter();
      adapter.hideActiveItemMenu();
    }
    
    return false;
  }

}
