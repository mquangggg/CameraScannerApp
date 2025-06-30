package com.example.camerascanner;

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
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;

public class CropActivity extends AppCompatActivity {

    private static final String TAG = "CropActivity";
    private CropImageView cropImageView;
    private CustomCropView customCropView;
    private Button btnHuyCrop, btnYesCrop;
    private Uri imageUriToCrop;
    private TextRecognizer textRecognizer;
    private Bitmap originalBitmapLoaded;
    private MagnifierView magnifierView;
    // private List<android.graphics.Rect> recognizedTextRects; // Đã bỏ biến này

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cropimage);

        cropImageView = findViewById(R.id.cropImageView);
        customCropView = findViewById(R.id.customCropView);
        btnHuyCrop = findViewById(R.id.btnHuyCrop);
        btnYesCrop = findViewById(R.id.btnYesCrop);
        magnifierView = findViewById(R.id.magnifierView);

        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

        customCropView.setMagnifierView(magnifierView);

        if (getIntent().getExtras() != null && getIntent().getExtras().containsKey("imageUri")) {
            imageUriToCrop = getIntent().getParcelableExtra("imageUri");
            if (imageUriToCrop != null) {
                cropImageView.setImageUriAsync(imageUriToCrop);
                cropImageView.setGuidelines(CropImageView.Guidelines.OFF);
                try {
                    originalBitmapLoaded = MediaStore.Images.Media.getBitmap(this.getContentResolver(), imageUriToCrop);
                    if (originalBitmapLoaded != null) {
                        cropImageView.post(() -> {
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

                            customCropView.setImageData(originalBitmapLoaded, imageToViewValues, viewToImageValues);

                            processImageForTextDetection(imageUriToCrop);
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
            Toast.makeText(this, getString(R.string.no_image_to_crop), Toast.LENGTH_SHORT).show();
            finish();
        }

        btnHuyCrop.setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });

        btnYesCrop.setOnClickListener(v -> {
            ArrayList<PointF> cropPoints = customCropView.getCropPoints();
            if (cropPoints.size() != 4) {
                Toast.makeText(this, getString(R.string.please_select_4_points_to_crop), Toast.LENGTH_SHORT).show();
                return;
            }

            ArrayList<PointF> transformedPoints = new ArrayList<>();
            for (PointF point : cropPoints) {
                float[] bitmapCoords = transformViewPointToBitmapPoint(point.x, point.y);
                transformedPoints.add(new PointF(bitmapCoords[0], bitmapCoords[1]));
            }

            Bitmap croppedBitmap = cropBitmapByPoints(originalBitmapLoaded, transformedPoints);
            if (croppedBitmap != null) {
                Uri croppedUri = saveBitmapToCache(croppedBitmap);
                Intent intent = new Intent(CropActivity.this, PdfGenerationAndPreviewActivity.class);
                intent.putExtra("croppedUri", croppedUri);
                startActivity(intent);
                finish();
            } else {
                Toast.makeText(this, getString(R.string.error_cropping_image), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private Bitmap cropBitmapByPoints(Bitmap sourceBitmap, ArrayList<PointF> points) {
        if (sourceBitmap == null || points.size() != 4) return null;

        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
        float maxX = Float.MIN_VALUE, maxY = Float.MIN_VALUE;
        for (PointF point : points) {
            minX = Math.min(minX, point.x);
            minY = Math.min(minY, point.y);
            maxX = Math.max(maxX, point.x);
            maxY = Math.max(maxY, point.y);
        }

        minX = Math.max(0f, minX);
        minY = Math.max(0f, minY);
        maxX = Math.min((float) sourceBitmap.getWidth(), maxX);
        maxY = Math.min((float) sourceBitmap.getHeight(), maxY);

        int width = (int) (maxX - minX);
        int height = (int) (maxY - minY);
        if (width <= 0 || height <= 0) return null;

        Bitmap resultBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(resultBitmap);
        Paint paint = new Paint();
        paint.setAntiAlias(true);

        Path adjustedPath = new Path();
        adjustedPath.moveTo(points.get(0).x - minX, points.get(0).y - minY);
        for (int i = 1; i < points.size(); i++) {
            adjustedPath.lineTo(points.get(i).x - minX, points.get(i).y - minY);
        }
        adjustedPath.close();

        canvas.drawPath(adjustedPath, paint);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(sourceBitmap, -minX, -minY, paint);

        return resultBitmap;
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
                            // Không cần lưu recognizedTextRects nữa
                            // recognizedTextRects = new ArrayList<>();
                            // for (Text.TextBlock block : text.getTextBlocks()) {
                            //     if (block.getBoundingBox() != null) {
                            //         recognizedTextRects.add(block.getBoundingBox());
                            //     }
                            // }
                            // customCropView.setTextBoundingBoxes(viewTextRects); // Loại bỏ dòng này

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
            initialBitmapPoints.add(new PointF(0, 0)); // Top-left
            initialBitmapPoints.add(new PointF(bitmapWidth, 0)); // Top-right
            initialBitmapPoints.add(new PointF(bitmapWidth, bitmapHeight)); // Bottom-right
            initialBitmapPoints.add(new PointF(0, bitmapHeight)); // Bottom-left
        } else {
            int padding = 20;
            minX = Math.max(0, minX - padding);
            minY = Math.max(0, minY - padding);
            maxX = Math.min(bitmapWidth, maxX + padding);
            maxY = Math.min(bitmapHeight, maxY + padding);

            initialBitmapPoints.add(new PointF(minX, minY)); // Top-left
            initialBitmapPoints.add(new PointF(maxX, minY)); // Top-right
            initialBitmapPoints.add(new PointF(maxX, maxY)); // Bottom-right
            initialBitmapPoints.add(new PointF(minX, maxY)); // Bottom-left
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
        if (originalBitmapLoaded != null) {
            originalBitmapLoaded.recycle();
            originalBitmapLoaded = null;
        }
    }
}