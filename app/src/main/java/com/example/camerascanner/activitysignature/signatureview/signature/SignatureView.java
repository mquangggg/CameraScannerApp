package com.example.camerascanner.activitysignature.signatureview.signature;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.Nullable;

import com.example.camerascanner.activitysignature.signatureview.SignatureBitmapProcessor;
import com.example.camerascanner.activitysignature.signatureview.SignatureGeometryUtils;

/**
 * Lớp **SignatureView** là một View tùy chỉnh trong Android, được thiết kế để cho phép người dùng
 * vẽ chữ ký, tải ảnh làm lớp phủ, và chỉnh sửa (cắt, thay đổi kích thước, xoay) chữ ký hoặc ảnh đó.
 * Nó tích hợp nhiều lớp quản lý khác nhau để xử lý trạng thái, vẽ, tương tác chạm và xử lý bitmap.
 */
public class SignatureView extends View {

    // --- Các đối tượng quản lý thành phần (Component managers) ---
    // Quản lý trạng thái hiện tại của chữ ký (đường dẫn, khung, hộp, cờ hiển thị, v.v.).
    private SignatureStateManager stateManager;
    // Quản lý các đối tượng Paint (bút vẽ) cho các thành phần đồ họa khác nhau.
    private SignaturePaintManager paintManager;
    // Xử lý các sự kiện chạm từ người dùng để vẽ hoặc chỉnh sửa.
    private SignatureTouchHandler touchHandler;
    // Chịu trách nhiệm vẽ tất cả các thành phần lên Canvas.
    private SignatureRenderer renderer;
    // Xử lý các thao tác liên quan đến Bitmap (phát hiện hộp giới hạn, cắt, tải ảnh).
    private SignatureBitmapProcessor bitmapProcessor;
    // Cung cấp các phương thức tiện ích cho các phép toán hình học.
    private SignatureGeometryUtils geometryUtils;

    // --- Giao diện Callback (Callback interface) ---
    /**
     * Giao diện `OnSignatureChangeListener` định nghĩa các phương thức callback
     * để thông báo về các thay đổi trạng thái của chữ ký hoặc các thành phần liên quan
     * cho các lớp bên ngoài (ví dụ: Activity hoặc Fragment).
     */
    public interface OnSignatureChangeListener {
        /**
         * Được gọi khi chữ ký (đường dẫn vẽ tay) thay đổi.
         */
        void onSignatureChanged();
        /**
         * Được gọi khi hộp giới hạn (bounding box) của nội dung được phát hiện.
         * @param boundingBox Đối tượng RectF của hộp giới hạn đã phát hiện.
         */
        void onBoundingBoxDetected(RectF boundingBox);
        /**
         * Được gọi khi hộp cắt (crop box) thay đổi kích thước hoặc vị trí.
         * @param cropBox Đối tượng RectF của hộp cắt hiện tại.
         */
        void onCropBoxChanged(RectF cropBox);
        /**
         * Được gọi khi khung chữ ký (signature frame) thay đổi kích thước hoặc vị trí.
         * @param frame Đối tượng RectF của khung chữ ký hiện tại.
         */
        void onFrameResized(RectF frame);
    }

    // Biến listener để gửi các sự kiện callback.
    private OnSignatureChangeListener listener;

    /**
     * Constructor của SignatureView. Được gọi khi View được inflate từ XML.
     * @param context Ngữ cảnh của ứng dụng.
     * @param attrs Các thuộc tính XML của View.
     */
    public SignatureView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        initComponents(); // Khởi tạo các đối tượng quản lý.
    }

    /**
     * Khởi tạo tất cả các đối tượng quản lý thành phần (StateManager, PaintManager, v.v.).
     * Các đối tượng này hoạt động độc lập nhưng phối hợp với nhau để quản lý trạng thái và hành vi của View.
     */
    private void initComponents() {
        stateManager = new SignatureStateManager();
        paintManager = new SignaturePaintManager();
        geometryUtils = new SignatureGeometryUtils();
        touchHandler = new SignatureTouchHandler(stateManager, geometryUtils);
        renderer = new SignatureRenderer(paintManager, stateManager, geometryUtils);
        bitmapProcessor = new SignatureBitmapProcessor(stateManager, paintManager);
    }

    /**
     * Phương thức callback của View, được gọi khi kích thước của View thay đổi.
     * Thường được gọi lần đầu khi View được hiển thị.
     * @param w Chiều rộng mới của View.
     * @param h Chiều cao mới của View.
     * @param oldw Chiều rộng cũ của View.
     * @param oldh Chiều cao cũ của View.
     */
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        // Khởi tạo vị trí và kích thước ban đầu của khung chữ ký dựa trên kích thước của View.
        stateManager.initializeFrame(w, h);
    }

    /**
     * Phương thức callback của View, được gọi khi View cần được vẽ lại.
     * @param canvas Đối tượng Canvas để vẽ lên.
     */
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // Giao phó việc vẽ cho SignatureRenderer, truyền Canvas và kích thước View.
        renderer.draw(canvas, getWidth(), getHeight());
    }

    /**
     * Phương thức callback của View, được gọi khi có sự kiện chạm trên View.
     * @param event Đối tượng MotionEvent chứa thông tin về sự kiện chạm.
     * @return `true` nếu sự kiện được xử lý, `false` nếu không.
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX(); // Lấy tọa độ X của điểm chạm.
        float y = event.getY(); // Lấy tọa độ Y của điểm chạm.

        boolean handled; // Biến cờ để kiểm tra xem sự kiện đã được xử lý hay chưa.

        // Kiểm tra chế độ hiện tại (vẽ hay chỉnh sửa) để giao phó sự kiện chạm.
        if (stateManager.isDrawingMode()) {
            handled = touchHandler.handleDrawingTouch(event, x, y);
            // Nếu sự kiện được xử lý và là ACTION_DOWN (bắt đầu vẽ), thông báo chữ ký đã thay đổi.
            if (handled && event.getAction() == MotionEvent.ACTION_DOWN) {
                notifySignatureChanged();
            }
        } else { // Chế độ chỉnh sửa.
            handled = touchHandler.handleEditingTouch(event, x, y);
            // Nếu sự kiện được xử lý và là ACTION_UP (kết thúc thao tác chỉnh sửa),
            // thông báo khung hoặc hộp cắt đã thay đổi.
            if (handled && event.getAction() == MotionEvent.ACTION_UP) {
                notifyFrameOrCropChanged();
            }
        }

        // Nếu sự kiện chạm được xử lý bởi touchHandler.
        if (handled) {
            constrainBoxes(); // Ràng buộc lại vị trí/kích thước của các hộp để đảm bảo chúng hợp lệ.
            invalidate();     // Yêu cầu View vẽ lại (gọi onDraw()).
        }

        return handled; // Trả về `true` nếu sự kiện được xử lý.
    }

    /**
     * Ràng buộc vị trí và kích thước của hộp cắt và khung chữ ký.
     * Đảm bảo chúng nằm trong giới hạn của View và có kích thước tối thiểu.
     */
    private void constrainBoxes() {
        geometryUtils.constrainBoxes(
                stateManager.getCropBox(),      // Hộp cắt.
                stateManager.getSignatureFrame(), // Khung chữ ký.
                getWidth(),                     // Chiều rộng của View.
                getHeight()                     // Chiều cao của View.
        );
    }

    /**
     * Gửi callback `onSignatureChanged()` tới listener đã đăng ký.
     * Được gọi khi có thay đổi trong đường dẫn chữ ký.
     */
    private void notifySignatureChanged() {
        if (listener != null) {
            listener.onSignatureChanged();
        }
    }

    /**
     * Gửi callback `onCropBoxChanged()` hoặc `onFrameResized()` tới listener đã đăng ký.
     * Được gọi khi thao tác chỉnh sửa khung hoặc hộp cắt kết thúc.
     * Quyết định gửi callback nào dựa trên tay cầm đang hoạt động.
     */
    private void notifyFrameOrCropChanged() {
        if (listener != null) {
            int activeHandle = touchHandler.getActiveHandle();
            // Nếu tay cầm hoạt động liên quan đến hộp cắt (ID <= 100).
            if (activeHandle <= 100) {
                listener.onCropBoxChanged(new RectF(stateManager.getCropBox()));
            } else { // Nếu tay cầm hoạt động liên quan đến khung (ID > 100).
                listener.onFrameResized(new RectF(stateManager.getSignatureFrame()));
            }
        }
    }

    // --- Các phương thức API công khai để điều khiển SignatureView từ bên ngoài ---

    /**
     * Đặt chế độ hoạt động của View.
     * @param drawingMode `true` để bật chế độ vẽ tự do, `false` để bật chế độ chỉnh sửa.
     */
    public void setDrawingMode(boolean drawingMode) {
        stateManager.setDrawingMode(drawingMode);
        invalidate(); // Yêu cầu vẽ lại View để cập nhật giao diện.
    }

    /**
     * Đặt khả năng thay đổi kích thước của khung chữ ký.
     * @param resizable `true` để cho phép thay đổi kích thước, `false` để cố định khung.
     */
    public void setFrameResizable(boolean resizable) {
        stateManager.setFrameResizable(resizable);
        invalidate(); // Yêu cầu vẽ lại View.
    }

    /**
     * Đặt trạng thái hiển thị của khung chữ ký.
     * @param show `true` để hiển thị khung, `false` để ẩn khung.
     */
    public void showSignatureFrame(boolean show) {
        stateManager.setShowSignatureFrame(show);
        invalidate(); // Yêu cầu vẽ lại View.
    }

    /**
     * Xóa sạch chữ ký hiện có, đặt lại các hộp và cờ trạng thái về mặc định.
     */
    public void clear() {
        stateManager.clear(); // Xóa trạng thái trong stateManager.
        invalidate();         // Yêu cầu vẽ lại View.
        notifySignatureChanged(); // Thông báo chữ ký đã bị xóa.
    }

    /**
     * Phát hiện hộp giới hạn (bounding box) của chữ ký hoặc bitmap phủ.
     * Hộp giới hạn này bao quanh nội dung không trong suốt.
     */
    public void detectBoundingBox() {
        bitmapProcessor.detectBoundingBox(); // Thực hiện phát hiện hộp giới hạn.
        invalidate();                        // Yêu cầu vẽ lại View để hiển thị hộp giới hạn.

        // Thông báo hộp giới hạn đã được phát hiện cho listener.
        if (listener != null) {
            listener.onBoundingBoxDetected(new RectF(stateManager.getBoundingBox()));
        }
    }
    /**
     * Thiết lập độ dày nét vẽ cho chữ ký
     * @param strokeWidth Độ dày mong muốn (tính bằng pixel)
     */
    public void setStrokeWidth(float strokeWidth) {
        paintManager.setSignatureStrokeWidth(strokeWidth);
        // Không cần invalidate() vì chỉ áp dụng cho các nét vẽ mới
    }

    /**
     * Lấy độ dày nét vẽ hiện tại
     * @return Độ dày nét vẽ hiện tại
     */
    public float getStrokeWidth() {
        return paintManager.getSignatureStrokeWidth();
    }


    /**
     * Chuyển View sang chế độ cắt (crop mode).
     * Trong chế độ này, người dùng có thể điều chỉnh hộp cắt.
     */
    public void showCropMode() {
        stateManager.setDrawingMode(false); // Tắt chế độ vẽ.
        // Nếu hộp giới hạn chưa được phát hiện, tự động phát hiện nó.
        if (stateManager.getBoundingBox().isEmpty()) {
            detectBoundingBox();
        }
        stateManager.setShowCropHandles(true); // Hiển thị các tay cầm cắt.
        stateManager.setFrameResizable(false); // Tắt khả năng thay đổi kích thước khung chính.
        invalidate(); // Yêu cầu vẽ lại View.
    }

    /**
     * Thoát khỏi chế độ cắt (crop mode) và quay lại chế độ vẽ mặc định.
     */
    public void hideCropMode() {
        stateManager.setShowCropHandles(false); // Ẩn các tay cầm cắt.
        stateManager.setDrawingMode(true);      // Bật lại chế độ vẽ.
        stateManager.setFrameResizable(true);   // Bật lại khả năng thay đổi kích thước khung chính.
        invalidate(); // Yêu cầu vẽ lại View.
    }

    /**
     * Lấy Bitmap đã được cắt dựa trên hộp cắt hiện tại hoặc khung chữ ký.
     * @return Đối tượng Bitmap đã được cắt.
     */
    public Bitmap getCroppedBitmap() {
        return bitmapProcessor.getCroppedBitmap(getWidth(), getHeight());
    }

    /**
     * Lấy Bitmap chỉ chứa riêng chữ ký (đường dẫn vẽ tay).
     * @return Đối tượng Bitmap của chữ ký.
     */
    public Bitmap getSignatureBitmap() {
        return bitmapProcessor.getSignatureBitmap();
    }

    /**
     * Tải một Bitmap vào làm lớp phủ (overlay) trên View.
     * @param bitmap Bitmap cần tải.
     */
    public void loadBitmap(Bitmap bitmap) {
        bitmapProcessor.loadBitmap(bitmap, getWidth(), getHeight());
        invalidate(); // Yêu cầu vẽ lại View để hiển thị bitmap mới.
    }

    // --- Các phương thức Getter để truy cập trạng thái của View ---

    /**
     * Kiểm tra xem chữ ký hiện tại có rỗng (chưa có nét vẽ nào) hay không.
     * @return `true` nếu chữ ký rỗng, `false` nếu có nét vẽ.
     */
    public boolean isEmpty() {
        return stateManager.getSignaturePath().isEmpty();
    }

    /**
     * Trả về một bản sao của `RectF` đại diện cho khung chữ ký hiện tại.
     * @return `RectF` của khung chữ ký.
     */
    public RectF getSignatureFrame() {
        return new RectF(stateManager.getSignatureFrame());
    }

    /**
     * Trả về một bản sao của `RectF` đại diện cho hộp giới hạn hiện tại.
     * @return `RectF` của hộp giới hạn.
     */
    public RectF getBoundingBox() {
        return new RectF(stateManager.getBoundingBox());
    }

    /**
     * Trả về một bản sao của `RectF` đại diện cho hộp cắt hiện tại.
     * @return `RectF` của hộp cắt.
     */
    public RectF getCropBox() {
        return new RectF(stateManager.getCropBox());
    }

    /**
     * Trả về góc xoay hiện tại của chữ ký/khung.
     * @return Góc xoay (float) tính bằng radian.
     */
    public float getRotationAngle() {
        return stateManager.getRotationAngle();
    }

    // --- Các phương thức Setter để thay đổi trạng thái của View ---

    /**
     * Đặt khung chữ ký bằng một `RectF` mới.
     * Sau khi đặt, các hộp sẽ được ràng buộc lại và View sẽ được vẽ lại.
     * @param rectF `RectF` mới cho khung chữ ký.
     */
    public void setSignatureFrame(RectF rectF) {
        stateManager.getSignatureFrame().set(rectF); // Cập nhật khung trong stateManager.
        constrainBoxes(); // Ràng buộc lại các hộp.
        invalidate();     // Yêu cầu vẽ lại View.
    }

    /**
     * Đăng ký một listener để nhận các thông báo về thay đổi trạng thái của chữ ký.
     * @param listener Đối tượng triển khai giao diện `OnSignatureChangeListener`.
     */
    public void setOnSignatureChangeListener(OnSignatureChangeListener listener) {
        this.listener = listener;
    }

    // Thêm các method sau vào class SignatureView của bạn:

    /**
     * Thiết lập màu sắc cho chữ ký
     * @param color Màu sắc mong muốn (int color)
     */
    public void setSignatureColor(int color) {
        paintManager.setSignatureColor(color);
        // Không cần invalidate() vì chỉ áp dụng cho các nét vẽ mới
        // Nếu muốn áp dụng cho nét vẽ hiện tại thì cần invalidate()
        invalidate();
    }

    /**
     * Lấy màu sắc hiện tại của chữ ký
     * @return Màu sắc hiện tại
     */
    public int getSignatureColor() {
        return paintManager.getSignatureColor();
    }

    /**
     * Thiết lập màu nền cho View (tùy chọn)
     * @param color Màu nền mong muốn
     */
    public void setBackgroundColor(int color) {
        paintManager.setBackgroundColor(color);
        invalidate();
    }

    /**
     * Reset màu chữ ký về màu mặc định (đen)
     */
    public void resetSignatureColor() {
        paintManager.resetSignatureColor();
        invalidate();
    }
}