package com.example.camerascanner.activitysignature;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.RectF;
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
            imageViewPreview.setImageURI(imageUri);
            originalImageBitmap = getBitmapFromUri(imageUri);
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

                // 1. Tính toán bounding box dựa trên tọa độ và kích thước bitmap đã crop
                RectF boundingBox = new RectF(boundingBoxLeft, boundingBoxTop,
                        boundingBoxLeft + signatureBitmap.getWidth(),
                        boundingBoxTop + signatureBitmap.getHeight());

                // 2. Thiết lập khung của SignatureView trùng với Bounding Box này
                signatureOverlay.setSignatureFrame(boundingBox);

            });
            signatureOverlay.setVisibility(View.VISIBLE);
        } else {
            signatureOverlay.setVisibility(View.GONE);
        }
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
        // Lấy bitmap chữ ký từ SignatureView
        Bitmap signatureBitmap = signatureOverlay.getCroppedBitmap();

        // Lấy khung signatureFrame hiện tại từ SignatureView
        RectF signatureFrame = signatureOverlay.getSignatureFrame();

        if (signatureBitmap == null || originalImageBitmap == null) {
            Toast.makeText(this, "Không có chữ ký hợp lệ hoặc ảnh gốc để hợp nhất.", Toast.LENGTH_SHORT).show();
            return;
        }

        // --- Bắt đầu xử lý trên luồng nền để tránh treo UI ---
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // Hợp nhất ảnh (thao tác nặng)
                    originalImageBitmap = resizeIfNeeded(originalImageBitmap, 1080, 1920);


                    Bitmap mergedImage = mergeBitmap(originalImageBitmap, signatureBitmap, signatureFrame);

                    // Lưu ảnh đã hợp nhất vào cache (thao tác nặng)
                    Uri mergedImageUri = saveBitmapToCache(mergedImage);

                    // Giải phóng bộ nhớ của các bitmap tạm thời
                    if (mergedImage != null && !mergedImage.isRecycled()) {
                        mergedImage.recycle();
                    }
                    if (signatureBitmap != null && !signatureBitmap.isRecycled()) {
                        signatureBitmap.recycle();
                    }

                    // Quay lại luồng UI để cập nhật giao diện hoặc kết thúc Activity
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (mergedImageUri != null) {
                                Intent resultIntent = new Intent();
                                resultIntent.putExtra("mergedImageUri", mergedImageUri);
                                setResult(RESULT_OK, resultIntent);
                                finish();
                            } else {
                                Toast.makeText(ImageSignPreviewActivity.this, "Lưu ảnh thất bại.", Toast.LENGTH_SHORT).show();
                            }
                        }
                    });

                } catch (Exception e) {
                    e.printStackTrace();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(ImageSignPreviewActivity.this, "Lỗi khi hợp nhất ảnh: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
        // --- Kết thúc xử lý trên luồng nền ---
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

    // Triển khai Listener từ SignatureView
    @Override
    public void onSignatureChanged() {}

    @Override
    public void onBoundingBoxDetected(RectF boundingBox) {}

    @Override
    public void onCropBoxChanged(RectF cropBox) {}

    @Override
    public void onFrameResized(RectF frame) {}

    private Bitmap resizeIfNeeded(Bitmap bitmap, int maxWidth, int maxHeight) {
        if (bitmap.getWidth() <= maxWidth && bitmap.getHeight() <= maxHeight) return bitmap;
        float ratio = Math.min((float) maxWidth / bitmap.getWidth(), (float) maxHeight / bitmap.getHeight());
        int newWidth = Math.round(bitmap.getWidth() * ratio);
        int newHeight = Math.round(bitmap.getHeight() * ratio);
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
    }
}