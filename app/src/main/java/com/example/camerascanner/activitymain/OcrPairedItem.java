package com.example.camerascanner.activitymain;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class OcrPairedItem {
    private final File imageFile;
    private final File textFile;
    private final String timestamp; // Dấu thời gian chung để nhận diện cặp
    private final String formattedDate; // Ngày được định dạng để hiển thị
    private final long totalSize; // Tổng kích thước của cặp ảnh và văn bản

    // Constructor đầy đủ (giữ nguyên để tương thích)
    public OcrPairedItem(File imageFile, File textFile, String timestamp, String formattedDate, long totalSize) {
        this.imageFile = imageFile;
        this.textFile = textFile;
        this.timestamp = timestamp;
        this.formattedDate = formattedDate;
        this.totalSize = totalSize;
    }

    // Constructor đơn giản cho việc rename (tự động tính toán các giá trị)
    public OcrPairedItem(File imageFile, File textFile) {
        this.imageFile = imageFile;
        this.textFile = textFile;

        // Tự động tạo timestamp từ file mới nhất
        long latestModified = Math.max(
                imageFile != null ? imageFile.lastModified() : 0,
                textFile != null ? textFile.lastModified() : 0
        );

        this.timestamp = String.valueOf(latestModified);

        // Tự động format date
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
        this.formattedDate = sdf.format(new Date(latestModified));

        // Tự động tính tổng kích thước
        long imageSize = (imageFile != null && imageFile.exists()) ? imageFile.length() : 0;
        long textSize = (textFile != null && textFile.exists()) ? textFile.length() : 0;
        this.totalSize = imageSize + textSize;
    }

    // Constructor từ OcrPairedItem khác (copy constructor)
    public OcrPairedItem(OcrPairedItem other, File newImageFile, File newTextFile) {
        this.imageFile = newImageFile;
        this.textFile = newTextFile;
        this.timestamp = other.timestamp; // Giữ nguyên timestamp gốc

        // Cập nhật formattedDate và totalSize
        long latestModified = Math.max(
                newImageFile != null ? newImageFile.lastModified() : 0,
                newTextFile != null ? newTextFile.lastModified() : 0
        );

        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
        this.formattedDate = sdf.format(new Date(latestModified));

        long imageSize = (newImageFile != null && newImageFile.exists()) ? newImageFile.length() : 0;
        long textSize = (newTextFile != null && newTextFile.exists()) ? newTextFile.length() : 0;
        this.totalSize = imageSize + textSize;
    }

    public File getImageFile() {
        return imageFile;
    }

    public File getTextFile() {
        return textFile;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public String getFormattedDate() {
        return formattedDate;
    }

    public long getTotalSize() {
        return totalSize;
    }

    // Phương thức helper để tạo OcrPairedItem mới với file đã rename
    public OcrPairedItem createRenamedItem(File newImageFile, File newTextFile) {
        return new OcrPairedItem(this, newImageFile, newTextFile);
    }

    // Override equals để so sánh dựa trên đường dẫn file
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        OcrPairedItem that = (OcrPairedItem) obj;

        boolean imageEquals = (imageFile != null && that.imageFile != null)
                ? imageFile.getAbsolutePath().equals(that.imageFile.getAbsolutePath())
                : imageFile == that.imageFile;

        boolean textEquals = (textFile != null && that.textFile != null)
                ? textFile.getAbsolutePath().equals(that.textFile.getAbsolutePath())
                : textFile == that.textFile;

        return imageEquals && textEquals;
    }

    @Override
    public int hashCode() {
        int result = imageFile != null ? imageFile.getAbsolutePath().hashCode() : 0;
        result = 31 * result + (textFile != null ? textFile.getAbsolutePath().hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "OcrPairedItem{" +
                "imageFile=" + (imageFile != null ? imageFile.getName() : "null") +
                ", textFile=" + (textFile != null ? textFile.getName() : "null") +
                ", timestamp='" + timestamp + '\'' +
                ", formattedDate='" + formattedDate + '\'' +
                ", totalSize=" + totalSize +
                '}';
    }
}