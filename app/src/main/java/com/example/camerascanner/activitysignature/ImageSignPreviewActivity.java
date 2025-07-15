package com.example.camerascanner.activitysignature;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.camerascanner.R;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

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

        // Ánh xạ View
        imageViewPreview = findViewById(R.id.imageViewPreview);
        signatureOverlay = findViewById(R.id.signatureOverlay);
        btnCancel = findViewById(R.id.btnCancelImageSign);
        btnConfirm = findViewById(R.id.btnYesImageSign);

        // Lấy dữ liệu từ Intent
        imageUri = getIntent().getParcelableExtra("imageUri");
        signatureUri = getIntent().getParcelableExtra("signatureUri");
        boundingBoxLeft = getIntent().getFloatExtra("boundingBoxLeft", 0f);
        boundingBoxTop = getIntent().getFloatExtra("boundingBoxTop", 0f);

        // Tải ảnh gốc và chữ ký
        if (imageUri != null) {
            originalImageBitmap = getBitmapFromUri(imageUri);
            imageViewPreview.setImageURI(imageUri);
        }
        loadSignatureBitmap();

        // Cấu hình SignatureOverlay
        setupSignatureOverlay();

        // Thiết lập listener cho các nút
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
        // Đặt SignatureView ở chế độ xem trước, bật hiển thị khung và cho phép resize
        signatureOverlay.setDrawingMode(false);
        signatureOverlay.showSignatureFrame(true);
        signatureOverlay.setFrameResizable(true);
        signatureOverlay.setOnSignatureChangeListener(this);

        // Tải chữ ký bitmap vào SignatureView
        if (signatureBitmap != null) {
            signatureOverlay.post(() -> {
                signatureOverlay.loadBitmap(signatureBitmap);

                // Tính toán vị trí chữ ký trên ImageView theo tỷ lệ
                RectF adjustedFrame = calculateAdjustedSignatureFrame();
                signatureOverlay.setSignatureFrame(adjustedFrame);
            });
            signatureOverlay.setVisibility(View.VISIBLE);
        } else {
            signatureOverlay.setVisibility(View.GONE);
        }
    }

    /**
     * Tính toán vị trí chữ ký trên ImageView dựa trên tỷ lệ scale và offset
     */
    private RectF calculateAdjustedSignatureFrame() {
        if (originalImageBitmap == null || signatureBitmap == null) {
            return new RectF();
        }

        // Lấy kích thước ImageView
        int imageViewWidth = imageViewPreview.getWidth();
        int imageViewHeight = imageViewPreview.getHeight();

        // Lấy kích thước ảnh gốc
        int bitmapWidth = originalImageBitmap.getWidth();
        int bitmapHeight = originalImageBitmap.getHeight();

        // Tính toán scale và offset khi ImageView sử dụng fitCenter
        float scaleX = (float) imageViewWidth / bitmapWidth;
        float scaleY = (float) imageViewHeight / bitmapHeight;
        float scale = Math.min(scaleX, scaleY); // fitCenter sử dụng scale nhỏ nhất

        // Tính toán kích thước ảnh sau khi scale
        float scaledWidth = bitmapWidth * scale;
        float scaledHeight = bitmapHeight * scale;

        // Tính toán offset để căn giữa ảnh trong ImageView
        float offsetX = (imageViewWidth - scaledWidth) / 2;
        float offsetY = (imageViewHeight - scaledHeight) / 2;

        // Chuyển đổi tọa độ từ SignatureActivity sang ImageView
        float signatureWidthInImageView = signatureBitmap.getWidth() * scale;
        float signatureHeightInImageView = signatureBitmap.getHeight() * scale;

        // Tính vị trí chữ ký trên ImageView
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
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            Toast.makeText(this, "Không tìm thấy file chữ ký.", Toast.LENGTH_SHORT).show();
            signatureBitmap = null;
        }
    }

    private void mergeImagesAndFinish() {
        // Lấy khung signatureFrame hiện tại từ SignatureView
        RectF signatureFrame = signatureOverlay.getSignatureFrame();

        if (signatureBitmap == null || originalImageBitmap == null) {
            Toast.makeText(this, "Không có chữ ký hợp lệ hoặc ảnh gốc để hợp nhất.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Xử lý trên luồng nền
        new Thread(() -> {
            try {
                // Resize ảnh gốc nếu cần
                Bitmap processedImage = resizeIfNeeded(originalImageBitmap, 1080, 1920);

                // Chuyển đổi tọa độ từ ImageView về tọa độ ảnh gốc
                RectF realImageFrame = convertUICoordinatesToImageCoordinates(signatureFrame);

                // Hợp nhất ảnh với tọa độ đã chuyển đổi
                Bitmap mergedImage = mergeBitmap(processedImage, signatureBitmap, realImageFrame);

                // Lưu ảnh
                Uri mergedImageUri = saveBitmapToCache(mergedImage);

                // Giải phóng bộ nhớ
                if (mergedImage != null && !mergedImage.isRecycled()) {
                    mergedImage.recycle();
                }

                // Quay lại UI thread
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

    /**
     * Chuyển đổi tọa độ từ UI (ImageView) về tọa độ ảnh gốc
     */
    private RectF convertUICoordinatesToImageCoordinates(RectF uiFrame) {
        if (originalImageBitmap == null) {
            return new RectF();
        }

        // Lấy kích thước ImageView
        int imageViewWidth = imageViewPreview.getWidth();
        int imageViewHeight = imageViewPreview.getHeight();

        // Lấy kích thước ảnh gốc
        int bitmapWidth = originalImageBitmap.getWidth();
        int bitmapHeight = originalImageBitmap.getHeight();

        // Tính toán scale và offset của ImageView (fitCenter)
        float scaleX = (float) imageViewWidth / bitmapWidth;
        float scaleY = (float) imageViewHeight / bitmapHeight;
        float scale = Math.min(scaleX, scaleY);

        // Tính toán offset
        float scaledWidth = bitmapWidth * scale;
        float scaledHeight = bitmapHeight * scale;
        float offsetX = (imageViewWidth - scaledWidth) / 2;
        float offsetY = (imageViewHeight - scaledHeight) / 2;

        // Chuyển đổi ngược lại về tọa độ ảnh gốc
        float realLeft = (uiFrame.left - offsetX) / scale;
        float realTop = (uiFrame.top - offsetY) / scale;
        float realRight = (uiFrame.right - offsetX) / scale;
        float realBottom = (uiFrame.bottom - offsetY) / scale;

        return new RectF(realLeft, realTop, realRight, realBottom);
    }

    private Bitmap mergeBitmap(Bitmap baseBitmap, Bitmap overlayBitmap, RectF overlayFrame) {
        Bitmap mergedBitmap = baseBitmap.copy(baseBitmap.getConfig(), true);
        Canvas canvas = new Canvas(mergedBitmap);
        canvas.drawBitmap(overlayBitmap, null, overlayFrame, null);
        return mergedBitmap;
    }

    private Bitmap getBitmapFromUri(Uri uri) {
        try {
            return BitmapFactory.decodeStream(getContentResolver().openInputStream(uri));
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private Uri saveBitmapToCache(Bitmap bitmap) {
        try {
            File cachePath = new File(getCacheDir(), "merged_images");
            cachePath.mkdirs();
            File file = new File(cachePath, "merged_image_" + System.currentTimeMillis() + ".png");
            FileOutputStream fos = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
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

    // Implement các callback từ SignatureView
    @Override
    public void onSignatureChanged() {}

    @Override
    public void onBoundingBoxDetected(RectF boundingBox) {}

    @Override
    public void onCropBoxChanged(RectF cropBox) {}

    @Override
    public void onFrameResized(RectF frame) {}
}