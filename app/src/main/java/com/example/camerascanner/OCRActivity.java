package com.example.camerascanner;

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
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.File;
import java.io.FileNotFoundException;
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

    private ImageButton  btnOCRBack , btnSaveOCRImage;

    private AppCompatButton btnSaveWord, btnCopyOCRText;
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
        btnSaveOCRImage = findViewById(R.id.btnSaveOCRImage);
        btnSaveWord = findViewById(R.id.btnSaveWord);
        btnCopyOCRText = findViewById(R.id.btnCopyOCRText);
        editTextOCR = findViewById(R.id.editTextOCR);

        executorService = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());

        imageUriToProcess = getIntent().getParcelableExtra(EXTRA_IMAGE_URI_FOR_OCR);

        // Thêm đoạn mã này để tải và hiển thị ảnh, sau đó bắt đầu OCR
        if (imageUriToProcess != null) {
            try {
                InputStream inputStream = getContentResolver().openInputStream(imageUriToProcess);
                if (inputStream != null) {
                    currentImageBitmap = BitmapFactory.decodeStream(inputStream);
                    ivImageForOCR.setImageBitmap(currentImageBitmap);
                    inputStream.close();
                    startOcrProcess(imageUriToProcess); // Bắt đầu quá trình OCR với URI đã tải
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
        // Kết thúc đoạn mã thêm vào

        btnOCRBack.setOnClickListener(v->{
            Intent intent = new Intent(OCRActivity.this, CropActivity.class);
            startActivity(intent);
            finish();
        });
        btnSaveOCRImage.setOnClickListener(v -> {
            saveOcrImageToFile();
        });
        btnSaveWord.setOnClickListener(v->{
            saveTextToFile("txt");        });
        btnCopyOCRText.setOnClickListener(v->{
            copyTextToClipboard();
        });

    }
    private void copyTextToClipboard(){
        String textToCopy = editTextOCR.getText().toString().trim();
        if (textToCopy.isEmpty()){
            Toast.makeText(this, "Không có văn abrn để sao chép",Toast.LENGTH_LONG).show();
            return;
        }

        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Văn bản OCR",textToCopy);
        if(clipboard != null){
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this,"Đã sao chép văn bản vào bộ nhớ tạm! ",Toast.LENGTH_LONG).show();
            return;
        }
        else {
            Toast.makeText(this,"Không thể sao chép văn bản! ",Toast.LENGTH_LONG).show();
            return;
        }
    }
    /**
     * Phương thức này chịu trách nhiệm tải ảnh từ URI, hiển thị nó, và thực hiện quá trình nhận dạng văn bản (OCR) bằng ML Kit.
     * Nó chạy trên một luồng nền để tránh chặn luồng UI.
     *
     * @param imageUri URI của ảnh cần xử lý OCR.
     */
    private void startOcrProcess(Uri imageUri) {
        editTextOCR.setText("Đang tải ảnh và trích xuất văn bản...");
        editTextOCR.setEnabled(false);
        btnCopyOCRText.setEnabled(false);
        btnSaveOCRImage.setEnabled(false);
        btnSaveWord.setEnabled(false);

        executorService.execute(() ->{
            Bitmap bitmap = null;
            InputStream inputStream = null;
            String ocrResult = "Lỗi khi trích xuất văn bản.";
            try{
                // Mở lại input stream cho quá trình OCR vì cái đã dùng để hiển thị có thể đã đóng
                inputStream = getContentResolver().openInputStream(imageUri);
                if(inputStream == null){
                    throw new IOException("Không thể mở InputStream từ URI cho OCR: " + imageUri);
                }
                bitmap = BitmapFactory.decodeStream(inputStream);
                inputStream.close(); // Đóng sau khi giải mã
                if (bitmap == null) {
                    throw new IOException("Không thể giải mã Bitmap từ InputStream cho OCR.");
                }

                // currentImageBitmap đã được thiết lập trong onCreate để hiển thị. Không cần thiết lập lại ở đây.
                // currentImageBitmap = bitmap; // Dòng này không cần thiết ở đây vì nó đã được thiết lập để hiển thị

                InputImage image = InputImage.fromBitmap(bitmap, 0);
                TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

                ocrResult = Tasks.await(recognizer.process(image)).getText();

            } catch (Exception e){
                Log.e(TAG, "Lỗi khi tải ảnh hoặc trích xuất văn bản OCR: " + e.getMessage(), e);
                ocrResult = "Lỗi: " + e.getMessage();
            } finally{
                if (inputStream != null) {
                    try {
                        inputStream.close();
                    } catch (IOException e) {
                        Log.e(TAG, "Lỗi khi đóng InputStream: " + e.getMessage(), e);
                    }
                }
            }
            final String finalOcrResult = ocrResult;
            mainHandler.post(() -> {
                editTextOCR.setText(finalOcrResult);
                editTextOCR.setEnabled(true);
                btnCopyOCRText.setEnabled(true);
                btnSaveOCRImage.setEnabled(true);
                btnSaveWord.setEnabled(true);

                // Không cần thiết lập ảnh ở đây, nó đã được thiết lập trong onCreate
                // if (currentImageBitmap != null) {
                //    ivImageForOCR.setImageBitmap(currentImageBitmap);
                // }

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
     * Phương thức này kiểm tra quyền ghi vào bộ nhớ ngoài và sau đó gọi phương thức {@link #performSaveTextFile(String, String)}
     * để lưu văn bản OCR vào một tệp với phần mở rộng được chỉ định.
     *
     * @param fileExtension Phần mở rộng của tệp (ví dụ: "txt").
     */
    private void saveTextToFile(String fileExtension) {
        String textToSave = editTextOCR.getText().toString().trim();
        if (textToSave.isEmpty()) {
            Toast.makeText(this, "Không có văn bản để lưu.", Toast.LENGTH_LONG).show();
            return;
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_WRITE_STORAGE);
        } else {
            performSaveTextFile(textToSave, fileExtension);
        }
    }
    /**
     * Thực hiện việc lưu nội dung văn bản vào một tệp trong thư mục "MyScannerApp_OCR" trong thư mục "Documents".
     * Việc lưu tệp được thực hiện trên một luồng nền.
     *
     * @param textContent Nội dung văn bản cần lưu.
     * @param fileExtension Phần mở rộng của tệp (ví dụ: "txt").
     */
    private void performSaveTextFile(String textContent, String fileExtension) {
        executorService.execute(() -> {
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String fileName = "OCR_Text_" + timeStamp + "." + fileExtension;
            String mimeType = "text/plain"; // Luôn là plaintext

            Uri fileUri = null;
            ContentResolver resolver = getContentResolver();
            ContentValues contentValues = new ContentValues();
            contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
            contentValues.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);

            OutputStream outputStream = null;
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + File.separator + "MyOCRTexts");
                    fileUri = resolver.insert(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), contentValues);
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
                    mainHandler.post(() -> Toast.makeText(OCRActivity.this, "Đã lưu tệp: " + fileName, Toast.LENGTH_LONG).show());
                } else {
                    throw new IOException("Không thể mở OutputStream.");
                }
            } catch (IOException e) {
                Log.e(TAG, "Lỗi khi lưu tệp văn bản: " + e.getMessage(), e);
                mainHandler.post(() -> Toast.makeText(OCRActivity.this, "Lỗi khi lưu tệp: " + e.getMessage(), Toast.LENGTH_LONG).show());
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
     * và sau đó gọi phương thức {@link #performSaveImageFile(Bitmap)} để lưu {@code Bitmap} hiện tại (ảnh OCR) vào một tệp ảnh.
     */
    private void saveOcrImageToFile() {
        if (currentImageBitmap == null) {
            Toast.makeText(this, "Không có ảnh để lưu.", Toast.LENGTH_LONG).show();
            return;
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_WRITE_STORAGE);
        } else {
            performSaveImageFile(currentImageBitmap);
        }
    }
    /**
     * Thực hiện việc lưu ảnh {@code Bitmap} vào thư mục ảnh của thiết bị (thư mục "MyScannerApp_OCR" trong "Pictures").
     * Việc lưu tệp ảnh được thực hiện trên một luồng nền.
     *
     * @param imageBitmap Bitmap của ảnh cần lưu.
     */
    private void performSaveImageFile(Bitmap imageBitmap) {
        executorService.execute(() -> {
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String fileName = "OCR_Image_" + timeStamp + ".jpeg"; // Đổi đuôi file thành .jpeg
            String mimeType = "image/jpeg"; // Đổi MIME type thành image/jpeg

            Uri collectionUri = null;
            OutputStream outputStream = null;
            boolean saved = false;

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ContentValues contentValues = new ContentValues();
                    contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                    contentValues.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
                    // Đặt đường dẫn lưu ảnh khớp với đường dẫn lưu PDF: Documents/MyPDFs
                    contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + File.separator + "MyPDFs");

                    // Sử dụng MediaStore.Files cho các tệp chung trong thư mục Documents
                    collectionUri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
                    Uri itemUri = getContentResolver().insert(collectionUri, contentValues);

                    if (itemUri == null) {
                        throw new IOException("Không thể tạo URI cho ảnh.");
                    }
                    outputStream = getContentResolver().openOutputStream(itemUri);
                    if (outputStream == null) {
                        throw new IOException("Không thể mở OutputStream cho ảnh.");
                    }

                    // Nén và lưu Bitmap dưới dạng JPEG
                    saved = imageBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream);

                } else {
                    // Đối với Android thấp hơn Q
                    // Đặt đường dẫn lưu ảnh khớp với đường dẫn lưu PDF: Documents/MyPDFs
                    File parentDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "MyPDFs");
                    if (!parentDir.exists()) {
                        parentDir.mkdirs(); // Tạo thư mục nếu nó chưa tồn tại
                    }
                    File imageFile = new File(parentDir, fileName);

                    outputStream = new FileOutputStream(imageFile);
                    saved = imageBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream);

                    if (saved) {
                        // Thông báo cho MediaStore về tệp mới để nó hiển thị trong thư viện
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