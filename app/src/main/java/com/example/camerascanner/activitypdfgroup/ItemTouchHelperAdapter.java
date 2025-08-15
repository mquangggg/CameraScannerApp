package com.example.camerascanner.activitypdfgroup;

/**
 * Interface riêng biệt cho ItemTouchHelper để tránh cyclic inheritance
 */
public interface ItemTouchHelperAdapter {
    boolean onItemMove(int fromPosition, int toPosition);
    void onItemDismiss(int position);
    void onItemMoveFinished();
}