package org.mortalis.homeplayer;

import java.io.File;
import java.util.List;

import android.content.Context;
import android.graphics.Color;
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

import org.mortalis.homeplayer.actions.SingleAction;
import org.mortalis.homeplayer.components.IconOverlayView;

import static org.mortalis.homeplayer.Fun.log;


public class FilesAdapter extends RecyclerView.Adapter<FilesAdapter.ItemViewHolder> {
  
  private static final int ITEM_LAYOUT = R.layout.browser_list_item;
  
  private final List<ListItem> fileList;
  private RecyclerView recyclerView;
  
  private boolean itemLongPressed;
  
  private int lastItemSelectedPos = -1;
  private int selectedItemPos = -1;
  
  private ItemViewHolder holderWithMenu;

  private final int item_icon_color_default;
  private final int item_icon_color_lastplayed;
  private final int text_color_default;
  private final int text_color_error;
  private final float menuButtonWidth;
  
  SingleAction<ListItem> itemClickAction;
  SingleAction<ListItem> iconClickAction;
  SingleAction<String> afterFileRemovedAction;
  SingleAction<String> infoClickAction;
  SingleAction<ListItem> repeatSelectAction;
  
  
  public FilesAdapter(List<ListItem> fileList, Context context) {
    this.fileList = fileList;
    
    item_icon_color_default = MaterialColors.getColor(context, R.attr.listItemIconColor, Color.TRANSPARENT);
    item_icon_color_lastplayed = MaterialColors.getColor(context, R.attr.listItemIconColorHighlight, Color.TRANSPARENT);
    text_color_default = MaterialColors.getColor(context, R.attr.primaryTextColor, Color.TRANSPARENT);
    text_color_error = MaterialColors.getColor(context, R.attr.listItemTextColorError, Color.TRANSPARENT);
    
    menuButtonWidth = context.getResources().getDimension(R.dimen.item_menu_button_width);
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
    // Finds position of the file in the current list, if the current directory path is a subpath of the file
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
    if (lastItemSelectedPos != -1) {
      notifyItemChanged(lastItemSelectedPos);
    }
    lastItemSelectedPos = selectedItemPos;

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
      if (item.path.equals(path)) {
        return i;
      }
    }
    
    return -1;
  }
  
  public void hideActiveItemMenu() {
    if (holderWithMenu == null) return;
    holderWithMenu.hideItemMenu();
    notifyItemChanged(holderWithMenu.getBindingAdapterPosition());
    holderWithMenu = null;
  }
  
  
  public class ItemViewHolder extends RecyclerView.ViewHolder {
    FrameLayout iconContainer;
    ImageView itemIcon;
    IconOverlayView itemIconOverlay;
    TextView itemText;
    TextView itemTime;
    ImageView fileRepeatIcon;
    RelativeLayout itemMenuPanel;
    
    ImageButton bRemoveFile;
    ImageButton bFileInfo;
    ImageButton bRepeatFile;
    
    ListItem item;
    
    private boolean isRemovePressed;
    
    public ItemViewHolder(View rootView) {
      super(rootView);
      
      iconContainer = rootView.findViewById(R.id.iconContainer);
      itemIcon = rootView.findViewById(R.id.itemIcon);
      itemIconOverlay = rootView.findViewById(R.id.itemIconOverlay);
      itemText = rootView.findViewById(R.id.itemText);
      itemTime = rootView.findViewById(R.id.itemTime);
      fileRepeatIcon = rootView.findViewById(R.id.fileRepeatIcon);
      
      itemMenuPanel = rootView.findViewById(R.id.itemMenuPanel);
      bRemoveFile = rootView.findViewById(R.id.bRemoveFile);
      bFileInfo = rootView.findViewById(R.id.bFileInfo);
      bRepeatFile = rootView.findViewById(R.id.bRepeatFile);
      
      iconContainer.setOnClickListener(v -> {
        this.item.isFavorite = !this.item.isFavorite;
        itemIconOverlay.setShowIndicator(this.item.isFavorite);
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
      
      bRepeatFile.setOnClickListener(v -> {
        v.setSelected(!v.isSelected());
        item.repeat = !item.repeat;
        repeatSelectAction.execute(item);
        notifyItemChanged(getBindingAdapterPosition());
        hideItemMenu();
      });
      
      itemView.setOnTouchListener((view, event) -> processOnTouch(view, event));
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
      bRepeatFile.setSelected(item.isFile && item.repeat);
      resetRemoveState();
      itemMenuPanel.setVisibility(View.VISIBLE);
      
      TranslateAnimation animation = new TranslateAnimation(menuButtonWidth, 0, 0, 0);
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
      itemIconOverlay.setShowIndicator(item.isFavorite);
      itemIconOverlay.setShowIcon(!item.isFile && item.isVisited);
      
      itemText.setText(item.text);
      int textColor = item.hasError ? text_color_error: text_color_default;
      itemText.setTextColor(textColor);
      
      itemTime.setText(item.time);
      itemTime.setVisibility(item.isFile ? View.VISIBLE: View.GONE);
      
      fileRepeatIcon.setVisibility(item.isFile && item.repeat ? View.VISIBLE: View.GONE);
      bRepeatFile.setSelected(item.isFile && item.repeat);
      
      int iconColor = item_icon_color_default;
      if (item.isLastPlayed) iconColor = item_icon_color_lastplayed;
      itemIcon.setImageResource(item.icon);
      itemIcon.setColorFilter(iconColor);
    }
  } // ItemViewHolder
  
}
