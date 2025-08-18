package com.example.camerascanner.activitypdfgroup;

import android.content.Intent;
import android.graphics.Bitmap;
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
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.example.camerascanner.R;
import com.example.camerascanner.activitycamera.CameraActivity;
import com.example.camerascanner.activityimagepreview.ImagePreviewActivity;
import com.example.camerascanner.BaseActivity;
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
public class PDFGroupActivity extends BaseActivity implements
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
    private ImageButton btnXepHang,btnXepItem;
    private boolean isGridView = true;
    private boolean isDragModeEnabled = false;
    private Button btnConfirm;

    // Data
    private List<ImageItem> imageList;
    private ImageGroupAdapter adapter;
    private PdfStyle currentPdfStyle;
    private Uri originalImageUri;
    private Uri tempProcessedImageUri;

    // Helper classes
    private PdfGenerator pdfGenerator;

    // Background processing
    private ExecutorService executorService;
    private Handler mainHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_after_pdf_generation);

        initializeUI();
        initializeHelpers();

        if (savedInstanceState != null) {
            restoreFromSavedState(savedInstanceState);
        } else {
            imageList = new ArrayList<>();
            processInitialIntentData();
            setupRecyclerView();
            setupClickListeners();
            updateUI();
        }
    }

    private void initializeUI() {
        tvTitle = findViewById(R.id.tvTitle);
        btnAddMore = findViewById(R.id.btnAddMore);
        recyclerViewImages = findViewById(R.id.recyclerViewImages);
        layoutEmptyState = findViewById(R.id.layoutEmptyState);
        btnCancel = findViewById(R.id.btnCancel);
        btnConfirm = findViewById(R.id.btnConfirm);
        btnXepHang = findViewById(R.id.btnXepHang);
        btnXepItem = findViewById(R.id.btnXepItem);
    }

    private void initializeHelpers() {
        pdfGenerator = new PdfGenerator(this);
        executorService = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * Khôi phục trạng thái Activity từ Bundle đã lưu.
     * Tải lại danh sách ảnh từ các URI đã lưu bằng cách sử dụng một luồng nền.
     * Sau khi tải xong, cập nhật UI trên luồng chính.
     */
    private void restoreFromSavedState(Bundle savedInstanceState) {
        imageList = new ArrayList<>();
        ArrayList<String> savedUriStrings = savedInstanceState.getStringArrayList("savedImageUris");

        if (savedUriStrings != null && !savedUriStrings.isEmpty()) {
            setUIEnabled(false);
            executorService.execute(() -> {
                List<ImageItem> loadedImages = new ArrayList<>();
                for (int i = 0; i < savedUriStrings.size(); i++) {
                    String uriString = savedUriStrings.get(i);
                    try {
                        Uri imageUri = Uri.parse(uriString);
                        Bitmap bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), imageUri);
                        if (bitmap != null) {
                            String imageName = getString(R.string.image_name_format, (loadedImages.size() + 1));
                            loadedImages.add(new ImageItem(bitmap, imageName, uriString));
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error loading bitmap from saved URI: " + uriString, e);
                    }
                }

                mainHandler.post(() -> {
                    imageList.addAll(loadedImages);
                    setupRecyclerView();
                    setupClickListeners();
                    updateUI();
                    setUIEnabled(true);
                });
            });
        } else {
            imageList = new ArrayList<>();
            setupRecyclerView();
            setupClickListeners();
            updateUI();
        }

        // Restore style
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
    }

    /**
     * Xử lý dữ liệu được truyền từ Activity trước (thường là CameraActivity).
     * Tải bitmap của ảnh đã xử lý từ URI được cung cấp trên một luồng nền
     * và thêm vào danh sách ảnh nếu thành công.
     */
    private void processInitialIntentData() {
        Intent intent = getIntent();
        if (intent != null) {
            // Lấy danh sách URI từ Intent (cho chế độ nhiều ảnh)
            ArrayList<String> processedImageUriStrings = intent.getStringArrayListExtra("processedImageUris");

            if (processedImageUriStrings != null && !processedImageUriStrings.isEmpty()) {
                setUIEnabled(false);
                executorService.execute(() -> {
                    List<ImageItem> loadedImages = new ArrayList<>();
                    for (String uriString : processedImageUriStrings) {
                        try {
                            Uri imageUri = Uri.parse(uriString);
                            Bitmap bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), imageUri);
                            if (bitmap != null) {
                                String imageName = getString(R.string.image_name_format, (loadedImages.size() + 1));
                                loadedImages.add(new ImageItem(bitmap, imageName, uriString));
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error loading bitmap from URI: " + uriString, e);
                        }
                    }

                    mainHandler.post(() -> {
                        setUIEnabled(true);
                        if (!loadedImages.isEmpty()) {
                            imageList.addAll(loadedImages);
                            updateUI();
                            Toast.makeText(this, getString(R.string.images_loaded_from_gallery, loadedImages.size()), Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(this, getString(R.string.cannot_load_any_image), Toast.LENGTH_SHORT).show();
                        }
                    });
                });
            }
            // Giữ logic cũ để xử lý trường hợp một ảnh duy nhất từ CameraActivity
            else {
                String processedImageUriString = intent.getStringExtra("processedImageUri");
                if (processedImageUriString != null && imageList.isEmpty()) {
                    tempProcessedImageUri = Uri.parse(processedImageUriString);
                    setUIEnabled(false);

                    executorService.execute(() -> {
                        Bitmap bitmap = null;
                        try {
                            bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), tempProcessedImageUri);
                        } catch (Exception e) {
                            Log.e(TAG, "Error loading bitmap from URI", e);
                        }

                        final Bitmap finalBitmap = bitmap;
                        mainHandler.post(() -> {
                            setUIEnabled(true);
                            if (finalBitmap != null) {
                                String imageName = getString(R.string.image_name_format, 1);
                                ImageItem firstImage = new ImageItem(finalBitmap, imageName, tempProcessedImageUri.toString());
                                imageList.add(firstImage);
                                updateUI();
                            } else {
                                Toast.makeText(this, getString(R.string.cannot_load_processed_image), Toast.LENGTH_SHORT).show();
                            }
                        });
                    });
                }
            }

            // Get style info
            String styleStr = intent.getStringExtra("pdfStyle");
            if (styleStr != null) {
                try {
                    currentPdfStyle = PdfStyle.valueOf(styleStr);
                } catch (IllegalArgumentException e) {
                    currentPdfStyle = PdfStyle.ORIGINAL;
                }
            } else {
                currentPdfStyle = PdfStyle.ORIGINAL;
            }

            // Get original image URI
            String originalUriStr = intent.getStringExtra("originalImageUri");
            if (originalUriStr != null) {
                originalImageUri = Uri.parse(originalUriStr);
            }
        }
    }

    private void setupRecyclerView() {
        if (recyclerViewImages != null) {
            GridLayoutManager layoutManager = new GridLayoutManager(this, 2);
            recyclerViewImages.setLayoutManager(layoutManager);
            adapter = new ImageGroupAdapter(this, imageList, this);
            recyclerViewImages.setAdapter(adapter);

            SimpleItemTouchHelperCallback callback = new SimpleItemTouchHelperCallback(adapter);
            ItemTouchHelper itemTouchHelper = new ItemTouchHelper(callback);
            itemTouchHelper.attachToRecyclerView(recyclerViewImages);

            // Gán ItemTouchHelper cho adapter
            adapter.setItemTouchHelper(itemTouchHelper);

        }
    }

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
            btnXepHang.setOnClickListener(v -> toggleLayoutView());
        }
        if (btnXepItem != null) {
            btnXepItem.setOnClickListener(v -> toggleDragMode());
        }
    }
    // Thêm phương thức mới để chuyển đổi trạng thái kéo thả
    private void toggleDragMode() {
        isDragModeEnabled = !isDragModeEnabled;
        if (adapter != null) {
            adapter.setDragEnabled(isDragModeEnabled);
            Toast.makeText(this, isDragModeEnabled ?
                    "Đã bật chế độ sắp xếp" :
                    "Đã tắt chế độ sắp xếp", Toast.LENGTH_SHORT).show();
        }
        // Cập nhật biểu tượng nút để phản ánh trạng thái


    }

    /**
     * Chuyển đổi giữa chế độ xem lưới (Grid View) và xem danh sách (List View) cho RecyclerView.
     * Cập nhật số cột của GridLayoutManager và thông báo cho adapter.
     */
    private void toggleLayoutView() {
        if (recyclerViewImages != null) {
            RecyclerView.LayoutManager currentLayoutManager = recyclerViewImages.getLayoutManager();
            if (currentLayoutManager instanceof GridLayoutManager) {
                GridLayoutManager layoutManager = (GridLayoutManager) currentLayoutManager;

                if (isGridView) {
                    layoutManager.setSpanCount(1);
                    adapter.setDragEnabled(false);
                    btnXepItem.setVisibility(View.GONE);
                    isGridView = false;
                    Toast.makeText(this, getString(R.string.switch_to_list_view), Toast.LENGTH_SHORT).show();
                } else {
                    layoutManager.setSpanCount(2);
                    isGridView = true;
                    isDragModeEnabled = false;
                    btnXepItem.setBackgroundResource(R.drawable.ic_view_grid);
                    btnXepItem.setVisibility(View.VISIBLE);
                    Toast.makeText(this, getString(R.string.switch_to_grid_view), Toast.LENGTH_SHORT).show();
                }

                if (adapter != null) {
                    adapter.notifyDataSetChanged();
                }
            }
        }
    }

    /**
     * Cập nhật giao diện người dùng dựa trên trạng thái hiện tại của danh sách ảnh.
     * Thay đổi tiêu đề, hiển thị/ẩn trạng thái rỗng và bật/tắt nút "Xác nhận".
     */
    private void updateUI() {
        // Update title
        if (tvTitle != null) {
            String title = getString(R.string.captured_images_title, (imageList != null ? imageList.size() : 0));
            tvTitle.setText(title);
        }

        // Show/hide empty state
        if (layoutEmptyState != null && recyclerViewImages != null) {
            boolean isEmpty = imageList == null || imageList.isEmpty();
            layoutEmptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
            recyclerViewImages.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        }

        // Enable/disable confirm button
        if (btnConfirm != null) {
            boolean hasImages = imageList != null && !imageList.isEmpty();
            btnConfirm.setEnabled(hasImages);
            btnConfirm.setAlpha(hasImages ? 1.0f : 0.5f);
        }

        // Update adapter
        if (adapter != null) {
            adapter.notifyDataSetChanged();
            if (imageList != null && imageList.size() > 1) {
                recyclerViewImages.scrollToPosition(imageList.size() - 1);
            }
        }
    }

    /**
     * Mở CameraActivity để người dùng chụp thêm ảnh.
     * Gửi cờ 'FROM_PDF_GROUP' để CameraActivity biết cách xử lý kết quả.
     */
    private void handleAddMoreImages() {
        Intent intent = new Intent(PDFGroupActivity.this, CameraActivity.class);
        intent.putExtra("FROM_PDF_GROUP", true);
        startActivityForResult(intent, REQUEST_ADD_IMAGE);
    }

    /**
     * Xử lý kết quả trả về từ các Activity khác (CameraActivity hoặc ImagePreviewActivity).
     *
     * @param requestCode Mã yêu cầu xác định Activity nào trả về kết quả.
     * @param resultCode  Mã kết quả (RESULT_OK, RESULT_CANCELED, v.v.).
     * @param data        Intent chứa dữ liệu trả về.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Xử lý kết quả khi thêm ảnh mới từ CameraActivity
        if (requestCode == REQUEST_ADD_IMAGE) {
            if (resultCode == RESULT_OK && data != null) {
                String processedImageUriString = data.getStringExtra("processedImageUri");
                if (processedImageUriString != null) {
                    Uri newImageUri = Uri.parse(processedImageUriString);
                    setUIEnabled(false);

                    executorService.execute(() -> {
                        Bitmap newBitmap = null;
                        try {
                            newBitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), newImageUri);

                            if (newBitmap != null) {
                                String imageName = getString(R.string.image_name_format, (imageList.size() + 1));
                                String actualFilePath = newImageUri.toString();
                                final Bitmap finalNewBitmap = newBitmap;

                                mainHandler.post(() -> {
                                    try {
                                        ImageItem newImageItem = new ImageItem(finalNewBitmap, imageName, actualFilePath);
                                        if (adapter != null) {
                                            adapter.addImage(newImageItem);
                                        } else {
                                            imageList.add(newImageItem);
                                        }
                                        updateUIComponents();
                                        setUIEnabled(true);
                                        Toast.makeText(this, getString(R.string.image_added_successfully), Toast.LENGTH_SHORT).show();
                                    } catch (Exception e) {
                                        Toast.makeText(this, getString(R.string.error_adding_image), Toast.LENGTH_SHORT).show();
                                        setUIEnabled(true);
                                    }
                                });
                            } else {
                                mainHandler.post(() -> {
                                    Toast.makeText(this, getString(R.string.cannot_load_new_image), Toast.LENGTH_SHORT).show();
                                    setUIEnabled(true);
                                });
                            }
                        } catch (Exception e) {
                            mainHandler.post(() -> {
                                Toast.makeText(this, getString(R.string.error_loading_image), Toast.LENGTH_SHORT).show();
                                setUIEnabled(true);
                            });
                        }
                    });
                }
            }
        }
        // Xử lý kết quả khi chỉnh sửa ảnh từ ImagePreviewActivity
        else if (requestCode == REQUEST_IMAGE_PREVIEW) {
            if (resultCode == RESULT_OK && data != null) {
                String updatedImageUriString = data.getStringExtra("updatedImageUri");
                int imagePosition = data.getIntExtra("imagePosition", -1);

                if (updatedImageUriString != null && imagePosition >= 0 && imagePosition < imageList.size()) {
                    Uri updatedImageUri = Uri.parse(updatedImageUriString);
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
                                        Toast.makeText(this, getString(R.string.image_updated_successfully), Toast.LENGTH_SHORT).show();
                                    } catch (Exception e) {
                                        Toast.makeText(this, getString(R.string.error_updating_image), Toast.LENGTH_SHORT).show();
                                        setUIEnabled(true);
                                    }
                                });
                            } else {
                                mainHandler.post(() -> {
                                    Toast.makeText(this, getString(R.string.cannot_load_updated_image), Toast.LENGTH_SHORT).show();
                                    setUIEnabled(true);
                                });
                            }
                        } catch (Exception e) {
                            mainHandler.post(() -> {
                                Toast.makeText(this, getString(R.string.error_loading_image), Toast.LENGTH_SHORT).show();
                                setUIEnabled(true);
                            });
                        }
                    });
                }
            }
        }
    }

    /**
     * Cập nhật các thành phần giao diện người dùng như tiêu đề, trạng thái trống, và nút xác nhận.
     * Chú thích này gần giống với `updateUI()` nhưng được tách ra để sử dụng trong `onActivityResult`.
     */
    private void updateUIComponents() {
        if (tvTitle != null) {
            String title = getString(R.string.captured_images_title, (imageList != null ? imageList.size() : 0));
            tvTitle.setText(title);
        }

        if (layoutEmptyState != null && recyclerViewImages != null) {
            boolean isEmpty = imageList == null || imageList.isEmpty();
            layoutEmptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
            recyclerViewImages.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        }

        if (btnConfirm != null) {
            boolean hasImages = imageList != null && !imageList.isEmpty();
            btnConfirm.setEnabled(hasImages);
            btnConfirm.setAlpha(hasImages ? 1.0f : 0.5f);
        }

        if (imageList != null && imageList.size() > 1) {
            recyclerViewImages.scrollToPosition(imageList.size() - 1);
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        ArrayList<String> imageUriStrings = new ArrayList<>();
        for (ImageItem item : imageList) {
            if (item.getFilePath() != null) {
                imageUriStrings.add(item.getFilePath());
            }
        }
        outState.putStringArrayList("savedImageUris", imageUriStrings);
        outState.putInt("imageListSize", imageList.size());

        if (currentPdfStyle != null) {
            outState.putString("currentPdfStyle", currentPdfStyle.name());
        }
    }

    private void setUIEnabled(boolean enabled) {
        if (btnAddMore != null) btnAddMore.setEnabled(enabled);
        if (btnCancel != null) btnCancel.setEnabled(enabled);
        if (btnConfirm != null) btnConfirm.setEnabled(enabled);
        if (recyclerViewImages != null) recyclerViewImages.setEnabled(enabled);
    }

    private void handleCancel() {
        finish();
    }

    /**
     * Xử lý sự kiện khi người dùng nhấn nút "Tạo PDF".
     * Kiểm tra xem có ảnh nào không và hiển thị hộp thoại để nhập tên file PDF.
     */
    private void handleCreatePDF() {
        if (imageList.isEmpty()) {
            Toast.makeText(this, getString(R.string.no_images_for_pdf), Toast.LENGTH_SHORT).show();
            return;
        }
        DialogHelper.showPdfFileNameDialog(this, this::createPDFWithFileName);
    }

    /**
     * Bắt đầu quá trình tạo PDF sau khi người dùng đã nhập tên file.
     * Kiểm tra quyền ghi bộ nhớ nếu cần và gọi hàm `performCreatePDF`.
     *
     * @param fileName Tên file PDF được nhập bởi người dùng.
     */
    private void createPDFWithFileName(String fileName) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q && !PermissionHelper.hasStoragePermission(this)) {
            PermissionHelper.requestStoragePermission(this);
            return;
        }
        performCreatePDF(fileName);
    }

    /**
     * Thực hiện việc tạo file PDF trên một luồng nền để tránh làm tắc UI.
     *
     * 1. Tải lại các bitmap từ file nếu cần thiết.
     * 2. Gọi `pdfGenerator.createMultiPagePdf` để tạo PDF.
     * 3. Xử lý kết quả (thành công/thất bại) trên luồng chính.
     * 4. Dọn dẹp các file tạm sau khi hoàn tất.
     *
     * @param fileName Tên file PDF sẽ được tạo.
     */
    private void performCreatePDF(String fileName) {
        Toast.makeText(this, getString(R.string.creating_pdf), Toast.LENGTH_SHORT).show();
        setUIEnabled(false);

        executorService.execute(() -> {
            try {
                List<Bitmap> bitmaps = new ArrayList<>();
                for (ImageItem item : imageList) {
                    if (item.getBitmap() != null && !item.getBitmap().isRecycled()) {
                        bitmaps.add(item.getBitmap());
                    } else if (item.getFilePath() != null) {
                        try {
                            Bitmap reloadedBitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), Uri.parse(item.getFilePath()));
                            if (reloadedBitmap != null) {
                                item.setBitmap(reloadedBitmap);
                                bitmaps.add(reloadedBitmap);
                            }
                        } catch (IOException e) {
                            Log.e(TAG, "Error reloading bitmap from " + item.getFilePath(), e);
                        }
                    }
                }

                if (bitmaps.isEmpty()) {
                    throw new Exception(getString(R.string.no_valid_images_for_pdf));
                }

                Uri pdfUri = pdfGenerator.createMultiPagePdf(bitmaps, fileName);

                mainHandler.post(() -> {
                    setUIEnabled(true);
                    if (pdfUri != null) {
                        Toast.makeText(this, getString(R.string.pdf_created_successfully), Toast.LENGTH_LONG).show();
                        resetActivityState();
                        navigateToMainActivity();
                    } else {
                        Toast.makeText(this, getString(R.string.error_creating_pdf), Toast.LENGTH_LONG).show();
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Error creating PDF", e);
                mainHandler.post(() -> {
                    setUIEnabled(true);
                    Toast.makeText(this, getString(R.string.error_creating_pdf), Toast.LENGTH_LONG).show();
                });
            } finally {
                cleanupTempFile();
            }
        });
    }

    /**
     * Xóa file ảnh tạm đã được tạo trước đó.
     */
    private void cleanupTempFile() {
        if (tempProcessedImageUri != null) {
            try {
                File tempFile = new File(tempProcessedImageUri.getPath());
                if (tempFile.exists()) {
                    tempFile.delete();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error cleaning up temp file", e);
            }
        }
    }

    /**
     * Đặt lại trạng thái của Activity sau khi PDF đã được tạo thành công.
     * Giải phóng bộ nhớ của các bitmap và xóa danh sách ảnh.
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
    }

    /**
     * Điều hướng người dùng về MainActivity.
     * Sử dụng cờ để xóa tất cả các Activity khác trên stack.
     */
    private void navigateToMainActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    //region OnImageActionListener implementations
    /**
     * Xử lý sự kiện khi một ảnh trong danh sách được nhấp vào.
     * Mở ImagePreviewActivity để xem và chỉnh sửa ảnh đó.
     *
     * @param position Vị trí của ảnh được nhấp.
     */
    @Override
    public void onImageClick(int position) {
        if (position >= 0 && position < imageList.size()) {
            String imageUriString = imageList.get(position).getFilePath();
            if (imageUriString != null) {
                Uri imageUri = Uri.parse(imageUriString);
                Intent previewIntent = new Intent(this, ImagePreviewActivity.class);
                previewIntent.putExtra("imageUri", imageUri);
                previewIntent.putExtra("FROM_PDF_GROUP_PREVIEW", true);
                previewIntent.putExtra("imagePosition", position);
                // Thêm cờ quyền truy cập
                previewIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivityForResult(previewIntent, REQUEST_IMAGE_PREVIEW);
            } else {
                Toast.makeText(this, getString(R.string.no_image_data_found), Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * Xử lý sự kiện khi người dùng xóa một ảnh khỏi danh sách.
     * Giải phóng bộ nhớ của bitmap và cập nhật lại UI.
     *
     * @param position Vị trí của ảnh cần xóa.
     */
    @Override
    public void onImageDelete(int position) {
        if (position >= 0 && position < imageList.size()) {
            ImageItem removedItem = imageList.remove(position);
            if (removedItem.getBitmap() != null) {
                removedItem.getBitmap().recycle();
            }
            updateUI();
            Toast.makeText(this, getString(R.string.image_deleted, removedItem.getName()), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Xử lý sự kiện khi người dùng sắp xếp lại thứ tự ảnh bằng cách kéo và thả.
     * Cập nhật vị trí của ảnh trong danh sách và thay đổi tên ảnh tương ứng với thứ tự mới.
     *
     * @param fromPosition Vị trí ban đầu của ảnh.
     * @param toPosition   Vị trí đích của ảnh.
     */
    @Override
    public void onImageReorder(int fromPosition, int toPosition) {
        for (int i = 0; i < imageList.size(); i++) {
            String newName = getString(R.string.image_name_format, (i + 1));
            imageList.get(i).setName(newName);
        }

        // Cập nhật giao diện nếu cần
        updateUI();
    }
    //endregion

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }

    /**
     * Giải phóng các tài nguyên khi Activity bị hủy.
     * Tắt dịch vụ luồng nền và giải phóng bộ nhớ của tất cả các bitmap để tránh rò rỉ bộ nhớ.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow();
        }
        for (ImageItem item : imageList) {
            if (item.getBitmap() != null && !item.getBitmap().isRecycled()) {
                item.getBitmap().recycle();
            }
        }
        imageList.clear();
    }
}