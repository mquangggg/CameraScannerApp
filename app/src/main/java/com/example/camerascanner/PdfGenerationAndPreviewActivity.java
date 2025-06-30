package com.example.camerascanner;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.pdf.PdfDocument;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Activity này chịu trách nhiệm hiển thị bản xem trước của ảnh,
 * cho phép người dùng chọn kiểu PDF (ảnh gốc hoặc ảnh trắng đen),
 * tạo file PDF và hiển thị bản xem trước của PDF.
 */
public class PdfGenerationAndPreviewActivity extends AppCompatActivity {

    // Tag để sử dụng cho Logcat, giúp dễ dàng lọc thông báo log của Activity này.
    private static final String TAG = "PdfGenAndPreview";
    // Mã yêu cầu quyền để xử lý kết quả cấp quyền.
    private static final int PERMISSION_REQUEST_CODE = 1;

    // Khai báo các thành phần UI (View) từ layout XML.
    private ImageView ivPdfPreview; // ImageView để hiển thị bản xem trước PDF.
    private Button btnSavePdf; // Nút để lưu PDF.
    private Button btnDeletePdf; // Nút để xóa PDF hiện tại.
    private Button btnRegeneratePdf; // Nút để tạo lại PDF với lựa chọn kiểu ảnh mới.
    private TextView tvPdfPreviewStatus; // TextView để hiển thị trạng thái xử lý PDF.
    private RadioGroup rgPdfStyle; // RadioGroup chứa các lựa chọn kiểu PDF.
    private RadioButton rbOriginal; // RadioButton cho chế độ ảnh gốc.
    private RadioButton rbBlackWhite; // RadioButton cho chế độ ảnh trắng đen.

    // Các biến để lưu trữ dữ liệu và tài nguyên.
    private Uri imageUriToConvert; // URI của ảnh gốc được truyền từ Activity trước.
    private Bitmap croppedBitmap; // Bitmap của ảnh đã cắt (phiên bản gốc).
    private Bitmap processedBitmap; // Bitmap của ảnh đã được xử lý (trắng đen).
    private Uri finalPdfUri; // URI của file PDF đã được tạo và lưu.

    // Các biến cho việc xử lý bất đồng bộ.
    private ExecutorService executorService; // Dịch vụ thực thi tác vụ trên luồng nền.
    private Handler mainHandler; // Handler để gửi các tác vụ về luồng chính (UI thread).

    // Biến trạng thái để theo dõi kiểu PDF hiện tại mà người dùng đã chọn.
    private PdfStyle currentPdfStyle = PdfStyle.ORIGINAL;

    /**
     * Enum định nghĩa các kiểu xử lý ảnh khi tạo PDF.
     * ORIGINAL: Sử dụng ảnh gốc (đã cắt).
     * BLACK_WHITE: Sử dụng ảnh đã chuyển sang trắng đen.
     */
    private enum PdfStyle {
        ORIGINAL,
        BLACK_WHITE
    }

    /**
     * Phương thức được gọi khi Activity được tạo lần đầu.
     * Đây là nơi bạn khởi tạo các thành phần UI, thiết lập lắng nghe sự kiện,
     * và xử lý dữ liệu được truyền đến Activity này.
     *
     * @param savedInstanceState Nếu Activity được khởi tạo lại (ví dụ: sau khi xoay màn hình),
     * đây là Bundle chứa dữ liệu trạng thái gần nhất của Activity.
     */
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Thiết lập layout cho Activity từ file XML.
        setContentView(R.layout.activity_pdf_generation_and_preview);

        // Ánh xạ các thành phần UI từ layout XML vào các biến Java.
        ivPdfPreview = findViewById(R.id.ivPdfPreview);
        btnSavePdf = findViewById(R.id.btnSavePdf);
        btnDeletePdf = findViewById(R.id.btnDeletePdf);
        btnRegeneratePdf = findViewById(R.id.btnRegeneratePdf);
        tvPdfPreviewStatus = findViewById(R.id.tvPdfPreviewStatus);
        rgPdfStyle = findViewById(R.id.rgPdfStyle);
        rbOriginal = findViewById(R.id.rbOriginal);
        rbBlackWhite = findViewById(R.id.rbBlackWhite);

        // Khởi tạo ExecutorService để chạy các tác vụ nặng trên luồng nền.
        executorService = Executors.newSingleThreadExecutor();
        // Khởi tạo Handler để giao tiếp với luồng UI chính từ các luồng nền.
        mainHandler = new Handler(Looper.getMainLooper());

        // Kiểm tra xem có URI ảnh được truyền từ Activity trước đó hay không.
        if (getIntent().getExtras() != null && getIntent().getExtras().containsKey("croppedUri")) {
            imageUriToConvert = getIntent().getParcelableExtra("croppedUri");
            if (imageUriToConvert != null) {
                // Cập nhật trạng thái trên UI.
                if (tvPdfPreviewStatus != null) tvPdfPreviewStatus.setText(getString(R.string.status_loading_and_processing_image));
                // Bắt đầu quá trình tải và xử lý ảnh bất đồng bộ.
                loadAndProcessImageAsync();
            } else {
                // Hiển thị thông báo lỗi và đóng Activity nếu URI không hợp lệ.
                Toast.makeText(this, getString(R.string.error_invalid_image_uri), Toast.LENGTH_SHORT).show();
                if (tvPdfPreviewStatus != null) tvPdfPreviewStatus.setText(getString(R.string.error_loading_image_uri));
                finish();
            }
        } else {
            // Hiển thị thông báo lỗi và đóng Activity nếu không có ảnh để xử lý.
            Toast.makeText(this, getString(R.string.error_no_image_to_process), Toast.LENGTH_SHORT).show();
            if (tvPdfPreviewStatus != null) tvPdfPreviewStatus.setText(getString(R.string.error_no_image_to_process));
            finish();
        }

        // Đặt lựa chọn mặc định cho RadioGroup là "Gốc" và cập nhật trạng thái kiểu PDF.
        rbOriginal.setChecked(true);
        currentPdfStyle = PdfStyle.ORIGINAL;

        /**
         * Thiết lập lắng nghe sự kiện thay đổi lựa chọn trên RadioGroup.
         * Khi người dùng chọn giữa "Gốc" và "Trắng đen", cập nhật biến `currentPdfStyle`
         * và tự động tạo lại bản xem trước PDF.
         */
        rgPdfStyle.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbOriginal) {
                currentPdfStyle = PdfStyle.ORIGINAL;
                Toast.makeText(this, "Chế độ Gốc được chọn", Toast.LENGTH_SHORT).show();
            } else if (checkedId == R.id.rbBlackWhite) {
                currentPdfStyle = PdfStyle.BLACK_WHITE;
                Toast.makeText(this, "Chế độ Trắng đen được chọn", Toast.LENGTH_SHORT).show();
            }
            // Cập nhật trạng thái trên UI thread ngay lập tức.
            mainHandler.post(() -> {
                if (tvPdfPreviewStatus != null) tvPdfPreviewStatus.setText("Đang tạo lại bản xem trước...");
            });
            // Kiểm tra quyền và bắt đầu quá trình tạo lại PDF với kiểu mới.
            checkPermissionsAndGeneratePdfInternal();
        });

        /**
         * Thiết lập lắng nghe sự kiện click cho nút "Lưu PDF".
         * Khi nút được nhấn, hiển thị thông báo xác nhận và đóng Activity.
         * Việc lưu PDF thực tế đã diễn ra trong hàm `generatePdf()`.
         */
        btnSavePdf.setOnClickListener(v -> {
            Toast.makeText(this, getString(R.string.pdf_saved_confirmation), Toast.LENGTH_SHORT).show();
            finish();
        });

        /**
         * Thiết lập lắng nghe sự kiện click cho nút "Xóa & Hủy".
         * Khi nút được nhấn, gọi hàm để xóa file PDF đã tạo và đóng Activity.
         */
        btnDeletePdf.setOnClickListener(v -> {
            deletePdfDocument(finalPdfUri);
            Toast.makeText(this, getString(R.string.pdf_deleted_and_canceled), Toast.LENGTH_SHORT).show();
            finish();
        });

        /**
         * Thiết lập lắng nghe sự kiện click cho nút "Tạo lại PDF".
         * Khi nút được nhấn, cập nhật trạng thái trên UI và bắt đầu lại quá trình
         * tạo PDF với kiểu ảnh hiện tại đang được chọn (Gốc hoặc Trắng đen).
         */
        btnRegeneratePdf.setOnClickListener(v -> {
            mainHandler.post(() -> {
                if (tvPdfPreviewStatus != null) tvPdfPreviewStatus.setText(getString(R.string.status_generating_pdf));
            });
            checkPermissionsAndGeneratePdfInternal();
        });
    }

    /**
     * Tải ảnh từ URI và xử lý (chuyển sang trắng đen) một cách bất đồng bộ trên luồng nền.
     * Hàm này sẽ:
     * 1. Mở InputStream từ URI ảnh.
     * 2. Giải mã ảnh thành Bitmap gốc (`croppedBitmap`).
     * 3. Chuyển đổi Bitmap gốc thành phiên bản trắng đen (`processedBitmap`).
     * 4. Sau khi xử lý xong, gọi hàm để kiểm tra quyền và tạo PDF.
     * Tất cả các bước nặng về I/O và xử lý hình ảnh đều được thực hiện trên `executorService`.
     */
    private void loadAndProcessImageAsync() {
        executorService.execute(() -> {
            try {
                InputStream inputStream = getContentResolver().openInputStream(imageUriToConvert);
                if (inputStream == null) {
                    throw new IOException("Không thể mở InputStream từ URI: " + imageUriToConvert);
                }

                // Giải mã ảnh gốc.
                croppedBitmap = android.graphics.BitmapFactory.decodeStream(inputStream);
                inputStream.close();

                if (croppedBitmap == null) {
                    throw new IOException("Không thể giải mã Bitmap từ InputStream.");
                }

                // Cập nhật trạng thái trên UI thread.
                mainHandler.post(() -> {
                    if (tvPdfPreviewStatus != null) tvPdfPreviewStatus.setText(getString(R.string.status_converting_to_black_white));
                });
                // Chuyển đổi ảnh gốc sang trắng đen.
                processedBitmap = convertToBlackAndWhite(croppedBitmap);

                // Cập nhật trạng thái trên UI thread.
                mainHandler.post(() -> {
                    if (tvPdfPreviewStatus != null) tvPdfPreviewStatus.setText(getString(R.string.status_generating_pdf));
                });

                // Kiểm tra quyền và tiến hành tạo PDF.
                checkPermissionsAndGeneratePdfInternal();

            } catch (Exception e) {
                // Ghi log lỗi và hiển thị Toast trên UI thread nếu có lỗi.
                Log.e(TAG, "Lỗi khi tải hoặc xử lý ảnh trong background: " + e.getMessage(), e);
                mainHandler.post(() -> {
                    Toast.makeText(this, String.format(getString(R.string.error_loading_processing_image_background), e.getMessage()), Toast.LENGTH_LONG).show();
                    if (tvPdfPreviewStatus != null) tvPdfPreviewStatus.setText(getString(R.string.error_failed_to_load_image));
                    finish();
                });
            }
        });
    }

    /**
     * Chuyển đổi một Bitmap màu thành Bitmap trắng đen.
     * Phương thức này duyệt qua từng pixel của Bitmap gốc, tính toán giá trị xám
     * của pixel đó, và sau đó áp dụng một ngưỡng để quyết định pixel đó sẽ là màu đen
     * hoặc màu trắng trong Bitmap mới.
     *
     * @param original Bitmap gốc cần được chuyển đổi.
     * @return Một Bitmap mới đã được chuyển đổi sang định dạng trắng đen.
     */
    private Bitmap convertToBlackAndWhite(Bitmap original) {
        int width = original.getWidth();
        int height = original.getHeight();
        // Tạo một Bitmap mới với cùng kích thước, định dạng ARGB_8888 (hỗ trợ alpha).
        Bitmap bwBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bwBitmap);
        canvas.drawColor(Color.WHITE); // Đặt nền mặc định của Bitmap trắng đen là màu trắng.

        int pixel;
        int A, R, G, B;
        int threshold = 128; // Ngưỡng để phân biệt đen và trắng (giá trị từ 0 đến 255). Có thể điều chỉnh.

        // Duyệt qua từng pixel của Bitmap.
        for (int x = 0; x < width; ++x) {
            for (int y = 0; y < height; ++y) {
                pixel = original.getPixel(x, y); // Lấy giá trị màu của pixel.
                A = Color.alpha(pixel); // Kênh Alpha.
                R = Color.red(pixel); // Kênh Đỏ.
                G = Color.green(pixel); // Kênh Xanh lá.
                B = Color.blue(pixel); // Kênh Xanh dương.

                // Tính toán giá trị xám trung bình từ các kênh màu.
                int gray = (R + G + B) / 3;

                // Áp dụng ngưỡng: nếu giá trị xám thấp hơn ngưỡng, pixel là đen; ngược lại, là trắng.
                if (gray < threshold) {
                    bwBitmap.setPixel(x, y, Color.argb(A, 0, 0, 0)); // Đặt pixel là màu đen.
                } else {
                    bwBitmap.setPixel(x, y, Color.argb(A, 255, 255, 255)); // Đặt pixel là màu trắng.
                }
            }
        }
        return bwBitmap; // Trả về Bitmap trắng đen đã tạo.
    }

    /**
     * Kiểm tra quyền truy cập bộ nhớ cần thiết để lưu PDF.
     * Trên Android Q (API 29) trở lên, sử dụng MediaStore API không yêu cầu quyền
     * WRITE_EXTERNAL_STORAGE. Dưới Android Q, quyền này là bắt buộc.
     * Nếu quyền chưa được cấp, phương thức sẽ yêu cầu quyền.
     * Sau khi quyền được cấp (hoặc nếu không cần quyền), sẽ gọi `generatePdf()`.
     */
    private void checkPermissionsAndGeneratePdfInternal() {
        // Kiểm tra phiên bản Android.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Trên Android Q (API 29) trở lên, không cần quyền WRITE_EXTERNAL_STORAGE khi dùng MediaStore.
            generatePdf();
        } else {
            // Đối với Android 9 (Pie) trở xuống, kiểm tra quyền WRITE_EXTERNAL_STORAGE.
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                // Nếu quyền chưa được cấp, yêu cầu quyền.
                mainHandler.post(() -> {
                    ActivityCompat.requestPermissions(this,
                            new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                            PERMISSION_REQUEST_CODE);
                });
            } else {
                // Nếu quyền đã được cấp, tiến hành tạo PDF.
                generatePdf();
            }
        }
    }

    /**
     * Callback được gọi sau khi người dùng phản hồi yêu cầu cấp quyền.
     *
     * @param requestCode Mã yêu cầu được truyền khi gọi `requestPermissions`.
     * @param permissions Mảng chứa các quyền đã yêu cầu.
     * @param grantResults Mảng chứa kết quả cấp quyền cho từng quyền tương ứng.
     * `PackageManager.PERMISSION_GRANTED` nếu quyền được cấp,
     * `PackageManager.PERMISSION_DENIED` nếu quyền bị từ chối.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            // Kiểm tra xem quyền WRITE_EXTERNAL_STORAGE có được cấp hay không.
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Nếu quyền được cấp, bắt đầu tạo PDF trên luồng nền.
                executorService.execute(this::generatePdf);
            } else {
                // Nếu quyền bị từ chối, hiển thị thông báo cho người dùng.
                mainHandler.post(() -> {
                    Toast.makeText(this, getString(R.string.permission_required_to_save_pdf), Toast.LENGTH_LONG).show();
                    if (tvPdfPreviewStatus != null) tvPdfPreviewStatus.setText(getString(R.string.error_no_permission_to_save_pdf));
                });
            }
        }
    }

    /**
     * Tạo một tài liệu PDF từ Bitmap đã chọn (gốc hoặc trắng đen) và lưu nó vào bộ nhớ.
     * Phương thức này:
     * 1. Chọn Bitmap để sử dụng dựa trên `currentPdfStyle`.
     * 2. Tạo một đối tượng `PdfDocument`.
     * 3. Vẽ Bitmap lên trang PDF, điều chỉnh tỷ lệ để vừa với trang.
     * 4. Lưu tài liệu PDF vào bộ nhớ, sử dụng MediaStore API trên Android Q+ hoặc FileOutputStream trên các phiên bản cũ hơn.
     * 5. Sau khi lưu thành công, hiển thị bản xem trước PDF.
     */
    private void generatePdf() {
        Bitmap bitmapToUse = null;
        String styleName = "";

        // Xác định Bitmap và tên kiểu ảnh dựa trên lựa chọn của người dùng.
        if (currentPdfStyle == PdfStyle.ORIGINAL) {
            bitmapToUse = croppedBitmap; // Sử dụng ảnh gốc đã cắt.
            styleName = "Gốc";
        } else if (currentPdfStyle == PdfStyle.BLACK_WHITE) {
            bitmapToUse = processedBitmap; // Sử dụng ảnh đã xử lý trắng đen.
            styleName = "Trắng đen";
        }

        // Kiểm tra xem có Bitmap để tạo PDF không.
        if (bitmapToUse == null) {
            mainHandler.post(() -> {
                Toast.makeText(this, getString(R.string.error_no_processed_image_for_pdf), Toast.LENGTH_SHORT).show();
                if (tvPdfPreviewStatus != null) tvPdfPreviewStatus.setText(getString(R.string.error_no_image_to_create_pdf));
            });
            return;
        }

        // Cập nhật trạng thái trên UI thread. Sử dụng biến `final` để truy cập trong lambda.
        final Bitmap finalBitmapToUse = bitmapToUse;
        final String finalStyleName = styleName;
        mainHandler.post(() -> {
            if (tvPdfPreviewStatus != null) tvPdfPreviewStatus.setText(getString(R.string.status_generating_pdf) + " (" + finalStyleName + ")");
        });

        // Khởi tạo một tài liệu PDF mới.
        PdfDocument document = new PdfDocument();
        int pageHeight = 1120; // Chiều cao của trang PDF (tương đương khổ A4).
        int pageWidth = 792;  // Chiều rộng của trang PDF (tương đương khổ A4).
        // Tạo thông tin cho trang đầu tiên của PDF.
        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create();

        // Bắt đầu một trang mới trong tài liệu PDF.
        PdfDocument.Page page = document.startPage(pageInfo);
        Canvas canvas = page.getCanvas(); // Lấy Canvas để vẽ nội dung lên trang PDF.

        // Tính toán tỷ lệ co giãn để ảnh vừa vặn với trang PDF mà vẫn giữ nguyên tỷ lệ khung hình.
        float scaleX = (float) pageWidth / finalBitmapToUse.getWidth();
        float scaleY = (float) pageHeight / finalBitmapToUse.getHeight();
        float scale = Math.min(scaleX, scaleY); // Chọn tỷ lệ nhỏ hơn để đảm bảo ảnh không bị cắt.

        // Tính toán vị trí để căn giữa ảnh trên trang PDF.
        float left = (pageWidth - finalBitmapToUse.getWidth() * scale) / 2;
        float top = (pageHeight - finalBitmapToUse.getHeight() * scale) / 2;

        // Vẽ Bitmap đã chọn lên Canvas của trang PDF.
        canvas.drawBitmap(finalBitmapToUse, null, new android.graphics.RectF(left, top, left + finalBitmapToUse.getWidth() * scale, top + finalBitmapToUse.getHeight() * scale), null);
        document.finishPage(page); // Hoàn thành việc vẽ trang.

        // Tạo tên file PDF động với timestamp và kiểu ảnh (Original/BW).
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String pdfFileName = "ScannedPdf_" + timeStamp + "_" + (finalStyleName.equals("Gốc") ? "Original" : "BW") + ".pdf";

        OutputStream outputStream = null;
        try {
            // Kiểm tra phiên bản Android để quyết định cách lưu file.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Trên Android Q (API 29) trở lên, sử dụng MediaStore API.
                ContentResolver resolver = getContentResolver();
                ContentValues contentValues = new ContentValues();
                contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, pdfFileName); // Tên hiển thị của file.
                contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf"); // Kiểu MIME của file (PDF).
                // Đặt đường dẫn tương đối trong thư mục Tải xuống của ứng dụng.
                contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + File.separator + "MyPDFs");
                // Chèn file mới vào MediaStore và nhận URI của nó.
                finalPdfUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues);
                if (finalPdfUri != null) {
                    // Mở OutputStream để ghi dữ liệu vào URI vừa tạo.
                    outputStream = resolver.openOutputStream(finalPdfUri);
                }
            } else {
                // Đối với Android 9 (Pie) trở xuống, lưu trực tiếp vào thư mục công khai.
                File pdfDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "MyPDFs");
                if (!pdfDir.exists()) {
                    pdfDir.mkdirs(); // Tạo thư mục nếu nó chưa tồn tại.
                }
                File pdfFile = new File(pdfDir, pdfFileName);
                outputStream = new FileOutputStream(pdfFile); // Mở FileOutputStream để ghi dữ liệu.
                finalPdfUri = Uri.fromFile(pdfFile); // Lấy URI từ file đã tạo.
            }

            // Kiểm tra xem OutputStream có được mở thành công không.
            if (outputStream != null) {
                document.writeTo(outputStream); // Ghi nội dung của tài liệu PDF vào OutputStream.
                // Cập nhật UI thread với thông báo thành công.
                mainHandler.post(() -> {
                    Toast.makeText(this, String.format(getString(R.string.pdf_creation_successful), pdfFileName), Toast.LENGTH_LONG).show();
                    if (tvPdfPreviewStatus != null) tvPdfPreviewStatus.setText(String.format(getString(R.string.pdf_saved_at), (finalPdfUri != null ? finalPdfUri.getPath() : "Không xác định")));
                    Log.d(TAG, "PDF saved at: " + (finalPdfUri != null ? finalPdfUri.toString() : "null"));
                });

                // Nếu URI PDF hợp lệ, hiển thị bản xem trước.
                if (finalPdfUri != null) {
                    final Uri uriToPreview = finalPdfUri;
                    executorService.execute(() -> displayPdfPreview(uriToPreview));
                } else {
                    mainHandler.post(() -> Toast.makeText(this, getString(R.string.error_no_pdf_uri_for_preview), Toast.LENGTH_SHORT).show());
                }

            } else {
                throw new IOException("Không thể mở OutputStream để lưu PDF.");
            }

        } catch (IOException e) {
            // Xử lý lỗi khi tạo hoặc lưu PDF, hiển thị thông báo trên UI thread.
            Log.e(TAG, "Lỗi khi tạo hoặc lưu PDF: " + e.getMessage(), e);
            mainHandler.post(() -> {
                Toast.makeText(this, String.format(getString(R.string.error_creating_pdf), e.getMessage()), Toast.LENGTH_LONG).show();
                if (tvPdfPreviewStatus != null) tvPdfPreviewStatus.setText(String.format(getString(R.string.error_creating_pdf), e.getMessage()));
            });
            finalPdfUri = null; // Đặt lại URI nếu có lỗi.
        } finally {
            // Đảm bảo đóng tài liệu PDF và OutputStream để giải phóng tài nguyên.
            document.close();
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    Log.e(TAG, "Lỗi khi đóng OutputStream: " + e.getMessage(), e);
                }
            }
        }
    }

    /**
     * Hiển thị trang đầu tiên của tài liệu PDF trong ImageView.
     * Phương thức này sử dụng `PdfRenderer`, một API chỉ khả dụng từ Android 5.0 (API 21) trở lên.
     * Nó mở file PDF, render trang đầu tiên thành một Bitmap và đặt Bitmap đó vào `ivPdfPreview`.
     *
     * @param pdfUri URI của file PDF cần hiển thị.
     */
    private void displayPdfPreview(Uri pdfUri) {
        // Kiểm tra phiên bản Android để đảm bảo `PdfRenderer` khả dụng.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            mainHandler.post(() -> {
                Toast.makeText(this, getString(R.string.pdf_preview_unsupported_android_version), Toast.LENGTH_LONG).show();
                if (tvPdfPreviewStatus != null) tvPdfPreviewStatus.setText(getString(R.string.pdf_preview_unsupported));
            });
            return;
        }

        PdfRenderer renderer = null;
        ParcelFileDescriptor parcelFileDescriptor = null;
        PdfRenderer.Page currentPage = null;
        Bitmap renderedBitmap = null;
        try {
            // Mở ParcelFileDescriptor từ URI của PDF.
            parcelFileDescriptor = getContentResolver().openFileDescriptor(pdfUri, "r");
            if (parcelFileDescriptor == null) {
                throw new IOException("Không thể mở ParcelFileDescriptor cho URI PDF.");
            }
            renderer = new PdfRenderer(parcelFileDescriptor); // Tạo đối tượng PdfRenderer.

            currentPage = renderer.openPage(0); // Mở trang đầu tiên của PDF (chỉ mục 0).

            // Lấy kích thước mong muốn để render Bitmap, ưu tiên kích thước của ImageView.
            int desiredWidth = ivPdfPreview.getWidth();
            int desiredHeight = ivPdfPreview.getHeight();

            // Nếu ImageView chưa có kích thước (ví dụ: chưa được layout), sử dụng kích thước trang PDF
            // hoặc giới hạn kích thước để tránh tạo Bitmap quá lớn.
            if (desiredWidth == 0 || desiredHeight == 0) {
                desiredWidth = currentPage.getWidth();
                desiredHeight = currentPage.getHeight();
                if (desiredWidth > 2000) desiredWidth = 2000;
                if (desiredHeight > 2000) desiredHeight = 2000;
            }

            // Tạo một Bitmap để render trang PDF vào.
            renderedBitmap = Bitmap.createBitmap(desiredWidth, desiredHeight, Bitmap.Config.ARGB_8888);
            // Render trang PDF vào Bitmap.
            currentPage.render(renderedBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);

            final Bitmap finalRenderedBitmap = renderedBitmap;
            // Cập nhật ImageView và trạng thái trên UI thread.
            mainHandler.post(() -> {
                ivPdfPreview.setImageBitmap(finalRenderedBitmap);
                if (tvPdfPreviewStatus != null) tvPdfPreviewStatus.setText(getString(R.string.pdf_generation_successful_confirm));
            });

        } catch (IOException e) {
            // Ghi log lỗi và hiển thị thông báo lỗi trên UI thread.
            Log.e(TAG, "Lỗi khi hiển thị PDF: " + e.getMessage(), e);
            mainHandler.post(() -> {
                Toast.makeText(this, getString(R.string.error_loading_pdf_preview), Toast.LENGTH_LONG).show();
                if (tvPdfPreviewStatus != null) tvPdfPreviewStatus.setText(getString(R.string.error_cannot_preview_pdf));
            });
        } finally {
            // Đảm bảo đóng tất cả các tài nguyên để tránh rò rỉ bộ nhớ.
            if (currentPage != null) {
                currentPage.close();
            }
            if (renderer != null) {
                renderer.close();
            }
            if (parcelFileDescriptor != null) {
                try {
                    parcelFileDescriptor.close();
                } catch (IOException e) {
                    Log.e(TAG, "Lỗi khi đóng ParcelFileDescriptor: " + e.getMessage(), e);
                }
            }
        }
    }

    /**
     * Xóa file PDF đã lưu khỏi bộ nhớ thiết bị.
     * Phương thức này thực hiện xóa trên luồng nền để tránh chặn luồng UI.
     *
     * @param pdfUri URI của file PDF cần xóa.
     */
    private void deletePdfDocument(Uri pdfUri) {
        if (pdfUri != null) {
            executorService.execute(() -> {
                try {
                    ContentResolver resolver = getContentResolver();
                    // Xóa file bằng ContentResolver.
                    int rowsDeleted = resolver.delete(pdfUri, null, null);
                    if (rowsDeleted > 0) {
                        Log.d(TAG, "Đã xóa PDF thành công: " + pdfUri.toString());
                        mainHandler.post(() -> Toast.makeText(this, getString(R.string.pdf_deleted_successfully), Toast.LENGTH_SHORT).show());
                    } else {
                        Log.e(TAG, "Không thể xóa PDF: " + pdfUri.toString());
                        mainHandler.post(() -> Toast.makeText(this, getString(R.string.error_cannot_delete_pdf), Toast.LENGTH_SHORT).show());
                    }
                } catch (SecurityException e) {
                    // Xử lý lỗi quyền truy cập khi xóa.
                    Log.e(TAG, "Không có quyền xóa PDF: " + e.getMessage(), e);
                    mainHandler.post(() -> Toast.makeText(this, String.format(getString(R.string.error_no_permission_to_delete_pdf), e.getMessage()), Toast.LENGTH_LONG).show());
                } catch (Exception e) {
                    // Xử lý các lỗi khác khi xóa.
                    Log.e(TAG, "Lỗi khi xóa PDF: " + e.getMessage(), e);
                    mainHandler.post(() -> Toast.makeText(this, String.format(getString(R.string.error_deleting_pdf), e.getMessage()), Toast.LENGTH_LONG).show());
                }
            });
        }
    }

    /**
     * Phương thức được gọi khi Activity bị hủy.
     * Đây là nơi quan trọng để giải phóng các tài nguyên không còn cần thiết
     * như Bitmap và ExecutorService để tránh rò rỉ bộ nhớ.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Giải phóng bộ nhớ của Bitmap nếu chúng không rỗng.
        if (croppedBitmap != null) {
            croppedBitmap.recycle(); // Giải phóng bộ nhớ liên quan đến Bitmap.
            croppedBitmap = null;
        }
        if (processedBitmap != null) {
            processedBitmap.recycle();
            processedBitmap = null;
        }
        // Tắt ExecutorService nếu nó đang chạy để dừng tất cả các luồng và giải phóng tài nguyên.
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow();
        }
    }
}