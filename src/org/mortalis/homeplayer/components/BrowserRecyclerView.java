package org.mortalis.homeplayer.components;

import org.mortalis.homeplayer.fastscroll.FastScrollDelegate;
import org.mortalis.homeplayer.fastscroll.FastScrollRecyclerView;
import org.mortalis.homeplayer.Fun;
import org.mortalis.homeplayer.R;

import android.content.Context;
import android.util.AttributeSet;
import androidx.core.content.ContextCompat;


public class BrowserRecyclerView extends FastScrollRecyclerView {

  public BrowserRecyclerView(Context context) {
    this(context, null);
  }

  public BrowserRecyclerView(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public BrowserRecyclerView(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
  }
  
  @Override
  public FastScrollDelegate createFastScrollDelegate(Context context) {
    FastScrollDelegate.Builder builder = new FastScrollDelegate.Builder(this);
    var w = getResources().getDimension(R.dimen.scrollbar_width);
    var h = getResources().getDimension(R.dimen.scrollbar_height);
    builder.width(w).height(h);
    builder.thumbPressedColor(ContextCompat.getColor(context, R.color.scrollbar_pressed_color));
    return builder.build();
  }
  
}
