package org.mortalis.homeplayer.components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Picture;
import android.graphics.BlendMode;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.google.android.material.color.MaterialColors;

import org.mortalis.homeplayer.Fun;
import org.mortalis.homeplayer.R;
import static org.mortalis.homeplayer.Fun.log;


public class ProgressSliderView extends View {
  
  private static final float MAX_VERTICAL_DISTANCE = Fun.dpToPx(100);
  private static final int WAVEFORM_PAD = (int) Fun.dpToPx(2);
  
  private boolean sliderEnabled;
  private boolean touchEnabled;
  
  private Paint canvasPaint;
  private Paint borderPaint;
  private Paint progressPaint;
  private Paint waveformPaint;
  
  private RectF canvasRect;
  private RectF progressRect;
  private RectF borderRect;
  
  private Picture waveformPicture;
  
  private int canvasWidth;
  private int canvasHeight;
  
  private int workingWidth;
  private int workingHeight;
  
  private int borderWidth;
  private float snapPosX;
  private int progressColor;
  
  private int maxValue;
  private int progress;

  private short[] samples;
  
  private ProgressChangeListener progressChangeListener;
  
  
  public ProgressSliderView(Context context) {
    this(context, null);
  }
  
  public ProgressSliderView(Context context, AttributeSet attrs) {
    super(context, attrs, 0);
    init();
  }
  
  
  private void init() {
    this.borderWidth = (int) Math.ceil(getResources().getDimension(R.dimen.slider_border_width));
    this.snapPosX = (int) getResources().getDimension(R.dimen.slider_left_right_snap_size);
    this.progressColor = MaterialColors.getColor(this, R.attr.sliderProgressColor);
    
    this.canvasPaint = new Paint();
    this.canvasPaint.setColor(MaterialColors.getColor(this, R.attr.sliderBackgroundColor));
    this.canvasPaint.setStyle(Paint.Style.FILL);
    
    this.progressPaint = new Paint();
    this.progressPaint.setColor(progressColor);
    this.progressPaint.setStyle(Paint.Style.FILL);
    
    this.waveformPaint = new Paint();
    this.waveformPaint.setColor(MaterialColors.getColor(this, R.attr.sliderWaveformColor));
    // this.waveformPaint.setBlendMode(BlendMode.COLOR_DODGE);
    // this.waveformPaint.setBlendMode(BlendMode.PLUS);
    this.waveformPaint.setBlendMode(BlendMode.SCREEN);
    
    this.borderPaint = new Paint();
    this.borderPaint.setColor(MaterialColors.getColor(this, R.attr.sliderBorderColor));
    this.borderPaint.setStrokeWidth(this.borderWidth);
    this.borderPaint.setStyle(Paint.Style.STROKE);
    
    this.canvasRect = new RectF();
    this.progressRect = new RectF();
    this.borderRect = new RectF();
  }
  
  
  public void reset() {
    setProgress(0);
    this.touchEnabled = true;
  }
  
  public void setMax(int value) {
    this.maxValue = value;
  }
  
  public void setProgress(int value) {
    this.progress = value;
    rebuildProgess();
    invalidate();
  }
  
  public boolean atMaxProgress() {
    return this.progress == this.maxValue;
  }
  
  private void rebuildUI() {
    this.canvasRect.set(0, 0, this.canvasWidth, this.canvasHeight);
    
    if (this.borderWidth != 0) {
      float left   = (float) this.borderWidth / 2;
      float top    = (float) this.borderWidth / 2;
      float right  = this.canvasWidth  - (float) this.borderWidth / 2;
      float bottom = this.canvasHeight - (float) this.borderWidth / 2;
      this.borderRect.set(left, top, right, bottom);
    }
    
    if (this.maxValue == 0) setMax(this.workingWidth);
    if (this.maxValue == 0) return;
    
    rebuildProgess();
    invalidate();
  }
  
  private void rebuildProgess() {
    float _progress = (float) this.progress * this.workingWidth / this.maxValue;
    
    float left   = this.borderWidth;
    float top    = this.borderWidth;
    float right  = left + _progress;
    float bottom = this.canvasHeight - this.borderWidth;
    this.progressRect.set(left, top, right, bottom);
  }
  
  
  @Override
  public boolean onTouchEvent(MotionEvent event) {
    if (!this.sliderEnabled) return true;
    
    int action = event.getAction();
    int x = (int) event.getX() - this.borderWidth;
    int y = (int) event.getY() - this.borderWidth;
    
    if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
      if (!this.touchEnabled) return true;
      
      if (x < this.snapPosX) x = 0;
      if (x > this.workingWidth - this.snapPosX) x = this.workingWidth;
      
      // Detect if vertical offset is greater than max and reset the position
      int outerVerticalOffset = 0;
      if (y < 0) outerVerticalOffset = Math.abs(y);
      if (y > this.workingHeight) outerVerticalOffset = y - this.workingHeight;
      
      if (outerVerticalOffset > MAX_VERTICAL_DISTANCE) {
        this.touchEnabled = false;
        cancelTouch();
        return true;
      }
      
      int _progress = (int) ((float) x * this.maxValue / this.workingWidth);
      setProgress(_progress);
      
      sendPosition(this.progress);
    }
    
    if (action == MotionEvent.ACTION_UP) {
      if (!this.touchEnabled) {
        this.touchEnabled = true;
        return true;
      }
      
      releaseTouch(this.progress);
    }
    
    return true;
  }
  
  @Override
  protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    if (w == 0 || h == 0) return;
    this.canvasWidth = w;
    this.canvasHeight = h;
    this.workingWidth = this.canvasWidth - this.borderWidth * 2;
    this.workingHeight = this.canvasHeight - this.borderWidth * 2;
    rebuildUI();
  }
  
  @Override
  protected void onDraw(Canvas canvas) {
    canvas.drawRect(this.canvasRect, this.canvasPaint);
    canvas.drawRect(this.borderRect, this.borderPaint);
    canvas.drawRect(this.progressRect, this.progressPaint);
    drawWaveform(canvas);
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
  
  private void cancelTouch() {
    if (this.progressChangeListener != null) {
      this.progressChangeListener.onCancelled();
    }
  }
  
  private void sendPosition(int position) {
    if (this.progressChangeListener != null) {
      this.progressChangeListener.onChanging(position);
    }
  }
  
  private void releaseTouch(int position) {
    if (this.progressChangeListener != null) {
      this.progressChangeListener.onChanged(position);
    }
  }
  
  public void restoreProgressColor() {
    if (this.progressPaint.getColor() != this.progressColor) {
      this.progressPaint.setColor(this.progressColor);
      invalidate();
    }
  }
  
  public void changeProgressColor(int color) {
    if (this.progressPaint.getColor() != color) {
      this.progressPaint.setColor(color);
      invalidate();
    }
  }
  
  public void updateWaveform(short[] samples) {
    // Saving reference to possibly prevent SIGSEGV after returning from JNI
    this.samples = samples;
    
    this.waveformPicture = new Picture();
    Canvas waveformCanvas = this.waveformPicture.beginRecording(this.canvasWidth, this.canvasHeight);
    
    float center = (float) this.canvasHeight / 2;
    
    for (int i = 0; i < samples.length; i++) {
      float h = samples[i];

      float x = this.borderWidth + i;
      float y0 = center - h;
      float y1 = center + h + 1;
      
      waveformCanvas.drawLine(x, y0, x, y1, this.waveformPaint);
    }
    
    this.waveformPicture.endRecording();
    postInvalidate();
  }
  
  public void clearWaveform() {
    this.waveformPicture = null;
    invalidate();
  }
  
  private void drawWaveform(Canvas canvas) {
    if (waveformPicture == null) return;
    canvas.drawPicture(waveformPicture);
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
  
  public int getWaveformWidth() {
    return this.workingWidth;
  }
  
  public int getWaveformHeight() {
    return this.workingHeight - WAVEFORM_PAD;
  }
  

  // ------------------ Classes ------------------
  
  public interface ProgressChangeListener {
    public void onChanging(int value);
    public void onChanged(int value);
    public void onCancelled();
  }
  
}
