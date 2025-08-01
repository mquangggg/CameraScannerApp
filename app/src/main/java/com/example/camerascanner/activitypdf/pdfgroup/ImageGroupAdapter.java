package com.example.camerascanner.activitypdf.pdfgroup;

import static androidx.fragment.app.FragmentManager.TAG;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.camerascanner.R;

import java.util.List;
@SuppressLint("RestrictedApi")

/**
 * Adapter cho RecyclerView hiển thị danh sách ảnh trong group
 */
public class ImageGroupAdapter extends RecyclerView.Adapter<ImageGroupAdapter.ImageViewHolder> {

    private Context context;
    private List<ImageItem> imageList;
    private OnImageActionListener listener;

    public interface OnImageActionListener {
        void onImageClick(int position);
        void onImageDelete(int position);
        void onImageReorder(int fromPosition, int toPosition);
    }

    public ImageGroupAdapter(Context context, List<ImageItem> imageList, OnImageActionListener listener) {
        this.context = context;
        this.imageList = imageList;
        this.listener = listener;
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

        // SỬ DỤNG PHƯƠNG THỨC bind() ĐÃ CÓ SẴN THAY VÌ XỬ LÝ TRỰC TIẾP
        holder.bind(item, position);

        Log.d(TAG, "Bound item at position " + position + ": " + item.getName());
    }

    @Override
    public int getItemCount() {
        return imageList != null ? imageList.size() : 0;
    }

    class ImageViewHolder extends RecyclerView.ViewHolder {
        private ImageView ivImage;
        private TextView tvImageName;
        private TextView tvImageInfo;
        private ImageView ivDelete;
        private View itemContainer;

        @SuppressLint("RestrictedApi")
        public ImageViewHolder(@NonNull View itemView) {
            super(itemView);

            // CHỈ LẤY CÁC VIEW CÓ TRONG LAYOUT
            ivImage = itemView.findViewById(R.id.ivImage);
            ivDelete = itemView.findViewById(R.id.ivDelete);
            itemContainer = itemView.findViewById(R.id.itemContainer);

            // CÁC VIEW KHÔNG TỒN TẠI TRONG LAYOUT - SET NULL
            tvImageName = null;
            tvImageInfo = null;

            // LOG để debug
            Log.d(TAG, "ImageViewHolder initialized - ivImage: " + (ivImage != null) +
                    ", ivDelete: " + (ivDelete != null) +
                    ", itemContainer: " + (itemContainer != null));
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

                // SKIP TextView vì không có trong layout
                // Layout hiện tại chỉ có ImageView, không có TextView cho tên và thông tin

                // Click listener cho item
                if (itemContainer != null) {
                    itemContainer.setOnClickListener(v -> {
                        if (listener != null) {
                            listener.onImageClick(position);
                            Log.d(TAG, "Item clicked at position: " + position);
                        }
                    });
                } else {
                    Log.e(TAG, "itemContainer is null at position " + position);
                }

                // Click listener cho nút delete
                if (ivDelete != null) {
                    ivDelete.setOnClickListener(v -> {
                        if (listener != null) {
                            listener.onImageDelete(position);
                            Log.d(TAG, "Delete clicked at position: " + position);
                        }
                    });

                    // Đảm bảo nút delete hiển thị
                    ivDelete.setVisibility(View.VISIBLE);
                } else {
                    Log.e(TAG, "ivDelete is null at position " + position);
                }

                // Selection indicator - sử dụng vSelectedIndicator thay vì background
                View selectedIndicator = itemView.findViewById(R.id.vSelectedIndicator);
                if (selectedIndicator != null) {
                    selectedIndicator.setVisibility(item.isSelected() ? View.VISIBLE : View.GONE);
                }

            } catch (Exception e) {
                Log.e(TAG, "Error binding item at position " + position + ": " + e.getMessage(), e);
            }
        }

        /**
         * Format file size cho dễ đọc
         */
        private String formatFileSize(long bytes) {
            if (bytes < 1024) {
                return bytes + " B";
            } else if (bytes < 1024 * 1024) {
                return String.format("%.1f KB", bytes / 1024.0);
            } else {
                return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
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
     * Di chuyển ảnh
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
}