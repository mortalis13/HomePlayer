package org.mortalis.homeplayernative.components;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
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
import static org.mortalis.homeplayernative.Fun.loge;


public class EqualizerView extends View {
  
  public static final int MIN_BANDS = 6;
  public static final int MAX_BANDS = 10;
  public static final float MAX_MAIN_GAIN = 20f;  // 20dB
  public static final float MAX_UNITS = 20f;  // 20dB
  
  public static final float SIDE_MARGIN = Fun.dpToPx(8);
  public static final float CENTRAL_MARK_WIDTH = Fun.dpToPx(4);
  public static final float GAIN_ZERO_GAP = Fun.dpToPx(20);
  public static final float BAND_ZERO_GAP = Fun.dpToPx(32);
  
  private boolean enabled;
  
  private Paint canvasPaint;
  private Paint activationButtonPaint;
  private Paint activationButtonActivePaint;
  private Paint resetButtonPaint;
  private Paint resetButtonSelectedPaint;
  
  private Paint mainGainPaint;
  private Paint mainGainProgressPaint;
  private TextPaint mainGainTextPaint;
  
  private Paint bandPaint;
  private Paint bandProgressPaint;
  private TextPaint bandTextPaint;
  private Paint bandCenterPaint;
  
  private RectF canvasRect;
  private RectF activationButtonRect;
  private RectF resetButtonRect;
  private RectF mainGainRect;
  private RectF mainGainProgressRect;
  private RectF bandCenterRect;

  // Common
  private int canvasWidth;
  private int canvasHeight;
  
  private int buttonWidth;
  private int buttonHeight;
  private int margin;
  
  private float startX;
  private float startGain;
  
  private boolean resetPressed;
  
  // Main gain
  private float mainGain;
  private float mainGainStep;
  private String mainGainText;
  private float mainGainTextX;
  private float mainGainTextY;
  
  private boolean mainGainSelected;
  private boolean mainGainCenterSelected;
  
  // Bands
  private List<Band> bands;
  private int bandsCount;
  
  private int bandHeight;
  private float bandTextYOffset;
  private float bandGainStep;
  
  private int bandsAreaY;
  private int currentBand;
  
  private boolean bandSelected;
  private boolean bandCenterSelected;
  
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
    
    this.resetButtonPaint = new Paint();
    this.resetButtonPaint.setColor(MaterialColors.getColor(this, R.attr.eqResetButtonBackground));
    this.resetButtonPaint.setStyle(Paint.Style.FILL);
    
    this.resetButtonSelectedPaint = new Paint();
    this.resetButtonSelectedPaint.setColor(MaterialColors.getColor(this, R.attr.eqResetButtonSelectedBackground));
    this.resetButtonSelectedPaint.setStyle(Paint.Style.FILL);
    
    this.mainGainPaint = new Paint();
    this.mainGainPaint.setColor(Color.TRANSPARENT);
    this.mainGainPaint.setStyle(Paint.Style.FILL);
    
    this.mainGainProgressPaint = new Paint();
    this.mainGainProgressPaint.setColor(MaterialColors.getColor(this, R.attr.eqMainGainProgressBackground));
    this.mainGainProgressPaint.setStyle(Paint.Style.FILL);
    
    this.mainGainTextPaint = new TextPaint();
    this.mainGainTextPaint.setTextAlign(Paint.Align.CENTER);
    this.mainGainTextPaint.setAntiAlias(true);
    this.mainGainTextPaint.setColor(MaterialColors.getColor(this, R.attr.eqBandTextColor));
    this.mainGainTextPaint.setTextSize(getResources().getDimension(R.dimen.eq_main_gain_text_size));
    
    this.bandPaint = new Paint();
    this.bandPaint.setColor(MaterialColors.getColor(this, R.attr.eqBandBackground));
    this.bandPaint.setStyle(Paint.Style.FILL);
    
    this.bandProgressPaint = new Paint();
    this.bandProgressPaint.setColor(MaterialColors.getColor(this, R.attr.eqBandProgressBackground));
    this.bandProgressPaint.setStyle(Paint.Style.FILL);
    
    this.bandCenterPaint = new Paint();
    this.bandCenterPaint.setColor(MaterialColors.getColor(this, R.attr.eqBandCenterBackground));
    this.bandCenterPaint.setStyle(Paint.Style.FILL);
    
    this.bandTextPaint = new TextPaint();
    this.bandTextPaint.setTextAlign(Paint.Align.LEFT);
    this.bandTextPaint.setAntiAlias(true);
    this.bandTextPaint.setColor(MaterialColors.getColor(this, R.attr.eqBandTextColor));
    this.bandTextPaint.setTextSize(getResources().getDimension(R.dimen.eq_band_text_size));
    
    this.bandTextYOffset = (bandTextPaint.descent() - bandTextPaint.ascent()) / 2 - bandTextPaint.descent();
    
    bandHeight = (int) (getResources().getDimension(R.dimen.eq_band_height));
    buttonWidth = (int) (getResources().getDimension(R.dimen.eq_button_width));
    buttonHeight = (int) (getResources().getDimension(R.dimen.eq_button_height));
    
    this.canvasRect = new RectF();
    this.activationButtonRect = new RectF();
    this.resetButtonRect = new RectF();
    this.mainGainRect = new RectF();
    this.mainGainProgressRect = new RectF();
    this.bandCenterRect = new RectF();
    
    bands = new ArrayList<>(MAX_BANDS);
  }
  
  private void rebuildBand(int bandNum) {
    // Updates dynamic band data (gain progress and text)
    Band band = bands.get(bandNum);
    
    float progressX = band.rect.centerX();
    float progressY = band.rect.top;
    float progressWidth = band.gain * this.bandGainStep;
    
    if (band.gain < 0) {
      progressWidth = -progressWidth;
      progressX -= progressWidth;
    }
    
    band.progressRect = new RectF(progressX, progressY, progressX + progressWidth, progressY + this.bandHeight);
    
    String sign = (band.gain > 0) ? "+": "";
    band.gainText = String.format("%s%.1f dB", sign, band.gain);
    
    band.gainTextX = band.rect.right - SIDE_MARGIN - this.bandTextPaint.measureText(band.gainText);
    band.gainTextY = band.rect.centerY() + bandTextYOffset;
  }
  
  private void rebuildMainGain() {
    float progressX = this.canvasRect.centerX();
    float progressY = this.mainGainRect.top;
    float progressR = this.canvasRect.centerX() + this.mainGain * this.mainGainStep;
    float progressB = this.mainGainRect.bottom;
    
    if (this.mainGain < 0) {
      progressX = this.canvasRect.centerX() - Math.abs(mainGain * this.mainGainStep);
      progressR = this.canvasRect.centerX();
    }
    
    this.mainGainProgressRect.set(progressX, progressY, progressR, progressB);
    
    String sign = (mainGain > 0) ? "+": "";
    mainGainText = String.format("%s%.1f dB", sign, mainGain);
    
    mainGainTextX = this.mainGainRect.centerX();
    float textOffset = (mainGainTextPaint.descent() - mainGainTextPaint.ascent()) / 2 - mainGainTextPaint.descent();
    mainGainTextY = mainGainRect.centerY() + textOffset;
  }
  
  private void rebuildUI() {
    this.bandGainStep = (float) this.canvasWidth / 2 / MAX_UNITS;
    this.margin = (int) ((float) (this.canvasHeight - this.buttonHeight - bands.size() * this.bandHeight) / bands.size());
    
    if (margin < 0) {
      if (Math.abs(margin) < this.bandHeight / 2) {
        this.bandHeight = Math.round(this.bandHeight - Math.abs(margin));
      }
      margin = 0;
    }
    
    this.bandsAreaY = this.buttonHeight;
    
    this.canvasRect.set(0, 0, this.canvasWidth, this.canvasHeight);
    this.activationButtonRect.set(this.canvasWidth - this.buttonWidth, 0, this.canvasWidth, this.buttonHeight);
    this.resetButtonRect.set(0, 0, this.buttonWidth, this.buttonHeight);
    
    float centralX0 = this.canvasRect.centerX() - CENTRAL_MARK_WIDTH / 2;
    float centralY0 = this.activationButtonRect.bottom;
    float centralHeight = bands.size() * this.bandHeight + (bands.size() - 1) * margin;
    this.bandCenterRect.set(centralX0, centralY0, centralX0 + CENTRAL_MARK_WIDTH, centralY0 + centralHeight);
    
    this.mainGainRect.set(this.buttonWidth, 0, this.canvasWidth - this.buttonWidth, this.buttonHeight);
    this.mainGainStep = this.mainGainRect.width() / 2 / MAX_MAIN_GAIN;
    
    rebuildMainGain();
    
    for (int i = 0; i < bands.size(); i++) {
      Band band = bands.get(i);
      float bandY = this.activationButtonRect.bottom + i * (this.bandHeight + margin);
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
      
      rebuildBand(i);
    }
    
    invalidate();
  }


  private void onActiveButton() {
    if (this.changeListener != null) {
      this.changeListener.stateChanged(this.enabled);
    }
  }
  
  private void onResetButton() {
    for (int i = 0; i < bands.size(); i++) {
      if (bands.get(i).gain != 0) {
        bands.get(i).gain = 0;
        rebuildBand(i);
        onGainChanged(i);
      }
    }
    invalidate();
  }
  
  private void onMainGainChanged() {
    if (this.changeListener != null) {
      this.changeListener.mainGainChanged(mainGain);
    }
  }
  
  private void onGainChanged(int band) {
    if (this.changeListener != null) {
      this.changeListener.gainChanged(band + 1, bands.get(band).gain);
    }
  }
  
  
  private float normalizeSliderGain(float sliderX, float gainStep, float gainMax) {
    // X in px
    // Step in px per gain unit
    // Max in dB
    // Converts slider offset to gain in dB and cuts to 1 decimal point => 0.1, 2.7, -3.5 dB
    float sliderOffset = sliderX - this.startX;
    float gain = this.startGain + sliderOffset / gainStep;
    if (Math.abs(gain) > gainMax) {
      gain = Math.signum(gain) * gainMax;
      this.startX = sliderX;
      this.startGain = gain;
    }
    gain = (int) (gain * 10) / 10f;
    return gain;
  }
  
  @Override
  public boolean onTouchEvent(MotionEvent event) {
    int action = event.getAction();
    float x = event.getX();
    float y = event.getY();
    
    if (action == MotionEvent.ACTION_DOWN) {
      if (x > this.activationButtonRect.left &&
          x < this.activationButtonRect.right &&
          y > this.activationButtonRect.top &&
          y < this.activationButtonRect.bottom)
      {
        this.enabled = !this.enabled;
        invalidate();
        onActiveButton();
        return true;
      }
      
      if (x > this.resetButtonRect.left &&
          x < this.resetButtonRect.right &&
          y > this.resetButtonRect.top &&
          y < this.resetButtonRect.bottom)
      {
        this.resetPressed = true;
        invalidate();
        return true;
      }
      
      if (x >= this.mainGainRect.left &&
          x <= this.mainGainRect.right &&
          y >= this.mainGainRect.top &&
          y <= this.mainGainRect.bottom)
      {
        this.mainGainSelected = true;
        this.startX = x;
        this.startGain = mainGain;
        
        if (x >= this.mainGainRect.centerX() - GAIN_ZERO_GAP && x <= this.mainGainRect.centerX() + GAIN_ZERO_GAP) {
          this.mainGainCenterSelected = true;
        }
        return true;
      }
      
      if (y >= this.bandsAreaY && this.bandHeight != 0) {
        int bandNum = (int) ((y - this.bandsAreaY) / (this.bandHeight + this.margin));
        if (bandNum < 0 || bandNum >= bands.size()) return true;
        
        Band band = bands.get(bandNum);
        if (y < band.rect.bottom + this.margin) {
          this.bandSelected = true;
          this.startX = x;
          this.currentBand = bandNum;
          this.startGain = band.gain;
          
          if (x >= bandCenterRect.left - BAND_ZERO_GAP && x <= bandCenterRect.right + BAND_ZERO_GAP) {
            this.bandCenterSelected = true;
          }
        }
      }
    }
    
    else if (action == MotionEvent.ACTION_MOVE) {
      if (this.mainGainSelected && this.mainGainStep != 0) {
        float gain = normalizeSliderGain(x, this.mainGainStep, MAX_MAIN_GAIN);
        if (mainGain != gain) {
          mainGain = gain;
          rebuildMainGain();
          invalidate();
          onMainGainChanged();
        }
        
        return true;
      }
      
      if (this.bandSelected && this.bandGainStep != 0) {
        float gain = normalizeSliderGain(x, this.bandGainStep, MAX_UNITS);
        Band band = bands.get(this.currentBand);
        if (band.gain != gain) {
          band.gain = gain;
          rebuildBand(this.currentBand);
          invalidate();
          onGainChanged(this.currentBand);
        }
      }
    }
    
    else if (action == MotionEvent.ACTION_UP) {
      if (this.resetPressed &&
          x > this.resetButtonRect.left &&
          x < this.resetButtonRect.right &&
          y > this.resetButtonRect.top &&
          y < this.resetButtonRect.bottom)
      {
        this.resetPressed = false;
        onResetButton();
        return true;
      }
      
      if (this.resetPressed) {
        this.resetPressed = false;
        invalidate();
      }
      
      if (this.mainGainSelected) {
        if (this.mainGainCenterSelected && mainGain != 0 &&
            x >= this.mainGainRect.centerX() - GAIN_ZERO_GAP &&
            x <= this.mainGainRect.centerX() + GAIN_ZERO_GAP &&
            y >= this.mainGainRect.top &&
            y <= this.mainGainRect.bottom)
        {
          mainGain = 0;
          rebuildMainGain();
          invalidate();
          onMainGainChanged();
        }
        
        this.mainGainSelected = false;
        this.mainGainCenterSelected = false;
      }
      
      if (this.bandSelected) {
        Band band = bands.get(this.currentBand);
        if (this.bandCenterSelected && band.gain != 0 &&
            x >= bandCenterRect.left - BAND_ZERO_GAP &&
            x <= bandCenterRect.right + BAND_ZERO_GAP &&
            y >= band.rect.top &&
            y <= band.rect.bottom)
        {
          band.gain = 0;
          rebuildBand(this.currentBand);
          invalidate();
          onGainChanged(this.currentBand);
        }
        
        this.bandSelected = false;
        this.bandCenterSelected = false;
        this.currentBand = -1;
      }
      
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
    canvas.drawRect(this.resetButtonRect, resetPressed ? this.resetButtonSelectedPaint : this.resetButtonPaint);
    
    for (int i = 0; i < bands.size(); i++) {
      Band band = bands.get(i);
      canvas.drawRect(band.rect, this.bandPaint);
      canvas.drawRect(band.progressRect, this.bandProgressPaint);
      canvas.drawText(band.frequencyText, band.frequencyTextX, band.frequencyTextY, this.bandTextPaint);
      canvas.drawText(band.gainText, band.gainTextX, band.gainTextY, this.bandTextPaint);
    }
  
    canvas.drawRect(this.bandCenterRect, this.bandCenterPaint);
    
    canvas.drawRect(this.mainGainRect, this.mainGainPaint);
    canvas.drawRect(this.mainGainProgressRect, this.mainGainProgressPaint);
    canvas.drawText(mainGainText, mainGainTextX, mainGainTextY, this.mainGainTextPaint);
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
  
  public void setupBands(int bandsCount) {
    if (bandsCount < MIN_BANDS || bandsCount > MAX_BANDS) {
      loge("Incorrect number of EQ bands, not within [%d, %d]", MIN_BANDS, MAX_BANDS);
      return;
    }
    
    this.bandsCount = bandsCount;
    bands.clear();
    for (int i = 0; i < bandsCount; i++) {
      bands.add(new Band());
    }
    fillBandFrequencies();
  }
  
  
  public void setChangeListener(ChangeListener changeListener) {
    this.changeListener = changeListener;
  }
  
  public int getBandsCount() {
    return this.bandsCount;
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
    public void mainGainChanged(float gain);
    public void gainChanged(int band, float gain);
  }
}
