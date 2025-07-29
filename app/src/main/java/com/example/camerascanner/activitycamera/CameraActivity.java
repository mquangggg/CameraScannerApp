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

    // Thành phần UI
    private PreviewView previewView; // Hiển thị luồng xem trước camera
    private CustomOverlayView customOverlayView; // Lớp phủ tùy chỉnh để vẽ hình chữ nhật
    private ImageView imageView; // ImageView để hiển thị ảnh đã chụp (hiện không được sử dụng)
    private FloatingActionButton btnTakePhoto; // Nút chụp ảnh
    private ImageButton btnSelectImage; // Nút chọn ảnh từ thư viện
    private TabLayout tabLayoutCameraModes; // TabLayout để chuyển đổi chế độ camera (Scan/ID Card)

    // Thành phần Camera
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture; // Đối tượng CameraX để quản lý vòng đời camera
    private ImageCapture imageCapture; // Trường hợp sử dụng để chụp ảnh
    private ImageAnalysis imageAnalysis; // Trường hợp sử dụng để phân tích khung hình xem trước
    private ExecutorService cameraExecutor; // Executor để chạy các tác vụ camera trên một luồng nền
    private AppPermissionHandler appPermissionHandler; // Xử lý quyền ứng dụng

    // Hằng số OpenCV
    private static final double CANNY_THRESHOLD1 = 40; // Ngưỡng dưới cho thuật toán Canny
    private static final double CANNY_THRESHOLD2 = 120; // Ngưỡng trên cho thuật toán Canny
    private static final double APPROX_POLY_DP_EPSILON_FACTOR = 0.03; // Hệ số epsilon cho xấp xỉ đa giác
    private static final double MIN_COSINE_ANGLE = 0.2; // Giá trị cosine góc tối thiểu để coi là góc vuông
    private static final double MIN_AREA_PERCENTAGE = 0.02; // Tỷ lệ phần trăm diện tích tối thiểu cho hình chữ nhật được phát hiện
    private static final double MAX_AREA_PERCENTAGE = 0.90; // Tỷ lệ phần trăm diện tích tối đa cho hình chữ nhật được phát hiện

    // Tham số OpenCV động
    private double dynamicCannyThreshold1 = CANNY_THRESHOLD1; // Ngưỡng Canny dưới động
    private double dynamicCannyThreshold2 = CANNY_THRESHOLD2; // Ngưỡng Canny trên động

    // Trạng thái phát hiện
    private MatOfPoint lastDetectedQuadrilateral = null; // Hình chữ nhật cuối cùng được phát hiện
    private int lastImageProxyWidth = 0; // Chiều rộng của ImageProxy khi khung hình được phát hiện
    private int lastImageProxyHeight = 0; // Chiều cao của ImageProxy khi khung hình được phát hiện
    private int lastRotationDegrees = 0; // Độ xoay của ImageProxy khi khung hình được phát hiện
    private long lastDetectionTimestamp = 0L; // Thời gian phát hiện cuối cùng
    private static final long QUAD_PERSISTENCE_TIMEOUT_MS = 1500; // Thời gian chờ để xóa hình chữ nhật đã phát hiện

    // Xử lý khung hình
    private int frameCount = 0; // Bộ đếm khung hình
    private static final int PROCESS_FRAME_INTERVAL = 3; // Khoảng thời gian xử lý khung hình (xử lý mỗi 3 khung hình)

    // Tự động chụp thẻ ID
    private boolean isIdCardMode = false; // Cờ cho biết có đang ở chế độ thẻ ID hay không
    private boolean autoCaptureEnabled = true; // Cờ để bật/tắt tự động chụp
    private long lastAutoCaptureTime = 0L; // Thời gian tự động chụp cuối cùng
    private static final long AUTO_CAPTURE_COOLDOWN_MS = 3000; // Thời gian hồi chiêu giữa các lần tự động chụp
    private static final double ID_CARD_ASPECT_RATIO_MIN = 1.5; // Tỷ lệ khung hình tối thiểu cho thẻ ID
    private static final double ID_CARD_ASPECT_RATIO_MAX = 1.85; // Tỷ lệ khung hình tối đa cho thẻ ID
    private int consecutiveValidFrames = 0; // Đếm số khung hình hợp lệ liên tiếp
    private static final int REQUIRED_CONSECUTIVE_FRAMES = 10; // Số khung hình liên tiếp cần thiết để tự động chụp

    // Thư viện ảnh và Intent
    private Uri selectedImageUri; // URI của ảnh đã chọn từ thư viện
    private ActivityResultLauncher<String> galleryLauncher; // Launcher để mở thư viện ảnh
    private ActivityResultLauncher<Intent> imagePreviewLauncher; // Launcher để xem trước ảnh (hiện không được sử dụng)

    /**
     * Phương thức khởi tạo hoạt động.
     * Được gọi khi hoạt động được tạo lần đầu tiên.
     * @param savedInstanceState Dữ liệu trạng thái hoạt động đã lưu.
     */
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

    // ========== PHƯƠNG THỨC KHỞI TẠO ==========

    /**
     * Khởi tạo thư viện OpenCV.
     * @return true nếu OpenCV được khởi tạo thành công, false nếu ngược lại.
     */
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

    /**
     * Khởi tạo các thành phần UI bằng cách tìm kiếm ID của chúng trong layout.
     */
    private void initializeViews() {
        previewView = findViewById(R.id.previewView);
        customOverlayView = findViewById(R.id.customOverlayView);
        imageView = findViewById(R.id.imageView);
        btnTakePhoto = findViewById(R.id.btnTakePhoto);
        btnSelectImage = findViewById(R.id.btnSelectImage);
        tabLayoutCameraModes = findViewById(R.id.tabLayoutCameraModes);

        previewView.setScaleType(PreviewView.ScaleType.FIT_CENTER);
    }

    /**
     * Khởi tạo executor cho camera.
     */
    private void initializeCamera() {
        cameraExecutor = Executors.newSingleThreadExecutor();
    }

    /**
     * Khởi tạo và kiểm tra/yêu cầu quyền truy cập camera và bộ nhớ.
     */
    private void initializePermissions() {
        appPermissionHandler = new AppPermissionHandler(this, this);

        if (appPermissionHandler.checkCameraPermission()) {
            startCamera();
        } else {
            appPermissionHandler.requestCameraPermission();
        }
    }

    /**
     * Khởi tạo các ActivityResultLauncher cho việc chọn ảnh từ thư viện.
     */
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

    /**
     * Thiết lập các bộ lắng nghe sự kiện cho các nút UI.
     */
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

    /**
     * Thiết lập bộ lắng nghe cho TabLayout để xử lý việc chuyển đổi chế độ camera.
     */
    private void setupTabLayout() {
        tabLayoutCameraModes.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                handleTabSelection(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                // Không cần xử lý đặc biệt
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                // Không cần xử lý đặc biệt
            }
        });

        if (tabLayoutCameraModes.getTabCount() > 0) {
            tabLayoutCameraModes.selectTab(tabLayoutCameraModes.getTabAt(0));
        }
    }

    // ========== PHƯƠNG THỨC CALLBACK QUYỀN ==========

    /**
     * Phương thức callback được gọi khi quyền truy cập camera được cấp.
     * Bắt đầu xem trước camera.
     */
    @Override
    public void onCameraPermissionGranted() {
        startCamera();
    }

    /**
     * Phương thức callback được gọi khi quyền truy cập camera bị từ chối.
     * Hiển thị thông báo Toast cho biết chức năng không khả dụng.
     */
    @Override
    public void onCameraPermissionDenied() {
        showToast(getString(R.string.permission_denied_function_unavailable));
        Log.w(TAG, "Quyền camera bị từ chối.");
    }

    /**
     * Phương thức callback được gọi khi quyền truy cập bộ nhớ được cấp.
     * Mở thư viện để chọn ảnh.
     */
    @Override
    public void onStoragePermissionGranted() {
        openGallery();
    }

    /**
     * Phương thức callback được gọi khi quyền truy cập bộ nhớ bị từ chối.
     * Hiển thị thông báo Toast cho biết chức năng không khả dụng.
     */
    @Override
    public void onStoragePermissionDenied() {
        showToast(getString(R.string.permission_denied_function_unavailable));
        Log.w(TAG, "Quyền lưu trữ bị từ chối.");
    }

    // ========== PHƯƠNG THỨC THIẾT LẬP CAMERA ==========

    /**
     * Khởi tạo và bắt đầu camera CameraX.
     */
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

    /**
     * Liên kết các trường hợp sử dụng camera (Preview, ImageAnalysis, ImageCapture) với vòng đời camera.
     * @param cameraProvider Thể hiện ProcessCameraProvider.
     */
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

    /**
     * Tạo đối tượng Preview cho CameraX.
     * @return Đối tượng Preview đã cấu hình.
     */
    private Preview createPreview() {
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());
        return preview;
    }

    /**
     * Tạo đối tượng CameraSelector để chọn camera sau.
     * @return Đối tượng CameraSelector đã cấu hình.
     */
    private CameraSelector createCameraSelector() {
        return new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();
    }

    /**
     * Tạo và cấu hình đối tượng ImageAnalysis để phân tích khung hình camera.
     * @return Đối tượng ImageAnalysis đã cấu hình.
     */
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

    /**
     * Tạo và cấu hình đối tượng ImageCapture để chụp ảnh.
     * @return Đối tượng ImageCapture đã cấu hình.
     */
    private ImageCapture createImageCapture() {
        return new ImageCapture.Builder()
                .setTargetRotation(previewView.getDisplay().getRotation())
                .build();
    }

    // ========== PHƯƠNG THỨC PHÂN TÍCH ẢNH ==========

    /**
     * Phân tích một khung hình ảnh từ ImageProxy bằng OpenCV để phát hiện hình chữ nhật.
     * Thực hiện chỉnh sửa xoay, giảm nhiễu, phát hiện cạnh và tìm đường viền.
     * Trả về một Pair chứa hình chữ nhật tốt nhất được phát hiện (MatOfPoint) và Mat đã xử lý
     * phản ánh kích thước sau khi xoay (nếu có).
     * @param imageProxy Đối tượng ImageProxy đại diện cho khung hình camera hiện tại.
     */
    private void analyzeImage(ImageProxy imageProxy) {
        Log.d(TAG, "DEBUG_DIM: Kích thước gốc ImageProxy: " + imageProxy.getWidth() + "x" + imageProxy.getHeight() + " Xoay: " + imageProxy.getImageInfo().getRotationDegrees());

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
            Log.e(TAG, "Lỗi trong phân tích hình ảnh: " + e.getMessage(), e);
        } finally {
            cleanupImageAnalysis(imageProxy, newlyDetectedQuadrilateral, processedFrameForDimensions);
        }
    }

    /**
     * Quyết định xem khung hình hiện tại có nên được xử lý đầy đủ hay không.
     * @return true nếu khung hình nên được xử lý, false nếu ngược lại.
     */
    private boolean shouldProcessFrame() {
        return frameCount % PROCESS_FRAME_INTERVAL == 0;
    }

    /**
     * Cập nhật thông tin về hình chữ nhật được phát hiện cuối cùng.
     * @param detected Hình chữ nhật mới được phát hiện.
     * @param processed Mat đã xử lý chứa hình chữ nhật được phát hiện.
     * @param imageProxy ImageProxy ban đầu.
     */
    private void updateLastDetectedQuadrilateral(MatOfPoint detected, Mat processed, ImageProxy imageProxy) {
        if (lastDetectedQuadrilateral != null) {
            lastDetectedQuadrilateral.release();
        }
        lastDetectedQuadrilateral = new MatOfPoint(detected.toArray());
        lastDetectionTimestamp = System.currentTimeMillis();

        lastImageProxyWidth = processed.width();
        lastImageProxyHeight = processed.height();
        lastRotationDegrees = imageProxy.getImageInfo().getRotationDegrees();

        Log.d(TAG, "DEBUG_DIM: Kích thước Mat đã xử lý: " + processed.width() + "x" + processed.height());
        Log.d(TAG, "DEBUG_DIM: Kích thước đã lưu: " + lastImageProxyWidth + "x" + lastImageProxyHeight);
    }

    /**
     * Xử lý trường hợp không phát hiện được hình chữ nhật trong khung hình hiện tại.
     * @return Hình chữ nhật cuối cùng được phát hiện nếu nó chưa hết hạn, ngược lại là null.
     */
    private MatOfPoint handleNoDetection() {
        if (isQuadrilateralExpired()) {
            releaseLastDetectedQuadrilateral();
        }
        return lastDetectedQuadrilateral;
    }

    /**
     * Xử lý trường hợp bỏ qua xử lý khung hình đầy đủ.
     * @return Hình chữ nhật cuối cùng được phát hiện nếu nó chưa hết hạn, ngược lại là null.
     */
    private MatOfPoint handleSkippedFrame() {
        if (isQuadrilateralExpired()) {
            Log.d(TAG, "lastDetectedQuadrilateral đã hết thời gian chờ trên khung bị bỏ qua. Giải phóng và đặt là null.");
            releaseLastDetectedQuadrilateral();
        }
        Log.d(TAG, "Bỏ qua xử lý khung hình đầy đủ. Khung: " + frameCount + ". Hiển thị khung cũ nếu có.");
        return lastDetectedQuadrilateral;
    }

    /**
     * Kiểm tra xem hình chữ nhật được phát hiện cuối cùng đã hết hạn hay chưa.
     * @return true nếu đã hết hạn, false nếu ngược lại.
     */
    private boolean isQuadrilateralExpired() {
        return lastDetectedQuadrilateral != null &&
                (System.currentTimeMillis() - lastDetectionTimestamp > QUAD_PERSISTENCE_TIMEOUT_MS);
    }

    /**
     * Giải phóng tài nguyên của hình chữ nhật được phát hiện cuối cùng.
     */
    private void releaseLastDetectedQuadrilateral() {
        if (lastDetectedQuadrilateral != null) {
            lastDetectedQuadrilateral.release();
            lastDetectedQuadrilateral = null;
        }
    }

    /**
     * Cập nhật lớp phủ trên luồng UI chính.
     * @param quadrilateral Hình chữ nhật để vẽ trên lớp phủ, hoặc null để xóa.
     */
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

    /**
     * Cập nhật lớp phủ với hình chữ nhật đã phát hiện.
     * @param quadrilateral Hình chữ nhật MatOfPoint để cập nhật lớp phủ.
     */
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

    /**
     * Dọn dẹp tài nguyên sau khi phân tích hình ảnh.
     * @param imageProxy ImageProxy để đóng.
     * @param detected MatOfPoint đã phát hiện để giải phóng.
     * @param processed Mat đã xử lý để giải phóng.
     */
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

    // ========== PHƯƠNG THỨC TỰ ĐỘNG CHỤP THẺ ID ==========

    /**
     * Xử lý logic tự động chụp thẻ ID.
     * @param detectedQuadrilateral Hình chữ nhật MatOfPoint được phát hiện.
     */
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
                resetConsecutiveFrames("Tỷ lệ khung hình nằm ngoài phạm vi");
            }
        } else {
            resetConsecutiveFrames("Không phải hình chữ nhật 4 điểm");
        }
    }

    /**
     * Kiểm tra xem tỷ lệ khung hình của hình chữ nhật được phát hiện có hợp lệ cho thẻ ID hay không.
     * @param points Mảng các điểm của hình chữ nhật.
     * @return true nếu tỷ lệ khung hình hợp lệ, false nếu ngược lại.
     */
    private boolean isValidIdCardAspectRatio(Point[] points) {
        MatOfPoint sortedPoints = sortPoints(new MatOfPoint(points));
        Point[] sortedPts = sortedPoints.toArray();

        double avgWidth = calculateAverageWidth(sortedPts);
        double avgHeight = calculateAverageHeight(sortedPts);

        sortedPoints.release();

        if (avgHeight > 0) {
            double aspectRatio = avgWidth / avgHeight;
            Log.d(TAG, "Tỷ lệ khung hình đã tính toán: " + String.format("%.2f", aspectRatio) +
                    " (Min: " + ID_CARD_ASPECT_RATIO_MIN + ", Max: " + ID_CARD_ASPECT_RATIO_MAX + ")");

            return isAspectRatioValid(aspectRatio);
        } else {
            resetConsecutiveFrames("AvgHeight bằng không");
            return false;
        }
    }

    /**
     * Tính toán chiều rộng trung bình của hình chữ nhật.
     * @param sortedPts Mảng các điểm đã sắp xếp của hình chữ nhật.
     * @return Chiều rộng trung bình.
     */
    private double calculateAverageWidth(Point[] sortedPts) {
        double widthTop = calculateDistance(sortedPts[0], sortedPts[1]);
        double widthBottom = calculateDistance(sortedPts[3], sortedPts[2]);
        return (widthTop + widthBottom) / 2.0;
    }

    /**
     * Tính toán chiều cao trung bình của hình chữ nhật.
     * @param sortedPts Mảng các điểm đã sắp xếp của hình chữ nhật.
     * @return Chiều cao trung bình.
     */
    private double calculateAverageHeight(Point[] sortedPts) {
        double heightLeft = calculateDistance(sortedPts[0], sortedPts[3]);
        double heightRight = calculateDistance(sortedPts[1], sortedPts[2]);
        return (heightLeft + heightRight) / 2.0;
    }

    /**
     * Tính toán khoảng cách giữa hai điểm.
     * @param p1 Điểm thứ nhất.
     * @param p2 Điểm thứ hai.
     * @return Khoảng cách giữa hai điểm.
     */
    private double calculateDistance(Point p1, Point p2) {
        return Math.sqrt(Math.pow(p1.x - p2.x, 2) + Math.pow(p1.y - p2.y, 2));
    }

    /**
     * Kiểm tra xem tỷ lệ khung hình có nằm trong phạm vi hợp lệ cho thẻ ID hay không.
     * @param aspectRatio Tỷ lệ khung hình cần kiểm tra.
     * @return true nếu tỷ lệ khung hình hợp lệ, false nếu ngược lại.
     */
    private boolean isAspectRatioValid(double aspectRatio) {
        return (aspectRatio >= ID_CARD_ASPECT_RATIO_MIN && aspectRatio <= ID_CARD_ASPECT_RATIO_MAX) ||
                (aspectRatio >= 1/ID_CARD_ASPECT_RATIO_MAX && aspectRatio <= 1/ID_CARD_ASPECT_RATIO_MIN);
    }

    /**
     * Xử lý khi phát hiện khung hình thẻ ID hợp lệ liên tiếp.
     * @param currentTime Thời gian hiện tại.
     */
    private void handleValidIdCardFrame(long currentTime) {
        consecutiveValidFrames++;
        Log.d(TAG, "Khung hình hợp lệ. Liên tiếp: " + consecutiveValidFrames + "/" + REQUIRED_CONSECUTIVE_FRAMES);

        if (consecutiveValidFrames >= REQUIRED_CONSECUTIVE_FRAMES) {
            if (currentTime - lastAutoCaptureTime > AUTO_CAPTURE_COOLDOWN_MS) {
                performAutoCapture(currentTime);
            }
        }
    }

    /**
     * Thực hiện tự động chụp ảnh.
     * @param currentTime Thời gian hiện tại.
     */
    private void performAutoCapture(long currentTime) {
        Log.d(TAG, "Phát hiện thẻ ID hợp lệ liên tục. Đang tự động chụp...");
        runOnUiThread(() -> {
            showToast("Tự động chụp thẻ ID!");
            takePhoto();
        });
        lastAutoCaptureTime = currentTime;
        consecutiveValidFrames = 0;
    }

    /**
     * Đặt lại bộ đếm khung hình liên tiếp.
     * @param reason Lý do đặt lại.
     */
    private void resetConsecutiveFrames(String reason) {
        consecutiveValidFrames = 0;
        Log.d(TAG, reason + ". Đặt lại khung hình liên tiếp.");
    }

    // ========== PHƯƠNG THỨC XỬ LÝ ẢNH OPENCV ==========

    /**
     * Xử lý một khung hình ảnh từ ImageProxy bằng OpenCV để phát hiện hình chữ nhật.
     * Thực hiện chỉnh sửa xoay, giảm nhiễu, phát hiện cạnh và tìm đường viền.
     * Trả về một Pair chứa hình chữ nhật tốt nhất được phát hiện (MatOfPoint) và Mat đã xử lý
     * phản ánh kích thước sau khi xoay (nếu có).
     * @param imageProxy Đối tượng ImageProxy đại diện cho khung hình camera hiện tại.
     * @return Một Pair của MatOfPoint (hình chữ nhật tốt nhất) và Mat (khung hình đã xử lý cho kích thước), hoặc null nếu không phát hiện được.
     */
    @androidx.annotation.OptIn(markerClass = androidx.camera.core.ExperimentalGetImage.class)
    private Pair<MatOfPoint, Mat> processImageFrame(ImageProxy imageProxy) {
        Mat gray = null;
        Mat edges = null;
        Mat hierarchy = null;
        List<MatOfPoint> contours = null;
        MatOfPoint bestQuadrilateral = null;
        Mat matForDimensionStorage = null;

        try {
            // Trích xuất dữ liệu mặt phẳng Y
            Pair<Mat, Integer> grayResult = extractGrayMatFromImageProxy(imageProxy);
            gray = grayResult.first;
            int rotationDegrees = grayResult.second;

            if (gray == null) {
                return new Pair<>(null, null);
            }

            // Xử lý xoay
            Mat processedGray = handleImageRotation(gray, rotationDegrees);

            // Ghi nhật ký kích thước sau khi xoay
            logProcessedDimensions(imageProxy, processedGray, rotationDegrees);

            // Áp dụng quy trình xử lý hình ảnh
            applyImageProcessingPipeline(processedGray);

            // Phát hiện cạnh và tìm đường viền
            edges = detectEdges(processedGray);
            contours = findContours(edges, hierarchy);

            // Tìm hình chữ nhật tốt nhất
            bestQuadrilateral = findBestQuadrilateral(contours, processedGray.width(), processedGray.height());

            // Chuẩn bị các giá trị trả về
            if (bestQuadrilateral != null && !bestQuadrilateral.empty()) {
                matForDimensionStorage = processedGray.clone();
            }

            // Dọn dẹp mat đã xoay nếu được tạo
            cleanupRotatedMat(gray, processedGray, matForDimensionStorage);

        } catch (Exception e) {
            Log.e(TAG, "Lỗi khi xử lý khung hình ảnh: " + e.getMessage(), e);
            return new Pair<>(null, null);
        } finally {
            cleanupProcessingMats(gray, edges, hierarchy, contours);
        }

        return new Pair<>(bestQuadrilateral, matForDimensionStorage);
    }

    /**
     * Trích xuất dữ liệu mặt phẳng Y (đen trắng) từ ImageProxy thành Mat.
     * @param imageProxy ImageProxy nguồn.
     * @return Một Pair chứa Mat đen trắng và độ xoay của hình ảnh, hoặc null nếu lỗi.
     */
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
            Log.e(TAG, "Lỗi khi trích xuất gray mat: " + e.getMessage(), e);
            return new Pair<>(null, 0);
        }
    }

    /**
     * Trích xuất dữ liệu mặt phẳng Y (độ sáng) từ ByteBuffer.
     * @param yBuffer ByteBuffer chứa dữ liệu mặt phẳng Y.
     * @param yRowStride Số byte cho mỗi hàng trong bộ đệm Y.
     * @param width Chiều rộng của khung hình.
     * @param height Chiều cao của khung hình.
     * @return Mảng byte chứa dữ liệu mặt phẳng Y, hoặc null nếu lỗi đọc bộ đệm.
     */
    private byte[] extractYPlaneData(ByteBuffer yBuffer, int yRowStride, int width, int height) {
        byte[] data = new byte[width * height];
        int bufferOffset = 0;

        for (int row = 0; row < height; ++row) {
            int bytesToReadInRow = width;
            if (yBuffer.remaining() < bytesToReadInRow) {
                Log.e(TAG, "Lỗi tràn bộ đệm: Không đủ byte cho hàng " + row +
                        ". Còn lại: " + yBuffer.remaining() + ", Cần: " + bytesToReadInRow + ". Bỏ qua khung hình.");
                return null;
            }

            yBuffer.get(data, bufferOffset, bytesToReadInRow);
            bufferOffset += bytesToReadInRow;

            int paddingBytes = yRowStride - width;
            if (paddingBytes > 0) {
                if (yBuffer.remaining() >= paddingBytes) {
                    yBuffer.position(yBuffer.position() + paddingBytes);
                } else {
                    Log.w(TAG, "Không đủ bộ đệm còn lại để bỏ qua phần đệm cho hàng " + row +
                            ". Còn lại: " + yBuffer.remaining() + ", Phần đệm dự kiến: " + paddingBytes +
                            ". Các hàng tiếp theo có thể bị lệch.");
                    break;
                }
            }
        }
        return data;
    }

    /**
     * Xử lý xoay hình ảnh Mat dựa trên độ xoay.
     * @param gray Mat màu xám ban đầu.
     * @param rotationDegrees Độ xoay của hình ảnh (0, 90, 180, 270).
     * @return Mat đã xoay, hoặc Mat gốc nếu không cần xoay.
     */
    private Mat handleImageRotation(Mat gray, int rotationDegrees) {
        boolean needsRotation = (rotationDegrees == 90 || rotationDegrees == 270);

        if (needsRotation) {
            Mat rotatedGray = new Mat();
            Core.transpose(gray, rotatedGray); // Chuyển vị ma trận
            // Lật theo chiều dọc nếu xoay 90 độ, hoặc theo chiều ngang nếu 270 độ
            Core.flip(rotatedGray, rotatedGray, (rotationDegrees == 90) ? 1 : 0);
            return rotatedGray;
        }

        return gray;
    }

    /**
     * Ghi nhật ký kích thước của hình ảnh đã xử lý sau khi xoay.
     * @param imageProxy ImageProxy ban đầu.
     * @param processedGray Mat đã xử lý sau khi xoay.
     * @param rotationDegrees Độ xoay đã áp dụng.
     */
    private void logProcessedDimensions(ImageProxy imageProxy, Mat processedGray, int rotationDegrees) {
        Log.d(TAG, "DEBUG_ROTATION: Gốc " + imageProxy.getWidth() + "x" + imageProxy.getHeight() +
                " -> Đã xử lý " + processedGray.width() + "x" + processedGray.height() +
                " (xoay: " + rotationDegrees + "°)");
    }

    /**
     * Áp dụng chuỗi các bước xử lý hình ảnh OpenCV (làm mờ, tăng cường độ tương phản).
     * @param mat Mat để xử lý.
     */
    private void applyImageProcessingPipeline(Mat mat) {
        // Bước 1: Làm mờ trung vị để loại bỏ nhiễu hạt
        Imgproc.medianBlur(mat, mat, 3);

        // Bước 2: Làm mờ Gaussian nhẹ nhàng để giảm nhiễu đồng đều
        Imgproc.GaussianBlur(mat, mat, new org.opencv.core.Size(3, 3), 0);

        // Bước 3: Áp dụng CLAHE để tăng cường độ tương phản
        CLAHE clahe = Imgproc.createCLAHE(2.0, new org.opencv.core.Size(8, 8));
        clahe.apply(mat, mat);
    }

    /**
     * Phát hiện các cạnh trong hình ảnh đã xử lý bằng thuật toán Canny và thực hiện giãn nở.
     * @param processedGray Mat màu xám đã được xử lý.
     * @return Mat chứa các cạnh đã phát hiện.
     */
    private Mat detectEdges(Mat processedGray) {
        Mat edges = new Mat();
        Imgproc.Canny(processedGray, edges, dynamicCannyThreshold1, dynamicCannyThreshold2);

        // Giãn nở hình thái
        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new org.opencv.core.Size(3, 3));
        Imgproc.dilate(edges, edges, kernel);
        kernel.release();

        return edges;
    }

    /**
     * Tìm các đường viền trong hình ảnh cạnh.
     * @param edges Mat chứa các cạnh.
     * @param hierarchy Mat để lưu trữ thông tin phân cấp đường viền.
     * @return Danh sách các MatOfPoint đại diện cho các đường viền được tìm thấy.
     */
    private List<MatOfPoint> findContours(Mat edges, Mat hierarchy) {
        List<MatOfPoint> contours = new ArrayList<>();
        hierarchy = new Mat();
        Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE);
        return contours;
    }

    /**
     * Dọn dẹp Mat đã xoay nếu nó khác với Mat gốc và không được sử dụng để lưu trữ kích thước.
     * @param original Mat gốc.
     * @param processed Mat đã xử lý.
     * @param dimensionStorage Mat được sử dụng để lưu trữ kích thước.
     */
    private void cleanupRotatedMat(Mat original, Mat processed, Mat dimensionStorage) {
        boolean needsRotation = (processed != original);
        if (needsRotation && dimensionStorage == null) {
            processed.release();
        }
    }

    /**
     * Giải phóng các tài nguyên Mat được sử dụng trong quá trình xử lý hình ảnh.
     * @param gray Mat màu xám.
     * @param edges Mat cạnh.
     * @param hierarchy Mat phân cấp.
     * @param contours Danh sách các MatOfPoint đường viền.
     */
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

    /**
     * Điều chỉnh ngưỡng phát hiện cạnh Canny một cách linh hoạt dựa trên độ phân giải của khung hình đầu vào.
     * Độ phân giải cao hơn có thể sử dụng ngưỡng cao hơn.
     * @param frameWidth Chiều rộng của khung hình camera.
     * @param frameHeight Chiều cao của khung hình camera.
     */
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
        Log.d(TAG, "Ngưỡng Canny được điều chỉnh thành: " + dynamicCannyThreshold1 + ", " + dynamicCannyThreshold2 +
                " cho độ phân giải " + frameWidth + "x" + frameHeight);
    }

    /**
     * Tìm hình chữ nhật tốt nhất (đa giác 4 cạnh) trong danh sách các đường viền.
     * Lọc các đường viền theo số đỉnh, diện tích, độ lồi và sự nhất quán góc.
     * @param contours Danh sách MatOfPoint đại diện cho các đường viền được phát hiện.
     * @param imageWidth Chiều rộng của hình ảnh.
     * @param imageHeight Chiều cao của hình ảnh.
     * @return MatOfPoint đại diện cho hình chữ nhật tốt nhất được phát hiện, hoặc null nếu không tìm thấy.
     */
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

    /**
     * Xử lý một đường viền để xác định xem nó có phải là hình chữ nhật hợp lệ hay không.
     * @param contour Đường viền đầu vào.
     * @param minArea Diện tích tối thiểu cho hình chữ nhật hợp lệ.
     * @param maxArea Diện tích tối đa cho hình chữ nhật hợp lệ.
     * @return MatOfPoint của hình chữ nhật đã xử lý nếu hợp lệ, ngược lại là null.
     */
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

    /**
     * Kiểm tra xem một đường viền xấp xỉ có phải là hình chữ nhật hợp lệ hay không.
     * @param numVertices Số đỉnh của đường viền.
     * @param area Diện tích của đường viền.
     * @param minArea Diện tích tối thiểu hợp lệ.
     * @param maxArea Diện tích tối đa hợp lệ.
     * @param approxCurve Đường viền xấp xỉ.
     * @return true nếu đường viền là hình chữ nhật hợp lệ, false nếu ngược lại.
     */
    private boolean isValidQuadrilateral(long numVertices, double area, double minArea, double maxArea, MatOfPoint2f approxCurve) {
        if (numVertices != 4 || area <= minArea || area >= maxArea) {
            return false;
        }

        if (!Imgproc.isContourConvex(new MatOfPoint(approxCurve.toArray()))) {
            return false;
        }

        return hasValidCornerAngles(approxCurve.toArray());
    }

    /**
     * Kiểm tra xem các góc của hình chữ nhật có hợp lệ (gần 90 độ) hay không.
     * @param points Mảng các điểm của hình chữ nhật.
     * @return true nếu các góc hợp lệ, false nếu ngược lại.
     */
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

    /**
     * Tính toán cosine của góc giữa ba điểm (p1-p2-p3).
     * Được sử dụng để đánh giá "độ vuông" của các góc được phát hiện.
     * @param p1 Điểm 1.
     * @param p2 Điểm 2 (đỉnh của góc).
     * @param p3 Điểm 3.
     * @return Cosine của góc.
     */
    private double angle(Point p1, Point p2, Point p3) {
        double dx1 = p1.x - p2.x;
        double dy1 = p1.y - p2.y;
        double dx2 = p3.x - p2.x;
        double dy2 = p3.y - p2.y;
        return (dx1 * dx2 + dy1 * dy2) / (Math.sqrt(dx1 * dx1 + dy1 * dy1) * Math.sqrt(dx2 * dx2 + dy2 * dy2) + 1e-10);
    }

    /**
     * Sắp xếp các điểm của một hình chữ nhật theo thứ tự nhất quán (trên cùng bên trái, trên cùng bên phải, dưới cùng bên phải, dưới cùng bên trái).
     * Điều này rất quan trọng để vẽ và biến đổi phối cảnh nhất quán.
     * @param pointsMat MatOfPoint chứa các điểm hình chữ nhật chưa sắp xếp.
     * @return Một MatOfPoint mới với các điểm đã sắp xếp.
     */
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

    // ========== PHƯƠNG THỨC TƯƠNG TÁC UI ==========

    /**
     * Xử lý sự kiện chọn tab trong TabLayout.
     * @param position Vị trí tab được chọn.
     */
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

    /**
     * Xóa hộp giới hạn khỏi lớp phủ tùy chỉnh.
     */
    private void clearOverlay() {
        customOverlayView.clearBoundingBox();
        customOverlayView.invalidate();
    }

    /**
     * Xử lý kết quả từ việc chọn ảnh từ thư viện.
     * @param uri URI của ảnh đã chọn.
     */
    private void handleGalleryResult(Uri uri) {
        selectedImageUri = uri;
        Log.d(TAG, "Ảnh được tải từ thư viện, URI gốc: " + selectedImageUri);
        startCropActivity(selectedImageUri, null);
    }

    // ========== PHƯƠNG THỨC CHỤP ẢNH ==========

    /**
     * Chụp ảnh bằng ImageCapture và lưu vào một tệp tạm thời.
     * Sau khi chụp, bắt đầu CropActivity với URI ảnh đã chụp và hình chữ nhật được phát hiện (nếu có).
     */
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

    /**
     * Tạo một tệp mới để lưu ảnh đã chụp.
     * @return Đối tượng File cho ảnh đã chụp.
     */
    private File createPhotoFile() {
        return new File(getCacheDir(), "captured_image_" + System.currentTimeMillis() + ".jpeg");
    }

    /**
     * Lớp callback để xử lý kết quả của thao tác chụp ảnh.
     */
    private class PhotoCaptureCallback implements ImageCapture.OnImageSavedCallback {
        /**
         * Được gọi khi ảnh được lưu thành công.
         * @param outputFileResults Kết quả của tệp đầu ra.
         */
        @Override
        public void onImageSaved(ImageCapture.OutputFileResults outputFileResults) {
            Uri savedUri = outputFileResults.getSavedUri();
            if (savedUri != null) {
                handlePhotoSaved(savedUri);
            } else {
                handlePhotoSaveError("Uri null");
            }
        }

        /**
         * Được gọi khi có lỗi trong quá trình chụp ảnh.
         * @param exception Ngoại lệ ImageCaptureException.
         */
        @Override
        public void onError(ImageCaptureException exception) {
            handlePhotoCaptureError(exception);
        }
    }

    /**
     * Xử lý khi ảnh được lưu thành công.
     * @param savedUri URI của ảnh đã lưu.
     */
    private void handlePhotoSaved(Uri savedUri) {
        showToast(getString(R.string.photo_captured_saved));
        Log.d(TAG, "Ảnh đã chụp và lưu: " + savedUri);
        startCropActivity(savedUri, lastDetectedQuadrilateral);
    }

    /**
     * Xử lý khi xảy ra lỗi trong quá trình lưu ảnh.
     * @param error Thông báo lỗi.
     */
    private void handlePhotoSaveError(String error) {
        showToast(getString(R.string.failed_to_save_image));
        Log.e(TAG, "Không thể lưu ảnh: " + error);
    }

    /**
     * Xử lý khi có lỗi trong quá trình chụp ảnh.
     * @param exception Ngoại lệ ImageCaptureException.
     */
    private void handlePhotoCaptureError(ImageCaptureException exception) {
        Log.e(TAG, "Lỗi khi chụp ảnh: " + exception.getMessage(), exception);
        showToast("Lỗi khi chụp ảnh: " + exception.getMessage());
    }

    // ========== PHƯƠNG THỨC ĐIỀU HƯỚNG ==========

    /**
     * Bắt đầu CropActivity với URI ảnh đã cho và một hình chữ nhật được phát hiện tùy chọn.
     * Các điểm hình chữ nhật và kích thước ảnh gốc được truyền dưới dạng dữ liệu bổ sung.
     * @param imageUri URI của ảnh cần cắt.
     * @param detectedQuadrilateral MatOfPoint đại diện cho hình chữ nhật được phát hiện, hoặc null nếu không có.
     */
    private void startCropActivity(Uri imageUri, MatOfPoint detectedQuadrilateral) {
        Intent cropIntent = createCropIntent(imageUri, detectedQuadrilateral);
        startActivity(cropIntent);
    }

    /**
     * Tạo một Intent để bắt đầu CropActivity.
     * @param imageUri URI của ảnh.
     * @param detectedQuadrilateral Hình chữ nhật được phát hiện, có thể là null.
     * @return Intent đã cấu hình cho CropActivity.
     */
    private Intent createCropIntent(Uri imageUri, MatOfPoint detectedQuadrilateral) {
        Intent cropIntent = new Intent(CameraActivity.this, CropActivity.class);
        cropIntent.putExtra("imageUri", imageUri.toString());

        if (detectedQuadrilateral != null && !detectedQuadrilateral.empty()) {
            addQuadrilateralToIntent(cropIntent, detectedQuadrilateral);
        }

        return cropIntent;
    }

    /**
     * Thêm thông tin hình chữ nhật vào Intent nếu nó được phát hiện.
     * @param intent Intent để thêm dữ liệu.
     * @param detectedQuadrilateral MatOfPoint của hình chữ nhật được phát hiện.
     */
    private void addQuadrilateralToIntent(Intent intent, MatOfPoint detectedQuadrilateral) {
        Point[] points = detectedQuadrilateral.toArray();
        if (points.length == 4) {
            float[] quadPoints = convertPointsToFloatArray(points);
            intent.putExtra("detectedQuadrilateral", quadPoints);
            intent.putExtra("originalImageWidth", lastImageProxyWidth);
            intent.putExtra("originalImageHeight", lastImageProxyHeight);
        }
    }

    /**
     * Chuyển đổi một mảng các đối tượng Point thành một mảng float.
     * @param points Mảng các đối tượng Point.
     * @return Mảng float chứa các tọa độ x, y.
     */
    private float[] convertPointsToFloatArray(Point[] points) {
        float[] quadPoints = new float[8]; // 4 điểm x 2 tọa độ (x,y)
        for (int i = 0; i < 4; i++) {
            quadPoints[i * 2] = (float) points[i].x;
            quadPoints[i * 2 + 1] = (float) points[i].y;
        }
        return quadPoints;
    }

    // ========== PHƯƠNG THỨC TRẠNG THÁI UI ==========

    /**
     * Hiển thị các thành phần UI xem trước camera và ẩn imageView.
     * Nếu quyền camera được cấp, nó sẽ khởi động camera.
     */
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

    /**
     * Mở thư viện ảnh của thiết bị bằng galleryLauncher.
     */
    private void openGallery() {
        galleryLauncher.launch("image/*");
    }

    // ========== PHƯƠNG THỨC TIỆN ÍCH ==========

    /**
     * Hiển thị một thông báo Toast ngắn.
     * @param message Tin nhắn cần hiển thị.
     */
    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    // ========== PHƯƠNG THỨC VÒNG ĐỜI ==========

    /**
     * Được gọi khi hoạt động bị hủy.
     * Thực hiện dọn dẹp tài nguyên.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        cleanup();
    }

    /**
     * Dọn dẹp các tài nguyên như executor camera và hình chữ nhật được phát hiện cuối cùng.
     */
    private void cleanup() {
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
        releaseLastDetectedQuadrilateral();
    }
}