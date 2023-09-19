package org.mortalis.homeplayer.components;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.util.AttributeSet;

import androidx.appcompat.widget.AppCompatImageView;

import com.google.android.material.color.MaterialColors;

import org.mortalis.homeplayer.R;
import org.mortalis.homeplayer.Fun;
import static org.mortalis.homeplayer.Fun.log;


public class IconOverlayView extends AppCompatImageView {
  
  private static final float MAIN_ICON_SIZE = Fun.dpToPx(36);
  private static final float ICON_GAP_X = Fun.dpToPx(2);
  
  private boolean showIndicator;
  private Paint indicatorPaint;
  private RectF indicatorRect;

  private float indicatorWidth;
  private float indicatorHeight;
  
  private boolean showIcon;
  private Paint iconPaint;
  private RectF iconRect;

  private Bitmap iconBitmap;
  private float iconRatio;
  
  
  public IconOverlayView(Context context) {
    this(context, null);
  }
  
  public IconOverlayView(Context context, AttributeSet attrs) {
    super(context, attrs, 0);
    init();
  }
  
  
  private void init() {
    indicatorWidth = Math.round(getResources().getDimension(R.dimen.favorite_mark_width));
    indicatorHeight = Math.round(getResources().getDimension(R.dimen.favorite_mark_height));
    
    iconBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.round_audio_file_black_24);
    if (iconBitmap != null) {
      iconRatio = (float) iconBitmap.getHeight() / iconBitmap.getWidth();
    }
    
    indicatorPaint = new Paint();
    indicatorPaint.setAntiAlias(true);
    indicatorPaint.setColor(MaterialColors.getColor(this, R.attr.favoriveMarkColor));
    indicatorPaint.setStyle(Paint.Style.FILL);
    
    iconPaint = new Paint();
    iconPaint.setAntiAlias(true);
    iconPaint.setColorFilter(new PorterDuffColorFilter(MaterialColors.getColor(this, R.attr.listItemIconColorHighlight), PorterDuff.Mode.SRC_ATOP));
    iconPaint.setStyle(Paint.Style.FILL);
    
    indicatorRect = new RectF();
    iconRect = new RectF();
  }
  
  public void setShowIndicator(boolean value) {
    showIndicator = value;
    invalidate();
  }
  
  public void setShowIcon(boolean value) {
    showIcon = value;
    invalidate();
  }
  
  @Override
  protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    float left, top, right, bottom;
    
    left = 0;
    top = (h - this.indicatorHeight) / 2;
    right = this.indicatorWidth;
    bottom = h - top;
    indicatorRect.set(left, top, right, bottom);
    
    float iconW = (float) w / 3;
    float iconH = iconRatio * iconW;
    
    left = (float) w / 2 + MAIN_ICON_SIZE / 2 - iconW + ICON_GAP_X;
    top = (float) h / 2 + MAIN_ICON_SIZE / 2 - iconH;
    right = left + iconW;
    bottom = top + iconH;
    iconRect.set(left, top, right, bottom);
    
    invalidate();
  }
  
  @Override
  protected void onDraw(Canvas canvas) {
    if (showIndicator) {
      canvas.drawRect(indicatorRect, indicatorPaint);
    }
    
    if (showIcon && iconBitmap != null) {
      canvas.drawBitmap(iconBitmap, null, iconRect, iconPaint);
    }
  }
  
}
