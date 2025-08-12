// File: com/example/camerascanner/activitymain/MainActivity.java
package com.example.camerascanner.activitymain;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.camerascanner.R;
import com.example.camerascanner.activitycamera.CameraActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationBarView;
import com.google.android.material.resources.MaterialAttributes;
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


/**
 * MainActivity là màn hình chính của ứng dụng, hiển thị giao diện tab
 * để người dùng xem các tệp PDF và các cặp tệp OCR (ảnh và văn bản) đã lưu.
 * Nó cũng cung cấp chức năng mở Camera để chụp ảnh mới và xử lý các quyền cần thiết.
 */
public class MainActivity extends com.example.camerascanner.BaseActivity {

    // Hằng số cho các mã yêu cầu quyền
    private static final int REQUEST_CAMERA_PERMISSION = 101;
    private static final int REQUEST_STORAGE_PERMISSION = 102;
    private static final String TAG = "MainActivity"; // Thẻ dùng để ghi log

    // Khai báo các thành phần UI
    private RecyclerView recyclerView;
    private TabLayout tabLayout;
    private EditText etSearch;
    // Danh sách đầy đủ ban đầu (trước khi lọc) để tìm kiếm
    private List<File> originalPdfFiles;
    private List<OcrPairedItem> originalOcrPairedItems;
    private Button btnPdfAndOcr;
    private FloatingActionButton fabScan; // Nút Floating Action Button để bắt đầu quét

    // Khai báo Adapters và danh sách dữ liệu
    private PdfFileAdapter pdfAdapter; // Adapter cho danh sách các tệp PDF
    private OcrPairedAdapter ocrAdapter; // Adapter cho danh sách các cặp tệp OCR (ảnh và văn bản)
    private List<File> pdfFiles; // Danh sách các tệp PDF
    private List<OcrPairedItem> ocrPairedItems; // Danh sách các đối tượng ghép đôi OCR

    // ExecutorService để thực hiện các tác vụ tải tệp trong luồng nền
    private ExecutorService fileLoadingExecutor;
    private BottomNavigationView bottomNavigationiew;

    /**
     * Phương thức được gọi khi Activity lần đầu tiên được tạo.
     * Thiết lập layout, ánh xạ các View, khởi tạo Adapters và các danh sách dữ liệu,
     * thiết lập listener cho tab và nút FAB, và kiểm tra/yêu cầu quyền cần thiết.
     * @param savedInstanceState Đối tượng Bundle chứa trạng thái Activity trước đó nếu có.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        // Ánh xạ các View từ layout XML
        recyclerView = findViewById(R.id.recyclerView);
        // Thiết lập LayoutManager cho RecyclerView để hiển thị danh sách dạng tuyến tính
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        tabLayout = findViewById(R.id.tabLayout);
        fabScan = findViewById(R.id.btnStartCamera);
        btnPdfAndOcr = findViewById(R.id.btnPdfAndOcr);

        // Khởi tạo ExecutorService với một luồng duy nhất để tải tệp trong nền
        fileLoadingExecutor = Executors.newSingleThreadExecutor();

        // Khởi tạo các danh sách dữ liệu và Adapters (ban đầu có thể là rỗng)
        pdfFiles = new ArrayList<>();
        ocrPairedItems = new ArrayList<>();
        pdfAdapter = new PdfFileAdapter(this, pdfFiles);
        ocrAdapter = new OcrPairedAdapter(this, ocrPairedItems);
        originalPdfFiles = new ArrayList<>(); // Khởi tạo danh sách gốc
        originalOcrPairedItems = new ArrayList<>(); // Khởi tạo danh sách gốc

        //Tìm kiếm
        etSearch = findViewById(R.id.etSearch);
        //Camera
        //Nav

        bottomNavigationiew = findViewById(R.id.bottom_navigation);

        // Thiết lập listener cho sự kiện chọn tab
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            /**
             * Được gọi khi một tab được chọn.
             * Cập nhật Adapter của RecyclerView và tải lại dữ liệu tương ứng với tab đã chọn.
             * @param tab Tab vừa được chọn.
             */
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                //Xóa văn bản khi chuyển tap
                etSearch.setText("");
                if (tab.getText() != null) {
                    // Kiểm tra tên tab để xác định loại dữ liệu cần hiển thị
                    if (tab.getText().equals(getString(R.string.PDF_recent))) { // So sánh với tên tab trong XML
                        recyclerView.setAdapter(pdfAdapter);
                        loadPdfFiles(); // Tải lại danh sách PDF
                    } else if (tab.getText().equals(getString(R.string.OCR_recent))) { // So sánh với tên tab trong XML
                        recyclerView.setAdapter(ocrAdapter);
                        loadOcrPairedItems(); // Tải lại danh sách các cặp OCR
                    }
                }
            }

            /**
             * Được gọi khi một tab không còn được chọn.
             * @param tab Tab vừa bị bỏ chọn.
             */
            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                // Không cần làm gì khi tab không được chọn trong trường hợp này
            }

            /**
             * Được gọi khi một tab đã được chọn trước đó lại được chọn lại (ví dụ: người dùng nhấp vào tab hiện tại).
             * @param tab Tab vừa được chọn lại.
             */
            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                // Tải lại dữ liệu để làm mới danh sách khi tab được chọn lại
                etSearch.setText("");

                if (tab.getText() != null) {
                    if (tab.getText().equals(getString(R.string.PDF_recent))) {
                        loadPdfFiles();
                    } else if (tab.getText().equals(getString(R.string.OCR_recent))) {
                        loadOcrPairedItems();
                    }
                }
            }
        });

        // Chọn tab đầu tiên (PDF) làm mặc định khi khởi động Activity
        tabLayout.selectTab(tabLayout.getTabAt(0));
        // Đặt Adapter PDF làm mặc định cho RecyclerView
        recyclerView.setAdapter(pdfAdapter);

        // Thiết lập sự kiện click cho nút Floating Action Button (FAB)
        fabScan.setOnClickListener(v -> {
            // Kiểm tra quyền Camera trước khi mở CameraActivity
            if (checkCameraPermission()) {
                Intent intent = new Intent(MainActivity.this, CameraActivity.class);
                startActivity(intent);
            } else {
                requestCameraPermission(); // Yêu cầu quyền nếu chưa được cấp
            }
        });

        btnPdfAndOcr.setOnClickListener(v -> {
            // Kiểm tra quyền Camera trước khi mở CameraActivity
            if (checkCameraPermission()) {
                Intent intent = new Intent(MainActivity.this, CameraActivity.class);
                startActivity(intent);
            } else {
                requestCameraPermission(); // Yêu cầu quyền nếu chưa được cấp
            }
        });

        //Thiết lập sự kiện cho search
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filters(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });

        bottomNavigationiew.setOnItemSelectedListener(new NavigationBarView.OnItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                int itemID = item.getItemId();
                if (itemID == R.id.nav_home) {
                    return true;
                } else if (itemID == R.id.nav_tools) {
                    Toast.makeText(MainActivity.this, getString(R.string.tools), Toast.LENGTH_SHORT).show();
                    return true;
                } else if (itemID == R.id.nav_me) {
                    Intent intent = new Intent(MainActivity.this, MeActivity.class);
                    startActivity(intent);
                    finish();
                    return true;
                }
                return false;
            }
        });
        // Kiểm tra và yêu cầu quyền lưu trữ khi Activity bắt đầu
        checkAndRequestStoragePermission();
    }

    /**
     * Phương thức lọc danh sách dựa trên văn bản tìm kiếm
     *
     * @param text văn bản người dùng nhập vào để tìm kiếm
     */
    private void filters(String text) {
        //Kiểm tra tab
        if (tabLayout.getSelectedTabPosition() == 0) {
            List<File> filteredList = new ArrayList<>();
            if (text.isEmpty()) {
                filteredList.addAll(originalPdfFiles);
            } else {
                String lowerCaseText = text.toLowerCase(Locale.getDefault());
                for (File file : originalPdfFiles) {
                    if (file.getName().toLowerCase(Locale.getDefault()).contains(lowerCaseText)) {
                        filteredList.add(file);
                    }
                }
            }
            pdfFiles.clear();
            pdfFiles.addAll(filteredList);
            pdfAdapter.notifyDataSetChanged();
        } else if (tabLayout.getSelectedTabPosition() == 1) {
            List<OcrPairedItem> filteredList = new ArrayList<>();
            if (text.isEmpty()) {
                filteredList.addAll(originalOcrPairedItems);
            } else {
                String lowerCaseText = text.toLowerCase(Locale.getDefault());
                for (OcrPairedItem item : originalOcrPairedItems) {
                    if (item.getImageFile().getName().toLowerCase(Locale.getDefault()).contains(lowerCaseText) ||
                            item.getTextFile().getName().toLowerCase(Locale.getDefault()).contains(lowerCaseText)) {
                        filteredList.add(item);
                    }
                }
            }
            ocrPairedItems.clear();
            ocrPairedItems.addAll(filteredList);
            ocrAdapter.notifyDataSetChanged();

        }
    }

    /**
     * Phương thức được gọi khi Activity trở lại foreground (tiếp tục).
     * Tải lại dữ liệu cho tab hiện tại để đảm bảo danh sách được cập nhật (ví dụ: sau khi quay lại từ CameraActivity).
     */
    @Override
    protected void onResume() {
        super.onResume();
        etSearch.setText("");
        // Kiểm tra tab hiện tại và tải lại dữ liệu tương ứng
        if (tabLayout.getSelectedTabPosition() == 0) { // Tab PDF
            loadPdfFiles();
        } else if (tabLayout.getSelectedTabPosition() == 1) { // Tab OCR
            loadOcrPairedItems();
        }
        bottomNavigationiew.setSelectedItemId(R.id.nav_home);
    }

    /**
     * Kiểm tra xem quyền truy cập Camera đã được cấp hay chưa.
     *
     * @return true nếu quyền đã được cấp, false nếu chưa.
     */
    private boolean checkCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Yêu cầu quyền truy cập Camera từ người dùng.
     */
    private void requestCameraPermission() {
        // Yêu cầu quyền CAMERA. Kết quả sẽ được trả về trong onRequestPermissionsResult.
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
    }
    /**
     * Cập nhật file trong danh sách gốc khi có thay đổi tên
     * @param oldFile File cũ
     * @param newFile File mới sau khi đổi tên
     */
    public void updateFileInOriginalList(File oldFile, File newFile) {
        // Cập nhật trong danh sách PDF gốc
        for (int i = 0; i < originalPdfFiles.size(); i++) {
            if (originalPdfFiles.get(i).getAbsolutePath().equals(oldFile.getAbsolutePath())) {
                originalPdfFiles.set(i, newFile);
                break;
            }
        }
    }

    /**
     * Cập nhật item OCR trong danh sách gốc khi có thay đổi tên
     * @param oldItem Item OCR cũ
     * @param newItem Item OCR mới sau khi đổi tên
     */
    public void updateOcrItemInOriginalList(OcrPairedItem oldItem, OcrPairedItem newItem) {
        // Cập nhật trong danh sách OCR gốc
        for (int i = 0; i < originalOcrPairedItems.size(); i++) {
            OcrPairedItem item = originalOcrPairedItems.get(i);
            if (item.equals(oldItem)) {
                originalOcrPairedItems.set(i, newItem);
                break;
            }
        }

        // Nếu không tìm thấy bằng equals, tìm bằng đường dẫn file (fallback)
        boolean found = false;
        for (int i = 0; i < originalOcrPairedItems.size() && !found; i++) {
            OcrPairedItem item = originalOcrPairedItems.get(i);
            if (item.getImageFile() != null && oldItem.getImageFile() != null &&
                    item.getTextFile() != null && oldItem.getTextFile() != null &&
                    item.getImageFile().getAbsolutePath().equals(oldItem.getImageFile().getAbsolutePath()) &&
                    item.getTextFile().getAbsolutePath().equals(oldItem.getTextFile().getAbsolutePath())) {
                originalOcrPairedItems.set(i, newItem);
                found = true;
            }
        }
    }

    /**
     * Kiểm tra và yêu cầu quyền ghi bộ nhớ ngoài.
     * Quyền WRITE_EXTERNAL_STORAGE chỉ cần thiết cho các phiên bản Android dưới API 29 (Android Q)
     * khi truy cập các thư mục công cộng. Đối với Android Q trở lên,
     * ứng dụng sử dụng Scoped Storage và MediaStore API không yêu cầu quyền này cho việc ghi.
     * Tuy nhiên, quyền đọc vẫn có thể cần thiết tùy thuộc vào cách truy cập.
     */
    private void checkAndRequestStoragePermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            // Đối với Android < Q, kiểm tra và yêu cầu quyền WRITE_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_STORAGE_PERMISSION);
            } else {
                // Quyền đã được cấp, tải tệp ngay lập tức (cho trường hợp này)
                if (tabLayout.getSelectedTabPosition() == 0) {
                    loadPdfFiles();
                } else if (tabLayout.getSelectedTabPosition() == 1) {
                    loadOcrPairedItems();
                }
            }
        } else {
            // Đối với Android Q trở lên, không cần quyền WRITE_EXTERNAL_STORAGE cho MediaStore.
            // Do đó, ta chỉ cần tải dữ liệu nếu tab đã chọn là 0 hoặc 1.
            if (tabLayout.getSelectedTabPosition() == 0) {
                loadPdfFiles();
            } else if (tabLayout.getSelectedTabPosition() == 1) {
                loadOcrPairedItems();
            }
        }
    }

    /**
     * Phương thức callback được gọi sau khi người dùng phản hồi yêu cầu cấp quyền.
     * Xử lý kết quả của yêu cầu quyền Camera và Lưu trữ.
     *
     * @param requestCode  Mã yêu cầu đã được cung cấp khi gọi requestPermissions.
     * @param permissions  Mảng các quyền được yêu cầu.
     * @param grantResults Kết quả của việc cấp quyền (PERMISSION_GRANTED hoặc PERMISSION_DENIED) tương ứng với mỗi quyền.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Nếu quyền Camera được cấp, mô phỏng lại click nút FAB để mở camera
                fabScan.performClick();
            } else {
                // Thông báo nếu quyền Camera bị từ chối
                Toast.makeText(this, getString(R.string.camera_permission_is_required_to_use_the_scanner), Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQUEST_STORAGE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Nếu quyền lưu trữ được cấp, thông báo và tải lại dữ liệu cho tab hiện tại
                Toast.makeText(this, getString(R.string.storage_permission_granted), Toast.LENGTH_SHORT).show();
                if (tabLayout.getSelectedTabPosition() == 0) {
                    loadPdfFiles();
                } else if (tabLayout.getSelectedTabPosition() == 1) {
                    loadOcrPairedItems();
                }
            } else {
                // Thông báo nếu quyền lưu trữ bị từ chối
                Toast.makeText(this, getString(R.string.storage_permission_granted_dinied), Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * Tải danh sách các tệp PDF từ thư mục "Downloads/MyPDFs".
     * Các tệp được lọc chỉ lấy định dạng .pdf và được sắp xếp theo thời gian sửa đổi gần nhất.
     * Quá trình này chạy trên một luồng nền và cập nhật UI trên luồng chính.
     */
    private void loadPdfFiles() {
        fileLoadingExecutor.execute(() -> { // Chạy tác vụ trên luồng nền
            List<File> loadedFiles = new ArrayList<>();
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File pdfsDir = new File(downloadsDir, "MyPDFImages"); // Đường dẫn đến thư mục PDF

            // Kiểm tra xem thư mục có tồn tại và là một thư mục không
            if (pdfsDir.exists() && pdfsDir.isDirectory()) {
                // Lọc các tệp có đuôi .pdf
                File[] pdfFilesArray = pdfsDir.listFiles((dir, name) -> name.endsWith(".pdf"));
                if (pdfFilesArray != null) {
                    // Thêm các tệp PDF đã tìm thấy vào danh sách
                    Collections.addAll(loadedFiles, pdfFilesArray);
                }

                // Lọc các tệp có đuôi .jpg hoặc .jpeg
                File[] imageFilesArray = pdfsDir.listFiles((dir, name) -> name.endsWith(".jpg") || name.endsWith(".jpeg"));
                if (imageFilesArray != null) {
                    // Thêm các tệp ảnh đã tìm thấy vào danh sách
                    Collections.addAll(loadedFiles, imageFilesArray);
                }
            }

            // Sắp xếp danh sách các tệp PDF theo ngày sửa đổi gần nhất (mới nhất lên đầu)
            Collections.sort(loadedFiles, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));

            runOnUiThread(() -> { // Chạy trên luồng UI chính để cập nhật giao diện
                originalPdfFiles.clear(); // Xóa dữ liệu cũ từ danh sách gốc
                originalPdfFiles.addAll(loadedFiles); // Thêm dữ liệu mới vào danh sách gốc

                pdfFiles.clear(); // Xóa dữ liệu cũ
                pdfFiles.addAll(loadedFiles); // Thêm dữ liệu mới
                pdfAdapter.updateData(pdfFiles); // Cập nhật dữ liệu trong adapter
                // Chỉ thông báo thay đổi dữ liệu nếu adapter hiện tại của RecyclerView là pdfAdapter
                if (recyclerView.getAdapter() == pdfAdapter) {
                    pdfAdapter.notifyDataSetChanged();
                }
            });
        });
    }

    /**
     * Tải và ghép đôi các tệp ảnh và văn bản OCR từ thư mục "Downloads/MyOCRImages" và "Downloads/MyOCRTexts".
     * Các tệp được ghép dựa trên dấu thời gian chung trong tên file.
     * Quá trình này chạy trên một luồng nền và cập nhật UI trên luồng chính.
     */
    private void loadOcrPairedItems() {
        fileLoadingExecutor.execute(() -> { // Chạy tác vụ trên luồng nền
            List<OcrPairedItem> loadedPairedItems = new ArrayList<>();
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File ocrImagesDir = new File(downloadsDir, "MyOCRImages"); // Thư mục chứa ảnh OCR
            File ocrTextsDir = new File(downloadsDir, "MyOCRTexts"); // Thư mục chứa văn bản OCR

            // Đảm bảo các thư mục tồn tại, nếu không thì tạo chúng (để tránh lỗi NullPointerException)
            if (!ocrImagesDir.exists()) {
                ocrImagesDir.mkdirs();
            }
            if (!ocrTextsDir.exists()) {
                ocrTextsDir.mkdirs();
            }

            // Map để lưu trữ các tệp ảnh và văn bản theo dấu thời gian
            Map<String, File> imageMap = new HashMap<>();
            Map<String, File> textMap = new HashMap<>();

            // Thu thập tất cả các tệp ảnh OCR và lưu vào imageMap với dấu thời gian làm khóa
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

            // Thu thập tất cả các tệp văn bản OCR và lưu vào textMap với dấu thời gian làm khóa
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

            // Ghép đôi các tệp ảnh và văn bản dựa trên dấu thời gian chung
            for (Map.Entry<String, File> entry : imageMap.entrySet()) {
                String timestamp = entry.getKey();
                File imageFile = entry.getValue();
                File textFile = textMap.get(timestamp); // Lấy tệp văn bản tương ứng từ textMap

                // Chỉ tạo đối tượng OcrPairedItem nếu cả ảnh và văn bản đều tồn tại
                if (textFile != null) {
                    long totalSize = imageFile.length() + textFile.length(); // Tính tổng kích thước
                    String formattedDate = getFormattedDateFromTimestamp(timestamp); // Định dạng ngày tháng
                    loadedPairedItems.add(new OcrPairedItem(imageFile, textFile, timestamp, formattedDate, totalSize));
                }
            }

            // Sắp xếp danh sách các cặp theo dấu thời gian giảm dần (mới nhất lên đầu)
            Collections.sort(loadedPairedItems, (item1, item2) -> item2.getTimestamp().compareTo(item1.getTimestamp()));

            runOnUiThread(() -> { // Chạy trên luồng UI chính để cập nhật giao diện
                originalOcrPairedItems.clear(); // Xóa dữ liệu cũ từ danh sách gốc
                originalOcrPairedItems.addAll(loadedPairedItems); // Thêm dữ liệu mới vào danh sách gốc

                ocrPairedItems.clear(); // Xóa dữ liệu cũ
                ocrPairedItems.addAll(loadedPairedItems); // Thêm dữ liệu mới
                ocrAdapter.updateData(ocrPairedItems); // Cập nhật dữ liệu trong adapter
                // Chỉ thông báo thay đổi dữ liệu nếu adapter hiện tại của RecyclerView là ocrAdapter
                if (recyclerView.getAdapter() == ocrAdapter) {
                    ocrAdapter.notifyDataSetChanged();
                }
            });
        });
    }

    /**
     * Phương thức trợ giúp để trích xuất dấu thời gian từ tên tệp.
     * Tên tệp dự kiến có định dạng "PREFIX_yyyyMMdd_HHmmss.extension".
     *
     * @param fileName Tên của tệp (ví dụ: "OCR_Image_20240703_103000.jpeg").
     * @param prefix   Tiền tố của tên tệp (ví dụ: "OCR_Image_", "OCR_Text_").
     * @return Chuỗi dấu thời gian (ví dụ: "20240703_103000") hoặc null nếu tên tệp không khớp định dạng.
     */
    private String extractTimestamp(String fileName, String prefix) {
        if (fileName.startsWith(prefix) && fileName.contains("_") && fileName.contains(".")) {
            int startIndex = prefix.length(); // Vị trí bắt đầu của dấu thời gian
            int endIndex = fileName.lastIndexOf("."); // Vị trí kết thúc (trước phần mở rộng)
            if (endIndex > startIndex) {
                return fileName.substring(startIndex, endIndex); // Trích xuất chuỗi dấu thời gian
            }
        }
        return null; // Trả về null nếu không khớp định dạng
    }

    /**
     * Phương thức trợ giúp để định dạng chuỗi dấu thời gian thành định dạng ngày giờ dễ đọc hơn.
     *
     * @param timestamp Chuỗi dấu thời gian có định dạng "yyyyMMdd_HHmmss" (ví dụ: "20240703_103000").
     * @return Chuỗi ngày giờ đã định dạng (ví dụ: "03/07/2024 10:30") hoặc "Ngày không xác định" nếu có lỗi phân tích.
     */
    private String getFormattedDateFromTimestamp(String timestamp) {
        try {
            // Định dạng đầu vào của dấu thời gian
            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
            Date date = inputFormat.parse(timestamp); // Chuyển chuỗi thành đối tượng Date
            // Định dạng đầu ra mong muốn
            SimpleDateFormat outputFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
            return outputFormat.format(date); // Định dạng lại Date thành chuỗi
        } catch (ParseException e) {
            // Ghi log lỗi nếu không thể phân tích dấu thời gian
            Log.e(TAG, "Lỗi phân tích dấu thời gian: " + timestamp, e);
            return "Ngày không xác định"; // Trả về chuỗi lỗi
        }
    }

    /**
     * Cập nhật danh sách PDF gốc sau khi xóa file
     * @param deletedFile File PDF đã bị xóa
     */
    public void updateOriginalPdfList(File deletedFile) {
        if (originalPdfFiles != null) {
            originalPdfFiles.removeIf(file -> file.getAbsolutePath().equals(deletedFile.getAbsolutePath()));
        }
    }

    /**
     * Cập nhật danh sách OCR gốc sau khi xóa cặp OCR
     * @param deletedItem Cặp OCR đã bị xóa
     */
    public void updateOriginalOcrList(OcrPairedItem deletedItem) {
        if (originalOcrPairedItems != null) {
            originalOcrPairedItems.removeIf(item ->
                    item.getTimestamp().equals(deletedItem.getTimestamp()));
        }
    }

    /**
     * Phương thức được gọi khi Activity bị hủy (ví dụ: khi người dùng thoát ứng dụng).
     * Đảm bảo rằng ExecutorService được tắt để giải phóng tài nguyên hệ thống
     * và ngăn chặn rò rỉ bộ nhớ.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Kiểm tra nếu ExecutorService không rỗng và chưa tắt
        if (fileLoadingExecutor != null && !fileLoadingExecutor.isShutdown()) {
            fileLoadingExecutor.shutdownNow(); // Tắt ngay lập tức tất cả các tác vụ đang chờ
        }
    }
}