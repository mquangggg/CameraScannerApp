package com.example.camerascanner;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.pdf.PdfDocument;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

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

public class PdfGenerationAndPreviewActivity extends AppCompatActivity {

    private static final String TAG = "PdfGenAndPreview";
    private static final int PERMISSION_REQUEST_CODE = 1;

    private ImageView ivPdfPreview;
    private Button btnSavePdf;
    private Button btnDeletePdf;
    private TextView tvPdfPreviewStatus;

    private Uri imageUriToConvert;
    private Bitmap croppedBitmap; // Bitmap gốc đã cắt
    private Bitmap processedBitmap; // Bitmap đã xử lý (trắng đen)
    private Uri finalPdfUri;

    private ExecutorService executorService;
    private Handler mainHandler;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pdf_generation_and_preview);

        ivPdfPreview = findViewById(R.id.ivPdfPreview);
        btnSavePdf = findViewById(R.id.btnSavePdf);
        btnDeletePdf = findViewById(R.id.btnDeletePdf);
        tvPdfPreviewStatus = findViewById(R.id.tvPdfPreviewStatus);

        executorService = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());

        if (getIntent().getExtras() != null && getIntent().getExtras().containsKey("croppedUri")) {
            imageUriToConvert = getIntent().getParcelableExtra("croppedUri");
            if (imageUriToConvert != null) {
                if (tvPdfPreviewStatus != null) tvPdfPreviewStatus.setText("Đang tải ảnh và xử lý...");
                loadAndProcessImageAsync(); // Gọi hàm mới để tải và xử lý ảnh
            } else {
                Toast.makeText(this, getString(R.string.error_invalid_image_uri), Toast.LENGTH_SHORT).show();
                if (tvPdfPreviewStatus != null) tvPdfPreviewStatus.setText("Lỗi: Không có URI ảnh.");
                finish();
            }
        } else {
            Toast.makeText(this, getString(R.string.error_no_image_to_process), Toast.LENGTH_SHORT).show();
            if (tvPdfPreviewStatus != null) tvPdfPreviewStatus.setText("Lỗi: Không có ảnh.");
            finish();
        }

        btnSavePdf.setOnClickListener(v -> {
            // PDF đã được tạo và lưu trong generatePdf().
            // Nút này giờ chỉ để xác nhận và đóng Activity.
            Toast.makeText(this, getString(R.string.pdf_saved_confirmation), Toast.LENGTH_SHORT).show();
            finish();
        });

        btnDeletePdf.setOnClickListener(v -> {
            deletePdfDocument(finalPdfUri);
            Toast.makeText(this, getString(R.string.pdf_deleted_and_canceled), Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    // Hàm mới để tải và xử lý ảnh (chuyển sang trắng đen) trên background thread
    private void loadAndProcessImageAsync() {
        executorService.execute(() -> {
            try {
                InputStream inputStream = getContentResolver().openInputStream(imageUriToConvert);
                if (inputStream == null) {
                    throw new IOException("Không thể mở InputStream từ URI: " + imageUriToConvert);
                }

                croppedBitmap = android.graphics.BitmapFactory.decodeStream(inputStream);
                inputStream.close();

                if (croppedBitmap == null) {
                    throw new IOException("Không thể giải mã Bitmap từ InputStream.");
                }

                // *** CHUYỂN ĐỔI SANG TRẮNG ĐEN TẠI ĐÂY ***
                mainHandler.post(() -> {
                    if (tvPdfPreviewStatus != null) tvPdfPreviewStatus.setText(getString(R.string.status_converting_to_black_white));
                });
                processedBitmap = convertToBlackAndWhite(croppedBitmap);

                mainHandler.post(() -> {
                    if (tvPdfPreviewStatus != null) tvPdfPreviewStatus.setText(getString(R.string.status_generating_pdf));
                });

                checkPermissionsAndGeneratePdfInternal(); // Sau khi xử lý ảnh, tiếp tục tạo PDF

            } catch (Exception e) {
                Log.e(TAG, "Lỗi khi tải hoặc xử lý ảnh trong background: " + e.getMessage(), e);
                mainHandler.post(() -> {
                    Toast.makeText(this, "Không thể tải hoặc xử lý ảnh: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    if (tvPdfPreviewStatus != null) tvPdfPreviewStatus.setText("Lỗi: Không tải được ảnh.");
                    finish();
                });
            }
        });
    }

    // Phương thức chuyển đổi Bitmap sang trắng đen (sử dụng ngưỡng đơn giản)
    private Bitmap convertToBlackAndWhite(Bitmap original) {
        int width = original.getWidth();
        int height = original.getHeight();
        Bitmap bwBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bwBitmap);
        canvas.drawColor(Color.WHITE); // Đặt nền mặc định là trắng

        int pixel;
        int A, R, G, B;
        int threshold = 128; // Ngưỡng để quyết định đen hay trắng (0-255). Có thể điều chỉnh.

        for (int x = 0; x < width; ++x) {
            for (int y = 0; y < height; ++y) {
                pixel = original.getPixel(x, y);
                A = Color.alpha(pixel);
                R = Color.red(pixel);
                G = Color.green(pixel);
                B = Color.blue(pixel);

                // Chuyển đổi sang giá trị xám (ví dụ: trung bình)
                // Hoặc dùng công thức trọng số: (0.299 * R + 0.587 * G + 0.114 * B)
                int gray = (R + G + B) / 3;

                // Áp dụng ngưỡng để chuyển sang đen hoặc trắng
                if (gray < threshold) {
                    bwBitmap.setPixel(x, y, Color.argb(A, 0, 0, 0)); // Đen
                } else {
                    bwBitmap.setPixel(x, y, Color.argb(A, 255, 255, 255)); // Trắng
                }
            }
        }
        return bwBitmap;
    }


    private void checkPermissionsAndGeneratePdfInternal() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            generatePdf();
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                mainHandler.post(() -> {
                    ActivityCompat.requestPermissions(this,
                            new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                            PERMISSION_REQUEST_CODE);
                });
            } else {
                generatePdf();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                executorService.execute(this::generatePdf);
            } else {
                mainHandler.post(() -> {
                    Toast.makeText(this, getString(R.string.permission_required_to_save_pdf), Toast.LENGTH_LONG).show();
                    if (tvPdfPreviewStatus != null) tvPdfPreviewStatus.setText("Lỗi: Không có quyền lưu PDF.");
                });
            }
        }
    }

    private void generatePdf() {
        // Sử dụng processedBitmap (ảnh đã trắng đen) thay vì croppedBitmap
        if (processedBitmap == null) {
            mainHandler.post(() -> {
                Toast.makeText(this, getString(R.string.error_no_processed_image_for_pdf), Toast.LENGTH_SHORT).show();
                if (tvPdfPreviewStatus != null) tvPdfPreviewStatus.setText("Lỗi: Không có ảnh để tạo PDF.");
            });
            return;
        }

        mainHandler.post(() -> {
            if (tvPdfPreviewStatus != null) tvPdfPreviewStatus.setText(getString(R.string.status_generating_pdf));
        });

        PdfDocument document = new PdfDocument();
        int pageHeight = 1120;
        int pageWidth = 792;
        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create();

        PdfDocument.Page page = document.startPage(pageInfo);
        Canvas canvas = page.getCanvas();

        // Sử dụng kích thước của processedBitmap
        float scaleX = (float) pageWidth / processedBitmap.getWidth();
        float scaleY = (float) pageHeight / processedBitmap.getHeight();
        float scale = Math.min(scaleX, scaleY);

        float left = (pageWidth - processedBitmap.getWidth() * scale) / 2;
        float top = (pageHeight - processedBitmap.getHeight() * scale) / 2;

        // Vẽ processedBitmap lên Canvas
        canvas.drawBitmap(processedBitmap, null, new android.graphics.RectF(left, top, left + processedBitmap.getWidth() * scale, top + processedBitmap.getHeight() * scale), null);
        document.finishPage(page);

        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String pdfFileName = "ScannedPdf_" + timeStamp + ".pdf";

        OutputStream outputStream = null;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentResolver resolver = getContentResolver();
                ContentValues contentValues = new ContentValues();
                contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, pdfFileName);
                contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
                contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + File.separator + "MyPDFs");
                finalPdfUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues);
                if (finalPdfUri != null) {
                    outputStream = resolver.openOutputStream(finalPdfUri);
                }
            } else {
                File pdfDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "MyPDFs");
                if (!pdfDir.exists()) {
                    pdfDir.mkdirs();
                }
                File pdfFile = new File(pdfDir, pdfFileName);
                outputStream = new FileOutputStream(pdfFile);
                finalPdfUri = Uri.fromFile(pdfFile);
            }

            if (outputStream != null) {
                document.writeTo(outputStream);
                mainHandler.post(() -> {
                    Toast.makeText(this, getString(R.string.pdf_creation_successful) + pdfFileName, Toast.LENGTH_LONG).show();
                    if (tvPdfPreviewStatus != null) tvPdfPreviewStatus.setText(getString(R.string.pdf_saved_at) + (finalPdfUri != null ? finalPdfUri.getPath() : "Không xác định"));
                    Log.d(TAG, "PDF saved at: " + (finalPdfUri != null ? finalPdfUri.toString() : "null"));
                });

                if (finalPdfUri != null) {
                    final Uri uriToPreview = finalPdfUri;
                    executorService.execute(() -> displayPdfPreview(uriToPreview));
                } else {
                    mainHandler.post(() -> Toast.makeText(this, getString(R.string.error_no_pdf_uri_for_preview), Toast.LENGTH_SHORT).show());
                }

            } else {
                throw new IOException("Không thể mở OutputStream để lưu PDF.");
            }

        } catch (IOException e) {
            Log.e(TAG, "Lỗi khi tạo hoặc lưu PDF: " + e.getMessage(), e);
            mainHandler.post(() -> {
                Toast.makeText(this, getString(R.string.error_creating_pdf) + e.getMessage(), Toast.LENGTH_LONG).show();
                if (tvPdfPreviewStatus != null) tvPdfPreviewStatus.setText("Error: " + e.getMessage());
            });
            finalPdfUri = null;
        } finally {
            document.close();
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    Log.e(TAG, "Lỗi khi đóng OutputStream: " + e.getMessage(), e);
                }
            }
        }
    }

    private void displayPdfPreview(Uri pdfUri) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            mainHandler.post(() -> {
                Toast.makeText(this, getString(R.string.pdf_preview_unsupported_android_version), Toast.LENGTH_LONG).show();
                if (tvPdfPreviewStatus != null) tvPdfPreviewStatus.setText(getString(R.string.pdf_preview_unsupported));
            });
            return;
        }

        PdfRenderer renderer = null;
        ParcelFileDescriptor parcelFileDescriptor = null;
        PdfRenderer.Page currentPage = null;
        Bitmap renderedBitmap = null;
        try {
            parcelFileDescriptor = getContentResolver().openFileDescriptor(pdfUri, "r");
            if (parcelFileDescriptor == null) {
                throw new IOException("Could not open ParcelFileDescriptor for PDF Uri.");
            }
            renderer = new PdfRenderer(parcelFileDescriptor);

            currentPage = renderer.openPage(0);

            // Lấy kích thước thực tế của ImageView trên UI Thread
            // Đảm bảo ivPdfPreview đã có kích thước trước khi lấy
            int desiredWidth = ivPdfPreview.getWidth();
            int desiredHeight = ivPdfPreview.getHeight();

            // Nếu ImageView chưa được layout (width/height là 0), sử dụng kích thước trang PDF hoặc giá trị mặc định
            if (desiredWidth == 0 || desiredHeight == 0) {
                desiredWidth = currentPage.getWidth();
                desiredHeight = currentPage.getHeight();
                if (desiredWidth > 2000) desiredWidth = 2000;
                if (desiredHeight > 2000) desiredHeight = 2000;
            }

            renderedBitmap = Bitmap.createBitmap(desiredWidth, desiredHeight, Bitmap.Config.ARGB_8888);
            currentPage.render(renderedBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);

            Bitmap finalRenderedBitmap = renderedBitmap;
            mainHandler.post(() -> {
                ivPdfPreview.setImageBitmap(finalRenderedBitmap);
                if (tvPdfPreviewStatus != null) tvPdfPreviewStatus.setText(getString(R.string.pdf_generation_successful_confirm));
            });

        } catch (IOException e) {
            Log.e(TAG, "Lỗi khi hiển thị PDF: " + e.getMessage(), e);
            mainHandler.post(() -> {
                Toast.makeText(this, getString(R.string.error_loading_pdf_preview), Toast.LENGTH_LONG).show();
                if (tvPdfPreviewStatus != null) tvPdfPreviewStatus.setText(getString(R.string.error_cannot_preview_pdf));
            });
        } finally {
            if (currentPage != null) {
                currentPage.close();
            }
            if (renderer != null) {
                renderer.close();
            }
            if (parcelFileDescriptor != null) {
                try {
                    parcelFileDescriptor.close();
                } catch (IOException e) {
                    Log.e(TAG, "Lỗi khi đóng ParcelFileDescriptor: " + e.getMessage(), e);
                }
            }
        }
    }

    private void deletePdfDocument(Uri pdfUri) {
        if (pdfUri != null) {
            executorService.execute(() -> {
                try {
                    ContentResolver resolver = getContentResolver();
                    int rowsDeleted = resolver.delete(pdfUri, null, null);
                    if (rowsDeleted > 0) {
                        Log.d(TAG, "Đã xóa PDF thành công: " + pdfUri.toString());
                        mainHandler.post(() -> Toast.makeText(this, getString(R.string.pdf_deleted_successfully), Toast.LENGTH_SHORT).show());
                    } else {
                        Log.e(TAG, "Không thể xóa PDF: " + pdfUri.toString());
                        mainHandler.post(() -> Toast.makeText(this, getString(R.string.error_cannot_delete_pdf), Toast.LENGTH_SHORT).show());
                    }
                } catch (SecurityException e) {
                    Log.e(TAG, "Không có quyền xóa PDF: " + e.getMessage(), e);
                    mainHandler.post(() -> Toast.makeText(this, getString(R.string.error_no_permission_to_delete_pdf) + e.getMessage(), Toast.LENGTH_LONG).show());
                } catch (Exception e) {
                    Log.e(TAG, "Lỗi khi xóa PDF: " + e.getMessage(), e);
                    mainHandler.post(() -> Toast.makeText(this, getString(R.string.error_deleting_pdf) + e.getMessage(), Toast.LENGTH_LONG).show());
                }
            });
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (croppedBitmap != null) {
            croppedBitmap.recycle();
            croppedBitmap = null;
        }
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow();
        }
    }
}
