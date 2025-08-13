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
    private ImageButton btnXepHang;
    private boolean isGridView = true;
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
    }

    private void initializeHelpers() {
        pdfGenerator = new PdfGenerator(this);
        executorService = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
    }

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

    private void processInitialIntentData() {
        Intent intent = getIntent();
        if (intent != null) {
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
    }

    private void toggleLayoutView() {
        if (recyclerViewImages != null) {
            RecyclerView.LayoutManager currentLayoutManager = recyclerViewImages.getLayoutManager();
            if (currentLayoutManager instanceof GridLayoutManager) {
                GridLayoutManager layoutManager = (GridLayoutManager) currentLayoutManager;

                if (isGridView) {
                    layoutManager.setSpanCount(1);
                    isGridView = false;
                    Toast.makeText(this, getString(R.string.switch_to_list_view), Toast.LENGTH_SHORT).show();
                } else {
                    layoutManager.setSpanCount(2);
                    isGridView = true;
                    Toast.makeText(this, getString(R.string.switch_to_grid_view), Toast.LENGTH_SHORT).show();
                }

                if (adapter != null) {
                    adapter.notifyDataSetChanged();
                }
            }
        }
    }

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

    private void handleAddMoreImages() {
        Intent intent = new Intent(PDFGroupActivity.this, CameraActivity.class);
        intent.putExtra("FROM_PDF_GROUP", true);
        startActivityForResult(intent, REQUEST_ADD_IMAGE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

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
        } else if (requestCode == REQUEST_IMAGE_PREVIEW) {
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

    private void handleCreatePDF() {
        if (imageList.isEmpty()) {
            Toast.makeText(this, getString(R.string.no_images_for_pdf), Toast.LENGTH_SHORT).show();
            return;
        }
        DialogHelper.showPdfFileNameDialog(this, this::createPDFWithFileName);
    }

    private void createPDFWithFileName(String fileName) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q && !PermissionHelper.hasStoragePermission(this)) {
            PermissionHelper.requestStoragePermission(this);
            return;
        }
        performCreatePDF(fileName);
    }

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
            String imageUriString = imageList.get(position).getFilePath();
            if (imageUriString != null) {
                Intent previewIntent = new Intent(this, ImagePreviewActivity.class);
                previewIntent.putExtra("imageUri", imageUriString);
                previewIntent.putExtra("FROM_PDF_GROUP_PREVIEW", true);
                previewIntent.putExtra("imagePosition", position);
                startActivityForResult(previewIntent, REQUEST_IMAGE_PREVIEW);
            } else {
                Toast.makeText(this, getString(R.string.no_image_data_found), Toast.LENGTH_SHORT).show();
            }
        }
    }

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

    @Override
    public void onImageReorder(int fromPosition, int toPosition) {
        if (fromPosition != toPosition) {
            ImageItem item = imageList.remove(fromPosition);
            imageList.add(toPosition, item);
            adapter.notifyItemMoved(fromPosition, toPosition);
            for (int i = 0; i < imageList.size(); i++) {
                String newName = getString(R.string.image_name_format, (i + 1));
                imageList.get(i).setName(newName);
            }
            updateUI();
            Toast.makeText(this, getString(R.string.images_reordered), Toast.LENGTH_SHORT).show();
        }
    }
    //endregion

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }

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