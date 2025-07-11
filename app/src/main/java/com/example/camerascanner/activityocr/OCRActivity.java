// File: com/example/camerascanner/activityocr/OCRActivity.java
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
import com.google.mlkit.vision.text.Text;
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

/**
 * OCRActivity là Activity chịu trách nhiệm xử lý và hiển thị kết quả Nhận dạng ký tự quang học (OCR).
 * Nó nhận một URI ảnh, tải ảnh, thực hiện quá trình OCR sử dụng ML Kit Text Recognition,
 * và hiển thị ảnh gốc cùng với văn bản được phát hiện (với tọa độ) trên một Custom View (OcrOverlayView).
 * Activity này cũng cung cấp chức năng sao chép văn bản và lưu văn bản/ảnh đã xử lý vào bộ nhớ thiết bị.
 */
public class OCRActivity extends AppCompatActivity {

    // Khai báo các thành phần UI
    private ImageButton btnOCRBack;
    private AppCompatButton btnSaveOCRImageAndWord, btnCopyOCRText;
    private ImageView ivImageForOCR; // Re-declared: Used to display original image
    // OcrOverlayView là một Custom View dùng để vẽ văn bản được nhận diện
    // kèm tọa độ lên trên một nền trắng (hoặc ảnh gốc)
    private OcrOverlayView ocrOverlayView;

    // Hằng số cho Intent và quyền truy cập
    public static final String EXTRA_IMAGE_URI_FOR_OCR = "image_uri_for_ocr"; //
    private static final String TAG = "OCRActivity"; // Dùng cho Logcat
    private static final int REQUEST_WRITE_STORAGE = 100; // Mã yêu cầu quyền ghi bộ nhớ

    // Các thành phần xử lý đa luồng và dữ liệu
    private ExecutorService executorService; // Để thực hiện các tác vụ nặng (OCR, lưu file) ở luồng nền
    private Handler mainHandler; // Để cập nhật UI trên luồng chính
    private Uri imageUriToProcess; // URI của ảnh đầu vào cần xử lý OCR
    private Bitmap currentImageBitmap; // Bitmap của ảnh đang được hiển thị và xử lý OCR
    private Text currentOcrResult; // Đối tượng Text đầy đủ từ ML Kit, chứa văn bản và tọa độ

    /**
     * Phương thức được gọi khi Activity lần đầu tiên được tạo.
     * Thiết lập layout, ánh xạ các View, khởi tạo các đối tượng cần thiết,
     * tải ảnh từ Intent và bắt đầu quá trình OCR.
     *
     * @param saveInstance Đối tượng Bundle chứa trạng thái Activity trước đó nếu có.
     */
    @Override
    protected void onCreate(Bundle saveInstance) {
        super.onCreate(saveInstance);
        setContentView(R.layout.activity_ocr);

        // Ánh xạ các thành phần UI từ layout XML
        ivImageForOCR = findViewById(R.id.ivImageForOcr); // Re-referencing ImageView
        btnOCRBack = findViewById(R.id.btnOCRBack); //
        btnSaveOCRImageAndWord = findViewById(R.id.btnSaveOCRImageAndWord); //
        btnCopyOCRText = findViewById(R.id.btnCopyOCRText); //
        ocrOverlayView = findViewById(R.id.ocrOverlayView); //

        // Khởi tạo ExecutorService để chạy tác vụ nền
        executorService = Executors.newSingleThreadExecutor();
        // Khởi tạo Handler để tương tác với luồng UI chính
        mainHandler = new Handler(Looper.getMainLooper());

        // Lấy URI ảnh từ Intent
        String imageUriString = getIntent().getStringExtra(EXTRA_IMAGE_URI_FOR_OCR);

        // Kiểm tra xem URI ảnh có tồn tại không
        if (imageUriString != null) { //
            imageUriToProcess = Uri.parse(imageUriString);
            try { //
                // Mở InputStream từ URI và giải mã thành Bitmap
                InputStream inputStream = getContentResolver().openInputStream(imageUriToProcess); //
                if (inputStream != null) { //
                    currentImageBitmap = BitmapFactory.decodeStream(inputStream); //
                    ivImageForOCR.setImageBitmap(currentImageBitmap); // Re-set image on ImageView
                    inputStream.close(); //
                    startOcrProcess(currentImageBitmap); // Bắt đầu quá trình OCR
                } else { //
                    Toast.makeText(this, "Không thể tải ảnh cho OCR.", Toast.LENGTH_SHORT).show(); //
                    Log.e(TAG, "InputStream is null for URI: " + imageUriToProcess); //
                }
            } catch (IOException e) { //
                Log.e(TAG, "Lỗi khi tải ảnh từ URI trong OCRActivity: " + e.getMessage(), e); //
                Toast.makeText(this, "Lỗi khi tải ảnh cho OCR: " + e.getMessage(), Toast.LENGTH_SHORT).show(); //
            }
        } else { //
            // Thông báo và đóng Activity nếu không có ảnh
            Toast.makeText(this, "Không nhận được ảnh để xử lý OCR.", Toast.LENGTH_SHORT).show(); //
            finish(); //
        }

        // Thiết lập sự kiện click cho nút "Quay lại"
        btnOCRBack.setOnClickListener(v -> { //
            Intent intent = new Intent(OCRActivity.this, MainActivity.class); //
            startActivity(intent); //
            finish(); // Đóng Activity hiện tại
        });

        // Thiết lập sự kiện click cho nút "Lưu ảnh và tạo text!"
        btnSaveOCRImageAndWord.setOnClickListener(v -> { //
            // Lấy văn bản đầy đủ từ kết quả OCR
            String ocrText = (currentOcrResult != null) ? currentOcrResult.getText().trim() : ""; //
            if (ocrText.isEmpty()) { //
                Toast.makeText(this, "Không có văn bản để lưu.", Toast.LENGTH_LONG).show(); //
                return; //
            }
            if (currentImageBitmap == null) { //
                Toast.makeText(this, "Không có ảnh để lưu.", Toast.LENGTH_LONG).show(); //
                return; //
            }

            // Tạo một dấu thời gian chung cho cả tên file ảnh và file văn bản
            String commonTimestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()); //

            // Lưu văn bản và ảnh vào bộ nhớ
            saveTextToFile(ocrText, commonTimestamp, "txt"); //
            // Để lưu ảnh đã có nền trắng và văn bản nhận diện, bạn cần tạo một Bitmap mới từ OcrOverlayView.
            // Điều này phức tạp hơn và thường được thực hiện bằng cách vẽ OcrOverlayView lên một Bitmap mới.
            // Hiện tại, saveOcrImageToFile đang lưu ảnh gốc.
            // Nếu bạn muốn lưu ảnh overlay, bạn cần implement một phương thức để lấy Bitmap từ OcrOverlayView.
            // Ví dụ: Bitmap overlayBitmap = ocrOverlayView.getBitmap();
            // Rồi gọi saveOcrImageToFile(overlayBitmap, commonTimestamp);
            // Trong ví dụ này, tôi vẫn giữ nguyên việc lưu ảnh gốc như trước để giữ đơn giản,
            // vì việc tạo bitmap từ view có thể cần thêm code và xử lý.
            saveOcrImageToFile(currentImageBitmap, commonTimestamp); // Vẫn lưu ảnh gốc

            // Sau khi lưu, quay lại MainActivity sau một khoảng thời gian ngắn
            mainHandler.postDelayed(() -> { //
                Intent intent = new Intent(OCRActivity.this, MainActivity.class); //
                startActivity(intent); //
                finish(); //
            }, 500); //
        });

        // Thiết lập sự kiện click cho nút "Copy"
        btnCopyOCRText.setOnClickListener(v -> { //
            copyTextToClipboard(); // Gọi phương thức sao chép văn bản
        });
    }

    /**
     * Sao chép văn bản được nhận diện vào clipboard của hệ thống.
     * Hiển thị thông báo Toast về kết quả sao chép.
     */
    private void copyTextToClipboard() { //
        // Lấy văn bản đầy đủ từ kết quả OCR
        String textToCopy = (currentOcrResult != null) ? currentOcrResult.getText().trim() : ""; //
        if (textToCopy.isEmpty()) { //
            Toast.makeText(this, "Không có văn bản để sao chép", Toast.LENGTH_LONG).show(); //
            return; //
        }

        // Lấy dịch vụ ClipboardManager và tạo ClipData
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE); //
        ClipData clip = ClipData.newPlainText("Văn bản OCR", textToCopy); //
        if (clipboard != null) { //
            clipboard.setPrimaryClip(clip); // Đặt dữ liệu vào clipboard
            Toast.makeText(this, "Đã sao chép văn bản vào bộ nhớ tạm! ", Toast.LENGTH_LONG).show(); //
        } else { //
            Toast.makeText(this, "Không thể sao chép văn bản! ", Toast.LENGTH_LONG).show(); //
        }
    }

    /**
     * Bắt đầu quá trình nhận dạng văn bản (OCR) trên một Bitmap.
     * Sử dụng ML Kit Text Recognition để xử lý ảnh và cập nhật giao diện người dùng
     * với kết quả văn bản và tọa độ.
     *
     * @param imageBitmap Bitmap của ảnh cần được xử lý OCR.
     */
    private void startOcrProcess(Bitmap imageBitmap) { //
        // Vô hiệu hóa các nút để tránh người dùng thao tác trong khi xử lý
        btnCopyOCRText.setEnabled(false); //
        btnSaveOCRImageAndWord.setEnabled(false); //

        // Chạy tác vụ OCR trên một luồng nền để tránh chặn luồng UI
        executorService.execute(() -> { //
            String fullOcrText = "Lỗi khi trích xuất văn bản."; // Mặc định là lỗi
            Text resultText = null; // Biến để lưu đối tượng Text từ ML Kit

            try { //
                if (imageBitmap == null) { //
                    throw new IOException("Bitmap để xử lý OCR là null."); //
                }

                // Tạo InputImage từ Bitmap
                InputImage image = InputImage.fromBitmap(imageBitmap, 0); //
                // Lấy thể hiện của TextRecognizer
                TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS); //

                // Thực hiện quá trình nhận dạng văn bản và chờ kết quả
                resultText = Tasks.await(recognizer.process(image)); //
                // Lấy toàn bộ văn bản được nhận dạng
                fullOcrText = resultText.getText(); //

            } catch (Exception e) { //
                // Xử lý lỗi nếu quá trình OCR thất bại
                Log.e(TAG, "Lỗi khi trích xuất văn bản OCR: " + e.getMessage(), e); //
                fullOcrText = "Lỗi: " + e.getMessage(); //
                resultText = null; // Đặt kết quả về null nếu có lỗi
            }

            // Gửi kết quả về luồng UI để cập nhật giao diện
            final String finalFullOcrText = fullOcrText; //
            final Text finalResultText = resultText; //

            mainHandler.post(() -> { //
                // Lưu kết quả OCR đầy đủ vào biến của Activity
                currentOcrResult = finalResultText; //

                if (currentImageBitmap != null && currentOcrResult != null) { //
                    // Truyền kết quả OCR và kích thước ảnh gốc cho OcrOverlayView để vẽ
                    // OcrOverlayView sẽ tính toán lại tỉ lệ và vị trí để vẽ văn bản đúng cách
                    ocrOverlayView.setOcrResult(currentOcrResult, currentImageBitmap.getWidth(), currentImageBitmap.getHeight()); //
                } else { //
                    // Xóa bất kỳ bản vẽ nào nếu có lỗi hoặc không có kết quả
                    ocrOverlayView.setOcrResult(null, 0, 0); //
                }

                // Kích hoạt lại các nút sau khi xử lý xong
                btnCopyOCRText.setEnabled(true); //
                btnSaveOCRImageAndWord.setEnabled(true); //

                // Hiển thị Toast dựa trên kết quả
                if (finalFullOcrText.startsWith("Lỗi:")) { //
                    Toast.makeText(OCRActivity.this, finalFullOcrText, Toast.LENGTH_LONG).show(); //
                } else if (finalFullOcrText.isEmpty()) { //
                    Toast.makeText(OCRActivity.this, "Không tìm thấy văn bản nào trong ảnh.", Toast.LENGTH_SHORT).show(); //
                }
            });
        });
    }

    /**
     * Xử lý kết quả yêu cầu cấp quyền từ người dùng.
     * Nếu quyền WRITE_EXTERNAL_STORAGE được cấp, thông báo cho người dùng.
     *
     * @param requestCode  Mã yêu cầu đã được cung cấp khi gọi requestPermissions.
     * @param permissions  Mảng các quyền được yêu cầu.
     * @param grantResults Kết quả của việc cấp quyền (PERMISSION_GRANTED hoặc PERMISSION_DENIED) tương ứng với mỗi quyền.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) { //
        super.onRequestPermissionsResult(requestCode, permissions, grantResults); //
        if (requestCode == REQUEST_WRITE_STORAGE) { //
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) { //
                Toast.makeText(this, "Đã cấp quyền lưu trữ. Vui lòng thử lại hành động lưu.", Toast.LENGTH_SHORT).show(); //
            } else { //
                Toast.makeText(this, "Ứng dụng cần quyền ghi để lưu tệp.", Toast.LENGTH_LONG).show(); //
            }
        }
    }

    /**
     * Yêu cầu quyền WRITE_EXTERNAL_STORAGE (nếu cần) và sau đó tiến hành lưu văn bản vào tệp.
     * Đối với Android 10 (API 29) trở lên, sử dụng MediaStore API để lưu trữ.
     * Đối với Android 9 (API 28) trở xuống, lưu trực tiếp vào thư mục công cộng.
     *
     * @param textToSave      Văn bản cần lưu.
     * @param commonTimestamp Dấu thời gian chung để đặt tên tệp.
     * @param fileExtension   Phần mở rộng của tệp (ví dụ: "txt").
     */
    private void saveTextToFile(String textToSave, String commonTimestamp, String fileExtension) { //
        // Kiểm tra quyền ghi bộ nhớ nếu phiên bản Android < Q (API 29)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) //
                != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) { //
            ActivityCompat.requestPermissions(this, //
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, //
                    REQUEST_WRITE_STORAGE); //
        } else { //
            // Nếu quyền đã được cấp hoặc là Android Q trở lên, tiến hành lưu
            performSaveTextFile(textToSave, commonTimestamp, fileExtension); //
        }
    }

    /**
     * Thực hiện việc lưu văn bản vào một tệp trong thư mục "Downloads/MyOCRTexts".
     * Sử dụng MediaStore cho Android Q+ và FileOutputStream cho các phiên bản cũ hơn.
     *
     * @param textContent     Nội dung văn bản cần ghi vào tệp.
     * @param commonTimestamp Dấu thời gian để đặt tên tệp.
     * @param fileExtension   Phần mở rộng của tệp.
     */
    private void performSaveTextFile(String textContent, String commonTimestamp, String fileExtension) { //
        executorService.execute(() -> { // Thực hiện trên luồng nền
            String fileName = "OCR_Text_" + commonTimestamp + "." + fileExtension; //
            String mimeType = "text/plain"; //

            Uri fileUri = null; //
            ContentResolver resolver = getContentResolver(); //
            ContentValues contentValues = new ContentValues(); //
            contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName); //
            contentValues.put(MediaStore.MediaColumns.MIME_TYPE, mimeType); //

            OutputStream outputStream = null; //
            try { //
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { //
                    // Đối với Android Q trở lên, sử dụng MediaStore.Downloads
                    contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + File.separator + "MyOCRTexts"); //
                    fileUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues); //
                    if (fileUri == null) { //
                        throw new IOException("Không thể tạo URI cho tệp."); //
                    }
                    outputStream = resolver.openOutputStream(fileUri); //
                } else { //
                    // Đối với các phiên bản cũ hơn, lưu trực tiếp vào thư mục công cộng
                    File documentsFolder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "MyOCRTexts"); //
                    if (!documentsFolder.exists()) { //
                        documentsFolder.mkdirs(); // Tạo thư mục nếu chưa có
                    }
                    File file = new File(documentsFolder, fileName); //
                    fileUri = Uri.fromFile(file); //
                    outputStream = new FileOutputStream(file); //
                }

                if (outputStream != null) { //
                    outputStream.write(textContent.getBytes()); //
                    outputStream.close(); //
                    mainHandler.post(() -> Toast.makeText(OCRActivity.this, "Đã lưu văn bản: " + fileName, Toast.LENGTH_LONG).show()); //
                } else { //
                    throw new IOException("Không thể mở OutputStream."); //
                }
            } catch (IOException e) { //
                Log.e(TAG, "Lỗi khi lưu tệp văn bản: " + e.getMessage(), e); //
                mainHandler.post(() -> Toast.makeText(OCRActivity.this, "Lỗi khi lưu tệp văn bản: " + e.getMessage(), Toast.LENGTH_LONG).show()); //
            } finally { //
                // Đảm bảo đóng OutputStream
                if (outputStream != null) { //
                    try { //
                        outputStream.close(); //
                    } catch (IOException e) { //
                        Log.e(TAG, "Lỗi khi đóng OutputStream: " + e.getMessage(), e); //
                    }
                }
            }
        });
    }

    /**
     * Yêu cầu quyền WRITE_EXTERNAL_STORAGE (nếu cần) và sau đó tiến hành lưu ảnh đã xử lý OCR.
     * Đối với Android 10 (API 29) trở lên, sử dụng MediaStore API để lưu trữ.
     * Đối với Android 9 (API 28) trở xuống, lưu trực tiếp vào thư mục công cộng.
     *
     * @param imageBitmap     Bitmap của ảnh cần lưu.
     * @param commonTimestamp Dấu thời gian chung để đặt tên tệp.
     */
    private void saveOcrImageToFile(Bitmap imageBitmap, String commonTimestamp) { //
        // Tương tự như lưu văn bản, kiểm tra quyền ghi nếu cần
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) //
                != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) { //
            ActivityCompat.requestPermissions(this, //
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, //
                    REQUEST_WRITE_STORAGE); //
        } else { //
            performSaveImageFile(imageBitmap, commonTimestamp); //
        }
    }

    /**
     * Thực hiện việc lưu Bitmap ảnh vào tệp JPEG trong thư mục "Downloads/MyOCRImages".
     * Sử dụng MediaStore cho Android Q+ và FileOutputStream cho các phiên bản cũ hơn.
     *
     * @param imageBitmap     Bitmap của ảnh cần lưu.
     * @param commonTimestamp Dấu thời gian để đặt tên tệp.
     */
    private void performSaveImageFile(Bitmap imageBitmap, String commonTimestamp) { //
        executorService.execute(() -> { // Thực hiện trên luồng nền
            String fileName = "OCR_Image_" + commonTimestamp + ".jpeg"; //
            String mimeType = "image/jpeg"; //

            Uri collectionUri = null; //
            OutputStream outputStream = null; //
            boolean saved = false; //

            try { //
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { //
                    // Đối với Android Q trở lên, sử dụng MediaStore.Downloads
                    ContentValues contentValues = new ContentValues(); //
                    contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName); //
                    contentValues.put(MediaStore.MediaColumns.MIME_TYPE, mimeType); //
                    contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + File.separator + "MyOCRImages"); //
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 1); // Đánh dấu là đang chờ

                    collectionUri = MediaStore.Downloads.EXTERNAL_CONTENT_URI; //
                    Uri itemUri = getContentResolver().insert(collectionUri, contentValues); //

                    if (itemUri == null) { //
                        throw new IOException("Không thể tạo URI cho ảnh."); //
                    }
                    outputStream = getContentResolver().openOutputStream(itemUri); //
                    if (outputStream == null) { //
                        throw new IOException("Không thể mở OutputStream cho ảnh."); //
                    }

                    saved = imageBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream); //

                    if (saved) { //
                        // Đánh dấu ảnh đã hoàn tất và hiển thị trong Gallery/Files
                        ContentValues updateContentValues = new ContentValues(); //
                        updateContentValues.put(MediaStore.Images.Media.IS_PENDING, 0); //
                        getContentResolver().update(itemUri, updateContentValues, null, null); //
                    }

                } else { //
                    // Đối với các phiên bản cũ hơn, lưu trực tiếp vào thư mục công cộng
                    File parentDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "MyOCRImages"); //
                    if (!parentDir.exists()) { //
                        parentDir.mkdirs(); //
                    }
                    File imageFile = new File(parentDir, fileName); //

                    outputStream = new FileOutputStream(imageFile); //
                    saved = imageBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream); //

                    if (saved) { //
                        // Quét Media Store để hiển thị ảnh mới
                        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE); //
                        mediaScanIntent.setData(Uri.fromFile(imageFile)); //
                        sendBroadcast(mediaScanIntent); //
                    }
                }

                final boolean finalSaved = saved; //
                mainHandler.post(() -> { //
                    if (finalSaved) { //
                        Toast.makeText(OCRActivity.this, "Đã lưu ảnh OCR: " + fileName, Toast.LENGTH_LONG).show(); //
                    } else { //
                        Toast.makeText(OCRActivity.this, "Không thể lưu ảnh OCR.", Toast.LENGTH_LONG).show(); //
                    }
                });

            } catch (IOException e) { //
                Log.e(TAG, "Lỗi khi lưu ảnh OCR: " + e.getMessage(), e); //
                final String errorMessage = "Lỗi khi lưu ảnh: " + e.getMessage(); //
                mainHandler.post(() -> Toast.makeText(OCRActivity.this, errorMessage, Toast.LENGTH_LONG).show()); //
            } finally { //
                // Đảm bảo đóng OutputStream
                if (outputStream != null) { //
                    try { //
                        outputStream.close(); //
                    } catch (IOException e) { //
                        Log.e(TAG, "Lỗi khi đóng OutputStream ảnh: " + e.getMessage(), e); //
                    }
                }
            }
        });
    }

    /**
     * Phương thức được gọi khi Activity bị hủy.
     * Đảm bảo giải phóng tài nguyên như ExecutorService và Bitmap,
     * và xóa file tạm thời nếu có.
     */
    @Override
    protected void onDestroy() { //
        super.onDestroy(); //
        // Đóng ExecutorService nếu nó đang chạy
        if (executorService != null && !executorService.isShutdown()) { //
            executorService.shutdownNow(); //
            try { //
                // Chờ một thời gian ngắn để các luồng kết thúc
                if (!executorService.awaitTermination(1, TimeUnit.SECONDS)) { //
                    Log.w(TAG, "ExecutorService không dừng hoàn toàn."); //
                }
            } catch (InterruptedException e) { //
                Log.e(TAG, "Interrupted while waiting for executor to shut down.", e); //
                Thread.currentThread().interrupt(); // Đặt lại trạng thái ngắt
            }
        }

        // Giải phóng Bitmap để tránh rò rỉ bộ nhớ
        // (Bạn vẫn cần giải phóng currentImageBitmap nếu nó được tải để lấy kích thước)
        if (currentImageBitmap != null) { //
            currentImageBitmap.recycle(); //
            currentImageBitmap = null; //
        }

        // Xóa file tạm thời nếu nó được tạo từ scheme "file://"
        // (Điều này quan trọng nếu ảnh được lấy từ Camera hoặc thư mục tạm thời)
        if (imageUriToProcess != null && ContentResolver.SCHEME_FILE.equals(imageUriToProcess.getScheme())) { //
            File file = new File(imageUriToProcess.getPath()); //
            if (file.exists()) { //
                boolean deleted = file.delete(); //
                if (deleted) { //
                    Log.d(TAG, "Đã xóa file tạm thời: " + file.getAbsolutePath()); //
                } else { //
                    Log.e(TAG, "Không thể xóa file tạm thời: " + file.getAbsolutePath()); //
                }
            }
        }
    }
}