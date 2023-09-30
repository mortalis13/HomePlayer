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
import org.mortalis.homeplayer.Fun;

import static org.mortalis.homeplayer.Fun.log;


public class TrimSliderView extends View {
  
  private static final int SLIDER_SENSITIVITY = 100;
  private static final float RESET_GAP = Fun.dpToPx(30);
  
  private boolean sliderEnabled;
  
  private Paint canvasPaint;
  private Paint progressPaint;
  
  private RectF canvasRect;
  private RectF progressRect;
  
  private int canvasWidth;
  private int canvasHeight;
  
  private int maxValue;
  private int progress;
  private float progressStep;
  
  private boolean hasMoved;
  private int moveStartX;
  private int stepsDone;
  
  private ProgressChangeListener progressChangeListener;
  
  
  public TrimSliderView(Context context) {
    this(context, null);
  }
  
  public TrimSliderView(Context context, AttributeSet attrs) {
    super(context, attrs, 0);
    init(context);
  }
  
  
  private void init(Context context) {
    this.canvasPaint = new Paint();
    this.canvasPaint.setAntiAlias(true);
    this.canvasPaint.setColor(MaterialColors.getColor(this, R.attr.trimSliderBackgroundColor));
    this.canvasPaint.setStyle(Paint.Style.FILL);
    
    this.progressPaint = new Paint();
    this.progressPaint.setAntiAlias(true);
    this.progressPaint.setColor(MaterialColors.getColor(this, R.attr.trimSliderProgressColor));
    this.progressPaint.setStyle(Paint.Style.FILL);
    
    this.canvasRect = new RectF();
    this.progressRect = new RectF();
  }
  
  
  public void setMax(int value) {
    this.maxValue = value;
  }
  
  public void setProgress(int value) {
    if (value > this.maxValue) value = this.maxValue;
    if (value < 0) value = 0;

    this.progress = value;
    rebuildProgess();
    invalidate();
  }
  
  private void rebuildUI() {
    this.canvasRect.set(0, 0, this.canvasWidth, this.canvasHeight);
    
    if (this.maxValue == 0) setMax(this.canvasWidth);
    if (this.maxValue == 0) return;
    
    rebuildProgess();
    invalidate();
  }

  private void rebuildProgess() {
    this.progressStep = (float) this.canvasWidth / this.maxValue;
    float progressPx = this.progress * this.progressStep;
    
    float left   = 0;
    float top    = 0;
    float right  = left + progressPx;
    float bottom = this.canvasHeight;
    this.progressRect.set(left, top, right, bottom);
  }
  
  
  @Override
  public boolean onTouchEvent(MotionEvent event) {
    if (!this.sliderEnabled) return true;
    
    int action = event.getAction();
    int x = (int) event.getX();
    int y = (int) event.getY();
    
    if (action == MotionEvent.ACTION_DOWN) {
      this.moveStartX = x;
      this.stepsDone = 0;
    }
    
    if (action == MotionEvent.ACTION_MOVE) {
      this.hasMoved = true;
      int moveOffsetX = x - this.moveStartX;
      
      float steps = moveOffsetX / (this.progressStep * 100 / SLIDER_SENSITIVITY);
      int stepsProgress = (int) steps;
      
      stepsProgress -= this.stepsDone;
      this.stepsDone += stepsProgress;
      
      int _progress = this.progress + stepsProgress;
      if (_progress != this.progress) {
        setProgress(_progress);
        sendPosition(this.progress);
      }
    }
    
    if (action == MotionEvent.ACTION_UP) {
      if (x < RESET_GAP) {
        setProgress(0);
        sendPosition(this.progress);
      }
      else if (!this.hasMoved) {
        int _progress = (int) ((float) x * this.maxValue / this.canvasWidth);
        setProgress(_progress);
        sendPosition(this.progress);
      }
      
      this.moveStartX = 0;
      this.hasMoved = false;
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
  
  
  public void enable() {
    this.sliderEnabled = true;
  }
  
  public void disable() {
    this.sliderEnabled = false;
  }
  
  private void sendPosition(int position) {
    if (this.progressChangeListener != null) {
      this.progressChangeListener.onChanging(position);
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
  
  
  // ------------------ Classes ------------------
  
  public interface ProgressChangeListener {
    public void onChanging(int value);
  }
  
}
