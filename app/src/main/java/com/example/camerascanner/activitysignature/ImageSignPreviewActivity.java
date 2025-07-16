package com.example.camerascanner.activitysignature;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.camerascanner.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class ImageSignPreviewActivity extends AppCompatActivity implements SignatureView.OnSignatureChangeListener {

    private ImageView imageViewPreview;
    private SignatureView signatureOverlay;
    private ImageButton btnCancel, btnConfirm;

    private Uri imageUri;
    private Uri signatureUri;
    private Bitmap originalImageBitmap;
    private Bitmap signatureBitmap;
    private float boundingBoxLeft;
    private float boundingBoxTop;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_image);

        imageViewPreview = findViewById(R.id.imageViewPreview);
        signatureOverlay = findViewById(R.id.signatureOverlay);
        btnCancel = findViewById(R.id.btnCancelImageSign);
        btnConfirm = findViewById(R.id.btnYesImageSign);

        imageUri = getIntent().getParcelableExtra("imageUri");
        signatureUri = getIntent().getParcelableExtra("signatureUri");
        boundingBoxLeft = getIntent().getFloatExtra("boundingBoxLeft", 0f);
        boundingBoxTop = getIntent().getFloatExtra("boundingBoxTop", 0f);

        if (imageUri != null) {
            originalImageBitmap = getBitmapFromUri(imageUri);
            imageViewPreview.setImageBitmap(originalImageBitmap); // dùng setImageBitmap để tránh delay render
        }

        loadSignatureBitmap();

        imageViewPreview.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            setupSignatureOverlay();
        });

        btnConfirm.setOnClickListener(v -> {
            if (imageUri != null && signatureUri != null && signatureBitmap != null) {
                mergeImagesAndFinish();
            } else {
                Toast.makeText(this, "Không thể hợp nhất ảnh. Thiếu ảnh gốc hoặc chữ ký.", Toast.LENGTH_SHORT).show();
                finish();
            }
        });

        btnCancel.setOnClickListener(v -> finish());
    }

    private void setupSignatureOverlay() {
        signatureOverlay.setDrawingMode(false);
        signatureOverlay.showSignatureFrame(true);
        signatureOverlay.setFrameResizable(true);
        signatureOverlay.setOnSignatureChangeListener(this);

        if (signatureBitmap != null) {
            signatureOverlay.post(() -> {
                signatureOverlay.loadBitmap(signatureBitmap);
                RectF adjustedFrame = calculateAdjustedSignatureFrame();
                Log.d("DEBUG", "Adjusted frame: " + adjustedFrame.toString());
                signatureOverlay.setSignatureFrame(adjustedFrame);
            });
            signatureOverlay.setVisibility(View.VISIBLE);
        } else {
            signatureOverlay.setVisibility(View.GONE);
        }
    }

    private RectF calculateAdjustedSignatureFrame() {
        if (originalImageBitmap == null || signatureBitmap == null) return new RectF();

        int imageViewWidth = imageViewPreview.getWidth();
        int imageViewHeight = imageViewPreview.getHeight();
        int bitmapWidth = originalImageBitmap.getWidth();
        int bitmapHeight = originalImageBitmap.getHeight();

        float scaleX = (float) imageViewWidth / bitmapWidth;
        float scaleY = (float) imageViewHeight / bitmapHeight;
        float scale = Math.min(scaleX, scaleY);

        float scaledWidth = bitmapWidth * scale;
        float scaledHeight = bitmapHeight * scale;
        float offsetX = (imageViewWidth - scaledWidth) / 2;
        float offsetY = (imageViewHeight - scaledHeight) / 2;

        float signatureWidthInImageView = signatureBitmap.getWidth() * scale;
        float signatureHeightInImageView = signatureBitmap.getHeight() * scale;
        float signatureLeftInImageView = offsetX + (boundingBoxLeft * scale);
        float signatureTopInImageView = offsetY + (boundingBoxTop * scale);

        return new RectF(
                signatureLeftInImageView,
                signatureTopInImageView,
                signatureLeftInImageView + signatureWidthInImageView,
                signatureTopInImageView + signatureHeightInImageView
        );
    }

    private void loadSignatureBitmap() {
        if (signatureUri == null) {
            signatureBitmap = null;
            return;
        }
        try {
            signatureBitmap = BitmapFactory.decodeStream(getContentResolver().openInputStream(signatureUri));
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Không tìm thấy file chữ ký.", Toast.LENGTH_SHORT).show();
            signatureBitmap = null;
        }
    }

    private void mergeImagesAndFinish() {
        RectF signatureFrame = signatureOverlay.getSignatureFrame();
        float rotationAngle = signatureOverlay.getRotationAngle();

        if (signatureBitmap == null || originalImageBitmap == null) {
            Toast.makeText(this, "Không có chữ ký hợp lệ hoặc ảnh gốc để hợp nhất.", Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(() -> {
            try {
                Bitmap processedImage = resizeIfNeeded(originalImageBitmap, 1080, 1920);
                RectF realImageFrame = convertUICoordinatesToImageCoordinates(signatureFrame, processedImage);
                Log.d("DEBUG", "Converted frame: " + realImageFrame.toString());
                Log.d("DEBUG", "Rotation angle: " + Math.toDegrees(rotationAngle));

                Bitmap mergedImage = mergeBitmapWithRotation(processedImage, signatureBitmap, realImageFrame, rotationAngle);
                Uri mergedImageUri = saveBitmapToCache(mergedImage);

                if (mergedImage != null && !mergedImage.isRecycled()) mergedImage.recycle();

                runOnUiThread(() -> {
                    if (mergedImageUri != null) {
                        Intent resultIntent = new Intent();
                        resultIntent.putExtra("mergedImageUri", mergedImageUri);
                        setResult(RESULT_OK, resultIntent);
                        finish();
                    } else {
                        Toast.makeText(this, "Lưu ảnh thất bại.", Toast.LENGTH_SHORT).show();
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() ->
                        Toast.makeText(this, "Lỗi khi hợp nhất ảnh: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
            }
        }).start();
    }

    private RectF convertUICoordinatesToImageCoordinates(RectF uiFrame, Bitmap actualBitmap) {
        if (actualBitmap == null) return new RectF();

        int imageViewWidth = imageViewPreview.getWidth();
        int imageViewHeight = imageViewPreview.getHeight();
        int bitmapWidth = actualBitmap.getWidth();
        int bitmapHeight = actualBitmap.getHeight();

        float scaleX = (float) imageViewWidth / bitmapWidth;
        float scaleY = (float) imageViewHeight / bitmapHeight;
        float scale = Math.min(scaleX, scaleY);

        float scaledWidth = bitmapWidth * scale;
        float scaledHeight = bitmapHeight * scale;
        float offsetX = (imageViewWidth - scaledWidth) / 2;
        float offsetY = (imageViewHeight - scaledHeight) / 2;

        float realLeft = (uiFrame.left - offsetX) / scale;
        float realTop = (uiFrame.top - offsetY) / scale;
        float realRight = (uiFrame.right - offsetX) / scale;
        float realBottom = (uiFrame.bottom - offsetY) / scale;

        return new RectF(realLeft, realTop, realRight, realBottom);
    }

    private Bitmap mergeBitmapWithRotation(Bitmap baseBitmap, Bitmap overlayBitmap, RectF overlayFrame, float rotationAngle) {
        Bitmap mergedBitmap = baseBitmap.copy(baseBitmap.getConfig(), true);
        Canvas canvas = new Canvas(mergedBitmap);

        // Tính toán center của overlay frame
        float centerX = overlayFrame.centerX();
        float centerY = overlayFrame.centerY();

        // Lưu trạng thái canvas
        canvas.save();

        // Áp dụng rotation quanh center của overlay frame
        if (rotationAngle != 0f) {
            canvas.rotate((float) Math.toDegrees(rotationAngle), centerX, centerY);
        }

        // Vẽ overlay bitmap
        canvas.drawBitmap(overlayBitmap, null, overlayFrame, null);

        // Khôi phục trạng thái canvas
        canvas.restore();

        return mergedBitmap;
    }

    private Bitmap mergeBitmap(Bitmap baseBitmap, Bitmap overlayBitmap, RectF overlayFrame) {
        Bitmap mergedBitmap = baseBitmap.copy(baseBitmap.getConfig(), true);
        Canvas canvas = new Canvas(mergedBitmap);
        canvas.drawBitmap(overlayBitmap, null, overlayFrame, null);
        return mergedBitmap;
    }

    private Bitmap getBitmapFromUri(Uri uri) {
        try {
            InputStream input = getContentResolver().openInputStream(uri);
            Bitmap bitmap = BitmapFactory.decodeStream(input);

            // Xử lý xoay ảnh từ Exif
            InputStream exifStream = getContentResolver().openInputStream(uri);
            ExifInterface exif = new ExifInterface(exifStream);
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            int rotation = exifToDegrees(orientation);
            if (rotation != 0) {
                Matrix matrix = new Matrix();
                matrix.postRotate(rotation);
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            }

            return bitmap;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private int exifToDegrees(int exifOrientation) {
        switch (exifOrientation) {
            case ExifInterface.ORIENTATION_ROTATE_90: return 90;
            case ExifInterface.ORIENTATION_ROTATE_180: return 180;
            case ExifInterface.ORIENTATION_ROTATE_270: return 270;
            default: return 0;
        }
    }

    private Uri saveBitmapToCache(Bitmap bitmap) {
        try {
            File cachePath = new File(getCacheDir(), "merged_images");
            cachePath.mkdirs();
            File file = new File(cachePath, "merged_image_" + System.currentTimeMillis() + ".jpg");
            FileOutputStream fos = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
            fos.close();
            return Uri.fromFile(file);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private Bitmap resizeIfNeeded(Bitmap bitmap, int maxWidth, int maxHeight) {
        if (bitmap.getWidth() <= maxWidth && bitmap.getHeight() <= maxHeight) return bitmap;
        float ratio = Math.min((float) maxWidth / bitmap.getWidth(), (float) maxHeight / bitmap.getHeight());
        int newWidth = Math.round(bitmap.getWidth() * ratio);
        int newHeight = Math.round(bitmap.getHeight() * ratio);
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
    }

    @Override
    public void onSignatureChanged() {}

    @Override
    public void onBoundingBoxDetected(RectF boundingBox) {}

    @Override
    public void onCropBoxChanged(RectF cropBox) {}

    @Override
    public void onFrameResized(RectF frame) {}
}