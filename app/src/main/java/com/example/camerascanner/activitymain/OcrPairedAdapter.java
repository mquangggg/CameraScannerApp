package com.example.camerascanner.activitymain;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
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
import java.text.DecimalFormat;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class OcrPairedAdapter extends RecyclerView.Adapter<OcrPairedAdapter.OcrPairedViewHolder> {

    private final Context context;
    private List<OcrPairedItem> ocrPairedList;
    private final ExecutorService thumbnailExecutor; // Để tải thumbnail trong nền

    public OcrPairedAdapter(Context context, List<OcrPairedItem> ocrPairedList) {
        this.context = context;
        this.ocrPairedList = ocrPairedList;
        this.thumbnailExecutor = Executors.newFixedThreadPool(2); // Số luồng có thể điều chỉnh
    }

    public void updateData(List<OcrPairedItem> newOcrPairedList) {
        this.ocrPairedList = newOcrPairedList;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public OcrPairedViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_ocr_paired_entry, parent, false);
        return new OcrPairedViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull OcrPairedViewHolder holder, int position) {
        OcrPairedItem item = ocrPairedList.get(position);

        holder.tvOcrImageFileName.setText(item.getImageFile().getName());
        holder.tvOcrTextFileName.setText(item.getTextFile().getName());
        holder.tvOcrDate.setText("Ngày: " + item.getFormattedDate());
        holder.tvOcrTotalSize.setText("Kích thước: " + formatFileSize(item.getTotalSize()));

        // Tải thumbnail ảnh trong nền
        if (item.getImageFile() != null && item.getImageFile().exists()) {
            loadThumbnailAsync(item.getImageFile(), holder.ivOcrThumbnail);
        } else {
            // Nếu không có ảnh, ivOcrThumbnail sẽ trống (sử dụng icon mặc định từ XML hoặc không hiển thị gì)
            holder.ivOcrThumbnail.setImageDrawable(null); // Đảm bảo không hiển thị ảnh cũ
        }

        holder.itemView.setOnClickListener(v -> openOcrDetailActivity(item)); // Đã thay đổi
        holder.btnOpenOcrItem.setOnClickListener(v -> openOcrDetailActivity(item)); // Đã thay đổi
    }

    @Override
    public int getItemCount() {
        return ocrPairedList.size();
    }

    private void loadThumbnailAsync(File imageFile, ImageView imageView) {
        // Hủy bỏ bất kỳ tác vụ tải thumbnail nào đang chạy cho ImageView này
        if (imageView.getTag() instanceof Future) {
            ((Future<?>) imageView.getTag()).cancel(true);
        }

        Future<?> future = thumbnailExecutor.submit(() -> {
            Bitmap thumbnail = null;
            try {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(imageFile.getAbsolutePath(), options);

                options.inSampleSize = calculateInSampleSize(options, 128, 128);
                options.inJustDecodeBounds = false;
                thumbnail = BitmapFactory.decodeFile(imageFile.getAbsolutePath(), options);

            } catch (Exception e) {
                e.printStackTrace();
            }

            final Bitmap finalThumbnail = thumbnail;
            imageView.post(() -> { // Cập nhật UI trên Main Thread
                if (finalThumbnail != null) {
                    imageView.setImageBitmap(finalThumbnail);
                } else {
                    // Nếu lỗi hoặc không có thumbnail, imageView sẽ trống.
                    // Nó sẽ hiển thị icon mặc định từ XML (ic_home_white) hoặc không hiển thị gì nếu src được bỏ qua.
                    // Không sử dụng R.drawable ở đây.
                    imageView.setImageDrawable(null); // Đảm bảo không hiển thị ảnh cũ hoặc bất kỳ drawable nào
                }
            });
        });
        imageView.setTag(future);
    }

    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    private String formatFileSize(long size) {
        if (size <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return new DecimalFormat("#,##0.#").format(size / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }

    // Phương thức mới để mở OCRDetailActivity
    private void openOcrDetailActivity(OcrPairedItem item) { // Đã đổi tên
        File imageFile = item.getImageFile();
        File textFile = item.getTextFile();

        if (imageFile == null || !imageFile.exists()) {
            Toast.makeText(context, "Ảnh OCR không tìm thấy.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (textFile == null || !textFile.exists()) {
            Toast.makeText(context, "Văn bản OCR không tìm thấy.", Toast.LENGTH_SHORT).show();
            return;
        }

        Uri imageUri = null;
        Uri textUri = null;

        try {
            imageUri = FileProvider.getUriForFile(
                    context,
                    context.getApplicationContext().getPackageName() + ".provider",
                    imageFile
            );
            textUri = FileProvider.getUriForFile(
                    context,
                    context.getApplicationContext().getPackageName() + ".provider",
                    textFile
            );
        } catch (IllegalArgumentException e) {
            Toast.makeText(context, "Không thể lấy Uri cho tệp: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }

        Intent intent = new Intent(context, OCRDetailActivity.class);
        intent.putExtra(OCRDetailActivity.EXTRA_IMAGE_URI, imageUri);
        intent.putExtra(OCRDetailActivity.EXTRA_TEXT_URI, textUri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        try {
            context.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(context, "Không thể mở chi tiết OCR: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    public static class OcrPairedViewHolder extends RecyclerView.ViewHolder {
        ImageView ivOcrThumbnail;
        TextView tvOcrImageFileName, tvOcrTextFileName, tvOcrDate, tvOcrTotalSize;
        ImageView btnOpenOcrItem;

        public OcrPairedViewHolder(@NonNull View itemView) {
            super(itemView);
            ivOcrThumbnail = itemView.findViewById(R.id.ivOcrThumbnail);
            tvOcrImageFileName = itemView.findViewById(R.id.tvOcrImageFileName);
            tvOcrTextFileName = itemView.findViewById(R.id.tvOcrTextFileName);
            tvOcrDate = itemView.findViewById(R.id.tvOcrDate);
            tvOcrTotalSize = itemView.findViewById(R.id.tvOcrTotalSize);
            btnOpenOcrItem = itemView.findViewById(R.id.btnOpenOcrItem);
        }
    }
}