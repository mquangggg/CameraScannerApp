package com.example.camerascanner.activitymain;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.camerascanner.activitycamera.CameraActivity;
import com.example.camerascanner.R;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CAMERA_PERMISSION = 101;
    private static final int REQUEST_STORAGE_PERMISSION = 102;
    private static final String TAG = "MainActivity";

    private RecyclerView recyclerView;
    private PdfFileAdapter pdfAdapter; // Adapter cho PDF
    private OcrPairedAdapter ocrAdapter; // Adapter mới cho OCR
    private List<File> pdfFiles;
    private List<OcrPairedItem> ocrPairedItems; // Danh sách các cặp OCR

    private TabLayout tabLayout;
    private FloatingActionButton fabScan;

    private ExecutorService fileLoadingExecutor; // Để tải tệp trong nền

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        tabLayout = findViewById(R.id.tabLayout);
        fabScan = findViewById(R.id.btnStartCamera);

        fileLoadingExecutor = Executors.newSingleThreadExecutor();

        // ĐÃ XÓA: Không thêm các tab bằng code nữa.
        // tabLayout.addTab(tabLayout.newTab().setText("PDF"));
        // tabLayout.addTab(tabLayout.newTab().setText("OCR"));

        // Khởi tạo adapters (ban đầu có thể là rỗng)
        pdfFiles = new ArrayList<>();
        ocrPairedItems = new ArrayList<>();
        pdfAdapter = new PdfFileAdapter(this, pdfFiles);
        ocrAdapter = new OcrPairedAdapter(this, ocrPairedItems);

        // Thiết lập listener cho tab
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (tab.getText() != null) {
                    if (tab.getText().equals("PDF gần đây")) { // ĐÃ SỬA: Thay đổi tên tab nếu cần, khớp với XML
                        recyclerView.setAdapter(pdfAdapter);
                        loadPdfFiles(); // Tải lại PDF khi chọn tab PDF
                    } else if (tab.getText().equals("OCR gần đây")) { // ĐÃ SỬA: Thay đổi tên tab nếu cần, khớp với XML
                        recyclerView.setAdapter(ocrAdapter);
                        loadOcrPairedItems(); // Tải lại OCR khi chọn tab OCR
                    }
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                // Không cần làm gì khi tab không được chọn
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                // Có thể làm mới dữ liệu khi tab được chọn lại
                if (tab.getText() != null) {
                    if (tab.getText().equals("PDF gần đây")) { // ĐÃ SỬA: Thay đổi tên tab nếu cần, khớp với XML
                        loadPdfFiles();
                    } else if (tab.getText().equals("OCR gần đây")) { // ĐÃ SỬA: Thay đổi tên tab nếu cần, khớp với XML
                        loadOcrPairedItems();
                    }
                }
            }
        });

        // Chọn tab PDF mặc định khi khởi động
        tabLayout.selectTab(tabLayout.getTabAt(0)); // Chọn tab PDF đầu tiên
        recyclerView.setAdapter(pdfAdapter); // Đặt adapter PDF mặc định

        fabScan.setOnClickListener(v -> {
            if (checkCameraPermission()) {
                Intent intent = new Intent(MainActivity.this, CameraActivity.class);
                startActivity(intent);
            } else {
                requestCameraPermission();
            }
        });

        // Kiểm tra quyền lưu trữ khi Activity bắt đầu
        checkAndRequestStoragePermission();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Tải lại dữ liệu cho tab hiện tại khi Activity trở lại foreground
        if (tabLayout.getSelectedTabPosition() == 0) { // PDF tab
            loadPdfFiles();
        } else if (tabLayout.getSelectedTabPosition() == 1) { // OCR tab
            loadOcrPairedItems();
        }
    }

    private boolean checkCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
    }

    private void checkAndRequestStoragePermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_STORAGE_PERMISSION);
            } else {
                // Quyền đã được cấp, tải tệp ngay lập tức
                if (tabLayout.getSelectedTabPosition() == 0) {
                    loadPdfFiles();
                } else if (tabLayout.getSelectedTabPosition() == 1) {
                    loadOcrPairedItems();
                }
            }
        } else {
            // Đối với Android Q (API 29) trở lên, không cần quyền WRITE_EXTERNAL_STORAGE cho thư mục riêng của ứng dụng
            // hoặc MediaStore. Các tệp sẽ được truy cập thông qua MediaStore hoặc thư mục riêng.
            // Tuy nhiên, nếu bạn đang truy cập các thư mục chung như Downloads/Documents, bạn vẫn cần quyền đọc.
            // Đối với mục đích của chúng ta, chúng ta sẽ giả định quyền đọc đã có hoặc không cần thiết cho MediaStore.
            if (tabLayout.getSelectedTabPosition() == 0) {
                loadPdfFiles();
            } else if (tabLayout.getSelectedTabPosition() == 1) {
                loadOcrPairedItems();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                fabScan.performClick(); // Thử lại hành động quét
            } else {
                Toast.makeText(this, "Cần quyền camera để sử dụng máy quét.", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQUEST_STORAGE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Đã cấp quyền lưu trữ.", Toast.LENGTH_SHORT).show();
                if (tabLayout.getSelectedTabPosition() == 0) {
                    loadPdfFiles();
                } else if (tabLayout.getSelectedTabPosition() == 1) {
                    loadOcrPairedItems();
                }
            } else {
                Toast.makeText(this, "Ứng dụng cần quyền lưu trữ để hiển thị tệp.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void loadPdfFiles() {
        fileLoadingExecutor.execute(() -> {
            List<File> loadedFiles = new ArrayList<>();
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File pdfsDir = new File(downloadsDir, "MyPDFs");

            if (pdfsDir.exists() && pdfsDir.isDirectory()) {
                File[] files = pdfsDir.listFiles((dir, name) -> name.endsWith(".pdf"));
                if (files != null) {
                    for (File file : files) {
                        loadedFiles.add(file);
                    }
                }
            }

            // Sắp xếp theo ngày sửa đổi gần nhất
            Collections.sort(loadedFiles, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));

            runOnUiThread(() -> {
                pdfFiles.clear();
                pdfFiles.addAll(loadedFiles);
                pdfAdapter.updateData(pdfFiles); // Cập nhật adapter
                // Kiểm tra nếu adapter hiện tại là pdfAdapter thì notifyDataSetChanged
                if (recyclerView.getAdapter() == pdfAdapter) {
                    pdfAdapter.notifyDataSetChanged();
                }
            });
        });
    }

    // PHƯƠNG THỨC MỚI ĐỂ TẢI VÀ GHÉP ĐÔI CÁC TỆP OCR
    private void loadOcrPairedItems() {
        fileLoadingExecutor.execute(() -> {
            List<OcrPairedItem> loadedPairedItems = new ArrayList<>();
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File ocrImagesDir = new File(downloadsDir, "MyOCRImages");
            File ocrTextsDir = new File(downloadsDir, "MyOCRTexts");

            // Đảm bảo các thư mục tồn tại, nếu không thì tạo chúng
            if (!ocrImagesDir.exists()) {
                ocrImagesDir.mkdirs();
            }
            if (!ocrTextsDir.exists()) {
                ocrTextsDir.mkdirs();
            }

            Map<String, File> imageMap = new HashMap<>();
            Map<String, File> textMap = new HashMap<>();

            // Thu thập tất cả các tệp ảnh OCR
            if (ocrImagesDir.exists() && ocrImagesDir.isDirectory()) {
                File[] imageFiles = ocrImagesDir.listFiles((dir, name) -> name.startsWith("OCR_Image_") && name.endsWith(".jpeg"));
                if (imageFiles != null) {
                    for (File file : imageFiles) {
                        String timestamp = extractTimestamp(file.getName(), "OCR_Image_");
                        if (timestamp != null) {
                            imageMap.put(timestamp, file);
                        }
                    }
                }
            }

            // Thu thập tất cả các tệp văn bản OCR
            if (ocrTextsDir.exists() && ocrTextsDir.isDirectory()) {
                File[] textFiles = ocrTextsDir.listFiles((dir, name) -> name.startsWith("OCR_Text_") && name.endsWith(".txt"));
                if (textFiles != null) {
                    for (File file : textFiles) {
                        String timestamp = extractTimestamp(file.getName(), "OCR_Text_");
                        if (timestamp != null) {
                            textMap.put(timestamp, file);
                        }
                    }
                }
            }

            // Ghép đôi các tệp dựa trên dấu thời gian
            for (Map.Entry<String, File> entry : imageMap.entrySet()) {
                String timestamp = entry.getKey();
                File imageFile = entry.getValue();
                File textFile = textMap.get(timestamp); // Lấy tệp văn bản tương ứng

                // Chỉ tạo cặp nếu cả ảnh và văn bản đều tồn tại
                if (textFile != null) {
                    long totalSize = imageFile.length() + textFile.length();
                    String formattedDate = getFormattedDateFromTimestamp(timestamp);
                    loadedPairedItems.add(new OcrPairedItem(imageFile, textFile, timestamp, formattedDate, totalSize));
                }
            }

            // Sắp xếp các cặp theo dấu thời gian (mới nhất trước)
            Collections.sort(loadedPairedItems, (item1, item2) -> item2.getTimestamp().compareTo(item1.getTimestamp()));

            runOnUiThread(() -> {
                ocrPairedItems.clear();
                ocrPairedItems.addAll(loadedPairedItems);
                ocrAdapter.updateData(ocrPairedItems); // Cập nhật adapter
                // Kiểm tra nếu adapter hiện tại là ocrAdapter thì notifyDataSetChanged
                if (recyclerView.getAdapter() == ocrAdapter) {
                    ocrAdapter.notifyDataSetChanged();
                }
            });
        });
    }

    // Helper method để trích xuất dấu thời gian từ tên tệp
    private String extractTimestamp(String fileName, String prefix) {
        if (fileName.startsWith(prefix) && fileName.contains("_") && fileName.contains(".")) {
            int startIndex = prefix.length();
            int endIndex = fileName.lastIndexOf(".");
            if (endIndex > startIndex) {
                return fileName.substring(startIndex, endIndex);
            }
        }
        return null;
    }

    // Helper method để định dạng ngày từ dấu thời gian
    private String getFormattedDateFromTimestamp(String timestamp) {
        try {
            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
            Date date = inputFormat.parse(timestamp);
            SimpleDateFormat outputFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
            return outputFormat.format(date);
        } catch (ParseException e) {
            Log.e(TAG, "Lỗi phân tích dấu thời gian: " + timestamp, e);
            return "Ngày không xác định";
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (fileLoadingExecutor != null && !fileLoadingExecutor.isShutdown()) {
            fileLoadingExecutor.shutdownNow();
        }
    }
}