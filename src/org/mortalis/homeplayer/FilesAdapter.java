package org.mortalis.homeplayer;

import java.io.File;
import java.util.List;
import java.util.ArrayList;

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
import org.mortalis.homeplayer.models.ListItem;

import static org.mortalis.homeplayer.Fun.log;
import static org.mortalis.homeplayer.Fun.logd;


public class FilesAdapter extends RecyclerView.Adapter<FilesAdapter.ItemViewHolder> {
  
  private static final int ITEM_LAYOUT = R.layout.browser_list_item;
  private static final int ITEM_ICON_FOLDER = R.drawable.round_folder_black_36;
  private static final int ITEM_ICON_FILE = R.drawable.round_audio_file_black_36;
  private static final int ITEM_ICON_CUE = R.drawable.baseline_navigate_next_black_24;
  
  private final List<ListItem> fileList;
  private RecyclerView recyclerView;
  
  private int lastItemSelectedPos = -1;
  private int selectedItemPos = -1;
  
  private ItemViewHolder holderWithMenu;

  private final int item_icon_color_default;
  private final int item_icon_color_folder;
  private final int item_icon_color_highlight;
  private final int item_icon_color_highlight_cue;
  private final int text_color_default;
  private final int text_color_error;
  private final int item_separator_color;
  private final int item_cue_separator_color;
  private final int item_current_cue_background;
  
  private final float menuButtonWidth;
  
  SingleAction<ListItem> itemClickAction;
  SingleAction<ListItem> iconClickAction;
  SingleAction<String> afterFileRemovedAction;
  SingleAction<String> infoClickAction;
  SingleAction<ListItem> repeatSelectAction;
  
  
  public FilesAdapter(List<ListItem> fileList, Context context) {
    this.fileList = fileList;
    
    item_icon_color_default = MaterialColors.getColor(context, R.attr.listItemIconColor, Color.TRANSPARENT);
    item_icon_color_folder = MaterialColors.getColor(context, R.attr.listItemIconColorFolder, Color.TRANSPARENT);
    item_icon_color_highlight = MaterialColors.getColor(context, R.attr.listItemIconColorHighlight, Color.TRANSPARENT);
    item_icon_color_highlight_cue = MaterialColors.getColor(context, R.attr.listItemIconColorCueHighlight, Color.TRANSPARENT);
    text_color_default = MaterialColors.getColor(context, R.attr.primaryTextColor, Color.TRANSPARENT);
    text_color_error = MaterialColors.getColor(context, R.attr.listItemTextColorError, Color.TRANSPARENT);
    item_separator_color = MaterialColors.getColor(context, R.attr.listItemSeparatorColor, Color.TRANSPARENT);
    item_cue_separator_color = MaterialColors.getColor(context, R.attr.listItemCueSeparatorColor, Color.TRANSPARENT);
    item_current_cue_background = MaterialColors.getColor(context, R.attr.listItemCueBackgroundSelected, Color.TRANSPARENT);
    
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
    if (position == selectedItemPos || item.isCurrentCueTrack) {
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
      var item = this.fileList.get(i);
      if (item.path != null && item.path.equals(filePath)) {
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
  
  public void selectCueTrack(ListItem sourceItem, int time) {
    if (sourceItem == null || !sourceItem.isCue) return;
    logd("selectCueTrack \"%s\" %d", sourceItem.path, time);
    int size = this.fileList.size();
    boolean itemFound = false;
    
    for (int i = 0; i < size; i++) {
      ListItem item = this.fileList.get(i);
      if (!item.isCueTrack || item.cueSource != sourceItem) continue;
      
      if (!itemFound && time < item.cueEndTime) {
        if (!item.isCurrentCueTrack) {
          item.isCurrentCueTrack = true;
          notifyItemChanged(i);
        }
        itemFound = true;
      }
      else if (item.isCurrentCueTrack) {
        item.isCurrentCueTrack = false;
        notifyItemChanged(i);
      }
    }
  }
  
  public void markLastPlayedItem(String filePath) {
    if (filePath == null) return;
    
    int size = this.fileList.size();
    
    for (int i = 0; i < size; i++) {
      ListItem item = this.fileList.get(i);
      if (item.isFolder) continue;
    
      if (item.path != null && item.path.equals(filePath)) {
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

      if (item.path != null && item.path.equals(filePath)) {
        item.isFavorite = true;
        notifyItemChanged(i);
      }
    }
  }
  
  public void markError(String filePath) {
    int size = this.fileList.size();
    
    for (int i = 0; i < size; i++) {
      ListItem item = this.fileList.get(i);
      if (item.isFolder) continue;
      
      if (item.path != null && item.path.equals(filePath)) {
        item.hasError = true;
        notifyItemChanged(i);
      }
    }
  }
  
  public int getItemPosition(String path) {
    int size = this.fileList.size();
    
    for (int i = 0; i < size; i++) {
      ListItem item = this.fileList.get(i);
      if (item.path != null && item.path.equals(path)) {
        return i;
      }
    }
    
    return -1;
  }
  
  public ListItem getItemByPath(String path) {
    int size = this.fileList.size();
    
    for (int i = 0; i < size; i++) {
      ListItem item = this.fileList.get(i);
      if (item.path != null && item.path.equals(path)) {
        return item;
      }
    }
    
    return null;
  }
  
  public ListItem getPlayingCueTrack(String path) {
    int size = this.fileList.size();
    
    for (int i = 0; i < size; i++) {
      ListItem item = this.fileList.get(i);
      if (!item.isCurrentCueTrack || !item.isCueTrack) continue;
      if (item.cueSource == null) continue;
      
      if (item.cueSource.path != null && item.cueSource.path.equals(path)) {
        return item;
      }
    }
    
    return null;
  }
  
  public List<ListItem> getCueList(ListItem source) {
    int size = this.fileList.size();
    List<ListItem> result = new ArrayList<>();
    
    for (int i = 0; i < size; i++) {
      ListItem item = this.fileList.get(i);
      if (!item.isCueTrack) continue;
      if (item.cueSource == source) {
        result.add(item);
      }
    }
    
    return result;
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
    public RelativeLayout itemMenuPanel;
    
    ImageButton bRemoveFile;
    ImageButton bFileInfo;
    ImageButton bRepeatFile;
    
    View itemSeparator;
    View itemSeparatorThick;
    
    ListItem item;
    
    private boolean isRemovePressed;
    private boolean itemLongPressed;
    
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
      
      itemSeparator = rootView.findViewById(R.id.itemSeparator);
      itemSeparatorThick = rootView.findViewById(R.id.itemSeparatorThick);
      
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
      if (this.item.isFolder) return;
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
      
      int list_item_background = (!item.isCueTrack) ? R.color.list_item_background: R.color.list_item_cue_background;
      itemView.setBackgroundResource(list_item_background);
      
      iconContainer.setClickable(true);
      itemIconOverlay.setShowIndicator(item.isFavorite);
      itemIconOverlay.setShowIcon(item.isFolder && item.isVisited);
      
      itemText.setText(item.text);
      int textColor = item.hasError ? text_color_error: text_color_default;
      itemText.setTextColor(textColor);
      
      itemTime.setText(item.time);
      itemTime.setVisibility(item.isFile || item.isCueTrack ? View.VISIBLE: View.GONE);
      
      fileRepeatIcon.setVisibility(item.isFile && item.repeat ? View.VISIBLE: View.GONE);
      bRepeatFile.setSelected(item.isFile && item.repeat);
      
      int icon = 0;
      if (item.isFile && item.path != null) {
        icon = ITEM_ICON_FILE;
      }
      else if (item.isFolder) {
        icon = ITEM_ICON_FOLDER;
      }
      else if (item.isCueTrack) {
        icon = ITEM_ICON_CUE;
      }
      
      int iconColor = item_icon_color_default;
      if (item.isLastPlayed) iconColor = item_icon_color_highlight;
      if (item.isLastPlayed && item.isCue) iconColor = item_icon_color_highlight_cue;
      if (item.isCueTrack && item.isCurrentCueTrack) iconColor = item_icon_color_highlight_cue;
      if (item.isFolder) iconColor = item_icon_color_folder;

      itemIcon.setImageResource(icon);
      itemIcon.setColorFilter(iconColor);
      
      itemSeparator.setVisibility(!item.isCue ? View.VISIBLE: View.GONE);
      itemSeparatorThick.setVisibility(item.isCue ? View.VISIBLE: View.GONE);
      itemSeparatorThick.setBackgroundColor(item.isCue ? item_cue_separator_color: item_separator_color);
    }
  } // ItemViewHolder
  
}
