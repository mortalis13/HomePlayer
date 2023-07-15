package org.mortalis.homeplayernative;

import java.io.File;
import java.util.List;

import android.content.Context;
import android.graphics.Color;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.TranslateAnimation;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.MaterialColors;

import org.mortalis.homeplayernative.actions.Action;
import org.mortalis.homeplayernative.components.SimplePaintView;
import static org.mortalis.homeplayernative.Fun.log;


public class FilesAdapter extends RecyclerView.Adapter<FilesAdapter.ItemViewHolder> {
  
  private static final int ITEM_LAYOUT = R.layout.browser_list_item;
  private static final int ITEM_MENU_BUTTONS = 2;
  
  private List<ListItem> fileList;
  private RecyclerView recyclerView;
  
  private boolean itemLongPressed;
  
  private int lastItemSelectedPos = -1;
  private int selectedItemPos = -1;
  
  private ItemViewHolder holderWithMenu;
  
  private int item_icon_color_default;
  private int item_icon_color_lastplayed;
  private int text_color_default;
  private int text_color_error;
  private float itemMenuWidth;
  
  Action<ListItem> itemClickAction;
  Action<ListItem> iconClickAction;
  Action<String> afterFileRemovedAction;
  Action<String> infoClickAction;
  Action<Integer> itemBeforeBindAction;
  
  
  public FilesAdapter(List<ListItem> fileList, Context context) {
    this.fileList = fileList;
    
    item_icon_color_default = MaterialColors.getColor(context, R.attr.listItemIconColor, Color.TRANSPARENT);
    item_icon_color_lastplayed = MaterialColors.getColor(context, R.attr.listItemIconColorHighlight, Color.TRANSPARENT);
    text_color_default = MaterialColors.getColor(context, R.attr.primaryTextColor, Color.TRANSPARENT);
    text_color_error = MaterialColors.getColor(context, R.attr.listItemTextColorError, Color.TRANSPARENT);
    
    itemMenuWidth = context.getResources().getDimension(R.dimen.item_menu_button_width) * ITEM_MENU_BUTTONS;
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
      itemBeforeBindAction.execute(position);
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
  
  @Override
  public void onAttachedToRecyclerView(RecyclerView recyclerView) {
    this.recyclerView = recyclerView;
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
    
    int size = this.fileList.size();
    
    for (int i = 0; i < size; i++) {
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
    int size = this.fileList.size();
    
    for (int i = 0; i < size; i++) {
      ListItem item = this.fileList.get(i);

      if (item.path.equals(filePath)) {
        item.isFavorite = true;
        notifyItemChanged(i);
      }
    }
  }
  
  public void markError(String filePath) {
    int size = this.fileList.size();
    
    for (int i = 0; i < size; i++) {
      ListItem item = this.fileList.get(i);
      if (!item.isFile) continue;
      
      if (item.path.equals(filePath)) {
        item.hasError = true;
        notifyItemChanged(i);
      }
    }
  }
  
  public int getItemPosition(String path) {
    int size = this.fileList.size();
    
    for (int i = 0; i < size; i++) {
      ListItem item = this.fileList.get(i);
      if (item.path.equals(path)) return i;
    }
    
    return -1;
  }
  
  public void hideActiveItemMenu(int currentPos) {
    if (holderWithMenu != null) {
      int pos = holderWithMenu.getBindingAdapterPosition();
      
      if (pos != currentPos) {
        holderWithMenu.hideItemMenu();
        notifyItemChanged(pos);
        holderWithMenu = null;
      }
    }
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
    
    private boolean isRemovePressed;
    
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
        iconClickAction.execute(this.item);
      });
      
      bRemoveFile.setOnClickListener(v -> {
        if (!isRemovePressed) {
          setRemoveState();
        }
        else {
          if (Fun.removeFile(this.item.path)) {
            afterFileRemovedAction.execute(this.item.path);
          }
          else {
            resetRemoveState();
          }
        }
      });
      
      bFileInfo.setOnClickListener(v -> {
        infoClickAction.execute(this.item.path);
        hideItemMenu();
      });
      
      itemView.setOnTouchListener((View view, MotionEvent event) -> {
        return processOnTouch(view, event);
      });
    }
    
    public void processLongPress() {
      if (!this.item.isFile) return;
      itemLongPressed = true;
      showItemMenu();
      holderWithMenu = this;
    }
    
    private boolean processOnTouch(View view, MotionEvent event) {
      if (this.item == null) return false;
      int action = event.getAction();
      
      // if (action == MotionEvent.ACTION_DOWN) log("ACTION_DOWN");
      // if (action == MotionEvent.ACTION_CANCEL) log("ACTION_CANCEL");
      // if (action == MotionEvent.ACTION_UP) log("ACTION_UP");
      
      if (action == MotionEvent.ACTION_DOWN) {
        view.setPressed(true);
      }
      else if (action == MotionEvent.ACTION_CANCEL) {
        view.setPressed(false);
        itemLongPressed = false;
      }
      else if (action == MotionEvent.ACTION_UP) {
        view.setPressed(false);
        if (!itemLongPressed) {
          itemClickAction.execute(this.item);
        }
        itemLongPressed = false;
      }
      
      return true;
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
      if (itemMenuPanel == null || itemMenuPanel.getVisibility() == View.VISIBLE) return;

      resetRemoveState();
      itemMenuPanel.setVisibility(View.VISIBLE);
      
      TranslateAnimation animation = new TranslateAnimation(itemMenuWidth, 0, 0, 0);
      animation.setDuration(150);
      itemMenuPanel.startAnimation(animation);
    }
    
    public void hideItemMenu() {
      if (itemMenuPanel == null) return;
      if (itemMenuPanel.getVisibility() != View.GONE) {
        itemMenuPanel.setVisibility(View.GONE);
      }
    }
    
    public void setRemoveState() {
      isRemovePressed = true;
      bRemoveFile.setBackgroundResource(R.color.danger_button_background);
    }
    
    public void resetRemoveState() {
      isRemovePressed = false;
      bRemoveFile.setBackgroundResource(R.color.list_item_button_background);
    }
    
    public void bind(ListItem item) {
      hideItemMenu();
      resetRemoveState();
      
      this.item = item;
      if (item == null) return;
      
      iconContainer.setClickable(true);
      itemIndicator.setVisibility(item.isFavorite ? View.VISIBLE: View.GONE);
      
      itemText.setText(item.text);
      int textColor = item.hasError ? text_color_error: text_color_default;
      itemText.setTextColor(textColor);
      
      itemTime.setText(item.time);
      itemTime.setVisibility(item.isFile ? View.VISIBLE: View.GONE);
      
      int iconColor = item_icon_color_default;
      if (item.isLastPlayed) iconColor = item_icon_color_lastplayed;
      itemIcon.setImageResource(item.icon);
      itemIcon.setColorFilter(iconColor);
    }
  } // ItemViewHolder
  
}
