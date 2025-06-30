// MainActivity.java
package com.example.camerascanner;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas; // Nhập Canvas để vẽ lên Bitmap
import android.graphics.Matrix; // Nhập Matrix để xoay Bitmap
import android.graphics.Paint; // Nhập Paint để thiết lập màu và kiểu vẽ
import android.graphics.RectF; // Nhập RectF cho ML Kit
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;
import android.util.Log;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull; // Giữ nguyên @NonNull cho các phần khác
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.exifinterface.media.ExifInterface;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

// Các import của CameraX
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import com.google.common.util.concurrent.ListenableFuture;

// Các import của ML Kit
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.objects.ObjectDetection;
import com.google.mlkit.vision.objects.ObjectDetector;
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions;
import com.google.mlkit.vision.objects.DetectedObject;


public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 100;
    private static final int STORAGE_PERMISSION_REQUEST_CODE = 101; // Để đọc/ghi ảnh từ thư viện

    // Các phần tử UI
    private PreviewView previewView; // Xem trước camera
    private CustomOverlayView customOverlayView; // Lớp phủ cho khung nhận diện đối tượng
    private ImageView imageView; // Để hiển thị ảnh đã chụp/chọn sau khi xử lý
    private Button btnTakePhoto, btnSelectImage, btnConfirm, btnXoay;

    // Các thành phần của CameraX
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private ImageCapture imageCapture;
    private ImageAnalysis imageAnalysis; // Thêm để phân tích thời gian thực
    private ExecutorService cameraExecutor; // Executor cho phân tích hình ảnh

    // Thành phần của ML Kit
    private ObjectDetector objectDetector;

    // Để chọn ảnh từ thư viện và chuyển sang màn hình cắt ảnh
    private Uri selectedImageUri;
    private ActivityResultLauncher<String> galleryLauncher; // Đổi sang GetContent để chọn thư viện đơn giản hơn
    private ActivityResultLauncher<Intent> cropActivityLauncher;

    // Biến để lưu trữ thông tin khung nhận diện cuối cùng từ ML Kit
    // Bounding Box từ ML Kit luôn ở hệ tọa độ ảnh gốc (unrotated) của ImageProxy
    private RectF lastDetectedBoundingBox;
    // Chiều rộng và chiều cao gốc của ImageProxy (trước khi xoay)
    private int lastImageProxyWidth;
    private int lastImageProxyHeight;
    // Độ xoay cần thiết để đưa ImageProxy về hướng thẳng đứng (từ ImageProxy.getImageInfo().getRotationDegrees())
    private int lastRotationDegrees;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Khởi tạo các phần tử UI
        previewView = findViewById(R.id.previewView);
        customOverlayView = findViewById(R.id.customOverlayView);
        imageView = findViewById(R.id.imageView); // Giữ ImageView để hiển thị sau khi chụp/chọn
        btnTakePhoto = findViewById(R.id.btnTakePhoto);
        btnSelectImage = findViewById(R.id.btnSelectImage);
        btnConfirm = findViewById(R.id.btnConfirm);
        btnXoay = findViewById(R.id.btnXoay);

        // Đảm bảo PreviewView sử dụng FIT_CENTER để khớp với logic chuyển đổi bounding box
        previewView.setScaleType(PreviewView.ScaleType.FIT_CENTER);

        // Khởi tạo CameraX executor
        cameraExecutor = Executors.newSingleThreadExecutor();

        // Khởi tạo Bộ phát hiện đối tượng ML Kit
        ObjectDetectorOptions options =
                new ObjectDetectorOptions.Builder()
                        .setDetectorMode(ObjectDetectorOptions.STREAM_MODE) // Phát hiện thời gian thực
                        .enableMultipleObjects()
                        .enableClassification() // Bật nếu bạn cần nhãn đối tượng
                        .build();
        objectDetector = ObjectDetection.getClient(options);

        // --- Xử lý quyền và thiết lập Camera/Thư viện ---
        if (checkCameraPermission()) {
            startCamera(); // Bắt đầu camera trong ứng dụng ngay lập tức nếu đã cấp quyền
        } else {
            requestCameraPermission();
        }

        // Khởi tạo các launcher cho thư viện và hoạt động cắt ảnh
        initLaunchers();

        // --- Lắng nghe sự kiện nút ---
        btnTakePhoto.setOnClickListener(v -> takePhoto()); // Gọi chức năng chụp ảnh trong ứng dụng

        btnSelectImage.setOnClickListener(v -> {
            if (checkStoragePermission()) {
                openGallery(); // Mở thư viện bằng launcher
            } else {
                requestStoragePermission();
            }
        });

        btnXoay.setOnClickListener(v -> {
            if (selectedImageUri != null) {
                try {
                    Bitmap currentBitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), selectedImageUri);
                    if (currentBitmap != null) {
                        Bitmap rotatedBitmap = rotateBitmap(currentBitmap, 90);
                        imageView.setImageBitmap(rotatedBitmap);
                        selectedImageUri = saveBitmapToCache(rotatedBitmap);
                        Log.d(TAG, "Ảnh đã xoay 90 độ. URI mới: " + selectedImageUri);
                    } else {
                        Toast.makeText(this, "Không thể tải ảnh để xoay.", Toast.LENGTH_SHORT).show();
                        Log.e(TAG, "Bitmap rỗng khi cố gắng xoay từ URI: " + selectedImageUri);
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Lỗi khi xoay ảnh: " + e.getMessage(), e);
                    Toast.makeText(this, getString(R.string.error_rotating_image), Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, getString(R.string.please_select_capture_image_first), Toast.LENGTH_SHORT).show();
                Log.w(TAG, "Nút xoay được nhấn nhưng không có URI ảnh nào được chọn.");
            }
        });

        btnConfirm.setOnClickListener(v -> {
            if (selectedImageUri != null) {
                Log.d(TAG, "Khởi chạy CropActivity với URI đã xử lý: " + selectedImageUri);
                Intent cropIntent = new Intent(MainActivity.this, CropActivity.class);
                cropIntent.putExtra("imageUri", selectedImageUri);
                cropActivityLauncher.launch(cropIntent);
            } else {
                Toast.makeText(this, getString(R.string.please_select_capture_image_first), Toast.LENGTH_SHORT).show();
                Log.w(TAG, "Nút xác nhận được nhấn nhưng không có URI ảnh nào được chọn.");
            }
        });

        // Trạng thái UI ban đầu: camera hiển thị, image view ẩn, nút xác nhận/xoay ẩn
        showCameraPreview();
    }

    private void initLaunchers() {
        // Launcher để chọn ảnh từ thư viện
        galleryLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) {
                selectedImageUri = uri;
                imageView.setImageURI(selectedImageUri);
                showImageView(); // Hiển thị ImageView và ẩn xem trước camera
                Log.d(TAG, "Ảnh được tải từ thư viện, URI gốc: " + selectedImageUri);
            } else {
                Toast.makeText(this, getString(R.string.failed_to_get_image_from_gallery), Toast.LENGTH_SHORT).show();
                Log.e(TAG, "selectedImageUri rỗng sau khi xử lý kết quả thư viện.");
                showCameraPreview(); // Quay lại camera nếu không có gì được chọn
            }
        });

        // Launcher cho kết quả từ CropActivity
        cropActivityLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK) {
                if (result.getData() != null) {
                    Uri croppedUri = result.getData().getParcelableExtra("croppedUri");
                    if (croppedUri != null) {
                        Log.d(TAG, "URI ảnh đã cắt nhận được từ CropActivity: " + croppedUri);
                        Intent pdfPreviewIntent = new Intent(MainActivity.this, PdfGenerationAndPreviewActivity.class);
                        pdfPreviewIntent.putExtra("croppedUri", croppedUri);
                        startActivity(pdfPreviewIntent);
                    } else {
                        Toast.makeText(this, getString(R.string.no_cropped_image_received), Toast.LENGTH_SHORT).show();
                        Log.e(TAG, "URI đã cắt rỗng từ CropActivity.");
                    }
                } else {
                    Toast.makeText(this, getString(R.string.crop_activity_no_data), Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Dữ liệu kết quả CropActivity rỗng.");
                }
            } else if (result.getResultCode() == RESULT_CANCELED) {
                Toast.makeText(this, getString(R.string.crop_canceled), Toast.LENGTH_SHORT).show();
                Log.d(TAG, "Thao tác CropActivity bị hủy.");
            } else {
                Toast.makeText(this, getString(R.string.crop_failed), Toast.LENGTH_SHORT).show();
                Log.e(TAG, "Thao tác CropActivity thất bại với mã kết quả: " + result.getResultCode());
            }
            // Sau khi quay lại từ CropActivity, hiển thị lại xem trước camera
            showCameraPreview();
        });
    }

    // --- Tích hợp CameraX và ML Kit ---
    private void startCamera() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Lỗi khi bắt đầu camera: " + e.getMessage(), e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCameraUseCases(@NonNull ProcessCameraProvider cameraProvider) {
        // Trường hợp sử dụng Preview
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        // Chọn camera sau
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        // Trường hợp sử dụng ImageAnalysis để xử lý thời gian thực
        imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(cameraExecutor, imageProxy -> {
            processImageProxy(imageProxy); // Xử lý từng khung hình
        });

        // Trường hợp sử dụng ImageCapture để chụp ảnh
        imageCapture = new ImageCapture.Builder()
                .setTargetRotation(previewView.getDisplay().getRotation())
                .build();

        // Hủy liên kết tất cả các trường hợp sử dụng trước khi liên kết lại
        cameraProvider.unbindAll();

        try {
            // Liên kết tất cả các trường hợp sử dụng với vòng đời
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis, imageCapture);
        } catch (Exception e) {
            Log.e(TAG, "Lỗi khi liên kết các trường hợp sử dụng camera: " + e.getMessage(), e);
        }
    }

    @androidx.annotation.OptIn(markerClass = androidx.camera.core.ExperimentalGetImage.class)
    private void processImageProxy(ImageProxy imageProxy) {
        android.media.Image mediaImage = imageProxy.getImage();
        if (mediaImage != null) {
            // Tạo InputImage cho ML Kit. ML Kit sẽ tự xử lý rotationDegrees để nội bộ xử lý ảnh thẳng đứng.
            // Tuy nhiên, bounding box mà ML Kit trả về vẫn dựa trên ảnh gốc (unrotated).
            InputImage image = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());

            objectDetector.process(image)
                    .addOnSuccessListener(detectedObjects -> {
                        if (!detectedObjects.isEmpty()) {
                            // Giả sử chúng ta chỉ hiển thị khung của đối tượng đầu tiên được phát hiện
                            DetectedObject firstObject = detectedObjects.get(0);
                            RectF boundingBox = new RectF(firstObject.getBoundingBox()); // Bounding box từ ML Kit, luôn ở hệ tọa độ ảnh gốc (unrotated)

                            // Lưu trữ thông tin này để sử dụng khi chụp ảnh
                            lastDetectedBoundingBox = new RectF(boundingBox);
                            lastImageProxyWidth = imageProxy.getWidth();
                            lastImageProxyHeight = imageProxy.getHeight();
                            lastRotationDegrees = imageProxy.getImageInfo().getRotationDegrees(); // Độ xoay của cảm biến

                            // --- Logic chuyển đổi cho lớp phủ PreviewView (đã xác nhận hoạt động tốt) ---
                            // Lấy kích thước của PreviewView
                            int previewWidth = previewView.getWidth();
                            int previewHeight = previewView.getHeight();

                            // Lấy kích thước gốc của ImageProxy (trước khi ML Kit xử lý xoay)
                            // QUAN TRỌNG: imageProxy.getWidth() và getHeight() là kích thước THÔ của khung hình
                            int imageProxyWidth = imageProxy.getWidth();
                            int imageProxyHeight = imageProxy.getHeight();
                            int rotationDegrees = imageProxy.getImageInfo().getRotationDegrees();

                            // Tính toán kích thước ảnh "hiệu quả" sau khi được xoay để thẳng đứng
                            // Nếu rotationDegrees là 90 hoặc 270, chiều rộng và chiều cao sẽ hoán đổi
                            int effectiveImageWidth = (rotationDegrees == 90 || rotationDegrees == 270) ? imageProxyHeight : imageProxyWidth;
                            int effectiveImageHeight = (rotationDegrees == 90 || rotationDegrees == 270) ? imageProxyWidth : imageProxyHeight;

                            // Tạo Matrix để chuyển đổi tọa độ từ không gian ảnh hiệu quả sang không gian PreviewView
                            Matrix matrix = new Matrix();

                            // Bước 1: Tỷ lệ ảnh để khớp với PreviewView.
                            // Logic này phải khớp với PreviewView.ScaleType bạn đã đặt (FIT_CENTER)
                            float scaleX = (float) previewWidth / effectiveImageWidth;
                            float scaleY = (float) previewHeight / effectiveImageHeight;
                            float scale = Math.min(scaleX, scaleY); // Dùng cho FIT_CENTER

                            matrix.postScale(scale, scale);

                            // Bước 2: Dịch chuyển ảnh để căn giữa nếu dùng FIT_CENTER
                            float dx = (previewWidth - effectiveImageWidth * scale) / 2f;
                            float dy = (previewHeight - effectiveImageHeight * scale) / 2f;
                            matrix.postTranslate(dx, dy);

                            // Áp dụng matrix lên bounding box ML Kit
                            // boundingBox từ ML Kit là trên ảnh unrotated, nhưng nếu PreviewView đã xoay,
                            // thì mapping trực tiếp này sẽ được người dùng cho là "hoạt động tốt"
                            RectF transformedBoundingBox = new RectF();
                            matrix.mapRect(transformedBoundingBox, boundingBox);

                            // Cập nhật lớp phủ tùy chỉnh với bounding box đã chuyển đổi
                            customOverlayView.setBoundingBox(transformedBoundingBox); // Truyền RectF
                            customOverlayView.postInvalidate(); // Vẽ lại lớp phủ

                        } else {
                            customOverlayView.clearBoundingBox(); // Xóa khung nếu không phát hiện đối tượng nào
                            customOverlayView.postInvalidate(); // Vẽ lại lớp phủ
                            // Nếu không có đối tượng được phát hiện, hãy xóa thông tin bounding box đã lưu
                            lastDetectedBoundingBox = null;
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Phát hiện đối tượng thất bại: " + e.getMessage(), e);
                        customOverlayView.clearBoundingBox();
                        customOverlayView.postInvalidate();
                        lastDetectedBoundingBox = null; // Xóa thông tin bounding box khi phát hiện lỗi
                    })
                    .addOnCompleteListener(task -> {
                        imageProxy.close(); // Quan trọng: Đóng ImageProxy để giải phóng tài nguyên
                    });
        }
    }

    private void takePhoto() {
        if (imageCapture == null) {
            Log.e(TAG, "ImageCapture chưa được khởi tạo.");
            Toast.makeText(this, getString(R.string.camera_not_ready), Toast.LENGTH_SHORT).show();
            return;
        }

        File photoFile = new File(getCacheDir(), "captured_image_" + System.currentTimeMillis() + ".jpeg");

        ImageCapture.OutputFileOptions outputFileOptions =
                new ImageCapture.OutputFileOptions.Builder(photoFile).build();

        imageCapture.takePicture(outputFileOptions, ContextCompat.getMainExecutor(this), new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(ImageCapture.OutputFileResults outputFileResults) {
                Uri savedUri = outputFileResults.getSavedUri();
                if (savedUri != null) {
                    Toast.makeText(MainActivity.this, getString(R.string.photo_captured_saved), Toast.LENGTH_SHORT).show();
                    Log.d(TAG, "Ảnh đã chụp và lưu: " + savedUri);

                    // --- BẮT ĐẦU LOGIC CẮT ẢNH DỰA TRÊN KHUNG NHẬN DIỆN VÀ HIỂN THỊ ---
                    Bitmap originalFullBitmap = null;
                    try {
                        originalFullBitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), savedUri);
                    } catch (IOException e) {
                        Log.e(TAG, "Không thể tải full bitmap để xử lý: " + e.getMessage(), e);
                        Toast.makeText(MainActivity.this, getString(R.string.failed_to_load_captured_image), Toast.LENGTH_SHORT).show();
                        selectedImageUri = savedUri;
                        imageView.setImageURI(selectedImageUri);
                        showImageView();
                        return;
                    }

                    if (originalFullBitmap != null) {
                        if (lastDetectedBoundingBox != null && lastImageProxyWidth > 0 && lastImageProxyHeight > 0) {
                            // lastDetectedBoundingBox từ ML Kit đã được làm thẳng đứng
                            RectF mlKitUprightBox = new RectF(lastDetectedBoundingBox);

                            float rawImageWidth = lastImageProxyWidth;
                            float rawImageHeight = lastImageProxyHeight;
                            float fullBitmapWidth = originalFullBitmap.getWidth();
                            float fullBitmapHeight = originalFullBitmap.getHeight();

                            Log.d(TAG, "DEBUG_CROP_FINAL: ML Kit Upright Box (từ ML Kit): " + mlKitUprightBox.toString());
                            Log.d(TAG, "DEBUG_CROP_FINAL: Kích thước ảnh Proxy thô cuối cùng (cảm biến): " + rawImageWidth + "x" + rawImageHeight);
                            Log.d(TAG, "DEBUG_CROP_FINAL: Độ xoay cuối cùng (xoay cảm biến để thẳng đứng): " + lastRotationDegrees);
                            Log.d(TAG, "DEBUG_CROP_FINAL: Kích thước Bitmap đầy đủ (đầu ra thẳng đứng của CameraX): " + fullBitmapWidth + "x" + fullBitmapHeight);

                            // Tính toán kích thước ảnh "ý niệm" của ML Kit sau khi nó tự làm thẳng đứng.
                            // Kích thước này có thể hoán đổi chiều rộng/chiều cao so với ảnh thô ban đầu
                            // tùy thuộc vào lastRotationDegrees.
                            float conceptualMlKitImageWidth = (lastRotationDegrees == 90 || lastRotationDegrees == 270) ? rawImageHeight : rawImageWidth;
                            float conceptualMlKitImageHeight = (lastRotationDegrees == 90 || lastRotationDegrees == 270) ? rawImageWidth : rawImageHeight;

                            Log.d(TAG, "DEBUG_CROP_FINAL: Kích thước ảnh ML Kit (ý niệm, thẳng đứng sau xử lý nội bộ ML Kit): " + conceptualMlKitImageWidth + "x" + conceptualMlKitImageHeight);

                            // Tính toán các hệ số tỷ lệ từ kích thước ảnh "ý niệm" của ML Kit
                            // sang kích thước thực tế của originalFullBitmap (cũng đã thẳng đứng).
                            float scaleFactorX = fullBitmapWidth / conceptualMlKitImageWidth;
                            float scaleFactorY = fullBitmapHeight / conceptualMlKitImageHeight;

                            Log.d(TAG, "DEBUG_CROP_FINAL: Hệ số tỷ lệ đến Bitmap đầy đủ: scaleX=" + scaleFactorX + ", scaleY=" + scaleFactorY);

                            // Áp dụng tỷ lệ này trực tiếp vào bounding box đã thẳng đứng của ML Kit.
                            RectF finalCroppedRectFloat = new RectF(
                                    mlKitUprightBox.left * scaleFactorX,
                                    mlKitUprightBox.top * scaleFactorY,
                                    mlKitUprightBox.right * scaleFactorX,
                                    mlKitUprightBox.bottom * scaleFactorY
                            );

                            finalCroppedRectFloat.sort(); // Đảm bảo tọa độ được sắp xếp đúng (left <= right, top <= bottom) sau khi scaling

                            Log.d(TAG, "DEBUG_CROP_FINAL: RectF cuối cùng trên Bitmap (float, trước khi giới hạn): " + finalCroppedRectFloat.toString());

                            // --- LOGIC GIỚI HẠN VÀ LOGGING CHI TIẾT ---
                            // Chuyển đổi sang tọa độ nguyên ban đầu (có thể hơi lệch)
                            int cropX_calculated = (int) Math.floor(finalCroppedRectFloat.left);
                            int cropY_calculated = (int) Math.floor(finalCroppedRectFloat.top);
                            int cropRight_calculated = (int) Math.ceil(finalCroppedRectFloat.right);
                            int cropBottom_calculated = (int) Math.ceil(finalCroppedRectFloat.bottom);

                            Log.d(TAG, "DEBUG_CROP_FINAL: Biên giới nguyên ban đầu (trước khi giới hạn): L=" + cropX_calculated + ", T=" + cropY_calculated + ", R=" + cropRight_calculated + ", B=" + cropBottom_calculated);

                            // Giới hạn các tọa độ x và y để không vượt quá ranh giới của bitmap
                            int cropX = Math.max(0, cropX_calculated);
                            int cropY = Math.max(0, cropY_calculated);

                            // Giới hạn các tọa độ right và bottom để không vượt quá kích thước của bitmap
                            // Điều này sẽ đảm bảo cropRight <= originalFullBitmap.getWidth() và cropBottom <= originalFullBitmap.getHeight()
                            int cropRight = Math.min(originalFullBitmap.getWidth(), cropRight_calculated);
                            int cropBottom = Math.min(originalFullBitmap.getHeight(), cropBottom_calculated);

                            // Tính toán lại chiều rộng và chiều cao sau khi đã giới hạn các cạnh
                            // Sử dụng Math.max(0, ...) để tránh chiều rộng/chiều cao âm nếu có lỗi tính toán nhỏ
                            int cropWidth = Math.max(0, cropRight - cropX);
                            int cropHeight = Math.max(0, cropBottom - cropY);
                            // --- KẾT THÚC LOGIC GIỚI HẠN ---

                            Log.d(TAG, "DEBUG_CROP_FINAL: Vùng cắt cuối cùng (đã giới hạn): X=" + cropX + ", Y=" + cropY + ", W=" + cropWidth + ", H=" + cropHeight);

                            // Kiểm tra xem kích thước vùng cắt có hợp lệ không trước khi tạo bitmap
                            // Đảm bảo cả chiều rộng và chiều cao đều lớn hơn 0 để tránh lỗi createBitmap với kích thước 0
                            if (cropWidth > 0 && cropHeight > 0) {
                                Bitmap croppedBitmap = Bitmap.createBitmap(
                                        originalFullBitmap,
                                        cropX,
                                        cropY,
                                        cropWidth,
                                        cropHeight
                                );

                                selectedImageUri = saveBitmapToCache(croppedBitmap);
                                imageView.setImageURI(selectedImageUri);
                                showImageView();
                                Log.d(TAG, "DEBUG_CROP_FINAL: Ảnh đã cắt được hiển thị thành công.");

                                if (croppedBitmap != originalFullBitmap) croppedBitmap.recycle();
                                originalFullBitmap.recycle(); // Giải phóng originalFullBitmap

                            } else {
                                Log.e(TAG, "DEBUG_CROP_FINAL: Kích thước hoặc tọa độ cắt không hợp lệ sau khi giới hạn. Chiều rộng=" + cropWidth + ", Chiều cao=" + cropHeight + ". Hiển thị ảnh gốc đầy đủ.");
                                selectedImageUri = savedUri;
                                imageView.setImageURI(selectedImageUri);
                                showImageView();
                                originalFullBitmap.recycle();
                            }

                        } else {
                            Log.w(TAG, "DEBUG_CROP_FINAL: Không phát hiện khung giới hạn hoặc kích thước proxy ảnh không hợp lệ. Hiển thị ảnh gốc đầy đủ.");
                            selectedImageUri = savedUri;
                            imageView.setImageURI(selectedImageUri);
                            showImageView();
                            originalFullBitmap.recycle();
                        }

                    } else {
                        Log.e(TAG, "DEBUG_CROP_FINAL: originalFullBitmap rỗng sau khi chụp.");
                        selectedImageUri = savedUri;
                        imageView.setImageURI(selectedImageUri);
                        showImageView();
                    }
                } else {
                    Toast.makeText(MainActivity.this, getString(R.string.failed_to_save_image), Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Không thể lưu ảnh: Uri null");
                }
            }

            @Override
            public void onError(ImageCaptureException exception) {
                Log.e(TAG, "Lỗi khi chụp ảnh: " + exception.getMessage(), exception);
                Toast.makeText(MainActivity.this, "Lỗi khi chụp ảnh: " + exception.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // --- Quản lý hiển thị UI ---
    private void showCameraPreview() {
        previewView.setVisibility(View.VISIBLE);
        customOverlayView.setVisibility(View.VISIBLE);
        imageView.setVisibility(View.GONE);
        btnXoay.setVisibility(View.GONE);
        btnConfirm.setVisibility(View.GONE);
        // Đảm bảo camera được khởi động nếu chúng ta đang hiển thị xem trước
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        }
    }

    private void showImageView() {
        previewView.setVisibility(View.GONE);
        customOverlayView.setVisibility(View.GONE);
        imageView.setVisibility(View.VISIBLE);
        btnXoay.setVisibility(View.VISIBLE);
        btnConfirm.setVisibility(View.VISIBLE);
        // Dừng các trường hợp sử dụng camera khi hiển thị chế độ xem hình ảnh
        if (cameraProviderFuture != null && cameraProviderFuture.isDone()) {
            try {
                cameraProviderFuture.get().unbindAll();
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Lỗi khi hủy liên kết các trường hợp sử dụng camera: " + e.getMessage(), e);
            }
        }
    }

    // --- Xử lý quyền ---
    private boolean checkCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA},
                CAMERA_PERMISSION_REQUEST_CODE);
    }

    private void requestStoragePermission() {
        String permission;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permission = Manifest.permission.READ_MEDIA_IMAGES;
        } else {
            permission = Manifest.permission.READ_EXTERNAL_STORAGE;
        }
        ActivityCompat.requestPermissions(this,
                new String[]{permission},
                STORAGE_PERMISSION_REQUEST_CODE);
    }

    private void openGallery() {
        // Sử dụng launcher đã tạo trong initLaunchers()
        galleryLauncher.launch("image/*");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
                startCamera(); // Bắt đầu camera trong ứng dụng
            } else if (requestCode == STORAGE_PERMISSION_REQUEST_CODE) {
                // Nếu quyền lưu trữ dành cho thư viện, khởi chạy lại thư viện
                openGallery();
            }
        } else {
            String permissionName = (permissions.length > 0) ? permissions[0] : "Quyền không xác định";
            Toast.makeText(this, permissionName + getString(R.string.permission_denied_function_unavailable), Toast.LENGTH_LONG).show();
            Log.w(TAG, "Quyền bị từ chối cho: " + permissionName + " (Mã yêu cầu: " + requestCode + ")");
            // Nếu quyền camera bị từ chối, bạn có thể muốn chuyển sang giao diện người dùng dự phòng hoặc thoát
            // Hiện tại, nó sẽ chỉ hiển thị một thông báo và xem trước camera sẽ không bắt đầu.
        }
    }

    // --- Xử lý nút Back ---
    @Override
    public void onBackPressed() {
        // Nếu ImageView đang hiển thị (có ảnh đang được xem)
        if (imageView.getVisibility() == View.VISIBLE) {
            // Chuyển về chế độ xem trước camera
            showCameraPreview();
            selectedImageUri = null; // Xóa URI ảnh đã chọn
            lastDetectedBoundingBox = null; // Xóa khung nhận diện đã lưu
            Toast.makeText(this, getString(R.string.back_to_camera_mode), Toast.LENGTH_SHORT).show();
        } else {
            // Nếu camera preview đã hoạt động, thực hiện hành động back mặc định (thoát ứng dụng)
            super.onBackPressed();
        }
    }

    // --- Các phương thức tiện ích ---
    private Bitmap rotateBitmap(Bitmap bitmap, float degrees) {
        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);
        Bitmap rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        if (rotatedBitmap != bitmap) {
            bitmap.recycle();
        }
        return rotatedBitmap;
    }

    private Uri saveBitmapToCache(Bitmap bitmap) {
        String fileName = "temp_processed_image_" + System.currentTimeMillis() + ".jpeg";
        File cachePath = new File(getCacheDir(), "processed_images");
        cachePath.mkdirs();
        File file = new File(cachePath, fileName);

        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
            fos.flush();
            return Uri.fromFile(file);
        } catch (IOException e) {
            Log.e(TAG, "Lỗi khi lưu bitmap đã xử lý vào cache: " + e.getMessage(), e);
            return null;
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    Log.e(TAG, "Lỗi khi đóng FileOutputStream: " + e.getMessage(), e);
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Tắt executor của camera để ngăn chặn rò rỉ bộ nhớ
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
        // Giải phóng tài nguyên của bộ phát hiện ML Kit
        if (objectDetector != null) {
            objectDetector.close();
        }
    }
}
