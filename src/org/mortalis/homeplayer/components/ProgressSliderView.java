package org.mortalis.homeplayer.components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.google.android.material.color.MaterialColors;

import org.mortalis.homeplayer.Fun;
import org.mortalis.homeplayer.R;

import static org.mortalis.homeplayer.Fun.log;


public class ProgressSliderView extends View {
  
  private static final float MAX_VERTICAL_DISTANCE = Fun.dpToPx(100);
  private static final int WAVEFORM_PAD = (int) Fun.dpToPx(6);
  private static final float LOOP_EDGE_WIDTH_05 = Fun.dpToPx(1);
  
  private boolean sliderEnabled;
  private boolean touchEnabled;
  
  private Paint canvasPaint;
  private Paint progressPaint;
  private Paint waveformPaint;
  private Paint waveformProgressPaint;
  
  private RectF canvasRect;
  private RectF progressRect;
  
  private boolean showLoop;
  private Paint loopPaint;
  private Paint loopProgressPaint;
  private RectF loopStartRect;
  private RectF loopEndRect;
  private RectF loopProgressRect;
  
  private int canvasWidth;
  private int canvasHeight;
  
  private float snapPosX;
  private int progressColor;
  
  private int maxValue;
  private int progress;
  private float progressStep;

  private short[] waveformData;
  
  private ProgressChangeListener progressChangeListener;
  
  
  public ProgressSliderView(Context context) {
    this(context, null);
  }
  
  public ProgressSliderView(Context context, AttributeSet attrs) {
    super(context, attrs, 0);
    init();
  }
  
  
  private void init() {
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
    this.waveformPaint.setAntiAlias(false);
    
    this.waveformProgressPaint = new Paint();
    this.waveformProgressPaint.setColor(MaterialColors.getColor(this, R.attr.sliderWaveformProgressColor));
    this.waveformProgressPaint.setAntiAlias(false);
    
    this.loopPaint = new Paint();
    this.loopPaint.setColor(MaterialColors.getColor(this, R.attr.sliderLoopPointsColor));
    this.loopPaint.setStyle(Paint.Style.FILL);
    
    this.loopProgressPaint = new Paint();
    this.loopProgressPaint.setColor(MaterialColors.getColor(this, R.attr.sliderLoopProgressColor));
    this.loopProgressPaint.setStyle(Paint.Style.FILL);
    
    this.canvasRect = new RectF();
    this.progressRect = new RectF();
    this.loopStartRect = new RectF();
    this.loopEndRect = new RectF();
    this.loopProgressRect = new RectF();
  }
  
  
  public void reset() {
    this.showLoop = false;
    this.touchEnabled = true;
    this.progress = 0;
    rebuildUI();
  }
  
  public void setMax(int value) {
    this.maxValue = value;
  }
  
  public void setProgress(int value) {
    this.progress = value;
    rebuildProgess();
    rebuildLoopProgress();
    invalidate();
  }
  
  public boolean atMaxProgress() {
    return this.progress == this.maxValue;
  }
  
  private void rebuildUI() {
    this.canvasRect.set(0, 0, this.canvasWidth, this.canvasHeight);
    
    if (this.maxValue == 0) setMax(this.canvasWidth);
    if (this.maxValue == 0) return;
    
    this.progressStep = (float) this.canvasWidth / this.maxValue;
    
    rebuildProgess();
    invalidate();
  }
  
  private void rebuildProgess() {
    float left   = 0;
    float top    = 0;
    float right  = this.progress * this.progressStep;
    float bottom = this.canvasHeight;
    this.progressRect.set(left, top, right, bottom);
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
      int outerVerticalOffset = (y < 0) ? Math.abs(y): y - this.canvasHeight;
      if (outerVerticalOffset > MAX_VERTICAL_DISTANCE) {
        this.touchEnabled = false;
        cancelTouch();
        return true;
      }
      
      int _progress = (int) (x / this.progressStep);
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
    
    if (showLoop) {
      canvas.drawRect(this.loopProgressRect, this.loopProgressPaint);
    }
    
    drawWaveform(canvas);
    
    if (showLoop) {
      canvas.drawRect(this.loopStartRect, this.loopPaint);
      canvas.drawRect(this.loopEndRect, this.loopPaint);
    }
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
  
  public void updateWaveform(short[] waveformData) {
    this.waveformData = waveformData;
    postInvalidate();
  }
  
  public void clearWaveform() {
    this.waveformData = null;
    invalidate();
  }
  
  private void drawWaveform(Canvas canvas) {
    if (waveformData == null) return;
    float center = (float) this.canvasHeight / 2;
    int x = 0;
    
    for (int i = 0; i < waveformData.length; i+=2, x++) {
      // Line from h1 to h2
      float h1 = waveformData[i];
      float h2 = waveformData[i+1];
      float y0 = center - h1;
      float y1 = center - h2;
      
      Paint paint = (x >= progressRect.right) ? this.waveformPaint: this.waveformProgressPaint;
      canvas.drawLine(x, y0, x, y1, paint);
    }
  }
  
  
  public void setLoopPoints(boolean loopEnabled, int loopStart, int loopEnd) {
    this.showLoop = loopEnabled;
    loopStartRect.set(0, 0, 0, 0);
    loopEndRect.set(0, 0, 0, 0);
    
    if (this.showLoop) {
      float loopStartPx = (float) loopStart * this.progressStep;
      if (loopStart != 0) {
        loopStartRect.set(loopStartPx - LOOP_EDGE_WIDTH_05, 0, loopStartPx + LOOP_EDGE_WIDTH_05, this.canvasHeight);
      }
      
      if (loopEnd != this.maxValue) {
        float loopEndPx = (float) loopEnd * this.progressStep;
        loopEndRect.set(loopEndPx - LOOP_EDGE_WIDTH_05, 0, loopEndPx + LOOP_EDGE_WIDTH_05, this.canvasHeight);
      }
    }
    
    rebuildLoopProgress();
    invalidate();
  }
  
  private void rebuildLoopProgress() {
    loopProgressRect.set(0, 0, 0, 0);
    if (this.showLoop && this.progressRect.right > loopStartRect.left) {
      float right = this.progressRect.right;
      if (right > loopEndRect.left && loopEndRect.left != 0) right = loopEndRect.left;
      loopProgressRect.set(loopStartRect.left, 0, right, this.canvasHeight);
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
    return this.canvasWidth;
  }
  
  public int getWaveformHeight() {
    return this.canvasHeight - WAVEFORM_PAD;
  }
  

  // ------------------ Classes ------------------
  
  public interface ProgressChangeListener {
    public void onChanging(int value);
    public void onChanged(int value);
    public void onCancelled();
  }
  
}
