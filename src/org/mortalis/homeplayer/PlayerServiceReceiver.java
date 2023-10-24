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
    String action = intent.getAction();
    if (receiverListener == null || action == null) return;

    switch (action) {
      case PlayerService.ACTION_PLAY -> receiverListener.onMsgPlay();
      case PlayerService.ACTION_PAUSE -> receiverListener.onMsgPause();
      case PlayerService.ACTION_NEXT -> receiverListener.onMsgNext();
      case PlayerService.ACTION_EXIT -> receiverListener.onMsgExit();
    }
  }
  
  public interface ReceiverListener {
    public void onMsgPlay();
    public void onMsgPause();
    public void onMsgNext();
    public void onMsgExit();
  }
}
