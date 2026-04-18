package com.woefe.shoppinglist.activity;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.woefe.shoppinglist.R;
import com.woefe.shoppinglist.shoppinglist.ListItem;
import com.woefe.shoppinglist.shoppinglist.ShoppingList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @author Wolfgang Popp
 */
public class RecyclerListAdapter extends RecyclerView.Adapter<RecyclerListAdapter.ViewHolder> {
    private final int colorChecked;
    private final int colorDefault;
    private final int colorBackground;
    private ShoppingList shoppingList;
    private ItemTouchHelper touchHelper;
    private ItemLongClickListener longClickListener;
    private DragListener dragListener;
    private MainActivity mainActivity;
    private boolean dragHandlerEnabled = true;
    private RecyclerView recyclerView;
    private volatile boolean isProcessingNotification = false;
    private Comparator<ListItem> sortComparator = null;
    private List<ListItem> sortedItemsCache = null;

private final ShoppingList.ShoppingListListener listener = new ShoppingList.ShoppingListListener() {
        @Override
        public void onShoppingListUpdate(ShoppingList list, ShoppingList.Event e) {
            if (isProcessingNotification) {
                android.util.Log.w("RecyclerListAdapter", "onShoppingListUpdate: already processing, skipping");
                return;
            }
            isProcessingNotification = true;
            try {
                updateSortedCache();
                notifyDataSetChanged();
            } catch (Exception ex) {
                android.util.Log.e("RecyclerListAdapter", "onShoppingListUpdate: exception", ex);
            } finally {
                isProcessingNotification = false;
            }
        }
    };
    
    private boolean isDragging = false;
    private int dragStartPosition = -1;
    private int lastDropPosition = -1;
    private int currentDragTargetPosition = -1;

    public RecyclerListAdapter(Context ctx) {
        colorChecked = ContextCompat.getColor(ctx, R.color.textColorChecked);
        colorDefault = ContextCompat.getColor(ctx, R.color.textColorDefault);
        colorBackground = ContextCompat.getColor(ctx, R.color.colorListItemBackground);
        touchHelper = new ItemTouchHelper(new RecyclerListCallback(ctx));
        mainActivity = (MainActivity) ctx;
    }

    public void connectShoppingList(ShoppingList shoppingList) {
        this.shoppingList = shoppingList;
        shoppingList.addListener(listener);
        notifyDataSetChanged();
    }

    public void disconnectShoppingList() {
        if (shoppingList != null) {
            shoppingList.removeListener(listener);
            shoppingList = null;
        }
        sortedItemsCache = null;
    }

    public void setSortComparator(Comparator<ListItem> comparator) {
        this.sortComparator = comparator;
        updateSortedCache();
        notifyDataSetChanged();
    }

    private void updateSortedCache() {
        if (sortComparator == null) {
            sortedItemsCache = null;
            return;
        }
        sortedItemsCache = new ArrayList<>(shoppingList);
        Collections.sort(sortedItemsCache, sortComparator);
    }

    private ListItem getItemAt(int position) {
        if (sortedItemsCache != null) {
            if (position >= sortedItemsCache.size()) {
                return null;
            }
            return sortedItemsCache.get(position);
        }
        if (shoppingList != null && position < shoppingList.size()) {
            return shoppingList.get(position);
        }
        return null;
    }

    private int getItemPosition(ListItem item) {
        if (sortedItemsCache != null) {
            return sortedItemsCache.indexOf(item);
        }
        return shoppingList.indexOfUuid(item.getUuid());
    }

    public int getListIndexFromDisplayPosition(int displayPosition) {
        if (displayPosition < 0 || displayPosition >= getItemCount()) {
            android.util.Log.w("RecyclerListAdapter", "getListIndexFromDisplayPosition: pos " + displayPosition + " out of bounds [0, " + getItemCount() + ")");
            return -1;
        }
        ListItem item = getItemAt(displayPosition);
        if (item == null) {
            android.util.Log.w("RecyclerListAdapter", "getListIndexFromDisplayPosition: item is null at " + displayPosition);
            return -1;
        }
        android.util.Log.d("RecyclerListAdapter", "getListIndexFromDisplayPosition: displayPos=" + displayPosition + " item=" + item.getDescription() + " item.class=" + item.getClass().getSimpleName() + " item.uuid=" + item.getUuid());
        int idx = shoppingList.indexOfUuid(item.getUuid());
        android.util.Log.d("RecyclerListAdapter", "getListIndexFromDisplayPosition: result=" + idx + " shoppingList.size=" + shoppingList.size());
        if (idx < 0) {
            for (int i = 0; i < shoppingList.size(); i++) {
                ListItem slItem = shoppingList.get(i);
                android.util.Log.d("RecyclerListAdapter", "getListIndexFromDisplayPosition: shoppingList[" + i + "] desc=" + slItem.getDescription() + " uuid=" + slItem.getUuid() + " class=" + slItem.getClass().getSimpleName());
            }
        }
        return idx;
    }

    public int getDisplayPositionFromListIndex(int listIndex) {
        if (listIndex < 0 || listIndex >= shoppingList.size()) {
            return -1;
        }
        ListItem item = shoppingList.get(listIndex);
        return getItemPosition(item);
    }

    public void move(int fromPos, int toPos) {
        int fromListIndex = getListIndexFromDisplayPosition(fromPos);
        int toListIndex = getListIndexFromDisplayPosition(toPos);
        if (fromListIndex >= 0 && toListIndex >= 0) {
            shoppingList.move(fromListIndex, toListIndex);
        }
    }

    public void remove(int pos) {
        android.util.Log.d("RecyclerListAdapter", "remove: pos=" + pos + " itemCount=" + getItemCount());
        int listIndex = getListIndexFromDisplayPosition(pos);
        android.util.Log.d("RecyclerListAdapter", "remove: listIndex=" + listIndex);
        if (listIndex < 0) {
            return;
        }
        android.util.Log.d("RecyclerListAdapter", "remove: calling shoppingList.remove(" + listIndex + ")");
        final ListItem lastDeletedItem = shoppingList.remove(listIndex);
        android.util.Log.d("RecyclerListAdapter", "remove: removed=" + (lastDeletedItem != null ? lastDeletedItem.getDescription() : "null"));
        if (lastDeletedItem != null) {
            mainActivity.makeUndoSnackbar()
                        .setAction(R.string.undo_delete, new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                if (lastDeletedItem != null) {
                                    shoppingList.add(listIndex, lastDeletedItem);
                                }
                            }
                        }).show();
        }
    }

    public void registerRecyclerView(RecyclerView view) {
        this.recyclerView = view;
        touchHelper.attachToRecyclerView(view);
    }

    public void setOnItemLongClickListener(ItemLongClickListener listener) {
        this.longClickListener = listener;
    }

    public int getDragStartPosition() {
        return dragStartPosition;
    }

    public int getLastDropPosition() {
        return lastDropPosition;
    }

    public void setCurrentDragPosition(int pos) {
        currentDragTargetPosition = pos;
    }

    public int getCurrentDragPosition() {
        return currentDragTargetPosition;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.list_item, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull final ViewHolder holder, int position) {
        android.util.Log.d("RecyclerListAdapter", "onBindViewHolder: pos=" + position + " itemCount=" + getItemCount());
        if (shoppingList == null || position < 0 || position >= getItemCount()) {
            android.util.Log.w("RecyclerListAdapter", "onBindViewHolder: skipping due to invalid state");
            return;
        }
        ListItem listItem = getItemAt(position);
        if (listItem == null) {
            android.util.Log.w("RecyclerListAdapter", "onBindViewHolder: listItem is null");
            return;
        }
        holder.description.setText(listItem.getDescription());
        holder.quantity.setText(listItem.getQuantity());

        if (listItem.isChecked()) {
            holder.description.setTextColor(colorChecked);
            holder.quantity.setTextColor(colorChecked);
        } else {
            holder.description.setTextColor(colorDefault);
            holder.quantity.setTextColor(colorDefault);
        }

        holder.itemView.setBackgroundColor(colorBackground);

        final ListItem boundItem = listItem;
        final int boundPosition = position;
        holder.view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (shoppingList != null && boundItem != null) {
                    int listIndex = shoppingList.indexOfUuid(boundItem.getUuid());
                    if (listIndex >= 0) {
                        shoppingList.toggleChecked(listIndex);
                    }
                }
            }
        });


        holder.view.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (shoppingList != null && boundItem != null) {
                    int listIndex = shoppingList.indexOfUuid(boundItem.getUuid());
                    return longClickListener != null && listIndex >= 0 && longClickListener.onLongClick(listIndex);
                }
                return false;
            }
        });

        holder.dragHandler.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (dragHandlerEnabled && event.getAction() == MotionEvent.ACTION_DOWN) {
                    touchHelper.startDrag(holder);
                    return true;
                }
                return false;
            }
        });

        holder.dragHandler.setVisibility(dragHandlerEnabled ? View.VISIBLE : View.INVISIBLE);

    }

    @Override
    public int getItemCount() {
        synchronized (this) {
            if (shoppingList != null) {
                if (sortedItemsCache != null) {
                    return sortedItemsCache.size();
                }
                return shoppingList.size();
            }
            return 0;
        }
    }

    public interface ItemLongClickListener {
        boolean onLongClick(int position);
    }

    public interface DragListener {
        void onDragStart();
        void onDragEnd();
    }

    public void setDragListener(DragListener listener) {
        this.dragListener = listener;
    }

    public void setDragHandlerEnabled(boolean enabled) {
        this.dragHandlerEnabled = enabled;
        if (shoppingList != null) {
            for (int i = 0; i < shoppingList.size(); i++) {
                ViewHolder holder = (ViewHolder) recyclerView.findViewHolderForAdapterPosition(i);
                if (holder != null) {
                    holder.dragHandler.setVisibility(enabled ? View.VISIBLE : View.INVISIBLE);
                }
            }
        }
    }

    

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView description;
        TextView quantity;
        ImageView dragHandler;
        View view;

        public ViewHolder(View itemView) {
            super(itemView);
            view = itemView;
            description = itemView.findViewById(R.id.text_description);
            quantity = itemView.findViewById(R.id.text_quantity);
            dragHandler = itemView.findViewById(R.id.drag_n_drop_handler);
        }
    }

    public class RecyclerListCallback extends ItemTouchHelper.Callback {
        private ColorDrawable background;
        private Drawable deleteIcon;
        private int backgroundColor;

        public RecyclerListCallback(Context ctx) {
            this.background = new ColorDrawable();
            this.deleteIcon = ContextCompat.getDrawable(ctx, R.drawable.ic_delete_forever_white_24);
            this.backgroundColor = ContextCompat.getColor(ctx, R.color.colorCritical);
        }

        @Override
        public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
            if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder != null) {
                isDragging = true;
                dragStartPosition = viewHolder.getAdapterPosition();
                viewHolder.itemView.setAlpha(0.8f);
                viewHolder.itemView.setScaleX(1.05f);
                viewHolder.itemView.setScaleY(1.05f);
                viewHolder.itemView.setElevation(10f);
                if (dragListener != null) {
                    dragListener.onDragStart();
                }
            }
        }

        @Override
        public void clearView(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
            isDragging = false;
            int fromListIndex = getListIndexFromDisplayPosition(dragStartPosition);
            int toListIndex = getListIndexFromDisplayPosition(lastDropPosition);
            if (fromListIndex >= 0 && toListIndex >= 0 && fromListIndex != toListIndex) {
                shoppingList.move(fromListIndex, toListIndex);
            } else if (lastDropPosition >= 0 && fromListIndex < 0) {
                int fromPos = lastDropPosition;
                int toPos = viewHolder.getAdapterPosition();
                int toIdx = getListIndexFromDisplayPosition(toPos);
                int fromIdx = getListIndexFromDisplayPosition(fromPos);
                if (fromIdx >= 0 && toIdx >= 0 && fromIdx != toIdx) {
                    shoppingList.move(fromIdx, toIdx);
                }
            }
            dragStartPosition = -1;
            lastDropPosition = -1;
            currentDragTargetPosition = -1;
            viewHolder.itemView.setAlpha(1.0f);
            viewHolder.itemView.setScaleX(1.0f);
            viewHolder.itemView.setScaleY(1.0f);
            viewHolder.itemView.setElevation(0f);
            if (dragListener != null) {
                dragListener.onDragEnd();
            }
        }

        @Override
        public boolean isItemViewSwipeEnabled() {
            return true;
        }

        @Override
        public boolean isLongPressDragEnabled() {
            return false;
        }

        @Override
        public int getMovementFlags(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
            final int dragFlags = ItemTouchHelper.UP | ItemTouchHelper.DOWN;
            final int swipeFlags = ItemTouchHelper.START;
            return makeMovementFlags(dragFlags, swipeFlags);
        }

        @Override
        public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
            if (viewHolder.getItemViewType() != target.getItemViewType()) {
                return false;
            }

            int fromPos = viewHolder.getAdapterPosition();
            int toPos = target.getAdapterPosition();

            if (fromPos < 0 || toPos < 0) {
                return false;
            }

            if (fromPos == toPos) {
                return true;
            }

            lastDropPosition = toPos;
            currentDragTargetPosition = toPos;
            notifyItemMoved(fromPos, toPos);
            return true;
        }

        @Override
        public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
            int pos = viewHolder.getAdapterPosition();
            android.util.Log.d("RecyclerListCallback", "onSwiped: pos=" + pos + " itemCount=" + getItemCount());
            viewHolder.itemView.setAlpha(1.0f);
            viewHolder.itemView.setTranslationX(0);
            android.util.Log.d("RecyclerListCallback", "onSwiped: calling remove");
            RecyclerListAdapter.this.remove(pos);
            android.util.Log.d("RecyclerListCallback", "onSwiped: after remove, itemCount=" + getItemCount());
        }

        @Override
        public void onChildDraw(Canvas c, RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
            if (actionState != ItemTouchHelper.ACTION_STATE_SWIPE) {
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
                return;
            }

            View itemView = viewHolder.itemView;

            int backgroundLeft = itemView.getRight() + (int) dX;
            background.setBounds(backgroundLeft, itemView.getTop(), itemView.getRight(), itemView.getBottom());
            background.setColor(backgroundColor);
            background.draw(c);

            int itemHeight = itemView.getBottom() - itemView.getTop();
            int intrinsicHeight = deleteIcon.getIntrinsicHeight();
            int iconTop = itemView.getTop() + (itemHeight - intrinsicHeight) / 2;
            int iconMargin = (itemHeight - intrinsicHeight) / 2;
            int iconLeft = itemView.getRight() - iconMargin - deleteIcon.getIntrinsicWidth();
            int iconRight = itemView.getRight() - iconMargin;
            int iconBottom = iconTop + intrinsicHeight;
            deleteIcon.setBounds(iconLeft, iconTop, iconRight, iconBottom);
            deleteIcon.draw(c);

            final float alpha = 1.0f - Math.abs(dX) / (float) itemView.getWidth();
            itemView.setAlpha(alpha);
            itemView.setTranslationX(dX);
        }
    }
}
