package com.example.camerascanner.activitymain;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory; // Thêm import này

import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.example.camerascanner.R;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class PdfFileAdapter extends RecyclerView.Adapter<PdfFileAdapter.PdfFileViewHolder> {

    private final Context context;
    private List<File> pdfFiles;
    private final ExecutorService thumbnailExecutor;

    public PdfFileAdapter(Context context, List<File> pdfFiles) {
        this.context = context;
        this.pdfFiles = pdfFiles;
        this.thumbnailExecutor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    }

    public void updateData(List<File> newFiles) {
        this.pdfFiles = newFiles;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public PdfFileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_file_entry, parent, false);
        return new PdfFileViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PdfFileViewHolder holder, int position) {
        File file = pdfFiles.get(position);
        holder.tvFileName.setText(file.getName());

        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
        holder.tvFileDate.setText("Ngày: " + sdf.format(new Date(file.lastModified())));

        holder.tvFileSize.setText("Kích thước: " + formatFileSize(file.length()));

        loadThumbnail(file, holder.fileIcon);


        holder.itemView.setOnClickListener(v -> {
            openPdfFile(file);
        });

        // Thêm chức năng xóa file PDF
        holder.btnDeleteFile.setOnClickListener(v -> {
            showDeleteConfirmationDialog(file, position);
        });
    }

    @Override
    public int getItemCount() {
        return pdfFiles.size();
    }

    private String formatFileSize(long size) {
        if (size <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return new DecimalFormat("#,##0.#").format(size / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }

    // Trong PdfFileAdapter.java

    private void openPdfFile(File file) {
        Uri fileUri;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                fileUri = FileProvider.getUriForFile(
                        context,
                        context.getApplicationContext().getPackageName() + ".provider",
                        file
                );
            } catch (IllegalArgumentException e) {
                Toast.makeText(context, "Không thể mở file: " + e.getMessage(), Toast.LENGTH_LONG).show();
                return;
            }
        } else {
            fileUri = Uri.fromFile(file);
        }

        Intent intent = new Intent(Intent.ACTION_VIEW);
        String mimeType;

        // Xác định loại MIME dựa trên phần mở rộng của tệp
        if (file.getName().toLowerCase(Locale.getDefault()).endsWith(".pdf")) {
            mimeType = "application/pdf";
        } else if (file.getName().toLowerCase(Locale.getDefault()).endsWith(".jpeg") ||
                file.getName().toLowerCase(Locale.getDefault()).endsWith(".jpg")) {
            mimeType = "image/jpeg"; // Đặt loại MIME cho JPEG
        } else {
            Toast.makeText(context, "Định dạng file không được hỗ trợ để mở.", Toast.LENGTH_LONG).show();
            return;
        }

        intent.setDataAndType(fileUri, mimeType);
        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        try {
            context.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(context, "Không có ứng dụng nào để mở file này. " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Hiển thị dialog xác nhận xóa file PDF
     * @param file File PDF cần xóa
     * @param position Vị trí của file trong danh sách
     */
    private void showDeleteConfirmationDialog(File file, int position) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Xác nhận xóa");
        builder.setMessage("Bạn có chắc chắn muốn xóa file PDF \"" + file.getName() + "\" không?");
        builder.setIcon(R.drawable.ic_delete_white);

        builder.setPositiveButton("Xóa", (dialog, which) -> {
            deletePdfFile(file, position);
        });

        builder.setNegativeButton("Hủy", (dialog, which) -> {
            dialog.dismiss();
        });

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    /**
     * Xóa file PDF khỏi hệ thống và cập nhật danh sách
     * @param file File PDF cần xóa
     * @param position Vị trí của file trong danh sách
     */
    private void deletePdfFile(File file, int position) {
        try {
            if (file.exists() && file.delete()) {
                // Xóa file thành công, cập nhật danh sách
                pdfFiles.remove(position);
                notifyItemRemoved(position);
                notifyItemRangeChanged(position, pdfFiles.size());

                // Cập nhật danh sách gốc trong MainActivity nếu cần
                if (context instanceof MainActivity) {
                    ((MainActivity) context).updateOriginalPdfList(file);
                }

                Toast.makeText(context, "Đã xóa file PDF thành công!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(context, "Không thể xóa file PDF. File có thể không tồn tại.", Toast.LENGTH_SHORT).show();
            }
        } catch (SecurityException e) {
            Toast.makeText(context, "Không có quyền xóa file: " + e.getMessage(), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(context, "Lỗi khi xóa file: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Tải thumbnail cho file PDF và hiển thị lên ImageView.
     * Sử dụng PdfRenderer (API 21+) để render trang đầu tiên của PDF.
     * @param file File PDF cần tạo thumbnail.
     * @param imageView ImageView để hiển thị thumbnail.
     */
    private void loadThumbnail(File file, ImageView imageView) {
        // Hủy bỏ bất kỳ tác vụ tải thumbnail nào đang chờ xử lý cho ImageView này
        if (imageView.getTag() instanceof Future) {
            ((Future<?>) imageView.getTag()).cancel(true);
        }

        // Xóa ảnh cũ để tránh hiển thị ảnh sai trong khi đang tải
        Future<?> future = thumbnailExecutor.submit(() -> {
            Bitmap thumbnailBitmap = null;
            String fileName = file.getName().toLowerCase(Locale.getDefault());

            if (fileName.endsWith(".pdf")) { // Xử lý PDF
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) { // PdfRenderer yêu cầu API 21+
                    ParcelFileDescriptor fileDescriptor = null;
                    PdfRenderer pdfRenderer = null;
                    PdfRenderer.Page page = null;
                    try {
                        fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
                        pdfRenderer = new PdfRenderer(fileDescriptor);
                        if (pdfRenderer.getPageCount() > 0) {
                            page = pdfRenderer.openPage(0); // Render trang đầu tiên

                            int desiredWidth = dpToPx(64);
                            int desiredHeight = dpToPx(64);

                            thumbnailBitmap = Bitmap.createBitmap(desiredWidth, desiredHeight, Bitmap.Config.ARGB_8888);
                            page.render(thumbnailBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                        } else {
                            Log.e("PdfFileAdapter", "PDF file has no pages: " + file.getAbsolutePath());
                        }
                    } catch (IOException e) {
                        Log.e("PdfFileAdapter", "Error rendering PDF thumbnail for " + file.getAbsolutePath() + ": " + e.getMessage(), e);
                    } finally {
                        if (page != null) {
                            page.close();
                        }
                        if (pdfRenderer != null) {
                            pdfRenderer.close();
                        }
                        if (fileDescriptor != null) {
                            try {
                                fileDescriptor.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                } else {
                    Log.w("PdfFileAdapter", "PdfRenderer not available below API 21. Current API: " + Build.VERSION.SDK_INT);
                }
            } else if (fileName.endsWith(".jpeg") || fileName.endsWith(".jpg")) { // Xử lý JPEG
                try {
                    // Giải mã hình ảnh JPEG
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inJustDecodeBounds = true; // Chỉ lấy kích thước mà không tải bitmap vào bộ nhớ
                    BitmapFactory.decodeFile(file.getAbsolutePath(), options);

                    // Tính toán tỷ lệ mẫu để giảm kích thước hình ảnh
                    int photoWidth = options.outWidth;
                    int photoHeight = options.outHeight;
                    int desiredWidth = dpToPx(64);
                    int desiredHeight = dpToPx(64);

                    int scaleFactor = Math.min(photoWidth / desiredWidth, photoHeight / desiredHeight);
                    if (scaleFactor <= 0) scaleFactor = 1; // Đảm bảo scaleFactor ít nhất là 1

                    options.inJustDecodeBounds = false; // Bây giờ tải bitmap vào bộ nhớ
                    options.inSampleSize = scaleFactor;
                    options.inPreferredConfig = Bitmap.Config.RGB_565; // Cấu hình bitmap hiệu quả hơn

                    thumbnailBitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
                    if (thumbnailBitmap != null) {
                        // Đảm bảo bitmap có kích thước mong muốn (cắt nếu cần)
                        thumbnailBitmap = Bitmap.createScaledBitmap(thumbnailBitmap, desiredWidth, desiredHeight, true);
                    }
                } catch (Exception e) {
                    Log.e("PdfFileAdapter", "Error loading JPEG thumbnail for " + file.getAbsolutePath() + ": " + e.getMessage(), e);
                }
            }

            final Bitmap finalThumbnailBitmap = thumbnailBitmap;
            imageView.post(() -> { // Cập nhật UI trên luồng chính
                if (finalThumbnailBitmap != null) {
                    imageView.setImageBitmap(finalThumbnailBitmap);
                } else {
                    // Nếu lỗi hoặc không tạo được thumbnail, hiển thị icon mặc định
                    // Bạn có thể đặt một icon mặc định ở đây nếu muốn
                    // imageView.setImageResource(R.drawable.ic_default_file);
                    imageView.setImageDrawable(null); // Giữ nguyên như cũ nếu bạn muốn icon mặc định từ XML
                }
            });
        });
        imageView.setTag(future);
    }

    // Phương thức trợ giúp để chuyển đổi dp sang pixel
    private int dpToPx(int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density);
    }

    public static class PdfFileViewHolder extends RecyclerView.ViewHolder {
        TextView tvFileName, tvFileDate, tvFileSize;
        ImageView fileIcon, btnDeleteFile;

        public PdfFileViewHolder(@NonNull View itemView) {
            super(itemView);
            tvFileName = itemView.findViewById(R.id.tvFileName);
            tvFileDate = itemView.findViewById(R.id.tvFileDate);
            tvFileSize = itemView.findViewById(R.id.tvFileSize);
            fileIcon = itemView.findViewById(R.id.ivPdfIcon);
            btnDeleteFile = itemView.findViewById(R.id.btnDeleteFile);
        }
    }
}