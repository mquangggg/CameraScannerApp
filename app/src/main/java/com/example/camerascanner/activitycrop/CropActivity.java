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
import com.example.camerascanner.activitypdf.pdfgroup.PDFGroupActivity;
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

public class CropActivity extends AppCompatActivity {

    private static final String TAG = "CropActivity";

    // Khai báo các thành phần UI
    private CropImageView cropImageView;
    private CustomCropView customCropView;
    private Button btnHuyCrop, btnYesCrop, btnMakeOCR;
    private MagnifierView magnifierView;

    // URI của ảnh đầu vào cần cắt
    private Uri imageUriToCrop;
    // Đối tượng TextRecognizer từ ML Kit để nhận dạng văn bản
    private TextRecognizer textRecognizer;
    // Bitmap của ảnh gốc sau khi được tải từ URI
    private Bitmap originalBitmapLoaded;

    // Biến để xác định có phải từ PDFGroup không
    private boolean isFromPdfGroup = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cropimage);

        // Ánh xạ các View từ layout XML
        cropImageView = findViewById(R.id.cropImageView);
        customCropView = findViewById(R.id.customCropView);
        btnHuyCrop = findViewById(R.id.btnHuyCrop);
        btnYesCrop = findViewById(R.id.btnYesCrop);
        magnifierView = findViewById(R.id.magnifierView);

        // Khởi tạo TextRecognizer
        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

        // Thiết lập MagnifierView cho CustomCropView
        customCropView.setMagnifierView(magnifierView);

        // Lấy thông tin từ Intent
        Intent intent = getIntent();
        if (intent != null) {
            isFromPdfGroup = intent.getBooleanExtra("FROM_PDF_GROUP", false);
            Log.d(TAG, "CropActivity: isFromPdfGroup=" + isFromPdfGroup);
        }

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
            setResult(RESULT_CANCELED);
            finish();
        });

        // Thiết lập sự kiện click cho nút "Đồng ý Crop"
        btnYesCrop.setOnClickListener(v -> {
            // Lấy các điểm cắt đã chọn từ CustomCropView
            ArrayList<PointF> cropPoints = customCropView.getCropPoints();
            if (cropPoints.size() != 4) {
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

                if (straightenedUri != null) {
                    // Trả kết quả về CameraActivity
                    Intent resultIntent = new Intent();
                    resultIntent.putExtra("processedImageUri", straightenedUri.toString());
                    setResult(RESULT_OK, resultIntent);
                    finish();
                } else {
                    Toast.makeText(this, "Lỗi khi lưu ảnh đã cắt", Toast.LENGTH_SHORT).show();
                }

                if (straightenedBitmap != originalBitmapLoaded) {
                    straightenedBitmap.recycle();
                }
            } else {
                Toast.makeText(this, getString(R.string.error_cropping_image), Toast.LENGTH_SHORT).show();
            }
        });
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

    private Uri saveBitmapToCache(Bitmap bitmap) {
        String fileName = "cropped_image_" + System.currentTimeMillis() + ".jpeg";
        File cachePath = new File(getCacheDir(), "cropped_images");
        cachePath.mkdirs();
        File file = new File(cachePath, fileName);

        try (FileOutputStream fos = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
            fos.flush();
            return Uri.fromFile(file);
        } catch (IOException e) {
            Log.e(TAG, getString(R.string.error_saving_cropped_bitmap) + e.getMessage(), e);
            return null;
        }
    }

    private void processImageForTextDetection(Uri imageUri) {
        try {
            InputImage inputImage = InputImage.fromFilePath(this, imageUri);
            textRecognizer.process(inputImage)
                    .addOnSuccessListener(text -> {
                        if (originalBitmapLoaded != null) {
                            setInitialCropPointsFromText(text, originalBitmapLoaded.getWidth(), originalBitmapLoaded.getHeight());
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Lỗi khi nhận dạng văn bản: " + e.getMessage(), e);
                        Toast.makeText(this, getString(R.string.error_text_recognition_failed), Toast.LENGTH_SHORT).show();
                    });
        } catch (Exception e) {
            Log.e(TAG, "Lỗi khi tạo InputImage từ URI: " + e.getMessage(), e);
            Toast.makeText(this, getString(R.string.error_creating_input_image_for_text_detection), Toast.LENGTH_SHORT).show();
        }
    }

    private void setInitialCropPointsFromText(Text recognizedText, int bitmapWidth, int bitmapHeight) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;

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

        if (minX == Integer.MAX_VALUE) {
            Log.d(TAG, "Không tìm thấy văn bản hoặc bounding box hợp lệ. Sử dụng toàn bộ ảnh.");
            initialBitmapPoints.add(new PointF(0, 0));
            initialBitmapPoints.add(new PointF(bitmapWidth, 0));
            initialBitmapPoints.add(new PointF(bitmapWidth, bitmapHeight));
            initialBitmapPoints.add(new PointF(0, bitmapHeight));
        } else {
            int padding = 20;
            minX = Math.max(0, minX - padding);
            minY = Math.max(0, minY - padding);
            maxX = Math.min(bitmapWidth, maxX + padding);
            maxY = Math.min(bitmapHeight, maxY + padding);

            initialBitmapPoints.add(new PointF(minX, minY));
            initialBitmapPoints.add(new PointF(maxX, minY));
            initialBitmapPoints.add(new PointF(maxX, maxY));
            initialBitmapPoints.add(new PointF(minX, maxY));
            Log.d(TAG, "Đã thiết lập các điểm cắt tự động theo văn bản.");
            Toast.makeText(this, getString(R.string.text_area_auto_detected), Toast.LENGTH_SHORT).show();
        }

        customCropView.clearPoints();
        for (PointF point : initialBitmapPoints) {
            customCropView.addPoint(transformBitmapPointToViewPoint(point.x, point.y));
        }
        customCropView.invalidate();
    }

    private PointF transformBitmapPointToViewPoint(float bitmapX, float bitmapY) {
        if (originalBitmapLoaded == null || customCropView.getWidth() == 0 || customCropView.getHeight() == 0) {
            return new PointF(bitmapX, bitmapY);
        }

        Matrix matrix = getImageToViewMatrix(originalBitmapLoaded.getWidth(), originalBitmapLoaded.getHeight(),
                customCropView.getWidth(), customCropView.getHeight());
        float[] pts = {bitmapX, bitmapY};
        matrix.mapPoints(pts);
        return new PointF(pts[0], pts[1]);
    }

    private float[] transformViewPointToBitmapPoint(float viewX, float viewY) {
        if (originalBitmapLoaded == null || customCropView.getWidth() == 0 || customCropView.getHeight() == 0) {
            return new float[]{viewX, viewY};
        }

        Matrix imageToViewMatrix = getImageToViewMatrix(originalBitmapLoaded.getWidth(), originalBitmapLoaded.getHeight(),
                customCropView.getWidth(), customCropView.getHeight());
        Matrix viewToImageMatrix = new Matrix();
        imageToViewMatrix.invert(viewToImageMatrix);

        float[] pts = {viewX, viewY};
        viewToImageMatrix.mapPoints(pts);
        return new float[]{pts[0], pts[1]};
    }

    private Matrix getImageToViewMatrix(int bitmapWidth, int bitmapHeight, int viewWidth, int viewHeight) {
        Matrix matrix = new Matrix();
        float scale;
        float dx = 0, dy = 0;

        float scaleX = (float) viewWidth / bitmapWidth;
        float scaleY = (float) viewHeight / bitmapHeight;

        if (scaleX < scaleY) {
            scale = scaleX;
            dy = (viewHeight - bitmapHeight * scale) / 2f;
        } else {
            scale = scaleY;
            dx = (viewWidth - bitmapWidth * scale) / 2f;
        }

        matrix.postScale(scale, scale);
        matrix.postTranslate(dx, dy);
        return matrix;
    }

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

    private Bitmap performPerspectiveTransform(Bitmap originalBitmap, ArrayList<PointF> cropPoints) {
        if (originalBitmap == null || cropPoints == null || cropPoints.size() != 4) {
            Log.e(TAG, "Dữ liệu đầu vào không hợp lệ cho biến đổi phối cảnh.");
            return null;
        }

        Mat originalMat = new Mat();
        Utils.bitmapToMat(originalBitmap, originalMat);

        Point[] pts = new Point[4];
        for (int i = 0; i < 4; i++) {
            pts[i] = new Point(cropPoints.get(i).x, cropPoints.get(i).y);
        }

        MatOfPoint unsortedMatOfPoint = new MatOfPoint(pts);
        MatOfPoint sortedPointsMat = sortPoints(unsortedMatOfPoint);
        Point[] sortedPts = sortedPointsMat.toArray();

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

        MatOfPoint2f srcPoints = new MatOfPoint2f(
                sortedPts[0], // Trên-Trái
                sortedPts[1], // Trên-Phải
                sortedPts[2], // Dưới-Phải
                sortedPts[3]  // Dưới-Trái
        );

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
                return originalBitmap;
        }

        Bitmap rotatedBitmap = Bitmap.createBitmap(originalBitmap, 0, 0,
                originalBitmap.getWidth(),
                originalBitmap.getHeight(),
                matrix, true);

        if (rotatedBitmap != originalBitmap) {
            originalBitmap.recycle();
        }

        Log.d(TAG, "DEBUG_BITMAP: Original size from URI: " + originalBitmap.getWidth() + "x" + originalBitmap.getHeight() +
                ", Orientation: " + orientation + ", Final size: " + rotatedBitmap.getWidth() + "x" + rotatedBitmap.getHeight());

        return rotatedBitmap;
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (textRecognizer != null) {
            textRecognizer.close();
        }
        if (originalBitmapLoaded != null && !originalBitmapLoaded.isRecycled()) {
            originalBitmapLoaded.recycle();
            originalBitmapLoaded = null;
        }
    }
}