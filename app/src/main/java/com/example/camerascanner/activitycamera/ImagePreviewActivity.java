package com.example.camerascanner.activitycamera;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log; // Import Log
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.Rotate; // Import để xoay ảnh với Glide
import com.bumptech.glide.request.RequestOptions; // Import RequestOptions
import com.example.camerascanner.R;
import com.example.camerascanner.activitysignature.SignatureActivity;
import com.example.camerascanner.activitycrop.CropActivity;
import com.example.camerascanner.activityocr.OCRActivity;
import com.example.camerascanner.activitypdf.PdfGenerationAndPreviewActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class ImagePreviewActivity extends AppCompatActivity {

    private static final String TAG = "ImagePreviewActivity"; // Thêm TAG cho logging
    private ImageView imageViewPreview;
    private Button btnRotatePreview,btnSign,btnReTake,btnCrop,btnMakeOcr;
    private Button btnConfirmPreview;
    private Uri imageUri;
    private int rotationAngle = 0; // Để theo dõi góc xoay hiện tại

    // Request code cho CropActivity, để biết khi nào CropActivity trả về kết quả
    private static final int REQUEST_CODE_CROP_IMAGE = 101;
    private static final int REQUEST_SIGNATURE = 102;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_preview);

        imageViewPreview = findViewById(R.id.imageViewPreview);
        btnRotatePreview = findViewById(R.id.btnRotate );
        btnConfirmPreview = findViewById(R.id.btnConfirmPreview);
        btnSign = findViewById(R.id.btnSign);
        btnMakeOcr = findViewById(R.id.btnMakeOcr);
        btnReTake = findViewById(R.id.btnRetake);
        btnCrop = findViewById(R.id.btnCrop);

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

        btnCrop.setOnClickListener(v -> {
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
                    startActivityForResult(cropIntent, REQUEST_CODE_CROP_IMAGE);
                } else {
                    Toast.makeText(this, "Lỗi khi lưu ảnh đã xoay.", Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "ImagePreviewActivity: Failed to save rotated bitmap to cache.");
                }
            } else {
                Toast.makeText(this, "Không thể lấy ảnh đã xoay để xử lý.", Toast.LENGTH_SHORT).show();
                Log.e(TAG, "ImagePreviewActivity: Could not get bitmap from ImageView.");
            }
        });
        btnConfirmPreview.setOnClickListener(v->{
            Bitmap rotatedBitmap = ((BitmapDrawable)imageViewPreview.getDrawable()).getBitmap();
            // Phương thức saveBitmapToCache() đã tồn tại trong ImagePreviewActivity.java
            Uri tempUri = saveBitmapToCache(rotatedBitmap);
            Intent pdfIntent = new Intent(ImagePreviewActivity.this, PdfGenerationAndPreviewActivity.class);
            pdfIntent.putExtra("croppedUri", tempUri);
            startActivity(pdfIntent);
        });
        btnMakeOcr.setOnClickListener(v->{
            if (imageViewPreview.getDrawable() instanceof BitmapDrawable) {
                Bitmap currentBitmap = ((BitmapDrawable) imageViewPreview.getDrawable()).getBitmap();

                // 2. Lưu Bitmap vào bộ nhớ cache
                // Điều này đảm bảo rằng chúng ta có một tệp ảnh để gửi tới OCRActivity
                Uri tempUri = saveBitmapToCache(currentBitmap);

                if (tempUri != null) {
                    // 3. Tạo Intent để mở OCRActivity và gửi URI
                    Intent intent = new Intent(ImagePreviewActivity.this, OCRActivity.class);

                    // OCRActivity đã được cung cấp sử dụng một key cho URI,
                    // thường là "imageUriForOcr" hoặc một biến tĩnh tương tự.
                    // Nếu OCRActivity.java sử dụng EXTRA_IMAGE_URI_FOR_OCR, hãy sử dụng nó.
                    // Nếu không, sử dụng một chuỗi thông thường như "imageUriForOcr".
                    intent.putExtra("image_uri_for_ocr", tempUri.toString());

                    startActivity(intent);
                } else {
                    // Xử lý lỗi nếu không lưu được ảnh tạm thời
                    Toast.makeText(ImagePreviewActivity.this, "Không thể chuẩn bị ảnh cho OCR.", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(ImagePreviewActivity.this, "Không có ảnh để xử lý OCR.", Toast.LENGTH_SHORT).show();
            }
        });
        btnReTake.setOnClickListener(v-> finish());
        btnSign.setOnClickListener(v->{
            Bitmap rotatedBitmap = ((BitmapDrawable)imageViewPreview.getDrawable()).getBitmap();
            // Phương thức saveBitmapToCache() đã tồn tại trong ImagePreviewActivity.java
            Uri tempUri = saveBitmapToCache(rotatedBitmap);

            Intent intent = new Intent(this, SignatureActivity.class);
            intent.putExtra("imageUri", tempUri);
            startActivityForResult(intent, REQUEST_SIGNATURE);

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
    // ... (các khai báo và phương thức khác) ...

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_CROP_IMAGE && resultCode == RESULT_OK) {
            // ... (Logic xử lý crop đã có) ...
            if (data != null && data.getData() != null) {
                Uri croppedImageUri = data.getData();
                Log.d(TAG, "ImagePreviewActivity: Received cropped image URI: " + croppedImageUri.toString());
                imageUri = croppedImageUri;
                loadImageWithRotation();
            } else {
                Log.e(TAG, "ImagePreviewActivity: No cropped image URI returned from CropActivity.");
            }
        } else if (requestCode == REQUEST_SIGNATURE && resultCode == RESULT_OK) {
            // Thêm logic để xử lý kết quả từ SignatureActivity
            if (data != null) {
                // Nhận mergedImageUri được gửi từ SignatureActivity
                Uri mergedImageUri = data.getParcelableExtra("mergedImageUri");
                if (mergedImageUri != null) {
                    Log.d(TAG, "ImagePreviewActivity: Received merged image URI: " + mergedImageUri.toString());

                    // Cập nhật URI ảnh hiện tại của Activity
                    this.imageUri = mergedImageUri;

                    // Tải lại ảnh trong ImageView với URI mới
                    loadImageWithRotation();
                    Toast.makeText(this, "Ảnh đã được ký và hợp nhất thành công.", Toast.LENGTH_SHORT).show();
                }
            }
        } else if (resultCode == RESULT_CANCELED) {
            Log.d(TAG, "Activity canceled.");
        }
    }
}