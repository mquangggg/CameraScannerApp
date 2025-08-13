package com.example.camerascanner.activitypdf.pdf;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.util.Log;

public class PdfFileManager {
    private static final String TAG = "PdfFileManager"; // Tag để sử dụng trong Logcat, giúp dễ dàng lọc thông báo.

    private final Context context; // Biến thành viên để lưu trữ Context của ứng dụng, cần thiết cho các thao tác với ContentResolver.

    /**
     * Hàm khởi tạo (Constructor) cho lớp PdfFileManager.
     * Khởi tạo một đối tượng PdfFileManager mới với Context đã cung cấp.
     *
     * @param context Ngữ cảnh (Context) của ứng dụng, được sử dụng để truy cập ContentResolver
     * và thực hiện các thao tác file.
     */
    public PdfFileManager(Context context) {
        this.context = context; // Gán Context được truyền vào cho biến thành viên.
    }

    /**
     * Xóa một file PDF dựa trên URI của nó.
     * Phương thức này sử dụng ContentResolver để gửi yêu cầu xóa file từ hệ thống.
     *
     * @param pdfUri URI của file PDF cần xóa. Đây là định danh duy nhất của file trong hệ thống.
     * @return true nếu file được xóa thành công, false nếu không thể xóa hoặc có lỗi xảy ra.
     */
    public boolean deletePdfFile(Uri pdfUri) {
        // Kiểm tra nếu URI là null, không thể xóa file không có địa chỉ.
        if (pdfUri == null) {
            Log.e(TAG, "URI PDF được cung cấp là null.");
            return false;
        }

        try {
            // Lấy ContentResolver từ Context để tương tác với các nhà cung cấp nội dung (bao gồm cả quản lý file).
            ContentResolver resolver = context.getContentResolver();
            // Thực hiện thao tác xóa. Phương thức delete() trả về số hàng (files) đã bị xóa.
            // Các tham số selection và selectionArgs được đặt là null vì chúng ta đang xóa một file cụ thể bằng URI.
            int rowsDeleted = resolver.delete(pdfUri, null, null);

            // Kiểm tra xem có bao nhiêu hàng đã bị xóa. Nếu lớn hơn 0, có nghĩa là file đã được xóa.
            if (rowsDeleted > 0) {
                // Ghi log thông báo xóa thành công.
                Log.d(TAG, "Đã xóa PDF thành công: " + pdfUri);
                return true; // Trả về true để chỉ ra xóa thành công.
            } else {
                // Ghi log thông báo không thể xóa (ví dụ: file không tồn tại hoặc không có quyền).
                Log.e(TAG, "Không thể xóa PDF: " + pdfUri + ". Có thể file không tồn tại hoặc không có quyền.");
                return false; // Trả về false để chỉ ra xóa không thành công.
            }
        } catch (Exception e) {
            // Bắt bất kỳ ngoại lệ nào có thể xảy ra trong quá trình xóa file (ví dụ: SecurityException).
            // Ghi log lỗi chi tiết.
            Log.e(TAG, "Lỗi khi xóa PDF: " + e.getMessage(), e);
            return false; // Trả về false do có lỗi.
        }
    }
}