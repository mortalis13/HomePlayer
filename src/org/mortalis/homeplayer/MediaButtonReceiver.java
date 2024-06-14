package org.mortalis.homeplayer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.view.KeyEvent;

import static org.mortalis.homeplayer.Fun.log;
import static org.mortalis.homeplayer.Fun.loge;
import static org.mortalis.homeplayer.Fun.logd;


public class MediaButtonReceiver extends BroadcastReceiver {
  
  public static ReceiverListener receiverListener;
  
  public interface ReceiverListener {
    public void onMsgTogglePlay();
    public void onMsgPrev();
    public void onMsgNext();
  }
  
  public void onReceive(Context context, Intent intent) {
    String action = intent.getAction();
    if (!action.equals(Intent.ACTION_MEDIA_BUTTON)) {
      loge("MediaButtonReceiver received with invalid action: " + action);
      return;
    }
    
    final KeyEvent event;
    final int code;
    
    try {
      event = (KeyEvent) intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
      code = event.getKeyCode();
    }
    catch (Exception e) {
      e.printStackTrace();
      return;
    }
    
    if (event.getAction() == KeyEvent.ACTION_DOWN) {
      logd("MediaButtonReceiver.onReceive(): " + KeyEvent.keyCodeToString(code));
      if (code == KeyEvent.KEYCODE_MEDIA_PLAY || code == KeyEvent.KEYCODE_MEDIA_PAUSE) {
        if (receiverListener != null) receiverListener.onMsgTogglePlay();
      }
      if (code == KeyEvent.KEYCODE_MEDIA_PREVIOUS) {
        if (receiverListener != null) receiverListener.onMsgPrev();
      }
      if (code == KeyEvent.KEYCODE_MEDIA_NEXT) {
        if (receiverListener != null) receiverListener.onMsgNext();
      }
    }
  }
  
}
