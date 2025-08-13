package com.example.camerascanner.activitysignature.signatureview.signature;


import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.example.camerascanner.BaseActivity;
import com.example.camerascanner.R;
import com.example.camerascanner.activitysignature.SignatureManager;
import com.example.camerascanner.activitysignature.signatureview.imagesignpreview.ImageSignPreviewActivity;

import java.io.File;
import java.io.FileOutputStream;

public class SignatureActivity extends BaseActivity {

    private SignatureView signatureView;
    private SeekBar seekBarStrokeWidth;
    private TextView tvStrokeWidth;

    private Button btnClear, btnDone;
    private Uri imageUri;
    private SignatureManager signatureManager;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign);

        signatureView = findViewById(R.id.signatureView);
        btnClear = findViewById(R.id.btnDeleteSign);
        btnDone = findViewById(R.id.btnYesSign);
        seekBarStrokeWidth = findViewById(R.id.seekBarStrokeWidth);
        tvStrokeWidth = findViewById(R.id.tvStrokeWidth);



        // Khởi tạo SignatureManager
        signatureManager = new SignatureManager(this);


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
                // Lưu chữ ký vào danh sách chữ ký đã lưu
                signatureManager.saveSignatureUri(savedUri);

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
        setupStrokeWidthControl();
    }
    private void setupStrokeWidthControl() {
        // Thiết lập SeekBar để điều chỉnh độ dày nét vẽ
        seekBarStrokeWidth.setMax(20); // Độ dày tối đa 20px
        seekBarStrokeWidth.setProgress(6); // Độ dày mặc định 6px
        updateStrokeWidthDisplay(6);

        seekBarStrokeWidth.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    // Đảm bảo độ dày tối thiểu là 1px
                    int strokeWidth = Math.max(1, progress);

                    // Cập nhật độ dày cho SignatureView
                    signatureView.setStrokeWidth(strokeWidth);
                    // Thêm dòng này để yêu cầu View vẽ lại ngay lập tức
                    signatureView.invalidate();
                    // Cập nhật hiển thị
                    updateStrokeWidthDisplay(strokeWidth);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // Không cần xử lý
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // Không cần xử lý
            }
        });
    }

    private void updateStrokeWidthDisplay(int strokeWidth) {
        tvStrokeWidth.setText(strokeWidth + "px");
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