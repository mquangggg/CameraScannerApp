package com.example.camerascanner.activityimagepreview;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log; // Import Log
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.Rotate; // Import để xoay ảnh với Glide
import com.bumptech.glide.request.RequestOptions; // Import RequestOptions
import com.example.camerascanner.R;
import com.example.camerascanner.activitysignature.SignatureAdapter;
import com.example.camerascanner.activitysignature.SignatureManager;
import com.example.camerascanner.BaseActivity;
import com.example.camerascanner.activitypdf.DialogHelper;
import com.example.camerascanner.activitypdf.PermissionHelper;
import com.example.camerascanner.activitysignature.signatureview.imagesignpreview.ImageSignPreviewActivity;
import com.example.camerascanner.activityimagepreview.Jpeg.JpegGenerator;
import com.example.camerascanner.activitysignature.signatureview.signature.SignatureActivity;
import com.example.camerascanner.activitycrop.CropActivity;
import com.example.camerascanner.activityocr.OCRActivity;
import com.example.camerascanner.activitypdf.PdfGenerationAndPreviewActivity;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ImagePreviewActivity extends BaseActivity {

    private static final String TAG = "ImagePreviewActivity"; // Thêm TAG cho logging
    private ImageView imageViewPreview;
    private ImageButton btnSaveJpeg;
    private Button btnRotatePreview,btnSign,btnGenPDF,btnMakeOcr,btnCrop;
    private Button btnConfirmPreview;
    private Uri imageUri;
    private int rotationAngle = 0; // Để theo dõi góc xoay hiện tại

    private static final int REQUEST_SIGNATURE = 102;
    private static final int REQUEST_CROP = 104; // Request code cho crop
    private static final int REQUEST_PDF_GEN_PREVIEW = 103; // Thêm dòng này
    private JpegGenerator jpegGenerator;
    private ExecutorService executorService;
    private Handler mainHandler;

    private SignatureManager signatureManager;
    private SignatureAdapter signatureAdapter;
    private List<Uri> savedSignatures;


    private boolean isFromPdfGroupPreview = false;
    private int imagePosition = -1;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_preview);

        imageViewPreview = findViewById(R.id.imageViewPreview);
        btnRotatePreview = findViewById(R.id.btnRotate );
        btnConfirmPreview = findViewById(R.id.btnConfirmPreview);
        btnSign = findViewById(R.id.btnSign);
        btnMakeOcr = findViewById(R.id.btnMakeOcr);
        btnGenPDF = findViewById(R.id.btnGenPDF);
        btnSaveJpeg = findViewById(R.id.btnSaveJpeg);
        btnCrop = findViewById(R.id.btnCrop);

        executorService = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());

        // Khởi tạo SignatureManager
        signatureManager = new SignatureManager(this);


        try {
            jpegGenerator = new JpegGenerator(this);
            Log.d(TAG, "JpegGenerator initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing JpegGenerator: " + e.getMessage(), e);
            jpegGenerator = null;
        }
        Intent intents = getIntent();
        if (intents != null) {
            isFromPdfGroupPreview = intents.getBooleanExtra("FROM_PDF_GROUP_PREVIEW", false);
            imagePosition = intents.getIntExtra("imagePosition", -1);
           }

        imageUri = intents.getParcelableExtra("imageUri");
        if (imageUri != null) {
            Log.d(TAG, "ImagePreviewActivity: Received URI: " + imageUri.toString()); // Log khi nhận URI
            loadImageWithRotation();
        } else {
            Toast.makeText(this, getString(R.string.no_image), Toast.LENGTH_SHORT).show();
            Log.e(TAG, "ImagePreviewActivity: No image URI received."); // Log lỗi nếu không nhận được URI
            finish();
        }

        btnRotatePreview.setOnClickListener(v -> {
            rotationAngle = (rotationAngle + 90) % 360;
            loadImageWithRotation();
            Log.d(TAG, "ImagePreviewActivity: Rotated image to " + rotationAngle + " degrees."); // Log khi xoay ảnh
        });

        // XỬ LÝ SỰ KIỆN XÁC NHẬN PREVIEW:
        // Lưu ảnh đã xoay vào cache và trả kết quả về PDFGroupActivity
        btnConfirmPreview.setOnClickListener(v -> {
            // Lấy bitmap hiện tại từ ImageView (đã bao gồm góc xoay)
            Bitmap rotatedBitmap = ((BitmapDrawable) imageViewPreview.getDrawable()).getBitmap();
            Uri tempUri = saveBitmapToCache(rotatedBitmap);

            if (tempUri != null) {
                // TRẢ KẾT QUẢ VỀ PDFGROUPACTIVITY:
                // Truyền URI ảnh đã xoay và vị trí để cập nhật đúng ảnh trong list
                Intent resultIntent = new Intent();
                resultIntent.putExtra("updatedImageUri", tempUri.toString());
                resultIntent.putExtra("imagePosition", imagePosition);
                setResult(RESULT_OK, resultIntent);
                finish();
                Log.d(TAG, "ImagePreviewActivity: Returning updated image to PDFGroupActivity at position " + imagePosition);
            } else {
                Toast.makeText(this, getString(R.string.failed_to_save_image), Toast.LENGTH_SHORT).show();
                setResult(RESULT_CANCELED); // Đặt kết quả là hủy nếu không lưu được
                finish();
                Log.e(TAG, "ImagePreviewActivity: Failed to save bitmap for confirmation.");
            }
        });
        // XỬ LÝ SỰ KIỆN TẠO OCR:
        // Chuyển ảnh hiện tại sang OCRActivity để nhận dạng văn bản
        btnMakeOcr.setOnClickListener(v->{
            if (imageViewPreview.getDrawable() instanceof BitmapDrawable) {
                Bitmap currentBitmap = ((BitmapDrawable) imageViewPreview.getDrawable()).getBitmap();

                // LƯU BITMAP VÀO CACHE:
                // Cần lưu ảnh tạm thời để truyền URI cho OCRActivity
                Uri tempUri = saveBitmapToCache(currentBitmap);

                if (tempUri != null) {
                    // KHỞI ĐỘNG OCRActivity:
                    // Truyền URI ảnh để thực hiện nhận dạng văn bản
                    Intent intent = new Intent(ImagePreviewActivity.this, OCRActivity.class);
                    intent.putExtra("image_uri_for_ocr", tempUri.toString());
                    startActivity(intent);
                } else {
                    // Xử lý lỗi nếu không lưu được ảnh tạm thời
                    Toast.makeText(ImagePreviewActivity.this, getString(R.string.no_image_to_ocr), Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(ImagePreviewActivity.this, getString(R.string.no_image), Toast.LENGTH_SHORT).show();
            }
        });
        // XỬ LÝ SỰ KIỆN TẠO PDF:
        // Chuyển ảnh hiện tại sang PdfGenerationAndPreviewActivity để tạo PDF
        btnGenPDF.setOnClickListener(v-> {
            // Lấy bitmap đã xoay từ ImageView
            Bitmap rotatedBitmap = ((BitmapDrawable)imageViewPreview.getDrawable()).getBitmap();
            Uri tempUri = saveBitmapToCache(rotatedBitmap);

            if (tempUri != null) {
                // KHỞI ĐỘNG PDF GENERATION:
                // Truyền URI ảnh để tạo PDF với ảnh đã được xoay
                Intent intent = new Intent(this, PdfGenerationAndPreviewActivity.class);
                intent.putExtra("imageUri", tempUri.toString());
                startActivityForResult(intent, REQUEST_PDF_GEN_PREVIEW);
            } else {
                Toast.makeText(this, getString(R.string.no_image_to_pdf), Toast.LENGTH_SHORT).show();
            }
        });
        // XỬ LÝ SỰ KIỆN THÊM CHỮ KÝ:
        // Hiển thị BottomSheetDialog với danh sách chữ ký và tùy chọn thêm mới
        btnSign.setOnClickListener(v->{
            // TẠO BOTTOM SHEET DIALOG:
            // Hiển thị giao diện chọn chữ ký từ dưới lên
            BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(this);
            View bottomSheetView  = getLayoutInflater().inflate(R.layout.sign_layout_botton,null);
            bottomSheetDialog.setContentView(bottomSheetView);

            // XỬ LÝ NÚT THÊM CHỮ KÝ MỚI:
            ImageButton btnAddSign = bottomSheetView.findViewById(R.id.btnAddSign);
            btnAddSign.setOnClickListener(v1->{
                bottomSheetDialog.dismiss();
                // Lấy bitmap đã xoay và lưu vào cache
                Bitmap rotatedBitmap = ((BitmapDrawable)imageViewPreview.getDrawable()).getBitmap();
                Uri tempUri = saveBitmapToCache(rotatedBitmap);

                // KHỞI ĐỘNG SIGNATURE ACTIVITY:
                // Cho phép người dùng vẽ chữ ký mới trên ảnh
                Intent intent = new Intent(ImagePreviewActivity.this, SignatureActivity.class);
                intent.putExtra("imageUri", tempUri);
                startActivityForResult(intent, REQUEST_SIGNATURE);
            });

            // THIẾT LẬP RECYCLERVIEW CHO DANH SÁCH CHỮ KÝ:
            RecyclerView recyclerViewSigns = bottomSheetView.findViewById(R.id.recyclerViewSigns);

            // Layout manager ngang để hiển thị chữ ký theo hàng ngang
            LinearLayoutManager layoutManager = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
            recyclerViewSigns.setLayoutManager(layoutManager);
            TextView tvNoSignatures = bottomSheetView.findViewById(R.id.tvNoSignatures);

            // KIỂM TRA VÀ HIỂN THỊ DANH SÁCH CHỮ KÝ:
            // Lấy danh sách chữ ký đã lưu từ SignatureManager
            savedSignatures = signatureManager.getSavedSignatureUris();
            if (savedSignatures.isEmpty()) {
                // Hiển thị thông báo "không có chữ ký" nếu danh sách rỗng
                tvNoSignatures.setVisibility(View.VISIBLE);
                recyclerViewSigns.setVisibility(View.GONE);
            } else {
                // Hiển thị RecyclerView nếu có chữ ký
                tvNoSignatures.setVisibility(View.GONE);
                recyclerViewSigns.setVisibility(View.VISIBLE);
            }
            // THIẾT LẬP ADAPTER CHO RECYCLERVIEW:
            // Xử lý sự kiện click và delete cho từng chữ ký
            signatureAdapter = new SignatureAdapter(this, savedSignatures,
                    new SignatureAdapter.OnSignatureClickListener() {
                @Override
                public void onSignatureClick(Uri signatureUri) {
                    bottomSheetDialog.dismiss();
                    // CHỌN CHỮ KÝ CÓ SẴN:
                    // Lấy bitmap đã xoay và lưu vào cache
                    Bitmap rotatedBitmap = ((BitmapDrawable)imageViewPreview.getDrawable()).getBitmap();
                    Uri tempUri = saveBitmapToCache(rotatedBitmap);

                    // KHỞI ĐỘNG IMAGE SIGN PREVIEW:
                    // Cho phép đặt chữ ký đã có lên ảnh với vị trí mặc định
                    Intent intent = new Intent(ImagePreviewActivity.this, ImageSignPreviewActivity.class);
                    intent.putExtra("imageUri", tempUri);
                    intent.putExtra("signatureUri", signatureUri);
                    // Vị trí mặc định cho chữ ký (có thể điều chỉnh sau)
                    intent.putExtra("boundingBoxLeft", 100f);
                    intent.putExtra("boundingBoxTop", 100f);

                    startActivityForResult(intent, REQUEST_SIGNATURE);
                }
            },new SignatureAdapter.OnSignatureDeleteListener() {
                @Override
                public void onSignatureDelete(Uri signatureUri, int position) {
                    // XÁC NHẬN XÓA CHỮ KÝ:
                    // Hiển thị dialog xác nhận trước khi xóa
                    showDeleteConfirmDialog(signatureUri, position, bottomSheetDialog);
                }
            });
            recyclerViewSigns.setAdapter(signatureAdapter);


            bottomSheetDialog.show();
        });
        // XỬ LÝ SỰ KIỆN CROP ẢNH:
        // Chuyển ảnh hiện tại sang CropActivity để cắt và chỉnh sửa
        btnCrop.setOnClickListener(v -> {
            if (imageViewPreview.getDrawable() instanceof BitmapDrawable) {
                // Lấy bitmap hiện tại từ ImageView
                Bitmap currentBitmap = ((BitmapDrawable) imageViewPreview.getDrawable()).getBitmap();
                Uri tempUri = saveBitmapToCache(currentBitmap);

                if (tempUri != null) {
                    // KHỞI ĐỘNG CROP ACTIVITY:
                    // Truyền URI ảnh và flag để CropActivity biết nguồn gọi
                    Intent intent = new Intent(ImagePreviewActivity.this, CropActivity.class);
                    intent.putExtra("imageUri", tempUri.toString());

                    // FLAG ĐỂ IDENTIFY NGUỒN GỌI:
                    // CropActivity sẽ xử lý khác nhau tùy theo nguồn gọi
                    intent.putExtra("FROM_IMAGE_PREVIEW", true);

                    startActivityForResult(intent, REQUEST_CROP);
                    Log.d(TAG, "ImagePreviewActivity: Starting CropActivity with URI: " + tempUri);
                } else {
                    Toast.makeText(ImagePreviewActivity.this, getString(R.string.failed_to_save_image), Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(ImagePreviewActivity.this, getString(R.string.no_image), Toast.LENGTH_SHORT).show();
            }
        });
        btnSaveJpeg.setOnClickListener(v->{
        //  Bitmap rotatedBitmap = ((BitmapDrawable)imageViewPreview.getDrawable()).getBitmap();
            handleSaveJpeg();
        });
    }
    /**
     * HIỂN THỊ DIALOG XÁC NHẬN XÓA CHỮ KÝ:
     * Hiển thị AlertDialog để người dùng xác nhận trước khi xóa chữ ký.
     * Sau khi xác nhận, sẽ xóa chữ ký khỏi SignatureManager và cập nhật UI.
     * @param signatureUri URI của chữ ký cần xóa
     * @param position Vị trí của chữ ký trong RecyclerView
     * @param bottomSheetDialog BottomSheetDialog để đóng sau khi xóa
     */
    private void showDeleteConfirmDialog(Uri signatureUri, int position, BottomSheetDialog bottomSheetDialog) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Xác nhận xóa");
        builder.setMessage("Bạn có chắc chắn muốn xóa chữ ký này?");

        builder.setPositiveButton("Xóa", (dialog, which) -> {
            // Xóa chữ ký khỏi SignatureManager
            signatureManager.removeSignatureUri(signatureUri);

            // Xóa file chữ ký khỏi cache nếu cần
            deleteSignatureFile(signatureUri);

            // Cập nhật adapter
            if (signatureAdapter != null) {
                signatureAdapter.removeSignature(position);
            }

            Toast.makeText(this, "Đã xóa chữ ký", Toast.LENGTH_SHORT).show();
        });

        builder.setNegativeButton("Hủy", (dialog, which) -> {
            dialog.dismiss();
        });

        builder.show();
    }

    /**
     * XÓA FILE CHỮ KÝ KHỎI BỘ NHỚ CACHE:
     * Xóa file chữ ký vật lý khỏi bộ nhớ cache của ứng dụng.
     * Chỉ xóa file local (scheme "file"), không xóa URI content.
     * @param signatureUri URI của file chữ ký cần xóa
     */
    private void deleteSignatureFile(Uri signatureUri) {
        try {
            if (signatureUri.getScheme().equals("file")) {
                File file = new File(signatureUri.getPath());
                if (file.exists()) {
                    file.delete();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error deleting signature file: " + e.getMessage(), e);
        }
    }
    /**
     * XỬ LÝ SỰ KIỆN LƯU JPEG:
     * Kiểm tra ảnh hiện tại và hiển thị dialog để người dùng nhập tên file.
     * Logic này được chuyển từ PdfGenerationAndPreviewActivity để tái sử dụng.
     * Sử dụng DialogHelper để hiển thị giao diện nhập tên file.
     */
    private void handleSaveJpeg() {
        if (imageViewPreview.getDrawable() instanceof BitmapDrawable) {
            Bitmap currentBitmap = ((BitmapDrawable) imageViewPreview.getDrawable()).getBitmap();
            if (currentBitmap != null) {
                // Hiển thị dialog để người dùng nhập tên file
                DialogHelper.showJpegFileNameDialog(this, this::saveJpegWithFileName);
            } else {
                Toast.makeText(this, getString(R.string.no_image_to_save_jpeg), Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, getString(R.string.no_image_to_save_jpeg), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * LƯU JPEG VỚI TÊN FILE DO NGƯỜI DÙNG NHẬP:
     * Kiểm tra quyền lưu trữ trước khi thực hiện lưu file.
     * Nếu chưa có quyền, sẽ yêu cầu người dùng cấp quyền.
     * @param fileName Tên file do người dùng nhập
     */
    private void saveJpegWithFileName(String fileName) {
        if (PermissionHelper.hasStoragePermission(this)) {
            performSaveJpeg(fileName);
        } else {
            PermissionHelper.requestStoragePermission(this);
        }
    }

    /**
     * THỰC HIỆN LƯU JPEG Ở BACKGROUND THREAD:
     * Lưu ảnh JPEG với tên file cụ thể trong background thread để không block UI.
     * Sử dụng JpegGenerator để tạo file JPEG và lưu vào bộ nhớ ngoài.
     * Hiển thị thông báo tiến trình và kết quả trên main thread.
     * @param fileName Tên file JPEG cần lưu
     */
    private void performSaveJpeg(String fileName) {
        if (!(imageViewPreview.getDrawable() instanceof BitmapDrawable)) {
            Toast.makeText(this, getString(R.string.no_image_to_save_jpeg), Toast.LENGTH_SHORT).show();
            return;
        }

        Bitmap currentBitmap = ((BitmapDrawable) imageViewPreview.getDrawable()).getBitmap();
        if (currentBitmap == null) {
            Toast.makeText(this, getString(R.string.no_image_to_save_jpeg), Toast.LENGTH_SHORT).show();
            return;
        }

        // Hiển thị thông báo đang lưu
        Toast.makeText(this, "Đang lưu ảnh JPEG...", Toast.LENGTH_SHORT).show();

        // THỰC HIỆN LƯU JPEG TRONG BACKGROUND THREAD:
        executorService.execute(() -> {
            try {
                // SỬ DỤNG JPEG GENERATOR:
                // Tạo file JPEG với tên file cụ thể và lưu vào bộ nhớ ngoài
                Uri jpegUri = jpegGenerator.saveAsJpeg(currentBitmap, fileName);

                // CẬP NHẬT UI TRÊN MAIN THREAD:
                // Hiển thị thông báo thành công và log kết quả
                mainHandler.post(() -> {
                    Toast.makeText(this, getString(R.string.jpeg_saved_success) + fileName, Toast.LENGTH_LONG).show();
                    Log.d(TAG, "JPEG saved successfully: " + fileName);

                    // Có thể chuyển về MainActivity hoặc ở lại
                    // navigateToMainActivity(); // Uncomment nếu muốn chuyển về MainActivity
                });

            } catch (Exception e) {
                // XỬ LÝ LỖI TRÊN MAIN THREAD:
                // Log lỗi và hiển thị thông báo lỗi cho người dùng
                Log.e(TAG, "Lỗi khi lưu JPEG: " + e.getMessage(), e);
                mainHandler.post(() -> {
                    Toast.makeText(this, "Lỗi khi lưu JPEG: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }
    /**
     * LƯU BITMAP VÀO CACHE:
     * Lưu bitmap vào thư mục cache của ứng dụng với tên file tự động.
     * Tạo thư mục cache nếu chưa tồn tại và sử dụng JPEG format với chất lượng 90%.
     * @param bitmap Bitmap cần lưu vào cache
     * @return Uri của file đã lưu hoặc null nếu lỗi
     */
    private Uri saveBitmapToCache(Bitmap bitmap) {
        String fileName = "rotated_temp_" + System.currentTimeMillis() + ".jpeg";
        File cachePath = new File(getCacheDir(), "rotated_images");
        cachePath.mkdirs(); // Tạo thư mục nếu nó chưa tồn tại

        File file = new File(cachePath, fileName);

        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
            fos.flush();
            return Uri.fromFile(file);
        } catch (IOException e) {
            Log.e(TAG, "Error saving bitmap to cache: " + e.getMessage(), e);
            return null;
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error closing FileOutputStream: " + e.getMessage(), e);
                }
            }
        }
    }


    /**
     * TẢI ẢNH VỚI GÓC XOAY HIỆN TẠI:
     * Sử dụng Glide để tải ảnh từ URI với góc xoay được áp dụng.
     * Sử dụng RequestOptions và Rotate transform để xoay ảnh theo rotationAngle.

     */
    private void loadImageWithRotation() {
        // THIẾT LẬP REQUEST OPTIONS VỚI ROTATE TRANSFORM:
        // Tạo transform để xoay ảnh theo góc hiện tại
        RequestOptions requestOptions = new RequestOptions();
        requestOptions = requestOptions.transform(new Rotate(rotationAngle)); // Áp dụng xoay

        // TẢI ẢNH VỚI GLIDE:
        // Sử dụng Glide để tải ảnh từ URI với transform xoay đã áp dụng
        Glide.with(this)
                .load(imageUri)
                .apply(requestOptions) // Áp dụng RequestOptions
                .into(imageViewPreview);
    }

    // Xử lý kết quả trả về từ CropActivity
    // ... (các khai báo và phương thức khác) ...

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SIGNATURE && resultCode == RESULT_OK) {
            if (data != null) {
                Uri mergedImageUri = data.getParcelableExtra("mergedImageUri");
                Uri newSignatureUri = data.getParcelableExtra("newSignatureUri");
                if (newSignatureUri != null) {
                    // Lưu chữ ký mới vào SignatureManager
                    signatureManager.saveSignatureUri(newSignatureUri);
                }
                if (mergedImageUri != null) {
                    Log.d(TAG, "ImagePreviewActivity: Received merged image URI: " + mergedImageUri);
                    this.imageUri = mergedImageUri;
                    loadImageWithRotation();
                    Toast.makeText(this, getString(R.string.image_sign), Toast.LENGTH_SHORT).show();
                }
            }
        }
        // Xử lý kết quả trả về từ PdfGenerationAndPreviewActivity
        if (requestCode == REQUEST_PDF_GEN_PREVIEW && resultCode == RESULT_OK) {
            if (data != null && data.hasExtra("processedImageUri")) {
                String processedImageUriString = data.getStringExtra("processedImageUri");
                Uri processedImageUri = Uri.parse(processedImageUriString);

                // Cập nhật biến imageUri của ImagePreviewActivity với URI mới
                this.imageUri = processedImageUri;

                // Tải lại ảnh đã được xử lý vào ImageView
                loadImageWithRotation();
                Toast.makeText(this, getString(R.string.image_pdf_success), Toast.LENGTH_SHORT).show();
                Log.d(TAG, "Received processed image URI from PDF Generation: " + processedImageUri.toString());
            }
        }
        // Xử lý kết quả trả về từ CropActivity
        if (requestCode == REQUEST_CROP && resultCode == RESULT_OK) {
            if (data != null && data.hasExtra("processedImageUri")) {
                String croppedImageUriString = data.getStringExtra("processedImageUri");
                Uri croppedImageUri = Uri.parse(croppedImageUriString);

                // Cập nhật biến imageUri với ảnh đã được crop
                this.imageUri = croppedImageUri;

                // Reset rotation angle vì ảnh crop mới không cần xoay
                this.rotationAngle = 0;

                // Tải lại ảnh đã được crop vào ImageView
                loadImageWithRotation();
                Toast.makeText(this, "Ảnh đã được cắt thành công", Toast.LENGTH_SHORT).show();
                Log.d(TAG, "ImagePreviewActivity: Received cropped image URI: " + croppedImageUri.toString());
            }
        }
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Giải phóng tài nguyên
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow();
        }
    }
}