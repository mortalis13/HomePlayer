package org.mortalis.homeplayer.components;

import org.mortalis.homeplayer.R;
import org.mortalis.homeplayer.Fun;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import androidx.core.content.ContextCompat;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;


public class SliderView extends View {
  
  private boolean sliderEnabled;
  
  private Paint canvasPaint;
  private Paint borderPaint;
  private Paint progressPaint;
  
  private RectF canvasRect;
  private RectF progressRect;
  private RectF borderRect;
  
  private int canvasWidth;
  private int canvasHeight;
  
  private float borderWidth;
  private float snapPosX;
  private float leftOffset;
  private float topOffset;
  
  private int maxValue;
  private int progress;
  
  private ProgressChangeListener progressChangeListener;
  
  
  public SliderView(Context context) {
    this(context, null);
  }
  
  public SliderView(Context context, AttributeSet attrs) {
    super(context, attrs, 0);
    init(context);
  }
  
  
  private void init(Context context) {
    this.borderWidth = (float) Math.ceil(getResources().getDimension(R.dimen.plain_slider_border_width));
    this.snapPosX = (int) getResources().getDimension(R.dimen.plain_slider_left_right_snap_size);
    
    this.leftOffset = this.borderWidth;
    this.topOffset = this.borderWidth;
    
    this.canvasPaint = new Paint();
    this.canvasPaint.setAntiAlias(true);
    this.canvasPaint.setColor(ContextCompat.getColor(context, R.color.plain_slider_background_color));
    this.canvasPaint.setStyle(Paint.Style.FILL);
    
    this.progressPaint = new Paint();
    this.progressPaint.setAntiAlias(true);
    this.progressPaint.setColor(ContextCompat.getColor(context, R.color.plain_slider_progress_color));
    this.progressPaint.setStyle(Paint.Style.FILL);
    
    this.borderPaint = new Paint();
    this.borderPaint.setAntiAlias(true);
    this.borderPaint.setColor(ContextCompat.getColor(context, R.color.plain_slider_border_color));
    this.borderPaint.setStrokeWidth(this.borderWidth);
    this.borderPaint.setStyle(Paint.Style.STROKE);
    
    this.canvasRect = new RectF();
    this.progressRect = new RectF();
    this.borderRect = new RectF();
  }
  
  
  public void setMax(int value) {
    this.maxValue = value;
  }
  
  public void setProgress(int value) {
    this.progress = value;
    rebuildUI();
  }
  
  private void rebuildUI() {
    this.canvasRect.set(0, 0, this.canvasWidth, this.canvasHeight);
    
    float left   = this.borderWidth / 2;
    float top    = this.borderWidth / 2;
    float right  = this.canvasWidth  - this.borderWidth / 2;
    float bottom = this.canvasHeight - this.borderWidth / 2;
    this.borderRect.set(left, top, right, bottom);
    
    if (this.maxValue == 0) setMax(this.canvasWidth);
    if (this.maxValue == 0) return;
    
    float _progress = (float) this.progress * this.canvasWidth / this.maxValue;
    
    left   = this.leftOffset;
    top    = this.topOffset;
    right  = left + _progress;
    bottom = this.canvasHeight - this.borderWidth;
    this.progressRect.set(left, top, right, bottom);
    
    invalidate();
  }
  
  
  @Override
  public boolean onTouchEvent(MotionEvent event) {
    if (!this.sliderEnabled) return true;
    
    int action = event.getAction();
    int x = (int) event.getX();
    
    if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
      if (x < this.snapPosX) x = 0;
      if (x > this.canvasWidth - this.snapPosX) x = this.canvasWidth;
      
      int _progress = (int) ((float) x * this.maxValue / this.canvasWidth);
      setProgress(_progress);
      
      if (this.progressChangeListener != null) {
        this.progressChangeListener.onChanging(this.progress);
      }
    }
    
    if (action == MotionEvent.ACTION_UP) {
      if (this.progressChangeListener != null) {
        this.progressChangeListener.onChanged(this.progress);
      }
    }
    
    return true;
  }
  
  @Override
  protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    if (w == 0 || h == 0) return;
    this.canvasWidth = w;
    this.canvasHeight = h;
    rebuildUI();
  }
  
  @Override
  protected void onDraw(Canvas canvas) {
    canvas.drawRect(this.canvasRect, this.canvasPaint);
    canvas.drawRect(this.progressRect, this.progressPaint);
    canvas.drawRect(this.borderRect, this.borderPaint);
  }
  
  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    setMeasuredDimension(measureWidth(widthMeasureSpec), measureHeight(heightMeasureSpec));
  }
  
  
  public void enable() {
    this.sliderEnabled = true;
  }
  
  public void disable() {
    this.sliderEnabled = false;
  }
  
  
  private int measureHeight(int measureSpec) {
    int size = getPaddingTop() + getPaddingBottom();
    return resolveSizeAndState(size, measureSpec, 0);
  }
  
  private int measureWidth(int measureSpec) {
    int size = getPaddingLeft() + getPaddingRight();
    return resolveSizeAndState(size, measureSpec, 0);
  }
  
  
  // ------------------ Getters ------------------
  
  public void setProgressChangeListener(ProgressChangeListener progressChangeListener) {
    this.progressChangeListener = progressChangeListener;
  }
  
  
  // ------------------ Classes ------------------
  
  public interface ProgressChangeListener {
    public void onChanging(int value);
    public void onChanged(int value);
  }
  
}
