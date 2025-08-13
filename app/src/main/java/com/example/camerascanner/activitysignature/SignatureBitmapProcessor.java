package com.example.camerascanner.activitysignature;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;

import com.example.camerascanner.activitysignature.signatureview.signature.SignaturePaintManager;
import com.example.camerascanner.activitysignature.signatureview.signature.SignatureStateManager;

/**
 * Lớp **SignatureBitmapProcessor** chịu trách nhiệm xử lý các thao tác liên quan đến Bitmap
 * trong quá trình quản lý chữ ký. Các chức năng bao gồm phát hiện hộp giới hạn (bounding box),
 * cắt ảnh (cropping), và tải ảnh (loading bitmaps).
 */
public class SignatureBitmapProcessor {
    // Đối tượng quản lý trạng thái hiện tại của chữ ký (đường dẫn, bitmap phủ, khung, hộp, v.v.).
    private final SignatureStateManager stateManager;
    // Đối tượng quản lý các thuộc tính của bút vẽ (màu sắc, độ rộng nét, v.v.).
    private final SignaturePaintManager paintManager;

    /**
     * Constructor khởi tạo **SignatureBitmapProcessor** với các đối tượng quản lý trạng thái và bút vẽ.
     * @param stateManager Đối tượng quản lý trạng thái chữ ký.
     * @param paintManager Đối tượng quản lý các thuộc tính bút vẽ.
     */
    public SignatureBitmapProcessor(SignatureStateManager stateManager, SignaturePaintManager paintManager) {
        this.stateManager = stateManager;
        this.paintManager = paintManager;
    }

    /**
     * Phát hiện hộp giới hạn (bounding box) cho chữ ký hiện có hoặc bitmap phủ.
     * - Nếu có **bitmap phủ**, hộp giới hạn sẽ được tính toán dựa trên nội dung (pixel không trong suốt) của bitmap đó.
     * - Nếu chỉ có **đường dẫn chữ ký**, hộp giới hạn sẽ được tính toán trực tiếp từ đường dẫn.
     * Sau khi phát hiện, hộp giới hạn và hộp cắt sẽ được cập nhật trong `stateManager`.
     */
    public void detectBoundingBox() {
        // Thoát nếu không có cả đường dẫn chữ ký và bitmap phủ (không có gì để phát hiện).
        if (stateManager.getSignaturePath().isEmpty() && stateManager.getOverlayBitmap() == null) return;

        // Khởi tạo một đối tượng RectF để lưu trữ hộp giới hạn được phát hiện.
        RectF detectedBox = new RectF();

        // Kiểm tra nếu có bitmap phủ.
        if (stateManager.getOverlayBitmap() != null) {
            // Tìm hộp giới hạn của nội dung (pixel không trong suốt) trong bitmap phủ.
            detectedBox = findContentBounds(stateManager.getOverlayBitmap());
            // Lấy khung chữ ký hiện tại (vị trí và kích thước của bitmap phủ trên view).
            RectF frame = stateManager.getSignatureFrame();
            // Điều chỉnh vị trí của hộp giới hạn để phù hợp với vị trí của khung chữ ký trên view.
            detectedBox.offset(frame.left, frame.top);
        } else {
            // Nếu không có bitmap phủ, tính toán hộp giới hạn trực tiếp từ đường dẫn chữ ký.
            stateManager.getSignaturePath().computeBounds(detectedBox, true);
        }

        // Cập nhật hộp giới hạn chính và hộp cắt trong `stateManager` bằng hộp đã phát hiện.
        stateManager.getBoundingBox().set(detectedBox);
        stateManager.getCropBox().set(detectedBox);
        // Đặt cờ để báo hiệu rằng hộp giới hạn nên được hiển thị.
        stateManager.setShowBoundingBox(true);
    }

    /**
     * Tìm hộp giới hạn nhỏ nhất chứa tất cả các pixel không trong suốt (alpha > 0) trong một Bitmap.
     * Phương thức này duyệt qua từng pixel của bitmap để xác định các cạnh của nội dung.
     * Một khoảng đệm (padding) nhỏ được thêm vào hộp giới hạn để đảm bảo không cắt quá sát nội dung.
     *
     * @param bitmap Bitmap cần tìm hộp giới hạn nội dung.
     * @return Một đối tượng `RectF` chứa tọa độ của hộp giới hạn nội dung đã phát hiện.
     */
    private RectF findContentBounds(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        // Khởi tạo các giá trị ban đầu cho các cạnh của hộp giới hạn.
        // `left` và `top` được khởi tạo với giá trị lớn nhất, `right` và `bottom` với giá trị nhỏ nhất.
        int left = width, top = height, right = 0, bottom = 0;

        // Duyệt qua từng hàng (y) và cột (x) của bitmap.
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                // Kiểm tra nếu pixel tại (x, y) có độ trong suốt lớn hơn 0 (tức là không hoàn toàn trong suốt).
                if (Color.alpha(bitmap.getPixel(x, y)) > 0) {
                    // Cập nhật `left` và `top` nếu tìm thấy pixel ở vị trí nhỏ hơn hiện tại.
                    left = Math.min(left, x);
                    top = Math.min(top, y);
                    // Cập nhật `right` và `bottom` nếu tìm thấy pixel ở vị trí lớn hơn hiện tại.
                    right = Math.max(right, x);
                    bottom = Math.max(bottom, y);
                }
            }
        }

        // Nếu không tìm thấy nội dung (hoặc hộp giới hạn không hợp lệ, ví dụ: left >= right), trả về một RectF rỗng.
        if (left >= right || top >= bottom) return new RectF();

        // Định nghĩa một khoảng đệm (padding) để thêm vào xung quanh nội dung được phát hiện.
        int padding = 20;
        // Trả về một RectF mới với các cạnh đã được điều chỉnh bằng padding.
        // `Math.max(0, ...)` đảm bảo các cạnh không vượt ra ngoài biên 0.
        // `Math.min(width, ...)` và `Math.min(height, ...)` đảm bảo các cạnh không vượt quá kích thước bitmap.
        return new RectF(
                Math.max(0, left - padding),
                Math.max(0, top - padding),
                Math.min(width, right + padding),
                Math.min(height, bottom + padding)
        );
    }

    /**
     * Lấy một Bitmap đã được cắt dựa trên trạng thái hiện tại.
     * - Nếu có **bitmap phủ** và **khung chữ ký**, nó sẽ cắt bitmap từ khung đó.
     * - Nếu không, và có một **hộp cắt** được định nghĩa, nó sẽ cắt bitmap từ đường dẫn chữ ký.
     *
     * @param viewWidth Chiều rộng của view hiển thị chữ ký.
     * @param viewHeight Chiều cao của view hiển thị chữ ký.
     * @return Một đối tượng `Bitmap` đã được cắt, hoặc `null` nếu không có gì để cắt.
     */
    public Bitmap getCroppedBitmap(int viewWidth, int viewHeight) {
        // Ưu tiên cắt từ bitmap phủ nếu nó tồn tại và khung chữ ký không rỗng.
        if (stateManager.getOverlayBitmap() != null && !stateManager.getSignatureFrame().isEmpty()) {
            return cropBitmapFromFrame(viewWidth, viewHeight);
        }

        // Nếu không có bitmap phủ hoặc khung rỗng, kiểm tra hộp cắt cho đường dẫn chữ ký.
        if (!stateManager.getCropBox().isEmpty()) {
            return cropBitmapFromPath(viewWidth, viewHeight);
        }

        // Trả về null nếu không có trường hợp nào để cắt.
        return null;
    }

    /**
     * Cắt một phần của bitmap phủ dựa trên kích thước và vị trí của khung chữ ký.
     * Phương thức này tạo một bitmap tạm thời có kích thước bằng view, vẽ bitmap phủ lên đó,
     * sau đó cắt ra phần tương ứng với khung.
     *
     * @param viewWidth Chiều rộng của view hiển thị.
     * @param viewHeight Chiều cao của view hiển thị.
     * @return Một đối tượng `Bitmap` đã được cắt từ bitmap phủ.
     */
    private Bitmap cropBitmapFromFrame(int viewWidth, int viewHeight) {
        // Tạo một Bitmap tạm thời có kích thước bằng toàn bộ view để vẽ lên.
        Bitmap fullView = Bitmap.createBitmap(viewWidth, viewHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(fullView);
        // Vẽ bitmap phủ lên canvas, định vị và co giãn nó để vừa với khung chữ ký.
        canvas.drawBitmap(stateManager.getOverlayBitmap(), null, stateManager.getSignatureFrame(), null);

        RectF frame = stateManager.getSignatureFrame();
        // Tạo một Bitmap mới bằng cách cắt từ `fullView` theo tọa độ và kích thước của khung.
        Bitmap cropped = Bitmap.createBitmap(fullView,
                (int) frame.left, (int) frame.top,
                (int) frame.width(), (int) frame.height());

        // Giải phóng bộ nhớ của Bitmap tạm thời `fullView`.
        fullView.recycle();
        return cropped;
    }

    /**
     * Cắt một phần của đường dẫn chữ ký thành một Bitmap.
     * Phương thức này tạo một bitmap tạm thời có kích thước bằng view, vẽ đường dẫn chữ ký lên đó,
     * sau đó cắt ra phần tương ứng với hộp cắt.
     *
     * @param viewWidth Chiều rộng của view hiển thị.
     * @param viewHeight Chiều cao của view hiển thị.
     * @return Một đối tượng `Bitmap` đã được cắt từ đường dẫn chữ ký.
     */
    private Bitmap cropBitmapFromPath(int viewWidth, int viewHeight) {
        // Tạo một Bitmap tạm thời có kích thước bằng toàn bộ view để vẽ đường dẫn chữ ký.
        Bitmap fullBitmap = Bitmap.createBitmap(viewWidth, viewHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(fullBitmap);
        // Vẽ đường dẫn chữ ký lên canvas bằng Paint đã cấu hình.
        canvas.drawPath(stateManager.getSignaturePath(), paintManager.getSignaturePaint());

        RectF cropBox = stateManager.getCropBox();
        // Tạo một Bitmap mới bằng cách cắt từ `fullBitmap` theo tọa độ và kích thước của hộp cắt.
        Bitmap cropped = Bitmap.createBitmap(fullBitmap,
                (int) cropBox.left, (int) cropBox.top,
                (int) cropBox.width(), (int) cropBox.height());

        // Giải phóng bộ nhớ của Bitmap tạm thời `fullBitmap`.
        fullBitmap.recycle();
        return cropped;
    }

    /**
     * Lấy một Bitmap chỉ chứa riêng đường dẫn chữ ký đã vẽ.
     * Bitmap này sẽ có kích thước vừa khít với hộp giới hạn của chữ ký.
     *
     * @return Một đối tượng `Bitmap` chứa chữ ký được vẽ.
     */
    public Bitmap getSignatureBitmap() {
        // Xác định hộp giới hạn để sử dụng. Ưu tiên hộp giới hạn đã tính toán, nếu rỗng thì dùng khung chữ ký.
        RectF bounds = stateManager.getBoundingBox().isEmpty() ?
                stateManager.getSignatureFrame() : stateManager.getBoundingBox();

        // Tạo một Bitmap mới với kích thước vừa đủ để chứa hộp giới hạn đã xác định.
        Bitmap bitmap = Bitmap.createBitmap(
                (int) bounds.width(),
                (int) bounds.height(),
                Bitmap.Config.ARGB_8888 // Cấu hình ARGB_8888 hỗ trợ kênh alpha (trong suốt).
        );

        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.TRANSPARENT); // Đặt nền của bitmap là trong suốt.
        // Di chuyển canvas theo hướng ngược lại của tọa độ top-left của hộp giới hạn.
        // Điều này đảm bảo rằng đường dẫn chữ ký (hoặc nội dung khác) sẽ được vẽ bắt đầu từ (0,0) trên bitmap mới.
        canvas.translate(-bounds.left, -bounds.top);
        // Vẽ đường dẫn chữ ký lên canvas.
        canvas.drawPath(stateManager.getSignaturePath(), paintManager.getSignaturePaint());

        return bitmap;
    }

    /**
     * Tải một Bitmap đã cho vào làm bitmap phủ (overlay bitmap) trong `stateManager`.
     * Khi một bitmap được tải, đường dẫn chữ ký hiện có sẽ được đặt lại (xóa),
     * và chế độ vẽ sẽ được tắt. Khung chữ ký sẽ được thiết lập để vừa với kích thước của bitmap được tải.
     *
     * @param bitmap Bitmap cần được tải làm lớp phủ.
     * @param viewWidth Chiều rộng của view hiển thị.
     * @param viewHeight Chiều cao của view hiển thị.
     */
    public void loadBitmap(Bitmap bitmap, int viewWidth, int viewHeight) {
        // Thoát nếu bitmap được cung cấp là null.
        if (bitmap == null) return;

        // Đặt bitmap đã cho làm bitmap phủ trong stateManager.
        stateManager.setOverlayBitmap(bitmap);
        // Đặt lại đường dẫn chữ ký (xóa mọi nét vẽ hiện có).
        stateManager.getSignaturePath().reset();

        // Nếu kích thước view hợp lệ, thiết lập khung chữ ký để phù hợp với kích thước của bitmap được tải.
        if (viewWidth > 0 && viewHeight > 0) {
            stateManager.getSignatureFrame().set(0, 0, bitmap.getWidth(), bitmap.getHeight());
        }

        // Tắt chế độ vẽ (người dùng không còn vẽ tay nữa, mà đang làm việc với ảnh).
        stateManager.setDrawingMode(false);
        // Đặt cờ để hiển thị khung chữ ký (áp dụng cho bitmap phủ).
        stateManager.setShowSignatureFrame(true);
    }
}