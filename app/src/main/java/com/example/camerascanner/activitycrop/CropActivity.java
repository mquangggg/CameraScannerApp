package com.example.camerascanner.activitycrop;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.canhub.cropper.CropImageView;
import com.example.camerascanner.R;
import com.example.camerascanner.activitycamera.ImagePreviewActivity;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * CropActivity cho phép người dùng cắt (crop) ảnh bằng cách chọn 4 điểm.
 * Nó cũng tích hợp chức năng nhận diện văn bản (OCR) để tự động đề xuất vùng cắt
 * và cho phép chuyển ảnh đã cắt sang Activity tạo PDF hoặc OCR.
 */
public class CropActivity extends AppCompatActivity {

    private static final String TAG = "CropActivity"; // Thẻ (tag) dùng để ghi log

    // Khai báo các thành phần UI
    private CropImageView cropImageView; // CropImageView từ thư viện để hiển thị ảnh
    private CustomCropView customCropView; // Custom View để người dùng vẽ các điểm cắt
    private Button btnHuyCrop, btnYesCrop, btnMakeOCR; // Các nút chức năng
    private MagnifierView magnifierView; // Kính lúp để hỗ trợ chọn điểm chính xác hơn

    // URI của ảnh đầu vào cần cắt
    private Uri imageUriToCrop;
    // Đối tượng TextRecognizer từ ML Kit để nhận dạng văn bản
    private TextRecognizer textRecognizer;
    // Bitmap của ảnh gốc sau khi được tải từ URI
    private Bitmap originalBitmapLoaded;


    /**
     * Phương thức được gọi khi Activity lần đầu tiên được tạo.
     * Thiết lập layout, ánh xạ các View, khởi tạo TextRecognizer,
     * lấy URI ảnh từ Intent, tải ảnh, xử lý nhận diện văn bản tự động đề xuất vùng cắt,
     * và thiết lập các sự kiện click cho các nút.
     *
     * @param savedInstanceState Đối tượng Bundle chứa trạng thái Activity trước đó nếu có.
     */
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Đặt layout cho Activity này. Chú ý tên layout đã thay đổi so với phiên bản trước
        setContentView(R.layout.activity_cropimage);

        // Ánh xạ các View từ layout XML
        cropImageView = findViewById(R.id.cropImageView);
        customCropView = findViewById(R.id.customCropView);
        btnHuyCrop = findViewById(R.id.btnHuyCrop);
        btnYesCrop = findViewById(R.id.btnYesCrop);
        magnifierView = findViewById(R.id.magnifierView);
        //btnMakeOCR = findViewById(R.id.btnMakeOCR);

        // Khởi tạo TextRecognizer để nhận dạng văn bản Latin
        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

        // Thiết lập MagnifierView cho CustomCropView để hiển thị kính lúp khi chạm
        customCropView.setMagnifierView(magnifierView);

        // Kiểm tra và lấy URI ảnh từ Intent
        if (getIntent().getExtras() != null && getIntent().getExtras().containsKey("imageUri")) {
            String imageUriString = getIntent().getStringExtra("imageUri");
            if (imageUriString != null) {
                imageUriToCrop = Uri.parse(imageUriString);

                if (imageUriToCrop != null) {
                    // Thiết lập ảnh cho CropImageView
                    cropImageView.setImageUriAsync(imageUriToCrop);
                    cropImageView.setGuidelines(CropImageView.Guidelines.OFF);

                    try {
                        // Tải Bitmap gốc từ URI
                        originalBitmapLoaded = getCorrectlyOrientedBitmap(imageUriToCrop);
                        if (originalBitmapLoaded != null) {
                            customCropView.post(() -> {
                                // Tính toán ma trận chuyển đổi
                                Matrix imageToViewMatrix = getImageToViewMatrix(
                                        originalBitmapLoaded.getWidth(),
                                        originalBitmapLoaded.getHeight(),
                                        customCropView.getWidth(),
                                        customCropView.getHeight()
                                );
                                Matrix viewToImageMatrix = new Matrix();
                                imageToViewMatrix.invert(viewToImageMatrix);

                                float[] imageToViewValues = new float[9];
                                imageToViewMatrix.getValues(imageToViewValues);
                                float[] viewToImageValues = new float[9];
                                viewToImageMatrix.getValues(viewToImageValues);

                                // Thiết lập dữ liệu ảnh và ma trận cho CustomCropView
                                customCropView.setImageData(originalBitmapLoaded, imageToViewValues, viewToImageValues);

                                // Kiểm tra xem có khung phát hiện từ camera không
                                if (getIntent().hasExtra("detectedQuadrilateral")) {
                                    // Có khung từ camera - sử dụng khung đó
                                    setupCropFromDetectedQuadrilateral();
                                } else {
                                    // Không có khung từ camera - tự động phát hiện bằng OCR
                                    processImageForTextDetection(imageUriToCrop);
                                }
                            });
                        }
                    } catch (IOException e) {
                        Log.e(TAG, getString(R.string.error_loading_original_bitmap) + e.getMessage(), e);
                        Toast.makeText(this, "Lỗi khi tải ảnh gốc để nhận diện văn bản.", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(this, getString(R.string.no_image_uri_received_for_cropping), Toast.LENGTH_SHORT).show();
                    finish();
                }
            } else {
                Toast.makeText(this, getString(R.string.no_image_uri_received_for_cropping), Toast.LENGTH_SHORT).show();
                finish();
            }
        } else {
            Toast.makeText(this, getString(R.string.no_image_to_crop), Toast.LENGTH_SHORT).show();
            finish();
        }

        // Thiết lập sự kiện click cho nút "Hủy Crop"
        btnHuyCrop.setOnClickListener(v -> {
            setResult(RESULT_CANCELED); // Đặt kết quả là hủy
            finish(); // Đóng Activity
        });

        // Thiết lập sự kiện click cho nút "Đồng ý Crop" (Lưu PDF)
        btnYesCrop.setOnClickListener(v -> {
            // Lấy các điểm cắt đã chọn từ CustomCropView
            ArrayList<PointF> cropPoints = customCropView.getCropPoints();
            if (cropPoints.size() != 4) {
                // Yêu cầu người dùng chọn đủ 4 điểm
                Toast.makeText(this, getString(R.string.please_select_4_points_to_crop), Toast.LENGTH_SHORT).show();
                return;
            }

            // Chuyển đổi tọa độ các điểm từ View sang Bitmap
            ArrayList<PointF> transformedPoints = new ArrayList<>();
            for (PointF point : cropPoints) {
                float[] bitmapCoords = transformViewPointToBitmapPoint(point.x, point.y);
                transformedPoints.add(new PointF(bitmapCoords[0], bitmapCoords[1]));
            }

            // Cắt Bitmap dựa trên 4 điểm đã chọn
            Bitmap straightenedBitmap = performPerspectiveTransform(originalBitmapLoaded, transformedPoints);
            if (straightenedBitmap != null) {
                Uri straightenedUri = saveBitmapToCache(straightenedBitmap);
                Intent resultIntent = new Intent(CropActivity.this, ImagePreviewActivity.class);
                resultIntent.putExtra("imageUri",straightenedUri.toString());
                startActivity(resultIntent);
                if (straightenedBitmap != originalBitmapLoaded) { // Giải phóng bitmap nếu là bản sao
                    straightenedBitmap.recycle();
                }
            } else {
                Toast.makeText(this, getString(R.string.error_cropping_image), Toast.LENGTH_SHORT).show();
            }
        });

        // Thiết lập sự kiện click cho nút "Thực hiện OCR"

    }

    // Thêm method mới để setup crop từ khung đã phát hiện
    private void setupCropFromDetectedQuadrilateral() {
        float[] quadPoints = getIntent().getFloatArrayExtra("detectedQuadrilateral");
        int originalImageWidth = getIntent().getIntExtra("originalImageWidth", 0);
        int originalImageHeight = getIntent().getIntExtra("originalImageHeight", 0);

        if (quadPoints != null && quadPoints.length == 8 && originalImageWidth > 0 && originalImageHeight > 0) {
            // Chuyển đổi float[] thành ArrayList<PointF> trong tọa độ bitmap gốc
            ArrayList<PointF> detectedPointsInOriginalImage = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                float x = quadPoints[i * 2];
                float y = quadPoints[i * 2 + 1];

                // Scale điểm từ kích thước phát hiện về kích thước bitmap gốc
                float scaleX = (float) originalBitmapLoaded.getWidth() / originalImageWidth;
                float scaleY = (float) originalBitmapLoaded.getHeight() / originalImageHeight;

                detectedPointsInOriginalImage.add(new PointF(x * scaleX, y * scaleY));
            }

            // Chuyển đổi các điểm từ tọa độ bitmap sang tọa độ view và thiết lập
            customCropView.clearPoints();
            for (PointF point : detectedPointsInOriginalImage) {
                PointF viewPoint = transformBitmapPointToViewPoint(point.x, point.y);
                customCropView.addPoint(viewPoint);
            }
            customCropView.invalidate();

            Toast.makeText(this, "Đã thiết lập khung crop từ camera", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "Đã thiết lập các điểm crop từ khung phát hiện camera.");
        } else {
            // Fallback về OCR nếu dữ liệu không hợp lệ
            Log.w(TAG, "Dữ liệu khung phát hiện không hợp lệ, fallback về OCR");
            processImageForTextDetection(imageUriToCrop);
        }
    }

    /**
     * Lưu một Bitmap vào thư mục cache của ứng dụng và trả về Uri của tệp đã lưu.
     * Đây là phương thức được sử dụng để lưu ảnh đã cắt trước khi truyền sang Activity khác.
     *
     * @param bitmap Bitmap cần lưu.
     * @return Uri của tệp đã lưu, hoặc null nếu có lỗi.
     */
    private Uri saveBitmapToCache(Bitmap bitmap) {
        String fileName = "cropped_image_" + System.currentTimeMillis() + ".jpeg";
        File cachePath = new File(getCacheDir(), "cropped_images"); // Thư mục con để lưu ảnh cắt
        cachePath.mkdirs(); // Tạo thư mục nếu nó chưa tồn tại
        File file = new File(cachePath, fileName);

        try (FileOutputStream fos = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos); // Nén ảnh với chất lượng 90%
            fos.flush(); // Đảm bảo tất cả dữ liệu được ghi
            return Uri.fromFile(file); // Trả về URI từ File
        } catch (IOException e) {
            // Ghi log lỗi và hiển thị thông báo
            Log.e(TAG, getString(R.string.error_saving_cropped_bitmap) + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Xử lý ảnh để nhận diện văn bản bằng ML Kit.
     * Nếu nhận diện thành công, các bounding box của văn bản sẽ được dùng
     * để tự động thiết lập các điểm cắt ban đầu trên CustomCropView.
     *
     * @param imageUri URI của ảnh cần xử lý nhận diện văn bản.
     */
    private void processImageForTextDetection(Uri imageUri) {
        try {
            // Tạo đối tượng InputImage từ URI
            InputImage inputImage = InputImage.fromFilePath(this, imageUri);
            // Bắt đầu quá trình nhận diện văn bản
            textRecognizer.process(inputImage)
                    .addOnSuccessListener(text -> {
                        // Khi nhận diện thành công, thiết lập các điểm cắt ban đầu
                        if (originalBitmapLoaded != null) {
                            setInitialCropPointsFromText(text, originalBitmapLoaded.getWidth(), originalBitmapLoaded.getHeight());
                        }
                    })
                    .addOnFailureListener(e -> {
                        // Xử lý lỗi nếu quá trình nhận diện văn bản thất bại
                        Log.e(TAG, "Lỗi khi nhận dạng văn bản: " + e.getMessage(), e);
                        Toast.makeText(this, getString(R.string.error_text_recognition_failed), Toast.LENGTH_SHORT).show();
                    });
        } catch (Exception e) {
            // Xử lý lỗi nếu không thể tạo InputImage từ URI
            Log.e(TAG, "Lỗi khi tạo InputImage từ URI: " + e.getMessage(), e);
            Toast.makeText(this, getString(R.string.error_creating_input_image_for_text_detection), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Thiết lập các điểm cắt ban đầu trên CustomCropView dựa trên vùng văn bản được nhận dạng.
     * Nếu không tìm thấy văn bản, vùng cắt sẽ bao gồm toàn bộ ảnh.
     *
     * @param recognizedText Đối tượng Text chứa kết quả nhận diện văn bản.
     * @param bitmapWidth    Chiều rộng của Bitmap gốc.
     * @param bitmapHeight   Chiều cao của Bitmap gốc.
     */
    private void setInitialCropPointsFromText(Text recognizedText, int bitmapWidth, int bitmapHeight) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;

        // Tìm bounding box tổng hợp của tất cả các khối văn bản
        if (!recognizedText.getTextBlocks().isEmpty()) {
            for (Text.TextBlock block : recognizedText.getTextBlocks()) {
                android.graphics.Rect boundingBox = block.getBoundingBox();
                if (boundingBox != null) {
                    minX = Math.min(minX, boundingBox.left);
                    minY = Math.min(minY, boundingBox.top);
                    maxX = Math.max(maxX, boundingBox.right);
                    maxY = Math.max(maxY, boundingBox.bottom);
                }
            }
        }

        ArrayList<PointF> initialBitmapPoints = new ArrayList<>();

        // Nếu không tìm thấy văn bản, thiết lập vùng cắt là toàn bộ ảnh
        if (minX == Integer.MAX_VALUE) {
            Log.d(TAG, "Không tìm thấy văn bản hoặc bounding box hợp lệ. Sử dụng toàn bộ ảnh.");
            initialBitmapPoints.add(new PointF(0, 0)); // Góc trên-trái
            initialBitmapPoints.add(new PointF(bitmapWidth, 0)); // Góc trên-phải
            initialBitmapPoints.add(new PointF(bitmapWidth, bitmapHeight)); // Góc dưới-phải
            initialBitmapPoints.add(new PointF(0, bitmapHeight)); // Góc dưới-trái
        } else {
            // Thêm một khoảng đệm (padding) xung quanh vùng văn bản được tìm thấy
            int padding = 20; // Đệm 20 pixel
            minX = Math.max(0, minX - padding);
            minY = Math.max(0, minY - padding);
            maxX = Math.min(bitmapWidth, maxX + padding);
            maxY = Math.min(bitmapHeight, maxY + padding);

            // Thêm các điểm của vùng văn bản đã có padding vào danh sách
            initialBitmapPoints.add(new PointF(minX, minY)); // Góc trên-trái
            initialBitmapPoints.add(new PointF(maxX, minY)); // Góc trên-phải
            initialBitmapPoints.add(new PointF(maxX, maxY)); // Góc dưới-phải
            initialBitmapPoints.add(new PointF(minX, maxY)); // Góc dưới-trái
            Log.d(TAG, "Đã thiết lập các điểm cắt tự động theo văn bản.");
            Toast.makeText(this, getString(R.string.text_area_auto_detected), Toast.LENGTH_SHORT).show();
        }

        // Xóa các điểm cũ và thêm các điểm mới đã chuyển đổi sang tọa độ View
        customCropView.clearPoints();
        for (PointF point : initialBitmapPoints) {
            customCropView.addPoint(transformBitmapPointToViewPoint(point.x, point.y));
        }
        customCropView.invalidate(); // Vẽ lại CustomCropView để hiển thị các điểm mới
    }

    /**
     * Chuyển đổi một điểm từ tọa độ của Bitmap sang tọa độ của View.
     *
     * @param bitmapX Tọa độ X trên Bitmap.
     * @param bitmapY Tọa độ Y trên Bitmap.
     * @return Đối tượng PointF với tọa độ đã được chuyển đổi sang View.
     */
    private PointF transformBitmapPointToViewPoint(float bitmapX, float bitmapY) {
        // Kiểm tra điều kiện để tránh lỗi chia cho 0 hoặc truy cập null
        if (originalBitmapLoaded == null || customCropView.getWidth() == 0 || customCropView.getHeight() == 0) {
            return new PointF(bitmapX, bitmapY); // Trả về nguyên bản nếu không đủ thông tin
        }

        // Lấy ma trận chuyển đổi từ ảnh sang View
        Matrix matrix = getImageToViewMatrix(originalBitmapLoaded.getWidth(), originalBitmapLoaded.getHeight(),
                customCropView.getWidth(), customCropView.getHeight());
        float[] pts = {bitmapX, bitmapY}; // Tạo mảng chứa tọa độ điểm
        matrix.mapPoints(pts); // Áp dụng ma trận để chuyển đổi tọa độ
        return new PointF(pts[0], pts[1]); // Trả về điểm đã chuyển đổi
    }

    /**
     * Chuyển đổi một điểm từ tọa độ của View sang tọa độ của Bitmap.
     *
     * @param viewX Tọa độ X trên View.
     * @param viewY Tọa độ Y trên View.
     * @return Mảng float chứa tọa độ [X, Y] đã được chuyển đổi sang Bitmap.
     */
    private float[] transformViewPointToBitmapPoint(float viewX, float viewY) {
        // Kiểm tra điều kiện để tránh lỗi chia cho 0 hoặc truy cập null
        if (originalBitmapLoaded == null || customCropView.getWidth() == 0 || customCropView.getHeight() == 0) {
            return new float[]{viewX, viewY}; // Trả về nguyên bản nếu không đủ thông tin
        }

        // Lấy ma trận chuyển đổi từ ảnh sang View
        Matrix imageToViewMatrix = getImageToViewMatrix(originalBitmapLoaded.getWidth(), originalBitmapLoaded.getHeight(),
                customCropView.getWidth(), customCropView.getHeight());
        Matrix viewToImageMatrix = new Matrix();
        imageToViewMatrix.invert(viewToImageMatrix); // Lấy ma trận nghịch đảo (từ View sang ảnh)

        float[] pts = {viewX, viewY}; // Tạo mảng chứa tọa độ điểm
        viewToImageMatrix.mapPoints(pts); // Áp dụng ma trận nghịch đảo để chuyển đổi tọa độ
        return new float[]{pts[0], pts[1]}; // Trả về điểm đã chuyển đổi
    }

    /**
     * Tính toán ma trận chuyển đổi để đưa một Bitmap vừa vặn vào một View,
     * giữ nguyên tỷ lệ khung hình và căn giữa.
     *
     * @param bitmapWidth  Chiều rộng của Bitmap.
     * @param bitmapHeight Chiều cao của Bitmap.
     * @param viewWidth    Chiều rộng của View.
     * @param viewHeight   Chiều cao của View.
     * @return Đối tượng Matrix chứa các phép biến đổi (tỷ lệ và dịch chuyển).
     */
    private Matrix getImageToViewMatrix(int bitmapWidth, int bitmapHeight, int viewWidth, int viewHeight) {
        Matrix matrix = new Matrix();
        float scale;
        float dx = 0, dy = 0; // Các giá trị dịch chuyển

        // Tính toán tỷ lệ theo chiều rộng và chiều cao
        float scaleX = (float) viewWidth / bitmapWidth;
        float scaleY = (float) viewHeight / bitmapHeight;

        // Chọn tỷ lệ nhỏ hơn để đảm bảo toàn bộ ảnh vừa với View mà không bị cắt
        if (scaleX < scaleY) {
            scale = scaleX;
            // Tính toán dịch chuyển theo chiều Y để căn giữa ảnh theo chiều dọc
            dy = (viewHeight - bitmapHeight * scale) / 2f;
        } else {
            scale = scaleY;
            // Tính toán dịch chuyển theo chiều X để căn giữa ảnh theo chiều ngang
            dx = (viewWidth - bitmapWidth * scale) / 2f;
        }

        matrix.postScale(scale, scale); // Áp dụng tỷ lệ
        matrix.postTranslate(dx, dy); // Áp dụng dịch chuyển để căn giữa
        return matrix;
    }

    // Đặt phương thức này bên trong lớp CropActivity
    private MatOfPoint sortPoints(MatOfPoint pointsMat) {
        Point[] pts = pointsMat.toArray();
        Point[] rect = new Point[4];

        Arrays.sort(pts, (p1, p2) -> Double.compare(p1.y, p2.y));

        Point[] topPoints = Arrays.copyOfRange(pts, 0, 2);
        Point[] bottomPoints = Arrays.copyOfRange(pts, 2, 4);

        Arrays.sort(topPoints, (p1, p2) -> Double.compare(p1.x, p2.x));
        rect[0] = topPoints[0]; // Top-Left
        rect[1] = topPoints[1]; // Top-Right

        Arrays.sort(bottomPoints, (p1, p2) -> Double.compare(p1.x, p2.x));
        rect[3] = bottomPoints[0]; // Bottom-Left
        rect[2] = bottomPoints[1]; // Bottom-Right

        return new MatOfPoint(rect);
    }

    // Đặt phương thức này bên trong lớp CropActivity
    private Bitmap performPerspectiveTransform(Bitmap originalBitmap, ArrayList<PointF> cropPoints) {
        if (originalBitmap == null || cropPoints == null || cropPoints.size() != 4) {
            Log.e(TAG, "Dữ liệu đầu vào không hợp lệ cho biến đổi phối cảnh.");
            return null;
         }

        Mat originalMat = new Mat();
        // Chuyển đổi Bitmap sang Mat
        Utils.bitmapToMat(originalBitmap, originalMat);

        // Chuyển đổi ArrayList<PointF> sang mảng Point của OpenCV
        Point[] pts = new Point[4];
        for (int i = 0; i < 4; i++) {
            pts[i] = new Point(cropPoints.get(i).x, cropPoints.get(i).y);
        }

        // Sắp xếp các điểm để đảm bảo thứ tự đúng cho biến đổi phối cảnh
        MatOfPoint unsortedMatOfPoint = new MatOfPoint(pts);
        MatOfPoint sortedPointsMat = sortPoints(unsortedMatOfPoint);
        Point[] sortedPts = sortedPointsMat.toArray();

        // Tính toán kích thước đích cho hình chữ nhật đã làm thẳng
        // Dựa trên chiều dài lớn nhất của các cạnh đối diện
        double widthTop = Math.sqrt(Math.pow(sortedPts[0].x - sortedPts[1].x, 2) + Math.pow(sortedPts[0].y - sortedPts[1].y, 2));
        double widthBottom = Math.sqrt(Math.pow(sortedPts[3].x - sortedPts[2].x, 2) + Math.pow(sortedPts[3].y - sortedPts[2].y, 2));
        int targetWidth = (int) Math.max(widthTop, widthBottom);

        double heightLeft = Math.sqrt(Math.pow(sortedPts[0].x - sortedPts[3].x, 2) + Math.pow(sortedPts[0].y - sortedPts[3].y, 2));
        double heightRight = Math.sqrt(Math.pow(sortedPts[1].x - sortedPts[2].x, 2) + Math.pow(sortedPts[1].y - sortedPts[2].y, 2));
        int targetHeight = (int) Math.max(heightLeft, heightRight);

        if (targetWidth <= 0 || targetHeight <= 0) {
            Log.e(TAG, "Kích thước đích không hợp lệ cho biến đổi phối cảnh. Trả về null.");
            originalMat.release();
            sortedPointsMat.release();
            unsortedMatOfPoint.release();
            return null;
        }

        // Định nghĩa các điểm nguồn (tứ giác đã sắp xếp trong tọa độ bitmap)
        MatOfPoint2f srcPoints = new MatOfPoint2f(
                sortedPts[0], // Trên-Trái
                sortedPts[1], // Trên-Phải
                sortedPts[2], // Dưới-Phải
                sortedPts[3]  // Dưới-Trái
        );

        // Định nghĩa các điểm đích (một hình chữ nhật hoàn hảo)
        MatOfPoint2f dstPoints = new MatOfPoint2f(
                new Point(0, 0),
                new Point(targetWidth - 1, 0),
                new Point(targetWidth - 1, targetHeight - 1),
                new Point(0, targetHeight - 1)
        );

        Mat transformedMat = new Mat();
        Mat perspectiveTransformMatrix = null;
        Bitmap resultBitmap = null;

        try {
            perspectiveTransformMatrix = Imgproc.getPerspectiveTransform(srcPoints, dstPoints);
            Imgproc.warpPerspective(originalMat, transformedMat, perspectiveTransformMatrix, new org.opencv.core.Size(targetWidth, targetHeight));

            resultBitmap = Bitmap.createBitmap(transformedMat.cols(), transformedMat.rows(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(transformedMat, resultBitmap);
        } catch (Exception e) {
            Log.e(TAG, "Lỗi khi thực hiện biến đổi phối cảnh: " + e.getMessage(), e);
            return null;
        } finally {
            // Giải phóng tất cả các đối tượng Mat của OpenCV để tránh rò rỉ bộ nhớ
            if (originalMat != null) originalMat.release();
            if (transformedMat != null) transformedMat.release();
            if (perspectiveTransformMatrix != null) perspectiveTransformMatrix.release();
            if (srcPoints != null) srcPoints.release();
            if (dstPoints != null) dstPoints.release();
            if (unsortedMatOfPoint != null) unsortedMatOfPoint.release();
            if (sortedPointsMat != null) sortedPointsMat.release();
        }

        return resultBitmap;
    }
    private Bitmap getCorrectlyOrientedBitmap(Uri imageUri) throws IOException {
        Bitmap originalBitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), imageUri);

        // Lấy thông tin EXIF để xác định orientation
        InputStream input = getContentResolver().openInputStream(imageUri);
        androidx.exifinterface.media.ExifInterface exif = new androidx.exifinterface.media.ExifInterface(input);
        int orientation = exif.getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL);
        input.close();

        Matrix matrix = new Matrix();
        switch (orientation) {
            case androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90:
                matrix.postRotate(90);
                break;
            case androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180:
                matrix.postRotate(180);
                break;
            case androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270:
                matrix.postRotate(270);
                break;
            default:
                return originalBitmap; // Không cần xoay
        }

        Bitmap rotatedBitmap = Bitmap.createBitmap(originalBitmap, 0, 0,
                originalBitmap.getWidth(),
                originalBitmap.getHeight(),
                matrix, true);

        if (rotatedBitmap != originalBitmap) {
            originalBitmap.recycle(); // Giải phóng bitmap gốc
        }

        Log.d(TAG, "DEBUG_BITMAP: Original size from URI: " + originalBitmap.getWidth() + "x" + originalBitmap.getHeight() +
                ", Orientation: " + orientation + ", Final size: " + rotatedBitmap.getWidth() + "x" + rotatedBitmap.getHeight());

        return rotatedBitmap;
    }

    /**
     * Phương thức được gọi khi Activity trở nên hiển thị với người dùng.
     * (Không có logic cụ thể nào trong phương thức này ở đây, thường dùng để khởi tạo
     * hoặc tải lại dữ liệu mà cần Activity hiển thị).
     */
    @Override
    protected void onStart() {
        super.onStart();
    }

    /**
     * Phương thức được gọi khi Activity bị hủy.
     * Đảm bảo rằng TextRecognizer được đóng để giải phóng tài nguyên.
     * Cũng giải phóng Bitmap gốc để tránh rò rỉ bộ nhớ.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (textRecognizer != null) {
            textRecognizer.close(); // Đóng TextRecognizer
        }
        // Giải phóng bitmap gốc đã tải nếu nó tồn tại và chưa được giải phóng
        if (originalBitmapLoaded != null && !originalBitmapLoaded.isRecycled()) {
            originalBitmapLoaded.recycle(); // Giải phóng bộ nhớ của bitmap
            originalBitmapLoaded = null; // Đặt về null
        }
    }
}