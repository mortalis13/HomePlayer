package org.mortalis.homeplayer;

import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.view.GestureDetectorCompat;
import androidx.recyclerview.widget.RecyclerView;

import static org.mortalis.homeplayer.Fun.log;


public class RecyclerTouchListener extends RecyclerView.SimpleOnItemTouchListener {
  
  private GestureDetectorCompat gestureDetector;
  
  public RecyclerTouchListener(final RecyclerView recyclerView) {
    gestureDetector = new GestureDetectorCompat(recyclerView.getContext(), new GestureDetector.SimpleOnGestureListener() {
      public void onLongPress(MotionEvent event) {
        var viewHolder = getViewHolder(recyclerView, event.getX(), event.getY());
        viewHolder.processLongPress();
      }
    });
  }
  
  public boolean onInterceptTouchEvent(RecyclerView recyclerView, MotionEvent event) {
    gestureDetector.onTouchEvent(event);
    int action = event.getAction();
    
    if (action == MotionEvent.ACTION_DOWN) {
      var viewHolder = getViewHolder(recyclerView, event.getX(), event.getY());
      int currentPos = viewHolder.getBindingAdapterPosition();
      
      FilesAdapter adapter = (FilesAdapter) recyclerView.getAdapter();
      adapter.hideActiveItemMenu(currentPos);
    }
    
    return false;
  }
  
  private FilesAdapter.ItemViewHolder getViewHolder(RecyclerView recyclerView, float x, float y) {
    View view = recyclerView.findChildViewUnder(x, y);
    FilesAdapter.ItemViewHolder viewHolder = (FilesAdapter.ItemViewHolder) recyclerView.findContainingViewHolder(view);
    return viewHolder;
  }

}
