package org.mortalis.homeplayer.components;

import org.mortalis.homeplayer.R;
import org.mortalis.homeplayer.Fun;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Path;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;


public class SimplePaintView extends ImageView {
  
  private Paint canvasPaint;
  
  private RectF canvasRect;
  private Path canvasPath;
  
  private float itemWidth;
  private float itemHeight;
  
  public SimplePaintView(Context context) {
    this(context, null);
  }
  
  public SimplePaintView(Context context, AttributeSet attrs) {
    super(context, attrs, 0);
    init(context);
  }
  
  
  private void init(Context context) {
    this.canvasPaint = new Paint();
    this.canvasPaint.setAntiAlias(true);
    this.canvasPaint.setColor(ContextCompat.getColor(context, R.color.favorive_mark_color));
    this.canvasPaint.setStyle(Paint.Style.FILL);
    
    this.itemWidth = Math.round(getResources().getDimension(R.dimen.favorite_mark_width));
    this.itemHeight = Math.round(getResources().getDimension(R.dimen.favorite_mark_height));
  }
  
  @Override
  protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    float px1 = 0;
    float py1 = (float) h;
    float px2 = px1;
    float py2 = py1 - this.itemHeight;
    float px3 = this.itemWidth;
    float py3 = py1;
    
    canvasPath = new Path();
    canvasPath.moveTo(px1, py1);
    canvasPath.lineTo(px2, py2);
    canvasPath.lineTo(px3, py3);
    canvasPath.lineTo(px1, py1);
    
    invalidate();
  }
  
  @Override
  protected void onDraw(Canvas canvas) {
    canvas.drawPath(canvasPath, canvasPaint);
  }
  
}
