package com.example.camerascanner.activitycamera;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log; // Import Log
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.Rotate; // Import để xoay ảnh với Glide
import com.bumptech.glide.request.RequestOptions; // Import RequestOptions
import com.example.camerascanner.R;
import com.example.camerascanner.activitycrop.CropActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class ImagePreviewActivity extends AppCompatActivity {

    private static final String TAG = "ImagePreviewActivity"; // Thêm TAG cho logging
    private ImageView imageViewPreview;
    private Button btnRotatePreview;
    private Button btnConfirmPreview;
    private Uri imageUri;
    private int rotationAngle = 0; // Để theo dõi góc xoay hiện tại

    // Request code cho CropActivity, để biết khi nào CropActivity trả về kết quả
    private static final int REQUEST_CODE_CROP_IMAGE = 101;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_preview);

        imageViewPreview = findViewById(R.id.imageViewPreview);
        btnRotatePreview = findViewById(R.id.btnRotatePreview);
        btnConfirmPreview = findViewById(R.id.btnConfirmPreview);

        String imageUriString = getIntent().getStringExtra("imageUri");
        if (imageUriString != null) {
            imageUri = Uri.parse(imageUriString);
            Log.d(TAG, "ImagePreviewActivity: Received URI: " + imageUri.toString()); // Log khi nhận URI
            loadImageWithRotation();
        } else {
            Toast.makeText(this, "Không có ảnh để xem trước.", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "ImagePreviewActivity: No image URI received."); // Log lỗi nếu không nhận được URI
            finish();
        }

        btnRotatePreview.setOnClickListener(v -> {
            rotationAngle = (rotationAngle + 90) % 360;
            loadImageWithRotation();
            Log.d(TAG, "ImagePreviewActivity: Rotated image to " + rotationAngle + " degrees."); // Log khi xoay ảnh
        });

        btnConfirmPreview.setOnClickListener(v -> {
            Bitmap rotatedBitmap = null;
            if (imageViewPreview.getDrawable() instanceof BitmapDrawable) {
                rotatedBitmap = ((BitmapDrawable) imageViewPreview.getDrawable()).getBitmap();
            }

            if (rotatedBitmap != null) {
                // Lưu Bitmap đã xoay vào bộ nhớ cache và lấy URI mới
                Uri tempUri = saveBitmapToCache(rotatedBitmap);
                if (tempUri != null) {
                    Intent cropIntent = new Intent(ImagePreviewActivity.this, CropActivity.class);
                    cropIntent.putExtra("imageUri", tempUri.toString());
                    Log.d(TAG, "ImagePreviewActivity: Launching CropActivity with TEMPORARY URI: " + tempUri.toString());
                    startActivityForResult(cropIntent, REQUEST_CODE_CROP_IMAGE);finish();
                } else {
                    Toast.makeText(this, "Lỗi khi lưu ảnh đã xoay.", Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "ImagePreviewActivity: Failed to save rotated bitmap to cache.");
                }
            } else {
                Toast.makeText(this, "Không thể lấy ảnh đã xoay để xử lý.", Toast.LENGTH_SHORT).show();
                Log.e(TAG, "ImagePreviewActivity: Could not get bitmap from ImageView.");
            }
        });
    }
    private Uri saveBitmapToCache(Bitmap bitmap) {
        String fileName = "rotated_temp_" + System.currentTimeMillis() + ".jpeg";
        File cachePath = new File(getCacheDir(), "rotated_images");
        cachePath.mkdirs(); // Tạo thư mục nếu nó chưa tồn tại

        File file = new File(cachePath, fileName);

        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
            fos.flush();
            return Uri.fromFile(file);
        } catch (IOException e) {
            Log.e(TAG, "Error saving bitmap to cache: " + e.getMessage(), e);
            return null;
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error closing FileOutputStream: " + e.getMessage(), e);
                }
            }
        }
    }


    // Phương thức tải ảnh với góc xoay hiện tại
    private void loadImageWithRotation() {
        RequestOptions requestOptions = new RequestOptions();
        requestOptions = requestOptions.transform(new Rotate(rotationAngle)); // Áp dụng xoay

        Glide.with(this)
                .load(imageUri)
                .apply(requestOptions) // Áp dụng RequestOptions
                .into(imageViewPreview);
    }

    // Xử lý kết quả trả về từ CropActivity
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_CROP_IMAGE && resultCode == RESULT_OK) {
            if (data != null && data.getData() != null) {
                Uri croppedImageUri = data.getData();
                Log.d(TAG, "ImagePreviewActivity: Received cropped image URI: " + croppedImageUri.toString()); // Log khi nhận URI đã cắt
                // TODO: Bây giờ bạn đã có URI của ảnh đã cắt.
                // Bạn có thể chuyển URI này sang PdfGenerationAndPreviewActivity
                // hoặc xử lý lưu trữ, hoặc quay về MainActivity với kết quả.
                // Ví dụ:
                Intent resultIntent = new Intent();
                resultIntent.setData(croppedImageUri);
                setResult(RESULT_OK, resultIntent);
                finish(); // Kết thúc ImagePreviewActivity và trả về kết quả cho Activity gọi nó (CameraActivity hoặc MainActivity)
            } else {
                Log.e(TAG, "ImagePreviewActivity: No cropped image URI returned from CropActivity."); // Log lỗi nếu không có URI đã cắt
            }
        } else if (resultCode == RESULT_CANCELED) {
            Log.d(TAG, "ImagePreviewActivity: Crop activity canceled."); // Log khi CropActivity bị hủy
        }
    }
}