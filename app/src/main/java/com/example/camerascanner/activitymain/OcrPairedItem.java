package com.example.camerascanner.activitymain;

import java.io.File;

public class OcrPairedItem {
    private final File imageFile;
    private final File textFile;
    private final String timestamp; // Dấu thời gian chung để nhận diện cặp
    private final String formattedDate; // Ngày được định dạng để hiển thị
    private final long totalSize; // Tổng kích thước của cặp ảnh và văn bản

    public OcrPairedItem(File imageFile, File textFile, String timestamp, String formattedDate, long totalSize) {
        this.imageFile = imageFile;
        this.textFile = textFile;
        this.timestamp = timestamp;
        this.formattedDate = formattedDate;
        this.totalSize = totalSize;
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
}