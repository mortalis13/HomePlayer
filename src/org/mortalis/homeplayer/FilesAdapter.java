package org.mortalis.homeplayer;

import java.io.File;
import java.util.List;

import androidx.recyclerview.widget.RecyclerView;
import android.view.ViewGroup;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.TranslateAnimation;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import android.widget.ImageButton;
import android.view.MotionEvent;
import android.view.GestureDetector;
import androidx.core.content.ContextCompat;

import org.mortalis.homeplayer.components.SimplePaintView;


public class FilesAdapter extends RecyclerView.Adapter<FilesAdapter.ItemViewHolder> {
  
  private static final int ITEM_LAYOUT = R.layout.browser_list_item;
  
  private List<ListItem> fileList;
  
  private int lastItemSelectedPos = -1;
  private int selectedItemPos = -1;
  
  private int holderWithMenu = -1;
  
  private int item_icon_color_default;
  private int item_icon_color_lastplayed;
  
  IconClickListener iconClickListener;
  AfterRemovedListener afterRemovedListener;
  InfoClickListener infoClickListener;
  ItemClickListener itemClickListener;
  ItemBindListener itemBindListener;
  
  GestureDetector gestureDetector;
  
  boolean itemSwipedLeft;
  boolean itemSwiping;
  
  
  public FilesAdapter(List<ListItem> fileList, Context context) {
    this.fileList = fileList;
    
    item_icon_color_default = ContextCompat.getColor(context, R.color.list_item_icon);
    item_icon_color_lastplayed = ContextCompat.getColor(context, R.color.list_item_is_last_played_file);
  }
  
  @Override
  public ItemViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
    Context context = parent.getContext();
    LayoutInflater inflater = LayoutInflater.from(context);
    
    View rootView = inflater.inflate(ITEM_LAYOUT, parent, false);
    
    ItemViewHolder viewHolder = new ItemViewHolder(rootView);
    return viewHolder;
  }
  
  @Override
  public void onBindViewHolder(ItemViewHolder holder, int position) {
    ListItem item = this.fileList.get(position);
    
    if (item.isFile && item.time == null) {
      if (itemBindListener != null) itemBindListener.itemBeforeBind(position);
    }
    
    holder.bind(item);
    if (position == selectedItemPos) {
      holder.select();
    }
    else {
      holder.unselect();
    }
  }
  
  @Override
  public int getItemCount() {
    return this.fileList.size();
  }
  
  private int getDirsCount() {
    int result = 0;
    for (ListItem item: this.fileList) if (!item.isFile) result ++; else break;
    return result;
  }
  
  public void resetSelection() {
    lastItemSelectedPos = -1;
    selectedItemPos = -1;
  }
  
  public int getPositionForSubpath(String filePath) {
    // Finds position of the file in the current list, if the curretn directory path is a subpath of the file
    if (filePath == null) return -1;
    int size = this.fileList.size();
    if (size == 0) return -1;
    
    // Check if current directory is not a subpath of the requested file
    String listParent = new File(this.fileList.get(0).path).getParent();
    if (listParent != null && !filePath.startsWith(listParent)) return -1;
    
    for (int i = 0; i < size; i++) {
      if (this.fileList.get(i).path.equals(filePath)) {
        return i;
      }
    }
    return getPositionForSubpath(new File(filePath).getParent());
  }
  
  public void selectItem(int itemPos) {
    selectedItemPos = itemPos;
    if (lastItemSelectedPos == -1) {
      lastItemSelectedPos = selectedItemPos;
    }
    else {
      notifyItemChanged(lastItemSelectedPos);
      lastItemSelectedPos = selectedItemPos;
    }
    
    notifyItemChanged(selectedItemPos);
  }
  
  public void markLastPlayedItem(String filePath) {
    if (filePath == null) return;
    
    for (int i = 0; i < this.fileList.size(); i++) {
      ListItem item = this.fileList.get(i);
      if (!item.isFile) continue;
    
      if (item.path.equals(filePath)) {
        item.isLastPlayed = true;
        notifyItemChanged(i);
      }
      else if (item.isLastPlayed) {
        item.isLastPlayed = false;
        notifyItemChanged(i);
      }
    }
  }
  
  public void markAsFavorite(String filePath) {
    for (int i = 0; i < this.fileList.size(); i++) {
      ListItem item = this.fileList.get(i);
      if (!item.isFile) continue;
      if (item.path.equals(filePath)) {
        item.isFavorite = true;
        notifyItemChanged(i);
      }
    }
  }
  
  public int getItemPosition(String path) {
    for (int i = 0; i < this.fileList.size(); i++) {
      ListItem item = this.fileList.get(i);
      if (item.path.equals(path)) return i;
    }
    return -1;
  }
  
  
  public class ItemViewHolder extends RecyclerView.ViewHolder {
    ImageView itemIcon;
    SimplePaintView itemIndicator;
    TextView itemText;
    TextView itemTime;
    FrameLayout iconContainer;
    RelativeLayout itemMenuPanel;
    
    ImageButton bRemoveFile;
    ImageButton bFileInfo;
    
    ListItem item;
    
    boolean isRemovePressed;
    
    public ItemViewHolder(View rootView) {
      super(rootView);
      
      itemIcon = rootView.findViewById(R.id.itemIcon);
      itemIndicator = rootView.findViewById(R.id.itemIndicator);
      itemText = rootView.findViewById(R.id.itemText);
      itemTime = rootView.findViewById(R.id.itemTime);
      iconContainer = rootView.findViewById(R.id.iconContainer);
      
      itemMenuPanel = rootView.findViewById(R.id.itemMenuPanel);
      bRemoveFile = rootView.findViewById(R.id.bRemoveFile);
      bFileInfo = rootView.findViewById(R.id.bFileInfo);
      
      iconContainer.setOnClickListener(v -> {
        this.item.isFavorite = !this.item.isFavorite;
        itemIndicator.setVisibility(this.item.isFavorite ? View.VISIBLE: View.GONE);
        if (iconClickListener != null) iconClickListener.iconClicked(this.item);
      });
      
      bRemoveFile.setOnClickListener(v -> {
        if (!isRemovePressed) {
          setRemoveState();
        }
        else {
          if (Fun.removeFile(this.item.path)) {
            if (afterRemovedListener != null) afterRemovedListener.fileRemoved(this.item.path);
          }
          else {
            resetRemoveState();
          }
        }
      });
      
      bFileInfo.setOnClickListener(v -> {
        if (infoClickListener != null) infoClickListener.infoClicked(this.item.path);
        hideItemMenu();
      });
      
      itemView.setOnTouchListener((view, event) -> {
        if (this.item.isFile) {
          float x = event.getX();
          float y = view.getTop() + event.getY();
          event = MotionEvent.obtain(event.getDownTime(), event.getEventTime(), event.getAction(), x, y, event.getMetaState());
          
          boolean result = gestureDetector.onTouchEvent(event);
          if (result) return true;
        }
        
        return this.processOnTouch(view, event);
      });
    }
    
    private boolean processOnTouch(View view, MotionEvent event) {
      if (this.item == null) return false;
      int action = event.getAction();
      
      // if (action == MotionEvent.ACTION_DOWN) Fun.log("ACTION_DOWN");
      // else if (action == MotionEvent.ACTION_CANCEL) Fun.log("ACTION_CANCEL");
      // else if (action == MotionEvent.ACTION_UP) Fun.log("ACTION_UP");
      
      if (action == MotionEvent.ACTION_DOWN) {
        view.setPressed(true);
        hideActiveMenu();
      }
      else if (action == MotionEvent.ACTION_CANCEL) {
        view.setPressed(false);
        itemSwipedLeft = false;
        itemSwiping = false;
      }
      else if (action == MotionEvent.ACTION_UP) {
        view.setPressed(false);
        
        if (itemSwipedLeft) {
          showItemMenu();
          holderWithMenu = getBindingAdapterPosition();
        }
        else if (!itemSwiping) {
          if (itemClickListener != null) itemClickListener.itemClicked(this.item);
        }
        
        itemSwipedLeft = false;
        itemSwiping = false;
      }
      
      return true;
    }
    
    private void hideActiveMenu() {
      int currentPos = getBindingAdapterPosition();
      
      if (holderWithMenu != -1) {
        int pos = holderWithMenu;
        holderWithMenu = -1;
        hideItemMenu();
        
        if (pos != currentPos) {
          notifyItemChanged(pos);
        }
      }
    }
    
    public void select() {
      itemView.setSelected(true);
      itemView.setPressed(false);
    }
    
    public void unselect() {
      itemView.setSelected(false);
      itemView.setPressed(false);
    }
    
    private void showItemMenu() {
      resetRemoveState();
      
      if (itemMenuPanel == null) return;
      if (itemMenuPanel.getVisibility() != View.VISIBLE) {
        itemMenuPanel.setVisibility(View.VISIBLE);
        
        float menuWidth = Fun.dpToPx(141);
        TranslateAnimation animation = new TranslateAnimation(menuWidth, 0, 0, 0);
        animation.setDuration(150);
        itemMenuPanel.startAnimation(animation);
      }
    }
    
    private void hideItemMenu() {
      if (itemMenuPanel == null) return;
      if (itemMenuPanel.getVisibility() != View.GONE) {
        itemMenuPanel.setVisibility(View.GONE);
      }
    }
    
    public void setRemoveState() {
      isRemovePressed = true;
      bRemoveFile.setBackgroundResource(R.color.remove_file_confirm);
    }
    
    public void resetRemoveState() {
      isRemovePressed = false;
      bRemoveFile.setBackgroundResource(R.color.list_item_button_background_default);
    }
    
    public void bind(ListItem item) {
      hideItemMenu();
      resetRemoveState();
      
      this.item = item;
      if (item == null) return;
      
      iconContainer.setClickable(item.isFile);
      itemIndicator.setVisibility(item.isFavorite ? View.VISIBLE: View.GONE);
      
      itemText.setText(item.text);
      itemTime.setText(item.time);
      itemTime.setVisibility(item.isFile ? View.VISIBLE: View.GONE);
      
      int iconColor = item_icon_color_default;
      if (item.isLastPlayed) iconColor = item_icon_color_lastplayed;
      itemIcon.setImageResource(item.icon);
      itemIcon.setColorFilter(iconColor);
    }
  } // ItemViewHolder
  
  
  public interface IconClickListener {
    public void iconClicked(ListItem item);
  }

  public interface AfterRemovedListener {
    public void fileRemoved(String path);
  }

  public interface InfoClickListener {
    public void infoClicked(String path);
  }

  public interface ItemClickListener {
    public void itemClicked(ListItem item);
  }

  public interface ItemBindListener {
    public void itemBeforeBind(int position);
  }

}
