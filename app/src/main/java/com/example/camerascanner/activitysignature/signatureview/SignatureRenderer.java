package com.example.camerascanner.activitysignature.signatureview;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;

/**
 * Lớp **SignatureRenderer** chịu trách nhiệm vẽ tất cả các thành phần liên quan đến chữ ký
 * lên đối tượng `Canvas`. Nó sử dụng `SignaturePaintManager` để lấy các đối tượng `Paint`,
 * `SignatureStateManager` để truy cập trạng thái hiện tại của chữ ký (đường dẫn, bitmap, khung, hộp, v.v.),
 * và `SignatureGeometryUtils` cho các phép toán hình học.
 */
public class SignatureRenderer {
    // Kích thước cố định cho các điểm điều khiển (handles) để điều chỉnh kích thước hoặc xoay.
    private static final int HANDLE_SIZE = 50;

    // Đối tượng quản lý các bút vẽ (Paint) cho các thành phần khác nhau.
    private SignaturePaintManager paintManager;
    // Đối tượng quản lý trạng thái hiện tại của chữ ký (đường dẫn, bitmap, khung, hộp, cờ hiển thị, v.v.).
    private SignatureStateManager stateManager;
    // Đối tượng cung cấp các tiện ích hình học (ví dụ: xoay điểm, tính khoảng cách).
    private SignatureGeometryUtils geometryUtils;

    /**
     * Constructor khởi tạo **SignatureRenderer** với các đối tượng quản lý cần thiết.
     * @param paintManager Đối tượng quản lý các bút vẽ.
     * @param stateManager Đối tượng quản lý trạng thái chữ ký.
     * @param geometryUtils Đối tượng cung cấp các tiện ích hình học.
     */
    public SignatureRenderer(SignaturePaintManager paintManager, SignatureStateManager stateManager, SignatureGeometryUtils geometryUtils) {
        this.paintManager = paintManager;
        this.stateManager = stateManager;
        this.geometryUtils = geometryUtils;
    }

    /**
     * Phương thức chính để vẽ tất cả các thành phần của chữ ký lên Canvas.
     * Logic vẽ được điều khiển bởi các cờ trạng thái trong `stateManager`.
     *
     * @param canvas Đối tượng Canvas để vẽ lên.
     * @param width Chiều rộng của view hiển thị.
     * @param height Chiều cao của view hiển thị.
     */
    public void draw(Canvas canvas, int width, int height) {
        // Nếu đang ở chế độ vẽ, vẽ nền cho toàn bộ view.
        if (stateManager.isDrawingMode()) {
            canvas.drawRect(0, 0, width, height, paintManager.getBackgroundPaint());
        }

        // Nếu khung chữ ký được hiển thị và có thể thay đổi kích thước, vẽ khung chữ ký.
        if (stateManager.isShowSignatureFrame() && stateManager.isFrameResizable()) {
            drawSignatureFrame(canvas);
        }

        // Lưu trạng thái hiện tại của Canvas trước khi thực hiện các phép biến đổi (xoay, cắt).
        canvas.save();

        // Nếu có góc xoay khác 0, thực hiện xoay Canvas quanh tâm của khung chữ ký.
        if (stateManager.getRotationAngle() != 0f) {
            RectF frame = stateManager.getSignatureFrame();
            // Chuyển đổi góc từ radian sang độ và xoay Canvas.
            canvas.rotate((float) Math.toDegrees(stateManager.getRotationAngle()), frame.centerX(), frame.centerY());
        }

        // Nếu khung chữ ký được hiển thị, cắt Canvas theo hình dạng của khung.
        // Điều này đảm bảo rằng chữ ký và bitmap phủ chỉ hiển thị bên trong khung.
        if (stateManager.isShowSignatureFrame()) {
            canvas.clipRect(stateManager.getSignatureFrame());
        }

        // Vẽ đường dẫn chữ ký lên Canvas.
        canvas.drawPath(stateManager.getSignaturePath(), paintManager.getSignaturePaint());

        // Nếu có bitmap phủ, vẽ bitmap đó lên Canvas, vừa với khung chữ ký.
        if (stateManager.getOverlayBitmap() != null) {
            canvas.drawBitmap(stateManager.getOverlayBitmap(), null, stateManager.getSignatureFrame(), null);
        }

        // Khôi phục trạng thái Canvas về trạng thái đã lưu trước đó (hủy bỏ xoay và cắt).
        canvas.restore();

        // Nếu hộp giới hạn được hiển thị và không rỗng, vẽ hộp giới hạn.
        if (stateManager.isShowBoundingBox() && !stateManager.getBoundingBox().isEmpty()) {
            drawBoundingBox(canvas);
        }

        // Nếu các tay cầm cắt được hiển thị và hộp cắt không rỗng, vẽ hộp cắt và tay cầm cắt.
        if (stateManager.isShowCropHandles() && !stateManager.getCropBox().isEmpty()) {
            drawCropBox(canvas);
            drawSingleCropHandle(canvas);
        }
    }

    /**
     * Vẽ khung chữ ký lên Canvas. Khung này có thể xoay và có một tay cầm điều khiển.
     * @param canvas Đối tượng Canvas để vẽ lên.
     */
    private void drawSignatureFrame(Canvas canvas) {
        // Lưu trạng thái Canvas trước khi xoay khung.
        canvas.save();

        // Nếu có góc xoay khác 0, xoay Canvas quanh tâm của khung.
        if (stateManager.getRotationAngle() != 0f) {
            RectF frame = stateManager.getSignatureFrame();
            canvas.rotate((float) Math.toDegrees(stateManager.getRotationAngle()), frame.centerX(), frame.centerY());
        }

        // Vẽ hình chữ nhật của khung chữ ký bằng Paint đã cấu hình.
        canvas.drawRect(stateManager.getSignatureFrame(), paintManager.getFramePaint());
        // Khôi phục trạng thái Canvas sau khi vẽ khung xoay.
        canvas.restore();

        // Nếu khung có thể thay đổi kích thước, vẽ tay cầm điều khiển duy nhất cho khung.
        if (stateManager.isFrameResizable()) {
            drawSingleFrameHandle(canvas);
        }

        // Nếu không có chữ ký hoặc bitmap phủ, vẽ văn bản hướng dẫn lên khung.
        if (stateManager.getSignaturePath().isEmpty() && stateManager.getOverlayBitmap() == null) {
            drawInstructionText(canvas);
        }
    }

    /**
     * Vẽ văn bản hướng dẫn lên Canvas, thường là khi không có chữ ký hoặc ảnh nào được tải.
     * @param canvas Đối tượng Canvas để vẽ lên.
     */
    private void drawInstructionText(Canvas canvas) {
        // Khởi tạo và cấu hình Paint cho văn bản.
        Paint textPaint = new Paint();
        textPaint.setAntiAlias(true); // Bật khử răng cưa cho văn bản.
        textPaint.setTextAlign(Paint.Align.CENTER); // Căn giữa văn bản.

        RectF frame = stateManager.getSignatureFrame(); // Lấy khung chữ ký để định vị văn bản.

        // Vẽ dòng chữ "Ký tên ở đây".
        textPaint.setColor(Color.parseColor("#666666")); // Màu xám đậm.
        textPaint.setTextSize(32); // Kích thước chữ lớn hơn.
        canvas.drawText("Ký tên ở đây", frame.centerX(), frame.centerY() - 20, textPaint);

        // Vẽ dòng chữ "Kéo góc để thay đổi kích thước và xoay".
        textPaint.setColor(Color.parseColor("#999999")); // Màu xám nhạt hơn.
        textPaint.setTextSize(20); // Kích thước chữ nhỏ hơn.
        canvas.drawText("Kéo góc để thay đổi kích thước và xoay", frame.centerX(), frame.centerY() + 20, textPaint);
    }

    /**
     * Vẽ một tay cầm điều khiển duy nhất cho khung chữ ký (thường là ở góc dưới bên trái).
     * Tay cầm này cho phép thay đổi kích thước và xoay khung.
     * @param canvas Đối tượng Canvas để vẽ lên.
     */
    private void drawSingleFrameHandle(Canvas canvas) {
        // Khởi tạo và cấu hình Paint cho tay cầm.
        Paint handlePaint = new Paint();
        handlePaint.setColor(Color.parseColor("#FFFF00")); // Màu xanh lá cây.
        handlePaint.setStyle(Paint.Style.FILL); // Tô đầy hình tròn.
        handlePaint.setAntiAlias(true); // Bật khử răng cưa.

        float handleRadius = HANDLE_SIZE / 2f; // Bán kính của tay cầm.
        RectF frame = stateManager.getSignatureFrame(); // Lấy khung chữ ký.

        // Xác định vị trí ban đầu của tay cầm (góc dưới bên trái của khung).
        float handleX = frame.left;
        float handleY = frame.bottom;

        // Nếu khung chữ ký đã được xoay, xoay vị trí của tay cầm tương ứng.
        if (stateManager.getRotationAngle() != 0f) {
            float[] point = geometryUtils.rotatePoint(handleX, handleY, frame.centerX(), frame.centerY(), stateManager.getRotationAngle());
            handleX = point[0];
            handleY = point[1];
        }

        // Vẽ hình tròn cho tay cầm.
        canvas.drawCircle(handleX, handleY, handleRadius, handlePaint);

        // Khởi tạo và cấu hình Paint cho đường viền của tay cầm.
        Paint borderPaint = new Paint();
        borderPaint.setColor(Color.WHITE); // Màu trắng.
        borderPaint.setStyle(Paint.Style.STROKE); // Chỉ vẽ nét viền.
        borderPaint.setStrokeWidth(3f); // Độ rộng nét viền.
        borderPaint.setAntiAlias(true); // Bật khử răng cưa.
        // Vẽ đường viền hình tròn cho tay cầm.
        canvas.drawCircle(handleX, handleY, handleRadius, borderPaint);
    }

    /**
     * Vẽ hộp giới hạn (bounding box) xung quanh chữ ký hoặc nội dung.
     * @param canvas Đối tượng Canvas để vẽ lên.
     */
    private void drawBoundingBox(Canvas canvas) {
        // Vẽ hình chữ nhật của hộp giới hạn bằng Paint đã cấu hình trong PaintManager.
        canvas.drawRect(stateManager.getBoundingBox(), paintManager.getBoundingBoxPaint());

        // Khởi tạo và cấu hình Paint cho văn bản.
        Paint textPaint = new Paint();
        textPaint.setColor(Color.WHITE); // Màu trắng.
        textPaint.setStyle(Paint.Style.FILL); // Tô đầy văn bản.
        textPaint.setAntiAlias(true); // Bật khử răng cưa.
        textPaint.setTextSize(24); // Kích thước chữ.

        RectF bbox = stateManager.getBoundingBox(); // Lấy hộp giới hạn.
        // Vẽ văn bản "Bounding Box" phía trên hộp giới hạn.
        canvas.drawText("Bounding Box", bbox.left, bbox.top - 10, textPaint);
    }

    /**
     * Vẽ hộp cắt (crop box) lên Canvas.
     * @param canvas Đối tượng Canvas để vẽ lên.
     */
    private void drawCropBox(Canvas canvas) {
        // Khởi tạo và cấu hình Paint cho hộp cắt.
        Paint cropBoxPaint = new Paint();
        cropBoxPaint.setColor(Color.parseColor("#2196F3")); // Màu xanh dương.
        cropBoxPaint.setStyle(Paint.Style.STROKE); // Chỉ vẽ nét viền.
        cropBoxPaint.setStrokeWidth(6f); // Độ rộng nét viền.
        cropBoxPaint.setAntiAlias(true); // Bật khử răng cưa.

        // Vẽ hình chữ nhật của hộp cắt.
        canvas.drawRect(stateManager.getCropBox(), cropBoxPaint);

        // Khởi tạo và cấu hình Paint cho văn bản.
        Paint textPaint = new Paint();
        textPaint.setColor(Color.parseColor("#2196F3")); // Màu xanh dương.
        textPaint.setStyle(Paint.Style.FILL); // Tô đầy văn bản.
        textPaint.setAntiAlias(true); // Bật khử răng cưa.
        textPaint.setTextSize(20); // Kích thước chữ.

        RectF cropBox = stateManager.getCropBox(); // Lấy hộp cắt.
        // Vẽ văn bản "Crop Area" phía trên hộp cắt.
        canvas.drawText("Crop Area", cropBox.left, cropBox.top - 10, textPaint);
    }

    /**
     * Vẽ một tay cầm điều khiển duy nhất cho hộp cắt (thường là ở góc dưới bên trái).
     * Tay cầm này cho phép điều chỉnh kích thước của hộp cắt.
     * @param canvas Đối tượng Canvas để vẽ lên.
     */
    private void drawSingleCropHandle(Canvas canvas) {
        float handleRadius = HANDLE_SIZE / 2f; // Bán kính của tay cầm.
        RectF cropBox = stateManager.getCropBox(); // Lấy hộp cắt.

        // Xác định vị trí ban đầu của tay cầm (góc dưới bên trái của hộp cắt).
        float handleX = cropBox.left;
        float handleY = cropBox.bottom;

        // Nếu hộp cắt đã được xoay, xoay vị trí của tay cầm tương ứng.
        if (stateManager.getRotationAngle() != 0f) {
            float[] point = geometryUtils.rotatePoint(handleX, handleY, cropBox.centerX(), cropBox.centerY(), stateManager.getRotationAngle());
            handleX = point[0];
            handleY = point[1];
        }

        // Vẽ hình tròn cho tay cầm bằng Paint đã cấu hình trong PaintManager.
        canvas.drawCircle(handleX, handleY, handleRadius, paintManager.getCropHandlePaint());

        // Khởi tạo và cấu hình Paint cho đường viền của tay cầm.
        Paint borderPaint = new Paint();
        borderPaint.setColor(Color.WHITE); // Màu trắng.
        borderPaint.setStyle(Paint.Style.STROKE); // Chỉ vẽ nét viền.
        borderPaint.setStrokeWidth(3f); // Độ rộng nét viền.
        borderPaint.setAntiAlias(true); // Bật khử răng cưa.
        // Vẽ đường viền hình tròn cho tay cầm.
        canvas.drawCircle(handleX, handleY, handleRadius, borderPaint);
    }
}