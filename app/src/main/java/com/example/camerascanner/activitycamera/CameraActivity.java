package com.example.camerascanner.activitycamera;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory; // Thêm import này
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Pair;
import android.util.Size; // Thêm import này cho CameraX
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.core.resolutionselector.ResolutionStrategy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.example.camerascanner.R;
import com.example.camerascanner.activitycrop.CropActivity;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.common.util.concurrent.ListenableFuture;

import org.opencv.android.OpenCVLoader; // Import OpenCVLoader
import org.opencv.android.Utils; // Import Utils
import org.opencv.core.Core; // Import Core
import org.opencv.core.CvType; // Import CvType
import org.opencv.core.Mat; // Import Mat
import org.opencv.core.MatOfPoint; // Import MatOfPoint
import org.opencv.core.MatOfPoint2f; // Import MatOfPoint2f
import org.opencv.core.Point; // Import Point
import org.opencv.imgproc.CLAHE; // Import CLAHE
import org.opencv.imgproc.Imgproc; // Import Imgproc

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CameraActivity extends AppCompatActivity {

    private static final String TAG = "CameraActivity"; // Đổi TAG để tránh nhầm lẫn
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 100;
    private static final int STORAGE_PERMISSION_REQUEST_CODE = 101;
    private static final int REQUEST_CODE_IMAGE_PREVIEW = 200;

    private PreviewView previewView;
    private CustomOverlayView customOverlayView; // Bây giờ sẽ nhận MatOfPoint
    private ImageView imageView;
    private FloatingActionButton btnTakePhoto;
    private ImageButton btnSelectImage;

    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private ImageCapture imageCapture;
    private ImageAnalysis imageAnalysis;
    private ExecutorService cameraExecutor;

    // --- Các hằng số và biến OpenCV ---
    private static final double CANNY_THRESHOLD1 = 40;
    private static final double CANNY_THRESHOLD2 = 120;
    private double dynamicCannyThreshold1 = CANNY_THRESHOLD1;
    private double dynamicCannyThreshold2 = CANNY_THRESHOLD2;
    private static final double APPROX_POLY_DP_EPSILON_FACTOR = 0.05;
    private static final double MIN_COSINE_ANGLE = 0.5;
    private static final double MIN_AREA_PERCENTAGE = 0.02;
    private static final double MAX_AREA_PERCENTAGE = 0.90;

    private MatOfPoint lastDetectedQuadrilateral = null; // Lưu trữ MatOfPoint cuối cùng được phát hiện
    private int lastImageProxyWidth = 0; // Kích thước của ImageProxy khi phát hiện được khung
    private int lastImageProxyHeight = 0;
    private int lastRotationDegrees = 0; // Độ xoay của ImageProxy khi phát hiện được khung

    private long lastDetectionTimestamp = 0L;
    private static final long QUAD_PERSISTENCE_TIMEOUT_MS = 1500; // 1.5 giây

    private int frameCount = 0;
    private static final int PROCESS_FRAME_INTERVAL = 3; // Xử lý mỗi 3 khung hình

    private Uri selectedImageUri;
    private ActivityResultLauncher<String> galleryLauncher;
    private ActivityResultLauncher<Intent> imagePreviewLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        // Khởi tạo OpenCV
        if (!OpenCVLoader.initLocal()) {
            Log.e(TAG, "OpenCV initialization failed!");
            Toast.makeText(this, "OpenCV failed to load!", Toast.LENGTH_LONG).show();
            return;
        } else {
            Log.d(TAG, "OpenCV initialization successful!");
        }

        previewView = findViewById(R.id.previewView);
        customOverlayView = findViewById(R.id.customOverlayView);
        imageView = findViewById(R.id.imageView);
        btnTakePhoto = findViewById(R.id.btnTakePhoto);
        btnSelectImage = findViewById(R.id.btnSelectImage);

        // Điều chỉnh ScaleType của PreviewView nếu cần, ví dụ FIT_CENTER, FIT_XY
        previewView.setScaleType(PreviewView.ScaleType.FIT_CENTER); // Hoặc PreviewView.ScaleType.FILL_CENTER để lấp đầy

        cameraExecutor = Executors.newSingleThreadExecutor();

        if (checkCameraPermission()) {
            startCamera();
        } else {
            requestCameraPermission();
        }

        initLaunchers();

        btnTakePhoto.setOnClickListener(v -> takePhoto());

        btnSelectImage.setOnClickListener(v -> {
            if (checkStoragePermission()) {
                openGallery();
            } else {
                requestStoragePermission();
            }
        });

        showCameraPreview();
    }

    private void initLaunchers() {
        galleryLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) {
                selectedImageUri = uri;
                Log.d(TAG, "Ảnh được tải từ thư viện, URI gốc: " + selectedImageUri);
                showImageView(); // Chuyển sang chế độ xem ảnh
                processStaticImage(selectedImageUri); // Xử lý ảnh tĩnh bằng OpenCV
            } else {
                Toast.makeText(this, getString(R.string.failed_to_get_image_from_gallery), Toast.LENGTH_SHORT).show();
                Log.e(TAG, "selectedImageUri rỗng sau khi xử lý kết quả thư viện.");
                showCameraPreview();
            }
        });

        imagePreviewLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK) {
                if (result.getData() != null) {
                    Uri confirmedImageUri = result.getData().getData();
                    if (confirmedImageUri != null) {
                        Log.d(TAG, "URI ảnh đã xác nhận từ ImagePreviewActivity: " + confirmedImageUri);
                        Intent cropIntent = new Intent(CameraActivity.this, CropActivity.class);
                        cropIntent.putExtra("imageUri", confirmedImageUri.toString()); // Chuyển URI dưới dạng String
                        startActivity(cropIntent);
                    } else {
                        Toast.makeText(this, getString(R.string.no_cropped_image_received), Toast.LENGTH_SHORT).show();
                        Log.e(TAG, "URI đã xác nhận rỗng từ ImagePreviewActivity.");
                    }
                }
            } else if (result.getResultCode() == RESULT_CANCELED) {
                Toast.makeText(this, getString(R.string.crop_canceled), Toast.LENGTH_SHORT).show();
                Log.d(TAG, "Thao tác ImagePreviewActivity bị hủy.");
            } else {
                Toast.makeText(this, getString(R.string.crop_failed), Toast.LENGTH_SHORT).show();
                Log.e(TAG, "Thao tác ImagePreviewActivity thất bại với mã kết quả: " + result.getResultCode());
            }
            showCameraPreview();
        });
    }

    private void startImagePreviewActivity(Uri imageUri) {
        Intent intent = new Intent(CameraActivity.this, ImagePreviewActivity.class);
        intent.putExtra("imageUri", imageUri.toString());
        imagePreviewLauncher.launch(intent);
    }

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
                .setResolutionSelector(new ResolutionSelector.Builder()
                        .setResolutionStrategy(new ResolutionStrategy(new Size(640, 480),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                        .build()) // Đặt độ phân giải phân tích
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(cameraExecutor, imageProxy -> {

            Log.d(TAG, "DEBUG_DIM: ImageProxy original dimensions: " + imageProxy.getWidth() + "x" + imageProxy.getHeight() + " Rotation: " + imageProxy.getImageInfo().getRotationDegrees());

            // ĐÃ SỬA: Biến để giữ MatOfPoint và Mat từ processImageFrame
            MatOfPoint newlyDetectedQuadrilateral = null;
            Mat processedFrameForDimensions = null; // ĐÃ SỬA: Biến để giữ Mat đã xử lý

            try {
                final MatOfPoint finalQuadrilateralForOverlay;

                if (frameCount % PROCESS_FRAME_INTERVAL == 0) {
                    // ĐÃ SỬA: Thay đổi cách gọi processImageFrame và nhận Pair
                    Pair<MatOfPoint, Mat> detectionResult = processImageFrame(imageProxy);
                    newlyDetectedQuadrilateral = detectionResult.first; // <--- Lấy MatOfPoint từ Pair
                    processedFrameForDimensions = detectionResult.second; // <--- Lấy Mat đã xử lý

                    Log.d(TAG, "Đã xử lý khung hình đầy đủ. Khung: " + frameCount);

                    if (newlyDetectedQuadrilateral != null && processedFrameForDimensions != null) { // <--- ĐÃ SỬA: Kiểm tra cả Mat
                        if (lastDetectedQuadrilateral != null) {
                            lastDetectedQuadrilateral.release();
                        }
                        lastDetectedQuadrilateral = new MatOfPoint(newlyDetectedQuadrilateral.toArray()); // Tạo bản sao
                        lastDetectionTimestamp = System.currentTimeMillis();

                        // LƯU Ý QUAN TRỌNG: Lưu kích thước của MAT ĐÃ ĐƯỢC XOAY mà OpenCV đã xử lý
                        lastImageProxyWidth = processedFrameForDimensions.width(); // <--- ĐÃ SỬA
                        lastImageProxyHeight = processedFrameForDimensions.height(); // <--- ĐÃ SỬA
                        lastRotationDegrees = imageProxy.getImageInfo().getRotationDegrees(); // Vẫn lấy độ xoay của ImageProxy

                        finalQuadrilateralForOverlay = newlyDetectedQuadrilateral;
                        Log.d(TAG, "DEBUG_DIM: Processed Mat dimensions (from processedFrameForDimensions): " + processedFrameForDimensions.width() + "x" + processedFrameForDimensions.height());
                        Log.d(TAG, "DEBUG_DIM: Stored lastImageProxyWidth: " + lastImageProxyWidth + " lastImageProxyHeight: " + lastImageProxyHeight);
                    } else {
                        if (lastDetectedQuadrilateral != null && (System.currentTimeMillis() - lastDetectionTimestamp > QUAD_PERSISTENCE_TIMEOUT_MS)) {
                            Log.d(TAG, "lastDetectedQuadrilateral đã hết thời gian chờ. Giải phóng và đặt là null.");
                            lastDetectedQuadrilateral.release();
                            lastDetectedQuadrilateral = null;
                        }
                        finalQuadrilateralForOverlay = lastDetectedQuadrilateral;
                    }
                } else {
                    if (lastDetectedQuadrilateral != null && (System.currentTimeMillis() - lastDetectionTimestamp > QUAD_PERSISTENCE_TIMEOUT_MS)) {
                        Log.d(TAG, "lastDetectedQuadrilateral đã hết thời gian chờ trên khung bị bỏ qua. Giải phóng và đặt là null.");
                        lastDetectedQuadrilateral.release();
                        lastDetectedQuadrilateral = null;
                    }
                    finalQuadrilateralForOverlay = lastDetectedQuadrilateral;
                    Log.d(TAG, "Bỏ qua xử lý khung hình đầy đủ. Khung: " + frameCount + ". Hiển thị khung cũ nếu có.");
                }

                runOnUiThread(() -> {
                    if (finalQuadrilateralForOverlay != null) {
                        // Kích thước overlay view của bạn đã được tính toán đúng dựa trên effectiveImageWidth/Height
                        // Đây là kích thước Mat mà các điểm được phát hiện trên đó.
                        int effectiveImageWidth = lastImageProxyWidth; // <--- ĐÃ SỬA: đã lưu kích thước đúng rồi
                        int effectiveImageHeight = lastImageProxyHeight; // <--- ĐÃ SỬA: đã lưu kích thước đúng rồi

                        // Gọi scalePointsToOverlayView từ CustomOverlayView
                        customOverlayView.setQuadrilateral(
                                CustomOverlayView.scalePointsToOverlayView(
                                        finalQuadrilateralForOverlay,
                                        effectiveImageWidth,
                                        effectiveImageHeight,
                                        previewView.getWidth(), // Sử dụng kích thước của PreviewView
                                        previewView.getHeight()
                                )
                        );
                    } else {
                        customOverlayView.clearBoundingBox(); // Sử dụng clearBoundingBox hiện có
                    }
                    customOverlayView.invalidate(); // Yêu cầu vẽ lại
                });

            } catch (Exception e) {
                Log.e(TAG, "Error in image analysis: " + e.getMessage(), e);
            } finally {
                imageProxy.close();
                if (newlyDetectedQuadrilateral != null) {
                    newlyDetectedQuadrilateral.release();
                }
                if (processedFrameForDimensions != null) { // <--- ĐÃ SỬA: Giải phóng Mat được trả về
                    processedFrameForDimensions.release();
                }
                frameCount++;
            }
        });

        imageCapture = new ImageCapture.Builder()
                .setTargetRotation(previewView.getDisplay().getRotation())
                .build();
        Log.d("CameraActivity", "ImageCapture target rotation: " + imageCapture.getTargetRotation());

        cameraProvider.unbindAll();

        try {
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis, imageCapture);
        } catch (Exception e) {
            Log.e(TAG, "Lỗi khi liên kết các trường hợp sử dụng camera: " + e.getMessage(), e);
        }
    }


    // Trong CameraActivity.java

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

                    Bitmap originalFullBitmap = null;
                    try {
                        originalFullBitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), savedUri);
                        if (originalFullBitmap.getWidth() > originalFullBitmap.getHeight()) {
                            Matrix matrix = new Matrix();
                            matrix.postRotate(90); // Xoay 90 độ theo chiều kim đồng hồ
                            originalFullBitmap = Bitmap.createBitmap(originalFullBitmap, 0, 0,
                                    originalFullBitmap.getWidth(), originalFullBitmap.getHeight(), matrix, true);
                        }
                    } catch (IOException e) {
                        Toast.makeText(CameraActivity.this, getString(R.string.failed_to_load_captured_image), Toast.LENGTH_SHORT).show();
                        selectedImageUri = savedUri;
                        Glide.with(CameraActivity.this).load(selectedImageUri).into(imageView);
                        showImageView();
                        startImagePreviewActivity(selectedImageUri); // Gửi ảnh gốc nếu lỗi
                        return;
                    }

                    if (originalFullBitmap != null) {
                        if (lastDetectedQuadrilateral != null && !lastDetectedQuadrilateral.empty()) {
                            // Kích thước của Mat mà lastDetectedQuadrilateral được tìm thấy
                            // Đây là các giá trị đã được lưu đúng sau khi xoay Mat
                            int originalDetectionWidth = lastImageProxyWidth; // <--- ĐÃ SỬA
                            int originalDetectionHeight = lastImageProxyHeight; // <--- ĐÃ SỬA

                            // Kích thước của ảnh Bitmap đã chụp
                            int capturedBitmapWidth = originalFullBitmap.getWidth();
                            int capturedBitmapHeight = originalFullBitmap.getHeight();

                            // Tính toán tỷ lệ khung hình của khung phân tích và ảnh chụp
                            double analysisAspectRatio = (double) originalDetectionWidth / originalDetectionHeight;
                            double captureAspectRatio = (double) capturedBitmapWidth / capturedBitmapHeight;

                            double effectiveSrcWidth = originalDetectionWidth;
                            double effectiveSrcHeight = originalDetectionHeight;
                            double offsetX = 0;
                            double offsetY = 0;

                            // Điều chỉnh cho sự không khớp tỷ lệ khung hình tiềm ẩn và cắt xén/letterboxing ngầm bởi CameraX
                            // Đây là một heuristic phổ biến nếu CameraX cắt xén một luồng để phù hợp với tỷ lệ khung hình của luồng khác.
                            // Sử dụng ngưỡng nhỏ để tránh vấn đề dấu phẩy động
                            if (Math.abs(analysisAspectRatio - captureAspectRatio) > 0.01) {
                                if (analysisAspectRatio > captureAspectRatio) {
                                    // Khung phân tích rộng hơn khung chụp (ví dụ: phân tích 16:9, chụp 4:3)
                                    // Điều này có nghĩa là CameraX có thể đã cắt theo chiều dọc của khung phân tích
                                    effectiveSrcHeight = (double) originalDetectionWidth / captureAspectRatio;
                                    offsetY = (originalDetectionHeight - effectiveSrcHeight) / 2.0;
                                    Log.d(TAG, "Điều chỉnh cho tỷ lệ khung hình phân tích rộng hơn, effectiveSrcHeight mới: " + effectiveSrcHeight + ", offsetY: " + offsetY);
                                } else {
                                    // Khung phân tích hẹp hơn khung chụp (ví dụ: phân tích 4:3, chụp 16:9)
                                    // Điều này có nghĩa là CameraX có thể đã cắt theo chiều ngang của khung phân tích
                                    effectiveSrcWidth = (double) originalDetectionHeight * captureAspectRatio;
                                    offsetX = (originalDetectionWidth - effectiveSrcWidth) / 2.0;
                                    Log.d(TAG, "Điều chỉnh cho tỷ lệ khung hình phân tích hẹp hơn, effectiveSrcWidth mới: " + effectiveSrcWidth + ", offsetX: " + offsetX);
                                }
                            }

                            // Tính toán tỷ lệ scale dựa trên kích thước nguồn hiệu quả (đã điều chỉnh)
                            double scaleX = (double) capturedBitmapWidth / effectiveSrcWidth;
                            double scaleY = (double) capturedBitmapHeight / effectiveSrcHeight;

                            Point[] detectedPoints = lastDetectedQuadrilateral.toArray();

                            // Scale và dịch chuyển các điểm
                            Point[] scaledPoints = new Point[4];
                            for (int i = 0; i < 4; i++) {
                                scaledPoints[i] = new Point(
                                        (detectedPoints[i].x - offsetX) * scaleX, // Áp dụng offset sau đó scale
                                        (detectedPoints[i].y - offsetY) * scaleY  // Áp dụng offset sau đó scale
                                );
                            }

                            // Sắp xếp lại các điểm (quan trọng cho perspective transform)
                            // Hàm sortPoints() của bạn cần đảm bảo các điểm được sắp xếp đúng thứ tự: Top-Left, Top-Right, Bottom-Right, Bottom-Left
                            MatOfPoint sortedScaledPoints = sortPoints(new MatOfPoint(scaledPoints));
                            Point[] sortedPts = sortedScaledPoints.toArray();

                            // Tính toán kích thước của hình chữ nhật đích
                            // Chiều rộng là trung bình của cạnh trên và cạnh dưới
                            double widthTop = Math.sqrt(Math.pow(sortedPts[0].x - sortedPts[1].x, 2) + Math.pow(sortedPts[0].y - sortedPts[1].y, 2));
                            double widthBottom = Math.sqrt(Math.pow(sortedPts[3].x - sortedPts[2].x, 2) + Math.pow(sortedPts[3].y - sortedPts[2].y, 2));
                            int targetWidth = (int) Math.max(widthTop, widthBottom);

                            // Chiều cao là trung bình của cạnh trái và cạnh phải
                            double heightLeft = Math.sqrt(Math.pow(sortedPts[0].x - sortedPts[3].x, 2) + Math.pow(sortedPts[0].y - sortedPts[3].y, 2));
                            double heightRight = Math.sqrt(Math.pow(sortedPts[1].x - sortedPts[2].x, 2) + Math.pow(sortedPts[1].y - sortedPts[2].y, 2));
                            int targetHeight = (int) Math.max(heightLeft, heightRight);

                            // Đảm bảo kích thước không quá nhỏ
                            if (targetWidth <= 0 || targetHeight <= 0) {
                                Log.e(TAG, "Kích thước đích không hợp lệ cho biến đổi phối cảnh. Sử dụng ảnh gốc.");
                                selectedImageUri = savedUri;
                                Glide.with(CameraActivity.this).load(selectedImageUri).into(imageView);
                                showImageView();
                                if (originalFullBitmap != null) originalFullBitmap.recycle();
                                startImagePreviewActivity(selectedImageUri);
                                return;
                            }

                            // Định nghĩa 4 điểm nguồn (tứ giác đã phát hiện) và 4 điểm đích (hình chữ nhật chuẩn)
                            MatOfPoint2f srcPoints = new MatOfPoint2f(
                                    sortedPts[0], // Top-Left
                                    sortedPts[1], // Top-Right
                                    sortedPts[2], // Bottom-Right
                                    sortedPts[3]  // Bottom-Left
                            );

                            MatOfPoint2f dstPoints = new MatOfPoint2f(
                                    new Point(0, 0),
                                    new Point(targetWidth - 1, 0),
                                    new Point(targetWidth - 1, targetHeight - 1),
                                    new Point(0, targetHeight - 1)
                            );

                            // Chuyển đổi Bitmap gốc sang Mat của OpenCV
                            Mat originalMat = new Mat(originalFullBitmap.getHeight(), originalFullBitmap.getWidth(), CvType.CV_8UC4);
                            Utils.bitmapToMat(originalFullBitmap, originalMat);

                            // Thực hiện biến đổi phối cảnh
                            Mat transformedMat = new Mat();
                            Mat perspectiveTransform = Imgproc.getPerspectiveTransform(srcPoints, dstPoints);
                            Imgproc.warpPerspective(originalMat, transformedMat, perspectiveTransform, new org.opencv.core.Size(targetWidth, targetHeight));

                            // Chuyển đổi Mat đã biến đổi trở lại thành Bitmap
                            Bitmap resultBitmap = Bitmap.createBitmap(transformedMat.cols(), transformedMat.rows(), Bitmap.Config.ARGB_8888);
                            Utils.matToBitmap(transformedMat, resultBitmap);

                            // Lưu Bitmap đã xử lý vào bộ nhớ cache và hiển thị
                            selectedImageUri = saveBitmapToCache(resultBitmap);
                            Glide.with(CameraActivity.this).load(selectedImageUri).into(imageView);
                            showImageView();

                            // Giải phóng tài nguyên OpenCV và Bitmap
                            if (originalFullBitmap != null) originalFullBitmap.recycle();
                            if (resultBitmap != null && resultBitmap != originalFullBitmap) resultBitmap.recycle(); // Cẩn thận với việc giải phóng resultBitmap nếu nó có thể là originalFullBitmap
                            originalMat.release();
                            transformedMat.release();
                            perspectiveTransform.release();
                            srcPoints.release();
                            dstPoints.release();
                            sortedScaledPoints.release();

                            // Chuyển ảnh đã xử lý sang ImagePreviewActivity
                            startImagePreviewActivity(selectedImageUri);

                        } else {
                            Log.w(TAG, "Không phát hiện khung giới hạn OpenCV hoặc khung rỗng. Hiển thị ảnh gốc đầy đủ.");
                            selectedImageUri = savedUri;
                            Glide.with(CameraActivity.this).load(selectedImageUri).into(imageView);
                            showImageView();
                            if (originalFullBitmap != null) originalFullBitmap.recycle();
                            startImagePreviewActivity(selectedImageUri);
                        }
                    } else {
                        Log.e(TAG, "originalFullBitmap rỗng sau khi chụp.");
                        selectedImageUri = savedUri;
                        Glide.with(CameraActivity.this).load(selectedImageUri).into(imageView);
                        showImageView();
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


    // Xử lý ảnh tĩnh được chọn từ thư viện bằng OpenCV
    private void processStaticImage(Uri imageUri) {
        new Thread(() -> {
            Mat rgba = null;
            Mat gray = null;
            Mat edges = null;
            Mat hierarchy = null;
            List<MatOfPoint> contours = null;
            MatOfPoint bestQuadrilateral = null;

            try {
                InputStream inputStream = getContentResolver().openInputStream(imageUri);
                Bitmap originalBitmap = BitmapFactory.decodeStream(inputStream);
                if (inputStream != null) {
                    inputStream.close();
                }

                if (originalBitmap == null) {
                    Log.e(TAG, "Could not decode bitmap from URI: " + imageUri);
                    return;
                }

                Bitmap resizedBitmap = resizeBitmap(originalBitmap, 800); // Kích thước tối đa cho xử lý

                rgba = new Mat();
                Utils.bitmapToMat(resizedBitmap, rgba);

                gray = new Mat();
                Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY);

                Imgproc.medianBlur(gray, gray, 5);

                CLAHE clahe = Imgproc.createCLAHE(2.0, new org.opencv.core.Size(8, 8));
                clahe.apply(gray, gray);

                edges = new Mat();
                Imgproc.Canny(gray, edges, CANNY_THRESHOLD1, CANNY_THRESHOLD2);

                Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new org.opencv.core.Size(3, 3));
                Imgproc.dilate(edges, edges, kernel);
                kernel.release();

                contours = new ArrayList<>();
                hierarchy = new Mat();
                Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE);

                bestQuadrilateral = findBestQuadrilateral(contours, gray.width(), gray.height());

                final MatOfPoint finalBestQuadrilateralForToast = bestQuadrilateral;

                final Bitmap resultBitmap = Bitmap.createBitmap(rgba.cols(), rgba.rows(), Bitmap.Config.ARGB_8888);
                if (bestQuadrilateral != null) {
                    bestQuadrilateral = sortPoints(bestQuadrilateral);
                    //Imgproc.drawContours(rgba, Collections.singletonList(bestQuadrilateral), -1, new Scalar(0, 255, 0, 255), 5);
                    Log.i(TAG, "Static Image - Đã phát hiện tứ giác tốt nhất với diện tích: " + Imgproc.contourArea(bestQuadrilateral));
                } else {
                    Log.i(TAG, "Static Image - Không tìm thấy tứ giác hợp lệ nào.");
                }
                Utils.matToBitmap(rgba, resultBitmap);

                runOnUiThread(() -> {
                    imageView.setImageBitmap(resultBitmap); // Hiển thị ảnh đã xử lý lên imageView
                    if (finalBestQuadrilateralForToast != null) {
                        Toast.makeText(CameraActivity.this, "Đã tìm thấy khung trên ảnh tĩnh!", Toast.LENGTH_SHORT).show();
                        // Chuyển ảnh đã xử lý (với khung vẽ) sang ImagePreviewActivity
                        // Lưu ý: nếu bạn muốn crop ảnh tĩnh theo khung, bạn cần thêm logic cắt ở đây
                        startImagePreviewActivity(saveBitmapToCache(resultBitmap)); // Lưu Bitmap vào cache để truyền URI
                    } else {
                        Toast.makeText(CameraActivity.this, "Không tìm thấy khung trên ảnh tĩnh.", Toast.LENGTH_SHORT).show();
                        startImagePreviewActivity(imageUri); // Nếu không tìm thấy, gửi ảnh gốc
                    }
                });

            } catch (IOException e) {
                Log.e(TAG, "Error loading static image: " + e.getMessage());
                runOnUiThread(() -> Toast.makeText(CameraActivity.this, "Lỗi tải ảnh: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            } finally {
                if (rgba != null) rgba.release();
                if (gray != null) gray.release();
                if (edges != null) edges.release();
                if (hierarchy != null) hierarchy.release();
                if (contours != null) {
                    for (MatOfPoint contour : contours) {
                        contour.release();
                    }
                }
            }
        }).start();
    }

    private Bitmap resizeBitmap(Bitmap bitmap, int maxDimension) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        float ratio = (float) width / height;

        if (width > height) {
            if (width > maxDimension) {
                width = maxDimension;
                height = (int) (width / ratio);
            }
        } else {
            if (height > maxDimension) {
                height = maxDimension;
                width = (int) (height * ratio);
            }
        }
        return Bitmap.createScaledBitmap(bitmap, width, height, true);
    }

    private void showCameraPreview() {
        previewView.setVisibility(View.VISIBLE);
        customOverlayView.setVisibility(View.VISIBLE);
        imageView.setVisibility(View.GONE);
        btnTakePhoto.setVisibility(View.VISIBLE);
        btnSelectImage.setVisibility(View.VISIBLE);

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

        if (cameraProviderFuture != null && cameraProviderFuture.isDone()) {
            try {
                cameraProviderFuture.get().unbindAll();
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Lỗi khi hủy liên kết các trường hợp sử dụng camera: " + e.getMessage(), e);
            }
        }
    }

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
        // Giải phóng MatOfPoint cuối cùng khi Activity bị hủy
        if (lastDetectedQuadrilateral != null) {
            lastDetectedQuadrilateral.release();
            lastDetectedQuadrilateral = null;
        }
    }

    // --- Các hàm xử lý ảnh OpenCV được copy từ MainActivity ---

    // Thêm import này nếu chưa có

    @androidx.annotation.OptIn(markerClass = androidx.camera.core.ExperimentalGetImage.class)
    private Pair<MatOfPoint, Mat> processImageFrame(ImageProxy imageProxy) { // ĐÃ SỬA: Thay đổi kiểu trả về
        Mat gray = null;
        Mat edges = null;
        Mat hierarchy = null;
        List<MatOfPoint> contours = null;
        MatOfPoint bestQuadrilateral = null;
        Mat matForDimensionStorage = null; // ĐÃ SỬA: Biến mới để lưu Mat cần lấy kích thước

        try {
            ImageProxy.PlaneProxy yPlane = imageProxy.getPlanes()[0];
            ByteBuffer yBuffer = yPlane.getBuffer();
            int yRowStride = yPlane.getRowStride();
            int yPixelStride = yPlane.getPixelStride();

            int originalFrameWidth = imageProxy.getWidth();
            int originalFrameHeight = imageProxy.getHeight();

            adjustOpenCVParametersForResolution(originalFrameWidth, originalFrameHeight);

            gray = new Mat(originalFrameHeight, originalFrameWidth, CvType.CV_8UC1);

            byte[] data = new byte[originalFrameWidth * originalFrameHeight];
            int bufferOffset = 0;

            for (int row = 0; row < originalFrameHeight; ++row) {
                int bytesToReadInRow = originalFrameWidth;
                if (yBuffer.remaining() < bytesToReadInRow) {
                    Log.e(TAG, "BufferUnderflow: Not enough bytes for row " + row + ". Remaining: " + yBuffer.remaining() + ", Needed: " + bytesToReadInRow + ". Skipping frame.");
                    return new Pair<>(null, null); // ĐÃ SỬA: Trả về null cho cả hai nếu lỗi
                }
                yBuffer.get(data, bufferOffset, bytesToReadInRow);
                bufferOffset += bytesToReadInRow;

                int paddingBytes = yRowStride - originalFrameWidth;
                if (paddingBytes > 0) {
                    if (yBuffer.remaining() >= paddingBytes) {
                        yBuffer.position(yBuffer.position() + paddingBytes);
                    } else {
                        Log.w(TAG, "Not enough buffer remaining to skip padding for row " + row + ". Remaining: " + yBuffer.remaining() + ", Expected padding: " + paddingBytes + ". Further rows might be misaligned.");
                        break;
                    }
                }
            }
            gray.put(0, 0, data);

            // --- Phần xoay ảnh ---
            int rotationDegrees = imageProxy.getImageInfo().getRotationDegrees();
            Log.d("CameraActivity", "ImageAnalysis rotationDegrees: " + rotationDegrees);
            if (rotationDegrees == 90 || rotationDegrees == 270) {
                Mat rotatedGray = new Mat();
                Core.transpose(gray, rotatedGray);
                Core.flip(rotatedGray, rotatedGray, (rotationDegrees == 90) ? 1 : 0);
                gray.release(); // Giải phóng Mat gốc
                gray = rotatedGray; // 'gray' bây giờ là Mat đã được xoay
            }

            // --- Các bước xử lý ảnh OpenCV còn lại giữ nguyên, thao tác trên 'gray' đã xoay ---
            Imgproc.medianBlur(gray, gray, 5);
            CLAHE clahe = Imgproc.createCLAHE(2.0, new org.opencv.core.Size(8, 8));
            clahe.apply(gray, gray);
            edges = new Mat();
            Imgproc.Canny(gray, edges, dynamicCannyThreshold1, dynamicCannyThreshold2);
            Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new org.opencv.core.Size(3, 3));
            Imgproc.dilate(edges, edges, kernel);
            kernel.release();
            contours = new ArrayList<>();
            hierarchy = new Mat();
            Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE);
            bestQuadrilateral = findBestQuadrilateral(contours, gray.width(), gray.height());

            // Lưu trữ Mat đã xử lý cuối cùng để trả về lấy kích thước
            matForDimensionStorage = gray.clone(); // ĐÃ SỬA: Clone Mat để tránh bị release trong finally

        } catch (Exception e) {
            Log.e(TAG, "Error processing image frame: " + e.getMessage(), e);
            return new Pair<>(null, null); // ĐÃ SỬA: Trả về null nếu có lỗi
        } finally {
            // ĐÃ SỬA: Không giải phóng 'gray' ở đây vì nó đã được clone và sẽ được trả về
            // if (gray != null) gray.release();
            if (edges != null) edges.release();
            if (hierarchy != null) hierarchy.release();
            if (contours != null) {
                for (MatOfPoint m : contours) {
                    m.release();
                }
            }
        }
        return new Pair<>(bestQuadrilateral, matForDimensionStorage); // ĐÃ SỬA: Trả về cả hai
    }
    private void adjustOpenCVParametersForResolution(int frameWidth, int frameHeight) {
        // Điều chỉnh ngưỡng Canny dựa trên chiều rộng khung hình
        // Bạn có thể tùy chỉnh các ngưỡng và khoảng độ phân giải này
        // dựa trên kết quả thử nghiệm trên các thiết bị khác nhau của mình.
        if (frameWidth <= 480) { // Ví dụ: Cho độ phân giải rất thấp (VD: 480p hoặc thấp hơn)
            dynamicCannyThreshold1 = 20;
            dynamicCannyThreshold2 = 60;
        } else if (frameWidth <= 640) { // Ví dụ: Cho độ phân giải 640x480
            dynamicCannyThreshold1 = 30;
            dynamicCannyThreshold2 = 90;
        } else if (frameWidth <= 1280) { // Ví dụ: Cho độ phân giải 720p hoặc 1024x768
            dynamicCannyThreshold1 = 40;
            dynamicCannyThreshold2 = 120;
        } else { // Ví dụ: Cho độ phân giải 1080p trở lên
            dynamicCannyThreshold1 = 50;
            dynamicCannyThreshold2 = 150;
        }
        Log.d(TAG, "Canny thresholds adjusted to: " + dynamicCannyThreshold1 + ", " + dynamicCannyThreshold2 + " for resolution " + frameWidth + "x" + frameHeight);

        // MIN_AREA_PERCENTAGE và MAX_AREA_PERCENTAGE đã là hằng số tỷ lệ phần trăm
        // và được tính toán động thành minAllowedArea, maxAllowedArea trong findBestQuadrilateral.
        // Do đó, không cần điều chỉnh trực tiếp ở đây.
    }

    private MatOfPoint findBestQuadrilateral(List<MatOfPoint> contours, int imageWidth, int imageHeight) {
        MatOfPoint bestQuadrilateral = null;
        double maxArea = 0;
        double totalArea = imageWidth * imageHeight;
        double minAllowedArea = totalArea * MIN_AREA_PERCENTAGE;
        double maxAllowedArea = totalArea * MAX_AREA_PERCENTAGE;

        for (MatOfPoint contour : contours) {
            MatOfPoint2f contour2f = new MatOfPoint2f(contour.toArray());
            double perimeter = Imgproc.arcLength(contour2f, true);
            MatOfPoint2f approxCurve = new MatOfPoint2f();
            Imgproc.approxPolyDP(contour2f, approxCurve, APPROX_POLY_DP_EPSILON_FACTOR * perimeter, true);

            long numVertices = approxCurve.total();
            double currentArea = Imgproc.contourArea(approxCurve);

            if (numVertices == 4 &&
                    currentArea > minAllowedArea &&
                    currentArea < maxAllowedArea) {

                if (Imgproc.isContourConvex(new MatOfPoint(approxCurve.toArray()))) {
                    double maxCosine = 0;
                    Point[] points = approxCurve.toArray();

                    for (int i = 0; i < 4; i++) {
                        Point p1 = points[i];
                        Point p2 = points[(i + 1) % 4];
                        Point p3 = points[(i + 2) % 4];

                        double cosineAngle = Math.abs(angle(p1, p2, p3));
                        maxCosine = Math.max(maxCosine, cosineAngle);
                    }

                    if (maxCosine < MIN_COSINE_ANGLE) {
                        if (currentArea > maxArea) {
                            maxArea = currentArea;
                            bestQuadrilateral = new MatOfPoint(points);
                        }
                    }
                }
            }
            contour2f.release();
            approxCurve.release();
        }
        return bestQuadrilateral;
    }

    private double angle(Point p1, Point p2, Point p3) {
        double dx1 = p1.x - p2.x;
        double dy1 = p1.y - p2.y;
        double dx2 = p3.x - p2.x;
        double dy2 = p3.y - p2.y;
        return (dx1 * dx2 + dy1 * dy2) / (Math.sqrt(dx1 * dx1 + dy1 * dy1) * Math.sqrt(dx2 * dx2 + dy2 * dy2) + 1e-10);
    }

    private MatOfPoint sortPoints(MatOfPoint pointsMat) {
        Point[] pts = pointsMat.toArray();
        Point[] rect = new Point[4];

        Arrays.sort(pts, (p1, p2) -> Double.compare(p1.y, p2.y));

        Point[] topPoints = Arrays.copyOfRange(pts, 0, 2);
        Point[] bottomPoints = Arrays.copyOfRange(pts, 2, 4);

        Arrays.sort(topPoints, (p1, p2) -> Double.compare(p1.x, p2.x));
        rect[0] = topPoints[0];
        rect[1] = topPoints[1];

        Arrays.sort(bottomPoints, (p1, p2) -> Double.compare(p1.x, p2.x));
        rect[3] = bottomPoints[0];
        rect[2] = bottomPoints[1];

        return new MatOfPoint(rect);
    }
}