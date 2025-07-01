package com.example.camerascanner;

import android.content.Intent;
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
            // Chuyển sang CropActivity
            Intent cropIntent = new Intent(ImagePreviewActivity.this, CropActivity.class);
            // TRUYỀN imageUri VÀO ĐÂY BẰNG CÁCH SỬ DỤNG putExtra
            cropIntent.putExtra("imageUri", imageUri.toString()); // Dòng này đã được thêm vào/sửa
            Log.d(TAG, "ImagePreviewActivity: Launching CropActivity with URI: " + imageUri.toString()); // Log khi gửi URI
            startActivityForResult(cropIntent, REQUEST_CODE_CROP_IMAGE); // Sử dụng startActivityForResult để nhận kết quả từ Crop
        });
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