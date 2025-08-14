package com.example.camerascanner.activitysignature.signatureview.imagesignpreview;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.camerascanner.BaseActivity;
import com.example.camerascanner.R;
import com.example.camerascanner.activitysignature.signatureview.signature.SignatureView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import yuku.ambilwarna.AmbilWarnaDialog;

public class ImageSignPreviewActivity extends BaseActivity implements SignatureView.OnSignatureChangeListener {

    private ImageView imageViewPreview;
    private SignatureView signatureOverlay;
    private ImageButton btnCancel, btnConfirm;
    private Button btnColorPicker;
    private TextView tvColorHex;

    private Uri imageUri;
    private Uri signatureUri;
    private Bitmap originalImageBitmap;
    private Bitmap originalSignatureBitmap; // Bitmap gốc không thay đổi màu
    private Bitmap currentColoredSignature; // Bitmap đã được apply màu
    private float boundingBoxLeft;
    private float boundingBoxTop;

    // Current selected color
    private int selectedColor = Color.BLACK;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_image);

        initViews();
        getDataFromIntent();
        setupColorPicker();
        loadImageAndSignature();
        setupClickListeners();
    }

    private void initViews() {
        imageViewPreview = findViewById(R.id.imageViewPreview);
        signatureOverlay = findViewById(R.id.signatureOverlay);
        btnCancel = findViewById(R.id.btnCancelImageSign);
        btnConfirm = findViewById(R.id.btnYesImageSign);
        btnColorPicker = findViewById(R.id.btnColorPicker);
        tvColorHex = findViewById(R.id.tvColorHex);
    }

    /**
     * Lấy dữ liệu URI của ảnh và chữ ký, cùng với vị trí ban đầu của chữ ký, từ Intent.
     */
    private void getDataFromIntent() {
        imageUri = getIntent().getParcelableExtra("imageUri");
        signatureUri = getIntent().getParcelableExtra("signatureUri");
        boundingBoxLeft = getIntent().getFloatExtra("boundingBoxLeft", 0f);
        boundingBoxTop = getIntent().getFloatExtra("boundingBoxTop", 0f);
    }

    /**
     * Thiết lập AmbilWarnaDialog để người dùng chọn màu.
     * Cập nhật màu đã chọn vào giao diện và áp dụng màu mới cho chữ ký.
     */
    private void setupColorPicker() {
        // Set initial color
        updateColorPreview(selectedColor);

        // Set click listener for color picker button
        btnColorPicker.setOnClickListener(v -> {
            AmbilWarnaDialog colorPickerDialog = new AmbilWarnaDialog(this, selectedColor, new AmbilWarnaDialog.OnAmbilWarnaListener() {
                @Override
                public void onCancel(AmbilWarnaDialog dialog) {
                    Log.d("AmbilWarna", "Color picker cancelled");
                }

                @Override
                public void onOk(AmbilWarnaDialog dialog, int color) {
                    // User selected a color
                    selectedColor = color;
                    updateColorPreview(color);

                    // Apply new color to signature
                    applyColorToSignatureOverlay();

                    Log.d("AmbilWarna", "Color selected: " + String.format("#%06X", (0xFFFFFF & color)));
                }
            });

            colorPickerDialog.show();
        });
    }

    /**
     * Áp dụng màu mới đã chọn cho chữ ký.
     * Tạo một bitmap chữ ký mới với màu đã chọn và cập nhật lên `signatureOverlay`.
     */
    private void applyColorToSignatureOverlay() {
        if (originalSignatureBitmap != null) {
            // Tạo bitmap mới với màu đã chọn
            currentColoredSignature = createColoredSignatureBitmap(originalSignatureBitmap, selectedColor);

            // Load bitmap mới vào SignatureView
            if (currentColoredSignature != null) {
                signatureOverlay.loadBitmap(currentColoredSignature);
                signatureOverlay.invalidate(); // Force redraw
            }
        }
    }

    /**
     * Tạo một bản sao của bitmap chữ ký ban đầu và áp dụng một lớp màu mới lên nó.
     *
     * @param originalBitmap Bitmap chữ ký gốc.
     * @param newColor       Màu mới muốn áp dụng.
     * @return Bitmap chữ ký mới đã được tô màu.
     */
    private Bitmap createColoredSignatureBitmap(Bitmap originalBitmap, int newColor) {
        if (originalBitmap == null) return null;

        // Tạo bản sao có thể chỉnh sửa
        Bitmap coloredBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true);

        // Tạo canvas để vẽ lên bitmap
        Canvas canvas = new Canvas(coloredBitmap);

        // Tạo đối tượng Paint với bộ lọc màu (color filter)
        Paint paint = new Paint();
        paint.setColorFilter(new android.graphics.PorterDuffColorFilter(newColor, PorterDuff.Mode.SRC_ATOP));

        // Vẽ bitmap gốc với bộ lọc màu lên canvas
        canvas.drawBitmap(originalBitmap, 0, 0, paint);

        return coloredBitmap;
    }

    /**
     * Cập nhật giao diện của nút chọn màu và text hiển thị mã màu.
     */
    private void updateColorPreview(int color) {
        // Update button background color
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(color);
        drawable.setStroke(4, Color.WHITE);
        drawable.setCornerRadius(8);
        btnColorPicker.setBackground(drawable);

        // Update hex text
        String hexColor = String.format("#%06X", (0xFFFFFF & color));
        tvColorHex.setText(hexColor);

        // Add subtle animation
        btnColorPicker.animate()
                .scaleX(1.1f)
                .scaleY(1.1f)
                .setDuration(100)
                .withEndAction(() -> {
                    btnColorPicker.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(100)
                            .start();
                })
                .start();
    }

    /**
     * Tải ảnh gốc và chữ ký từ các URI tương ứng.
     * Thiết lập `SignatureOverlay` sau khi `ImageView` đã được layout.
     */
    private void loadImageAndSignature() {
        if (imageUri != null) {
            originalImageBitmap = getBitmapFromUri(imageUri);
            imageViewPreview.setImageBitmap(originalImageBitmap);
        }

        loadSignatureBitmap();

        imageViewPreview.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            setupSignatureOverlay();
        });
    }

    private void setupClickListeners() {
        btnConfirm.setOnClickListener(v -> {
            if (imageUri != null && signatureUri != null && currentColoredSignature != null) {
                mergeImagesAndFinish();
            } else {
                Toast.makeText(this, getString(R.string.error_merging_images_missing_elements), Toast.LENGTH_SHORT).show();
                finish();
            }
        });

        btnCancel.setOnClickListener(v -> finish());
    }

    /**
     * Thiết lập `SignatureView` để hiển thị và tương tác với chữ ký.
     * Cài đặt chế độ không vẽ, hiển thị khung và khả năng thay đổi kích thước.
     * Tính toán vị trí ban đầu của khung chữ ký dựa trên dữ liệu bounding box đã truyền.
     */
    private void setupSignatureOverlay() {
        signatureOverlay.setDrawingMode(false);
        signatureOverlay.showSignatureFrame(true);
        signatureOverlay.setFrameResizable(true);
        signatureOverlay.setOnSignatureChangeListener(this);

        if (originalSignatureBitmap != null) {
            // Apply initial color và load vào overlay
            applyColorToSignatureOverlay();

            signatureOverlay.post(() -> {
                RectF adjustedFrame = calculateAdjustedSignatureFrame();
                Log.d("DEBUG", "Adjusted frame: " + adjustedFrame);
                signatureOverlay.setSignatureFrame(adjustedFrame);
            });
            signatureOverlay.setVisibility(View.VISIBLE);
        } else {
            signatureOverlay.setVisibility(View.GONE);
        }
    }

    /**
     * Chuyển đổi tọa độ bounding box của chữ ký từ kích thước bitmap gốc sang kích thước
     * tương ứng trên `ImageView` hiển thị.
     *
     * @return Một `RectF` chứa tọa độ đã điều chỉnh cho `SignatureView`.
     */
    private RectF calculateAdjustedSignatureFrame() {
        if (originalImageBitmap == null || originalSignatureBitmap == null) return new RectF();

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

        float signatureWidthInImageView = originalSignatureBitmap.getWidth() * scale;
        float signatureHeightInImageView = originalSignatureBitmap.getHeight() * scale;
        float signatureLeftInImageView = offsetX + (boundingBoxLeft * scale);
        float signatureTopInImageView = offsetY + (boundingBoxTop * scale);

        return new RectF(
                signatureLeftInImageView,
                signatureTopInImageView,
                signatureLeftInImageView + signatureWidthInImageView,
                signatureTopInImageView + signatureHeightInImageView
        );
    }

    /**
     * Tải bitmap chữ ký từ URI.
     * Tạo một phiên bản bitmap đã được tô màu dựa trên màu đã chọn ban đầu.
     */
    private void loadSignatureBitmap() {
        if (signatureUri == null) {
            originalSignatureBitmap = null;
            currentColoredSignature = null;
            return;
        }
        try {
            originalSignatureBitmap = BitmapFactory.decodeStream(getContentResolver().openInputStream(signatureUri));
            // Tạo version với màu hiện tại
            if (originalSignatureBitmap != null) {
                currentColoredSignature = createColoredSignatureBitmap(originalSignatureBitmap, selectedColor);
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Không tìm thấy file chữ ký.", Toast.LENGTH_SHORT).show();
            originalSignatureBitmap = null;
            currentColoredSignature = null;
        }
    }

    /**
     * Hợp nhất chữ ký và ảnh gốc trên một luồng nền.
     *
     * 1. Lấy khung và góc xoay cuối cùng của chữ ký từ `SignatureView`.
     * 2. Chuyển đổi tọa độ từ UI sang tọa độ bitmap thực tế.
     * 3. Hợp nhất hai bitmap (ảnh gốc và chữ ký đã xoay).
     * 4. Lưu ảnh đã hợp nhất vào bộ nhớ cache.
     * 5. Gửi URI của ảnh đã hợp nhất trở lại Activity gọi và kết thúc.
     */
    private void mergeImagesAndFinish() {
        RectF signatureFrame = signatureOverlay.getSignatureFrame();
        float rotationAngle = signatureOverlay.getRotationAngle();

        if (currentColoredSignature == null || originalImageBitmap == null) {
            Toast.makeText(this, "Không có chữ ký hợp lệ hoặc ảnh gốc để hợp nhất.", Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(() -> {
            try {
                // Giảm kích thước ảnh gốc nếu cần để tránh lỗi bộ nhớ
                Bitmap processedImage = resizeIfNeeded(originalImageBitmap, 1080, 1920);
                // Chuyển đổi khung chữ ký từ tọa độ màn hình sang tọa độ bitmap
                RectF realImageFrame = convertUICoordinatesToImageCoordinates(signatureFrame, processedImage);

                Log.d("DEBUG", "Converted frame: " + realImageFrame);
                Log.d("DEBUG", "Rotation angle: " + Math.toDegrees(rotationAngle));
                Log.d("DEBUG", "Selected color: " + String.format("#%06X", (0xFFFFFF & selectedColor)));

                // Hợp nhất ảnh gốc và chữ ký
                Bitmap mergedImage = mergeBitmapWithRotation(processedImage, currentColoredSignature, realImageFrame, rotationAngle);
                // Lưu ảnh đã hợp nhất vào bộ nhớ cache
                Uri mergedImageUri = saveBitmapToCache(mergedImage);

                if (mergedImage != null && !mergedImage.isRecycled()) mergedImage.recycle();

                runOnUiThread(() -> {
                    if (mergedImageUri != null) {
                        Intent resultIntent = new Intent();
                        resultIntent.putExtra("mergedImageUri", mergedImageUri);
                        resultIntent.putExtra("selectedColor", selectedColor);
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
     * Chuyển đổi tọa độ của khung chữ ký từ không gian UI (trên ImageView) sang
     * không gian của bitmap gốc.
     *
     * @param uiFrame      Khung chữ ký trong tọa độ UI.
     * @param actualBitmap Bitmap gốc.
     * @return `RectF` chứa tọa độ đã được chuyển đổi.
     */
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

    /**
     * Hợp nhất hai bitmap: bitmap nền và bitmap chữ ký.
     * Chữ ký sẽ được vẽ lên bitmap nền tại vị trí và góc xoay được chỉ định.
     *
     * @param baseBitmap    Bitmap ảnh nền.
     * @param overlayBitmap Bitmap chữ ký.
     * @param overlayFrame  Vị trí và kích thước của chữ ký trên bitmap nền.
     * @param rotationAngle Góc xoay của chữ ký (radian).
     * @return Bitmap đã được hợp nhất.
     */
    private Bitmap mergeBitmapWithRotation(Bitmap baseBitmap, Bitmap overlayBitmap, RectF overlayFrame, float rotationAngle) {
        Bitmap mergedBitmap = baseBitmap.copy(baseBitmap.getConfig(), true);
        Canvas canvas = new Canvas(mergedBitmap);

        float centerX = overlayFrame.centerX();
        float centerY = overlayFrame.centerY();

        canvas.save();

        if (rotationAngle != 0f) {
            canvas.rotate((float) Math.toDegrees(rotationAngle), centerX, centerY);
        }

        canvas.drawBitmap(overlayBitmap, null, overlayFrame, null);

        canvas.restore();

        return mergedBitmap;
    }

    /**
     * Lấy bitmap từ một URI, xử lý xoay ảnh dựa trên dữ liệu EXIF.
     *
     * @param uri URI của file ảnh.
     * @return Bitmap đã được xử lý.
     */
    private Bitmap getBitmapFromUri(Uri uri) {
        try {
            InputStream input = getContentResolver().openInputStream(uri);
            Bitmap bitmap = BitmapFactory.decodeStream(input);

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

    /**
     * Chuyển đổi giá trị hướng ảnh từ EXIF thành góc xoay (độ).
     *
     * @param exifOrientation Giá trị hướng ảnh từ EXIF.
     * @return Góc xoay tương ứng (0, 90, 180, 270).
     */
    private int exifToDegrees(int exifOrientation) {
        switch (exifOrientation) {
            case ExifInterface.ORIENTATION_ROTATE_90: return 90;
            case ExifInterface.ORIENTATION_ROTATE_180: return 180;
            case ExifInterface.ORIENTATION_ROTATE_270: return 270;
            default: return 0;
        }
    }

    /**
     * Lưu một bitmap vào thư mục cache của ứng dụng dưới dạng file JPEG.
     *
     * @param bitmap Bitmap cần lưu.
     * @return URI của file đã lưu, hoặc null nếu lỗi.
     */
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

    /**
     * Giảm kích thước bitmap nếu nó lớn hơn kích thước tối đa cho phép.
     *
     * @param bitmap   Bitmap cần kiểm tra.
     * @param maxWidth  Chiều rộng tối đa.
     * @param maxHeight Chiều cao tối đa.
     * @return Bitmap đã được resize hoặc bitmap gốc nếu không cần.
     */
    private Bitmap resizeIfNeeded(Bitmap bitmap, int maxWidth, int maxHeight) {
        if (bitmap.getWidth() <= maxWidth && bitmap.getHeight() <= maxHeight) return bitmap;
        float ratio = Math.min((float) maxWidth / bitmap.getWidth(), (float) maxHeight / bitmap.getHeight());
        int newWidth = Math.round(bitmap.getWidth() * ratio);
        int newHeight = Math.round(bitmap.getHeight() * ratio);
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
    }

    // Interface callbacks
    @Override
    public void onSignatureChanged() {}

    @Override
    public void onBoundingBoxDetected(RectF boundingBox) {}

    @Override
    public void onCropBoxChanged(RectF cropBox) {}

    @Override
    public void onFrameResized(RectF frame) {}

    /**
     * Giải phóng tài nguyên bitmap khi Activity bị hủy để tránh rò rỉ bộ nhớ.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up bitmaps to prevent memory leaks
        if (originalImageBitmap != null && !originalImageBitmap.isRecycled()) {
            originalImageBitmap.recycle();
        }
        if (originalSignatureBitmap != null && !originalSignatureBitmap.isRecycled()) {
            originalSignatureBitmap.recycle();
        }
        if (currentColoredSignature != null && !currentColoredSignature.isRecycled()) {
            currentColoredSignature.recycle();
        }
    }
}