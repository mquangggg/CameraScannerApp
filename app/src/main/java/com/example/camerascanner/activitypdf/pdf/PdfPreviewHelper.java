package com.example.camerascanner.activitypdf.pdf;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.widget.ImageView;

import java.io.IOException;

public class PdfPreviewHelper {
    private static final String TAG = "PdfPreviewHelper"; // Thẻ (tag) cho nhật ký Logcat.

    private Context context; // Biến thành viên để lưu trữ Context của ứng dụng.

    /**
     * Hàm khởi tạo (Constructor) cho lớp PdfPreviewHelper.
     * Khởi tạo một đối tượng PdfPreviewHelper mới với Context đã cung cấp.
     *
     * @param context Ngữ cảnh của ứng dụng, cần thiết để truy cập ContentResolver
     * và các tài nguyên hệ thống.
     */
    public PdfPreviewHelper(Context context) {
        this.context = context;
    }

    /**
     * Hiển thị trang đầu tiên của một tài liệu PDF dưới dạng hình ảnh trong một ImageView.
     * Phương thức này sử dụng PdfRenderer (chỉ khả dụng từ API 21 trở lên) để mở và hiển thị PDF.
     *
     * @param pdfUri URI của file PDF cần hiển thị.
     * @param imageView ImageView mà hình ảnh preview của PDF sẽ được hiển thị.
     * @return true nếu preview được hiển thị thành công, false nếu có lỗi hoặc API level không được hỗ trợ.
     */
    public boolean displayPdfPreview(Uri pdfUri, ImageView imageView) {
        // Kiểm tra phiên bản Android. PdfRenderer chỉ khả dụng từ Lollipop (API 21) trở lên.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            Log.w(TAG, "PdfRenderer không được hỗ trợ trên API level thấp hơn Lollipop.");
            return false;
        }

        PdfRenderer renderer = null; // Đối tượng dùng để render PDF.
        ParcelFileDescriptor parcelFileDescriptor = null; // Descriptor cho file PDF.
        PdfRenderer.Page currentPage = null; // Trang PDF hiện tại được mở.

        try {
            // Mở ParcelFileDescriptor từ URI của PDF với quyền đọc ("r").
            parcelFileDescriptor = context.getContentResolver().openFileDescriptor(pdfUri, "r");
            // Kiểm tra nếu ParcelFileDescriptor là null, nghĩa là không thể mở file.
            if (parcelFileDescriptor == null) {
                Log.e(TAG, "Không thể mở ParcelFileDescriptor cho URI PDF: " + pdfUri);
                throw new IOException("Không thể mở ParcelFileDescriptor cho URI PDF");
            }

            // Khởi tạo PdfRenderer với ParcelFileDescriptor.
            renderer = new PdfRenderer(parcelFileDescriptor);
            // Mở trang đầu tiên của tài liệu PDF (trang có chỉ mục 0).
            currentPage = renderer.openPage(0);

            // Tạo một Bitmap để chứa nội dung được render từ trang PDF.
            // Kích thước của Bitmap sẽ được điều chỉnh để phù hợp với ImageView hoặc kích thước mặc định.
            Bitmap renderedBitmap = createRenderedBitmap(currentPage, imageView);
            // Render nội dung của trang PDF lên Bitmap đã tạo.
            // Tham số đầu tiên là Bitmap đích, hai tham số tiếp theo (RectF và Matrix) là null để render toàn bộ trang.
            // RENDER_MODE_FOR_DISPLAY chỉ ra rằng Bitmap sẽ được dùng để hiển thị trên màn hình.
            currentPage.render(renderedBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);

            // Đặt Bitmap đã render vào ImageView để hiển thị preview.
            imageView.setImageBitmap(renderedBitmap);
            return true; // Trả về true nếu thành công.

        } catch (IOException e) {
            // Ghi log lỗi nếu có bất kỳ vấn đề nào xảy ra trong quá trình mở hoặc render PDF.
            Log.e(TAG, "Lỗi khi hiển thị PDF: " + e.getMessage(), e);
            return false; // Trả về false nếu thất bại.
        } finally {
            // Đảm bảo tất cả các tài nguyên (trang, renderer, file descriptor) được đóng.
            closeResources(currentPage, renderer, parcelFileDescriptor);
        }
    }

    /**
     * Tạo một Bitmap với kích thước phù hợp để render trang PDF.
     * Kích thước của Bitmap sẽ ưu tiên kích thước của ImageView.
     * Nếu ImageView chưa có kích thước (ví dụ: chưa được layout),
     * nó sẽ sử dụng kích thước mặc định (min(page.getWidth(), 2000), min(page.getHeight(), 2000))
     * để tránh tạo ra Bitmap quá lớn hoặc quá nhỏ.
     *
     * @param page Trang PDF mà Bitmap sẽ được render.
     * @param imageView ImageView sẽ hiển thị Bitmap.
     * @return Một Bitmap mới với cấu hình ARGB_8888 và kích thước được tính toán.
     */
    private Bitmap createRenderedBitmap(PdfRenderer.Page page, ImageView imageView) {
        // Lấy chiều rộng và chiều cao hiện tại của ImageView.
        int desiredWidth = imageView.getWidth();
        int desiredHeight = imageView.getHeight();

        // Nếu ImageView chưa có kích thước (thường xảy ra khi nó chưa được layout),
        // sử dụng kích thước mặc định dựa trên kích thước trang PDF, nhưng giới hạn tối đa là 2000 pixel
        // để tránh tràn bộ nhớ với các trang PDF rất lớn.
        if (desiredWidth == 0 || desiredHeight == 0) {
            desiredWidth = Math.min(page.getWidth(), 2000);
            desiredHeight = Math.min(page.getHeight(), 2000);
            Log.w(TAG, "ImageView chưa được layout. Sử dụng kích thước mặc định cho Bitmap: " + desiredWidth + "x" + desiredHeight);
        }

        // Tạo một Bitmap mới với kích thước đã xác định và định dạng ARGB_8888.
        return Bitmap.createBitmap(desiredWidth, desiredHeight, Bitmap.Config.ARGB_8888);
    }

    /**
     * Đóng tất cả các tài nguyên liên quan đến PdfRenderer một cách an toàn.
     * Phương thức này kiểm tra từng đối tượng (trang, renderer, file descriptor)
     * và đóng chúng nếu chúng không phải là null, đồng thời xử lý các ngoại lệ IOException
     * có thể xảy ra trong quá trình đóng.
     *
     * @param currentPage Đối tượng PdfRenderer.Page cần đóng.
     * @param renderer Đối tượng PdfRenderer cần đóng.
     * @param parcelFileDescriptor Đối tượng ParcelFileDescriptor cần đóng.
     */
    private void closeResources(PdfRenderer.Page currentPage, PdfRenderer renderer, ParcelFileDescriptor parcelFileDescriptor) {
        // Đóng trang PDF nếu nó không phải là null.
        if (currentPage != null) {
            currentPage.close();
        }
        // Đóng renderer PDF nếu nó không phải là null.
        if (renderer != null) {
            renderer.close();
        }
        // Đóng ParcelFileDescriptor nếu nó không phải là null.
        if (parcelFileDescriptor != null) {
            try {
                parcelFileDescriptor.close();
            } catch (IOException e) {
                // Ghi log lỗi nếu có vấn đề khi đóng ParcelFileDescriptor.
                Log.e(TAG, "Lỗi khi đóng ParcelFileDescriptor: " + e.getMessage());
            }
        }
    }
}