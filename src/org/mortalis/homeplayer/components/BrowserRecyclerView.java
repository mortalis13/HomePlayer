package org.mortalis.homeplayer.components;

import org.mortalis.homeplayer.fastscroll.FastScrollDelegate;
import org.mortalis.homeplayer.fastscroll.FastScrollRecyclerView;

import android.content.Context;
import android.util.AttributeSet;


public class BrowserRecyclerView extends FastScrollRecyclerView {

  public BrowserRecyclerView(Context context) {
    this(context, null);
  }

  public BrowserRecyclerView(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public BrowserRecyclerView(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    init(context);
  }
  
  private void init(Context context) {
    FastScrollDelegate.FASTSCROLLER_FADE_TIMEOUT = 3000;
  }
  
  
  @Override
  public FastScrollDelegate createFastScrollDelegate(Context context) {
    FastScrollDelegate.Builder builder = new FastScrollDelegate.Builder(this);
    builder.width(8).height(100);
    builder.thumbPressedColor(0x88356e86);
    return builder.build();
  }
  
}
