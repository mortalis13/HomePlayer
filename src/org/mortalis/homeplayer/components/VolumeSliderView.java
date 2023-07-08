package org.mortalis.homeplayer.components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.google.android.material.color.MaterialColors;

import org.mortalis.homeplayer.Fun;
import org.mortalis.homeplayer.R;
import static org.mortalis.homeplayer.Fun.log;


public class VolumeSliderView extends View {
  
  private final static int SLIDER_SENSITIVITY = 130;
  private static final float MAX_VERTICAL_DISTANCE = Fun.dpToPx(100);
  
  private Paint canvasPaint;
  private Paint borderPaint;
  private Paint progressPaint;
  
  private RectF canvasRect;
  private RectF progressRect;
  private RectF borderRect;
  
  private int canvasWidth;
  private int canvasHeight;
  
  private float borderWidth;
  private float leftOffset;
  private float topOffset;
  
  private int mediumLevel;
  private int highLevel;
  
  private int normalColor;
  private int mediumColor;
  private int highColor;
  
  private int maxValue;
  private int progress;
  private float progressStep;
  
  private int moveStartX;
  private int stepsDone;
  
  private ProgressChangeListener progressChangeListener;
  
  
  public VolumeSliderView(Context context) {
    this(context, null);
  }
  
  public VolumeSliderView(Context context, AttributeSet attrs) {
    super(context, attrs, 0);
    init(context);
  }
  
  
  private void init(Context context) {
    this.borderWidth = (float) Math.ceil(getResources().getDimension(R.dimen.volume_slider_border_width));
    this.mediumLevel = getResources().getInteger(R.integer.volume_slider_medium_level);
    this.highLevel = getResources().getInteger(R.integer.volume_slider_high_level);
    
    this.normalColor = MaterialColors.getColor(this, R.attr.volumeSliderProgressColor);
    this.mediumColor = MaterialColors.getColor(this, R.attr.volumeSliderProgressMediumColor);
    this.highColor = MaterialColors.getColor(this, R.attr.volumeSliderProgressHighColor);
    
    this.leftOffset = this.borderWidth;
    this.topOffset = this.borderWidth;
    
    this.canvasPaint = new Paint();
    this.canvasPaint.setAntiAlias(true);
    this.canvasPaint.setColor(MaterialColors.getColor(this, R.attr.volumeSliderBackgroundColor));
    this.canvasPaint.setStyle(Paint.Style.FILL);
    
    this.progressPaint = new Paint();
    this.progressPaint.setAntiAlias(true);
    this.progressPaint.setColor(this.normalColor);
    this.progressPaint.setStyle(Paint.Style.FILL);
    
    this.borderPaint = new Paint();
    this.borderPaint.setAntiAlias(true);
    this.borderPaint.setColor(MaterialColors.getColor(this, R.attr.volumeSliderBorderColor));
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
    
    float valuePercent = (float) value / this.maxValue * 100;
    int color = this.normalColor;
    if (valuePercent > this.mediumLevel) color = this.mediumColor;
    if (valuePercent > this.highLevel) color = this.highColor;
    this.progressPaint.setColor(color);
    
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
    
    this.progressStep = (float) this.canvasWidth / this.maxValue;
    float progressPx = this.progress * this.progressStep;
    
    left   = this.leftOffset;
    top    = this.topOffset;
    right  = left + progressPx;
    bottom = this.canvasHeight - this.borderWidth;
    this.progressRect.set(left, top, right, bottom);
    
    invalidate();
  }
  
  
  @Override
  public boolean onTouchEvent(MotionEvent event) {
    int action = event.getAction();
    int x = (int) event.getX();
    int y = (int) event.getY();
    
    if (action == MotionEvent.ACTION_DOWN) {
      this.moveStartX = x;
      this.stepsDone = 0;
    }
    
    if (action == MotionEvent.ACTION_MOVE) {
      // Detect if vertical offset is greater than max and reset the position
      int outerVerticalOffset = (y < 0) ? Math.abs(y): y - this.canvasHeight;
      if (outerVerticalOffset > MAX_VERTICAL_DISTANCE) {
        cancelTouch();
        return true;
      }
      
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
      this.moveStartX = 0;
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
    public void onCancelled();
    public void onChanging(int value);
  }
  
}
