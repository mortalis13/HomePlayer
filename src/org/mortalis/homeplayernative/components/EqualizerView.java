package org.mortalis.homeplayernative.components;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextPaint;
import android.graphics.RectF;
import android.graphics.Picture;
import android.graphics.BlendMode;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.google.android.material.color.MaterialColors;

import org.mortalis.homeplayernative.Fun;
import org.mortalis.homeplayernative.R;
import static org.mortalis.homeplayernative.Fun.log;


public class EqualizerView extends View {
  
  public static final int MAX_BANDS = 8;
  public static final float MAX_UNITS = 20f;  // 10dB
  
  public static final int SIDE_MARGIN = 20;
  public static final int CENTRAL_MARK_WIDTH = 12;  // px
  public static final int BAND_ZERO_GAP = CENTRAL_MARK_WIDTH * 2;  // px
  
  private boolean enabled;
  
  private Paint canvasPaint;
  private Paint activationButtonPaint;
  private Paint activationButtonActivePaint;
  private Paint bandPaint;
  private Paint bandProgressPaint;
  private TextPaint bandTextPaint;
  private Paint bandCenterPaint;
  
  private RectF canvasRect;
  private RectF activationButtonRect;
  private RectF bandCenterRect;

  private int canvasWidth;
  private int canvasHeight;
  
  private List<Band> bands;
  
  private float bandTextYOffset;
  private float bandHeight;
  
  private float activationButtonWidth;
  private float activationButtonHeight;
  
  private float pixelsPerUnit;
  
  private float margin;
  private float bandsAreaY;
  
  private boolean bandSelected;
  private boolean bandCenterSelected;

  private int currentBand;
  private float startX;
  private float startGain;
  
  private ChangeListener changeListener;
  
  
  public EqualizerView(Context context) {
    this(context, null);
  }
  
  public EqualizerView(Context context, AttributeSet attrs) {
    super(context, attrs, 0);
    init();
  }
  
  
  private void init() {
    this.canvasPaint = new Paint();
    this.canvasPaint.setColor(MaterialColors.getColor(this, R.attr.eqBackground));
    this.canvasPaint.setStyle(Paint.Style.FILL);
    
    this.activationButtonPaint = new Paint();
    this.activationButtonPaint.setColor(MaterialColors.getColor(this, R.attr.eqActivationButtonBackground));
    this.activationButtonPaint.setStyle(Paint.Style.FILL);
    
    this.activationButtonActivePaint = new Paint();
    this.activationButtonActivePaint.setColor(MaterialColors.getColor(this, R.attr.eqActivationButtonActiveBackground));
    this.activationButtonActivePaint.setStyle(Paint.Style.FILL);
    
    this.bandPaint = new Paint();
    this.bandPaint.setColor(MaterialColors.getColor(this, R.attr.eqBandBackground));
    this.bandPaint.setStyle(Paint.Style.FILL);
    
    this.bandProgressPaint = new Paint();
    this.bandProgressPaint.setColor(MaterialColors.getColor(this, R.attr.eqBandProgressBackground));
    this.bandProgressPaint.setStyle(Paint.Style.FILL);
    
    this.bandCenterPaint = new Paint();
    this.bandCenterPaint.setColor(MaterialColors.getColor(this, R.attr.eqBandCenterBackground));
    this.bandCenterPaint.setStyle(Paint.Style.FILL);
    
    bandTextPaint = new TextPaint();
    bandTextPaint.setTextAlign(Paint.Align.LEFT);
    bandTextPaint.setAntiAlias(true);
    bandTextPaint.setColor(MaterialColors.getColor(this, R.attr.eqBandTextColor));
    bandTextPaint.setTextSize(getResources().getDimension(R.dimen.eq_band_text_size));
    float textHeight = bandTextPaint.descent() - bandTextPaint.ascent();
    bandTextYOffset = textHeight / 2 - bandTextPaint.descent();
    
    bandHeight = getResources().getDimension(R.dimen.eq_band_height);
    
    activationButtonWidth = getResources().getDimension(R.dimen.eq_activation_button_width);
    activationButtonHeight = Math.round(getResources().getDimension(R.dimen.eq_activation_button_height));
    
    bands = new ArrayList<>(MAX_BANDS);
    for (int i = 0; i < MAX_BANDS; i++) {
      bands.add(new Band());
    }
    
    fillBandFrequencies();
    
    this.canvasRect = new RectF();
    this.activationButtonRect = new RectF();
    this.bandCenterRect = new RectF();
  }
  
  private void fillBandFrequencies() {
    if (bands.size() == 8) {
      bands.get(0).frequency = 50f;
      bands.get(1).frequency = 200f;
      bands.get(2).frequency = 400f;
      bands.get(3).frequency = 900f;
      bands.get(4).frequency = 1500f;
      bands.get(5).frequency = 4000f;
      bands.get(6).frequency = 10000f;
      bands.get(7).frequency = 18000f;
    }
  }
  
  private void rebuildBandGain(int bandNum) {
    Band band = bands.get(bandNum);
    
    float progressX = band.rect.centerX();
    float progressWidth = band.gain * this.pixelsPerUnit;
    if (band.gain < 0) {
      progressWidth = -progressWidth;
      progressX -= progressWidth;
    }
    float progressY = band.rect.top;
    
    band.progressRect = new RectF(progressX, progressY, progressX + progressWidth, progressY + this.bandHeight);
    
    String sign = (band.gain > 0) ? "+": "";
    band.gainText = String.format("%s%.1f dB", sign, band.gain);
    
    band.gainTextX = band.rect.right - SIDE_MARGIN - this.bandTextPaint.measureText(band.gainText);
    band.gainTextY = band.rect.centerY() + bandTextYOffset;
    
    invalidate();
  }
  
  private void rebuildUI() {
    this.pixelsPerUnit = (float) this.canvasWidth / 2 / MAX_UNITS;
    this.margin = (float) (this.canvasHeight - this.activationButtonHeight - bands.size() * this.bandHeight) / (bands.size());
    
    float bandMargin = margin;
    if (bandMargin < 0) bandMargin = 0;
    
    this.bandsAreaY = this.activationButtonHeight;
    
    this.canvasRect.set(0, 0, this.canvasWidth, this.canvasHeight);
    this.activationButtonRect.set(this.canvasWidth - this.activationButtonWidth, 0, this.canvasWidth, this.activationButtonHeight);
    
    float centralX0 = this.canvasRect.centerX() - CENTRAL_MARK_WIDTH / 2;
    float centralY0 = this.activationButtonRect.bottom;
    this.bandCenterRect.set(centralX0, centralY0, centralX0 + CENTRAL_MARK_WIDTH, this.canvasHeight - margin);
    
    for (int i = 0; i < bands.size(); i++) {
      Band band = bands.get(i);
      float bandY = this.activationButtonRect.bottom + i * (this.bandHeight + bandMargin);
      band.rect = new RectF(0, bandY, this.canvasWidth, bandY + this.bandHeight);
      
      if (band.frequency >= 1000) {
        String format = "%.1f kHz";
        if (band.frequency % 1000 == 0) format = "%.0f kHz";
        band.frequencyText = String.format(format, band.frequency / 1000);
      }
      else {
        String format = "%.1f Hz";
        if (band.frequency == (int) band.frequency) format = "%.0f Hz";
        band.frequencyText = String.format(format, band.frequency);
      }
      
      band.frequencyTextX = SIDE_MARGIN;
      band.frequencyTextY = band.rect.centerY() + bandTextYOffset;
      
      rebuildBandGain(i);
    }
    
    invalidate();
  }
  
  private void onGainChanged(int band, float gain) {
    bands.get(band).gain = gain;
    rebuildBandGain(band);
    if (this.changeListener != null) {
      this.changeListener.gainChanged(band + 1, gain);
    }
  }
  
  private void onActiveButton() {
    this.enabled = !this.enabled;
    invalidate();
    if (this.changeListener != null) {
      this.changeListener.stateChanged(this.enabled);
    }
  }
  
  @Override
  public boolean onTouchEvent(MotionEvent event) {
    int action = event.getAction();
    float x = event.getX();
    float y = event.getY();
    
    if (action == MotionEvent.ACTION_DOWN) {
      if (x > this.activationButtonRect.left && x < this.activationButtonRect.right && y > this.activationButtonRect.top && y < this.activationButtonRect.bottom) {
        onActiveButton();
        return true;
      }
      
      if (y >= this.bandsAreaY && this.bandHeight != 0) {
        int bandNum = (int) ((y - this.bandsAreaY) / (this.bandHeight + this.margin));
        if (bandNum < 0 || bandNum >= bands.size()) return true;
        
        Band band = bands.get(bandNum);
        if (y <= band.rect.bottom) {
          this.bandSelected = true;
          this.startX = x;
          this.currentBand = bandNum;
          this.startGain = band.gain;
          
          if (x >= (bandCenterRect.left - BAND_ZERO_GAP) && x <= (bandCenterRect.right + BAND_ZERO_GAP)) {
            this.bandCenterSelected = true;
          }
        }
      }
    }
    
    else if (action == MotionEvent.ACTION_MOVE) {
      if (this.bandSelected) {
        float offsetX = x - this.startX;

        float gain = this.startGain + offsetX / this.pixelsPerUnit;
        if (Math.abs(gain) > MAX_UNITS) {
          gain = Math.signum(gain) * MAX_UNITS;
          this.startX = x;
          this.startGain = gain;
        }
        gain = (int) (gain * 10) / 10f;
        
        Band band = bands.get(this.currentBand);
        if (gain != band.gain) {
          onGainChanged(this.currentBand, gain);
        }
      }
    }
    
    else if (action == MotionEvent.ACTION_UP) {
      if (this.bandSelected && this.bandCenterSelected) {
        Band band = bands.get(this.currentBand);
        if (band.gain != 0 && x >= (bandCenterRect.left - BAND_ZERO_GAP) && x <= (bandCenterRect.right + BAND_ZERO_GAP)) {
          onGainChanged(this.currentBand, 0);
        }
      }
      
      this.bandSelected = false;
      this.bandCenterSelected = false;
      this.currentBand = -1;
      this.startX = -1;
      this.startGain = -1;
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
    canvas.drawRect(this.activationButtonRect, enabled ? this.activationButtonActivePaint : this.activationButtonPaint);
    
    for (int i = 0; i < bands.size(); i++) {
      Band band = bands.get(i);
      canvas.drawRect(band.rect, this.bandPaint);
      canvas.drawRect(band.progressRect, this.bandProgressPaint);
      canvas.drawText(band.frequencyText, band.frequencyTextX, band.frequencyTextY, this.bandTextPaint);
      canvas.drawText(band.gainText, band.gainTextX, band.gainTextY, this.bandTextPaint);
    }
    
    canvas.drawRect(this.bandCenterRect, this.bandCenterPaint);
  }
  
  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    setMeasuredDimension(measureWidth(widthMeasureSpec), measureHeight(heightMeasureSpec));
  }
  
  
  private int measureHeight(int measureSpec) {
    int size = getPaddingTop() + getPaddingBottom();
    return resolveSizeAndState(size, measureSpec, 0);
  }
  
  private int measureWidth(int measureSpec) {
    int size = getPaddingLeft() + getPaddingRight();
    return resolveSizeAndState(size, measureSpec, 0);
  }
  
  
  public void setChangeListener(ChangeListener changeListener) {
    this.changeListener = changeListener;
  }
  
  public int getBandsCount() {
    return MAX_BANDS;
  }
  
  public float getBandFrequency(int band) {
    return bands.get(band).frequency;
  }
  
  public float getBandGain(int band) {
    return bands.get(band).gain;
  }
  
  public void setBandGain(int band, float gain) {
    bands.get(band).gain = gain;
  }
  
  
  private class Band {
    public RectF rect;
    public RectF progressRect;
    
    public String frequencyText;
    public float frequencyTextX;
    public float frequencyTextY;
    
    public String gainText;
    public float gainTextX;
    public float gainTextY;
    
    public float gain;
    public float frequency;
  }
  
  public interface ChangeListener {
    public void stateChanged(boolean enabled);
    public void gainChanged(int band, float gain);
  }
}
