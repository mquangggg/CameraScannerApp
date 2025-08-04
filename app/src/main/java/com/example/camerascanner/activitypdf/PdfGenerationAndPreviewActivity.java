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
import androidx.core.content.FileProvider;

import com.example.camerascanner.R;
import com.example.camerascanner.activitycamera.ImagePreviewActivity;
import com.example.camerascanner.activitymain.MainActivity;
import com.example.camerascanner.activitypdf.Jpeg.JpegGenerator;
import com.example.camerascanner.activitypdf.pdf.PdfFileManager;
import com.example.camerascanner.activitypdf.pdf.PdfGenerator;
import com.example.camerascanner.activitypdf.pdf.PdfStyle;
import com.example.camerascanner.activitypdf.pdfgroup.PDFGroupActivity;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
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
    private Button btnSaveJPEG;
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
    private PdfFileManager pdfFileManager;
    private JpegGenerator jpegGenerator;

    // Background processing
    private ExecutorService executorService;
    private Handler mainHandler;
    private boolean isFromPdfGroup = false;
    private int originalRequestCode = -1;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pdf_generation_and_preview);
        Intent intent = getIntent();
        if (intent != null) {
            isFromPdfGroup = intent.getBooleanExtra("FROM_PDF_GROUP", false);
            originalRequestCode = intent.getIntExtra("ORIGINAL_REQUEST_CODE", -1);
            Log.d(TAG, "PdfGeneratorAndPreviewActivity: isFromPdfGroup=" + isFromPdfGroup + ", requestCode=" + originalRequestCode);
        }

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
        btnSaveJPEG = findViewById(R.id.btnSaveJpeg);
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
        pdfFileManager = new PdfFileManager(this);
        jpegGenerator = new JpegGenerator(this);
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
        btnSaveJPEG.setOnClickListener(v -> handleSaveJpeg());
    }

    /**
     * Xử lý dữ liệu Intent truyền vào (ảnh cần chuyển PDF).
     */
    private void processIntentData() {
        Bundle extras = getIntent().getExtras();

        // Kiểm tra cả hai tham số có thể có
        Uri imageUri = null;

        if (extras != null) {
            // Trường hợp từ ImagePreviewActivity (croppedUri)
            if (extras.containsKey("croppedUri")) {
                String uriString = getIntent().getStringExtra("croppedUri"); // <-- Lấy dưới dạng String
                if (uriString != null) {
                    imageUri = Uri.parse(uriString); // <-- Phân tích cú pháp thành Uri
                    Log.d(TAG, "Received croppedUri: " + imageUri);
                } else {
                    Log.e(TAG, "croppedUri string is null in intent extras.");
                }
            }
            // Trường hợp khác có thể sử dụng tham số khác
            else if (extras.containsKey("processedImageUri")) {
                String uriString = extras.getString("processedImageUri");
                if (uriString != null) {
                    imageUri = Uri.parse(uriString);
                    Log.d(TAG, "Received processedImageUri: " + imageUri);
                }
            }
        }

        if (imageUri != null) {
            imageUriToConvert = imageUri;
            updateStatus(getString(R.string.status_loading_and_processing_image));
            loadAndProcessImageAsync();
        } else {
            Log.e(TAG, "No valid image URI found in intent extras");
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
        if (processedBitmap == null) {
            Toast.makeText(this, "Không có ảnh để xử lý.", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            // Bước 1: Luôn lưu processedBitmap vào thư mục cache tạm thời
            // Tên file độc nhất để tránh trùng lặp
            File cachePath = new File(getCacheDir(), "processed_images_temp");
            cachePath.mkdirs(); // Tạo thư mục nếu nó chưa tồn tại
            File tempImageFile = new File(cachePath, "temp_processed_image_" + System.currentTimeMillis() + ".png");

            FileOutputStream fos = new FileOutputStream(tempImageFile);
            processedBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos); // Nén và lưu Bitmap
            fos.close();

            // Bước 2: Lấy Uri từ file tạm thời bằng FileProvider
            // Đảm bảo FileProvider đã được cấu hình đúng trong AndroidManifest.xml và res/xml/file_paths.xml
            Uri tempImageUri = FileProvider.getUriForFile(this, getPackageName() + ".provider", tempImageFile);

            // Bước 3: Quyết định cách chuyển ảnh đến PDFGroupActivity dựa trên isFromPdfGroup
            if (isFromPdfGroup) {
                // LUỒNG: THÊM ẢNH VÀO NHÓM PDF (được gọi từ chuỗi startActivityForResult)
                // Mục tiêu: Trả lại URI của ảnh đã xử lý về cho Activity đã gọi
                Log.d(TAG, "Returning result to previous Activity (PDF Group) with Uri: " + tempImageUri.toString());

                Intent resultIntent = new Intent();
                resultIntent.putExtra("processedImageUri", tempImageUri.toString());
                resultIntent.putExtra("pdfStyle", currentPdfStyle.name());
                if (imageUriToConvert != null) {
                    resultIntent.putExtra("originalImageUri", imageUriToConvert.toString());
                }
                resultIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); // Cấp quyền đọc tạm thời

                setResult(RESULT_OK, resultIntent); // Đặt kết quả là OK
                finish(); // Đóng PdfGenerationAndPreviewActivity

            } else {
                // Mục tiêu: Trực tiếp mở PDFGroupActivity và truyền ảnh sang đó
                Log.d(TAG, "Starting PDFGroupActivity for single image with Uri: " + tempImageUri.toString());

                Intent intentToPdfGroup = new Intent(this, PDFGroupActivity.class);
                intentToPdfGroup.putExtra("processedImageUri", tempImageUri.toString()); // Chuyển Uri thành String
                intentToPdfGroup.putExtra("pdfStyle", currentPdfStyle.name());
                // Cần truyền một cờ để PDFGroupActivity biết đây là ảnh đơn lẻ
                intentToPdfGroup.putExtra("IS_SINGLE_IMAGE_FLOW", true);
                if (imageUriToConvert != null) {
                    intentToPdfGroup.putExtra("originalImageUri", imageUriToConvert.toString());
                }
                intentToPdfGroup.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); // Rất quan trọng: Cấp quyền đọc tạm thời

                startActivity(intentToPdfGroup);
                finish(); // Đóng PdfGenerationAndPreviewActivity
            }
        } catch (IOException e) {
            Log.e(TAG, "Lỗi I/O khi lưu ảnh tạm thời: " + e.getMessage(), e);
            Toast.makeText(this, "Lỗi khi lưu ảnh tạm thời: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            setResult(RESULT_CANCELED); // Báo lỗi nếu lưu ảnh tạm thất bại
            finish();
        } catch (Exception e) {
            Log.e(TAG, "Lỗi khi xử lý hoặc chuyển tiếp ảnh: " + e.getMessage(), e);
            Toast.makeText(this, "Lỗi xử lý ảnh: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            setResult(RESULT_CANCELED); // Báo lỗi nếu có lỗi khác
            finish();
        }
    }

    /**
     * Xử lý sự kiện lưu JPEG khi nhấn nút Save JPEG.
     */
    private void handleSaveJpeg() {
        if (processedBitmap != null) {
            DialogHelper.showJpegFileNameDialog(this, this::saveJpegWithFileName);
        } else {
            Toast.makeText(this, "Không có ảnh để lưu JPEG.", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Lưu JPEG với tên file do người dùng nhập.
     */
    private void saveJpegWithFileName(String fileName) {
        if (PermissionHelper.hasStoragePermission(this)) {
            performSaveJpeg(fileName);
        } else {
            PermissionHelper.requestStoragePermission(this);
        }
    }

    /**
     * Thực hiện lưu JPEG ở background.
     */
    private void performSaveJpeg(String fileName) {
        updateStatus("Đang lưu ảnh JPEG...");

        executorService.execute(() -> {
            try {
                Uri jpegUri = jpegGenerator.saveAsJpeg(processedBitmap, fileName);

                mainHandler.post(() -> {
                    Toast.makeText(this, "Ảnh JPEG đã được lưu thành công: " + fileName, Toast.LENGTH_LONG).show();
                    updateStatus("Ảnh JPEG đã được lưu: " + fileName);
                    navigateToMainActivity();
                });

            } catch (Exception e) {
                Log.e(TAG, "Lỗi khi lưu JPEG: " + e.getMessage(), e);
                mainHandler.post(() -> {
                    Toast.makeText(this, "Lỗi khi lưu JPEG: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    updateStatus("Lỗi khi lưu JPEG: " + e.getMessage());
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
                Toast.makeText(this, "Cần quyền để lưu file", Toast.LENGTH_LONG).show();
                updateStatus("Lỗi: Không có quyền lưu file");
            }
        }
    }
    @Override
    public void onBackPressed() {
        // Tạo Intent để quay lại Activity trước đó
        super.onBackPressed();
        Intent intent = new Intent(this, ImagePreviewActivity.class);
        startActivity(intent);
        finish(); // Đóng Activity hiện tại
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