package org.mortalis.homeplayernative.components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;

import androidx.appcompat.widget.AppCompatImageView;

import com.google.android.material.color.MaterialColors;

import org.mortalis.homeplayernative.R;


public class IconOverlayView extends AppCompatImageView {
  
  private Paint canvasPaint;
  
  private RectF canvasRect;

  private float itemWidth;
  private float itemHeight;
  
  public IconOverlayView(Context context) {
    this(context, null);
  }
  
  public IconOverlayView(Context context, AttributeSet attrs) {
    super(context, attrs, 0);
    init();
  }
  
  
  private void init() {
    this.canvasPaint = new Paint();
    this.canvasPaint.setAntiAlias(true);
    this.canvasPaint.setColor(MaterialColors.getColor(this, R.attr.favoriveMarkColor));
    this.canvasPaint.setStyle(Paint.Style.FILL);
    
    this.itemWidth = Math.round(getResources().getDimension(R.dimen.favorite_mark_width));
    this.itemHeight = Math.round(getResources().getDimension(R.dimen.favorite_mark_height));
  }
  
  @Override
  protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    float left = 0;
    float top = (h - this.itemHeight) / 2;
    float right = this.itemWidth;
    float bottom = h - top;
    canvasRect = new RectF(left, top, right, bottom);
    
    invalidate();
  }
  
  @Override
  protected void onDraw(Canvas canvas) {
    canvas.drawRect(canvasRect, canvasPaint);
  }
  
}
