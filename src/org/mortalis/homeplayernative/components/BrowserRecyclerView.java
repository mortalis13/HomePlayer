package org.mortalis.homeplayernative.components;

import android.content.Context;
import android.util.AttributeSet;

import com.google.android.material.color.MaterialColors;

import org.mortalis.homeplayernative.R;
import org.mortalis.homeplayernative.fastscroll.FastScrollDelegate;
import org.mortalis.homeplayernative.fastscroll.FastScrollRecyclerView;


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
    builder.thumbNormalColor(MaterialColors.getColor(this, R.attr.scrollbarDefaultBackground));
    builder.thumbPressedColor(MaterialColors.getColor(this, R.attr.scrollbarPressedBackground));
    return builder.build();
  }
  
}
