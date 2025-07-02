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
import androidx.core.content.FileProvider; // Vẫn cần FileProvider cho Android N+
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date; // Sử dụng Date như trong PdfFileAdapter
import java.util.List;
import java.util.Locale;

public class WordFileAdapter extends RecyclerView.Adapter<WordFileAdapter.FileViewHolder> { // Đổi tên ViewHolder thành FileViewHolder cho nhất quán

    private final Context context; // Dùng final như trong PdfFileAdapter
    private List<File> fileList; // Đặt tên là fileList để rõ ràng hơn

    public WordFileAdapter(Context context, List<File> fileList) {
        this.context = context;
        this.fileList = fileList;
    }

    // Phương thức để cập nhật danh sách tệp và thông báo cho Adapter (giống PdfFileAdapter)
    public void updateData(List<File> newFiles) {
        this.fileList = newFiles; // Gán trực tiếp list mới
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public FileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Sử dụng layout item_file_entry.xml
        View view = LayoutInflater.from(context).inflate(R.layout.item_file_entry, parent, false);
        return new FileViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FileViewHolder holder, int position) {
        File file = fileList.get(position); // Sử dụng fileList

        holder.fileName.setText(file.getName()); // Sử dụng fileName

        // Định dạng ngày sửa đổi (giống PdfFileAdapter)
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
        holder.fileDate.setText("Ngày: " + sdf.format(new Date(file.lastModified())));

        // Định dạng và hiển thị kích thước tệp (giống PdfFileAdapter)
        holder.fileSize.setText("Kích thước: " + formatFileSize(file.length()));

        // --- BỎ ĐI CÁC DÒNG THIẾT LẬP ICON VÀ MÀU SẮC CỤ THỂ ---
        // Giờ đây, icon sẽ được lấy từ thuộc tính android:src và app:tint trong item_file_entry.xml
        // (ví dụ: @drawable/ic_home_white và @color/green_accent)

        // Xử lý sự kiện click trên nút btnOpenFile
        holder.btnOpenFile.setOnClickListener(v -> {
            openWordFile(file); // Gọi phương thức mở tệp văn bản
        });

        // Xử lý sự kiện click trên toàn bộ item (giống PdfFileAdapter)
        holder.itemView.setOnClickListener(v -> {
            openWordFile(file); // Gọi phương thức mở tệp văn bản
        });
    }

    @Override
    public int getItemCount() {
        return fileList.size(); // Sử dụng fileList
    }

    // Phương thức trợ giúp để định dạng kích thước tệp (giống PdfFileAdapter)
    private String formatFileSize(long size) {
        if (size <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return new DecimalFormat("#,##0.#").format(size / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }

    // Phương thức để mở tệp văn bản (tùy chỉnh từ openPdfFile)
    private void openWordFile(File file) {
        Uri fileUri;
        // Kiểm tra phiên bản Android để sử dụng FileProvider
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            try {
                fileUri = androidx.core.content.FileProvider.getUriForFile(
                        context,
                        context.getApplicationContext().getPackageName() + ".provider",
                        file
                );
            } catch (IllegalArgumentException e) {
                Toast.makeText(context, "Không thể mở tệp: " + e.getMessage(), Toast.LENGTH_LONG).show();
                return;
            }
        } else {
            fileUri = Uri.fromFile(file); // Dùng cho API dưới 24
        }

        Intent intent = new Intent(Intent.ACTION_VIEW);
        String mimeType;
        String fileNameLower = file.getName().toLowerCase(Locale.ROOT);
        if (fileNameLower.endsWith(".docx")) {
            mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        } else if (fileNameLower.endsWith(".txt")) {
            mimeType = "text/plain";
        } else {
            mimeType = "*/*"; // Mặc định cho các loại tệp khác nếu có
        }

        intent.setDataAndType(fileUri, mimeType);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); // Cấp quyền đọc tạm thời

        try {
            context.startActivity(Intent.createChooser(intent, "Mở tệp với"));
        } catch (Exception e) {
            Toast.makeText(context, "Không có ứng dụng nào có thể mở tệp này. " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace(); // In lỗi ra logcat để gỡ lỗi
        }
    }

    // ViewHolder (giống PdfFileAdapter nhưng với tên biến đã được chỉnh sửa cho rõ ràng)
    public static class FileViewHolder extends RecyclerView.ViewHolder {
        TextView fileName, fileDate, fileSize;
        ImageView fileIcon, btnOpenFile; // fileIcon ánh xạ tới ivPdfIcon

        public FileViewHolder(@NonNull View itemView) {
            super(itemView);
            fileName = itemView.findViewById(R.id.tvFileName);
            fileDate = itemView.findViewById(R.id.tvFileDate);
            fileSize = itemView.findViewById(R.id.tvFileSize);
            fileIcon = itemView.findViewById(R.id.ivPdfIcon); // Ánh xạ tới ivPdfIcon từ layout
            btnOpenFile = itemView.findViewById(R.id.btnOpenFile);
        }
    }
}