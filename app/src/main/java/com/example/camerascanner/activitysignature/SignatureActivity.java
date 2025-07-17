package com.example.camerascanner.activitysignature;


import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.camerascanner.R;
import com.example.camerascanner.activitysignature.signatureview.SignatureView;

import java.io.File;
import java.io.FileOutputStream;

public class SignatureActivity extends AppCompatActivity {

    private SignatureView signatureView;
    private Button btnClear, btnDone;
    private Uri imageUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign);

        signatureView = findViewById(R.id.signatureView);
        btnClear = findViewById(R.id.btnDeleteSign);
        btnDone = findViewById(R.id.btnYesSign);

        // Cấu hình để cho phép ký toàn màn hình và ẩn khung mặc định
        signatureView.setDrawingMode(true);
        signatureView.showSignatureFrame(false);
        signatureView.setFrameResizable(false);

        btnClear.setOnClickListener(v -> signatureView.clear());

        btnDone.setOnClickListener(v -> {
            if (signatureView.isEmpty()) {
                return;
            }

            // Gọi detectBoundingBox() để tạo khung bao quanh chữ ký
            signatureView.detectBoundingBox();
            // Lấy tọa độ của Bounding Box
            RectF boundingBox = signatureView.getBoundingBox();


            // Lấy chữ ký sau khi đã phát hiện bounding box
            Bitmap signatureBitmap = signatureView.getSignatureBitmap();
            Uri savedUri = saveBitmapToCache(signatureBitmap);

            imageUri = getIntent().getParcelableExtra("imageUri");
            if (savedUri != null) {
                Intent intent = new Intent(SignatureActivity.this, ImageSignPreviewActivity.class);
                intent.putExtra("signatureUri", savedUri);
                intent.putExtra("imageUri", imageUri); // hoặc truyền imageUri nếu có

                // Thêm tọa độ Bounding Box vào Intent
                intent.putExtra("boundingBoxLeft", boundingBox.left);
                intent.putExtra("boundingBoxTop", boundingBox.top);

                startActivityForResult(intent, 200); // eg: REQUEST_MERGE
                signatureView.clear();
            }
        });
    }


    private Uri saveBitmapToCache(Bitmap bitmap) {
        try {
            File cachePath = new File(getCacheDir(), "signatures");
            cachePath.mkdirs();
            File file = new File(cachePath, "signature_" + System.currentTimeMillis() + ".png");
            FileOutputStream fos = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.close();
            return Uri.fromFile(file);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 200 && resultCode == RESULT_OK && data != null) {
            Uri mergedImageUri = data.getParcelableExtra("mergedImageUri");
            Intent resultIntent = new Intent();
            resultIntent.putExtra("mergedImageUri", mergedImageUri);
            setResult(RESULT_OK, resultIntent);
            finish();
        }
    }
}