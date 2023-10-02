package org.mortalis.homeplayer.components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.google.android.material.color.MaterialColors;

import org.mortalis.homeplayer.R;

import static org.mortalis.homeplayer.Fun.log;


public class RangeSliderView extends View {
  
  private static final int MIN_RANGE = 100;
  
  private Paint canvasPaint;
  private Paint progressPaint;
  
  private RectF canvasRect;
  private RectF progressRect;
  
  private int canvasWidth;
  private int canvasHeight;
  
  private int maxValue;
  private int progressStart;
  private int progressEnd;
  private float progressStep;
  
  private int pointer;
  private float startX;
  private int startValue;
  
  private boolean rangeStartSelected;
  private boolean rangeEndSelected;

  private ProgressListener progressListener;
  
  
  public RangeSliderView(Context context) {
    this(context, null);
  }
  
  public RangeSliderView(Context context, AttributeSet attrs) {
    super(context, attrs, 0);
    init();
  }
  
  
  private void init() {
    this.canvasPaint = new Paint();
    this.canvasPaint.setColor(MaterialColors.getColor(this, R.attr.rangeSliderBackgroundColor));
    this.canvasPaint.setStyle(Paint.Style.FILL);
    
    this.progressPaint = new Paint();
    this.progressPaint.setColor(MaterialColors.getColor(this, R.attr.rangeSliderProgressColor));
    this.progressPaint.setStyle(Paint.Style.FILL);
    
    this.canvasRect = new RectF();
    this.progressRect = new RectF();
  }
  
  
  public void reset() {
    this.progressStart = 0;
    this.progressEnd = maxValue;
    rebuildUI();
    if (this.progressListener != null) {
      this.progressListener.onReset();
    }
  }
  
  public void setMax(int value) {
    this.maxValue = value;
  }
  
  public void setProgressStart(int value) {
    if (value < 0) value = 0;
    if (value > maxValue) value = maxValue;
    if (value >= progressEnd) value = progressEnd - MIN_RANGE;
    
    this.progressStart = value;
    rebuildProgress();
    invalidate();
  }
  
  public void setProgressEnd(int value) {
    if (value < 0) value = 0;
    if (value > maxValue) value = maxValue;
    if (value <= progressStart) value = progressStart + MIN_RANGE;
    
    this.progressEnd = value;
    rebuildProgress();
    invalidate();
  }
  
  public int getProgressStart() {
    return this.progressStart;
  }
  
  public int getProgressEnd() {
    return this.progressEnd;
  }
  
  private void rebuildUI() {
    this.canvasRect.set(0, 0, this.canvasWidth, this.canvasHeight);

    if (this.maxValue == 0) setMax(this.canvasWidth);
    if (this.maxValue == 0) return;
    
    this.progressStep = (float) this.canvasWidth / this.maxValue;
    
    rebuildProgress();
    invalidate();
  }
  
  private void rebuildProgress() {
    float left   = this.progressStart * this.progressStep;
    float top    = 0;
    float right  = this.progressEnd * this.progressStep;
    float bottom = this.canvasHeight;
    this.progressRect.set(left, top, right, bottom);
  }
  
  @Override
  public boolean onTouchEvent(MotionEvent event) {
    int action = event.getActionMasked();
    
    if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN || action == MotionEvent.ACTION_POINTER_UP) {
      pointer = event.getActionIndex();
      float x = event.getX(pointer);
      
      if (action == MotionEvent.ACTION_POINTER_UP) {
        pointer = 0;
        x = event.getX((event.getActionIndex() == 0) ? 1: 0);
      }
      
      startX = x;
      
      float distanceToStart = Math.abs(x - this.progressStart * this.progressStep);
      float distanceToEnd = Math.abs(x - this.progressEnd * this.progressStep);
      
      if (distanceToStart < distanceToEnd) {
        rangeStartSelected = true;
        rangeEndSelected = false;
        startValue = this.progressStart;
      }
      else {
        rangeEndSelected = true;
        rangeStartSelected = false;
        startValue = this.progressEnd;
      }
    }
    
    if (action == MotionEvent.ACTION_MOVE) {
      if (pointer >= event.getPointerCount()) return true;
      float x = event.getX(pointer);
      
      float offsetX = x - startX;
      int progress = startValue + (int) (offsetX / this.progressStep);
      
      if (progress < 0) progress = 0;
      if (progress > maxValue) progress = maxValue;
      
      if (rangeStartSelected) {
        if (progress > this.progressEnd) progress = this.progressEnd - MIN_RANGE;
        setProgressStart(progress);
      }
      else if (rangeEndSelected) {
        if (progress < this.progressStart) progress = this.progressStart + MIN_RANGE;
        setProgressEnd(progress);
      }
      
      sendPosition(this.progressStart, this.progressEnd);
    }
    
    if (action == MotionEvent.ACTION_UP) {
      rangeStartSelected = false;
      rangeEndSelected = false;
      pointer = 0;
      startX = 0;
      startValue = 0;
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
  }
  
  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    setMeasuredDimension(measureWidth(widthMeasureSpec), measureHeight(heightMeasureSpec));
  }
  
  
  private void sendPosition(int positionStart, int positionEnd) {
    if (this.progressListener != null) {
      this.progressListener.onChanging(positionStart, positionEnd);
    }
  }
  
  private int measureHeight(int measureSpec) {
    int size = getPaddingTop() + getPaddingBottom();
    return resolveSizeAndState(size, measureSpec, 0);
  }
  
  private int measureWidth(int measureSpec) {
    int size = getPaddingLeft() + getPaddingRight();
    return resolveSizeAndState(size, measureSpec, 0);
  }
  
  
  public void setProgressListener(ProgressListener progressListener) {
    this.progressListener = progressListener;
  }

  public interface ProgressListener {
    public void onChanging(int valueStart, int valueEnd);
    public void onReset();
  }
  
}
