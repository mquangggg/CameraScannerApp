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
    private Button btnRotatePreview,btnSign,btnReTake,btnMakeOcr;
    private Button btnConfirmPreview;
    private Uri imageUri;
    private int rotationAngle = 0; // Để theo dõi góc xoay hiện tại

    private static final int REQUEST_SIGNATURE = 102;

    // Chúng ta sẽ cần một REQUEST_CODE mới để gọi PdfGenerationAndPreviewActivity
    private static final int REQUEST_PDF_GENERATION_PREVIEW = 1003;

    private boolean isFromPdfGroup = false;
    private int originalRequestCode = -1;


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

        Intent intents = getIntent();
        if (intents != null) {
            isFromPdfGroup = intents.getBooleanExtra("FROM_PDF_GROUP", false);
            originalRequestCode = intents.getIntExtra("ORIGINAL_REQUEST_CODE", -1);
            Log.d(TAG, "ImagePreviewActivity: isFromPdfGroup=" + isFromPdfGroup + ", requestCode=" + originalRequestCode);
        }

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
            Bitmap rotatedBitmap = ((BitmapDrawable) imageViewPreview.getDrawable()).getBitmap();
            Uri tempUri = saveBitmapToCache(rotatedBitmap);

            if (tempUri != null) {
                Intent pdfIntent = new Intent(ImagePreviewActivity.this, PdfGenerationAndPreviewActivity.class);
                pdfIntent.putExtra("croppedUri", tempUri.toString()); // Truyền Uri của ảnh đã xử lý

                // RẤT QUAN TRỌNG: Truyền các cờ xác định luồng sang Activity tiếp theo
                pdfIntent.putExtra("FROM_PDF_GROUP", isFromPdfGroup);
                pdfIntent.putExtra("ORIGINAL_REQUEST_CODE", originalRequestCode); // Dù có thể không dùng, vẫn nên truyền
                Log.d(TAG, "ImagePreviewActivity -> PdfGenerationAndPreviewActivity: FROM_PDF_GROUP=" +
                        isFromPdfGroup + ", originalRequestCode=" + originalRequestCode);

                // Gọi PdfGenerationAndPreviewActivity bằng startActivityForResult
                // Để nó có thể trả kết quả về ImagePreviewActivity,
                // và ImagePreviewActivity có thể trả về tiếp cho CropActivity/CameraActivity/PDFGroupActivity
                startActivityForResult(pdfIntent, REQUEST_PDF_GENERATION_PREVIEW);
                Log.d(TAG, "ImagePreviewActivity: Confirmed image, starting PdfGenerationAndPreviewActivity.");
            } else {
                Toast.makeText(this, "Không thể lưu ảnh đã xử lý.", Toast.LENGTH_SHORT).show();
                setResult(RESULT_CANCELED); // Đặt kết quả là hủy nếu không lưu được
                finish();
                Log.e(TAG, "ImagePreviewActivity: Failed to save bitmap for confirmation.");
            }
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
        if (requestCode == REQUEST_SIGNATURE && resultCode == RESULT_OK) {
            if (data != null) {
                Uri mergedImageUri = data.getParcelableExtra("mergedImageUri");
                if (mergedImageUri != null) {
                    Log.d(TAG, "ImagePreviewActivity: Received merged image URI: " + mergedImageUri.toString());
                    this.imageUri = mergedImageUri;
                    loadImageWithRotation();
                    Toast.makeText(this, "Ảnh đã được ký và hợp nhất thành công.", Toast.LENGTH_SHORT).show();
                }
            }
        } // Xử lý kết quả trả về từ PdfGenerationAndPreviewActivity
        else if (requestCode == REQUEST_PDF_GENERATION_PREVIEW) {
            if (resultCode == RESULT_OK && data != null) {
                // Đọc lại cờ và extra từ data nhận được
                // và đặt nó vào một Intent mới để trả về
                Intent resultIntent = new Intent();
                resultIntent.putExtra("processedImageUri", data.getStringExtra("processedImageUri"));
                // **Quan trọng:** Bạn cần đảm bảo cờ FROM_PDF_GROUP được chuyển tiếp
                // Bạn có thể đọc nó từ Intent ban đầu của ImagePreviewActivity và thêm vào đây
                boolean isFromPdfGroup = getIntent().getBooleanExtra("FROM_PDF_GROUP", false);
                if (isFromPdfGroup) {
                    resultIntent.putExtra("FROM_PDF_GROUP", true);
                    setResult(RESULT_OK, resultIntent);
                    finish();
                }
            } else {}
        }
        else if (resultCode == RESULT_CANCELED) {
            Log.d(TAG, "ImagePreviewActivity: Activity canceled from requestCode: " + requestCode);
            // Nếu có Activity nào trong chuỗi này hủy (ví dụ CropActivity hủy),
            // thì ImagePreviewActivity cũng nên hủy và truyền lại kết quả CANCELED
            if (isFromPdfGroup) {
                setResult(RESULT_CANCELED);
                finish();
            }


        }
    }
}