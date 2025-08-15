package com.example.camerascanner.activitypdfgroup;

import static androidx.fragment.app.FragmentManager.TAG;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.example.camerascanner.R;

import java.util.Collections;
import java.util.List;

@SuppressLint("RestrictedApi")
/**
 * Adapter cho RecyclerView hiển thị danh sách ảnh trong group với hỗ trợ kéo thả
 */
public class ImageGroupAdapter extends RecyclerView.Adapter<ImageGroupAdapter.ImageViewHolder>
        implements ItemTouchHelperAdapter {

    private boolean isDragEnabled = false;
    private final Context context;
    private List<ImageItem> imageList;
    private final OnImageActionListener listener;
    private ItemTouchHelper itemTouchHelper;

    public interface OnImageActionListener {
        void onImageClick(int position);
        void onImageDelete(int position);
        void onImageReorder(int fromPosition, int toPosition);
    }

    public interface OnStartDragListener {
        void onStartDrag(RecyclerView.ViewHolder viewHolder);
    }

    private OnStartDragListener dragStartListener;

    public ImageGroupAdapter(Context context, List<ImageItem> imageList, OnImageActionListener listener) {
        this.context = context;
        this.imageList = imageList;
        this.listener = listener;
    }

    /**
     * Set ItemTouchHelper để hỗ trợ kéo thả
     */
    public void setItemTouchHelper(ItemTouchHelper itemTouchHelper) {
        this.itemTouchHelper = itemTouchHelper;
    }

    /**
     * Set listener cho drag start
     */
    public void setOnStartDragListener(OnStartDragListener listener) {
        this.dragStartListener = listener;
    }

    @NonNull
    @Override
    public ImageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_pdf_entry, parent, false);
        return new ImageViewHolder(view);
    }

    @SuppressLint("RestrictedApi")
    @Override
    public void onBindViewHolder(@NonNull ImageViewHolder holder, int position) {
        if (position < 0 || position >= imageList.size()) {
            Log.w(TAG, "Invalid position: " + position + ", size: " + imageList.size());
            return;
        }

        ImageItem item = imageList.get(position);
        if (item == null) {
            Log.w(TAG, "ImageItem is null at position: " + position);
            return;
        }

        holder.bind(item, position);
        Log.d(TAG, "Bound item at position " + position + ": " + item.getName());
        // Cập nhật trạng thái kéo thả cho ViewHolder
        holder.setDragEnabled(isDragEnabled);
    }

    @Override
    public int getItemCount() {
        return imageList != null ? imageList.size() : 0;
    }

    // IMPLEMENT ItemTouchHelperAdapter METHODS
    @Override
    public boolean onItemMove(int fromPosition, int toPosition) {
        if (imageList == null || fromPosition < 0 || toPosition < 0 ||
                fromPosition >= imageList.size() || toPosition >= imageList.size()) {
            return false;
        }

        try {
            // Di chuyển item trong list
            Collections.swap(imageList, fromPosition, toPosition);

            // Thông báo adapter
            notifyItemMoved(fromPosition, toPosition);
            Log.d(TAG, "Moved item from " + fromPosition + " to " + toPosition);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error moving item: " + e.getMessage(), e);
            return false;
        }
    }

    @Override
    public void onItemDismiss(int position) {
        // Không sử dụng swipe to dismiss, chỉ dùng button delete
    }

    @Override
    public void onItemMoveFinished() {
        if (imageList == null || imageList.isEmpty()) {
            return;
        }

        try {
            // Cập nhật tên các item sau khi di chuyển xong
            // (Đã di chuyển trong onItemMove)
            for (int i = 0; i < imageList.size(); i++) {
                String newName = "Ảnh " + (i + 1);
                imageList.get(i).setName(newName);
            }

            // Gọi listener chỉ để thông báo đã hoàn thành sắp xếp,
            // không cần truyền fromPosition và toPosition nữa
            // (Vì listener đã biết toàn bộ danh sách đã thay đổi)
            if (listener != null) {
                listener.onImageReorder(0, imageList.size() - 1);
            }

            // Sử dụng notifyDataSetChanged() để cập nhật lại toàn bộ RecyclerView
            notifyDataSetChanged();
            Log.d(TAG, "Item move finished, updated all item names");
        } catch (Exception e) {
            Log.e(TAG, "Error in onItemMoveFinished: " + e.getMessage(), e);
        }
    }

    class ImageViewHolder extends RecyclerView.ViewHolder {
        private final ImageView ivImage;
        private final TextView tvImageName;
        private final TextView tvImageInfo;
        private boolean isDragEnabled = false; // Biến này sẽ được cập nhật từ adapter
        private final ImageView ivDelete;
        private final View itemContainer;

        @SuppressLint("RestrictedApi")
        public ImageViewHolder(@NonNull View itemView) {
            super(itemView);

            // Lấy các view có trong layout
            ivImage = itemView.findViewById(R.id.ivImage);
            ivDelete = itemView.findViewById(R.id.ivDelete);
            itemContainer = itemView.findViewById(R.id.itemContainer);

            // Các view không tồn tại trong layout - set null
            tvImageName = null;
            tvImageInfo = null;

            // Thiết lập touch listener để hỗ trợ kéo thả
            setupDragAndTouch();

            Log.d(TAG, "ImageViewHolder initialized - ivImage: " + (ivImage != null) +
                    ", ivDelete: " + (ivDelete != null) +
                    ", itemContainer: " + (itemContainer != null));
        }

        // Phương thức mới để cập nhật trạng thái kéo thả từ adapter
        public void setDragEnabled(boolean enabled) {
            this.isDragEnabled = enabled;
        }

        // Sửa đổi phương thức setupDragAndTouch() trong class ImageViewHolder
        @SuppressLint("ClickableViewAccessibility")
        private void setupDragAndTouch() {
            if (itemContainer != null) {
                // OnTouchListener chỉ bắt đầu drag khi chế độ kéo thả được bật
                itemContainer.setOnTouchListener((v, event) -> {
                    // Kiểm tra trạng thái isDragEnabled của ViewHolder
                    if (isDragEnabled && itemTouchHelper != null && event.getAction() == MotionEvent.ACTION_DOWN) {
                        itemTouchHelper.startDrag(ImageViewHolder.this);
                        return true;
                    }
                    return false;
                });

                // OnClickListener chỉ xử lý sự kiện click khi chế độ kéo thả không được bật
                itemContainer.setOnClickListener(v -> {
                    if (!isDragEnabled && listener != null) {
                        listener.onImageClick(getAdapterPosition());
                    }
                });
            }
        }

        public void bind(ImageItem item, int position) {
            try {
                // Hiển thị ảnh
                if (ivImage != null) {
                    if (item.isValid()) {
                        ivImage.setImageBitmap(item.getBitmap());
                        ivImage.setAlpha(1.0f);
                        Log.d(TAG, "Set bitmap for position " + position + " - size: " +
                                item.getBitmap().getWidth() + "x" + item.getBitmap().getHeight());
                    } else {
                        ivImage.setImageResource(R.drawable.ic_home_white);
                        ivImage.setAlpha(0.5f);
                        Log.w(TAG, "Invalid bitmap at position " + position + ", using placeholder");
                    }
                } else {
                    Log.e(TAG, "ivImage is null at position " + position);
                }

                // Click listener cho nút delete
                if (ivDelete != null) {
                    ivDelete.setOnClickListener(v -> {
                        if (listener != null) {
                            listener.onImageDelete(getAdapterPosition());
                            Log.d(TAG, "Delete clicked at position: " + getAdapterPosition());
                        }
                    });

                    // Đảm bảo nút delete hiển thị
                    ivDelete.setVisibility(View.VISIBLE);
                } else {
                    Log.e(TAG, "ivDelete is null at position " + position);
                }

                // Selection indicator
                View selectedIndicator = itemView.findViewById(R.id.vSelectedIndicator);
                if (selectedIndicator != null) {
                    selectedIndicator.setVisibility(item.isSelected() ? View.VISIBLE : View.GONE);
                }

                // Thêm visual indicator cho drag capability
                addDragIndicator();

            } catch (Exception e) {
                Log.e(TAG, "Error binding item at position " + position + ": " + e.getMessage(), e);
            }
        }

        /**
         * Thêm visual indicator để người dùng biết có thể kéo thả
         */
        private void addDragIndicator() {
            if (itemContainer != null) {
                // Thêm subtle elevation để báo hiệu có thể tương tác
                itemContainer.setElevation(2f);

                // Thêm ripple effect
                itemContainer.setClickable(true);
                itemContainer.setFocusable(true);
            }
        }

        /**
         * Set trạng thái khi đang được kéo
         */
        public void onItemSelected() {
            itemView.setAlpha(0.7f);
            itemView.setScaleX(1.05f);
            itemView.setScaleY(1.05f);
            if (itemContainer != null) {
                itemContainer.setElevation(8f);
            }
        }

        /**
         * Khôi phục trạng thái bình thường
         */
        public void onItemClear() {
            itemView.setAlpha(1.0f);
            itemView.setScaleX(1.0f);
            itemView.setScaleY(1.0f);
            if (itemContainer != null) {
                itemContainer.setElevation(2f);
            }
        }
    }

    /**
     * Cập nhật danh sách ảnh
     */
    public void updateImageList(List<ImageItem> newImageList) {
        if (newImageList != null) {
            this.imageList = newImageList;
            notifyDataSetChanged();
            Log.d(TAG, "Updated image list with " + newImageList.size() + " items");
        }
    }

    /**
     * Thêm ảnh mới
     */
    public void addImage(ImageItem item) {
        if (item != null && imageList != null) {
            imageList.add(item);
            int newPosition = imageList.size() - 1;
            notifyItemInserted(newPosition);
            Log.d(TAG, "Added new image at position " + newPosition + ": " + item.getName());
        }
    }
    public void setDragEnabled(boolean enabled) {
        this.isDragEnabled = enabled;
        // Bắt buộc tất cả các item phải được re-bind để cập nhật trạng thái
        notifyDataSetChanged();
    }

    /**
     * Xóa ảnh tại vị trí
     */
    public void removeImage(int position) {
        if (imageList != null && position >= 0 && position < imageList.size()) {
            ImageItem removedItem = imageList.remove(position);
            notifyItemRemoved(position);
            notifyItemRangeChanged(position, imageList.size());
            Log.d(TAG, "Removed image at position " + position + ": " +
                    (removedItem != null ? removedItem.getName() : "null"));
        }
    }

    /**
     * Di chuyển ảnh (được gọi từ bên ngoài)
     */
    @SuppressLint("RestrictedApi")
    public void moveImage(int fromPosition, int toPosition) {
        if (imageList != null &&
                fromPosition >= 0 && fromPosition < imageList.size() &&
                toPosition >= 0 && toPosition < imageList.size()) {

            ImageItem item = imageList.remove(fromPosition);
            imageList.add(toPosition, item);
            notifyItemMoved(fromPosition, toPosition);
            Log.d(TAG, "Moved image from " + fromPosition + " to " + toPosition);
        }
    }

    /**
     * Lấy danh sách ảnh hiện tại
     */
    public List<ImageItem> getImageList() {
        return imageList;
    }
}