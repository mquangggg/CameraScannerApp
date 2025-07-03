package com.example.camerascanner.activityocr;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.camerascanner.R;
import com.example.camerascanner.activitymain.MainActivity;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class OCRActivity extends AppCompatActivity {

    private ImageButton  btnOCRBack ;

    private AppCompatButton btnSaveOCRImageAndWord, btnCopyOCRText;
    private ImageView ivImageForOCR;

    private EditText editTextOCR;
    public static final String EXTRA_IMAGE_URI_FOR_OCR = "image_uri_for_ocr";
    private static final String TAG = "OCRActivity";
    private static final int REQUEST_WRITE_STORAGE = 100;

    private ExecutorService executorService;
    private Handler mainHandler;
    private Uri imageUriToProcess;
    private Bitmap currentImageBitmap; // Bitmap của ảnh đang được hiển thị và xử lý


    @Override
    protected void onCreate(Bundle saveInstance){
        super.onCreate(saveInstance);
        setContentView(R.layout.activity_ocr);

        ivImageForOCR = findViewById(R.id.ivImageForOcr);
        btnOCRBack = findViewById(R.id.btnOCRBack);
        btnSaveOCRImageAndWord = findViewById(R.id.btnSaveOCRImageAndWord);
        btnCopyOCRText = findViewById(R.id.btnCopyOCRText);
        editTextOCR = findViewById(R.id.editTextOCR);

        executorService = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());

        imageUriToProcess = getIntent().getParcelableExtra(EXTRA_IMAGE_URI_FOR_OCR);

        if (imageUriToProcess != null) {
            try {
                InputStream inputStream = getContentResolver().openInputStream(imageUriToProcess);
                if (inputStream != null) {
                    currentImageBitmap = BitmapFactory.decodeStream(inputStream);
                    ivImageForOCR.setImageBitmap(currentImageBitmap);
                    inputStream.close();
                    // TRUYỀN currentImageBitmap TRỰC TIẾP CHO startOcrProcess
                    startOcrProcess(currentImageBitmap); // ĐÃ SỬA ĐỔI: TRUYỀN BITMAP ĐÃ TẢI
                } else {
                    Toast.makeText(this, "Không thể tải ảnh cho OCR.", Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "InputStream is null for URI: " + imageUriToProcess);
                }
            } catch (IOException e) {
                Log.e(TAG, "Lỗi khi tải ảnh từ URI trong OCRActivity: " + e.getMessage(), e);
                Toast.makeText(this, "Lỗi khi tải ảnh cho OCR: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "Không nhận được ảnh để xử lý OCR.", Toast.LENGTH_SHORT).show();
            finish(); // Đóng activity nếu không nhận được URI ảnh
        }

        btnOCRBack.setOnClickListener(v->{
            Intent intent = new Intent(OCRActivity.this, MainActivity.class);
            startActivity(intent);
            finish();
        });

        btnSaveOCRImageAndWord.setOnClickListener(v -> {
            String ocrText = editTextOCR.getText().toString().trim();
            if (ocrText.isEmpty()) {
                Toast.makeText(this, "Không có văn bản để lưu.", Toast.LENGTH_LONG).show();
                return;
            }
            if (currentImageBitmap == null) {
                Toast.makeText(this, "Không có ảnh để lưu.", Toast.LENGTH_LONG).show();
                return;
            }

            String commonTimestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());

            saveTextToFile(ocrText, commonTimestamp, "txt");
            saveOcrImageToFile(currentImageBitmap, commonTimestamp);

            mainHandler.postDelayed(() -> {
                Intent intent = new Intent(OCRActivity.this, MainActivity.class);
                startActivity(intent);
                finish();
            }, 500);
        });

        btnCopyOCRText.setOnClickListener(v->{
            copyTextToClipboard();
        });

    }
    private void copyTextToClipboard(){
        String textToCopy = editTextOCR.getText().toString().trim();
        if (textToCopy.isEmpty()){
            Toast.makeText(this, "Không có văn bản để sao chép",Toast.LENGTH_LONG).show();
            return;
        }

        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Văn bản OCR",textToCopy);
        if(clipboard != null){
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this,"Đã sao chép văn bản vào bộ nhớ tạm! ",Toast.LENGTH_LONG).show();
        }
        else {
            Toast.makeText(this,"Không thể sao chép văn bản! ",Toast.LENGTH_LONG).show();
        }
    }
    /**
     * Phương thức này chịu trách nhiệm hiển thị ảnh, và thực hiện quá trình nhận dạng văn bản (OCR) bằng ML Kit.
     * Nó chạy trên một luồng nền để tránh chặn luồng UI.
     *
     * @param imageBitmap Bitmap của ảnh cần xử lý OCR. // ĐÃ SỬA ĐỔI: Nhận Bitmap trực tiếp
     */
    private void startOcrProcess(Bitmap imageBitmap) { // ĐÃ SỬA ĐỔI: Nhận Bitmap trực tiếp
        editTextOCR.setText("Đang tải ảnh và trích xuất văn bản...");
        editTextOCR.setEnabled(false);
        btnCopyOCRText.setEnabled(false);
        btnSaveOCRImageAndWord.setEnabled(false);

        executorService.execute(() ->{
            String ocrResult = "Lỗi khi trích xuất văn bản.";
            try{
                // KHÔNG CẦN TẢI VÀ GIẢI MÃ LẠI BITMAP TỪ URI NỮA
                // Vì currentImageBitmap đã có sẵn và được truyền vào đây
                if (imageBitmap == null) { // ĐÃ SỬA: Kiểm tra null cho imageBitmap đã truyền vào
                    throw new IOException("Bitmap để xử lý OCR là null.");
                }

                InputImage image = InputImage.fromBitmap(imageBitmap, 0); // SỬ DỤNG BITMAP ĐÃ CÓ
                TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

                ocrResult = Tasks.await(recognizer.process(image)).getText();

            } catch (Exception e){
                Log.e(TAG, "Lỗi khi trích xuất văn bản OCR: " + e.getMessage(), e); // ĐÃ SỬA: Bỏ "tải ảnh hoặc"
                ocrResult = "Lỗi: " + e.getMessage();
            } finally{
                // KHÔNG CẦN ĐÓNG INPUTSTREAM Ở ĐÂY NỮA
            }
            final String finalOcrResult = ocrResult;
            mainHandler.post(() -> {
                editTextOCR.setText(finalOcrResult);
                editTextOCR.setEnabled(true);
                btnCopyOCRText.setEnabled(true);
                btnSaveOCRImageAndWord.setEnabled(true);

                if (finalOcrResult.startsWith("Lỗi:")) {
                    Toast.makeText(OCRActivity.this, finalOcrResult, Toast.LENGTH_LONG).show();
                } else if (finalOcrResult.isEmpty()) {
                    Toast.makeText(OCRActivity.this, "Không tìm thấy văn bản nào trong ảnh.", Toast.LENGTH_SHORT).show();
                }
            });
        });

    }
    /**
     * Phương thức callback này được gọi khi người dùng phản hồi yêu cầu cấp quyền.
     *
     * @param requestCode Mã yêu cầu quyền được truyền vào {@link ActivityCompat#requestPermissions(android.app.Activity, String[], int)}.
     * @param permissions Các quyền đã được yêu cầu.
     * @param grantResults Kết quả cấp quyền cho các quyền tương ứng, là {@link PackageManager#PERMISSION_GRANTED} hoặc {@link PackageManager#PERMISSION_DENIED}.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_WRITE_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Đã cấp quyền lưu trữ. Vui lòng thử lại hành động lưu.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Ứng dụng cần quyền ghi để lưu tệp.", Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * Phương thức này kiểm tra quyền ghi vào bộ nhớ ngoài và sau đó gọi phương thức {@link #performSaveTextFile(String, String, String)}
     * để lưu văn bản OCR vào một tệp với phần mở rộng được chỉ định và dấu thời gian chung.
     *
     * @param textToSave Nội dung văn bản cần lưu.
     * @param commonTimestamp Dấu thời gian chung để ghép đôi tệp.
     * @param fileExtension Phần mở rộng của tệp (ví dụ: "txt").
     */
    private void saveTextToFile(String textToSave, String commonTimestamp, String fileExtension) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_WRITE_STORAGE);
        } else {
            performSaveTextFile(textToSave, commonTimestamp, fileExtension);
        }
    }
    /**
     * Thực hiện việc lưu nội dung văn bản vào một tệp trong thư mục "MyOCRTexts" trong thư mục "Downloads".
     * Việc lưu tệp được thực hiện trên một luồng nền.
     *
     * @param textContent Nội dung văn bản cần lưu.
     * @param commonTimestamp Dấu thời gian chung để ghép đôi tệp.
     * @param fileExtension Phần mở rộng của tệp (ví dụ: "txt").
     */
    private void performSaveTextFile(String textContent, String commonTimestamp, String fileExtension) {
        executorService.execute(() -> {
            String fileName = "OCR_Text_" + commonTimestamp + "." + fileExtension;
            String mimeType = "text/plain";

            Uri fileUri = null;
            ContentResolver resolver = getContentResolver();
            ContentValues contentValues = new ContentValues();
            contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
            contentValues.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);

            OutputStream outputStream = null;
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + File.separator + "MyOCRTexts");
                    fileUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues);
                    if (fileUri == null) {
                        throw new IOException("Không thể tạo URI cho tệp.");
                    }
                    outputStream = resolver.openOutputStream(fileUri);
                } else {
                    File documentsFolder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "MyOCRTexts");
                    if (!documentsFolder.exists()) {
                        documentsFolder.mkdirs();
                    }
                    File file = new File(documentsFolder, fileName);
                    fileUri = Uri.fromFile(file);
                    outputStream = new FileOutputStream(file);
                }

                if (outputStream != null) {
                    outputStream.write(textContent.getBytes());
                    outputStream.close();
                    mainHandler.post(() -> Toast.makeText(OCRActivity.this, "Đã lưu văn bản: " + fileName, Toast.LENGTH_LONG).show());
                } else {
                    throw new IOException("Không thể mở OutputStream.");
                }
            } catch (IOException e) {
                Log.e(TAG, "Lỗi khi lưu tệp văn bản: " + e.getMessage(), e);
                mainHandler.post(() -> Toast.makeText(OCRActivity.this, "Lỗi khi lưu tệp văn bản: " + e.getMessage(), Toast.LENGTH_LONG).show());
            } finally {
                if (outputStream != null) {
                    try {
                        outputStream.close();
                    } catch (IOException e) {
                        Log.e(TAG, "Lỗi khi đóng OutputStream: " + e.getMessage(), e);
                    }
                }
            }
        });
    }
    /**
     * Phương thức này kiểm tra xem có ảnh để lưu không, kiểm tra quyền ghi vào bộ nhớ ngoài
     * và sau đó gọi phương thức {@link #performSaveImageFile(Bitmap, String)} để lưu {@code Bitmap} hiện tại (ảnh OCR) vào một tệp ảnh.
     *
     * @param imageBitmap Bitmap của ảnh cần lưu.
     * @param commonTimestamp Dấu thời gian chung để ghép đôi tệp.
     */
    private void saveOcrImageToFile(Bitmap imageBitmap, String commonTimestamp) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_WRITE_STORAGE);
        } else {
            performSaveImageFile(imageBitmap, commonTimestamp);
        }
    }
    /**
     * Thực hiện việc lưu ảnh {@code Bitmap} vào thư mục "MyOCRImages" trong thư mục "Downloads".
     * Việc lưu tệp ảnh được thực hiện trên một luồng nền.
     *
     * @param imageBitmap Bitmap của ảnh cần lưu.
     * @param commonTimestamp Dấu thời gian chung để ghép đôi tệp.
     */
    private void performSaveImageFile(Bitmap imageBitmap, String commonTimestamp) {
        executorService.execute(() -> {
            String fileName = "OCR_Image_" + commonTimestamp + ".jpeg";
            String mimeType = "image/jpeg";

            Uri collectionUri = null;
            OutputStream outputStream = null;
            boolean saved = false;

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ContentValues contentValues = new ContentValues();
                    contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                    contentValues.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
                    contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + File.separator + "MyOCRImages");
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 1);

                    collectionUri = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
                    Uri itemUri = getContentResolver().insert(collectionUri, contentValues);

                    if (itemUri == null) {
                        throw new IOException("Không thể tạo URI cho ảnh.");
                    }
                    outputStream = getContentResolver().openOutputStream(itemUri);
                    if (outputStream == null) {
                        throw new IOException("Không thể mở OutputStream cho ảnh.");
                    }

                    saved = imageBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream);

                    if (saved) {
                        ContentValues updateContentValues = new ContentValues();
                        updateContentValues.put(MediaStore.Images.Media.IS_PENDING, 0);
                        getContentResolver().update(itemUri, updateContentValues, null, null);
                    }

                } else {
                    File parentDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "MyOCRImages");
                    if (!parentDir.exists()) {
                        parentDir.mkdirs();
                    }
                    File imageFile = new File(parentDir, fileName);

                    outputStream = new FileOutputStream(imageFile);
                    saved = imageBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream);

                    if (saved) {
                        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                        mediaScanIntent.setData(Uri.fromFile(imageFile));
                        sendBroadcast(mediaScanIntent);
                    }
                }

                final boolean finalSaved = saved;
                mainHandler.post(() -> {
                    if (finalSaved) {
                        Toast.makeText(OCRActivity.this, "Đã lưu ảnh OCR: " + fileName, Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(OCRActivity.this, "Không thể lưu ảnh OCR.", Toast.LENGTH_LONG).show();
                    }
                });

            } catch (IOException e) {
                Log.e(TAG, "Lỗi khi lưu ảnh OCR: " + e.getMessage(), e);
                final String errorMessage = "Lỗi khi lưu ảnh: " + e.getMessage();
                mainHandler.post(() -> Toast.makeText(OCRActivity.this, errorMessage, Toast.LENGTH_LONG).show());
            } finally {
                if (outputStream != null) {
                    try {
                        outputStream.close();
                    } catch (IOException e) {
                        Log.e(TAG, "Lỗi khi đóng OutputStream ảnh: " + e.getMessage(), e);
                    }
                }
            }
        });
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow();
            try {
                if (!executorService.awaitTermination(1, TimeUnit.SECONDS)) {
                    Log.w(TAG, "ExecutorService không dừng hoàn toàn.");
                }
            } catch (InterruptedException e) {
                Log.e(TAG, "Interrupted while waiting for executor to shut down.", e);
                Thread.currentThread().interrupt();
            }
        }

        if (currentImageBitmap != null) {
            currentImageBitmap.recycle();
            currentImageBitmap = null;
        }

        // Xóa file tạm thời nếu nó được tạo từ scheme "file://"
        if (imageUriToProcess != null && ContentResolver.SCHEME_FILE.equals(imageUriToProcess.getScheme())) {
            File file = new File(imageUriToProcess.getPath());
            if (file.exists()) {
                boolean deleted = file.delete();
                if (deleted) {
                    Log.d(TAG, "Đã xóa file tạm thời: " + file.getAbsolutePath());
                } else {
                    Log.e(TAG, "Không thể xóa file tạm thời: " + file.getAbsolutePath());
                }
            }
        }
    }
}