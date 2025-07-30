package com.example.camerascanner.activitypdf.Jpeg;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class JpegFileNameGenerator {

    /**
     * Tạo tên file JPEG mặc định dựa trên thời gian hiện tại
     * Format: "ScannedImage_YYYYMMDD_HHmmss.jpg"
     */
    public static String generateDefaultFileName() {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        return "ScannedImage_" + timestamp + ".jpg";
    }

    /**
     * Đảm bảo tên file có đuôi .jpg hoặc .jpeg
     * Nếu tên file rỗng hoặc null, tạo tên mặc định
     */
    public static String ensureJpegExtension(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return generateDefaultFileName();
        }

        String trimmedName = fileName.trim();
        if (!trimmedName.toLowerCase().endsWith(".jpg") &&
                !trimmedName.toLowerCase().endsWith(".jpeg")) {
            trimmedName += ".jpg";
        }

        return trimmedName;
    }

    /**
     * Tạo tên file JPEG từ tên file PDF (thay đổi extension)
     */
    public static String generateFromPdfName(String pdfFileName) {
        if (pdfFileName == null || pdfFileName.trim().isEmpty()) {
            return generateDefaultFileName();
        }

        String baseName = pdfFileName.trim();

        // Loại bỏ đuôi .pdf nếu có
        if (baseName.toLowerCase().endsWith(".pdf")) {
            baseName = baseName.substring(0, baseName.length() - 4);
        }

        return baseName + ".jpg";
    }
}