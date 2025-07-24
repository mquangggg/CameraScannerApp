package com.example.camerascanner.activitypdf;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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

import com.example.camerascanner.R;
import com.example.camerascanner.activitymain.MainActivity;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Activity được refactor để sử dụng các lớp helper
 */
public class PdfGenerationAndPreviewActivity extends AppCompatActivity {
    private static final String TAG = "PdfGenAndPreview";

    // UI Components
    private ImageView ivPdfPreview;
    private Button btnSavePdf;
    private Button btnDeletePdf;
    private Button btnRegeneratePdf;
    private TextView tvPdfPreviewStatus;
    private RadioGroup rgPdfStyle;
    private RadioButton rbOriginal;
    private RadioButton rbBlackWhite;

    // Data
    private Uri imageUriToConvert;
    private Bitmap croppedBitmap;
    private Bitmap processedBitmap;
    private Bitmap blackWhiteBitmap; // Cache bitmap trắng đen để tránh tạo lại
    private Uri finalPdfUri;
    private PdfStyle currentPdfStyle = PdfStyle.ORIGINAL;

    // Helper classes
    private PdfGenerator pdfGenerator;
    private PdfPreviewHelper pdfPreviewHelper;
    private PdfFileManager pdfFileManager;

    // Background processing
    private ExecutorService executorService;
    private Handler mainHandler;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pdf_generation_and_preview);

        initializeComponents();
        initializeHelpers();
        setupUI();
        processIntentData();
    }

    /**
     * Khởi tạo các thành phần giao diện UI.
     */
    private void initializeComponents() {
        ivPdfPreview = findViewById(R.id.ivPdfPreview);
        btnSavePdf = findViewById(R.id.btnSavePdf);
        btnDeletePdf = findViewById(R.id.btnDeletePdf);
        btnRegeneratePdf = findViewById(R.id.btnRegeneratePdf);
        tvPdfPreviewStatus = findViewById(R.id.tvPdfPreviewStatus);
        rgPdfStyle = findViewById(R.id.rgPdfStyle);
        rbOriginal = findViewById(R.id.rbOriginal);
        rbBlackWhite = findViewById(R.id.rbBlackWhite);
        // Luồng đơn
        executorService = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * Khởi tạo các helper cho xử lý PDF, preview, file.
     */
    private void initializeHelpers() {
        pdfGenerator = new PdfGenerator(this);
        pdfPreviewHelper = new PdfPreviewHelper(this);
        pdfFileManager = new PdfFileManager(this);
    }

    /**
     * Thiết lập UI, listener cho radio group và các nút.
     */
    private void setupUI() {
        rbOriginal.setChecked(true);
        currentPdfStyle = PdfStyle.ORIGINAL;

        setupRadioGroupListener();
        setupButtonListeners();
    }

    /**
     * Thiết lập listener cho RadioGroup chọn kiểu PDF.
     */
    private void setupRadioGroupListener() {
        rgPdfStyle.setOnCheckedChangeListener((group, checkedId) -> {
            if (croppedBitmap == null) {
                Toast.makeText(this, "Không có ảnh để áp dụng kiểu.", Toast.LENGTH_SHORT).show();
                return;
            }

            if (checkedId == R.id.rbOriginal) {
                // Chỉ thay đổi nếu đang không ở chế độ Original
                if (currentPdfStyle != PdfStyle.ORIGINAL) {
                    processedBitmap = croppedBitmap;
                    currentPdfStyle = PdfStyle.ORIGINAL;
                    updatePreview();
                    Toast.makeText(this, "Chế độ Gốc được chọn", Toast.LENGTH_SHORT).show();
                }
            } else if (checkedId == R.id.rbBlackWhite) {
                // Chỉ thay đổi nếu đang không ở chế độ Black & White
                if (currentPdfStyle != PdfStyle.BLACK_WHITE) {
                    // Tạo bitmap trắng đen chỉ khi chưa có hoặc cần tạo lại
                    if (blackWhiteBitmap == null) {
                        createBlackWhiteBitmapAsync();
                    } else {
                        processedBitmap = blackWhiteBitmap;
                        currentPdfStyle = PdfStyle.BLACK_WHITE;
                        updatePreview();
                        Toast.makeText(this, "Chế độ Trắng đen được chọn", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });
    }

    /**
     * Tạo bitmap trắng đen bất đồng bộ và cache lại
     */
    // Trong PdfGenerationAndPreviewActivity
    private void createBlackWhiteBitmapAsync() {
        updateStatus("Đang xử lý ảnh trắng đen...");

        executorService.execute(() -> {
            try {
                // Chọn phương pháp phù hợp
                blackWhiteBitmap = ImageProcessor.convertToBlackAndWhite(
                        croppedBitmap,
                        ImageProcessor.ConversionMethod.ADAPTIVE // hoặc ADAPTIVE cho tài liệu
                );

                // Hoặc sử dụng method chuyên biệt
                // blackWhiteBitmap = ImageProcessor.convertDocumentToBlackAndWhite(croppedBitmap);

                mainHandler.post(() -> {
                    if (blackWhiteBitmap != null) {
                        processedBitmap = blackWhiteBitmap;
                        currentPdfStyle = PdfStyle.BLACK_WHITE;
                        updatePreview();
                        updateStatus("Ảnh trắng đen đã sẵn sàng.");
                        Toast.makeText(this, "Chế độ Trắng đen được chọn", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Lỗi khi tạo ảnh trắng đen", Toast.LENGTH_SHORT).show();
                        rbOriginal.setChecked(true);
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Lỗi khi tạo bitmap trắng đen: " + e.getMessage(), e);
                mainHandler.post(() -> {
                    Toast.makeText(this, "Lỗi khi xử lý ảnh trắng đen", Toast.LENGTH_SHORT).show();
                    rbOriginal.setChecked(true);
                });
            }
        });
    }

    /**
     * Thiết lập listener cho các nút Save, Delete, Regenerate.
     */
    private void setupButtonListeners() {
        btnSavePdf.setOnClickListener(v -> handleSavePdf());
        btnDeletePdf.setOnClickListener(v -> handleDeletePdf());
        btnRegeneratePdf.setOnClickListener(v -> handleRegeneratePdf());
    }

    /**
     * Xử lý dữ liệu Intent truyền vào (ảnh cần chuyển PDF).
     */
    private void processIntentData() {
        Bundle extras = getIntent().getExtras();
        if (extras != null && extras.containsKey("croppedUri")) {
            imageUriToConvert = getIntent().getParcelableExtra("croppedUri");
            if (imageUriToConvert != null) {
                updateStatus(getString(R.string.status_loading_and_processing_image));
                loadAndProcessImageAsync();
            } else {
                handleError(getString(R.string.error_invalid_image_uri));
            }
        } else {
            handleError(getString(R.string.error_no_image_to_process));
        }
    }

    /**
     * Load và xử lý ảnh bất đồng bộ.
     */
    private void loadAndProcessImageAsync() {
        executorService.execute(() -> {
            try {
                croppedBitmap = ImageLoader.loadBitmapFromUri(this, imageUriToConvert);

                mainHandler.post(() -> {
                    if (croppedBitmap != null) {
                        // Khởi tạo processedBitmap với bitmap gốc
                        processedBitmap = croppedBitmap;

                        updatePreview();
                        updateStatus("Ảnh đã sẵn sàng để tạo PDF.");
                    } else {
                        handleError("Không thể tải ảnh: Bitmap rỗng.");
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Lỗi khi tải ảnh: " + e.getMessage(), e);
                mainHandler.post(() -> handleError("Lỗi khi tải ảnh: " + e.getMessage()));
            }
        });
    }

    /**
     * Cập nhật bản xem trước PDF trên UI.
     */
    private void updatePreview() {
        if (processedBitmap != null) {
            ivPdfPreview.setImageBitmap(processedBitmap);
            updateStatus("Đã cập nhật bản xem trước.");
        }
    }

    /**
     * Cập nhật trạng thái hiển thị trên UI.
     */
    private void updateStatus(String message) {
        if (tvPdfPreviewStatus != null) {
            tvPdfPreviewStatus.setText(message);
        }
    }

    /**
     * Xử lý lỗi, hiển thị thông báo và kết thúc Activity nếu cần.
     */
    private void handleError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        updateStatus(message);
        finish();
    }

    /**
     * Xử lý sự kiện lưu PDF khi nhấn nút Save.
     */
    private void handleSavePdf() {
        if (processedBitmap != null) {
            DialogHelper.showFileNameDialog(this, this::savePdfWithFileName);
        } else {
            Toast.makeText(this, "Không có ảnh để lưu PDF.", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Lưu PDF với tên file do người dùng nhập.
     */
    private void savePdfWithFileName(String fileName) {
        if (PermissionHelper.hasStoragePermission(this)) {
            performSavePdf(fileName);
        } else {
            PermissionHelper.requestStoragePermission(this);
        }
    }

    /**
     * Thực hiện lưu PDF ở background.
     */
    private void performSavePdf(String fileName) {
        updateStatus(getString(R.string.status_generating_pdf));

        executorService.execute(() -> {
            try {
                finalPdfUri = pdfGenerator.createPdf(processedBitmap, fileName);

                mainHandler.post(() -> {
                    Toast.makeText(this, "PDF đã được tạo thành công: " + fileName, Toast.LENGTH_LONG).show();
                    updateStatus("PDF đã được lưu: " + fileName);
                    navigateToMainActivity();
                });

            } catch (Exception e) {
                Log.e(TAG, "Lỗi khi tạo PDF: " + e.getMessage(), e);
                mainHandler.post(() -> {
                    Toast.makeText(this, "Lỗi khi tạo PDF: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    updateStatus("Lỗi khi tạo PDF: " + e.getMessage());
                });
            }
        });
    }

    /**
     * Xử lý sự kiện xóa PDF khi nhấn nút Delete.
     */
    private void handleDeletePdf() {
        if (finalPdfUri != null) {
            executorService.execute(() -> {
                boolean success = pdfFileManager.deletePdfFile(finalPdfUri);

                mainHandler.post(() -> {
                    if (success) {
                        Toast.makeText(this, "PDF đã được xóa thành công", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Không thể xóa PDF", Toast.LENGTH_SHORT).show();
                    }
                    finish();
                });
            });
        } else {
            Toast.makeText(this, "Không có PDF để xóa", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    /**
     * Xử lý sự kiện tạo lại bản xem trước PDF khi nhấn nút Regenerate.
     */
    private void handleRegeneratePdf() {
        if (croppedBitmap == null) {
            Toast.makeText(this, "Không có ảnh để tạo lại bản xem trước.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Reset cache bitmap trắng đen để tạo lại
        if (blackWhiteBitmap != null) {
            blackWhiteBitmap.recycle();
            blackWhiteBitmap = null;
        }

        // Tạo lại bitmap dựa trên chế độ hiện tại
        if (currentPdfStyle == PdfStyle.ORIGINAL) {
            processedBitmap = croppedBitmap;
            updatePreview();
        } else {
            createBlackWhiteBitmapAsync();
        }
    }

    /**
     * Chuyển về MainActivity sau khi lưu PDF thành công.
     */
    private void navigateToMainActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    /**
     * Xử lý kết quả xin quyền truy cập bộ nhớ.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PermissionHelper.PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Quyền đã được cấp", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Cần quyền để lưu PDF", Toast.LENGTH_LONG).show();
                updateStatus("Lỗi: Không có quyền lưu PDF");
            }
        }
    }

    /**
     * Giải phóng tài nguyên khi Activity bị hủy.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Giải phóng bitmap
        if (croppedBitmap != null) {
            croppedBitmap.recycle();
            croppedBitmap = null;
        }
        if (processedBitmap != null) {
            processedBitmap.recycle();
            processedBitmap = null;
        }
        if (blackWhiteBitmap != null) {
            blackWhiteBitmap.recycle();
            blackWhiteBitmap = null;
        }

        // Tắt executor service
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow();
        }
    }
}