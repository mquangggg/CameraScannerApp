package com.example.camerascanner.activitycrop;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.Rotate;
import com.bumptech.glide.request.RequestOptions;
import com.example.camerascanner.R;
import com.example.camerascanner.activityocr.OCRActivity;
import com.example.camerascanner.activitypdf.PdfGenerationAndPreviewActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class ImageAfterCropActivity extends AppCompatActivity {
    private Button btnRotateCropPreview, btnMakePdf, btnMakeOrc;
    private ImageView imageViewCropPreview;

    private static final String TAG = "ImageAfterCropActivity"; // Thêm TAG cho logging

    private Uri imageUri;
    private int rotationAngle = 0;
    private static final int REQUEST_CODE_CROP_IMAGE = 101;
    @Override
    protected void onCreate(Bundle saveInstance){
        super.onCreate(saveInstance);
        setContentView(R.layout.activity_aftercrop);
        btnMakePdf = findViewById(R.id.btnMakePdf);
        btnMakeOrc = findViewById(R.id.btnMakeOrc);
        btnRotateCropPreview = findViewById(R.id.btnRotateCropPreview);
        imageViewCropPreview = findViewById(R.id.imageViewCropPreview);

        // Lấy URI ảnh từ Intent
        if (getIntent() != null && getIntent().getExtras() != null) {
            imageUri = getIntent().getParcelableExtra("croppedUri"); // Lấy URI với key "imageUri"
        }
        btnMakePdf.setOnClickListener(v->{
            Bitmap rotatedBitmap = ((BitmapDrawable)imageViewCropPreview.getDrawable()).getBitmap();
            Uri tempUri = saveBitmapToCacheAndGetUri(rotatedBitmap);
            Intent pdfIntent = new Intent(ImageAfterCropActivity.this, PdfGenerationAndPreviewActivity.class);
            pdfIntent.putExtra("croppedUri", tempUri);
            startActivity(pdfIntent);
        });
        // Thiết lập sự kiện click cho nút "Thực hiện OCR"
        btnMakeOrc.setOnClickListener(v -> {
            // Lấy các điểm cắt đã chọn từ CustomCropView
            Bitmap rotatedBitmap = ((BitmapDrawable)imageViewCropPreview.getDrawable()).getBitmap();
            Uri tempUri = saveBitmapToCache(rotatedBitmap);
            Intent intent = new Intent(ImageAfterCropActivity.this, OCRActivity.class);
            intent.putExtra(OCRActivity.EXTRA_IMAGE_URI_FOR_OCR, tempUri);
            startActivity(intent);
            finish();
            finish();
        });

        btnRotateCropPreview.setOnClickListener(v->{
            rotationAngle = (rotationAngle + 90) % 360;
            loadImageWithRotation();
        });
        loadImageWithRotation();

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
    private Uri saveBitmapToCacheAndGetUri(Bitmap bitmap) {
        String fileName = "cropped_temp_" + System.currentTimeMillis() + ".jpeg";
        // Tạo thư mục con trong thư mục cache của ứng dụng để lưu ảnh đã cắt tạm thời
        File cachePath = new File(getCacheDir(), "cropped_images");
        cachePath.mkdirs(); // Tạo thư mục nếu nó chưa tồn tại

        File file = new File(cachePath, fileName);

        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos); // Nén ảnh với chất lượng 90%
            fos.flush(); // Đảm bảo tất cả dữ liệu được ghi vào tệp
            return Uri.fromFile(file); // Trả về URI từ File
        } catch (IOException e) {
            Log.e(TAG, "Lỗi khi lưu bitmap vào cache: " + e.getMessage(), e);
            return null;
        } finally {
            if (fos != null) {
                try {
                    fos.close(); // Đóng FileOutputStream
                } catch (IOException e) {
                    Log.e(TAG, "Lỗi khi đóng FileOutputStream: " + e.getMessage(), e);
                }
            }
        }
    }
    private void loadImageWithRotation() {
        RequestOptions requestOptions = new RequestOptions();
        requestOptions = requestOptions.transform(new Rotate(rotationAngle)); // Áp dụng xoay

        Glide.with(this)
                .load(imageUri)
                .apply(requestOptions) // Áp dụng RequestOptions
                .into(imageViewCropPreview);
    }
}
