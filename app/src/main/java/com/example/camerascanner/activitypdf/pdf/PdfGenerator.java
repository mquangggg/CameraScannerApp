package com.example.camerascanner.activitypdf.pdf;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

public class PdfGenerator {
    private static final String TAG = "PdfGenerator"; // Tag để sử dụng trong Logcat.
    private static final int PAGE_WIDTH = 792; // Chiều rộng mặc định của trang PDF (điểm).
    private static final int PAGE_HEIGHT = 1120; // Chiều cao mặc định của trang PDF (điểm).

    private Context context; // Ngữ cảnh của ứng dụng, cần thiết để truy cập hệ thống file.

    /**
     * Hàm khởi tạo (Constructor) cho lớp PdfGenerator.
     * Khởi tạo một đối tượng PdfGenerator mới với Context đã cung cấp.
     *
     * @param context Ngữ cảnh của ứng dụng, được sử dụng để thực hiện các thao tác file.
     */
    public PdfGenerator(Context context) {
        this.context = context;
    }

    /**
     * Tạo một tài liệu PDF từ một đối tượng Bitmap và lưu nó với tên file đã cho.
     * Phương thức này sẽ tạo một trang PDF, vẽ Bitmap lên trang đó,
     * sau đó lưu tài liệu PDF vào bộ nhớ thiết bị.
     *
     * @param bitmap Đối tượng Bitmap cần chuyển đổi thành PDF. Không được phép là null.
     * @param fileName Tên của file PDF sẽ được lưu.
     * @return Uri của file PDF đã được lưu.
     * @throws IOException Nếu có lỗi xảy ra trong quá trình tạo hoặc lưu file PDF.
     * @throws IllegalArgumentException Nếu bitmap được cung cấp là null.
     */
    public Uri createPdf(Bitmap bitmap, String fileName) throws IOException {
        // Kiểm tra nếu Bitmap là null, ném ngoại lệ để tránh lỗi.
        if (bitmap == null) {
            throw new IllegalArgumentException("Bitmap không thể null");
        }

        // Khởi tạo một tài liệu PDF mới.
        PdfDocument document = new PdfDocument();
        // Định nghĩa thông tin trang PDF (chiều rộng, chiều cao, số trang).
        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create();
        // Bắt đầu một trang mới trong tài liệu PDF.
        PdfDocument.Page page = document.startPage(pageInfo);

        // Lấy đối tượng Canvas của trang để vẽ lên đó.
        Canvas canvas = page.getCanvas();
        // Vẽ Bitmap lên Canvas của trang PDF, điều chỉnh kích thước và vị trí.
        drawBitmapOnCanvas(canvas, bitmap);
        // Kết thúc trang hiện tại.
        document.finishPage(page);

        // Lưu tài liệu PDF đã tạo vào bộ nhớ và lấy Uri của file đã lưu.
        Uri pdfUri = savePdfToStorage(document, fileName);
        // Đóng tài liệu PDF để giải phóng tài nguyên.
        document.close();

        // Trả về Uri của file PDF đã lưu.
        return pdfUri;
    }

    /**
     * Vẽ một Bitmap lên Canvas của trang PDF, tự động điều chỉnh kích thước
     * và căn giữa Bitmap để phù hợp với kích thước trang.
     *
     * @param canvas Canvas của trang PDF để vẽ Bitmap lên.
     * @param bitmap Bitmap cần vẽ.
     */
    private void drawBitmapOnCanvas(Canvas canvas, Bitmap bitmap) {
        // Tính toán tỷ lệ co giãn theo chiều rộng để Bitmap vừa với trang.
        float scaleX = (float) PAGE_WIDTH / bitmap.getWidth();
        // Tính toán tỷ lệ co giãn theo chiều cao để Bitmap vừa với trang.
        float scaleY = (float) PAGE_HEIGHT / bitmap.getHeight();
        // Chọn tỷ lệ co giãn nhỏ hơn để đảm bảo toàn bộ Bitmap vừa với trang mà không bị cắt.
        float scale = Math.min(scaleX, scaleY);

        // Tính toán vị trí X để căn giữa Bitmap theo chiều ngang.
        float left = (PAGE_WIDTH - bitmap.getWidth() * scale) / 2;
        // Tính toán vị trí Y để căn giữa Bitmap theo chiều dọc.
        float top = (PAGE_HEIGHT - bitmap.getHeight() * scale) / 2;

        // Vẽ Bitmap lên Canvas.
        // Tham số thứ hai (null) là Rect nguồn (toàn bộ Bitmap).
        // Tham số thứ ba là Rect đích, xác định vị trí và kích thước của Bitmap trên Canvas.
        // Tham số thứ tư (null) là Paint (không cần thiết cho việc vẽ cơ bản).
        canvas.drawBitmap(bitmap, null,
                new android.graphics.RectF(left, top, // Góc trên bên trái của Rect đích.
                        left + bitmap.getWidth() * scale, // Góc dưới bên phải của Rect đích (chiều rộng).
                        top + bitmap.getHeight() * scale), null); // Góc dưới bên phải của Rect đích (chiều cao).
    }

    /**
     * Lưu tài liệu PDF vào bộ nhớ thiết bị.
     * Phương thức này xử lý việc lưu file khác nhau tùy thuộc vào phiên bản Android:
     * - Android Q (API 29) trở lên: Sử dụng MediaStore để lưu vào thư mục Downloads/MyPDFImages.
     * - Dưới Android Q: Lưu trực tiếp vào thư mục Downloads/MyPDFImages trên bộ nhớ ngoài.
     *
     * @param document Tài liệu PdfDocument cần lưu.
     * @param fileName Tên của file PDF.
     * @return Uri của file PDF đã được lưu.
     * @throws IOException Nếu có lỗi khi ghi dữ liệu vào OutputStream hoặc không thể mở OutputStream.
     */
    private Uri savePdfToStorage(PdfDocument document, String fileName) throws IOException {
        Uri pdfUri;
        OutputStream outputStream = null; // Khởi tạo OutputStream là null để đảm bảo nó được đóng.

        try {
            // Kiểm tra phiên bản Android.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Đối với Android Q trở lên, sử dụng MediaStore để lưu file.
                pdfUri = saveToMediaStore(fileName);
                if (pdfUri != null) {
                    // Mở OutputStream từ Uri được trả về bởi MediaStore.
                    outputStream = context.getContentResolver().openOutputStream(pdfUri);
                }
            } else {
                // Đối với các phiên bản Android cũ hơn Q, lưu trực tiếp vào bộ nhớ ngoài.
                pdfUri = saveToExternalStorage(fileName);
                // Tạo FileOutputStream từ đường dẫn file.
                outputStream = new FileOutputStream(new File(pdfUri.getPath()));
            }

            // Kiểm tra nếu OutputStream đã được mở thành công.
            if (outputStream != null) {
                // Ghi toàn bộ tài liệu PDF vào OutputStream.
                document.writeTo(outputStream);
                // Đảm bảo tất cả dữ liệu đã được ghi ra.
                outputStream.flush();
                // Trả về Uri của file đã lưu.
                return pdfUri;
            } else {
                // Nếu không thể mở OutputStream, ném ngoại lệ.
                throw new IOException("Không thể mở OutputStream để lưu PDF");
            }
        } finally {
            // Đảm bảo OutputStream được đóng, bất kể có lỗi xảy ra hay không.
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    // Ghi log nếu có lỗi khi đóng OutputStream.
                    Log.e(TAG, "Lỗi khi đóng OutputStream: " + e.getMessage());
                }
            }
        }
    }
    /**
     * Tạo PDF multi-page từ danh sách bitmap
     * @param bitmaps Danh sách bitmap để tạo PDF
     * @param fileName Tên file PDF (ví dụ: "my_document.pdf")
     * @return Uri của PDF được tạo
     * @throws Exception nếu có lỗi trong quá trình tạo PDF
     */
    public Uri createMultiPagePdf(List<Bitmap> bitmaps, String fileName) throws Exception {
        if (bitmaps == null || bitmaps.isEmpty()) {
            throw new Exception("Danh sách ảnh trống");
        }

        String pdfFileName = fileName.endsWith(".pdf") ? fileName : fileName + ".pdf";

        try {
            // Tạo thư mục tùy chỉnh MyPDFImages bên trong thư mục files của ứng dụng
            // Đường dẫn sẽ là: /Android/data/YOUR_PACKAGE_NAME/files/MyPDFImages/
            File customDir = new File(context.getExternalFilesDir(null), "MyPDFImages");
            if (!customDir.exists()) {
                customDir.mkdirs(); // Tạo thư mục nếu nó chưa tồn tại
            }

            // Tạo file PDF bên trong thư mục MyPDFImages
            File pdfFile = new File(customDir, pdfFileName);

            // Tạo PdfDocument
            PdfDocument pdfDocument = new PdfDocument();

            for (int i = 0; i < bitmaps.size(); i++) {
                Bitmap bitmap = bitmaps.get(i);
                if (bitmap == null || bitmap.isRecycled()) {
                    Log.w(TAG, "Bỏ qua bitmap null hoặc recycled ở vị trí: " + i);
                    continue;
                }

                // Tạo page info với kích thước của bitmap
                PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(bitmap.getWidth(), bitmap.getHeight(), i + 1).create();

                // Bắt đầu một page mới
                PdfDocument.Page page = pdfDocument.startPage(pageInfo);

                // Vẽ bitmap lên trang
                Canvas canvas = page.getCanvas();
                canvas.drawBitmap(bitmap, 0, 0, null);

                // Kết thúc trang
                pdfDocument.finishPage(page);
            }

            // Ghi PDF ra file
            FileOutputStream fos = new FileOutputStream(pdfFile);
            pdfDocument.writeTo(fos);
            fos.close();

            // Đóng PdfDocument để giải phóng tài nguyên
            pdfDocument.close();

            // Trả về Uri của file PDF bằng FileProvider
            // Đảm bảo bạn đã cấu hình FileProvider trong AndroidManifest.xml và res/xml/file_paths.xml
            return FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", pdfFile);

        } catch (IOException e) {
            Log.e(TAG, "Lỗi khi tạo PDF: " + e.getMessage(), e);
            throw new Exception("Không thể tạo file PDF: " + e.getMessage(), e);
        } catch (Exception e) {
            Log.e(TAG, "Lỗi không xác định khi tạo PDF: " + e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Lưu file PDF vào MediaStore (dành cho Android Q trở lên).
     * Phương thức này tạo một mục mới trong MediaStore cho file PDF
     * và trả về Uri để ghi dữ liệu vào đó.
     *
     * @param fileName Tên file PDF.
     * @return Uri của mục MediaStore mới tạo, hoặc null nếu không thể tạo.
     */
    private Uri saveToMediaStore(String fileName) {
        ContentResolver resolver = context.getContentResolver();
        ContentValues contentValues = new ContentValues();
        // Đặt tên hiển thị của file.
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        // Đặt kiểu MIME của file là PDF.
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
        // Đặt đường dẫn tương đối trong thư mục Downloads, tạo một thư mục con "MyPDFImages".
        contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS + File.separator + "MyPDFImages");

        // Đối với Android Q trở lên, chèn mục mới vào MediaStore.Downloads.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues);
        }
        return null; // Trả về null nếu phiên bản Android không phù hợp.
    }

    /**
     * Lưu file PDF vào bộ nhớ ngoài công cộng (dành cho Android dưới Q).
     * Phương thức này tạo một thư mục "MyPDFImages" trong thư mục Downloads
     * và trả về Uri của file PDF trong thư mục đó.
     *
     * @param fileName Tên file PDF.
     * @return Uri của file PDF trên bộ nhớ ngoài.
     */
    private Uri saveToExternalStorage(String fileName) {
        // Lấy đường dẫn đến thư mục Downloads công cộng và tạo thư mục con "MyPDFImages".
        File pdfDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "MyPDFImages");
        // Kiểm tra nếu thư mục không tồn tại, tạo nó.
        if (!pdfDir.exists()) {
            pdfDir.mkdirs(); // Tạo thư mục và các thư mục cha cần thiết.
        }
        // Tạo đối tượng File cho file PDF trong thư mục đã tạo.
        File pdfFile = new File(pdfDir, fileName);
        // Trả về Uri từ đối tượng File.
        return Uri.fromFile(pdfFile);
    }
}