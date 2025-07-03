package com.example.camerascanner.activitymain;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
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

        loadPdfThumbnail(file, holder.fileIcon);

        holder.btnOpenFile.setOnClickListener(v -> {
            openPdfFile(file);
        });

        holder.itemView.setOnClickListener(v -> {
            openPdfFile(file);
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
        intent.setDataAndType(fileUri, "application/pdf");
        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        try {
            context.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(context, "Không có ứng dụng nào để mở PDF. " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Tải thumbnail cho file PDF và hiển thị lên ImageView.
     * Sử dụng PdfRenderer (API 21+) để render trang đầu tiên của PDF.
     * @param pdfFile File PDF cần tạo thumbnail.
     * @param imageView ImageView để hiển thị thumbnail.
     */
    private void loadPdfThumbnail(File pdfFile, ImageView imageView) {
        // Hủy bỏ bất kỳ tác vụ tải thumbnail nào đang chờ xử lý cho ImageView này
        if (imageView.getTag() instanceof Future) {
            ((Future<?>) imageView.getTag()).cancel(true);
        }

        // Xóa ảnh cũ để tránh hiển thị ảnh sai trong khi đang tải
        imageView.setImageDrawable(null);

        Future<?> future = thumbnailExecutor.submit(() -> {
            Bitmap thumbnailBitmap = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) { // PdfRenderer yêu cầu API 21+
                ParcelFileDescriptor fileDescriptor = null;
                PdfRenderer pdfRenderer = null;
                PdfRenderer.Page page = null;
                try {
                    fileDescriptor = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY);
                    pdfRenderer = new PdfRenderer(fileDescriptor);
                    if (pdfRenderer.getPageCount() > 0) {
                        page = pdfRenderer.openPage(0); // Render trang đầu tiên

                        // Lấy kích thước mong muốn cho thumbnail từ ImageView (48dp x 48dp trong item_file_entry.xml)
                        int desiredWidth = dpToPx(64);
                        int desiredHeight = dpToPx(64);

                        // Tạo Bitmap với kích thước phù hợp với ImageView
                        thumbnailBitmap = Bitmap.createBitmap(desiredWidth, desiredHeight, Bitmap.Config.ARGB_8888);
                        page.render(thumbnailBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                    } else {
                        Log.e("PdfFileAdapter", "PDF file has no pages: " + pdfFile.getAbsolutePath());
                    }
                } catch (IOException e) {
                    Log.e("PdfFileAdapter", "Error rendering PDF thumbnail for " + pdfFile.getAbsolutePath() + ": " + e.getMessage(), e);
                    e.printStackTrace();
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

            final Bitmap finalThumbnailBitmap = thumbnailBitmap;
            imageView.post(() -> { // Cập nhật UI trên luồng chính
                if (finalThumbnailBitmap != null) {
                    imageView.setImageBitmap(finalThumbnailBitmap);
                } else {
                    // Nếu lỗi hoặc không tạo được thumbnail, ImageView sẽ trống.
                    // Icon mặc định được đặt trong item_file_entry.xml sẽ được hiển thị
                    imageView.setImageDrawable(null);
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
        ImageView fileIcon, btnOpenFile;

        public PdfFileViewHolder(@NonNull View itemView) {
            super(itemView);
            tvFileName = itemView.findViewById(R.id.tvFileName);
            tvFileDate = itemView.findViewById(R.id.tvFileDate);
            tvFileSize = itemView.findViewById(R.id.tvFileSize);
            fileIcon = itemView.findViewById(R.id.ivPdfIcon);
            btnOpenFile = itemView.findViewById(R.id.btnOpenFile);
        }
    }
}