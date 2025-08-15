package com.example.camerascanner.activitypdfgroup;

import android.graphics.Canvas;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

/**
 * ItemTouchHelper Callback sử dụng interface riêng biệt
 */
public class SimpleItemTouchHelperCallback extends ItemTouchHelper.Callback {

    private final ItemTouchHelperAdapter adapter;

    public SimpleItemTouchHelperCallback(ItemTouchHelperAdapter adapter) {
        this.adapter = adapter;
    }

    @Override
    public boolean isLongPressDragEnabled() {
        // Không cần long press, drag sẽ được handle bằng touch listener
        return false;
    }

    @Override
    public boolean isItemViewSwipeEnabled() {
        // Không cho phép swipe, chỉ dùng nút delete
        return false;
    }

    @Override
    public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
        // CHỈ CHO PHÉP DRAG THEO CHIỀU DỌC VÀ NGANG - Phù hợp với GridLayout
        final int dragFlags = ItemTouchHelper.UP | ItemTouchHelper.DOWN |
                ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT;
        final int swipeFlags = 0; // Không swipe

        return makeMovementFlags(dragFlags, swipeFlags);
    }

    @Override
    public boolean onMove(@NonNull RecyclerView recyclerView,
                          @NonNull RecyclerView.ViewHolder viewHolder,
                          @NonNull RecyclerView.ViewHolder target) {

        int fromPosition = viewHolder.getAdapterPosition();
        int toPosition = target.getAdapterPosition();

        // KIỂM TRA VỊ TRÍ HỢP LỆ
        if (fromPosition == RecyclerView.NO_POSITION || toPosition == RecyclerView.NO_POSITION) {
            return false;
        }

        // Gọi adapter để xử lý di chuyển
        return adapter.onItemMove(fromPosition, toPosition);
    }

    @Override
    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
        // Không xử lý swipe
    }

    @Override
    public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
        super.onSelectedChanged(viewHolder, actionState);

        // VISUAL FEEDBACK KHI BẮT ĐẦU KÉOO
        if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
            if (viewHolder instanceof ImageGroupAdapter.ImageViewHolder) {
                ((ImageGroupAdapter.ImageViewHolder) viewHolder).onItemSelected();
            }
        }
    }

    @Override
    public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
        super.clearView(recyclerView, viewHolder);

        // KHÔI PHỤC TRẠNG THÁI BÌNH THƯỜNG
        if (viewHolder instanceof ImageGroupAdapter.ImageViewHolder) {
            ((ImageGroupAdapter.ImageViewHolder) viewHolder).onItemClear();
        }

        // Thông báo adapter đã hoàn thành di chuyển
        adapter.onItemMoveFinished();
    }

    @Override
    public long getAnimationDuration(@NonNull RecyclerView recyclerView, int animationType,
                                     float animateDx, float animateDy) {
        // ANIMATION NHANH HƠN - Cảm giác responsive
        return 200;
    }

    @Override
    public float getMoveThreshold(@NonNull RecyclerView.ViewHolder viewHolder) {
        // GIẢM THRESHOLD - Dễ kéo hơn
        return 0.2f;
    }

    @Override
    public float getSwipeThreshold(@NonNull RecyclerView.ViewHolder viewHolder) {
        // Không sử dụng swipe
        return Float.MAX_VALUE;
    }

    @Override
    public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView,
                            @NonNull RecyclerView.ViewHolder viewHolder,
                            float dX, float dY, int actionState, boolean isCurrentlyActive) {
        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);

        // THÊM HIỆU ỨNG SHADOW KHI DRAG
        if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && isCurrentlyActive) {
            // Có thể thêm custom drawing effects tại đây nếu cần
        }
    }
}