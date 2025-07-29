package com.example.camerascanner.activitycamera;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Pair;
import android.util.Size;
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
import com.example.camerascanner.activitycamera.AppPermissionHandler;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import com.google.common.util.concurrent.ListenableFuture;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.imgproc.CLAHE;
import org.opencv.imgproc.Imgproc;

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

public class CameraActivity extends AppCompatActivity implements AppPermissionHandler.PermissionCallbacks {

    private static final String TAG = "CameraActivity";
    private static final int REQUEST_CODE_IMAGE_PREVIEW = 200;

    // UI Components
    private PreviewView previewView;
    private CustomOverlayView customOverlayView;
    private ImageView imageView;
    private FloatingActionButton btnTakePhoto;
    private ImageButton btnSelectImage;
    private TabLayout tabLayoutCameraModes;

    // Camera components
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private ImageCapture imageCapture;
    private ImageAnalysis imageAnalysis;
    private ExecutorService cameraExecutor;
    private AppPermissionHandler appPermissionHandler;

    // OpenCV constants
    private static final double CANNY_THRESHOLD1 = 40;
    private static final double CANNY_THRESHOLD2 = 120;
    private static final double APPROX_POLY_DP_EPSILON_FACTOR = 0.03;
    private static final double MIN_COSINE_ANGLE = 0.2;
    private static final double MIN_AREA_PERCENTAGE = 0.02;
    private static final double MAX_AREA_PERCENTAGE = 0.90;

    // Dynamic OpenCV parameters
    private double dynamicCannyThreshold1 = CANNY_THRESHOLD1;
    private double dynamicCannyThreshold2 = CANNY_THRESHOLD2;

    // Detection state
    private MatOfPoint lastDetectedQuadrilateral = null;
    private int lastImageProxyWidth = 0;
    private int lastImageProxyHeight = 0;
    private int lastRotationDegrees = 0;
    private long lastDetectionTimestamp = 0L;
    private static final long QUAD_PERSISTENCE_TIMEOUT_MS = 1500;

    // Frame processing
    private int frameCount = 0;
    private static final int PROCESS_FRAME_INTERVAL = 3;

    // ID Card auto capture
    private boolean isIdCardMode = false;
    private boolean autoCaptureEnabled = true;
    private long lastAutoCaptureTime = 0L;
    private static final long AUTO_CAPTURE_COOLDOWN_MS = 3000;
    private static final double ID_CARD_ASPECT_RATIO_MIN = 1.5;
    private static final double ID_CARD_ASPECT_RATIO_MAX = 1.85;
    private int consecutiveValidFrames = 0;
    private static final int REQUIRED_CONSECUTIVE_FRAMES = 10;

    // Gallery and intents
    private Uri selectedImageUri;
    private ActivityResultLauncher<String> galleryLauncher;
    private ActivityResultLauncher<Intent> imagePreviewLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        if (!initializeOpenCV()) {
            return;
        }

        initializeViews();
        initializeCamera();
        initializePermissions();
        initializeLaunchers();
        setupEventListeners();
        setupTabLayout();
        showCameraPreview();
    }

    // ========== INITIALIZATION METHODS ==========

    private boolean initializeOpenCV() {
        if (!OpenCVLoader.initLocal()) {
            Log.e(TAG, "OpenCV initialization failed!");
            Toast.makeText(this, "OpenCV failed to load!", Toast.LENGTH_LONG).show();
            return false;
        } else {
            Log.d(TAG, "OpenCV initialization successful!");
            return true;
        }
    }

    private void initializeViews() {
        previewView = findViewById(R.id.previewView);
        customOverlayView = findViewById(R.id.customOverlayView);
        imageView = findViewById(R.id.imageView);
        btnTakePhoto = findViewById(R.id.btnTakePhoto);
        btnSelectImage = findViewById(R.id.btnSelectImage);
        tabLayoutCameraModes = findViewById(R.id.tabLayoutCameraModes);

        previewView.setScaleType(PreviewView.ScaleType.FIT_CENTER);
    }

    private void initializeCamera() {
        cameraExecutor = Executors.newSingleThreadExecutor();
    }

    private void initializePermissions() {
        appPermissionHandler = new AppPermissionHandler(this, this);

        if (appPermissionHandler.checkCameraPermission()) {
            startCamera();
        } else {
            appPermissionHandler.requestCameraPermission();
        }
    }

    private void initializeLaunchers() {
        galleryLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) {
                handleGalleryResult(uri);
            } else {
                showToast(getString(R.string.failed_to_get_image_from_gallery));
                Log.e(TAG, "selectedImageUri rỗng sau khi xử lý kết quả thư viện.");
            }
        });
    }

    private void setupEventListeners() {
        btnTakePhoto.setOnClickListener(v -> takePhoto());

        btnSelectImage.setOnClickListener(v -> {
            if (appPermissionHandler.checkStoragePermission()) {
                openGallery();
            } else {
                appPermissionHandler.requestStoragePermission();
            }
        });
    }

    private void setupTabLayout() {
        tabLayoutCameraModes.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                handleTabSelection(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                // No special handling needed
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                // No special handling needed
            }
        });

        if (tabLayoutCameraModes.getTabCount() > 0) {
            tabLayoutCameraModes.selectTab(tabLayoutCameraModes.getTabAt(0));
        }
    }

    // ========== PERMISSION CALLBACK METHODS ==========

    @Override
    public void onCameraPermissionGranted() {
        startCamera();
    }

    @Override
    public void onCameraPermissionDenied() {
        showToast(getString(R.string.permission_denied_function_unavailable));
        Log.w(TAG, "Quyền camera bị từ chối.");
    }

    @Override
    public void onStoragePermissionGranted() {
        openGallery();
    }

    @Override
    public void onStoragePermissionDenied() {
        showToast(getString(R.string.permission_denied_function_unavailable));
        Log.w(TAG, "Quyền lưu trữ bị từ chối.");
    }

    // ========== CAMERA SETUP METHODS ==========

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
        Preview preview = createPreview();
        CameraSelector cameraSelector = createCameraSelector();
        ImageAnalysis imageAnalysis = createImageAnalysis();
        ImageCapture imageCapture = createImageCapture();

        this.imageAnalysis = imageAnalysis;
        this.imageCapture = imageCapture;

        cameraProvider.unbindAll();

        try {
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis, imageCapture);
        } catch (Exception e) {
            Log.e(TAG, "Lỗi khi liên kết các trường hợp sử dụng camera: " + e.getMessage(), e);
        }
    }

    private Preview createPreview() {
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());
        return preview;
    }

    private CameraSelector createCameraSelector() {
        return new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();
    }

    private ImageAnalysis createImageAnalysis() {
        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setResolutionSelector(new ResolutionSelector.Builder()
                        .setResolutionStrategy(new ResolutionStrategy(new Size(640, 480),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                        .build())
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(cameraExecutor, this::analyzeImage);
        return imageAnalysis;
    }

    private ImageCapture createImageCapture() {
        return new ImageCapture.Builder()
                .setTargetRotation(previewView.getDisplay().getRotation())
                .build();
    }

    // ========== IMAGE ANALYSIS METHODS ==========

    private void analyzeImage(ImageProxy imageProxy) {
        Log.d(TAG, "DEBUG_DIM: ImageProxy original dimensions: " + imageProxy.getWidth() + "x" + imageProxy.getHeight() + " Rotation: " + imageProxy.getImageInfo().getRotationDegrees());

        MatOfPoint newlyDetectedQuadrilateral = null;
        Mat processedFrameForDimensions = null;

        try {
            final MatOfPoint finalQuadrilateralForOverlay;

            if (shouldProcessFrame()) {
                Pair<MatOfPoint, Mat> detectionResult = processImageFrame(imageProxy);
                newlyDetectedQuadrilateral = detectionResult.first;
                processedFrameForDimensions = detectionResult.second;

                Log.d(TAG, "Đã xử lý khung hình đầy đủ. Khung: " + frameCount);

                if (newlyDetectedQuadrilateral != null && processedFrameForDimensions != null) {
                    updateLastDetectedQuadrilateral(newlyDetectedQuadrilateral, processedFrameForDimensions, imageProxy);
                    finalQuadrilateralForOverlay = newlyDetectedQuadrilateral;

                    handleIdCardAutoCapture(newlyDetectedQuadrilateral);
                } else {
                    finalQuadrilateralForOverlay = handleNoDetection();
                }
            } else {
                finalQuadrilateralForOverlay = handleSkippedFrame();
            }

            updateOverlayOnMainThread(finalQuadrilateralForOverlay);

        } catch (Exception e) {
            Log.e(TAG, "Error in image analysis: " + e.getMessage(), e);
        } finally {
            cleanupImageAnalysis(imageProxy, newlyDetectedQuadrilateral, processedFrameForDimensions);
        }
    }

    private boolean shouldProcessFrame() {
        return frameCount % PROCESS_FRAME_INTERVAL == 0;
    }

    private void updateLastDetectedQuadrilateral(MatOfPoint detected, Mat processed, ImageProxy imageProxy) {
        if (lastDetectedQuadrilateral != null) {
            lastDetectedQuadrilateral.release();
        }
        lastDetectedQuadrilateral = new MatOfPoint(detected.toArray());
        lastDetectionTimestamp = System.currentTimeMillis();

        lastImageProxyWidth = processed.width();
        lastImageProxyHeight = processed.height();
        lastRotationDegrees = imageProxy.getImageInfo().getRotationDegrees();

        Log.d(TAG, "DEBUG_DIM: Processed Mat dimensions: " + processed.width() + "x" + processed.height());
        Log.d(TAG, "DEBUG_DIM: Stored dimensions: " + lastImageProxyWidth + "x" + lastImageProxyHeight);
    }

    private MatOfPoint handleNoDetection() {
        if (isQuadrilateralExpired()) {
            releaseLastDetectedQuadrilateral();
        }
        return lastDetectedQuadrilateral;
    }

    private MatOfPoint handleSkippedFrame() {
        if (isQuadrilateralExpired()) {
            Log.d(TAG, "lastDetectedQuadrilateral đã hết thời gian chờ trên khung bị bỏ qua. Giải phóng và đặt là null.");
            releaseLastDetectedQuadrilateral();
        }
        Log.d(TAG, "Bỏ qua xử lý khung hình đầy đủ. Khung: " + frameCount + ". Hiển thị khung cũ nếu có.");
        return lastDetectedQuadrilateral;
    }

    private boolean isQuadrilateralExpired() {
        return lastDetectedQuadrilateral != null &&
                (System.currentTimeMillis() - lastDetectionTimestamp > QUAD_PERSISTENCE_TIMEOUT_MS);
    }

    private void releaseLastDetectedQuadrilateral() {
        if (lastDetectedQuadrilateral != null) {
            lastDetectedQuadrilateral.release();
            lastDetectedQuadrilateral = null;
        }
    }

    private void updateOverlayOnMainThread(MatOfPoint quadrilateral) {
        runOnUiThread(() -> {
            if (quadrilateral != null) {
                updateOverlayWithQuadrilateral(quadrilateral);
            } else {
                customOverlayView.clearBoundingBox();
            }
            customOverlayView.invalidate();
        });
    }

    private void updateOverlayWithQuadrilateral(MatOfPoint quadrilateral) {
        int effectiveImageWidth = lastImageProxyWidth;
        int effectiveImageHeight = lastImageProxyHeight;

        customOverlayView.setQuadrilateral(
                CustomOverlayView.scalePointsToOverlayView(
                        quadrilateral,
                        effectiveImageWidth,
                        effectiveImageHeight,
                        previewView.getWidth(),
                        previewView.getHeight()
                )
        );
    }

    private void cleanupImageAnalysis(ImageProxy imageProxy, MatOfPoint detected, Mat processed) {
        imageProxy.close();
        if (detected != null) {
            detected.release();
        }
        if (processed != null) {
            processed.release();
        }
        frameCount++;
    }

    // ========== ID CARD AUTO CAPTURE METHODS ==========

    private void handleIdCardAutoCapture(MatOfPoint detectedQuadrilateral) {
        if (!isIdCardMode || !autoCaptureEnabled) {
            consecutiveValidFrames = 0;
            return;
        }

        long currentTime = System.currentTimeMillis();
        Point[] points = detectedQuadrilateral.toArray();

        if (points.length == 4) {
            if (isValidIdCardAspectRatio(points)) {
                handleValidIdCardFrame(currentTime);
            } else {
                resetConsecutiveFrames("Aspect ratio out of range");
            }
        } else {
            resetConsecutiveFrames("Not a 4-point quadrilateral");
        }
    }

    private boolean isValidIdCardAspectRatio(Point[] points) {
        MatOfPoint sortedPoints = sortPoints(new MatOfPoint(points));
        Point[] sortedPts = sortedPoints.toArray();

        double avgWidth = calculateAverageWidth(sortedPts);
        double avgHeight = calculateAverageHeight(sortedPts);

        sortedPoints.release();

        if (avgHeight > 0) {
            double aspectRatio = avgWidth / avgHeight;
            Log.d(TAG, "Calculated Aspect Ratio: " + String.format("%.2f", aspectRatio) +
                    " (Min: " + ID_CARD_ASPECT_RATIO_MIN + ", Max: " + ID_CARD_ASPECT_RATIO_MAX + ")");

            return isAspectRatioValid(aspectRatio);
        } else {
            resetConsecutiveFrames("AvgHeight is zero");
            return false;
        }
    }

    private double calculateAverageWidth(Point[] sortedPts) {
        double widthTop = calculateDistance(sortedPts[0], sortedPts[1]);
        double widthBottom = calculateDistance(sortedPts[3], sortedPts[2]);
        return (widthTop + widthBottom) / 2.0;
    }

    private double calculateAverageHeight(Point[] sortedPts) {
        double heightLeft = calculateDistance(sortedPts[0], sortedPts[3]);
        double heightRight = calculateDistance(sortedPts[1], sortedPts[2]);
        return (heightLeft + heightRight) / 2.0;
    }

    private double calculateDistance(Point p1, Point p2) {
        return Math.sqrt(Math.pow(p1.x - p2.x, 2) + Math.pow(p1.y - p2.y, 2));
    }

    private boolean isAspectRatioValid(double aspectRatio) {
        return (aspectRatio >= ID_CARD_ASPECT_RATIO_MIN && aspectRatio <= ID_CARD_ASPECT_RATIO_MAX) ||
                (aspectRatio >= 1/ID_CARD_ASPECT_RATIO_MAX && aspectRatio <= 1/ID_CARD_ASPECT_RATIO_MIN);
    }

    private void handleValidIdCardFrame(long currentTime) {
        consecutiveValidFrames++;
        Log.d(TAG, "Valid frame. Consecutive: " + consecutiveValidFrames + "/" + REQUIRED_CONSECUTIVE_FRAMES);

        if (consecutiveValidFrames >= REQUIRED_CONSECUTIVE_FRAMES) {
            if (currentTime - lastAutoCaptureTime > AUTO_CAPTURE_COOLDOWN_MS) {
                performAutoCapture(currentTime);
            }
        }
    }

    private void performAutoCapture(long currentTime) {
        Log.d(TAG, "Phát hiện thẻ ID hợp lệ liên tục. Đang tự động chụp...");
        runOnUiThread(() -> {
            showToast("Tự động chụp thẻ ID!");
            takePhoto();
        });
        lastAutoCaptureTime = currentTime;
        consecutiveValidFrames = 0;
    }

    private void resetConsecutiveFrames(String reason) {
        consecutiveValidFrames = 0;
        Log.d(TAG, reason + ". Resetting consecutive frames.");
    }

    // ========== OPENCV IMAGE PROCESSING METHODS ==========

    @androidx.annotation.OptIn(markerClass = androidx.camera.core.ExperimentalGetImage.class)
    private Pair<MatOfPoint, Mat> processImageFrame(ImageProxy imageProxy) {
        Mat gray = null;
        Mat edges = null;
        Mat hierarchy = null;
        List<MatOfPoint> contours = null;
        MatOfPoint bestQuadrilateral = null;
        Mat matForDimensionStorage = null;

        try {
            // Extract Y plane data
            Pair<Mat, Integer> grayResult = extractGrayMatFromImageProxy(imageProxy);
            gray = grayResult.first;
            int rotationDegrees = grayResult.second;

            if (gray == null) {
                return new Pair<>(null, null);
            }

            // Handle rotation
            Mat processedGray = handleImageRotation(gray, rotationDegrees);

            // Log dimensions after rotation
            logProcessedDimensions(imageProxy, processedGray, rotationDegrees);

            // Apply image processing pipeline
            applyImageProcessingPipeline(processedGray);

            // Detect edges and find contours
            edges = detectEdges(processedGray);
            contours = findContours(edges, hierarchy);

            // Find best quadrilateral
            bestQuadrilateral = findBestQuadrilateral(contours, processedGray.width(), processedGray.height());

            // Prepare return values
            if (bestQuadrilateral != null && !bestQuadrilateral.empty()) {
                matForDimensionStorage = processedGray.clone();
            }

            // Cleanup rotated mat if created
            cleanupRotatedMat(gray, processedGray, matForDimensionStorage);

        } catch (Exception e) {
            Log.e(TAG, "Error processing image frame: " + e.getMessage(), e);
            return new Pair<>(null, null);
        } finally {
            cleanupProcessingMats(gray, edges, hierarchy, contours);
        }

        return new Pair<>(bestQuadrilateral, matForDimensionStorage);
    }

    private Pair<Mat, Integer> extractGrayMatFromImageProxy(ImageProxy imageProxy) {
        try {
            ImageProxy.PlaneProxy yPlane = imageProxy.getPlanes()[0];
            ByteBuffer yBuffer = yPlane.getBuffer();
            int yRowStride = yPlane.getRowStride();

            int originalFrameWidth = imageProxy.getWidth();
            int originalFrameHeight = imageProxy.getHeight();
            int rotationDegrees = imageProxy.getImageInfo().getRotationDegrees();

            adjustOpenCVParametersForResolution(originalFrameWidth, originalFrameHeight);

            Mat gray = new Mat(originalFrameHeight, originalFrameWidth, CvType.CV_8UC1);
            byte[] data = extractYPlaneData(yBuffer, yRowStride, originalFrameWidth, originalFrameHeight);

            if (data == null) {
                gray.release();
                return new Pair<>(null, rotationDegrees);
            }

            gray.put(0, 0, data);
            return new Pair<>(gray, rotationDegrees);

        } catch (Exception e) {
            Log.e(TAG, "Error extracting gray mat: " + e.getMessage(), e);
            return new Pair<>(null, 0);
        }
    }

    private byte[] extractYPlaneData(ByteBuffer yBuffer, int yRowStride, int width, int height) {
        byte[] data = new byte[width * height];
        int bufferOffset = 0;

        for (int row = 0; row < height; ++row) {
            int bytesToReadInRow = width;
            if (yBuffer.remaining() < bytesToReadInRow) {
                Log.e(TAG, "BufferUnderflow: Not enough bytes for row " + row +
                        ". Remaining: " + yBuffer.remaining() + ", Needed: " + bytesToReadInRow + ". Skipping frame.");
                return null;
            }

            yBuffer.get(data, bufferOffset, bytesToReadInRow);
            bufferOffset += bytesToReadInRow;

            int paddingBytes = yRowStride - width;
            if (paddingBytes > 0) {
                if (yBuffer.remaining() >= paddingBytes) {
                    yBuffer.position(yBuffer.position() + paddingBytes);
                } else {
                    Log.w(TAG, "Not enough buffer remaining to skip padding for row " + row +
                            ". Remaining: " + yBuffer.remaining() + ", Expected padding: " + paddingBytes +
                            ". Further rows might be misaligned.");
                    break;
                }
            }
        }
        return data;
    }

    private Mat handleImageRotation(Mat gray, int rotationDegrees) {
        boolean needsRotation = (rotationDegrees == 90 || rotationDegrees == 270);

        if (needsRotation) {
            Mat rotatedGray = new Mat();
            Core.transpose(gray, rotatedGray);
            Core.flip(rotatedGray, rotatedGray, (rotationDegrees == 90) ? 1 : 0);
            return rotatedGray;
        }

        return gray;
    }

    private void logProcessedDimensions(ImageProxy imageProxy, Mat processedGray, int rotationDegrees) {
        Log.d(TAG, "DEBUG_ROTATION: Original " + imageProxy.getWidth() + "x" + imageProxy.getHeight() +
                " -> Processed " + processedGray.width() + "x" + processedGray.height() +
                " (rotation: " + rotationDegrees + "°)");
    }

    private void applyImageProcessingPipeline(Mat mat) {
        // Step 1: Median blur to remove salt and pepper noise
        Imgproc.medianBlur(mat, mat, 3);

        // Step 2: Gaussian blur to reduce uniform noise
        Imgproc.GaussianBlur(mat, mat, new org.opencv.core.Size(3, 3), 0);

        // Step 3: Apply CLAHE for contrast enhancement
        CLAHE clahe = Imgproc.createCLAHE(2.0, new org.opencv.core.Size(8, 8));
        clahe.apply(mat, mat);
    }

    private Mat detectEdges(Mat processedGray) {
        Mat edges = new Mat();
        Imgproc.Canny(processedGray, edges, dynamicCannyThreshold1, dynamicCannyThreshold2);

        // Morphological dilation
        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new org.opencv.core.Size(3, 3));
        Imgproc.dilate(edges, edges, kernel);
        kernel.release();

        return edges;
    }

    private List<MatOfPoint> findContours(Mat edges, Mat hierarchy) {
        List<MatOfPoint> contours = new ArrayList<>();
        hierarchy = new Mat();
        Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE);
        return contours;
    }

    private void cleanupRotatedMat(Mat original, Mat processed, Mat dimensionStorage) {
        boolean needsRotation = (processed != original);
        if (needsRotation && dimensionStorage == null) {
            processed.release();
        }
    }

    private void cleanupProcessingMats(Mat gray, Mat edges, Mat hierarchy, List<MatOfPoint> contours) {
        if (gray != null) gray.release();
        if (edges != null) edges.release();
        if (hierarchy != null) hierarchy.release();
        if (contours != null) {
            for (MatOfPoint m : contours) {
                m.release();
            }
        }
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
        Log.d(TAG, "Canny thresholds adjusted to: " + dynamicCannyThreshold1 + ", " + dynamicCannyThreshold2 +
                " for resolution " + frameWidth + "x" + frameHeight);
    }

    private MatOfPoint findBestQuadrilateral(List<MatOfPoint> contours, int imageWidth, int imageHeight) {
        MatOfPoint bestQuadrilateral = null;
        double maxArea = 0;
        double totalArea = imageWidth * imageHeight;
        double minAllowedArea = totalArea * MIN_AREA_PERCENTAGE;
        double maxAllowedArea = totalArea * MAX_AREA_PERCENTAGE;

        for (MatOfPoint contour : contours) {
            MatOfPoint quadrilateral = processContourForQuadrilateral(contour, minAllowedArea, maxAllowedArea);

            if (quadrilateral != null) {
                double currentArea = Imgproc.contourArea(new MatOfPoint2f(quadrilateral.toArray()));
                if (currentArea > maxArea) {
                    if (bestQuadrilateral != null) {
                        bestQuadrilateral.release();
                    }
                    maxArea = currentArea;
                    bestQuadrilateral = quadrilateral;
                } else {
                    quadrilateral.release();
                }
            }
        }
        return bestQuadrilateral;
    }

    private MatOfPoint processContourForQuadrilateral(MatOfPoint contour, double minArea, double maxArea) {
        MatOfPoint2f contour2f = new MatOfPoint2f(contour.toArray());
        double perimeter = Imgproc.arcLength(contour2f, true);
        MatOfPoint2f approxCurve = new MatOfPoint2f();

        try {
            Imgproc.approxPolyDP(contour2f, approxCurve, APPROX_POLY_DP_EPSILON_FACTOR * perimeter, true);

            long numVertices = approxCurve.total();
            double currentArea = Imgproc.contourArea(approxCurve);

            if (isValidQuadrilateral(numVertices, currentArea, minArea, maxArea, approxCurve)) {
                return new MatOfPoint(approxCurve.toArray());
            }

        } finally {
            contour2f.release();
            approxCurve.release();
        }

        return null;
    }

    private boolean isValidQuadrilateral(long numVertices, double area, double minArea, double maxArea, MatOfPoint2f approxCurve) {
        if (numVertices != 4 || area <= minArea || area >= maxArea) {
            return false;
        }

        if (!Imgproc.isContourConvex(new MatOfPoint(approxCurve.toArray()))) {
            return false;
        }

        return hasValidCornerAngles(approxCurve.toArray());
    }

    private boolean hasValidCornerAngles(Point[] points) {
        double maxCosine = 0;

        for (int i = 0; i < 4; i++) {
            Point p1 = points[i];
            Point p2 = points[(i + 1) % 4];
            Point p3 = points[(i + 2) % 4];

            double cosineAngle = Math.abs(angle(p1, p2, p3));
            maxCosine = Math.max(maxCosine, cosineAngle);
        }

        return maxCosine < MIN_COSINE_ANGLE;
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

    // ========== UI INTERACTION METHODS ==========

    private void handleTabSelection(int position) {
        isIdCardMode = false;
        switch (position) {
            case 0:
                Log.d(TAG, "Chế độ: Quét");
                break;
            case 1:
                Log.d(TAG, "Chế độ: Thẻ ID");
                isIdCardMode = true;
                showToast("Đã chuyển sang chế độ Thẻ ID. Tự động chụp nếu phát hiện.");
                break;
        }
        clearOverlay();
    }

    private void clearOverlay() {
        customOverlayView.clearBoundingBox();
        customOverlayView.invalidate();
    }

    private void handleGalleryResult(Uri uri) {
        selectedImageUri = uri;
        Log.d(TAG, "Ảnh được tải từ thư viện, URI gốc: " + selectedImageUri);
        startCropActivity(selectedImageUri, null);
    }

    // ========== PHOTO CAPTURE METHODS ==========

    private void takePhoto() {
        if (imageCapture == null) {
            Log.e(TAG, "ImageCapture chưa được khởi tạo.");
            showToast(getString(R.string.camera_not_ready));
            return;
        }

        File photoFile = createPhotoFile();
        ImageCapture.OutputFileOptions outputFileOptions =
                new ImageCapture.OutputFileOptions.Builder(photoFile).build();

        imageCapture.takePicture(outputFileOptions, ContextCompat.getMainExecutor(this),
                new PhotoCaptureCallback());
    }

    private File createPhotoFile() {
        return new File(getCacheDir(), "captured_image_" + System.currentTimeMillis() + ".jpeg");
    }

    private class PhotoCaptureCallback implements ImageCapture.OnImageSavedCallback {
        @Override
        public void onImageSaved(ImageCapture.OutputFileResults outputFileResults) {
            Uri savedUri = outputFileResults.getSavedUri();
            if (savedUri != null) {
                handlePhotoSaved(savedUri);
            } else {
                handlePhotoSaveError("Uri null");
            }
        }

        @Override
        public void onError(ImageCaptureException exception) {
            handlePhotoCaptureError(exception);
        }
    }

    private void handlePhotoSaved(Uri savedUri) {
        showToast(getString(R.string.photo_captured_saved));
        Log.d(TAG, "Ảnh đã chụp và lưu: " + savedUri);
        startCropActivity(savedUri, lastDetectedQuadrilateral);
    }

    private void handlePhotoSaveError(String error) {
        showToast(getString(R.string.failed_to_save_image));
        Log.e(TAG, "Không thể lưu ảnh: " + error);
    }

    private void handlePhotoCaptureError(ImageCaptureException exception) {
        Log.e(TAG, "Lỗi khi chụp ảnh: " + exception.getMessage(), exception);
        showToast("Lỗi khi chụp ảnh: " + exception.getMessage());
    }

    // ========== NAVIGATION METHODS ==========

    private void startCropActivity(Uri imageUri, MatOfPoint detectedQuadrilateral) {
        Intent cropIntent = createCropIntent(imageUri, detectedQuadrilateral);
        startActivity(cropIntent);
    }

    private Intent createCropIntent(Uri imageUri, MatOfPoint detectedQuadrilateral) {
        Intent cropIntent = new Intent(CameraActivity.this, CropActivity.class);
        cropIntent.putExtra("imageUri", imageUri.toString());

        if (detectedQuadrilateral != null && !detectedQuadrilateral.empty()) {
            addQuadrilateralToIntent(cropIntent, detectedQuadrilateral);
        }

        return cropIntent;
    }

    private void addQuadrilateralToIntent(Intent intent, MatOfPoint detectedQuadrilateral) {
        Point[] points = detectedQuadrilateral.toArray();
        if (points.length == 4) {
            float[] quadPoints = convertPointsToFloatArray(points);
            intent.putExtra("detectedQuadrilateral", quadPoints);
            intent.putExtra("originalImageWidth", lastImageProxyWidth);
            intent.putExtra("originalImageHeight", lastImageProxyHeight);
        }
    }

    private float[] convertPointsToFloatArray(Point[] points) {
        float[] quadPoints = new float[8]; // 4 points x 2 coordinates (x,y)
        for (int i = 0; i < 4; i++) {
            quadPoints[i * 2] = (float) points[i].x;
            quadPoints[i * 2 + 1] = (float) points[i].y;
        }
        return quadPoints;
    }

    // ========== UI STATE METHODS ==========

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

    private void openGallery() {
        galleryLauncher.launch("image/*");
    }

    // ========== UTILITY METHODS ==========

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    // ========== LIFECYCLE METHODS ==========

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cleanup();
    }

    private void cleanup() {
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
        releaseLastDetectedQuadrilateral();
    }
}