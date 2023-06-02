package org.mortalis.homeplayer;

import android.content.Intent;
import android.content.Context;
import android.content.BroadcastReceiver;

public class PlayerServiceReceiver extends BroadcastReceiver {
  
  private ReceiverListener receiverListener;
  
  public void setReceiverListener(ReceiverListener receiverListener) {
    this.receiverListener = receiverListener;
  }
  
  @Override
  public void onReceive(Context context, Intent intent) {
    Fun.logd("PlayerServiceReceiver.onReceive: " + intent.getAction());
    
    if (receiverListener == null) return;
    
    String action = intent.getAction();
    if (action.equals(PlayerServiceOld.ACTION_PLAY)) {
      receiverListener.onMsgPlay();
    }
    else if (action.equals(PlayerServiceOld.ACTION_PAUSE)) {
      receiverListener.onMsgPause();
    }
    else if (action.equals(PlayerServiceOld.ACTION_EXIT)) {
      receiverListener.onMsgExit();
    }
  }
  
  public interface ReceiverListener {
    public void onMsgPlay();
    public void onMsgPause();
    public void onMsgExit();
  }
}
