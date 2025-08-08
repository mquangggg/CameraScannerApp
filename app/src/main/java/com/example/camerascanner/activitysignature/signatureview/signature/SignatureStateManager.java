package com.example.camerascanner.activitysignature.signatureview.signature;

import android.graphics.Bitmap;
import android.graphics.Path;
import android.graphics.RectF;

/**
 * Lớp **SignatureStateManager** quản lý và duy trì trạng thái của các thành phần liên quan đến chữ ký
 * trong ứng dụng. Nó chứa các đối tượng đồ họa (như Path, RectF, Bitmap) và các cờ trạng thái (boolean)
 * để kiểm soát cách chữ ký được hiển thị và tương tác.
 */
public class SignatureStateManager {
    // Đối tượng Path dùng để lưu trữ các nét vẽ của chữ ký.
    private Path signaturePath;
    // RectF định nghĩa khung bao quanh chữ ký hoặc vùng làm việc chính.
    private RectF signatureFrame;
    // RectF định nghĩa hộp giới hạn (bounding box) tự động phát hiện nội dung của chữ ký hoặc bitmap.
    private RectF boundingBox;
    // RectF định nghĩa hộp cắt (crop box) mà người dùng có thể điều chỉnh để cắt chữ ký.
    private RectF cropBox;
    // Bitmap được sử dụng làm lớp phủ (overlay), ví dụ: một hình ảnh được tải lên để ký lên.
    private Bitmap overlayBitmap;

    // Cờ boolean kiểm soát chế độ vẽ: true nếu đang vẽ tay, false nếu đang thao tác với bitmap.
    private boolean isDrawingMode = true;
    // Cờ boolean kiểm soát việc hiển thị khung chữ ký.
    private boolean showSignatureFrame = true;
    // Cờ boolean kiểm soát việc hiển thị hộp giới hạn.
    private boolean showBoundingBox = false;
    // Cờ boolean kiểm soát việc hiển thị các tay cầm điều khiển để cắt (crop handles).
    private boolean showCropHandles = false;
    // Cờ boolean kiểm soát việc khung chữ ký có thể thay đổi kích thước hay không.
    private boolean isFrameResizable = true;

    // Góc xoay hiện tại của chữ ký hoặc khung, tính bằng radian.
    private float rotationAngle = 0f;
    // Chiều rộng khởi tạo mặc định cho khung chữ ký.
    private float initialFrameWidth = 600f;
    // Chiều cao khởi tạo mặc định cho khung chữ ký.
    private float initialFrameHeight = 200f;

    /**
     * Constructor mặc định của lớp **SignatureStateManager**.
     * Khi một đối tượng `SignatureStateManager` được tạo, phương thức `initComponents()` sẽ được gọi
     * để khởi tạo các đối tượng đồ họa.
     */
    public SignatureStateManager() {
        initComponents();
    }

    /**
     * Phương thức này khởi tạo các đối tượng đồ họa như `Path` và `RectF` khi lớp được tạo.
     */
    private void initComponents() {
        signaturePath = new Path();     // Khởi tạo đối tượng Path mới cho chữ ký.
        signatureFrame = new RectF();   // Khởi tạo đối tượng RectF mới cho khung chữ ký.
        boundingBox = new RectF();      // Khởi tạo đối tượng RectF mới cho hộp giới hạn.
        cropBox = new RectF();          // Khởi tạo đối tượng RectF mới cho hộp cắt.
    }

    /**
     * Khởi tạo vị trí và kích thước ban đầu của khung chữ ký (`signatureFrame`).
     * Khung sẽ được căn giữa trên view với kích thước mặc định (`initialFrameWidth`, `initialFrameHeight`).
     *
     * @param width Chiều rộng của view hiển thị.
     * @param height Chiều cao của view hiển thị.
     */
    public void initializeFrame(int width, int height) {
        float centerX = width / 2f;  // Tính toán tọa độ X trung tâm của view.
        float centerY = height / 2f; // Tính toán tọa độ Y trung tâm của view.
        // Đặt tọa độ cho `signatureFrame` để nó được căn giữa.
        signatureFrame.set(
                centerX - initialFrameWidth / 2,
                centerY - initialFrameHeight / 2,
                centerX + initialFrameWidth / 2,
                centerY + initialFrameHeight / 2
        );
    }

    /**
     * Xóa bỏ tất cả trạng thái hiện tại của chữ ký.
     * Điều này bao gồm việc đặt lại đường dẫn chữ ký, làm rỗng các hộp giới hạn và hộp cắt,
     * đặt lại các cờ hiển thị và góc xoay về trạng thái mặc định ban đầu, và chuyển về chế độ vẽ.
     */
    public void clear() {
        signaturePath.reset();      // Xóa tất cả các đường nét trong Path của chữ ký.
        boundingBox.setEmpty();     // Làm rỗng hộp giới hạn.
        cropBox.setEmpty();         // Làm rỗng hộp cắt.
        showBoundingBox = false;    // Ẩn hộp giới hạn.
        showCropHandles = false;    // Ẩn các tay cầm cắt.
        isDrawingMode = true;       // Chuyển về chế độ vẽ.
        rotationAngle = 0f;         // Đặt lại góc xoay về 0.
        // Lưu ý: overlayBitmap và signatureFrame không được reset ở đây, tùy thuộc vào logic ứng dụng.
    }

    // --- Các phương thức getter để truy cập các đối tượng trạng thái ---

    /**
     * Trả về đối tượng `Path` chứa các nét vẽ của chữ ký.
     * @return `Path` của chữ ký.
     */
    public Path getSignaturePath() { return signaturePath; }

    /**
     * Trả về đối tượng `RectF` định nghĩa khung bao quanh chữ ký hoặc vùng làm việc.
     * @return `RectF` của khung chữ ký.
     */
    public RectF getSignatureFrame() { return signatureFrame; }

    /**
     * Trả về đối tượng `RectF` định nghĩa hộp giới hạn của nội dung chữ ký/bitmap.
     * @return `RectF` của hộp giới hạn.
     */
    public RectF getBoundingBox() { return boundingBox; }

    /**
     * Trả về đối tượng `RectF` định nghĩa hộp cắt mà người dùng thao tác.
     * @return `RectF` của hộp cắt.
     */
    public RectF getCropBox() { return cropBox; }

    /**
     * Trả về đối tượng `Bitmap` được sử dụng làm lớp phủ.
     * @return `Bitmap` lớp phủ.
     */
    public Bitmap getOverlayBitmap() { return overlayBitmap; }

    // --- Các phương thức getter cho các cờ trạng thái boolean và góc xoay ---

    /**
     * Kiểm tra xem ứng dụng có đang ở chế độ vẽ (người dùng có thể vẽ tay) hay không.
     * @return `true` nếu ở chế độ vẽ, `false` nếu đang thao tác với ảnh.
     */
    public boolean isDrawingMode() { return isDrawingMode; }

    /**
     * Kiểm tra xem khung chữ ký có đang được hiển thị hay không.
     * @return `true` nếu khung hiển thị, `false` nếu ẩn.
     */
    public boolean isShowSignatureFrame() { return showSignatureFrame; }

    /**
     * Kiểm tra xem hộp giới hạn có đang được hiển thị hay không.
     * @return `true` nếu hộp giới hạn hiển thị, `false` nếu ẩn.
     */
    public boolean isShowBoundingBox() { return showBoundingBox; }

    /**
     * Kiểm tra xem các tay cầm điều khiển cắt có đang được hiển thị hay không.
     * @return `true` nếu tay cầm cắt hiển thị, `false` nếu ẩn.
     */
    public boolean isShowCropHandles() { return showCropHandles; }

    /**
     * Kiểm tra xem khung chữ ký có thể thay đổi kích thước bởi người dùng hay không.
     * @return `true` nếu khung có thể thay đổi kích thước, `false` nếu cố định.
     */
    public boolean isFrameResizable() { return isFrameResizable; }

    /**
     * Trả về góc xoay hiện tại của chữ ký/khung.
     * @return Góc xoay (float) tính bằng radian.
     */
    public float getRotationAngle() { return rotationAngle; }

    // --- Các phương thức setter để thay đổi trạng thái ---

    /**
     * Đặt chế độ vẽ.
     * @param drawingMode `true` để bật chế độ vẽ, `false` để tắt.
     */
    public void setDrawingMode(boolean drawingMode) { this.isDrawingMode = drawingMode; }

    /**
     * Đặt trạng thái hiển thị của khung chữ ký.
     * @param show `true` để hiển thị, `false` để ẩn.
     */
    public void setShowSignatureFrame(boolean show) { this.showSignatureFrame = show; }

    /**
     * Đặt trạng thái hiển thị của hộp giới hạn.
     * @param show `true` để hiển thị, `false` để ẩn.
     */
    public void setShowBoundingBox(boolean show) { this.showBoundingBox = show; }

    /**
     * Đặt trạng thái hiển thị của các tay cầm cắt.
     * @param show `true` để hiển thị, `false` để ẩn.
     */
    public void setShowCropHandles(boolean show) { this.showCropHandles = show; }

    /**
     * Đặt khả năng thay đổi kích thước của khung chữ ký.
     * @param resizable `true` để cho phép thay đổi kích thước, `false` để cố định.
     */
    public void setFrameResizable(boolean resizable) { this.isFrameResizable = resizable; }

    /**
     * Đặt góc xoay cho chữ ký/khung.
     * @param angle Góc xoay (float) tính bằng radian.
     */
    public void setRotationAngle(float angle) { this.rotationAngle = angle; }

    /**
     * Đặt một Bitmap làm lớp phủ.
     * @param bitmap Đối tượng `Bitmap` sẽ được dùng làm lớp phủ.
     */
    public void setOverlayBitmap(Bitmap bitmap) { this.overlayBitmap = bitmap; }
}