package com.example.camerascanner;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout; // Thêm import này

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale; // Thêm import này
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE_READ_STORAGE = 1001;
    private FloatingActionButton startCamera;
    private RecyclerView recyclerViewFiles;
    private PdfFileAdapter pdfFileAdapter;
    private List<File> pdfFilesList;

    private LinearLayout btnHome, btnTools, btnMe;
    private ImageView ivHomeIcon, ivToolsIcon, ivMeIcon;
    private TextView tvHomeText, tvToolsText, tvMeText;


    // Các biến mới cho chức năng Văn bản
    private TabLayout tabLayoutFileTypes; // Khai báo TabLayout
    private WordFileAdapter wordFileAdapter; // Khai báo Adapter cho Word
    private List<File> wordFilesList; // Khai báo danh sách cho Word files
    private String currentFileType = "pdf"; // Biến để theo dõi loại file hiện tại

    private ExecutorService executorService;
    private Handler mainHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Ánh xạ các View của thanh điều hướng dưới cùng
        btnHome = findViewById(R.id.btnHome);
        btnTools = findViewById(R.id.btnTools);
        btnMe = findViewById(R.id.btnMe);

        ivHomeIcon = btnHome.findViewById(R.id.imageViewHomeIcon); // Bạn cần thêm ID cho ImageView trong XML
        tvHomeText = btnHome.findViewById(R.id.textViewHomeText); // Bạn cần thêm ID cho TextView trong XML

        ivToolsIcon = btnTools.findViewById(R.id.imageViewToolsIcon);
        tvToolsText = btnTools.findViewById(R.id.textViewToolsText);

        ivMeIcon = btnMe.findViewById(R.id.imageViewMeIcon);
        tvMeText = btnMe.findViewById(R.id.textViewMeText);

        startCamera = findViewById(R.id.btnStartCamera);
        recyclerViewFiles = findViewById(R.id.recyclerViewFiles);
        tabLayoutFileTypes = findViewById(R.id.tabLayoutFileTypes); // Khởi tạo TabLayout

        executorService = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());

        // Khởi tạo cho PDF
        pdfFilesList = new ArrayList<>();
        pdfFileAdapter = new PdfFileAdapter(this, pdfFilesList);
        // Khởi tạo cho Word
        wordFilesList = new ArrayList<>();
        wordFileAdapter = new WordFileAdapter(this, wordFilesList); // Đảm bảo bạn đã tạo lớp WordFileAdapter

        recyclerViewFiles.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewFiles.setAdapter(pdfFileAdapter); // Mặc định hiển thị PDF

        // Thiết lập lắng nghe sự kiện click cho các nút
        btnHome.setOnClickListener(v -> setSelectedBottomNavItem(btnHome));
        btnTools.setOnClickListener(v -> setSelectedBottomNavItem(btnTools));
        btnMe.setOnClickListener(v -> setSelectedBottomNavItem(btnMe));

// Đặt nút Home là được chọn mặc định khi khởi động
        setSelectedBottomNavItem(btnHome);

        startCamera.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, CameraActivity.class);
            startActivity(intent);
        });

        // Thiết lập lắng nghe sự kiện chuyển đổi tab
        tabLayoutFileTypes.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (tab.getPosition() == 0) { // Tab "PDF gần đây"
                    currentFileType = "pdf";
                    recyclerViewFiles.setAdapter(pdfFileAdapter); // Chuyển sang Adapter PDF
                    loadPdfFilesAsync(); // Tải lại danh sách PDF
                } else if (tab.getPosition() == 1) { // Tab "Văn bản gần đây"
                    currentFileType = "word";
                    recyclerViewFiles.setAdapter(wordFileAdapter); // Chuyển sang Adapter Word
                    loadWordFilesAsync(); // Tải lại danh sách Word
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                // Không cần làm gì khi tab không được chọn
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                // Tải lại dữ liệu khi tab đang được chọn lại
                if (tab.getPosition() == 0) {
                    loadPdfFilesAsync();
                } else if (tab.getPosition() == 1) {
                    loadWordFilesAsync();
                }
            }
        });

        // Tải các tệp ban đầu dựa trên tab đang chọn
        checkPermissionsAndLoadFiles();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Tải lại danh sách files mỗi khi Activity trở lại foreground
        if (currentFileType.equals("pdf")) {
            loadPdfFilesAsync();
        } else {
            loadWordFilesAsync();
        }
    }

    private void checkPermissionsAndLoadFiles() { // Đổi tên phương thức cho rõ ràng hơn
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Trên Android 10+ (API 29), không cần quyền READ_EXTERNAL_STORAGE để đọc các file riêng của ứng dụng
            // hoặc file được tạo qua MediaStore mà ứng dụng sở hữu.
            // Để đơn giản, ta vẫn gọi loadFilesAsync ở đây.
            // Lưu ý: Để đọc files từ các ứng dụng khác trong thư mục công khai, vẫn cần quyền
            loadFilesForCurrentTab();
        } else {
            // Đối với Android 9 (API 28) trở xuống, cần quyền READ_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        PERMISSION_REQUEST_CODE_READ_STORAGE);
            } else {
                loadFilesForCurrentTab();
            }
        }
    }

    private void loadFilesForCurrentTab() {
        if (currentFileType.equals("pdf")) {
            loadPdfFilesAsync();
        } else {
            loadWordFilesAsync();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE_READ_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadFilesForCurrentTab(); // Tải lại files sau khi có quyền
            } else {
                Toast.makeText(this, "Quyền đọc bộ nhớ là cần thiết để hiển thị các tệp đã lưu.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void loadPdfFilesAsync() {
        executorService.execute(() -> {
            List<File> files = new ArrayList<>();
            File docsDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "MyPDFs");

            if (docsDir.exists() && docsDir.isDirectory()) {
                File[] foundFiles = docsDir.listFiles((dir, name) ->
                        name.toLowerCase(Locale.ROOT).endsWith(".pdf") ||
                        name.toLowerCase(Locale.ROOT).endsWith(".jpeg") ); // Sử dụng toLowerCase để so sánh không phân biệt chữ hoa/thường
                if (foundFiles != null) {
                    Arrays.sort(foundFiles, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
                    files.addAll(Arrays.asList(foundFiles));
                }
            }

            mainHandler.post(() -> {
                pdfFilesList.clear();
                pdfFilesList.addAll(files);
                pdfFileAdapter.updateData(pdfFilesList);
                if (currentFileType.equals("pdf") && files.isEmpty()) { // Chỉ hiển thị toast nếu đang ở tab PDF
                    Toast.makeText(MainActivity.this, "Chưa có tệp PDF nào được lưu.", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    // Phương thức mới để tải các tệp văn bản (Word/Text)
    private void loadWordFilesAsync() {
        executorService.execute(() -> {
            List<File> files = new ArrayList<>();
            File wordDir;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                wordDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "MyOCRTexts");
            } else {
                wordDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "MyOCRTexts");
            }
            Log.d("MainActivity", "Background Thread: Checking directory: " + wordDir.getAbsolutePath());

            if (wordDir.exists() && wordDir.isDirectory()) {
                File[] foundFiles = wordDir.listFiles((dir, name) -> {
                    boolean isTxt = name.toLowerCase(Locale.ROOT).endsWith(".txt");
                    boolean isDocx = name.toLowerCase(Locale.ROOT).endsWith(".docx");
                    if (isTxt || isDocx) {
                        Log.d("MainActivity", "Background Thread: Found file: " + name);
                    }
                    return isTxt || isDocx;
                });
                if (foundFiles != null) {
                    Log.d("MainActivity", "Background Thread: Number of found files before sort: " + foundFiles.length);
                    Arrays.sort(foundFiles, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
                    files.addAll(Arrays.asList(foundFiles));
                    Log.d("MainActivity", "Background Thread: Number of files added to local list 'files': " + files.size());
                } else {
                    Log.w("MainActivity", "Background Thread: No files found or listFiles returned null in: " + wordDir.getAbsolutePath());
                }
            } else {
                Log.w("MainActivity", "Background Thread: Directory does not exist or is not a directory: " + wordDir.getAbsolutePath());
            }

            mainHandler.post(() -> {
                // Log trước khi xóa
                Log.d("MainActivity", "UI Thread: Before clear - wordFilesList size: " + wordFilesList.size());
                wordFilesList.clear();
                // Log sau khi xóa
                Log.d("MainActivity", "UI Thread: After clear - wordFilesList size: " + wordFilesList.size());
                // Log kích thước của list 'files' cục bộ
                Log.d("MainActivity", "UI Thread: Local 'files' list size (from background thread): " + files.size());
                wordFilesList.addAll(files);
                // Log sau khi thêm
                Log.d("MainActivity", "UI Thread: After addAll - wordFilesList size: " + wordFilesList.size());
                wordFileAdapter.updateData(wordFilesList);
                // Log cuối cùng (như cũ)
                Log.d("MainActivity", "UI Thread: Adapter updated with " + wordFilesList.size() + " files.");
                if (currentFileType.equals("word") && files.isEmpty()) {
                    Toast.makeText(MainActivity.this, "Chưa có tệp văn bản nào được lưu.", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    // Trong MainActivity.java, bên ngoài phương thức onCreate()
    private void setSelectedBottomNavItem(LinearLayout selectedItem) {
        // Đặt lại tất cả về màu trắng (không chọn)
        int defaultColor = ContextCompat.getColor(this, android.R.color.white);
        int selectedColor = ContextCompat.getColor(this, R.color.green_accent);

        ivHomeIcon.setColorFilter(defaultColor);
        tvHomeText.setTextColor(defaultColor);

        ivToolsIcon.setColorFilter(defaultColor);
        tvToolsText.setTextColor(defaultColor);

        ivMeIcon.setColorFilter(defaultColor);
        tvMeText.setTextColor(defaultColor);

        // Đặt nút được chọn thành màu xanh
        if (selectedItem == btnHome) {
            ivHomeIcon.setColorFilter(selectedColor);
            tvHomeText.setTextColor(selectedColor);
            // TODO: Thực hiện hành động khi Home được chọn (ví dụ: chuyển Fragment, Activity, hoặc thay đổi nội dung)
            Toast.makeText(this, "Home được chọn", Toast.LENGTH_SHORT).show();
        } else if (selectedItem == btnTools) {
            ivToolsIcon.setColorFilter(selectedColor);
            tvToolsText.setTextColor(selectedColor);
            // TODO: Thực hiện hành động khi Tools được chọn
            Toast.makeText(this, "Tools được chọn", Toast.LENGTH_SHORT).show();
        } else if (selectedItem == btnMe) {
            ivMeIcon.setColorFilter(selectedColor);
            tvMeText.setTextColor(selectedColor);
            // TODO: Thực hiện hành động khi Me được chọn
            Toast.makeText(this, "Me được chọn", Toast.LENGTH_SHORT).show();
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