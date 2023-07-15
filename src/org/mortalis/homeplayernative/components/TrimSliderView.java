package org.mortalis.homeplayernative.components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.google.android.material.color.MaterialColors;

import org.mortalis.homeplayernative.Fun;
import org.mortalis.homeplayernative.R;
import static org.mortalis.homeplayernative.Fun.log;


public class TrimSliderView extends View {
  
  private final static int SLIDER_SENSITIVITY = 100;
  
  private boolean sliderEnabled;
  
  private Paint canvasPaint;
  private Paint borderPaint;
  private Paint progressPaint;
  
  private RectF canvasRect;
  private RectF progressRect;
  private RectF borderRect;
  
  private int canvasWidth;
  private int canvasHeight;
  
  private int workingWidth;
  private int workingHeight;
  
  private int borderWidth;
  
  private int mediumLevel;
  private int highLevel;
  
  private int normalColor;
  private int mediumColor;
  private int highColor;
  
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
    this.borderWidth = (int) Math.ceil(getResources().getDimension(R.dimen.trim_slider_border_width));
    
    this.canvasPaint = new Paint();
    this.canvasPaint.setAntiAlias(true);
    this.canvasPaint.setColor(MaterialColors.getColor(this, R.attr.trimSliderBackgroundColor));
    this.canvasPaint.setStyle(Paint.Style.FILL);
    
    this.progressPaint = new Paint();
    this.progressPaint.setAntiAlias(true);
    this.progressPaint.setColor(MaterialColors.getColor(this, R.attr.trimSliderProgressColor));
    this.progressPaint.setStyle(Paint.Style.FILL);
    
    this.borderPaint = new Paint();
    this.borderPaint.setAntiAlias(true);
    this.borderPaint.setColor(MaterialColors.getColor(this, R.attr.trimSliderBorderColor));
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
    if (value > this.maxValue) value = this.maxValue;
    if (value < 0) value = 0;

    this.progress = value;
    rebuildProgess();
    invalidate();
  }
  
  private void rebuildUI() {
    this.canvasRect.set(0, 0, this.canvasWidth, this.canvasHeight);
    
    float left   = (float) this.borderWidth / 2;
    float top    = (float) this.borderWidth / 2;
    float right  = this.canvasWidth  - (float) this.borderWidth / 2;
    float bottom = this.canvasHeight - (float) this.borderWidth / 2;
    this.borderRect.set(left, top, right, bottom);
    
    if (this.maxValue == 0) setMax(this.workingWidth);
    if (this.maxValue == 0) return;
    
    rebuildProgess();
    invalidate();
  }

  private void rebuildProgess() {
    this.progressStep = (float) this.workingWidth / this.maxValue;
    float progressPx = this.progress * this.progressStep;
    
    float left   = this.borderWidth;
    float top    = this.borderWidth;
    float right  = left + progressPx;
    float bottom = this.canvasHeight - this.borderWidth;
    this.progressRect.set(left, top, right, bottom);
  }
  
  
  @Override
  public boolean onTouchEvent(MotionEvent event) {
    if (!this.sliderEnabled) return true;
    
    int action = event.getAction();
    int x = (int) event.getX() - this.borderWidth;
    int y = (int) event.getY() - this.borderWidth;
    
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
      if (!this.hasMoved) {
        int _progress = (int) ((float) x * this.maxValue / this.workingWidth);
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
    this.workingWidth = this.canvasWidth - this.borderWidth * 2;
    this.workingHeight = this.canvasHeight - this.borderWidth * 2;
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
