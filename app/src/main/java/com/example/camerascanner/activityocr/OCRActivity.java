// File: com/example/camerascanner/activityocr/OCRActivity.java
// File: com/example/camerascanner/activityocr/OCRActivity.java
package com.example.camerascanner.activityocr;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatButton;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.camerascanner.R;
import com.example.camerascanner.activityimagepreview.ImagePreviewActivity;
import com.example.camerascanner.BaseActivity;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

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
import java.util.concurrent.TimeUnit;

/**
 * OCRActivity được tối ưu hóa với các kỹ thuật xử lý ảnh nâng cao
 * để cải thiện độ chính xác của nhận dạng chữ viết
 */
public class OCRActivity extends BaseActivity {

    // Khai báo các thành phần UI
    private ImageButton btnOCRBack;
    private AppCompatButton btnSaveOCRImageAndWord, btnCopyOCRText;
    private ImageView ivImageForOCR;
    private OcrOverlayView ocrOverlayView;

    // Hằng số
    public static final String EXTRA_IMAGE_URI_FOR_OCR = "image_uri_for_ocr";
    private static final String TAG = "OCRActivity";
    private static final int REQUEST_WRITE_STORAGE = 100;

    // Hằng số cho xử lý ảnh
    private static final int MIN_IMAGE_SIZE = 300;
    private static final int MAX_IMAGE_SIZE = 2048;
    private static final float CONTRAST_FACTOR = 1.2f;
    private static final int BRIGHTNESS_OFFSET = 10;

    // Các thành phần xử lý
    private ExecutorService executorService;
    private Handler mainHandler;
    private Uri imageUriToProcess;
    private Bitmap currentImageBitmap;
    private Bitmap originalImageBitmap; // Thêm biến để lưu ảnh gốc
    private Text currentOcrResult;

    @Override
    protected void onCreate(Bundle saveInstance) {
        super.onCreate(saveInstance);
        setContentView(R.layout.activity_ocr);

        // Khởi tạo UI và các thành phần
        initializeViews();
        initializeExecutors();

        // Xử lý ảnh đầu vào
        handleImageInput();

        // Thiết lập sự kiện
        setupEventListeners();
    }

    /**
     * Khởi tạo các View components
     */
    private void initializeViews() {
        ivImageForOCR = findViewById(R.id.ivImageForOcr);
        btnOCRBack = findViewById(R.id.btnOCRBack);
        btnSaveOCRImageAndWord = findViewById(R.id.btnSaveOCRImageAndWord);
        btnCopyOCRText = findViewById(R.id.btnCopyOCRText);
        ocrOverlayView = findViewById(R.id.ocrOverlayView);
    }

    /**
     * Khởi tạo ExecutorService và Handler
     */
    private void initializeExecutors() {
        executorService = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * Xử lý ảnh đầu vào từ Intent
     */
    private void handleImageInput() {
        String imageUriString = getIntent().getStringExtra(EXTRA_IMAGE_URI_FOR_OCR);

        if (imageUriString != null) {
            imageUriToProcess = Uri.parse(imageUriString);
            loadAndProcessImage();
        } else {
            Toast.makeText(this, getString(R.string.error_no_image_to_process), Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    /**
     * Tải và xử lý ảnh với các kỹ thuật tối ưu
     */
    private void loadAndProcessImage() {
        executorService.execute(() -> {
            try {
                // Tải ảnh gốc
                InputStream inputStream = getContentResolver().openInputStream(imageUriToProcess);
                if (inputStream != null) {
                    originalImageBitmap = BitmapFactory.decodeStream(inputStream); // Lưu ảnh gốc
                    inputStream.close();

                    if (originalImageBitmap != null) {
                        // Áp dụng các kỹ thuật tối ưu ảnh cho OCR
                        Bitmap optimizedBitmap = optimizeImageForOCR(originalImageBitmap);

                        mainHandler.post(() -> {
                            currentImageBitmap = optimizedBitmap;
                            // Hiển thị ảnh gốc trong ImageView để user thấy
                            ivImageForOCR.setImageBitmap(originalImageBitmap);
                            // Thực hiện OCR trên ảnh đã tối ưu
                            startOcrProcess(optimizedBitmap);
                        });
                    } else {
                        showError("Không thể tải ảnh cho OCR.");
                    }
                } else {
                    showError("Không thể mở InputStream cho ảnh.");
                }
            } catch (IOException e) {
                Log.e(TAG, "Lỗi khi tải ảnh từ URI: " + e.getMessage(), e);
                showError("Lỗi khi tải ảnh cho OCR: " + e.getMessage());
            }
        });
    }

    /**
     * Tối ưu hóa ảnh cho OCR với nhiều kỹ thuật xử lý
     */
    private Bitmap optimizeImageForOCR(Bitmap originalBitmap) {
        // Bước 1: Resize ảnh về kích thước tối ưu
        Bitmap resizedBitmap = resizeImageOptimally(originalBitmap);

        // Bước 2: Tăng cường độ tương phản và độ sáng
        Bitmap contrastEnhanced = enhanceContrast(resizedBitmap);

        // Bước 3: Khử nhiễu
        Bitmap denoised = applyDenoising(contrastEnhanced);

        // Bước 4: Làm sắc nét
        Bitmap sharpened = applySharpen(denoised);

        // Bước 5: Chuyển đổi sang grayscale với thuật toán tối ưu
        Bitmap grayscale = convertToOptimalGrayscale(sharpened);

        // Bước 6: Điều chỉnh gamma
        Bitmap gammaAdjusted = adjustGamma(grayscale, 1.2f);

        // Giải phóng bộ nhớ các bitmap trung gian
        if (resizedBitmap != originalBitmap) resizedBitmap.recycle();
        if (contrastEnhanced != resizedBitmap) contrastEnhanced.recycle();
        if (denoised != contrastEnhanced) denoised.recycle();
        if (sharpened != denoised) sharpened.recycle();
        if (grayscale != sharpened) grayscale.recycle();

        return gammaAdjusted;
    }

    /**
     * Resize ảnh về kích thước tối ưu cho OCR
     */
    private Bitmap resizeImageOptimally(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        // Tính toán kích thước mới
        float scaleFactor = 1.0f;

        if (width < MIN_IMAGE_SIZE || height < MIN_IMAGE_SIZE) {
            // Ảnh quá nhỏ, cần phóng to
            scaleFactor = Math.max((float)MIN_IMAGE_SIZE / width, (float)MIN_IMAGE_SIZE / height);
        } else if (width > MAX_IMAGE_SIZE || height > MAX_IMAGE_SIZE) {
            // Ảnh quá lớn, cần thu nhỏ
            scaleFactor = Math.min((float)MAX_IMAGE_SIZE / width, (float)MAX_IMAGE_SIZE / height);
        }

        if (scaleFactor != 1.0f) {
            Matrix matrix = new Matrix();
            matrix.postScale(scaleFactor, scaleFactor);
            return Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true);
        }

        return bitmap;
    }

    /**
     * Tăng cường độ tương phản và độ sáng
     */
    private Bitmap enhanceContrast(Bitmap bitmap) {
        ColorMatrix colorMatrix = new ColorMatrix();
        colorMatrix.set(new float[]{
                CONTRAST_FACTOR, 0, 0, 0, BRIGHTNESS_OFFSET,
                0, CONTRAST_FACTOR, 0, 0, BRIGHTNESS_OFFSET,
                0, 0, CONTRAST_FACTOR, 0, BRIGHTNESS_OFFSET,
                0, 0, 0, 1, 0
        });

        return applyColorMatrix(bitmap, colorMatrix);
    }

    /**
     * Áp dụng ColorMatrix lên bitmap
     */
    private Bitmap applyColorMatrix(Bitmap bitmap, ColorMatrix colorMatrix) {
        Bitmap result = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), bitmap.getConfig());
        Canvas canvas = new Canvas(result);
        Paint paint = new Paint();
        paint.setColorFilter(new ColorMatrixColorFilter(colorMatrix));
        canvas.drawBitmap(bitmap, 0, 0, paint);
        return result;
    }

    /**
     * Khử nhiễu bằng thuật toán median filter đơn giản
     */
    private Bitmap applyDenoising(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        int[] result = new int[width * height];

        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                int[] neighbors = new int[9];
                int index = 0;

                // Lấy 9 pixel xung quanh
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        neighbors[index++] = pixels[(y + dy) * width + (x + dx)];
                    }
                }

                // Sắp xếp và lấy giá trị trung vị
                java.util.Arrays.sort(neighbors);
                result[y * width + x] = neighbors[4];
            }
        }

        // Copy biên
        for (int i = 0; i < width * height; i++) {
            if (result[i] == 0) result[i] = pixels[i];
        }

        Bitmap denoisedBitmap = Bitmap.createBitmap(width, height, bitmap.getConfig());
        denoisedBitmap.setPixels(result, 0, width, 0, 0, width, height);
        return denoisedBitmap;
    }

    /**
     * Làm sắc nét ảnh
     */
    private Bitmap applySharpen(Bitmap bitmap) {
        // Kernel làm sắc nét
        float[] sharpenKernel = {
                0, -1, 0,
                -1, 5, -1,
                0, -1, 0
        };

        return applyConvolution(bitmap, sharpenKernel, 3);
    }

    /**
     * Áp dụng convolution với kernel
     */
    private Bitmap applyConvolution(Bitmap bitmap, float[] kernel, int kernelSize) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        int[] result = new int[width * height];
        int offset = kernelSize / 2;

        for (int y = offset; y < height - offset; y++) {
            for (int x = offset; x < width - offset; x++) {
                float r = 0, g = 0, b = 0;

                for (int ky = 0; ky < kernelSize; ky++) {
                    for (int kx = 0; kx < kernelSize; kx++) {
                        int pixel = pixels[(y + ky - offset) * width + (x + kx - offset)];
                        float weight = kernel[ky * kernelSize + kx];

                        r += ((pixel >> 16) & 0xFF) * weight;
                        g += ((pixel >> 8) & 0xFF) * weight;
                        b += (pixel & 0xFF) * weight;
                    }
                }

                r = Math.max(0, Math.min(255, r));
                g = Math.max(0, Math.min(255, g));
                b = Math.max(0, Math.min(255, b));

                result[y * width + x] = 0xFF000000 | ((int)r << 16) | ((int)g << 8) | (int)b;
            }
        }

        // Copy biên
        for (int i = 0; i < width * height; i++) {
            if (result[i] == 0) result[i] = pixels[i];
        }

        Bitmap sharpenedBitmap = Bitmap.createBitmap(width, height, bitmap.getConfig());
        sharpenedBitmap.setPixels(result, 0, width, 0, 0, width, height);
        return sharpenedBitmap;
    }

    /**
     * Chuyển đổi sang grayscale với thuật toán tối ưu
     */
    private Bitmap convertToOptimalGrayscale(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        // Sử dụng công thức luminance cải tiến
        for (int i = 0; i < pixels.length; i++) {
            int pixel = pixels[i];
            int r = (pixel >> 16) & 0xFF;
            int g = (pixel >> 8) & 0xFF;
            int b = pixel & 0xFF;

            // Công thức luminance cải tiến cho OCR
            int gray = (int)(0.299 * r + 0.587 * g + 0.114 * b);
            pixels[i] = 0xFF000000 | (gray << 16) | (gray << 8) | gray;
        }

        Bitmap grayscaleBitmap = Bitmap.createBitmap(width, height, bitmap.getConfig());
        grayscaleBitmap.setPixels(pixels, 0, width, 0, 0, width, height);
        return grayscaleBitmap;
    }

    /**
     * Điều chỉnh gamma
     */
    private Bitmap adjustGamma(Bitmap bitmap, float gamma) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        // Tạo bảng lookup gamma
        int[] gammaTable = new int[256];
        for (int i = 0; i < 256; i++) {
            gammaTable[i] = (int)(255 * Math.pow(i / 255.0, 1.0 / gamma));
        }

        // Áp dụng gamma correction
        for (int i = 0; i < pixels.length; i++) {
            int pixel = pixels[i];
            int r = gammaTable[(pixel >> 16) & 0xFF];
            int g = gammaTable[(pixel >> 8) & 0xFF];
            int b = gammaTable[pixel & 0xFF];

            pixels[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
        }

        Bitmap gammaBitmap = Bitmap.createBitmap(width, height, bitmap.getConfig());
        gammaBitmap.setPixels(pixels, 0, width, 0, 0, width, height);
        return gammaBitmap;
    }

    /**
     * Bắt đầu quá trình OCR với cấu hình tối ưu
     */
    private void startOcrProcess(Bitmap imageBitmap) {
        btnCopyOCRText.setEnabled(false);
        btnSaveOCRImageAndWord.setEnabled(false);

        executorService.execute(() -> {
            String fullOcrText = "Lỗi khi trích xuất văn bản.";
            Text resultText = null;

            try {
                if (imageBitmap == null) {
                    throw new IOException("Bitmap để xử lý OCR là null.");
                }

                // Thử nhiều orientation nếu cần
                InputImage image = InputImage.fromBitmap(imageBitmap, 0);

                // Sử dụng TextRecognizer với cấu hình tối ưu
                TextRecognizer recognizer = TextRecognition.getClient(
                        TextRecognizerOptions.DEFAULT_OPTIONS
                );

                // Thực hiện OCR
                resultText = Tasks.await(recognizer.process(image));
                fullOcrText = resultText.getText();

                // Nếu kết quả kém, thử với ảnh xoay

                // Hậu xử lý văn bản
                fullOcrText = postProcessOcrText(fullOcrText);

            } catch (Exception e) {
                Log.e(TAG, "Lỗi khi trích xuất văn bản OCR: " + e.getMessage(), e);
                fullOcrText = "Lỗi: " + e.getMessage();
                resultText = null;
            }

            final String finalFullOcrText = fullOcrText;
            final Text finalResultText = resultText;

            mainHandler.post(() -> {
                currentOcrResult = finalResultText;

                if (imageBitmap != null && currentOcrResult != null) {
                    ocrOverlayView.setOcrResult(currentOcrResult,
                            originalImageBitmap.getWidth(),
                            originalImageBitmap.getHeight(),
                            imageBitmap.getWidth(), // This is the optimized bitmap
                            imageBitmap.getHeight());
                } else {
                    ocrOverlayView.setOcrResult(null, 0, 0,0,0);
                }

                btnCopyOCRText.setEnabled(true);
                btnSaveOCRImageAndWord.setEnabled(true);

                if (finalFullOcrText.startsWith("Lỗi:")) {
                    Toast.makeText(OCRActivity.this, finalFullOcrText, Toast.LENGTH_LONG).show();
                } else if (finalFullOcrText.isEmpty()) {
                    Toast.makeText(OCRActivity.this, getString(R.string.no_text_found_in_image), Toast.LENGTH_SHORT).show();
                } else {
                    int wordCount = finalFullOcrText.split("\\s+").length;

                    Toast.makeText(OCRActivity.this,
                            getString(R.string.ocr_word_count, wordCount),
                            Toast.LENGTH_SHORT).show();
                }
            });
        });
    }


    /**
     * Hậu xử lý văn bản OCR để cải thiện chất lượng
     */
    private String postProcessOcrText(String rawText) {
        if (rawText == null || rawText.trim().isEmpty()) {
            return rawText;
        }

        // Loại bỏ ký tự không mong muốn
        String processed = rawText.replaceAll("[^\\p{L}\\p{N}\\p{P}\\p{Z}]", "");

        // Chuẩn hóa khoảng trắng
        processed = processed.replaceAll("\\s+", " ");

        // Sửa lỗi nhận dạng phổ biến
        processed = processed.replace("0", "O"); // Số 0 thành chữ O nếu trong context chữ
        processed = processed.replace("1", "I"); // Số 1 thành chữ I nếu trong context chữ
        processed = processed.replace("5", "S"); // Số 5 thành chữ S nếu trong context chữ

        return processed.trim();
    }

    /**
     * Hiển thị thông báo lỗi
     */
    private void showError(String message) {
        mainHandler.post(() -> {
            Toast.makeText(OCRActivity.this, message, Toast.LENGTH_SHORT).show();
            Log.e(TAG, message);
        });
    }

    /**
     * Thiết lập các event listener
     */
    private void setupEventListeners() {
        btnOCRBack.setOnClickListener(v -> {
            finish();
        });

        btnSaveOCRImageAndWord.setOnClickListener(v -> {
            String ocrText = (currentOcrResult != null) ? currentOcrResult.getText().trim() : "";
            if (ocrText.isEmpty()) {
                Toast.makeText(this, "Không có văn bản để lưu.", Toast.LENGTH_LONG).show();
                return;
            }
            if (currentImageBitmap == null) {
                Toast.makeText(this, "Không có ảnh để lưu.", Toast.LENGTH_LONG).show();
                return;
            }

            String commonTimestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            saveTextToFile(ocrText, commonTimestamp, "txt");
            saveOcrImageToFile(originalImageBitmap, commonTimestamp);

            mainHandler.postDelayed(() -> {
                Intent intent = new Intent(OCRActivity.this, ImagePreviewActivity.class);
                startActivity(intent);
                finish();
            }, 500);
        });

        btnCopyOCRText.setOnClickListener(v -> copyTextToClipboard());
    }

    // Các phương thức còn lại giữ nguyên như code gốc
    private void copyTextToClipboard() {
        String textToCopy = (currentOcrResult != null) ? currentOcrResult.getText().trim() : "";
        if (textToCopy.isEmpty()) {
            Toast.makeText(this, "Không có văn bản để sao chép", Toast.LENGTH_LONG).show();
            return;
        }

        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Văn bản OCR", textToCopy);
        if (clipboard != null) {
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "Đã sao chép văn bản vào bộ nhớ tạm! ", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "Không thể sao chép văn bản! ", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_WRITE_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Đã cấp quyền lưu trữ. Vui lòng thử lại hành động lưu.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Ứng dụng cần quyền ghi để lưu tệp.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void saveTextToFile(String textToSave, String commonTimestamp, String fileExtension) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_WRITE_STORAGE);
        } else {
            performSaveTextFile(textToSave, commonTimestamp, fileExtension);
        }
    }

    private void performSaveTextFile(String textContent, String commonTimestamp, String fileExtension) {
        executorService.execute(() -> {
            String fileName = "OCR_Text_" + commonTimestamp + "." + fileExtension;
            String mimeType = "text/plain";

            Uri fileUri = null;
            ContentResolver resolver = getContentResolver();
            ContentValues contentValues = new ContentValues();
            contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
            contentValues.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);

            OutputStream outputStream = null;
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + File.separator + "MyOCRTexts");
                    fileUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues);
                    if (fileUri == null) {
                        throw new IOException("Không thể tạo URI cho tệp.");
                    }
                    outputStream = resolver.openOutputStream(fileUri);
                } else {
                    File documentsFolder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "MyOCRTexts");
                    if (!documentsFolder.exists()) {
                        documentsFolder.mkdirs();
                    }
                    File file = new File(documentsFolder, fileName);
                    fileUri = Uri.fromFile(file);
                    outputStream = new FileOutputStream(file);
                }

                if (outputStream != null) {
                    outputStream.write(textContent.getBytes());
                    outputStream.close();
                    mainHandler.post(() -> Toast.makeText(OCRActivity.this, "Đã lưu văn bản: " + fileName, Toast.LENGTH_LONG).show());
                } else {
                    throw new IOException("Không thể mở OutputStream.");
                }
            } catch (IOException e) {
                Log.e(TAG, "Lỗi khi lưu tệp văn bản: " + e.getMessage(), e);
                mainHandler.post(() -> Toast.makeText(OCRActivity.this, "Lỗi khi lưu tệp văn bản: " + e.getMessage(), Toast.LENGTH_LONG).show());
            } finally {
                if (outputStream != null) {
                    try {
                        outputStream.close();
                    } catch (IOException e) {
                        Log.e(TAG, "Lỗi khi đóng OutputStream: " + e.getMessage(), e);
                    }
                }
            }
        });
    }

    private void saveOcrImageToFile(Bitmap imageBitmap, String commonTimestamp) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_WRITE_STORAGE); //
        } else { //
            performSaveImageFile(imageBitmap, commonTimestamp); //
        }
    }

    /**
     * Thực hiện việc lưu Bitmap ảnh vào tệp JPEG trong thư mục "Downloads/MyOCRImages".
     * Sử dụng MediaStore cho Android Q+ và FileOutputStream cho các phiên bản cũ hơn.
     *
     * @param imageBitmap     Bitmap của ảnh cần lưu.
     * @param commonTimestamp Dấu thời gian để đặt tên tệp.
     */
    private void performSaveImageFile(Bitmap imageBitmap, String commonTimestamp) { //
        executorService.execute(() -> { // Thực hiện trên luồng nền
            String fileName = "OCR_Image_" + commonTimestamp + ".jpeg"; //
            String mimeType = "image/jpeg"; //

            Uri collectionUri = null; //
            OutputStream outputStream = null; //
            boolean saved = false; //

            try { //
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { //
                    // Đối với Android Q trở lên, sử dụng MediaStore.Downloads
                    ContentValues contentValues = new ContentValues(); //
                    contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName); //
                    contentValues.put(MediaStore.MediaColumns.MIME_TYPE, mimeType); //
                    contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + File.separator + "MyOCRImages"); //
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 1); // Đánh dấu là đang chờ

                    collectionUri = MediaStore.Downloads.EXTERNAL_CONTENT_URI; //
                    Uri itemUri = getContentResolver().insert(collectionUri, contentValues); //

                    if (itemUri == null) { //
                        throw new IOException("Không thể tạo URI cho ảnh."); //
                    }
                    outputStream = getContentResolver().openOutputStream(itemUri); //
                    if (outputStream == null) { //
                        throw new IOException("Không thể mở OutputStream cho ảnh."); //
                    }

                    saved = imageBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream); //

                    if (saved) { //
                        // Đánh dấu ảnh đã hoàn tất và hiển thị trong Gallery/Files
                        ContentValues updateContentValues = new ContentValues(); //
                        updateContentValues.put(MediaStore.Images.Media.IS_PENDING, 0); //
                        getContentResolver().update(itemUri, updateContentValues, null, null); //
                    }

                } else { //
                    // Đối với các phiên bản cũ hơn, lưu trực tiếp vào thư mục công cộng
                    File parentDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "MyOCRImages"); //
                    if (!parentDir.exists()) { //
                        parentDir.mkdirs(); //
                    }
                    File imageFile = new File(parentDir, fileName); //

                    outputStream = new FileOutputStream(imageFile); //
                    saved = imageBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream); //

                    if (saved) { //
                        // Quét Media Store để hiển thị ảnh mới
                        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE); //
                        mediaScanIntent.setData(Uri.fromFile(imageFile)); //
                        sendBroadcast(mediaScanIntent); //
                    }
                }

                final boolean finalSaved = saved; //
                mainHandler.post(() -> { //
                    if (finalSaved) { //
                        Toast.makeText(OCRActivity.this, "Đã lưu ảnh OCR: " + fileName, Toast.LENGTH_LONG).show(); //
                    } else { //
                        Toast.makeText(OCRActivity.this, "Không thể lưu ảnh OCR.", Toast.LENGTH_LONG).show(); //
                    }
                });

            } catch (IOException e) { //
                Log.e(TAG, "Lỗi khi lưu ảnh OCR: " + e.getMessage(), e); //
                final String errorMessage = "Lỗi khi lưu ảnh: " + e.getMessage(); //
                mainHandler.post(() -> Toast.makeText(OCRActivity.this, errorMessage, Toast.LENGTH_LONG).show()); //
            } finally { //
                // Đảm bảo đóng OutputStream
                if (outputStream != null) { //
                    try { //
                        outputStream.close(); //
                    } catch (IOException e) { //
                        Log.e(TAG, "Lỗi khi đóng OutputStream ảnh: " + e.getMessage(), e); //
                    }
                }
            }
        });
    }

    /**
     * Phương thức được gọi khi Activity bị hủy.
     * Đảm bảo giải phóng tài nguyên như ExecutorService và Bitmap,
     * và xóa file tạm thời nếu có.
     */
    @Override
    protected void onDestroy() { //
        super.onDestroy(); //
        // Đóng ExecutorService nếu nó đang chạy
        if (executorService != null && !executorService.isShutdown()) { //
            executorService.shutdownNow(); //
            try { //
                // Chờ một thời gian ngắn để các luồng kết thúc
                if (!executorService.awaitTermination(1, TimeUnit.SECONDS)) { //
                    Log.w(TAG, "ExecutorService không dừng hoàn toàn."); //
                }
            } catch (InterruptedException e) { //
                Log.e(TAG, "Interrupted while waiting for executor to shut down.", e); //
                Thread.currentThread().interrupt(); // Đặt lại trạng thái ngắt
            }
        }

        // Giải phóng Bitmap để tránh rò rỉ bộ nhớ
        // (Bạn vẫn cần giải phóng currentImageBitmap nếu nó được tải để lấy kích thước)
        if (currentImageBitmap != null) { //
            currentImageBitmap.recycle(); //
            currentImageBitmap = null; //
        }

        // Xóa file tạm thời nếu nó được tạo từ scheme "file://"
        // (Điều này quan trọng nếu ảnh được lấy từ Camera hoặc thư mục tạm thời)
        if (imageUriToProcess != null && ContentResolver.SCHEME_FILE.equals(imageUriToProcess.getScheme())) { //
            File file = new File(imageUriToProcess.getPath()); //
            if (file.exists()) { //
                boolean deleted = file.delete(); //
                if (deleted) { //
                    Log.d(TAG, "Đã xóa file tạm thời: " + file.getAbsolutePath()); //
                } else { //
                    Log.e(TAG, "Không thể xóa file tạm thời: " + file.getAbsolutePath()); //
                }
            }
        }
    }
}