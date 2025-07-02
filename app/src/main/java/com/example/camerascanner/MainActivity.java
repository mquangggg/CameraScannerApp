package com.example.camerascanner;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE_READ_STORAGE = 1001; // Mã yêu cầu quyền
    private FloatingActionButton startCamera;
    private RecyclerView recyclerViewFiles;
    private PdfFileAdapter pdfFileAdapter;
    private List<File> pdfFilesList;

    private ExecutorService executorService;
    private Handler mainHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) { // Đổi saveInstanceState thành savedInstanceState
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        startCamera = findViewById(R.id.btnStartCamera);
        recyclerViewFiles = findViewById(R.id.recyclerViewFiles);

        executorService = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());

        pdfFilesList = new ArrayList<>();
        pdfFileAdapter = new PdfFileAdapter(this, pdfFilesList);
        recyclerViewFiles.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewFiles.setAdapter(pdfFileAdapter);

        startCamera.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, CameraActivity.class);
            startActivity(intent);
        });

        // Tải các tệp PDF khi Activity khởi tạo
        checkPermissionsAndLoadPdfs();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Tải lại danh sách PDF mỗi khi Activity trở lại foreground
        loadPdfFilesAsync();
    }

    private void checkPermissionsAndLoadPdfs() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Trên Android 10 (API 29) trở lên, không cần quyền READ_EXTERNAL_STORAGE
            // để đọc các file riêng của ứng dụng hoặc file được tạo qua MediaStore mà ứng dụng sở hữu.
            // Tuy nhiên, nếu bạn muốn đọc các file bất kỳ trong Downloads, vẫn cần quyền.
            // Để đơn giản, ta vẫn gọi loadPdfFilesAsync
            loadPdfFilesAsync();
        } else {
            // Đối với Android 9 (API 28) trở xuống, cần quyền READ_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        PERMISSION_REQUEST_CODE_READ_STORAGE);
            } else {
                loadPdfFilesAsync();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE_READ_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadPdfFilesAsync();
            } else {
                Toast.makeText(this, "Quyền đọc bộ nhớ là cần thiết để hiển thị các tệp PDF đã lưu.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void loadPdfFilesAsync() {
        executorService.execute(() -> {
            List<File> files = new ArrayList<>();
            File pdfDir;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Trên Android 10+, files được lưu vào Downloads/MyPDFs thông qua MediaStore
                // Cách tốt nhất là truy vấn MediaStore, nhưng việc đọc trực tiếp File object
                // từ Environment.DIRECTORY_DOWNLOADS cũng hoạt động nếu ứng dụng đã tạo ra chúng.
                // Để đơn giản, ta vẫn dùng cách đọc File trực tiếp ở đây.
                pdfDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "MyPDFs");
            } else {
                // Dưới Android 10, vẫn là thư mục công khai
                pdfDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "MyPDFs");
            }

            if (pdfDir.exists() && pdfDir.isDirectory()) {
                File[] foundFiles = pdfDir.listFiles((dir, name) -> name.endsWith(".pdf"));
                if (foundFiles != null) {
                    // Sắp xếp theo ngày sửa đổi gần nhất (mới nhất lên đầu)
                    Arrays.sort(foundFiles, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
                    files.addAll(Arrays.asList(foundFiles));
                }
            }

            mainHandler.post(() -> {
                pdfFilesList.clear();
                pdfFilesList.addAll(files);
                pdfFileAdapter.updateData(pdfFilesList); // Cập nhật dữ liệu trong adapter
                if (files.isEmpty()) {
                    Toast.makeText(MainActivity.this, "Chưa có tệp PDF nào được lưu.", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow();
        }
    }
}