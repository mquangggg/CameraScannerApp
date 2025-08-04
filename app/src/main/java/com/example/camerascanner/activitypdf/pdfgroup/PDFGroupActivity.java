package com.example.camerascanner.activitypdf.pdfgroup;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.camerascanner.R;
import com.example.camerascanner.activitycamera.CameraActivity;
import com.example.camerascanner.activitycamera.ImagePreviewActivity;
import com.example.camerascanner.activitymain.MainActivity;
import com.example.camerascanner.activitypdf.DialogHelper;
import com.example.camerascanner.activitypdf.PermissionHelper;
import com.example.camerascanner.activitypdf.pdf.PdfGenerator;
import com.example.camerascanner.activitypdf.pdf.PdfStyle;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Activity quản lý group ảnh và tạo PDF multi-page
 */
public class PDFGroupActivity extends AppCompatActivity implements
        ImageGroupAdapter.OnImageActionListener {

    private static final String TAG = "PDFGroupActivity";
    private static final int REQUEST_ADD_IMAGE = 1001;
    private static final int REQUEST_IMAGE_PREVIEW = 1004;


    // UI Components
    private TextView tvTitle;
    private Button btnAddMore;
    private RecyclerView recyclerViewImages;
    private View layoutEmptyState;
    private Button btnCancel;
    private ImageButton btnXepHang;
    private boolean isGridView = true;
    private Button btnConfirm;

    // Data
    private List<ImageItem> imageList;
    private ImageGroupAdapter adapter;
    private PdfStyle currentPdfStyle;
    private Uri originalImageUri; // Uri của ảnh gốc được truyền vào
    private Uri tempProcessedImageUri; // Uri của ảnh đã xử lý được lưu tạm thời

    // Helper classes
    private PdfGenerator pdfGenerator;

    // Background processing
    private ExecutorService executorService;
    private Handler mainHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_after_pdf_generation);

        // Khởi tạo các thành phần UI (không phụ thuộc vào trạng thái đã lưu)
        tvTitle = findViewById(R.id.tvTitle);
        btnAddMore = findViewById(R.id.btnAddMore);
        recyclerViewImages = findViewById(R.id.recyclerViewImages);
        layoutEmptyState = findViewById(R.id.layoutEmptyState);
        btnCancel = findViewById(R.id.btnCancel);
        btnConfirm = findViewById(R.id.btnConfirm);
        btnXepHang = findViewById(R.id.btnXepHang);

        // Khởi tạo các helper
        pdfGenerator = new PdfGenerator(this);
        executorService = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());

        if (savedInstanceState != null) {
            // Activity được khôi phục từ trạng thái đã lưu
            imageList = new ArrayList<>(); // Khởi tạo danh sách mới
            ArrayList<String> savedUriStrings = savedInstanceState.getStringArrayList("savedImageUris");

            if (savedUriStrings != null && !savedUriStrings.isEmpty()) {
                Toast.makeText(this, "Đang tải lại ảnh...", Toast.LENGTH_SHORT).show();
                setUIEnabled(false);

                executorService.execute(() -> {
                    List<ImageItem> loadedImages = new ArrayList<>();
                    for (String uriString : savedUriStrings) {
                        try {
                            Uri imageUri = Uri.parse(uriString);
                            Bitmap bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), imageUri);
                            if (bitmap != null) {
                                String imageName = "Ảnh " + (loadedImages.size() + 1);
                                loadedImages.add(new ImageItem(bitmap, imageName, uriString));
                            } else {
                                Log.e(TAG, "Không thể tải bitmap từ Uri đã lưu: " + uriString);
                            }
                        } catch (IOException e) {
                            Log.e(TAG, "Lỗi I/O khi tải bitmap từ Uri đã lưu: " + uriString + " - " + e.getMessage(), e);
                        } catch (Exception e) {
                            Log.e(TAG, "Lỗi không xác định khi tải bitmap từ Uri đã lưu: " + uriString + " - " + e.getMessage(), e);
                        }
                    }

                    mainHandler.post(() -> {
                        imageList.addAll(loadedImages);
                        setupRecyclerView();
                        setupClickListeners();
                        updateUI();
                        setUIEnabled(true);
                        Toast.makeText(this, "Đã khôi phục " + imageList.size() + " ảnh.", Toast.LENGTH_SHORT).show();
                        Log.d(TAG, "onCreate: Restored " + imageList.size() + " images from savedInstanceState.");
                    });
                });
            } else {
                // savedInstanceState không null nhưng không có ảnh để khôi phục (ví dụ: danh sách rỗng đã được lưu)
                imageList = new ArrayList<>();
                Log.d(TAG, "onCreate: Restored state, but no images to restore or savedImageUris is null/empty.");
                setupRecyclerView();
                setupClickListeners();
                updateUI();
            }

            // Khôi phục style từ savedInstanceState
            String styleStr = savedInstanceState.getString("currentPdfStyle");
            if (styleStr != null) {
                try {
                    currentPdfStyle = PdfStyle.valueOf(styleStr);
                } catch (IllegalArgumentException e) {
                    currentPdfStyle = PdfStyle.ORIGINAL;
                }
            } else {
                currentPdfStyle = PdfStyle.ORIGINAL;
            }

        } else {
            // Activity được tạo mới lần đầu (savedInstanceState là null)
            // Khởi tạo danh sách rỗng và CHỈ XỬ LÝ intent ban đầu
            imageList = new ArrayList<>();
            // Vẫn gọi processInitialIntentData() ở đây, vì đây là lần đầu tiên Activity được tạo
            // và nó cần xử lý Intent chứa ảnh đầu tiên.
            processInitialIntentData();
            Log.d(TAG, "onCreate: First time creation, processed initial intent data.");

            // Các thiết lập RecyclerView và listeners nên được gọi sau khi imageList có thể đã được điền
            setupRecyclerView();
            setupClickListeners();
            // Cập nhật UI ban đầu (sẽ hiển thị ảnh sau khi tải xong trong processInitialIntentData)
            updateUI();
        }
        // --- KẾT THÚC PHẦN CHỈNH SỬA QUAN TRỌNG ---
    }


    /**
     * Xử lý dữ liệu từ Intent khi Activity được tạo lần đầu
     * (Đã cập nhật để nhận Uri và truyền vào ImageItem, tải bitmap ở luồng nền)
     */
    private void processInitialIntentData() {
        Intent intent = getIntent();
        if (intent != null) {
            String processedImageUriString = intent.getStringExtra("processedImageUri");
            if (processedImageUriString != null && imageList.isEmpty()) {
                tempProcessedImageUri = Uri.parse(processedImageUriString);

                // Hiển thị một thông báo hoặc loading spinner nếu cần
                Toast.makeText(this, "Đang tải ảnh...", Toast.LENGTH_SHORT).show();
                setUIEnabled(false); // Tạm thời tắt UI để tránh tương tác

                executorService.execute(() -> {
                    Bitmap bitmap = null;
                    try {
                        bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), tempProcessedImageUri);
                    } catch (IOException e) {
                        Log.e(TAG, "Lỗi I/O khi tải bitmap từ Uri: " + e.getMessage(), e);
                    } catch (Exception e) {
                        Log.e(TAG, "Lỗi không xác định khi tải bitmap từ Uri: " + e.getMessage(), e);
                    }

                    final Bitmap finalBitmap = bitmap; // Cần final để sử dụng trong lambda
                    mainHandler.post(() -> {
                        setUIEnabled(true); // Bật lại UI

                        if (finalBitmap != null) {
                            // Truyền Uri dưới dạng String vào filePath của ImageItem
                            ImageItem firstImage = new ImageItem(finalBitmap, "Ảnh 1", tempProcessedImageUri.toString());
                            imageList.add(firstImage);
                            updateUI(); // Cập nhật UI sau khi thêm ảnh
                            Toast.makeText(this, "Đã thêm ảnh đầu tiên.", Toast.LENGTH_SHORT).show();
                            Log.d(TAG, "Added initial image from Intent. Total images: " + imageList.size());
                        } else {
                            Log.e(TAG, "Không thể tải ảnh đã xử lý từ Uri: " + tempProcessedImageUri.toString());
                            Toast.makeText(this, "Không thể tải ảnh đã xử lý.", Toast.LENGTH_SHORT).show();
                        }
                    });
                });
            } else {
                Log.w(TAG, "Không tìm thấy 'processedImageUri' trong Intent.");
            }

            // Nhận thông tin style (giữ nguyên)
            String styleStr = intent.getStringExtra("pdfStyle");
            if (styleStr != null) {
                try {
                    currentPdfStyle = PdfStyle.valueOf(styleStr);
                } catch (IllegalArgumentException e) {
                    Log.e(TAG, "PdfStyle không hợp lệ: " + styleStr + ". Sử dụng ORIGINAL.", e);
                    currentPdfStyle = PdfStyle.ORIGINAL;
                }
            } else {
                Log.w(TAG, "Không tìm thấy 'pdfStyle' trong Intent. Sử dụng ORIGINAL.");
                currentPdfStyle = PdfStyle.ORIGINAL;
            }

            // Nhận URI ảnh gốc (giữ nguyên)
            String originalUriStr = intent.getStringExtra("originalImageUri");
            if (originalUriStr != null) {
                originalImageUri = Uri.parse(originalUriStr);
            } else {
                Log.w(TAG, "Không tìm thấy 'originalImageUri' trong Intent.");
            }
        }
    }
    /**
     * Thiết lập RecyclerView
     */
    private void setupRecyclerView() {
        if (recyclerViewImages != null) {
            GridLayoutManager layoutManager = new GridLayoutManager(this, 2);
            recyclerViewImages.setLayoutManager(layoutManager);

            adapter = new ImageGroupAdapter(this, imageList, this);
            recyclerViewImages.setAdapter(adapter);
        }
    }

    /**
     * Thiết lập click listener cho các nút
     */
    private void setupClickListeners() {
        if (btnAddMore != null) {
            btnAddMore.setOnClickListener(v -> handleAddMoreImages());
        }

        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> handleCancel());
        }

        if (btnConfirm != null) {
            btnConfirm.setOnClickListener(v -> handleCreatePDF());
        }
        if (btnXepHang != null) {
            Log.d(TAG, "btnXepHang found and setting click listener");
            btnXepHang.setOnClickListener(v -> {
                Log.d(TAG, "btnXepHang clicked!");
                toggleLayoutView();
            });
        } else {
            Log.e(TAG, "btnXepHang is null!");
        }
    }
    /**
     * Chuyển đổi giữa grid view (2 cột) và list view (1 cột)
     */
    private void toggleLayoutView() {
        Log.d(TAG, "toggleLayoutView() called");
        Log.d(TAG, "Current isGridView: " + isGridView);
        Log.d(TAG, "recyclerViewImages: " + (recyclerViewImages != null ? "exists" : "null"));

        if (recyclerViewImages != null) {
            RecyclerView.LayoutManager currentLayoutManager = recyclerViewImages.getLayoutManager();
            Log.d(TAG, "LayoutManager: " + (currentLayoutManager != null ? currentLayoutManager.getClass().getSimpleName() : "null"));

            if (currentLayoutManager instanceof GridLayoutManager) {
                GridLayoutManager layoutManager = (GridLayoutManager) currentLayoutManager;
                int currentSpanCount = layoutManager.getSpanCount();
                Log.d(TAG, "Current span count: " + currentSpanCount);

                if (isGridView) {
                    // Chuyển sang list view (1 cột)
                    layoutManager.setSpanCount(1);
                    isGridView = false;
                    Log.d(TAG, "Changed to LIST view (1 column)");
                    Toast.makeText(this, "Chuyển sang chế độ danh sách", Toast.LENGTH_SHORT).show();
                } else {
                    // Chuyển sang grid view (2 cột)
                    layoutManager.setSpanCount(2);
                    isGridView = true;
                    Log.d(TAG, "Changed to GRID view (2 columns)");
                    Toast.makeText(this, "Chuyển sang chế độ lưới", Toast.LENGTH_SHORT).show();
                }

                // Notify adapter để cập nhật layout
                if (adapter != null) {
                    adapter.notifyDataSetChanged();
                    Log.d(TAG, "Adapter notified");
                } else {
                    Log.e(TAG, "Adapter is null!");
                }
            } else {
                Log.e(TAG, "LayoutManager is not GridLayoutManager!");
            }
        } else {
            Log.e(TAG, "recyclerViewImages is null!");
        }
    }
    // Cũng cần cập nhật updateUI() để đảm bảo adapter được notify đúng cách
    // Cũng cập nhật updateUI() với logging chi tiết
    private void updateUI() {
        Log.d(TAG, "=== updateUI() START ===");
        Log.d(TAG, "imageList size: " + (imageList != null ? imageList.size() : "null"));
        Log.d(TAG, "adapter: " + (adapter != null ? "exists" : "null"));
        Log.d(TAG, "recyclerView: " + (recyclerViewImages != null ? "exists" : "null"));

        // Cập nhật title
        if (tvTitle != null) {
            String title = "Ảnh đã chụp (" + (imageList != null ? imageList.size() : 0) + ")";
            tvTitle.setText(title);
            Log.d(TAG, "Updated title: " + title);
        }

        // Hiển thị/ẩn empty state
        if (layoutEmptyState != null && recyclerViewImages != null) {
            boolean isEmpty = imageList == null || imageList.isEmpty();
            layoutEmptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
            recyclerViewImages.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
            Log.d(TAG, "Empty state: " + (isEmpty ? "VISIBLE" : "GONE"));
            Log.d(TAG, "RecyclerView: " + (isEmpty ? "GONE" : "VISIBLE"));
        }

        // Enable/disable nút confirm
        if (btnConfirm != null) {
            boolean hasImages = imageList != null && !imageList.isEmpty();
            btnConfirm.setEnabled(hasImages);
            btnConfirm.setAlpha(hasImages ? 1.0f : 0.5f);
            Log.d(TAG, "Confirm button enabled: " + hasImages);
        }

        // CẬP NHẬT ADAPTER
        if (adapter != null) {
            Log.d(TAG, "Calling adapter.notifyDataSetChanged()");
            adapter.notifyDataSetChanged();

            // Verify adapter item count
            Log.d(TAG, "Adapter item count: " + adapter.getItemCount());

            // Scroll to last item nếu có ảnh mới
            if (imageList != null && imageList.size() > 1) {
                recyclerViewImages.scrollToPosition(imageList.size() - 1);
                Log.d(TAG, "Scrolled to position: " + (imageList.size() - 1));
            }
        } else {
            Log.e(TAG, "Adapter is null in updateUI()!");
        }

        Log.d(TAG, "=== updateUI() END ===");
    }


    /**
     * Xử lý thêm ảnh mới
     */
    private void handleAddMoreImages() {
        Intent intent = new Intent(PDFGroupActivity.this, CameraActivity.class);
        intent.putExtra("FROM_PDF_GROUP", true);
        startActivityForResult(intent, REQUEST_ADD_IMAGE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        Log.d(TAG, "=== onActivityResult START ===");
        Log.d(TAG, "requestCode=" + requestCode + ", resultCode=" + resultCode);

        if (requestCode == REQUEST_ADD_IMAGE) {
            if (resultCode == RESULT_OK && data != null) {
                String processedImageUriString = data.getStringExtra("processedImageUri");
                Log.d(TAG, "Received processedImageUri from Camera->Crop flow: " + processedImageUriString);

                if (processedImageUriString != null) {
                    Uri newImageUri = Uri.parse(processedImageUriString);

                    Toast.makeText(this, "Đang thêm ảnh mới...", Toast.LENGTH_SHORT).show();
                    setUIEnabled(false);

                    executorService.execute(() -> {
                        Bitmap newBitmap = null;
                        try {
                            newBitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), newImageUri);

                            if (newBitmap != null) {
                                String imageName = "Ảnh " + (imageList.size() + 1);
                                String uniqueFilePath = newImageUri.toString() + "_" + System.currentTimeMillis();

                                final Bitmap finalNewBitmap = newBitmap;

                                mainHandler.post(() -> {
                                    try {
                                        ImageItem newImageItem = new ImageItem(finalNewBitmap, imageName, uniqueFilePath);

                                        if (adapter != null) {
                                            adapter.addImage(newImageItem);
                                        } else {
                                            imageList.add(newImageItem);
                                        }

                                        updateUIComponents();
                                        setUIEnabled(true);
                                        Toast.makeText(this, "Đã thêm ảnh mới thành công!", Toast.LENGTH_SHORT).show();
                                        Log.d(TAG, "Successfully added new image. Total images: " + imageList.size());
                                    } catch (Exception e) {
                                        Log.e(TAG, "ERROR in UI thread: " + e.getMessage(), e);
                                        Toast.makeText(this, "Lỗi khi thêm ảnh vào danh sách", Toast.LENGTH_SHORT).show();
                                        setUIEnabled(true);
                                    }
                                });

                            } else {
                                mainHandler.post(() -> {
                                    Log.e(TAG, "BITMAP IS NULL from Uri: " + newImageUri.toString());
                                    Toast.makeText(this, "Không thể tải ảnh mới.", Toast.LENGTH_SHORT).show();
                                    setUIEnabled(true);
                                });
                            }

                        } catch (Exception e) {
                            Log.e(TAG, "ERROR loading bitmap: " + e.getMessage(), e);
                            mainHandler.post(() -> {
                                Toast.makeText(this, "Lỗi khi tải ảnh mới: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                setUIEnabled(true);
                            });
                        }
                    });

                } else {
                    Log.w(TAG, "processedImageUri is null in result data");
                    Toast.makeText(this, "Không nhận được dữ liệu ảnh.", Toast.LENGTH_SHORT).show();
                }
            } else {
                Log.d(TAG, "Camera activity result cancelled or no data");
                Toast.makeText(this, "Chưa có ảnh nào được thêm.", Toast.LENGTH_SHORT).show();
            }
        }
        // XỬ LÝ CHO REQUEST_IMAGE_PREVIEW
        else if (requestCode == REQUEST_IMAGE_PREVIEW) {
            if (resultCode == RESULT_OK && data != null) {
                String updatedImageUriString = data.getStringExtra("updatedImageUri");
                int imagePosition = data.getIntExtra("imagePosition", -1);

                Log.d(TAG, "Received updated image from preview - URI: " + updatedImageUriString + ", position: " + imagePosition);

                if (updatedImageUriString != null && imagePosition >= 0 && imagePosition < imageList.size()) {
                    Uri updatedImageUri = Uri.parse(updatedImageUriString);

                    Toast.makeText(this, "Đang cập nhật ảnh...", Toast.LENGTH_SHORT).show();
                    setUIEnabled(false);

                    executorService.execute(() -> {
                        try {
                            Bitmap updatedBitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), updatedImageUri);

                            if (updatedBitmap != null) {
                                mainHandler.post(() -> {
                                    try {
                                        ImageItem currentItem = imageList.get(imagePosition);

                                        if (currentItem.getBitmap() != null && !currentItem.getBitmap().isRecycled()) {
                                            currentItem.getBitmap().recycle();
                                        }

                                        currentItem.setBitmap(updatedBitmap);
                                        currentItem.setFilePath(updatedImageUri.toString());

                                        if (adapter != null) {
                                            adapter.notifyItemChanged(imagePosition);
                                        }

                                        setUIEnabled(true);
                                        Toast.makeText(this, "Đã cập nhật ảnh thành công!", Toast.LENGTH_SHORT).show();

                                    } catch (Exception e) {
                                        Log.e(TAG, "Error updating image item: " + e.getMessage(), e);
                                        Toast.makeText(this, "Lỗi khi cập nhật ảnh", Toast.LENGTH_SHORT).show();
                                        setUIEnabled(true);
                                    }
                                });
                            } else {
                                mainHandler.post(() -> {
                                    Toast.makeText(this, "Không thể tải ảnh đã cập nhật", Toast.LENGTH_SHORT).show();
                                    setUIEnabled(true);
                                });
                            }

                        } catch (Exception e) {
                            Log.e(TAG, "Error loading updated bitmap: " + e.getMessage(), e);
                            mainHandler.post(() -> {
                                Toast.makeText(this, "Lỗi khi tải ảnh đã cập nhật: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                setUIEnabled(true);
                            });
                        }
                    });
                } else {
                    Log.w(TAG, "Invalid updated image data or position");
                    Toast.makeText(this, "Dữ liệu ảnh cập nhật không hợp lệ", Toast.LENGTH_SHORT).show();
                }
            } else {
                Log.d(TAG, "Image preview cancelled or no changes");
            }
        }

        Log.d(TAG, "=== onActivityResult END ===");
    }
    private void updateUIComponents() {
        Log.d(TAG, "=== updateUIComponents() START ===");

        // Cập nhật title
        if (tvTitle != null) {
            String title = "Ảnh đã chụp (" + (imageList != null ? imageList.size() : 0) + ")";
            tvTitle.setText(title);
            Log.d(TAG, "Updated title: " + title);
        }

        // Hiển thị/ẩn empty state
        if (layoutEmptyState != null && recyclerViewImages != null) {
            boolean isEmpty = imageList == null || imageList.isEmpty();
            layoutEmptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
            recyclerViewImages.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
            Log.d(TAG, "Empty state: " + (isEmpty ? "VISIBLE" : "GONE"));
            Log.d(TAG, "RecyclerView: " + (isEmpty ? "GONE" : "VISIBLE"));
        }

        // Enable/disable nút confirm
        if (btnConfirm != null) {
            boolean hasImages = imageList != null && !imageList.isEmpty();
            btnConfirm.setEnabled(hasImages);
            btnConfirm.setAlpha(hasImages ? 1.0f : 0.5f);
            Log.d(TAG, "Confirm button enabled: " + hasImages);
        }

        // Cuộn đến ảnh mới
        if (imageList != null && imageList.size() > 1) {
            recyclerViewImages.scrollToPosition(imageList.size() - 1);
            Log.d(TAG, "Scrolled to position: " + (imageList.size() - 1));
        }

        Log.d(TAG, "=== updateUIComponents() END ===");
    }

    /**
     * LƯU TRẠNG THÁI: Lưu danh sách chuỗi Uri của ảnh khi activity bị destroy/recreate
     */
    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        ArrayList<String> imageUriStrings = new ArrayList<>();
        for (ImageItem item : imageList) {
            // Lưu filePath (chính là chuỗi Uri)
            if (item.getFilePath() != null) {
                imageUriStrings.add(item.getFilePath());
            }
        }
        outState.putStringArrayList("savedImageUris", imageUriStrings); // Lưu danh sách Uri dưới dạng String
        outState.putInt("imageListSize", imageList.size()); // Giữ lại để log hoặc kiểm tra

        if (currentPdfStyle != null) {
            outState.putString("currentPdfStyle", currentPdfStyle.name());
        }

        Log.d(TAG, "onSaveInstanceState: Saved " + imageUriStrings.size() + " image URIs.");
    }

    /**
     * Set UI enabled/disabled
     */
    private void setUIEnabled(boolean enabled) {
        if (btnAddMore != null) btnAddMore.setEnabled(enabled);
        if (btnCancel != null) btnCancel.setEnabled(enabled);
        if (btnConfirm != null) btnConfirm.setEnabled(enabled);
        if (recyclerViewImages != null) recyclerViewImages.setEnabled(enabled);
        // Có thể thêm các UI element khác cần disable/enable
    }

    /**
     * Xử lý hủy
     */
    private void handleCancel() {
        finish();
    }

    /**
     * Xử lý tạo PDF từ group ảnh
     */
    private void handleCreatePDF() {
        if (imageList.isEmpty()) {
            Toast.makeText(this, "Không có ảnh để tạo PDF", Toast.LENGTH_SHORT).show();
            return;
        }

        DialogHelper.showPdfFileNameDialog(this, this::createPDFWithFileName);
    }

    /**
     * Tạo PDF với tên file được chỉ định
     */
    private void createPDFWithFileName(String fileName) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q && !PermissionHelper.hasStoragePermission(this)) {
            PermissionHelper.requestStoragePermission(this);
            return;
        }
        performCreatePDF(fileName);
    }

    /**
     * Thực hiện tạo PDF từ multiple images
     */
    private void performCreatePDF(String fileName) {
        Toast.makeText(this, "Đang tạo PDF từ " + imageList.size() + " ảnh...", Toast.LENGTH_SHORT).show();

        setUIEnabled(false);

        executorService.execute(() -> {
            try {
                List<Bitmap> bitmaps = new ArrayList<>();
                for (ImageItem item : imageList) {
                    // Đảm bảo bitmap hợp lệ và chưa được recycle
                    if (item.getBitmap() != null && !item.getBitmap().isRecycled()) {
                        bitmaps.add(item.getBitmap());
                    } else if (item.getFilePath() != null) {
                        // Nếu bitmap đã bị mất (ví dụ: bị recycle do hệ thống giải phóng bộ nhớ),
                        // thử tải lại từ filePath/Uri
                        try {
                            Bitmap reloadedBitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), Uri.parse(item.getFilePath()));
                            if (reloadedBitmap != null) {
                                item.setBitmap(reloadedBitmap); // Cập nhật bitmap trong ImageItem
                                bitmaps.add(reloadedBitmap);
                            } else {
                                Log.e(TAG, "Không thể tải lại bitmap từ " + item.getFilePath());
                            }
                        } catch (IOException e) {
                            Log.e(TAG, "Lỗi I/O khi tải lại bitmap từ " + item.getFilePath() + ": " + e.getMessage(), e);
                        }
                    }
                }

                if (bitmaps.isEmpty()) {
                    throw new Exception("Không có ảnh hợp lệ để tạo PDF");
                }

                Uri pdfUri = pdfGenerator.createMultiPagePdf(bitmaps, fileName);

                mainHandler.post(() -> {
                    setUIEnabled(true);

                    if (pdfUri != null) {
                        String message = "PDF đã được tạo thành công với " + bitmaps.size() + " trang: " + fileName;
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                        resetActivityState();
                        navigateToMainActivity();
                    } else {
                        Toast.makeText(this, "Lỗi khi tạo PDF", Toast.LENGTH_LONG).show();
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Lỗi khi tạo PDF: " + e.getMessage(), e);
                mainHandler.post(() -> {
                    setUIEnabled(true);
                    Toast.makeText(this, "Lỗi khi tạo PDF: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            } finally {
                // Quan trọng: Giải phóng tempProcessedImageUri sau khi đã sử dụng xong
                // nếu nó trỏ đến một file tạm thời trong cache
                if (tempProcessedImageUri != null) {
                    try {
                        File cachePath = new File(getCacheDir(), "images");
                        // Lấy tên file từ tempProcessedImageUri để xóa chính xác hơn
                        File tempFile = new File(tempProcessedImageUri.getPath());
                        if (tempFile.exists()) {
                            if (tempFile.delete()) {
                                Log.d(TAG, "Đã xóa file tạm thời: " + tempFile.getAbsolutePath());
                            } else {
                                Log.w(TAG, "Không thể xóa file tạm thời: " + tempFile.getAbsolutePath());
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Lỗi khi cố gắng xóa file tạm thời: " + e.getMessage(), e);
                    }
                }
            }
        });
    }

    /**
     * Reset trạng thái của Activity sau khi tạo PDF thành công
     */
    private void resetActivityState() {
        for (ImageItem item : imageList) {
            if (item.getBitmap() != null && !item.getBitmap().isRecycled()) {
                item.getBitmap().recycle();
            }
        }
        imageList.clear();
        tempProcessedImageUri = null;
        originalImageUri = null;
        updateUI();
        Log.d(TAG, "Activity state reset.");
    }

    /**
     * Điều hướng về MainActivity
     */
    private void navigateToMainActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    //region OnImageActionListener implementations
    @Override
    public void onImageClick(int position) {
        if (position >= 0 && position < imageList.size()) {
            // Lấy URI của ảnh từ danh sách
            String imageUriString = imageList.get(position).getFilePath();

            if (imageUriString != null) {
                // Tạo Intent để mở ImagePreviewActivity
                Intent previewIntent = new Intent(this, ImagePreviewActivity.class);

                // Gắn URI ảnh, flag và vị trí vào Intent
                previewIntent.putExtra("imageUri", imageUriString);
                previewIntent.putExtra("FROM_PDF_GROUP_PREVIEW", true);
                previewIntent.putExtra("imagePosition", position);

                // Khởi chạy ImagePreviewActivity và chờ kết quả
                startActivityForResult(previewIntent, REQUEST_IMAGE_PREVIEW);

            } else {
                Toast.makeText(this, "Không tìm thấy dữ liệu ảnh.", Toast.LENGTH_SHORT).show();
            }
        }}

    @Override
    public void onImageDelete(int position) {
        if (position >= 0 && position < imageList.size()) {
            ImageItem removedItem = imageList.remove(position);

            // Recycle bitmap để giải phóng memory
            if (removedItem.getBitmap() != null) {
                removedItem.getBitmap().recycle();
            }

            updateUI();
            Toast.makeText(this, "Đã xóa " + removedItem.getName(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onImageReorder(int fromPosition, int toPosition) {
        // Logic sắp xếp lại ảnh
        if (fromPosition != toPosition) {
            ImageItem item = imageList.remove(fromPosition);
            imageList.add(toPosition, item);
            adapter.notifyItemMoved(fromPosition, toPosition);
            // Cập nhật lại tên ảnh nếu cần
            for (int i = 0; i < imageList.size(); i++) {
                imageList.get(i).setName("Ảnh " + (i + 1));
            }
            updateUI();
            Toast.makeText(this, "Đã sắp xếp lại ảnh", Toast.LENGTH_SHORT).show();
        }
    }
    //endregion

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Giải phóng tài nguyên
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow();
        }
        // Giải phóng các bitmap còn lại khi Activity bị hủy hoàn toàn
        for (ImageItem item : imageList) {
            if (item.getBitmap() != null && !item.getBitmap().isRecycled()) {
                item.getBitmap().recycle();
            }
        }
        imageList.clear();
    }
}