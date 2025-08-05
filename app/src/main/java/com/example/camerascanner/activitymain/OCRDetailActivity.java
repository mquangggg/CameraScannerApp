package com.example.camerascanner.activitymain;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.camerascanner.R;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * OCRDetailActivity hiển thị chi tiết kết quả OCR, bao gồm ảnh gốc và văn bản đã nhận dạng.
 * Người dùng có thể xem ảnh, xem/chỉnh sửa văn bản và sao chép văn bản vào clipboard.
 */
public class OCRDetailActivity extends AppCompatActivity {

    private static final String TAG = "OCRDetailActivity"; // Thẻ (tag) dùng để ghi log cho debugging
    // Các khóa (keys) cho Intent extras để truyền URI của ảnh và văn bản liên quan đến OCR
    public static final String EXTRA_IMAGE_URI = "extra_image_uri";
    public static final String EXTRA_TEXT_URI = "extra_text_uri";

    // Khai báo các thành phần UI
    private ImageView ivDetailImage; // ImageView để hiển thị ảnh đã được OCR
    private EditText etDetailText; // EditText để hiển thị và cho phép chỉnh sửa văn bản OCR
    private Button btnDetailCopyToClipboard; // Nút để sao chép văn bản từ EditText vào clipboard

    private ExecutorService executorService; // Dịch vụ thực thi để tải nội dung ảnh và văn bản trên luồng nền

    /**
     * Phương thức được gọi khi Activity lần đầu tiên được tạo.
     * Thiết lập layout, ánh xạ các View, khởi tạo ExecutorService,
     * lấy URI dữ liệu từ Intent và bắt đầu tải nội dung,
     * đồng thời thiết lập listener cho nút sao chép.
     *
     * @param savedInstanceState Đối tượng Bundle chứa trạng thái Activity trước đó nếu có.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ocr_detail); // Đặt layout cho Activity này

        // Ánh xạ các View từ layout XML bằng ID
        ivDetailImage = findViewById(R.id.ivDetailImage);
        etDetailText = findViewById(R.id.etDetailText);
        btnDetailCopyToClipboard = findViewById(R.id.btnDetailCopyToClipboard);

        // Khởi tạo ExecutorService với một luồng duy nhất để quản lý các tác vụ tải dữ liệu
        executorService = Executors.newSingleThreadExecutor();

        // Lấy URI của ảnh và văn bản từ Intent đã truyền đến Activity này.
        // EXTRA_IMAGE_URI được lấy trực tiếp vì nó là dữ liệu chính của Intent,
        // EXTRA_TEXT_URI được lấy như một Parcelable extra.
        Uri imageUri = getIntent().getParcelableExtra(EXTRA_IMAGE_URI);
        Uri textUri = getIntent().getParcelableExtra(EXTRA_TEXT_URI);

        // Kiểm tra xem cả hai URI đều có tồn tại không trước khi tải nội dung
        if (imageUri != null && textUri != null) {
            // Tải và hiển thị ảnh cùng văn bản trên luồng nền để tránh chặn luồng UI
            loadOcrContent(imageUri, textUri);
        } else {
            // Thông báo lỗi và đóng Activity nếu một trong hai URI bị thiếu
            Toast.makeText(this, getString(R.string.error_load_ocr_uri_missing), Toast.LENGTH_SHORT).show();
            finish(); // Đóng Activity
        }

        // Thiết lập sự kiện click cho nút "Copy to Clipboard"
        btnDetailCopyToClipboard.setOnClickListener(v -> {
            copyTextToClipboard(); // Gọi phương thức sao chép văn bản
        });
    }

    /**
     * Tải ảnh từ URI và văn bản từ URI trên một luồng nền.
     * Sau khi tải xong, cập nhật ImageView và EditText trên luồng UI chính.
     * Xử lý lỗi nếu không thể tải ảnh hoặc văn bản.
     *
     * @param imageUri URI của tệp ảnh.
     * @param textUri URI của tệp văn bản.
     */
    private void loadOcrContent(Uri imageUri, Uri textUri) {
        // Thực thi tác vụ tải trên luồng nền do ExecutorService quản lý
        executorService.execute(() -> {
            Bitmap imageBitmap = null; // Biến để lưu Bitmap của ảnh
            String textContent = getString(R.string.error_load_text); // Biến để lưu nội dung văn bản, với giá trị mặc định lỗi

            // 1. Tải ảnh từ URI
            try (InputStream is = getContentResolver().openInputStream(imageUri)) {
                if (is != null) {
                    imageBitmap = BitmapFactory.decodeStream(is); // Decode Bitmap từ InputStream
                }
            } catch (IOException e) {
                // Ghi log lỗi nếu không thể tải ảnh
                Log.e(TAG, "Lỗi khi tải ảnh từ URI: " + imageUri, e);
            }

            // 2. Tải văn bản từ URI
            try (InputStream is = getContentResolver().openInputStream(textUri);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                StringBuilder stringBuilder = new StringBuilder(); // Sử dụng StringBuilder để xây dựng văn bản
                String line;
                while ((line = reader.readLine()) != null) {
                    stringBuilder.append(line).append("\n"); // Đọc từng dòng và thêm vào StringBuilder, kèm ký tự xuống dòng
                }
                textContent = stringBuilder.toString(); // Chuyển đổi StringBuilder thành String
            } catch (IOException e) {
                // Ghi log lỗi nếu không thể tải văn bản
                Log.e(TAG, "Lỗi khi tải văn bản từ URI: " + textUri, e);
            }

            // Tạo các biến final để có thể truy cập từ trong Runnable của runOnUiThread
            final Bitmap finalImageBitmap = imageBitmap;
            final String finalTextContent = textContent;

            // Cập nhật giao diện người dùng trên luồng UI chính
            runOnUiThread(() -> {
                if (finalImageBitmap != null) {
                    ivDetailImage.setImageBitmap(finalImageBitmap); // Hiển thị ảnh
                } else {
                    Toast.makeText(OCRDetailActivity.this, getString(R.string.error_display_image), Toast.LENGTH_SHORT).show();
                }
                etDetailText.setText(finalTextContent); // Hiển thị văn bản
            });
        });
    }

    /**
     * Sao chép văn bản hiện có trong EditText vào clipboard của hệ thống.
     * Hiển thị Toast thông báo cho người dùng về kết quả thao tác.
     */
    private void copyTextToClipboard() {
        // Lấy văn bản từ EditText và loại bỏ khoảng trắng thừa ở đầu/cuối
        String textToCopy = etDetailText.getText().toString().trim();
        if (textToCopy.isEmpty()) {
            Toast.makeText(this, getString(R.string.no_text_to_copy), Toast.LENGTH_LONG).show();
            return; // Thoát nếu không có văn bản
        }

        // Lấy ClipboardManager của hệ thống
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        // Tạo một ClipData mới với nhãn "Văn bản OCR" và nội dung văn bản cần sao chép
        ClipData clip = ClipData.newPlainText("Văn bản OCR", textToCopy);
        if (clipboard != null) {
            clipboard.setPrimaryClip(clip); // Đặt ClipData vào clipboard
            Toast.makeText(this, getString(R.string.text_copied_to_clipboard), Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this,getString(R.string.error_copy_text) , Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Phương thức được gọi khi Activity bị hủy.
     * Đảm bảo rằng ExecutorService được tắt để giải phóng tài nguyên
     * và ngăn chặn các tác vụ đang chờ chạy tiếp tục sau khi Activity bị hủy.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Kiểm tra nếu ExecutorService không rỗng và chưa tắt
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow(); // Tắt ngay lập tức tất cả các tác vụ đang chờ
        }
    }
}