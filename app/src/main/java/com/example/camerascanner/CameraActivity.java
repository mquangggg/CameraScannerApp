// CameraActivity.java
package com.example.camerascanner;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Matrix; // Nhập Matrix để xoay Bitmap (vẫn giữ nếu cần cho logic bên trong)
import android.graphics.RectF; // Nhập RectF cho ML Kit
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;
import android.util.Log;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

// Các import của CameraX
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.common.util.concurrent.ListenableFuture;

// Các import của ML Kit
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.objects.ObjectDetection;
import com.google.mlkit.vision.objects.ObjectDetector;
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions;
import com.google.mlkit.vision.objects.DetectedObject;

// Thêm import cho Glide nếu bạn muốn hiển thị ảnh trong ImageView (CameraActivity)
import com.bumptech.glide.Glide;

public class CameraActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 100;
    private static final int STORAGE_PERMISSION_REQUEST_CODE = 101;

    // Request code cho ImagePreviewActivity, để biết khi nào ImagePreviewActivity trả về kết quả
    private static final int REQUEST_CODE_IMAGE_PREVIEW = 200;


    // Các phần tử UI
    private PreviewView previewView;
    private CustomOverlayView customOverlayView;
    private ImageView imageView; // Vẫn cần để hiển thị ảnh tạm thời
    private FloatingActionButton btnTakePhoto;
    private ImageButton btnSelectImage; // Đã bỏ btnConfirm, btnXoay ở đây

    // Các thành phần của CameraX
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private ImageCapture imageCapture;
    private ImageAnalysis imageAnalysis;
    private ExecutorService cameraExecutor;

    // Thành phần của ML Kit
    private ObjectDetector objectDetector;

    // Để chọn ảnh từ thư viện
    private Uri selectedImageUri;
    private ActivityResultLauncher<String> galleryLauncher;

    // Launcher cho ImagePreviewActivity
    private ActivityResultLauncher<Intent> imagePreviewLauncher;

    // Biến để lưu trữ thông tin khung nhận diện cuối cùng từ ML Kit
    private RectF lastDetectedBoundingBox;
    private int lastImageProxyWidth;
    private int lastImageProxyHeight;
    private int lastRotationDegrees;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        // Khởi tạo các phần tử UI
        previewView = findViewById(R.id.previewView);
        customOverlayView = findViewById(R.id.customOverlayView);
        imageView = findViewById(R.id.imageView); // Giữ ImageView
        btnTakePhoto = findViewById(R.id.btnTakePhoto);
        btnSelectImage = findViewById(R.id.btnSelectImage);

        previewView.setScaleType(PreviewView.ScaleType.FIT_CENTER);

        // Khởi tạo CameraX executor
        cameraExecutor = Executors.newSingleThreadExecutor();

        // Khởi tạo Bộ phát hiện đối tượng ML Kit
        ObjectDetectorOptions options =
                new ObjectDetectorOptions.Builder()
                        .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
                        .enableMultipleObjects()
                        .enableClassification()
                        .build();
        objectDetector = ObjectDetection.getClient(options);

        // --- Xử lý quyền và thiết lập Camera/Thư viện ---
        if (checkCameraPermission()) {
            startCamera();
        } else {
            requestCameraPermission();
        }

        // Khởi tạo các launcher
        initLaunchers();

        // --- Lắng nghe sự kiện nút ---
        btnTakePhoto.setOnClickListener(v -> takePhoto());

        btnSelectImage.setOnClickListener(v -> {
            if (checkStoragePermission()) {
                openGallery();
            } else {
                requestStoragePermission();
            }
        });

        // Trạng thái UI ban đầu: camera hiển thị, image view ẩn
        showCameraPreview();
    }

    private void initLaunchers() {
        // Launcher để chọn ảnh từ thư viện
        galleryLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) {
                selectedImageUri = uri;
                Log.d(TAG, "Ảnh được tải từ thư viện, URI gốc: " + selectedImageUri);
                // Hiển thị ảnh đã chọn trong ImageView tạm thời
                Glide.with(this).load(selectedImageUri).into(imageView);
                showImageView(); // Chuyển sang chế độ xem ảnh
                // Gửi URI này đến ImagePreviewActivity
                startImagePreviewActivity(selectedImageUri); // Không có bounding box từ thư viện
            } else {
                Toast.makeText(this, getString(R.string.failed_to_get_image_from_gallery), Toast.LENGTH_SHORT).show();
                Log.e(TAG, "selectedImageUri rỗng sau khi xử lý kết quả thư viện.");
                showCameraPreview(); // Quay lại camera nếu không có gì được chọn
            }
        });

        // Launcher cho kết quả từ ImagePreviewActivity
        imagePreviewLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK) {
                if (result.getData() != null) {
                    Uri confirmedImageUri = result.getData().getData();
                    if (confirmedImageUri != null) {
                        Log.d(TAG, "URI ảnh đã xác nhận từ ImagePreviewActivity: " + confirmedImageUri);
                        // Chuyển URI này sang CropActivity
                        Intent cropIntent = new Intent(CameraActivity.this, CropActivity.class);
                        cropIntent.putExtra("imageUri", confirmedImageUri);
                        startActivity(cropIntent); // Khởi chạy CropActivity, CropActivity sẽ tự xử lý kết quả
                    } else {
                        Toast.makeText(this, getString(R.string.no_cropped_image_received), Toast.LENGTH_SHORT).show(); // Có thể đổi chuỗi này thành "Không có ảnh được xác nhận"
                        Log.e(TAG, "URI đã xác nhận rỗng từ ImagePreviewActivity.");
                    }
                }
            } else if (result.getResultCode() == RESULT_CANCELED) {
                Toast.makeText(this, getString(R.string.crop_canceled), Toast.LENGTH_SHORT).show(); // Có thể đổi chuỗi này thành "Xem trước ảnh bị hủy"
                Log.d(TAG, "Thao tác ImagePreviewActivity bị hủy.");
            } else {
                Toast.makeText(this, getString(R.string.crop_failed), Toast.LENGTH_SHORT).show(); // Có thể đổi chuỗi này thành "Xem trước ảnh thất bại"
                Log.e(TAG, "Thao tác ImagePreviewActivity thất bại với mã kết quả: " + result.getResultCode());
            }
            // Sau khi quay lại từ ImagePreviewActivity, luôn hiển thị lại xem trước camera
            showCameraPreview();
        });
    }

    private void startImagePreviewActivity(Uri imageUri) {
        Intent intent = new Intent(CameraActivity.this, ImagePreviewActivity.class);
        intent.putExtra("imageUri", imageUri.toString()); // Chuyển URI dưới dạng String
        imagePreviewLauncher.launch(intent);
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
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(cameraExecutor, imageProxy -> {
            processImageProxy(imageProxy);
        });

        imageCapture = new ImageCapture.Builder()
                .setTargetRotation(previewView.getDisplay().getRotation())
                .build();

        cameraProvider.unbindAll();

        try {
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis, imageCapture);
        } catch (Exception e) {
            Log.e(TAG, "Lỗi khi liên kết các trường hợp sử dụng camera: " + e.getMessage(), e);
        }
    }

    @androidx.annotation.OptIn(markerClass = androidx.camera.core.ExperimentalGetImage.class)
    private void processImageProxy(ImageProxy imageProxy) {
        android.media.Image mediaImage = imageProxy.getImage();
        if (mediaImage != null) {
            InputImage image = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());

            objectDetector.process(image)
                    .addOnSuccessListener(detectedObjects -> {
                        if (!detectedObjects.isEmpty()) {
                            DetectedObject firstObject = detectedObjects.get(0);
                            RectF boundingBox = new RectF(firstObject.getBoundingBox());

                            lastDetectedBoundingBox = new RectF(boundingBox);
                            lastImageProxyWidth = imageProxy.getWidth();
                            lastImageProxyHeight = imageProxy.getHeight();
                            lastRotationDegrees = imageProxy.getImageInfo().getRotationDegrees();

                            // --- Logic chuyển đổi cho lớp phủ PreviewView ---
                            int previewWidth = previewView.getWidth();
                            int previewHeight = previewView.getHeight();

                            int imageProxyWidth = imageProxy.getWidth();
                            int imageProxyHeight = imageProxy.getHeight();
                            int rotationDegrees = imageProxy.getImageInfo().getRotationDegrees();

                            int effectiveImageWidth = (rotationDegrees == 90 || rotationDegrees == 270) ? imageProxyHeight : imageProxyWidth;
                            int effectiveImageHeight = (rotationDegrees == 90 || rotationDegrees == 270) ? imageProxyWidth : imageProxyHeight;

                            Matrix matrix = new Matrix();

                            float scaleX = (float) previewWidth / effectiveImageWidth;
                            float scaleY = (float) previewHeight / effectiveImageHeight;
                            float scale = Math.min(scaleX, scaleY);

                            matrix.postScale(scale, scale);

                            float dx = (previewWidth - effectiveImageWidth * scale) / 2f;
                            float dy = (previewHeight - effectiveImageHeight * scale) / 2f;
                            matrix.postTranslate(dx, dy);

                            RectF transformedBoundingBox = new RectF();
                            matrix.mapRect(transformedBoundingBox, boundingBox);

                            customOverlayView.setBoundingBox(transformedBoundingBox);
                            customOverlayView.postInvalidate();

                        } else {
                            customOverlayView.clearBoundingBox();
                            customOverlayView.postInvalidate();
                            lastDetectedBoundingBox = null;
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Phát hiện đối tượng thất bại: " + e.getMessage(), e);
                        customOverlayView.clearBoundingBox();
                        customOverlayView.postInvalidate();
                        lastDetectedBoundingBox = null;
                    })
                    .addOnCompleteListener(task -> {
                        imageProxy.close();
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
                    Toast.makeText(CameraActivity.this, getString(R.string.photo_captured_saved), Toast.LENGTH_SHORT).show();
                    Log.d(TAG, "Ảnh đã chụp và lưu: " + savedUri);

                    // --- BẮT ĐẦU LOGIC CẮT ẢNH DỰA TRÊN KHUNG NHẬN DIỆN VÀ HIỂN THỊ ---
                    Bitmap originalFullBitmap = null;
                    try {
                        originalFullBitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), savedUri);
                    } catch (IOException e) {
                        Log.e(TAG, "Không thể tải full bitmap để xử lý: " + e.getMessage(), e);
                        Toast.makeText(CameraActivity.this, getString(R.string.failed_to_load_captured_image), Toast.LENGTH_SHORT).show();
                        selectedImageUri = savedUri;
                        Glide.with(CameraActivity.this).load(selectedImageUri).into(imageView);
                        showImageView();
                        // Gửi ảnh gốc đã lưu sang ImagePreviewActivity
                        startImagePreviewActivity(selectedImageUri);
                        return;
                    }

                    if (originalFullBitmap != null) {
                        if (lastDetectedBoundingBox != null && lastImageProxyWidth > 0 && lastImageProxyHeight > 0) {
                            RectF mlKitUprightBox = new RectF(lastDetectedBoundingBox);

                            float rawImageWidth = lastImageProxyWidth;
                            float rawImageHeight = lastImageProxyHeight;
                            float fullBitmapWidth = originalFullBitmap.getWidth();
                            float fullBitmapHeight = originalFullBitmap.getHeight();

                            float conceptualMlKitImageWidth = (lastRotationDegrees == 90 || lastRotationDegrees == 270) ? rawImageHeight : rawImageWidth;
                            float conceptualMlKitImageHeight = (lastRotationDegrees == 90 || lastRotationDegrees == 270) ? rawImageWidth : rawImageHeight;

                            float scaleFactorX = fullBitmapWidth / conceptualMlKitImageWidth;
                            float scaleFactorY = fullBitmapHeight / conceptualMlKitImageHeight;

                            RectF finalCroppedRectFloat = new RectF(
                                    mlKitUprightBox.left * scaleFactorX,
                                    mlKitUprightBox.top * scaleFactorY,
                                    mlKitUprightBox.right * scaleFactorX,
                                    mlKitUprightBox.bottom * scaleFactorY
                            );

                            finalCroppedRectFloat.sort();

                            int cropX = Math.max(0, (int) Math.floor(finalCroppedRectFloat.left));
                            int cropY = Math.max(0, (int) Math.floor(finalCroppedRectFloat.top));
                            int cropRight = Math.min(originalFullBitmap.getWidth(), (int) Math.ceil(finalCroppedRectFloat.right));
                            int cropBottom = Math.min(originalFullBitmap.getHeight(), (int) Math.ceil(finalCroppedRectFloat.bottom));

                            int cropWidth = Math.max(0, cropRight - cropX);
                            int cropHeight = Math.max(0, cropBottom - cropY);

                            if (cropWidth > 0 && cropHeight > 0) {
                                Bitmap croppedBitmap = Bitmap.createBitmap(
                                        originalFullBitmap,
                                        cropX,
                                        cropY,
                                        cropWidth,
                                        cropHeight
                                );

                                selectedImageUri = saveBitmapToCache(croppedBitmap);
                                Glide.with(CameraActivity.this).load(selectedImageUri).into(imageView);
                                showImageView();
                                if (croppedBitmap != originalFullBitmap) croppedBitmap.recycle();
                                originalFullBitmap.recycle();

                                // Gửi ảnh đã cắt sang ImagePreviewActivity
                                startImagePreviewActivity(selectedImageUri);

                            } else {
                                Log.e(TAG, "Kích thước hoặc tọa độ cắt không hợp lệ sau khi giới hạn. Hiển thị ảnh gốc đầy đủ.");
                                selectedImageUri = savedUri;
                                Glide.with(CameraActivity.this).load(selectedImageUri).into(imageView);
                                showImageView();
                                originalFullBitmap.recycle();
                                // Gửi ảnh gốc đã lưu sang ImagePreviewActivity
                                startImagePreviewActivity(selectedImageUri);
                            }

                        } else {
                            Log.w(TAG, "Không phát hiện khung giới hạn hoặc kích thước proxy ảnh không hợp lệ. Hiển thị ảnh gốc đầy đủ.");
                            selectedImageUri = savedUri;
                            Glide.with(CameraActivity.this).load(selectedImageUri).into(imageView);
                            showImageView();
                            originalFullBitmap.recycle();
                            // Gửi ảnh gốc đã lưu sang ImagePreviewActivity
                            startImagePreviewActivity(selectedImageUri);
                        }

                    } else {
                        Log.e(TAG, "originalFullBitmap rỗng sau khi chụp.");
                        selectedImageUri = savedUri;
                        Glide.with(CameraActivity.this).load(selectedImageUri).into(imageView);
                        showImageView();
                        // Gửi ảnh gốc đã lưu sang ImagePreviewActivity
                        startImagePreviewActivity(selectedImageUri);
                    }
                } else {
                    Toast.makeText(CameraActivity.this, getString(R.string.failed_to_save_image), Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Không thể lưu ảnh: Uri null");
                }
            }

            @Override
            public void onError(ImageCaptureException exception) {
                Log.e(TAG, "Lỗi khi chụp ảnh: " + exception.getMessage(), exception);
                Toast.makeText(CameraActivity.this, "Lỗi khi chụp ảnh: " + exception.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // --- Quản lý hiển thị UI ---
    private void showCameraPreview() {
        previewView.setVisibility(View.VISIBLE);
        customOverlayView.setVisibility(View.VISIBLE);
        imageView.setVisibility(View.GONE);
        btnTakePhoto.setVisibility(View.VISIBLE);
        btnSelectImage.setVisibility(View.VISIBLE);

        // Đảm bảo camera được khởi động nếu chúng ta đang hiển thị xem trước
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        }
    }

    private void showImageView() {
        previewView.setVisibility(View.GONE);
        customOverlayView.setVisibility(View.GONE);
        imageView.setVisibility(View.VISIBLE);
        btnTakePhoto.setVisibility(View.GONE);
        btnSelectImage.setVisibility(View.GONE);

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
        galleryLauncher.launch("image/*");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
                startCamera();
            } else if (requestCode == STORAGE_PERMISSION_REQUEST_CODE) {
                openGallery();
            }
        } else {
            String permissionName = (permissions.length > 0) ? permissions[0] : "Quyền không xác định";
            Toast.makeText(this, permissionName + getString(R.string.permission_denied_function_unavailable), Toast.LENGTH_LONG).show();
            Log.w(TAG, "Quyền bị từ chối cho: " + permissionName + " (Mã yêu cầu: " + requestCode + ")");
        }
    }

    // --- Xử lý nút Back ---
    @Override
    public void onBackPressed() {
        // Nếu ImageView đang hiển thị ảnh, quay lại chế độ xem trước camera
        if (imageView.getVisibility() == View.VISIBLE) {
            selectedImageUri = null; // Xóa URI ảnh đã chọn
            imageView.setImageDrawable(null); // Xóa ảnh khỏi ImageView
            showCameraPreview();
        } else {
            // Ngược lại, thực hiện hành động back mặc định (thoát activity)
            super.onBackPressed();
        }
    }

    // --- Các phương thức tiện ích ---
    // Phương thức rotateBitmap không còn được sử dụng trực tiếp ở đây,
    // nhưng giữ lại để tiện nếu có logic tương lai.
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
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
        if (objectDetector != null) {
            objectDetector.close();
        }
    }
}