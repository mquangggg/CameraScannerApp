package com.example.camerascanner;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PdfFileAdapter extends RecyclerView.Adapter<PdfFileAdapter.PdfFileViewHolder> {

    private final Context context;
    private List<File> pdfFiles;

    public PdfFileAdapter(Context context, List<File> pdfFiles) {
        this.context = context;
        this.pdfFiles = pdfFiles;
    }

    // Phương thức để cập nhật danh sách tệp và thông báo cho Adapter
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

        // Định dạng ngày
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
        holder.tvFileDate.setText("Ngày: " + sdf.format(new Date(file.lastModified())));

        // Định dạng kích thước file
        holder.tvFileSize.setText("Kích thước: " + formatFileSize(file.length()));

        // Xử lý sự kiện click để mở file
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
        Uri fileUri = Uri.fromFile(file); // Chỉ dùng cho API < 29
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            // Đối với Android N (API 24) trở lên, cần sử dụng FileProvider
            // để tránh FileUriExposedException.
            // Bạn cần cấu hình FileProvider trong AndroidManifest.xml và tạo file_paths.xml
            try {
                fileUri = androidx.core.content.FileProvider.getUriForFile(
                        context,
                        context.getApplicationContext().getPackageName() + ".provider", // Thay bằng authority của bạn
                        file
                );
            } catch (IllegalArgumentException e) {
                Toast.makeText(context, "Không thể mở file: " + e.getMessage(), Toast.LENGTH_LONG).show();
                return;
            }
        }

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(fileUri, "application/pdf");
        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); // Cấp quyền đọc tạm thời

        try {
            context.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(context, "Không có ứng dụng nào để mở PDF. " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    public static class PdfFileViewHolder extends RecyclerView.ViewHolder {
        TextView tvFileName, tvFileDate, tvFileSize;
        ImageView btnOpenFile;

        public PdfFileViewHolder(@NonNull View itemView) {
            super(itemView);
            tvFileName = itemView.findViewById(R.id.tvFileName);
            tvFileDate = itemView.findViewById(R.id.tvFileDate);
            tvFileSize = itemView.findViewById(R.id.tvFileSize);
            btnOpenFile = itemView.findViewById(R.id.btnOpenFile);
        }
    }
}