package com.example.camerascanner.activitymain;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.example.camerascanner.R;

import java.io.File;
import java.text.DecimalFormat;
import java.util.ArrayList;
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
        holder.tvOcrDate.setText(context.getString(R.string.day_create) + item.getFormattedDate());
        holder.tvOcrTotalSize.setText(context.getString(R.string.size) + formatFileSize(item.getTotalSize()));

        // Tải thumbnail ảnh trong nền
        if (item.getImageFile() != null && item.getImageFile().exists()) {
            loadThumbnailAsync(item.getImageFile(), holder.ivOcrThumbnail);
        } else {
            // Nếu không có ảnh, ivOcrThumbnail sẽ trống (sử dụng icon mặc định từ XML hoặc không hiển thị gì)
            holder.ivOcrThumbnail.setImageDrawable(null); // Đảm bảo không hiển thị ảnh cũ
        }

        holder.itemView.setOnClickListener(v -> openOcrDetailActivity(item));
        holder.btnShareOcrItem.setOnClickListener(v->{
            shareOcrPair(item);
        });
        // Thêm chức năng sửa tên file OCR
        holder.btnEditOcrItem.setOnClickListener(v -> {
            showEditOcrFileNamesDialog(item, position);
        });
        // Thêm chức năng xóa cặp OCR
        holder.btnDeleteOcrItem.setOnClickListener(v -> {
            showDeleteConfirmationDialog(item, position);
        });
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

    /**
     * Hiển thị dialog xác nhận xóa cặp OCR
     * @param item Cặp OCR cần xóa
     * @param position Vị trí của cặp OCR trong danh sách
     */
    private void showDeleteConfirmationDialog(OcrPairedItem item, int position) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(context.getString(R.string.confirm_delete));
        builder.setMessage(context.getString(R.string.confirm_delete_ocr_pair,
                item.getImageFile().getName(),
                item.getTextFile().getName()));  builder.setIcon(R.drawable.ic_delete_white);

        builder.setPositiveButton(context.getText(R.string.delete), (dialog, which) -> {
            deleteOcrPair(item, position);
        });

        builder.setNegativeButton(context.getText(R.string.cancel), (dialog, which) -> {
            dialog.dismiss();
        });

        AlertDialog dialog = builder.create();
        dialog.show();
    }
    /**
     * Chia sẻ cặp OCR (ảnh và văn bản)
     * @param item Cặp OCR cần chia sẻ
     */
    private void shareOcrPair(OcrPairedItem item) {
        File imageFile = item.getImageFile();
        File textFile = item.getTextFile();

        if (!imageFile.exists() || !textFile.exists()) {
            Toast.makeText(context, "Một hoặc cả hai file không tồn tại", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            // Tạo URI cho cả hai file
            Uri imageUri = FileProvider.getUriForFile(
                    context,
                    context.getApplicationContext().getPackageName() + ".provider",
                    imageFile
            );
            Uri textUri = FileProvider.getUriForFile(
                    context,
                    context.getApplicationContext().getPackageName() + ".provider",
                    textFile
            );

            // Tạo danh sách URI để chia sẻ nhiều file
            ArrayList<Uri> uris = new ArrayList<>();
            uris.add(imageUri);
            uris.add(textUri);

            Intent shareIntent = new Intent(Intent.ACTION_SEND_MULTIPLE);
            shareIntent.setType("*/*"); // Cho phép chia sẻ nhiều loại file
            shareIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Chia sẻ OCR: " + imageFile.getName());
            shareIntent.putExtra(Intent.EXTRA_TEXT, "Chia sẻ cặp OCR từ Camera Scanner\n" +
                    "Ảnh: " + imageFile.getName() + "\n" +
                    "Văn bản: " + textFile.getName());
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            Intent chooserIntent = Intent.createChooser(shareIntent, "Chia sẻ cặp OCR qua");
            context.startActivity(chooserIntent);

        } catch (Exception e) {
            Toast.makeText(context, "Không thể chia sẻ cặp OCR: " + e.getMessage(), Toast.LENGTH_LONG).show();
            Log.e("OcrPairedAdapter", "Error sharing OCR pair", e);
        }
    }
    /**
     * Hiển thị dialog để sửa tên cả 2 file OCR
     * @param item Cặp OCR cần sửa tên
     * @param position Vị trí trong danh sách
     */
    private void showEditOcrFileNamesDialog(OcrPairedItem item, int position) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(context.getString(R.string.edit_ocr_file_names));

        // Tạo layout cho 2 EditText
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 20, 50, 20);

        // EditText cho file ảnh
        TextView tvImageLabel = new TextView(context);
        tvImageLabel.setText(context.getString(R.string.image_file_name));
        tvImageLabel.setTextSize(16);
        tvImageLabel.setPadding(0, 0, 0, 10);
        layout.addView(tvImageLabel);

        final EditText editImageName = new EditText(context);
        editImageName.setInputType(InputType.TYPE_CLASS_TEXT);
        String imageName = item.getImageFile().getName();
        String imageNameWithoutExt = imageName.contains(".") ?
                imageName.substring(0, imageName.lastIndexOf(".")) : imageName;
        editImageName.setText(imageNameWithoutExt);
        editImageName.setSelection(imageNameWithoutExt.length());
        layout.addView(editImageName);

        // Khoảng cách
        View spacer = new View(context);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 30));
        layout.addView(spacer);

        // EditText cho file text
        TextView tvTextLabel = new TextView(context);
        tvTextLabel.setText(context.getString(R.string.text_file_name));
        tvTextLabel.setTextSize(16);
        tvTextLabel.setPadding(0, 0, 0, 10);
        layout.addView(tvTextLabel);

        final EditText editTextName = new EditText(context);
        editTextName.setInputType(InputType.TYPE_CLASS_TEXT);
        String textName = item.getTextFile().getName();
        String textNameWithoutExt = textName.contains(".") ?
                textName.substring(0, textName.lastIndexOf(".")) : textName;
        editTextName.setText(textNameWithoutExt);
        editTextName.setSelection(textNameWithoutExt.length());
        layout.addView(editTextName);

        builder.setView(layout);
        builder.setIcon(R.drawable.ic_edit_white);

        builder.setPositiveButton(context.getString(R.string.save), (dialog, which) -> {
            String newImageName = editImageName.getText().toString().trim();
            String newTextName = editTextName.getText().toString().trim();

            if (!newImageName.isEmpty() && !newTextName.isEmpty()) {
                renameOcrPair(item, newImageName, newTextName, position);
            } else {
                Toast.makeText(context, context.getString(R.string.error_empty_file_name), Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton(context.getString(R.string.cancel), (dialog, which) -> {
            dialog.dismiss();
        });

        AlertDialog dialog = builder.create();
        dialog.show();

        // Hiển thị bàn phím tự động
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
    }

    /**
     * Đổi tên cặp file OCR
     * @param item Cặp OCR cần đổi tên
     * @param newImageName Tên mới cho file ảnh
     * @param newTextName Tên mới cho file text
     * @param position Vị trí trong danh sách
     */
    private void renameOcrPair(OcrPairedItem item, String newImageName, String newTextName, int position) {
        try {
            File imageFile = item.getImageFile();
            File textFile = item.getTextFile();

            // Lấy extension của các file
            String imageExt = getFileExtension(imageFile.getName());
            String textExt = getFileExtension(textFile.getName());

            String fullNewImageName = newImageName + imageExt;
            String fullNewTextName = newTextName + textExt;

            // Kiểm tra tên file hợp lệ
            if (!isValidFileName(fullNewImageName) || !isValidFileName(fullNewTextName)) {
                Toast.makeText(context, context.getString(R.string.error_invalid_file_name), Toast.LENGTH_SHORT).show();
                return;
            }

            // Tạo file mới
            File newImageFile = new File(imageFile.getParent(), fullNewImageName);
            File newTextFile = new File(textFile.getParent(), fullNewTextName);

            // Kiểm tra file đã tồn tại
            if (newImageFile.exists() || newTextFile.exists()) {
                Toast.makeText(context, context.getString(R.string.error_file_already_exists), Toast.LENGTH_SHORT).show();
                return;
            }

            // Đổi tên cả 2 file
            boolean imageRenamed = imageFile.renameTo(newImageFile);
            boolean textRenamed = textFile.renameTo(newTextFile);

            if (imageRenamed && textRenamed) {
                // Tạo OcrPairedItem mới với file đã đổi tên, giữ nguyên timestamp gốc
                OcrPairedItem newItem = item.createRenamedItem(newImageFile, newTextFile);
                ocrPairedList.set(position, newItem);
                notifyItemChanged(position);

                // Cập nhật danh sách gốc trong MainActivity nếu cần
                if (context instanceof MainActivity) {
                    ((MainActivity) context).updateOcrItemInOriginalList(item, newItem);
                }

                Toast.makeText(context, context.getString(R.string.ocr_files_renamed_success), Toast.LENGTH_SHORT).show();
            } else {
                // Nếu chỉ đổi tên được 1 file, cần rollback
                if (imageRenamed && !textRenamed) {
                    newImageFile.renameTo(imageFile); // Rollback
                } else if (!imageRenamed && textRenamed) {
                    newTextFile.renameTo(textFile); // Rollback
                }
                Toast.makeText(context, context.getString(R.string.error_rename_file_failed), Toast.LENGTH_SHORT).show();
            }

        } catch (SecurityException e) {
            Toast.makeText(context, context.getString(R.string.error_rename_permission_denied) + e.getMessage(), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(context, context.getString(R.string.error_renaming_file) + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Lấy phần mở rộng của file
     * @param fileName Tên file
     * @return Phần mở rộng (bao gồm dấu chấm)
     */
    private String getFileExtension(String fileName) {
        if (fileName.contains(".")) {
            return fileName.substring(fileName.lastIndexOf("."));
        }
        return "";
    }

    /**
     * Kiểm tra tên file có hợp lệ không
     * @param fileName Tên file cần kiểm tra
     * @return true nếu hợp lệ, false nếu không
     */
    private boolean isValidFileName(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return false;
        }

        // Kiểm tra các ký tự không hợp lệ trong tên file
        String invalidChars = "\\/:*?\"<>|";
        for (char c : invalidChars.toCharArray()) {
            if (fileName.contains(String.valueOf(c))) {
                return false;
            }
        }

        // Kiểm tra độ dài tên file
        if (fileName.length() > 200) {
            return false;
        }

        return true;
    }

    /**
     * Xóa cặp OCR (cả ảnh và văn bản) khỏi hệ thống và cập nhật danh sách
     * @param item Cặp OCR cần xóa
     * @param position Vị trí của cặp OCR trong danh sách
     */
    private void deleteOcrPair(OcrPairedItem item, int position) {
        boolean imageDeleted = false;
        boolean textDeleted = false;
        String errorMessage = "";

        try {
            // Xóa file ảnh
            File imageFile = item.getImageFile();
            if (imageFile != null && imageFile.exists()) {
                imageDeleted = imageFile.delete();
                if (!imageDeleted) {
                    errorMessage += context.getString(R.string.error_delete_image_file);
                }
            } else {
                imageDeleted = true; // File không tồn tại, coi như đã xóa
            }

            // Xóa file văn bản
            File textFile = item.getTextFile();
            if (textFile != null && textFile.exists()) {
                textDeleted = textFile.delete();
                if (!textDeleted) {
                    errorMessage += context.getString(R.string.error_delete_text_file);
                }
            } else {
                textDeleted = true; // File không tồn tại, coi như đã xóa
            }

            // Kiểm tra kết quả xóa
            if (imageDeleted && textDeleted) {
                // Xóa thành công, cập nhật danh sách
                ocrPairedList.remove(position);
                notifyItemRemoved(position);
                notifyItemRangeChanged(position, ocrPairedList.size());

                // Cập nhật danh sách gốc trong MainActivity nếu cần
                if (context instanceof MainActivity) {
                    ((MainActivity) context).updateOriginalOcrList(item);
                }

                Toast.makeText(context, context.getString(R.string.ocr_pair_delete_success), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(context, context.getString(R.string.error_deleting_file) + errorMessage, Toast.LENGTH_LONG).show();
            }

        } catch (SecurityException e) {
            Toast.makeText(context, context.getString(R.string.error_delete_permission_denied) + e.getMessage(), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(context, context.getString(R.string.error_deleting_ocr_pair)+ e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // Phương thức mới để mở OCRDetailActivity
    private void openOcrDetailActivity(OcrPairedItem item) {
        File imageFile = item.getImageFile();
        File textFile = item.getTextFile();

        if (imageFile == null || !imageFile.exists()) {
            Toast.makeText(context, context.getString(R.string.ocr_image_not_found), Toast.LENGTH_SHORT).show();
            return;
        }
        if (textFile == null || !textFile.exists()) {
            Toast.makeText(context, context.getString(R.string.ocr_text_not_found), Toast.LENGTH_SHORT).show();
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
            Toast.makeText(context, context.getString(R.string.error_get_uri_for_file) + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }

        Intent intent = new Intent(context, OCRDetailActivity.class);
        intent.putExtra(OCRDetailActivity.EXTRA_IMAGE_URI, imageUri);
        intent.putExtra(OCRDetailActivity.EXTRA_TEXT_URI, textUri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        try {
            context.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(context, context.getString(R.string.error_open_ocr_details) + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    public static class OcrPairedViewHolder extends RecyclerView.ViewHolder {
        ImageView ivOcrThumbnail;
        TextView tvOcrImageFileName, tvOcrTextFileName, tvOcrDate, tvOcrTotalSize;
        ImageView btnDeleteOcrItem,btnShareOcrItem,btnEditOcrItem;

        public OcrPairedViewHolder(@NonNull View itemView) {
            super(itemView);
            ivOcrThumbnail = itemView.findViewById(R.id.ivOcrThumbnail);
            tvOcrImageFileName = itemView.findViewById(R.id.tvOcrImageFileName);
            tvOcrTextFileName = itemView.findViewById(R.id.tvOcrTextFileName);
            tvOcrDate = itemView.findViewById(R.id.tvOcrDate);
            tvOcrTotalSize = itemView.findViewById(R.id.tvOcrTotalSize);
            btnDeleteOcrItem = itemView.findViewById(R.id.btnDeleteOcrItem);
            btnShareOcrItem = itemView.findViewById(R.id.btnShareOcrItem);
            btnEditOcrItem = itemView.findViewById(R.id.btnEditOcrItem);
        }
    }
}