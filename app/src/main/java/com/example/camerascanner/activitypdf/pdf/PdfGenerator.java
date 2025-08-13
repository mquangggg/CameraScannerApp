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

    private final Context context; // Ngữ cảnh của ứng dụng, cần thiết để truy cập hệ thống file.

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
    private Uri  savePdfToStorage(PdfDocument document, String fileName) throws IOException {
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
     * Tạo PDF multi-page từ danh sách bitmap - mỗi ảnh một trang
     * Ảnh được hiển thị theo style danh sách đơn (chiếm toàn bộ chiều rộng)
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
                // Tạo PdfDocument
                PdfDocument pdfDocument = new PdfDocument();

                // Tạo từng trang PDF cho mỗi ảnh với kích thước động
                for (int i = 0; i < bitmaps.size(); i++) {
                    Bitmap bitmap = bitmaps.get(i);
                    if (bitmap == null || bitmap.isRecycled()) {
                        Log.w(TAG, "Bỏ qua bitmap null hoặc đã được recycle tại vị trí: " + i);
                        continue;
                    }

                    // Tính toán chiều cao page dựa trên tỷ lệ ảnh
                    int pageHeight = calculatePageHeight(bitmap);

                    // Tạo trang PDF với kích thước động
                    PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(PAGE_WIDTH, pageHeight, i + 1).create();
                    PdfDocument.Page page = pdfDocument.startPage(pageInfo);
                    Canvas canvas = page.getCanvas();

                    // Vẽ ảnh fill full page
                    drawBitmapFullPage(canvas, bitmap, pageHeight);

                    pdfDocument.finishPage(page);

                    Log.d(TAG, String.format("Tạo page %d với kích thước %dx%d cho ảnh %dx%d",
                            i + 1, PAGE_WIDTH, pageHeight, bitmap.getWidth(), bitmap.getHeight()));
                }

                Uri pdfUri = savePdfToStorage(pdfDocument, pdfFileName);
                pdfDocument.close();

                return pdfUri;

            } catch (IOException e) {
                Log.e(TAG, "Lỗi khi tạo PDF: " + e.getMessage(), e);
                throw new Exception("Không thể tạo file PDF: " + e.getMessage(), e);
            } catch (Exception e) {
                Log.e(TAG, "Lỗi không xác định khi tạo PDF: " + e.getMessage(), e);
                throw e;
            }
        }

/**
 * Tính toán chiều cao page dựa trên tỷ lệ ảnh và chiều rộng page cố định
 * @param bitmap Bitmap để tính toán
 * @return Chiều cao page phù hợp
 */
        private int calculatePageHeight(Bitmap bitmap) {
            if (bitmap == null || bitmap.isRecycled()) {
                return PAGE_HEIGHT; // Trả về chiều cao mặc định nếu bitmap không hợp lệ
            }

            final int MARGIN = 0; // Margin cho 4 cạnh
            final int USABLE_PAGE_WIDTH = PAGE_WIDTH;

            int bitmapWidth = bitmap.getWidth();
            int bitmapHeight = bitmap.getHeight();

            // Tính tỷ lệ để ảnh vừa với chiều rộng page
            float scale = (float) USABLE_PAGE_WIDTH / bitmapWidth;

            // Tính chiều cao ảnh sau khi scale
            int scaledImageHeight = Math.round(bitmapHeight * scale);

            // Chiều cao page = chiều cao ảnh đã scale + margin
            int calculatedPageHeight = scaledImageHeight;

            // Đảm bảo chiều cao page không quá nhỏ (tối thiểu 100 point)
            return Math.max(calculatedPageHeight, 100);
        }

/**
 * Vẽ bitmap fill full page với kích thước page đã được tính toán động
 * @param canvas Canvas của trang PDF
 * @param bitmap Bitmap cần vẽ
 * @param pageHeight Chiều cao của page hiện tại
 */
        private void drawBitmapFullPage(Canvas canvas, Bitmap bitmap, int pageHeight) {
            if (bitmap == null || bitmap.isRecycled()) {
                Log.w(TAG, "Không thể vẽ bitmap null hoặc đã recycle");
                return;
            }

            final int MARGIN = 20; // Margin cho 4 cạnh

            // Vẽ ảnh từ margin đến margin, fill full vùng có thể sử dụng
            android.graphics.RectF destRect = new android.graphics.RectF(
                    MARGIN,                    // left
                    MARGIN,                    // top
                    PAGE_WIDTH - MARGIN,       // right
                    pageHeight - MARGIN        // bottom
            );

            // Vẽ bitmap lên canvas với kích thước đã tính toán
            canvas.drawBitmap(bitmap, null, destRect, null);

            // Vẽ đường viền nhẹ xung quanh ảnh (tùy chọn)
            android.graphics.Paint borderPaint = new android.graphics.Paint();
            borderPaint.setColor(0xFFE0E0E0); // Màu xám nhạt
            borderPaint.setStyle(android.graphics.Paint.Style.STROKE);
            borderPaint.setStrokeWidth(1);
            canvas.drawRect(destRect, borderPaint);

            // Log thông tin để debug
            Log.d(TAG, String.format("Vẽ ảnh %dx%d fill full page %dx%d",
                    bitmap.getWidth(), bitmap.getHeight(), PAGE_WIDTH, pageHeight));
        }

    /**
     * Vẽ một Bitmap lên Canvas của trang PDF (phương thức cũ - backup)
     * Căn giữa ảnh hoàn toàn trên trang
     *
     * @param canvas Canvas của trang PDF để vẽ Bitmap lên.
     * @param bitmap Bitmap cần vẽ.
     */
    private void drawBitmapOnCanvas(Canvas canvas, Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) {
            Log.w(TAG, "Không thể vẽ bitmap null hoặc đã recycle");
            return;
        }

        int bitmapWidth = bitmap.getWidth();
        int bitmapHeight = bitmap.getHeight();

        // Tính toán tỷ lệ co giãn để ảnh vừa với trang mà không bị méo
        float scaleX = (float) PAGE_WIDTH / bitmapWidth;
        float scaleY = (float) PAGE_HEIGHT / bitmapHeight;

        // Chọn tỷ lệ nhỏ hơn để đảm bảo toàn bộ ảnh vừa với trang
        float scale = Math.min(scaleX, scaleY);

        // Tính toán kích thước mới sau khi scale
        float scaledWidth = bitmapWidth * scale;
        float scaledHeight = bitmapHeight * scale;

        // Tính toán vị trí để căn giữa ảnh hoàn toàn
        float left = (PAGE_WIDTH - scaledWidth) / 2;
        float top = (PAGE_HEIGHT - scaledHeight) / 2;

        // Tạo RectF đích để vẽ ảnh
        android.graphics.RectF destRect = new android.graphics.RectF(
                left,
                top,
                left + scaledWidth,
                top + scaledHeight
        );

        // Vẽ bitmap lên canvas với kích thước và vị trí đã tính toán
        canvas.drawBitmap(bitmap, null, destRect, null);

        // Log thông tin để debug (có thể bỏ trong production)
        Log.d(TAG, String.format("Vẽ ảnh %dx%d -> %.0fx%.0f tại (%.0f, %.0f) với scale %.2f",
                bitmapWidth, bitmapHeight, scaledWidth, scaledHeight, left, top, scale));
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