package com.example.camerascanner.activitypdf;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;

public class ImageLoader {
    private static final String TAG = "ImageLoader";

    /**
     * Tải một đối tượng Bitmap từ một URI hình ảnh đã cho.
     * Phương thức này mở một InputStream từ URI, giải mã nó thành Bitmap,
     * và đảm bảo InputStream được đóng sau khi hoàn tất hoặc khi có lỗi.
     *
     * @param context Ngữ cảnh (Context) của ứng dụng, được sử dụng để truy cập ContentResolver.
     * @param imageUri URI của hình ảnh cần tải.
     * @return Đối tượng Bitmap đã được tải từ URI.
     * @throws IOException Nếu không thể mở InputStream từ URI hoặc không thể giải mã Bitmap.
     */
    public static Bitmap loadBitmapFromUri(Context context, Uri imageUri) throws IOException {
        InputStream inputStream = null; // Khởi tạo inputStream là null để đảm bảo nó được đóng trong khối finally.
        try {
            // Mở một InputStream từ URI sử dụng ContentResolver của ngữ cảnh.
            inputStream = context.getContentResolver().openInputStream(imageUri);
            // Kiểm tra nếu InputStream là null, có nghĩa là không thể mở luồng dữ liệu.
            if (inputStream == null) {
                // Ghi log lỗi và ném ngoại lệ IOException.
                Log.e(TAG, "Không thể mở InputStream từ URI: " + imageUri);
                throw new IOException("Không thể mở InputStream từ URI: " + imageUri);
            }

            // Giải mã InputStream thành một đối tượng Bitmap.
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            // Kiểm tra nếu Bitmap giải mã được là null, có nghĩa là không thể giải mã hình ảnh.
            if (bitmap == null) {
                // Ghi log lỗi và ném ngoại lệ IOException.
                Log.e(TAG, "Không thể giải mã Bitmap từ InputStream");
                throw new IOException("Không thể giải mã Bitmap từ InputStream");
            }
            // Trả về Bitmap đã tải thành công.
            return bitmap;
        } finally {
            // Đảm bảo InputStream được đóng, bất kể có lỗi xảy ra hay không.
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    // Ghi log nếu có lỗi khi đóng InputStream.
                    Log.e(TAG, "Lỗi khi đóng InputStream", e);
                }
            }
        }
    }
}