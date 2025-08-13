package com.example.camerascanner.activityimagepreview.Jpeg;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.util.Log;

public class JpegFileManager {
    private static final String TAG = "JpegFileManager";

    private final Context context;

    /**
     * Constructor khởi tạo JpegFileManager với Context
     */
    public JpegFileManager(Context context) {
        this.context = context;
    }

    /**
     * Xóa một file JPEG dựa trên URI của nó
     *
     * @param jpegUri URI của file JPEG cần xóa
     * @return true nếu file được xóa thành công, false nếu không thể xóa hoặc có lỗi
     */
    public boolean deleteJpegFile(Uri jpegUri) {
        if (jpegUri == null) {
            Log.e(TAG, "URI JPEG được cung cấp là null.");
            return false;
        }

        try {
            ContentResolver resolver = context.getContentResolver();
            int rowsDeleted = resolver.delete(jpegUri, null, null);

            if (rowsDeleted > 0) {
                Log.d(TAG, "Đã xóa JPEG thành công: " + jpegUri);
                return true;
            } else {
                Log.e(TAG, "Không thể xóa JPEG: " + jpegUri + ". Có thể file không tồn tại hoặc không có quyền.");
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Lỗi khi xóa JPEG: " + e.getMessage(), e);
            return false;
        }
    }
}