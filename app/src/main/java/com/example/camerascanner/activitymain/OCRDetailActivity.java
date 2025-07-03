package com.example.camerascanner.activitymain;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.camerascanner.R;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class OCRDetailActivity extends AppCompatActivity {

    private static final String TAG = "OCRDetailActivity";
    public static final String EXTRA_IMAGE_URI = "extra_image_uri";
    public static final String EXTRA_TEXT_URI = "extra_text_uri";

    private ImageView ivDetailImage;
    private EditText etDetailText;
    private Button btnDetailCopyToClipboard;

    private ExecutorService executorService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ocr_detail);

        ivDetailImage = findViewById(R.id.ivDetailImage);
        etDetailText = findViewById(R.id.etDetailText);
        btnDetailCopyToClipboard = findViewById(R.id.btnDetailCopyToClipboard);

        executorService = Executors.newSingleThreadExecutor();

        Uri imageUri = getIntent().getParcelableExtra(EXTRA_IMAGE_URI);
        Uri textUri = getIntent().getParcelableExtra(EXTRA_TEXT_URI);

        if (imageUri != null && textUri != null) {
            loadOcrContent(imageUri, textUri);
        } else {
            Toast.makeText(this, "Không thể tải chi tiết OCR: URI bị thiếu.", Toast.LENGTH_SHORT).show();
            finish();
        }

        btnDetailCopyToClipboard.setOnClickListener(v -> {
            copyTextToClipboard();
        });
    }

    private void loadOcrContent(Uri imageUri, Uri textUri) {
        executorService.execute(() -> {
            Bitmap imageBitmap = null;
            String textContent = "Không thể tải văn bản.";

            // Load Image
            try (InputStream is = getContentResolver().openInputStream(imageUri)) {
                if (is != null) {
                    imageBitmap = BitmapFactory.decodeStream(is);
                }
            } catch (IOException e) {
                Log.e(TAG, "Lỗi khi tải ảnh từ URI: " + imageUri, e);
            }

            // Load Text
            try (InputStream is = getContentResolver().openInputStream(textUri);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                StringBuilder stringBuilder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    stringBuilder.append(line).append("\n");
                }
                textContent = stringBuilder.toString();
            } catch (IOException e) {
                Log.e(TAG, "Lỗi khi tải văn bản từ URI: " + textUri, e);
            }

            final Bitmap finalImageBitmap = imageBitmap;
            final String finalTextContent = textContent;

            runOnUiThread(() -> {
                if (finalImageBitmap != null) {
                    ivDetailImage.setImageBitmap(finalImageBitmap);
                } else {
                    Toast.makeText(OCRDetailActivity.this, "Không thể hiển thị ảnh.", Toast.LENGTH_SHORT).show();
                }
                etDetailText.setText(finalTextContent);
            });
        });
    }

    private void copyTextToClipboard() {
        String textToCopy = etDetailText.getText().toString().trim();
        if (textToCopy.isEmpty()) {
            Toast.makeText(this, "Không có văn bản để sao chép", Toast.LENGTH_LONG).show();
            return;
        }

        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Văn bản OCR", textToCopy);
        if (clipboard != null) {
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "Đã sao chép văn bản vào bộ nhớ tạm!", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "Không thể sao chép văn bản!", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow();
        }
    }
}