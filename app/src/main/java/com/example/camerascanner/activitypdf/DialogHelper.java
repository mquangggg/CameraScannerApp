package com.example.camerascanner.activitypdf;

import android.app.Activity;
import android.widget.EditText;
import androidx.appcompat.app.AlertDialog;

import com.example.camerascanner.activitypdf.Jpeg.JpegFileNameGenerator;
import com.example.camerascanner.activitypdf.pdf.FileNameGenerator;

public class DialogHelper {

    /**
     * Interface này được sử dụng để định nghĩa callback khi tên file được chọn.
     * Bất kỳ lớp nào muốn nhận tên file từ dialog đều phải implement interface này.
     */
    public interface OnFileNameSelectedListener {
        /**
         * Phương thức này được gọi khi người dùng đã chọn hoặc nhập tên file.
         *
         * @param fileName Tên file PDF đã được người dùng chọn/nhập.
         */
        void onFileNameSelected(String fileName);
    }

    /**
     * Hiển thị một dialog cho phép người dùng nhập hoặc chỉnh sửa tên file PDF.
     * Tên file mặc định sẽ được tạo sẵn và người dùng có thể chấp nhận hoặc thay đổi.
     * Sau khi người dùng nhấn "Lưu", tên file đã nhập sẽ được trả về thông qua listener.
     *
     * @param activity Hoạt động (Activity) hiện tại nơi dialog sẽ được hiển thị.
     * Cần thiết để tạo và hiển thị AlertDialog.
     * @param listener Một instance của OnFileNameSelectedListener để nhận tên file đã chọn
     * sau khi người dùng xác nhận lưu.
     */
    public static void showFileNameDialog(Activity activity, OnFileNameSelectedListener listener) {
        // Tạo tên file mặc định dựa trên thời gian hoặc quy tắc khác.
        String defaultFileName = FileNameGenerator.generateDefaultFileName();

        // Khởi tạo EditText để người dùng nhập tên file.
        final EditText input = new EditText(activity);
        // Đặt tên file mặc định vào EditText.
        input.setText(defaultFileName);
        // Chọn toàn bộ văn bản trong EditText khi nó được focus, giúp người dùng dễ dàng sửa đổi.
        input.setSelectAllOnFocus(true);

        // Xây dựng và hiển thị AlertDialog.
        new AlertDialog.Builder(activity)
                .setTitle("Lưu PDF") // Đặt tiêu đề cho dialog.
                .setMessage("Nhập tên cho tệp PDF:") // Đặt thông báo hướng dẫn cho người dùng.
                .setView(input) // Đặt EditText vào trong dialog để người dùng nhập liệu.
                .setPositiveButton("Lưu", (dialog, which) -> {
                    // Xử lý khi người dùng nhấn nút "Lưu".
                    String fileName = input.getText().toString().trim(); // Lấy tên file đã nhập và loại bỏ khoảng trắng thừa.
                    fileName = FileNameGenerator.ensurePdfExtension(fileName); // Đảm bảo tên file có đuôi ".pdf".
                    listener.onFileNameSelected(fileName); // Gọi callback để trả về tên file đã chọn.
                })
                .setNegativeButton("Hủy", (dialog, which) -> dialog.cancel()) // Xử lý khi người dùng nhấn nút "Hủy", đóng dialog.
                .show(); // Hiển thị dialog.
    }
    public static void showJpegFileNameDialog(Activity activity, OnFileNameSelectedListener listener) {
        // Tạo tên file mặc định dựa trên thời gian hoặc quy tắc khác.
        String defaultFileName = JpegFileNameGenerator.generateDefaultFileName();

        // Khởi tạo EditText để người dùng nhập tên file.
        final EditText input = new EditText(activity);
        // Đặt tên file mặc định vào EditText.
        input.setText(defaultFileName);
        // Chọn toàn bộ văn bản trong EditText khi nó được focus, giúp người dùng dễ dàng sửa đổi.
        input.setSelectAllOnFocus(true);

        // Xây dựng và hiển thị AlertDialog.
        new AlertDialog.Builder(activity)
                .setTitle("Lưu JPEG") // Đặt tiêu đề cho dialog.
                .setMessage("Nhập tên cho tệp JPEG:") // Đặt thông báo hướng dẫn cho người dùng.
                .setView(input) // Đặt EditText vào trong dialog để người dùng nhập liệu.
                .setPositiveButton("Lưu", (dialog, which) -> {
                    // Xử lý khi người dùng nhấn nút "Lưu".
                    String fileName = input.getText().toString().trim(); // Lấy tên file đã nhập và loại bỏ khoảng trắng thừa.
                    fileName = JpegFileNameGenerator.ensureJpegExtension(fileName); // Đảm bảo tên file có đuôi ".jpg".
                    listener.onFileNameSelected(fileName); // Gọi callback để trả về tên file đã chọn.
                })
                .setNegativeButton("Hủy", (dialog, which) -> dialog.cancel()) // Xử lý khi người dùng nhấn nút "Hủy", đóng dialog.
                .show(); // Hiển thị dialog.
    }
}