package com.example.camerascanner.activitycamera;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;
import android.util.Size;
import android.view.Display;
import android.view.Surface;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
import androidx.core.content.ContextCompat;

import com.example.camerascanner.R;
import com.example.camerascanner.activitycrop.CropActivity;
import com.example.camerascanner.BaseActivity;
import com.example.camerascanner.activitypdfgroup.PDFGroupActivity;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import com.google.common.util.concurrent.ListenableFuture;

import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.imgproc.CLAHE;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CameraActivity extends BaseActivity implements AppPermissionHandler.PermissionCallbacks{

    private static final String TAG = "CameraActivity";
    private static final int REQUEST_CODE_CROP = 200;

    private PreviewView previewView;
    private CustomOverlayView customOverlayView;
    private ImageView imageView;
    private FloatingActionButton btnTakePhoto;
    private ImageButton btnSelectImage;

    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private ImageCapture imageCapture;
    private ImageAnalysis imageAnalysis;
    private ExecutorService cameraExecutor;
    private AppPermissionHandler appPermissionHandler;

    // Flag để kiểm tra xem Activity có đang bị destroy không
    private boolean isDestroyed = false;

    // --- Các hằng số và biến OpenCV ---
    private static final double CANNY_THRESHOLD1 = 40;
    private static final double CANNY_THRESHOLD2 = 120;
    private double dynamicCannyThreshold1 = CANNY_THRESHOLD1;
    private double dynamicCannyThreshold2 = CANNY_THRESHOLD2;
    private static final double APPROX_POLY_DP_EPSILON_FACTOR = 0.03;
    private static final double MIN_COSINE_ANGLE = 0.3;
    private static final double MIN_AREA_PERCENTAGE = 0.02;
    private static final double MAX_AREA_PERCENTAGE = 0.90;

    private MatOfPoint lastDetectedQuadrilateral = null;
    private int lastImageProxyWidth = 0;
    private int lastImageProxyHeight = 0;
    private int lastRotationDegrees = 0;

    private long lastDetectionTimestamp = 0L;
    private static final long QUAD_PERSISTENCE_TIMEOUT_MS = 1500;

    private int frameCount = 0;
    private static final int PROCESS_FRAME_INTERVAL = 3;
    private TabLayout tabLayoutCameraModes;
    private Uri selectedImageUri;
    private ActivityResultLauncher<String> galleryLauncher;

    // --- Các biến và hằng số mới cho chức năng tự động chụp thẻ ID ---
    private boolean isIdCardMode = false;
    private final boolean autoCaptureEnabled = true;
    private long lastAutoCaptureTime = 0L;
    private static final long AUTO_CAPTURE_COOLDOWN_MS = 3000;
    private static final double ID_CARD_ASPECT_RATIO_MIN = 1.5;
    private static final double ID_CARD_ASPECT_RATIO_MAX = 1.85;
    private int consecutiveValidFrames = 0;
    private static final int REQUIRED_CONSECUTIVE_FRAMES = 10;

    // Biến để xác định có phải từ PDFGroup không
    private boolean isFromPdfGroup = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        // Kiểm tra xem có được gọi từ PDFGroupActivity không
        Intent intent = getIntent();
        if (intent != null) {
            isFromPdfGroup = intent.getBooleanExtra("FROM_PDF_GROUP", false);
            Log.d(TAG, "CameraActivity started from PDFGroup: " + isFromPdfGroup);
        }

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

        previewView.setScaleType(PreviewView.ScaleType.FIT_CENTER);
        cameraExecutor = Executors.newSingleThreadExecutor();
        appPermissionHandler = new AppPermissionHandler(this, this);

        if (appPermissionHandler.checkCameraPermission()) {
            startCamera();
        } else {
            appPermissionHandler.requestCameraPermission();
        }

        initLaunchers();

        btnTakePhoto.setOnClickListener(v -> takePhoto());

        btnSelectImage.setOnClickListener(v -> {
            if (appPermissionHandler.checkStoragePermission()) {
                openGallery();
            } else {
                appPermissionHandler.requestStoragePermission();
            }
        });

        showCameraPreview();

        tabLayoutCameraModes = findViewById(R.id.tabLayoutCameraModes);

        tabLayoutCameraModes.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                int position = tab.getPosition();
                isIdCardMode = false;
                switch (position) {
                    case 0:
                        Log.d(TAG, "Chế độ: Quét");
                        break;
                    case 1:
                        Log.d(TAG, "Chế độ: Thẻ ID");
                        isIdCardMode = true;
                        Toast.makeText(CameraActivity.this, getString(R.string.id_mode_activated_auto_capture), Toast.LENGTH_SHORT).show();
                        break;
                }
                if (customOverlayView != null) {
                    customOverlayView.clearBoundingBox();
                    customOverlayView.invalidate();
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });

        if (tabLayoutCameraModes.getTabCount() > 0) {
            tabLayoutCameraModes.selectTab(tabLayoutCameraModes.getTabAt(0));
        }
    }

    @Override
    public void onCameraPermissionGranted() {
        if (!isDestroyed) {
            startCamera();
        }
    }

    @Override
    public void onCameraPermissionDenied() {
        if (!isDestroyed) {
            Toast.makeText(this, getString(R.string.permission_denied_function_unavailable), Toast.LENGTH_LONG).show();
            Log.w(TAG, "Quyền camera bị từ chối.");
        }
    }

    @Override
    public void onStoragePermissionGranted() {
        if (!isDestroyed) {
            openGallery();
        }
    }

    @Override
    public void onStoragePermissionDenied() {
        if (!isDestroyed) {
            Toast.makeText(this, getString(R.string.permission_denied_function_unavailable), Toast.LENGTH_LONG).show();
            Log.w(TAG, "Quyền lưu trữ bị từ chối.");
        }
    }

    private void initLaunchers() {
        galleryLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null && !isDestroyed) {
                selectedImageUri = uri;
                Log.d(TAG, "Ảnh được tải từ thư viện, URI gốc: " + selectedImageUri);
                // Chuyển sang CropActivity từ thư viện
                startCropActivity(selectedImageUri, null);
            } else if (!isDestroyed) {
                Toast.makeText(this, getString(R.string.failed_to_get_image_from_gallery), Toast.LENGTH_SHORT).show();
                Log.e(TAG, "selectedImageUri rỗng sau khi xử lý kết quả thư viện.");
            }
        });
    }

    // Các phương thức camera giữ nguyên như cũ...
    private void startCamera() {
        if (isDestroyed) {
            Log.w(TAG, "Activity is destroyed, not starting camera");
            return;
        }

        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            if (isDestroyed) {
                Log.w(TAG, "Activity is destroyed, not binding camera");
                return;
            }
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Lỗi khi bắt đầu camera: " + e.getMessage(), e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCameraUseCases(@NonNull ProcessCameraProvider cameraProvider) {
        if (isDestroyed || previewView == null) {
            Log.w(TAG, "Activity is destroyed or previewView is null, not binding camera");
            return;
        }

        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        imageAnalysis = new ImageAnalysis.Builder()
                .setResolutionSelector(new ResolutionSelector.Builder()
                        .setResolutionStrategy(new ResolutionStrategy(new Size(640, 480),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                        .build())
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(cameraExecutor, imageProxy -> {
            if (isDestroyed) {
                imageProxy.close();
                return;
            }

            Log.d(TAG, "DEBUG_DIM: ImageProxy original dimensions: " + imageProxy.getWidth() + "x" + imageProxy.getHeight() + " Rotation: " + imageProxy.getImageInfo().getRotationDegrees());

            MatOfPoint newlyDetectedQuadrilateral = null;
            Mat processedFrameForDimensions = null;

            try {
                final MatOfPoint finalQuadrilateralForOverlay;

                if (frameCount % PROCESS_FRAME_INTERVAL == 0) {
                    Pair<MatOfPoint, Mat> detectionResult = processImageFrame(imageProxy);
                    newlyDetectedQuadrilateral = detectionResult.first;
                    processedFrameForDimensions = detectionResult.second;

                    Log.d(TAG, "Đã xử lý khung hình đầy đủ. Khung: " + frameCount);

                    if (newlyDetectedQuadrilateral != null && processedFrameForDimensions != null) {
                        if (lastDetectedQuadrilateral != null) {
                            lastDetectedQuadrilateral.release();
                        }
                        lastDetectedQuadrilateral = new MatOfPoint(newlyDetectedQuadrilateral.toArray());
                        lastDetectionTimestamp = System.currentTimeMillis();

                        lastImageProxyWidth = processedFrameForDimensions.width();
                        lastImageProxyHeight = processedFrameForDimensions.height();
                        lastRotationDegrees = imageProxy.getImageInfo().getRotationDegrees();

                        finalQuadrilateralForOverlay = newlyDetectedQuadrilateral;
                        Log.d(TAG, "DEBUG_DIM: Processed Mat dimensions (from processedFrameForDimensions): " + processedFrameForDimensions.width() + "x" + processedFrameForDimensions.height());
                        Log.d(TAG, "DEBUG_DIM: Stored lastImageProxyWidth: " + lastImageProxyWidth + " lastImageProxyHeight: " + lastImageProxyHeight);

                        // Logic tự động chụp thẻ ID
                        if (isIdCardMode && autoCaptureEnabled && !isDestroyed) {
                            long currentTime = System.currentTimeMillis();
                            Point[] points = newlyDetectedQuadrilateral.toArray();
                            if (points.length == 4) {
                                MatOfPoint sortedPoints = sortPoints(new MatOfPoint(points));
                                Point[] sortedPts = sortedPoints.toArray();

                                double widthTop = Math.sqrt(Math.pow(sortedPts[0].x - sortedPts[1].x, 2) + Math.pow(sortedPts[0].y - sortedPts[1].y, 2));
                                double widthBottom = Math.sqrt(Math.pow(sortedPts[3].x - sortedPts[2].x, 2) + Math.pow(sortedPts[3].y - sortedPts[2].y, 2));
                                double avgWidth = (widthTop + widthBottom) / 2.0;

                                double heightLeft = Math.sqrt(Math.pow(sortedPts[0].x - sortedPts[3].x, 2) + Math.pow(sortedPts[0].y - sortedPts[3].y, 2));
                                double heightRight = Math.sqrt(Math.pow(sortedPts[1].x - sortedPts[2].x, 2) + Math.pow(sortedPts[1].y - sortedPts[2].y, 2));
                                double avgHeight = (heightLeft + heightRight) / 2.0;

                                if (avgHeight > 0) {
                                    double aspectRatio = avgWidth / avgHeight;
                                    Log.d(TAG, "Calculated Aspect Ratio: " + String.format("%.2f", aspectRatio) + " (Min: " + ID_CARD_ASPECT_RATIO_MIN + ", Max: " + ID_CARD_ASPECT_RATIO_MAX + ")");

                                    if (aspectRatio >= ID_CARD_ASPECT_RATIO_MIN && aspectRatio <= ID_CARD_ASPECT_RATIO_MAX || aspectRatio >= 1/ID_CARD_ASPECT_RATIO_MAX && aspectRatio <= 1/ID_CARD_ASPECT_RATIO_MIN) {
                                        consecutiveValidFrames++;
                                        Log.d(TAG, "Valid frame. Consecutive: " + consecutiveValidFrames + "/" + REQUIRED_CONSECUTIVE_FRAMES);

                                        if (consecutiveValidFrames >= REQUIRED_CONSECUTIVE_FRAMES) {
                                            if (currentTime - lastAutoCaptureTime > AUTO_CAPTURE_COOLDOWN_MS) {
                                                Log.d(TAG, "Phát hiện thẻ ID hợp lệ liên tục. Đang tự động chụp...");
                                                runOnUiThread(() -> {
                                                    if (!isDestroyed) {
                                                        Toast.makeText(CameraActivity.this, "Tự động chụp thẻ ID!", Toast.LENGTH_SHORT).show();
                                                        takePhoto();
                                                    }
                                                });
                                                lastAutoCaptureTime = currentTime;
                                                consecutiveValidFrames = 0;
                                            }
                                        }
                                    } else {
                                        consecutiveValidFrames = 0;
                                        Log.d(TAG, "Aspect ratio out of range. Resetting consecutive frames.");
                                    }
                                } else {
                                    consecutiveValidFrames = 0;
                                    Log.d(TAG, "AvgHeight is zero. Resetting consecutive frames.");
                                }
                                sortedPoints.release();
                            } else {
                                consecutiveValidFrames = 0;
                                Log.d(TAG, "Not a 4-point quadrilateral. Resetting consecutive frames.");
                            }
                        } else {
                            consecutiveValidFrames = 0;
                        }
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
                    if (!isDestroyed && customOverlayView != null && previewView != null) {
                        if (finalQuadrilateralForOverlay != null) {
                            int effectiveImageWidth = lastImageProxyWidth;
                            int effectiveImageHeight = lastImageProxyHeight;

                            customOverlayView.setQuadrilateral(
                                    CustomOverlayView.scalePointsToOverlayView(
                                            finalQuadrilateralForOverlay,
                                            effectiveImageWidth,
                                            effectiveImageHeight,
                                            previewView.getWidth(),
                                            previewView.getHeight()
                                    )
                            );
                        } else {
                            customOverlayView.clearBoundingBox();
                        }
                        customOverlayView.invalidate();
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Error in image analysis: " + e.getMessage(), e);
            } finally {
                imageProxy.close();
                if (newlyDetectedQuadrilateral != null) {
                    newlyDetectedQuadrilateral.release();
                }
                if (processedFrameForDimensions != null) {
                    processedFrameForDimensions.release();
                }
                frameCount++;
            }
        });

        int targetRotation = Surface.ROTATION_0;
        try {
            Display display = previewView.getDisplay();
            if (display != null) {
                targetRotation = display.getRotation();
            } else {
                Log.w(TAG, "Display is null, using default rotation");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting display rotation: " + e.getMessage(), e);
        }

        imageCapture = new ImageCapture.Builder()
                .setTargetRotation(targetRotation)
                .build();
        Log.d("CameraActivity", "ImageCapture target rotation: " + imageCapture.getTargetRotation());

        cameraProvider.unbindAll();

        try {
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis, imageCapture);
        } catch (Exception e) {
            Log.e(TAG, "Lỗi khi liên kết các trường hợp sử dụng camera: " + e.getMessage(), e);
        }
    }

    private void takePhoto() {
        if (imageCapture == null || isDestroyed) {
            Log.e(TAG, "ImageCapture chưa được khởi tạo hoặc Activity đã bị destroy.");
            if (!isDestroyed) {
                Toast.makeText(this, getString(R.string.camera_not_ready), Toast.LENGTH_SHORT).show();
            }
            return;
        }

        File photoFile = new File(getCacheDir(), "captured_image_" + System.currentTimeMillis() + ".jpeg");

        ImageCapture.OutputFileOptions outputFileOptions =
                new ImageCapture.OutputFileOptions.Builder(photoFile).build();

        imageCapture.takePicture(outputFileOptions, ContextCompat.getMainExecutor(this), new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(ImageCapture.OutputFileResults outputFileResults) {
                if (isDestroyed) return;

                Uri savedUri = outputFileResults.getSavedUri();
                if (savedUri != null) {
                    Toast.makeText(CameraActivity.this, getString(R.string.photo_captured_saved), Toast.LENGTH_SHORT).show();
                    Log.d(TAG, "Ảnh đã chụp và lưu: " + savedUri);

                    // LUÔN chuyển sang CropActivity
                    startCropActivity(savedUri, lastDetectedQuadrilateral);

                } else {
                    Toast.makeText(CameraActivity.this, getString(R.string.failed_to_save_image), Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Không thể lưu ảnh: Uri null");
                }
            }

            @Override
            public void onError(ImageCaptureException exception) {
                if (isDestroyed) return;

                Log.e(TAG, "Lỗi khi chụp ảnh: " + exception.getMessage(), exception);
                Toast.makeText(CameraActivity.this, "Lỗi khi chụp ảnh: " + exception.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void startCropActivity(Uri imageUri, MatOfPoint detectedQuadrilateral) {
        if (isDestroyed) return;

        Intent cropIntent = new Intent(CameraActivity.this, CropActivity.class);
        cropIntent.putExtra("imageUri", imageUri.toString());

        // Truyền thông tin về nguồn gọi
        if (isFromPdfGroup) {
            cropIntent.putExtra("FROM_PDF_GROUP", true);
            Log.d(TAG, "Starting CropActivity from PDFGroup flow");
        } else {
            cropIntent.putExtra("FROM_PDF_GROUP", false);
            Log.d(TAG, "Starting CropActivity from single image flow");
        }

        // Nếu có khung phát hiện từ camera, truyền các điểm
        if (detectedQuadrilateral != null && !detectedQuadrilateral.empty()) {
            Point[] points = detectedQuadrilateral.toArray();
            if (points.length == 4) {
                float[] quadPoints = new float[8]; // 4 điểm x 2 tọa độ (x,y)
                for (int i = 0; i < 4; i++) {
                    quadPoints[i * 2] = (float) points[i].x;
                    quadPoints[i * 2 + 1] = (float) points[i].y;
                }
                cropIntent.putExtra("detectedQuadrilateral", quadPoints);
                cropIntent.putExtra("originalImageWidth", lastImageProxyWidth);
                cropIntent.putExtra("originalImageHeight", lastImageProxyHeight);
            }
        }

        startActivityForResult(cropIntent, REQUEST_CODE_CROP);
    }

    private void showCameraPreview() {
        if (isDestroyed) return;

        previewView.setVisibility(View.VISIBLE);
        customOverlayView.setVisibility(View.VISIBLE);
        imageView.setVisibility(View.GONE);
        btnTakePhoto.setVisibility(View.VISIBLE);
        btnSelectImage.setVisibility(View.VISIBLE);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        }
    }

    private void openGallery() {
        if (isDestroyed) return;
        galleryLauncher.launch("image/*");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_CROP) {
            if (resultCode == RESULT_OK && data != null) {
                String processedImageUriString = data.getStringExtra("processedImageUri");

                if (processedImageUriString != null) {
                    if (isFromPdfGroup) {
                        // Nếu từ PDFGroup, trả kết quả về PDFGroup
                        Intent resultIntent = new Intent();
                        resultIntent.putExtra("processedImageUri", processedImageUriString);
                        setResult(RESULT_OK, resultIntent);
                        finish();
                    } else {
                        // Nếu không từ PDFGroup, chuyển sang PDFGroup
                        Intent pdfGroupIntent = new Intent(this, PDFGroupActivity.class);
                        pdfGroupIntent.putExtra("processedImageUri", processedImageUriString);
                        startActivity(pdfGroupIntent);
                        finish();
                    }
                } else {
                    Toast.makeText(this, getString(R.string.no_image), Toast.LENGTH_SHORT).show();
                }
            } else {
                // Người dùng hủy crop
                if (isFromPdfGroup) {
                    setResult(RESULT_CANCELED);
                    finish();
                }
                // Nếu không từ PDFGroup, tiếp tục ở camera
            }
        }
    }

    @Override
    protected void onDestroy() {
        isDestroyed = true;
        super.onDestroy();

        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }

        if (lastDetectedQuadrilateral != null) {
            lastDetectedQuadrilateral.release();
            lastDetectedQuadrilateral = null;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (cameraProviderFuture != null) {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                cameraProvider.unbindAll();
            } catch (Exception e) {
                Log.e(TAG, "Error unbinding camera in onPause: " + e.getMessage(), e);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!isDestroyed && appPermissionHandler.checkCameraPermission()) {
            startCamera();
        }
    }

    // Các phương thức OpenCV giữ nguyên...
    @androidx.annotation.OptIn(markerClass = androidx.camera.core.ExperimentalGetImage.class)
    private Pair<MatOfPoint, Mat> processImageFrame(ImageProxy imageProxy) {
        if (isDestroyed) {
            return new Pair<>(null, null);
        }

        Mat gray = null;
        Mat edges = null;
        Mat hierarchy = null;
        List<MatOfPoint> contours = null;
        MatOfPoint bestQuadrilateral = null;
        Mat matForDimensionStorage = null;

        try {
            ImageProxy.PlaneProxy yPlane = imageProxy.getPlanes()[0];
            ByteBuffer yBuffer = yPlane.getBuffer();
            int yRowStride = yPlane.getRowStride();
            int yPixelStride = yPlane.getPixelStride();

            int originalFrameWidth = imageProxy.getWidth();
            int originalFrameHeight = imageProxy.getHeight();
            int rotationDegrees = imageProxy.getImageInfo().getRotationDegrees();

            adjustOpenCVParametersForResolution(originalFrameWidth, originalFrameHeight);

            gray = new Mat(originalFrameHeight, originalFrameWidth, CvType.CV_8UC1);

            byte[] data = new byte[originalFrameWidth * originalFrameHeight];
            int bufferOffset = 0;

            for (int row = 0; row < originalFrameHeight; ++row) {
                int bytesToReadInRow = originalFrameWidth;
                if (yBuffer.remaining() < bytesToReadInRow) {
                    Log.e(TAG, "BufferUnderflow: Not enough bytes for row " + row + ". Remaining: " + yBuffer.remaining() + ", Needed: " + bytesToReadInRow + ". Skipping frame.");
                    return new Pair<>(null, null);
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

            Mat processedGray = gray;
            boolean needsRotation = (rotationDegrees == 90 || rotationDegrees == 270);

            if (needsRotation) {
                Mat rotatedGray = new Mat();
                Core.transpose(gray, rotatedGray);
                Core.flip(rotatedGray, rotatedGray, (rotationDegrees == 90) ? 1 : 0);
                processedGray = rotatedGray;
            }

            int finalProcessedWidth = processedGray.width();
            int finalProcessedHeight = processedGray.height();

            Log.d(TAG, "DEBUG_ROTATION: Original " + originalFrameWidth + "x" + originalFrameHeight +
                    " -> Processed " + finalProcessedWidth + "x" + finalProcessedHeight +
                    " (rotation: " + rotationDegrees + "°)");

            Imgproc.medianBlur(processedGray, processedGray, 3);
            Imgproc.GaussianBlur(processedGray, processedGray, new org.opencv.core.Size(7,7), 0);

            CLAHE clahe = Imgproc.createCLAHE(2.0, new org.opencv.core.Size(8, 8));
            clahe.apply(processedGray, processedGray);

            edges = new Mat();
            Imgproc.Canny(processedGray, edges, dynamicCannyThreshold1, dynamicCannyThreshold2);

            Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new org.opencv.core.Size(3, 3));
            Imgproc.dilate(edges, edges, kernel);
            kernel.release();

            contours = new ArrayList<>();
            hierarchy = new Mat();
            Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE);
            bestQuadrilateral = findBestQuadrilateral(contours, finalProcessedWidth, finalProcessedHeight);

            if (bestQuadrilateral != null && !bestQuadrilateral.empty()) {
                matForDimensionStorage = processedGray.clone();
            }

            if (needsRotation && processedGray != gray) {
                if (matForDimensionStorage == null) {
                    processedGray.release();
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Error processing image frame: " + e.getMessage(), e);
            return new Pair<>(null, null);
        } finally {
            if (gray != null) gray.release();
            if (edges != null) edges.release();
            if (hierarchy != null) hierarchy.release();
            if (contours != null) {
                for (MatOfPoint m : contours) {
                    m.release();
                }
            }
        }

        return new Pair<>(bestQuadrilateral, matForDimensionStorage);
    }

    private void adjustOpenCVParametersForResolution(int frameWidth, int frameHeight) {
        if (frameWidth <= 480) {
            dynamicCannyThreshold1 = 20;
            dynamicCannyThreshold2 = 60;
        } else if (frameWidth <= 640) {
            dynamicCannyThreshold1 = 30;
            dynamicCannyThreshold2 = 90;
        } else if (frameWidth <= 1280) {
            dynamicCannyThreshold1 = 40;
            dynamicCannyThreshold2 = 120;
        } else {
            dynamicCannyThreshold1 = 50;
            dynamicCannyThreshold2 = 150;
        }
        Log.d(TAG, "Canny thresholds adjusted to: " + dynamicCannyThreshold1 + ", " + dynamicCannyThreshold2 + " for resolution " + frameWidth + "x" + frameHeight);
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