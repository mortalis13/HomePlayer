package org.mortalis.homeplayer.components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.content.ContextCompat;

import org.mortalis.homeplayer.Fun;
import org.mortalis.homeplayer.R;
import static org.mortalis.homeplayer.Fun.log;


public class SliderView extends View {
  
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
  
  private int canvasWidth;
  private int canvasHeight;
  
  private float borderWidth;
  private float snapPosX;
  private float leftOffset;
  private float topOffset;
  
  private int maxValue;
  private int progress;
  
  private short[] samples;
  
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
    
    this.waveformPaint = new Paint();
    this.waveformPaint.setAntiAlias(true);
    this.waveformPaint.setColor(0x66ff0000);
    
    this.borderPaint = new Paint();
    this.borderPaint.setAntiAlias(true);
    this.borderPaint.setColor(ContextCompat.getColor(context, R.color.plain_slider_border_color));
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
    int y = (int) event.getY();
    
    if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
      if (!this.touchEnabled) return true;
      
      if (x < this.snapPosX) x = 0;
      if (x > this.canvasWidth - this.snapPosX) x = this.canvasWidth;
      
      // Detect if vertical offset is greater than max and reset the position
      int outerVerticalOffset = 0;
      if (y < 0) outerVerticalOffset = Math.abs(y);
      if (y > this.canvasHeight) outerVerticalOffset = y - this.canvasHeight;
      
      if (outerVerticalOffset > MAX_VERTICAL_DISTANCE) {
        this.touchEnabled = false;
        cancelTouch();
        return true;
      }
      
      int _progress = (int) ((float) x * this.maxValue / this.canvasWidth);
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
    rebuildUI();
  }
  
  @Override
  protected void onDraw(Canvas canvas) {
    canvas.drawRect(this.canvasRect, this.canvasPaint);
    canvas.drawRect(this.progressRect, this.progressPaint);
    canvas.drawRect(this.borderRect, this.borderPaint);
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
  
  
  public void updateWaveform(short[] samples) {
    this.samples = samples;
    invalidate();
  }
  
  public void clearWaveform() {
    samples = null;
    invalidate();
  }
  
  private void drawWaveform(Canvas canvas) {
    if (samples == null) return;
    int height = getMeasuredHeight();
    float center = (float) height / 2;
    
    for (int i = 0; i < samples.length; i++) {
      float h = samples[i];

      float x = this.borderWidth + i;
      float y0 = center - h;
      float y1 = center + h + 1;
      
      canvas.drawLine(x, y0, x, y1, waveformPaint);
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
  
  
  // ------------------ Getters ------------------
  
  public void setProgressChangeListener(ProgressChangeListener progressChangeListener) {
    this.progressChangeListener = progressChangeListener;
  }
  
  public int getWaveformWidth() {
    return this.canvasWidth - (int) this.borderWidth * 2;
  }
  
  public int getWaveformHeight() {
    return this.canvasHeight - (int) this.borderWidth * 2 - WAVEFORM_PAD;
  }
  

  // ------------------ Classes ------------------
  
  public interface ProgressChangeListener {
    public void onChanging(int value);
    public void onChanged(int value);
    public void onCancelled();
  }
  
}
