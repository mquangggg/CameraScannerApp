package com.example.camerascanner.activitysignature;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.camerascanner.R;
import com.example.camerascanner.activitysignature.signatureview.SignatureView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * ImageSignPreviewActivity hiển thị ảnh gốc và cho phép người dùng đặt chữ ký lên ảnh.
 * Người dùng có thể di chuyển, thay đổi kích thước và xoay chữ ký trước khi hợp nhất vào ảnh.
 */
public class ImageSignPreviewActivity extends AppCompatActivity implements SignatureView.OnSignatureChangeListener {

    private ImageView imageViewPreview; // ImageView hiển thị ảnh gốc
    private SignatureView signatureOverlay; // Custom View để hiển thị và thao tác với chữ ký
    private ImageButton btnCancel, btnConfirm; // Các nút hủy và xác nhận

    private Uri imageUri; // URI của ảnh gốc
    private Uri signatureUri; // URI của chữ ký
    private Bitmap originalImageBitmap; // Bitmap của ảnh gốc
    private Bitmap signatureBitmap; // Bitmap của chữ ký
    private float boundingBoxLeft; // Tọa độ X ban đầu của hộp giới hạn chữ ký (tương đối với ảnh gốc)
    private float boundingBoxTop; // Tọa độ Y ban đầu của hộp giới hạn chữ ký (tương đối với ảnh gốc)

    /**
     * Phương thức được gọi khi Activity được tạo lần đầu.
     * Khởi tạo layout, lấy dữ liệu từ Intent và thiết lập các listeners.
     * @param savedInstanceState Đối tượng Bundle chứa trạng thái Activity đã lưu.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_image); // Đặt layout cho Activity

        // Ánh xạ các View từ layout
        imageViewPreview = findViewById(R.id.imageViewPreview);
        signatureOverlay = findViewById(R.id.signatureOverlay);
        btnCancel = findViewById(R.id.btnCancelImageSign);
        btnConfirm = findViewById(R.id.btnYesImageSign);

        // Lấy dữ liệu từ Intent
        imageUri = getIntent().getParcelableExtra("imageUri");
        signatureUri = getIntent().getParcelableExtra("signatureUri");
        boundingBoxLeft = getIntent().getFloatExtra("boundingBoxLeft", 0f);
        boundingBoxTop = getIntent().getFloatExtra("boundingBoxTop", 0f);

        // Nếu có URI ảnh, tải bitmap và hiển thị lên ImageView
        if (imageUri != null) {
            originalImageBitmap = getBitmapFromUri(imageUri);
            imageViewPreview.setImageBitmap(originalImageBitmap); // dùng setImageBitmap để tránh delay render
        }

        loadSignatureBitmap(); // Tải bitmap chữ ký

        // Đảm bảo ImageView đã được layout xong trước khi thiết lập SignatureOverlay
        imageViewPreview.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            setupSignatureOverlay();
        });

        // Thiết lập listener cho nút xác nhận
        btnConfirm.setOnClickListener(v -> {
            if (imageUri != null && signatureUri != null && signatureBitmap != null) {
                mergeImagesAndFinish(); // Hợp nhất ảnh và kết thúc Activity
            } else {
                Toast.makeText(this, getString(R.string.error_merging_images_missing_elements), Toast.LENGTH_SHORT).show();
                finish(); // Kết thúc Activity nếu thiếu dữ liệu
            }
        });

        // Thiết lập listener cho nút hủy
        btnCancel.setOnClickListener(v -> finish());
    }

    /**
     * Thiết lập SignatureOverlay: đặt chế độ, hiển thị khung và đăng ký listener.
     * Nếu có chữ ký, tải chữ ký và điều chỉnh vị trí/kích thước khung.
     */
    private void setupSignatureOverlay() {
        signatureOverlay.setDrawingMode(false); // Đặt chế độ không vẽ (chỉ chỉnh sửa)
        signatureOverlay.showSignatureFrame(true); // Hiển thị khung chữ ký
        signatureOverlay.setFrameResizable(true); // Cho phép thay đổi kích thước khung
        signatureOverlay.setOnSignatureChangeListener(this); // Đăng ký listener để nhận các sự kiện thay đổi

        if (signatureBitmap != null) {
            // Đảm bảo SignatureOverlay đã được layout trước khi tải bitmap và điều chỉnh khung
            signatureOverlay.post(() -> {
                signatureOverlay.loadBitmap(signatureBitmap); // Tải bitmap chữ ký vào overlay
                RectF adjustedFrame = calculateAdjustedSignatureFrame(); // Tính toán khung chữ ký đã điều chỉnh
                Log.d("DEBUG", "Adjusted frame: " + adjustedFrame.toString());
                signatureOverlay.setSignatureFrame(adjustedFrame); // Đặt khung chữ ký đã điều chỉnh
            });
            signatureOverlay.setVisibility(View.VISIBLE); // Hiển thị SignatureOverlay
        } else {
            signatureOverlay.setVisibility(View.GONE); // Ẩn SignatureOverlay nếu không có chữ ký
        }
    }

    /**
     * Tính toán kích thước và vị trí của khung chữ ký trên SignatureOverlay
     * dựa trên kích thước của ImageView và ảnh gốc, cùng với vị trí bounding box ban đầu.
     * @return RectF đại diện cho khung chữ ký đã điều chỉnh trong không gian của ImageView.
     */
    private RectF calculateAdjustedSignatureFrame() {
        if (originalImageBitmap == null || signatureBitmap == null) return new RectF(); // Trả về RectF rỗng nếu thiếu bitmap

        int imageViewWidth = imageViewPreview.getWidth();
        int imageViewHeight = imageViewPreview.getHeight();
        int bitmapWidth = originalImageBitmap.getWidth();
        int bitmapHeight = originalImageBitmap.getHeight();

        // Tính toán tỷ lệ scaling để ảnh vừa với ImageView
        float scaleX = (float) imageViewWidth / bitmapWidth;
        float scaleY = (float) imageViewHeight / bitmapHeight;
        float scale = Math.min(scaleX, scaleY);

        // Tính toán kích thước ảnh đã scale và offset để căn giữa trong ImageView
        float scaledWidth = bitmapWidth * scale;
        float scaledHeight = bitmapHeight * scale;
        float offsetX = (imageViewWidth - scaledWidth) / 2;
        float offsetY = (imageViewHeight - scaledHeight) / 2;

        // Tính toán kích thước và vị trí chữ ký trong không gian của ImageView
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

    /**
     * Tải bitmap chữ ký từ URI được cung cấp.
     */
    private void loadSignatureBitmap() {
        if (signatureUri == null) {
            signatureBitmap = null;
            return;
        }
        try {
            signatureBitmap = BitmapFactory.decodeStream(getContentResolver().openInputStream(signatureUri)); // Giải mã bitmap từ InputStream
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Không tìm thấy file chữ ký.", Toast.LENGTH_SHORT).show();
            signatureBitmap = null;
        }
    }

    /**
     * Hợp nhất ảnh gốc và chữ ký, sau đó kết thúc Activity và trả về URI của ảnh đã hợp nhất.
     * Thực hiện trong một luồng riêng để tránh chặn UI.
     */
    private void mergeImagesAndFinish() {
        RectF signatureFrame = signatureOverlay.getSignatureFrame(); // Lấy khung chữ ký hiện tại từ overlay
        float rotationAngle = signatureOverlay.getRotationAngle(); // Lấy góc xoay hiện tại của chữ ký

        if (signatureBitmap == null || originalImageBitmap == null) {
            Toast.makeText(this, "Không có chữ ký hợp lệ hoặc ảnh gốc để hợp nhất.", Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(() -> { // Chạy trong một luồng nền
            try {
                // Thay đổi kích thước ảnh gốc nếu cần thiết để tối ưu hóa việc hợp nhất
                Bitmap processedImage = resizeIfNeeded(originalImageBitmap, 1080, 1920);
                // Chuyển đổi tọa độ khung chữ ký từ không gian UI sang không gian của ảnh thật
                RectF realImageFrame = convertUICoordinatesToImageCoordinates(signatureFrame, processedImage);
                Log.d("DEBUG", "Converted frame: " + realImageFrame.toString());
                Log.d("DEBUG", "Rotation angle: " + Math.toDegrees(rotationAngle));

                // Hợp nhất ảnh gốc và chữ ký với góc xoay đã cho
                Bitmap mergedImage = mergeBitmapWithRotation(processedImage, signatureBitmap, realImageFrame, rotationAngle);
                Uri mergedImageUri = saveBitmapToCache(mergedImage); // Lưu ảnh đã hợp nhất vào cache

                // Giải phóng bitmap đã hợp nhất nếu không còn cần thiết
                if (mergedImage != null && !mergedImage.isRecycled()) mergedImage.recycle();

                runOnUiThread(() -> { // Chạy trên luồng UI
                    if (mergedImageUri != null) {
                        Intent resultIntent = new Intent();
                        resultIntent.putExtra("mergedImageUri", mergedImageUri); // Đặt URI ảnh đã hợp nhất vào Intent kết quả
                        setResult(RESULT_OK, resultIntent); // Đặt kết quả OK
                        finish(); // Kết thúc Activity
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
     * Chuyển đổi tọa độ của một khung từ không gian UI (ImageView) sang không gian của ảnh Bitmap thực tế.
     * @param uiFrame RectF của khung trong không gian UI.
     * @param actualBitmap Bitmap thực tế mà khung sẽ được áp dụng.
     * @return RectF của khung trong không gian tọa độ của Bitmap thực tế.
     */
    private RectF convertUICoordinatesToImageCoordinates(RectF uiFrame, Bitmap actualBitmap) {
        if (actualBitmap == null) return new RectF();

        int imageViewWidth = imageViewPreview.getWidth();
        int imageViewHeight = imageViewPreview.getHeight();
        int bitmapWidth = actualBitmap.getWidth();
        int bitmapHeight = actualBitmap.getHeight();

        // Tính toán tỷ lệ scaling và offset tương tự như calculateAdjustedSignatureFrame
        float scaleX = (float) imageViewWidth / bitmapWidth;
        float scaleY = (float) imageViewHeight / bitmapHeight;
        float scale = Math.min(scaleX, scaleY);

        float scaledWidth = bitmapWidth * scale;
        float scaledHeight = bitmapHeight * scale;
        float offsetX = (imageViewWidth - scaledWidth) / 2;
        float offsetY = (imageViewHeight - scaledHeight) / 2;

        // Chuyển đổi ngược từ tọa độ UI sang tọa độ ảnh
        float realLeft = (uiFrame.left - offsetX) / scale;
        float realTop = (uiFrame.top - offsetY) / scale;
        float realRight = (uiFrame.right - offsetX) / scale;
        float realBottom = (uiFrame.bottom - offsetY) / scale;

        return new RectF(realLeft, realTop, realRight, realBottom);
    }

    /**
     * Hợp nhất một bitmap overlay lên một bitmap nền, áp dụng xoay.
     * @param baseBitmap Bitmap nền.
     * @param overlayBitmap Bitmap sẽ được phủ lên.
     * @param overlayFrame RectF định vị và kích thước của overlayBitmap trên baseBitmap.
     * @param rotationAngle Góc xoay của overlayBitmap (radian).
     * @return Bitmap đã được hợp nhất.
     */
    private Bitmap mergeBitmapWithRotation(Bitmap baseBitmap, Bitmap overlayBitmap, RectF overlayFrame, float rotationAngle) {
        Bitmap mergedBitmap = baseBitmap.copy(baseBitmap.getConfig(), true); // Tạo bản sao của bitmap nền để vẽ lên
        Canvas canvas = new Canvas(mergedBitmap); // Tạo Canvas từ bitmap đã sao chép

        // Tính toán tâm của khung overlay
        float centerX = overlayFrame.centerX();
        float centerY = overlayFrame.centerY();

        canvas.save(); // Lưu trạng thái hiện tại của canvas

        // Áp dụng xoay quanh tâm của khung overlay
        if (rotationAngle != 0f) {
            canvas.rotate((float) Math.toDegrees(rotationAngle), centerX, centerY);
        }

        canvas.drawBitmap(overlayBitmap, null, overlayFrame, null); // Vẽ overlay bitmap lên canvas

        canvas.restore(); // Khôi phục trạng thái canvas đã lưu

        return mergedBitmap;
    }

    /**
     * Hợp nhất một bitmap overlay lên một bitmap nền mà không áp dụng xoay.
     * (Phương thức này có thể không còn được sử dụng nếu `mergeBitmapWithRotation` là đủ).
     * @param baseBitmap Bitmap nền.
     * @param overlayBitmap Bitmap sẽ được phủ lên.
     * @param overlayFrame RectF định vị và kích thước của overlayBitmap trên baseBitmap.
     * @return Bitmap đã được hợp nhất.
     */
    private Bitmap mergeBitmap(Bitmap baseBitmap, Bitmap overlayBitmap, RectF overlayFrame) {
        Bitmap mergedBitmap = baseBitmap.copy(baseBitmap.getConfig(), true); // Tạo bản sao của bitmap nền
        Canvas canvas = new Canvas(mergedBitmap); // Tạo Canvas từ bitmap đã sao chép
        canvas.drawBitmap(overlayBitmap, null, overlayFrame, null); // Vẽ overlay bitmap lên canvas
        return mergedBitmap;
    }

    /**
     * Tải một Bitmap từ Uri, bao gồm xử lý xoay ảnh dựa trên thông tin Exif.
     * @param uri Uri của ảnh.
     * @return Bitmap đã được giải mã và xoay đúng hướng, hoặc null nếu có lỗi.
     */
    private Bitmap getBitmapFromUri(Uri uri) {
        try {
            InputStream input = getContentResolver().openInputStream(uri);
            Bitmap bitmap = BitmapFactory.decodeStream(input); // Giải mã bitmap

            // Xử lý xoay ảnh từ Exif
            InputStream exifStream = getContentResolver().openInputStream(uri);
            ExifInterface exif = new ExifInterface(exifStream); // Đọc thông tin Exif
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            int rotation = exifToDegrees(orientation); // Chuyển đổi hướng Exif sang độ xoay
            if (rotation != 0) {
                Matrix matrix = new Matrix();
                matrix.postRotate(rotation); // Tạo ma trận xoay
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true); // Tạo bitmap đã xoay
            }

            return bitmap;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Chuyển đổi giá trị hướng Exif sang góc xoay tương ứng (độ).
     * @param exifOrientation Giá trị hướng từ Exif.
     * @return Góc xoay tính bằng độ.
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
     * Lưu một Bitmap vào thư mục cache của ứng dụng.
     * @param bitmap Bitmap cần lưu.
     * @return Uri của file Bitmap đã lưu, hoặc null nếu có lỗi.
     */
    private Uri saveBitmapToCache(Bitmap bitmap) {
        try {
            File cachePath = new File(getCacheDir(), "merged_images"); // Tạo thư mục con trong cache
            cachePath.mkdirs(); // Tạo thư mục nếu chưa tồn tại
            File file = new File(cachePath, "merged_image_" + System.currentTimeMillis() + ".jpg"); // Tạo tên file duy nhất
            FileOutputStream fos = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos); // Nén bitmap thành JPEG với chất lượng 90%
            fos.close(); // Đóng FileOutputStream
            return Uri.fromFile(file); // Trả về Uri của file đã lưu
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Thay đổi kích thước của Bitmap nếu nó lớn hơn kích thước tối đa cho phép.
     * @param bitmap Bitmap gốc.
     * @param maxWidth Chiều rộng tối đa.
     * @param maxHeight Chiều cao tối đa.
     * @return Bitmap đã được thay đổi kích thước nếu cần, hoặc Bitmap gốc nếu không cần thay đổi.
     */
    private Bitmap resizeIfNeeded(Bitmap bitmap, int maxWidth, int maxHeight) {
        if (bitmap.getWidth() <= maxWidth && bitmap.getHeight() <= maxHeight) return bitmap; // Không cần thay đổi kích thước
        // Tính toán tỷ lệ thu nhỏ để ảnh vừa với kích thước tối đa
        float ratio = Math.min((float) maxWidth / bitmap.getWidth(), (float) maxHeight / bitmap.getHeight());
        int newWidth = Math.round(bitmap.getWidth() * ratio);
        int newHeight = Math.round(bitmap.getHeight() * ratio);
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true); // Tạo bitmap đã được scale
    }

    // Các phương thức interface OnSignatureChangeListener (hiện tại không làm gì)

    /**
     * Callback khi chữ ký thay đổi. (Không được sử dụng trong Activity này)
     */
    @Override
    public void onSignatureChanged() {}

    /**
     * Callback khi hộp giới hạn được phát hiện. (Không được sử dụng trong Activity này)
     * @param boundingBox RectF của hộp giới hạn.
     */
    @Override
    public void onBoundingBoxDetected(RectF boundingBox) {}

    /**
     * Callback khi hộp cắt thay đổi. (Không được sử dụng trong Activity này)
     * @param cropBox RectF của hộp cắt.
     */
    @Override
    public void onCropBoxChanged(RectF cropBox) {}

    /**
     * Callback khi khung chữ ký thay đổi kích thước. (Không được sử dụng trong Activity này)
     * @param frame RectF của khung chữ ký.
     */
    @Override
    public void onFrameResized(RectF frame) {}
}