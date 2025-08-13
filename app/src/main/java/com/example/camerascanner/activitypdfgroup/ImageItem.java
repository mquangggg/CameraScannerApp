package com.example.camerascanner.activitypdfgroup;

import android.graphics.Bitmap;

/**
 * Data class đại diện cho một item ảnh trong group
 */
public class ImageItem {
    private Bitmap bitmap;
    private String name;
    private String filePath;
    private final long timestamp;
    private boolean isSelected;

    public ImageItem(Bitmap bitmap, String name) {
        this.bitmap = bitmap;
        this.name = name;
        this.timestamp = System.currentTimeMillis();
        this.isSelected = false;
    }

    public ImageItem(Bitmap bitmap, String name, String filePath) {
        this(bitmap, name);
        this.filePath = filePath;
    }

    // Getters
    public Bitmap getBitmap() {
        return bitmap;
    }

    public String getName() {
        return name;
    }

    public String getFilePath() {
        return filePath;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public boolean isSelected() {
        return isSelected;
    }

    // Setters
    public void setBitmap(Bitmap bitmap) {
        this.bitmap = bitmap;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public void setSelected(boolean selected) {
        isSelected = selected;
    }

    /**
     * Kiểm tra bitmap có hợp lệ không
     */
    public boolean isValid() {
        return bitmap != null && !bitmap.isRecycled();
    }

    /**
     * Lấy kích thước bitmap dưới dạng string
     */
    public String getDimensionString() {
        if (isValid()) {
            return bitmap.getWidth() + "x" + bitmap.getHeight();
        }
        return "0x0";
    }

    /**
     * Lấy kích thước file ước tính (bytes)
     */
    public long getEstimatedSize() {
        if (isValid()) {
            return bitmap.getByteCount();
        }
        return 0;
    }

    @Override
    public String toString() {
        return "ImageItem{" +
                "name='" + name + '\'' +
                ", dimension=" + getDimensionString() +
                ", size=" + getEstimatedSize() + " bytes" +
                ", valid=" + isValid() +
                '}';
    }
}