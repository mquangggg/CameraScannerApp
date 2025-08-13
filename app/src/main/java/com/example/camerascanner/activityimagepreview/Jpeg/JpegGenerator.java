package com.example.camerascanner.activitysignature.signatureview.imagesignpreview.Jpeg;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class JpegGenerator {
    private static final String TAG = "JpegGenerator";
    private static final int JPEG_QUALITY = 90; // Chất lượng JPEG (0-100)

    private final Context context;

    /**
     * Constructor khởi tạo JpegGenerator với Context
     */
    public JpegGenerator(Context context) {
        this.context = context;
    }

    /**
     * Lưu Bitmap dưới dạng JPEG với tên file được chỉ định
     *
     * @param bitmap Bitmap cần lưu
     * @param fileName Tên file JPEG (không bao gồm đuôi .jpg)
     * @return Uri của file JPEG đã lưu
     * @throws IOException Nếu có lỗi trong quá trình lưu
     */
    public Uri saveAsJpeg(Bitmap bitmap, String fileName) throws IOException {
        if (bitmap == null) {
            throw new IllegalArgumentException("Bitmap không thể null");
        }

        // Đảm bảo tên file có đuôi .jpg
        String finalFileName = ensureJpegExtension(fileName);

        Uri jpegUri;
        OutputStream outputStream = null;

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android Q trở lên: sử dụng MediaStore
                jpegUri = saveToMediaStore(finalFileName);
                if (jpegUri != null) {
                    outputStream = context.getContentResolver().openOutputStream(jpegUri);
                }
            } else {
                // Android cũ hơn: lưu trực tiếp vào external storage
                jpegUri = saveToExternalStorage(finalFileName);
                outputStream = new FileOutputStream(new File(jpegUri.getPath()));
            }

            if (outputStream != null) {
                // Nén và lưu bitmap dưới dạng JPEG
                boolean success = bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream);
                if (!success) {
                    throw new IOException("Không thể nén bitmap thành JPEG");
                }
                outputStream.flush();
                return jpegUri;
            } else {
                throw new IOException("Không thể mở OutputStream để lưu JPEG");
            }
        } finally {
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    Log.e(TAG, "Lỗi khi đóng OutputStream: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Đảm bảo tên file có đuôi .jpg
     */
    private String ensureJpegExtension(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return JpegFileNameGenerator.generateDefaultFileName();
        }

        String trimmedName = fileName.trim();
        if (!trimmedName.toLowerCase().endsWith(".jpg") &&
                !trimmedName.toLowerCase().endsWith(".jpeg")) {
            trimmedName += ".jpg";
        }

        return trimmedName;
    }

    /**
     * Lưu JPEG vào MediaStore (Android Q+)
     */
    private Uri saveToMediaStore(String fileName) {
        ContentResolver resolver = context.getContentResolver();
        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
        contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS + File.separator + "MyPDFImages");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues);
        }
        return null;
    }

    /**
     * Lưu JPEG vào external storage (Android < Q)
     */
    private Uri saveToExternalStorage(String fileName) {
        File jpegDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "MyPDFs");
        if (!jpegDir.exists()) {
            jpegDir.mkdirs();
        }
        File jpegFile = new File(jpegDir, fileName);
        return Uri.fromFile(jpegFile);
    }
}