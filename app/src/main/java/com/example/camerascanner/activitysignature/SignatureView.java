package com.example.camerascanner.activitysignature;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.Nullable;

/**
 * Lớp SignatureView là một View tùy chỉnh cho phép người dùng vẽ chữ ký,
 * hiển thị và thao tác với các khung (frame), hộp giới hạn (bounding box) và
 * hộp cắt (crop box) cho chữ ký hoặc ảnh overlay.
 */
public class SignatureView extends View {

    // Đối tượng Paint để vẽ các thành phần khác nhau
    private Paint signaturePaint, framePaint, boundingBoxPaint, cropHandlePaint, backgroundPaint;

    // Các thành phần vẽ chính
    private Path signaturePath; // Đường dẫn của chữ ký được vẽ
    private RectF signatureFrame, boundingBox, cropBox; // Khung chữ ký, hộp giới hạn và hộp cắt
    private Bitmap overlayBitmap; // Ảnh Bitmap được phủ lên (nếu có)

    // Quản lý trạng thái của View
    private boolean isDrawingMode = true; // Chế độ vẽ hay chế độ chỉnh sửa
    private boolean showSignatureFrame = true; // Hiển thị khung chữ ký
    private boolean showBoundingBox = false; // Hiển thị hộp giới hạn
    private boolean showCropHandles = false; // Hiển thị tay cầm cắt (crop handles)
    private boolean isFrameResizable = true; // Khung chữ ký có thể thay đổi kích thước không

    // Xử lý chạm (Touch handling)
    private static final int HANDLE_SIZE = 50; // Kích thước tay cầm
    private static final int HANDLE_TOUCH_TOLERANCE = 80; // Ngưỡng dung sai khi chạm vào tay cầm
    private static final int FRAME_BORDER_WIDTH = 8; // Chiều rộng viền của khung

    private int activeHandle = -1; // Tay cầm đang được kéo (-1 nếu không có)
    private float lastTouchX, lastTouchY; // Tọa độ X, Y của lần chạm cuối cùng
    private float initialFrameWidth = 600f; // Chiều rộng ban đầu của khung chữ ký
    private float initialFrameHeight = 200f; // Chiều cao ban đầu của khung chữ ký

    // Xoay và thay đổi tỷ lệ (Rotation and scaling)
    private float rotationAngle = 0f; // Góc xoay hiện tại (radian)
    private float initialDistance = 0f; // Khoảng cách ban đầu từ tâm đến điểm chạm (dùng để scale)
    private float initialWidth = 0f; // Chiều rộng ban đầu của đối tượng đang được thay đổi kích thước
    private float initialHeight = 0f; // Chiều cao ban đầu của đối tượng đang được thay đổi kích thước
    private float initialAngle = 0f; // Góc ban đầu của điểm chạm so với tâm (dùng để xoay)

    // Callbacks cho các sự kiện thay đổi
    private OnSignatureChangeListener listener;

    /**
     * Interface định nghĩa các callback khi có sự kiện thay đổi trên chữ ký hoặc các khung.
     */
    public interface OnSignatureChangeListener {
        /**
         * Được gọi khi chữ ký thay đổi (ví dụ: khi đang vẽ).
         */
        void onSignatureChanged();

        /**
         * Được gọi khi hộp giới hạn của chữ ký được phát hiện.
         * @param boundingBox Đối tượng RectF của hộp giới hạn.
         */
        void onBoundingBoxDetected(RectF boundingBox);

        /**
         * Được gọi khi hộp cắt thay đổi kích thước hoặc vị trí.
         * @param cropBox Đối tượng RectF của hộp cắt.
         */
        void onCropBoxChanged(RectF cropBox);

        /**
         * Được gọi khi khung chữ ký thay đổi kích thước hoặc vị trí.
         * @param frame Đối tượng RectF của khung chữ ký.
         */
        void onFrameResized(RectF frame);
    }

    /**
     * Constructor của SignatureView.
     * @param context Context của ứng dụng.
     * @param attrs Tập hợp các thuộc tính XML.
     */
    public SignatureView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        initPaints(); // Khởi tạo các đối tượng Paint
        initComponents(); // Khởi tạo các thành phần vẽ
    }

    /**
     * Khởi tạo và cấu hình các đối tượng Paint được sử dụng để vẽ.
     */
    private void initPaints() {
        signaturePaint = createPaint(Color.BLACK, 6f, Paint.Style.STROKE); // Paint cho chữ ký
        framePaint = createPaint(Color.parseColor("#4CAF50"), FRAME_BORDER_WIDTH, Paint.Style.STROKE); // Paint cho khung chữ ký
        framePaint.setPathEffect(new android.graphics.DashPathEffect(new float[]{20, 10}, 0)); // Tạo hiệu ứng nét đứt cho khung
        boundingBoxPaint = createPaint(Color.WHITE, 4f, Paint.Style.STROKE); // Paint cho hộp giới hạn
        cropHandlePaint = createPaint(Color.BLUE, 4f, Paint.Style.FILL_AND_STROKE); // Paint cho tay cầm cắt
        backgroundPaint = createPaint(Color.parseColor("#f8f8f8"), 0f, Paint.Style.FILL); // Paint cho nền khi vẽ
    }

    /**
     * Tạo một đối tượng Paint với các thuộc tính cơ bản.
     * @param color Màu sắc của Paint.
     * @param strokeWidth Độ rộng nét vẽ (nếu Style là STROKE).
     * @param style Kiểu vẽ (STROKE, FILL, FILL_AND_STROKE).
     * @return Đối tượng Paint đã được cấu hình.
     */
    private Paint createPaint(int color, float strokeWidth, Paint.Style style) {
        Paint paint = new Paint();
        paint.setColor(color);
        paint.setStrokeWidth(strokeWidth);
        paint.setStyle(style);
        paint.setAntiAlias(true); // Bật khử răng cưa
        if (style == Paint.Style.STROKE) {
            paint.setStrokeJoin(Paint.Join.ROUND); // Kiểu nối nét vẽ
            paint.setStrokeCap(Paint.Cap.ROUND); // Kiểu đầu nét vẽ
        }
        return paint;
    }

    /**
     * Khởi tạo các thành phần vẽ như Path và RectF.
     */
    private void initComponents() {
        signaturePath = new Path(); // Khởi tạo đường dẫn chữ ký
        signatureFrame = new RectF(); // Khởi tạo khung chữ ký
        boundingBox = new RectF(); // Khởi tạo hộp giới hạn
        cropBox = new RectF(); // Khởi tạo hộp cắt
    }

    /**
     * Được gọi khi kích thước của View thay đổi.
     * Thiết lập vị trí và kích thước ban đầu của khung chữ ký ở giữa View.
     * @param w Chiều rộng mới.
     * @param h Chiều cao mới.
     * @param oldw Chiều rộng cũ.
     * @param oldh Chiều cao cũ.
     */
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float centerX = w / 2f;
        float centerY = h / 2f;
        // Đặt khung chữ ký ở giữa View với kích thước ban đầu
        signatureFrame.set(
                centerX - initialFrameWidth / 2,
                centerY - initialFrameHeight / 2,
                centerX + initialFrameWidth / 2,
                centerY + initialFrameHeight / 2
        );
    }

    /**
     * Phương thức vẽ chính của View.
     * Vẽ chữ ký, khung, hộp giới hạn và các tay cầm dựa trên trạng thái hiện tại.
     * @param canvas Đối tượng Canvas để vẽ.
     */
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Nếu ở chế độ vẽ, vẽ nền màu xám nhạt
        if (isDrawingMode) {
            canvas.drawRect(0, 0, getWidth(), getHeight(), backgroundPaint);
        }

        // Nếu hiển thị khung chữ ký và khung có thể thay đổi kích thước, vẽ khung chữ ký
        if (showSignatureFrame && isFrameResizable) {
            drawSignatureFrame(canvas);
        }

        canvas.save(); // Lưu trạng thái hiện tại của Canvas

        // Áp dụng rotation cho signature frame
        if (rotationAngle != 0f) {
            canvas.rotate((float) Math.toDegrees(rotationAngle), signatureFrame.centerX(), signatureFrame.centerY());
        }

        // Nếu hiển thị khung chữ ký, cắt Canvas theo khung để chữ ký không vượt ra ngoài
        if (showSignatureFrame) {
            canvas.clipRect(signatureFrame);
        }

        canvas.drawPath(signaturePath, signaturePaint); // Vẽ chữ ký

        // Nếu có ảnh overlay, vẽ ảnh lên khung chữ ký
        if (overlayBitmap != null) {
            canvas.drawBitmap(overlayBitmap, null, signatureFrame, null);
        }

        canvas.restore(); // Khôi phục trạng thái Canvas đã lưu

        // Nếu hiển thị hộp giới hạn và hộp không rỗng, vẽ hộp giới hạn
        if (showBoundingBox && !boundingBox.isEmpty()) {
            drawBoundingBox(canvas);
        }

        // Nếu hiển thị tay cầm cắt và hộp cắt không rỗng, vẽ hộp cắt và tay cầm
        if (showCropHandles && !cropBox.isEmpty()) {
            drawCropBox(canvas);
            drawSingleCropHandle(canvas);
        }
    }

    /**
     * Vẽ khung chữ ký và tay cầm thay đổi kích thước (nếu có).
     * @param canvas Đối tượng Canvas để vẽ.
     */
    private void drawSignatureFrame(Canvas canvas) {
        canvas.save(); // Lưu trạng thái Canvas để áp dụng xoay riêng cho frame

        // Áp dụng rotation cho frame
        if (rotationAngle != 0f) {
            canvas.rotate((float) Math.toDegrees(rotationAngle), signatureFrame.centerX(), signatureFrame.centerY());
        }

        canvas.drawRect(signatureFrame, framePaint); // Vẽ hình chữ nhật của khung
        canvas.restore(); // Khôi phục trạng thái Canvas

        // Nếu khung có thể thay đổi kích thước, vẽ tay cầm
        if (isFrameResizable) {
            drawSingleFrameHandle(canvas);
        }

        // Nếu chữ ký và ảnh overlay đều rỗng, vẽ chữ hướng dẫn
        if (signaturePath.isEmpty() && overlayBitmap == null) {
            drawInstructionText(canvas);
        }
    }

    /**
     * Vẽ văn bản hướng dẫn bên trong khung chữ ký khi không có chữ ký hoặc ảnh.
     * @param canvas Đối tượng Canvas để vẽ.
     */
    private void drawInstructionText(Canvas canvas) {
        Paint textPaint = new Paint();
        textPaint.setAntiAlias(true);
        textPaint.setTextAlign(Paint.Align.CENTER);

        textPaint.setColor(Color.parseColor("#666666"));
        textPaint.setTextSize(32);
        canvas.drawText("Ký tên ở đây", signatureFrame.centerX(), signatureFrame.centerY() - 20, textPaint);

        textPaint.setColor(Color.parseColor("#999999"));
        textPaint.setTextSize(20);
        canvas.drawText("Kéo góc để thay đổi kích thước và xoay", signatureFrame.centerX(), signatureFrame.centerY() + 20, textPaint);
    }

    /**
     * Vẽ một tay cầm duy nhất ở góc dưới bên trái của khung chữ ký để thay đổi kích thước và xoay.
     * @param canvas Đối tượng Canvas để vẽ.
     */
    private void drawSingleFrameHandle(Canvas canvas) {
        Paint handlePaint = createPaint(Color.parseColor("#4CAF50"), 0f, Paint.Style.FILL);
        float handleRadius = HANDLE_SIZE / 2f;

        // Tính toán vị trí handle sau khi xoay
        float handleX = signatureFrame.left;
        float handleY = signatureFrame.bottom;

        if (rotationAngle != 0f) {
            float[] point = rotatePoint(handleX, handleY, signatureFrame.centerX(), signatureFrame.centerY(), rotationAngle);
            handleX = point[0];
            handleY = point[1];
        }

        // Vẽ tay cầm tại vị trí đã xoay
        canvas.drawCircle(handleX, handleY, handleRadius, handlePaint);

        // Vẽ viền trắng cho dễ nhìn
        Paint borderPaint = createPaint(Color.WHITE, 3f, Paint.Style.STROKE);
        canvas.drawCircle(handleX, handleY, handleRadius, borderPaint);
    }

    /**
     * Vẽ hộp giới hạn (Bounding Box) và văn bản chỉ dẫn.
     * @param canvas Đối tượng Canvas để vẽ.
     */
    private void drawBoundingBox(Canvas canvas) {
        canvas.drawRect(boundingBox, boundingBoxPaint); // Vẽ hình chữ nhật của hộp giới hạn
        Paint textPaint = createPaint(Color.WHITE, 0f, Paint.Style.FILL);
        textPaint.setTextSize(24);
        canvas.drawText("Bounding Box", boundingBox.left, boundingBox.top - 10, textPaint); // Vẽ chữ "Bounding Box"
    }

    /**
     * Vẽ hộp cắt (Crop Box) và văn bản chỉ dẫn.
     * @param canvas Đối tượng Canvas để vẽ.
     */
    private void drawCropBox(Canvas canvas) {
        Paint cropBoxPaint = createPaint(Color.parseColor("#2196F3"), 6f, Paint.Style.STROKE);
        canvas.drawRect(cropBox, cropBoxPaint); // Vẽ hình chữ nhật của hộp cắt

        Paint textPaint = createPaint(Color.parseColor("#2196F3"), 0f, Paint.Style.FILL);
        textPaint.setTextSize(20);
        canvas.drawText("Crop Area", cropBox.left, cropBox.top - 10, textPaint); // Vẽ chữ "Crop Area"
    }

    /**
     * Vẽ một tay cầm duy nhất ở góc dưới bên trái của hộp cắt để thay đổi kích thước và xoay.
     * @param canvas Đối tượng Canvas để vẽ.
     */
    private void drawSingleCropHandle(Canvas canvas) {
        float handleRadius = HANDLE_SIZE / 2f;
        Paint borderPaint = createPaint(Color.WHITE, 3f, Paint.Style.STROKE);

        // Tính toán vị trí handle sau khi xoay
        float handleX = cropBox.left;
        float handleY = cropBox.bottom;

        if (rotationAngle != 0f) {
            float[] point = rotatePoint(handleX, handleY, cropBox.centerX(), cropBox.centerY(), rotationAngle);
            handleX = point[0];
            handleY = point[1];
        }

        // Chỉ vẽ 1 điểm ở góc dưới bên trái của crop box
        canvas.drawCircle(handleX, handleY, handleRadius, cropHandlePaint); // Vẽ hình tròn của tay cầm
        canvas.drawCircle(handleX, handleY, handleRadius, borderPaint); // Vẽ viền trắng cho tay cầm
    }

    /**
     * Xử lý sự kiện chạm trên màn hình.
     * Phân biệt giữa chế độ vẽ và chế độ chỉnh sửa để xử lý chạm tương ứng.
     * @param event Đối tượng MotionEvent chứa thông tin về sự kiện chạm.
     * @return true nếu sự kiện đã được xử lý, false nếu không.
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        if (isDrawingMode) {
            return handleDrawingTouch(event, x, y); // Xử lý chạm ở chế độ vẽ
        } else {
            return handleEditingTouch(event, x, y); // Xử lý chạm ở chế độ chỉnh sửa
        }
    }

    /**
     * Xử lý sự kiện chạm khi ở chế độ vẽ chữ ký.
     * @param event Đối tượng MotionEvent.
     * @param x Tọa độ X của điểm chạm.
     * @param y Tọa độ Y của điểm chạm.
     * @return true nếu sự kiện đã được xử lý, false nếu không.
     */
    private boolean handleDrawingTouch(MotionEvent event, float x, float y) {
        // Nếu hiển thị khung chữ ký và điểm chạm nằm ngoài khung, không xử lý
        if (showSignatureFrame && !signatureFrame.contains(x, y)) {
            return false;
        }

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                signaturePath.moveTo(x, y); // Bắt đầu đường vẽ mới
                notifySignatureChanged(); // Thông báo chữ ký đã thay đổi
                break;
            case MotionEvent.ACTION_MOVE:
                signaturePath.lineTo(x, y); // Vẽ tiếp đường vẽ
                break;
            case MotionEvent.ACTION_UP:
                signaturePath.lineTo(x, y); // Kết thúc đường vẽ
                break;
        }
        invalidate(); // Yêu cầu vẽ lại View
        return true;
    }

    /**
     * Xử lý sự kiện chạm khi ở chế độ chỉnh sửa (thao tác với khung hoặc hộp cắt).
     * @param event Đối tượng MotionEvent.
     * @param x Tọa độ X của điểm chạm.
     * @param y Tọa độ Y của điểm chạm.
     * @return true nếu sự kiện đã được xử lý, false nếu không.
     */
    private boolean handleEditingTouch(MotionEvent event, float x, float y) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                return handleTouchDown(x, y); // Xử lý khi chạm xuống
            case MotionEvent.ACTION_MOVE:
                return handleTouchMove(x, y); // Xử lý khi di chuyển ngón tay
            case MotionEvent.ACTION_UP:
                return handleTouchUp(); // Xử lý khi nhấc ngón tay lên
        }
        return false;
    }

    /**
     * Xử lý sự kiện khi ngón tay chạm xuống màn hình ở chế độ chỉnh sửa.
     * Xác định xem người dùng có chạm vào một tay cầm hoặc một khung/hộp nào không.
     * @param x Tọa độ X của điểm chạm.
     * @param y Tọa độ Y của điểm chạm.
     * @return true nếu chạm vào một đối tượng có thể thao tác, false nếu không.
     */
    private boolean handleTouchDown(float x, float y) {
        lastTouchX = x;
        lastTouchY = y;

        // Kiểm tra tay cầm cắt
        if (showCropHandles) {
            activeHandle = getSingleCropHandleAtPoint(x, y);
            if (activeHandle != -1) {
                // Lưu trạng thái ban đầu để tính toán rotation và scale
                initialDistance = getDistance(x, y, cropBox.centerX(), cropBox.centerY());
                initialWidth = cropBox.width();
                initialHeight = cropBox.height();
                initialAngle = getAngle(x, y, cropBox.centerX(), cropBox.centerY());
                return true;
            }

            // Kiểm tra di chuyển hộp cắt
            if (cropBox.contains(x, y)) {
                activeHandle = 100; // Mã cho di chuyển hộp cắt
                return true;
            }
        }

        // Kiểm tra tay cầm khung chữ ký
        if (isFrameResizable && showSignatureFrame) {
            activeHandle = getSingleFrameHandleAtPoint(x, y);
            if (activeHandle != -1) {
                // Lưu trạng thái ban đầu để tính toán rotation và scale
                initialDistance = getDistance(x, y, signatureFrame.centerX(), signatureFrame.centerY());
                initialWidth = signatureFrame.width();
                initialHeight = signatureFrame.height();
                initialAngle = getAngle(x, y, signatureFrame.centerX(), signatureFrame.centerY());
                return true;
            }

            // Kiểm tra di chuyển khung chữ ký
            if (signatureFrame.contains(x, y)) {
                activeHandle = 200; // Mã cho di chuyển khung chữ ký
                return true;
            }
        }

        return false;
    }

    /**
     * Xử lý sự kiện khi ngón tay di chuyển trên màn hình ở chế độ chỉnh sửa.
     * Thực hiện thay đổi kích thước, xoay hoặc di chuyển đối tượng đang được thao tác.
     * @param x Tọa độ X hiện tại.
     * @param y Tọa độ Y hiện tại.
     * @return true nếu sự kiện đã được xử lý, false nếu không.
     */
    private boolean handleTouchMove(float x, float y) {
        if (activeHandle == -1) return false; // Không có tay cầm nào đang được kéo

        float dx = x - lastTouchX; // Khoảng cách di chuyển theo X
        float dy = y - lastTouchY; // Khoảng cách di chuyển theo Y

        if (activeHandle == 1) { // Tay cầm cắt
            updateCropBoxWithSingleHandle(x, y);
        } else if (activeHandle == 100) { // Di chuyển hộp cắt
            cropBox.offset(dx, dy);
        } else if (activeHandle == 2) { // Tay cầm khung
            updateSignatureFrameWithSingleHandle(x, y);
        } else if (activeHandle == 200) { // Di chuyển khung chữ ký
            signatureFrame.offset(dx, dy);
        }

        lastTouchX = x;
        lastTouchY = y;

        constrainBoxes(); // Đảm bảo các hộp không vượt quá giới hạn
        invalidate(); // Yêu cầu vẽ lại View
        return true;
    }

    /**
     * Xử lý sự kiện khi ngón tay nhấc lên khỏi màn hình ở chế độ chỉnh sửa.
     * Thông báo cho listener về sự thay đổi của hộp cắt hoặc khung.
     * @return true nếu sự kiện đã được xử lý, false nếu không.
     */
    private boolean handleTouchUp() {
        if (activeHandle == -1) return false;

        if (listener != null) {
            if (activeHandle <= 100) { // Nếu là thao tác với hộp cắt
                listener.onCropBoxChanged(new RectF(cropBox));
            } else { // Nếu là thao tác với khung chữ ký
                listener.onFrameResized(new RectF(signatureFrame));
            }
        }

        activeHandle = -1; // Reset tay cầm đang hoạt động
        return true;
    }

    /**
     * Kiểm tra xem một điểm chạm có nằm trong vùng của tay cầm cắt duy nhất không.
     * @param x Tọa độ X của điểm chạm.
     * @param y Tọa độ Y của điểm chạm.
     * @return 1 nếu chạm vào tay cầm cắt, -1 nếu không.
     */
    private int getSingleCropHandleAtPoint(float x, float y) {
        float tolerance = HANDLE_TOUCH_TOLERANCE / 2f;

        // Tính toán vị trí handle thực tế sau khi xoay
        float handleX = cropBox.left;
        float handleY = cropBox.bottom;

        if (rotationAngle != 0f) {
            float[] point = rotatePoint(handleX, handleY, cropBox.centerX(), cropBox.centerY(), rotationAngle);
            handleX = point[0];
            handleY = point[1];
        }

        // Kiểm tra điểm touch có trong vùng handle không
        if (Math.abs(x - handleX) < tolerance && Math.abs(y - handleY) < tolerance) {
            return 1; // Mã cho tay cầm cắt
        }
        return -1;
    }

    /**
     * Kiểm tra xem một điểm chạm có nằm trong vùng của tay cầm khung chữ ký duy nhất không.
     * @param x Tọa độ X của điểm chạm.
     * @param y Tọa độ Y của điểm chạm.
     * @return 2 nếu chạm vào tay cầm khung, -1 nếu không.
     */
    private int getSingleFrameHandleAtPoint(float x, float y) {
        float tolerance = HANDLE_TOUCH_TOLERANCE / 2f;

        // Tính toán vị trí handle thực tế sau khi xoay
        float handleX = signatureFrame.left;
        float handleY = signatureFrame.bottom;

        if (rotationAngle != 0f) {
            float[] point = rotatePoint(handleX, handleY, signatureFrame.centerX(), signatureFrame.centerY(), rotationAngle);
            handleX = point[0];
            handleY = point[1];
        }

        // Kiểm tra điểm touch có trong vùng handle không
        if (Math.abs(x - handleX) < tolerance && Math.abs(y - handleY) < tolerance) {
            return 2; // Mã cho tay cầm khung
        }
        return -1;
    }

    /**
     * Cập nhật kích thước và xoay của hộp cắt dựa trên thao tác với một tay cầm.
     * @param x Tọa độ X hiện tại của điểm chạm.
     * @param y Tọa độ Y hiện tại của điểm chạm.
     */
    private void updateCropBoxWithSingleHandle(float x, float y) {
        float centerX = cropBox.centerX();
        float centerY = cropBox.centerY();

        // Tính toán khoảng cách từ center đến điểm touch
        float currentDistance = getDistance(x, y, centerX, centerY);

        // Tính tỉ lệ scale
        float scale = currentDistance / initialDistance;

        // Tính toán góc xoay hiện tại
        float currentAngle = getAngle(x, y, centerX, centerY);
        rotationAngle = currentAngle - initialAngle; // Cập nhật góc xoay của View

        // Cập nhật kích thước crop box
        float newWidth = initialWidth * scale;
        float newHeight = initialHeight * scale;

        cropBox.set(
                centerX - newWidth / 2,
                centerY - newHeight / 2,
                centerX + newWidth / 2,
                centerY + newHeight / 2
        );
    }

    /**
     * Cập nhật kích thước và xoay của khung chữ ký dựa trên thao tác với một tay cầm.
     * @param x Tọa độ X hiện tại của điểm chạm.
     * @param y Tọa độ Y hiện tại của điểm chạm.
     */
    private void updateSignatureFrameWithSingleHandle(float x, float y) {
        float centerX = signatureFrame.centerX();
        float centerY = signatureFrame.centerY();

        // Tính toán khoảng cách từ center đến điểm touch
        float currentDistance = getDistance(x, y, centerX, centerY);

        // Tính tỉ lệ scale
        float scale = currentDistance / initialDistance;

        // Tính toán góc xoay hiện tại
        float currentAngle = getAngle(x, y, centerX, centerY);
        rotationAngle = currentAngle - initialAngle; // Cập nhật góc xoay của View

        // Cập nhật kích thước signature frame
        float newWidth = initialWidth * scale;
        float newHeight = initialHeight * scale;

        signatureFrame.set(
                centerX - newWidth / 2,
                centerY - newHeight / 2,
                centerX + newWidth / 2,
                centerY + newHeight / 2
        );
    }

    /**
     * Tính khoảng cách giữa hai điểm.
     * @param x1 Tọa độ X của điểm 1.
     * @param y1 Tọa độ Y của điểm 1.
     * @param x2 Tọa độ X của điểm 2.
     * @param y2 Tọa độ Y của điểm 2.
     * @return Khoảng cách Euclidean giữa hai điểm.
     */
    private float getDistance(float x1, float y1, float x2, float y2) {
        return (float) Math.sqrt((x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2));
    }

    /**
     * Tính góc của đường thẳng nối từ điểm (x2, y2) đến (x1, y1) so với trục X dương.
     * @param x1 Tọa độ X của điểm cuối.
     * @param y1 Tọa độ Y của điểm cuối.
     * @param x2 Tọa độ X của điểm gốc.
     * @param y2 Tọa độ Y của điểm gốc.
     * @return Góc tính bằng radian.
     */
    private float getAngle(float x1, float y1, float x2, float y2) {
        return (float) Math.atan2(y1 - y2, x1 - x2);
    }

    /**
     * Xoay một điểm quanh một tâm nhất định.
     * @param x Tọa độ X của điểm cần xoay.
     * @param y Tọa độ Y của điểm cần xoay.
     * @param centerX Tọa độ X của tâm xoay.
     * @param centerY Tọa độ Y của tâm xoay.
     * @param angle Góc xoay (radian).
     * @return Mảng float chứa tọa độ [x, y] của điểm sau khi xoay.
     */
    private float[] rotatePoint(float x, float y, float centerX, float centerY, float angle) {
        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);

        float translatedX = x - centerX;
        float translatedY = y - centerY;

        float rotatedX = translatedX * cos - translatedY * sin;
        float rotatedY = translatedX * sin + translatedY * cos;

        return new float[]{rotatedX + centerX, rotatedY + centerY};
    }

    /**
     * Đảm bảo các hộp (cropBox, signatureFrame) nằm trong giới hạn và có kích thước tối thiểu.
     */
    private void constrainBoxes() {
        // Ràng buộc hộp cắt nằm trong khung chữ ký
        cropBox.intersect(signatureFrame);
        ensureMinSize(cropBox, HANDLE_SIZE * 2); // Đảm bảo kích thước tối thiểu cho hộp cắt

        // Ràng buộc khung chữ ký nằm trong View
        signatureFrame.intersect(0, 0, getWidth(), getHeight());
        ensureMinSize(signatureFrame, 200, 100); // Đảm bảo kích thước tối thiểu cho khung chữ ký
    }

    /**
     * Đảm bảo một RectF có kích thước tối thiểu bằng nhau cho chiều rộng và chiều cao.
     * @param rect RectF cần kiểm tra.
     * @param minSize Kích thước tối thiểu.
     */
    private void ensureMinSize(RectF rect, float minSize) {
        ensureMinSize(rect, minSize, minSize);
    }

    /**
     * Đảm bảo một RectF có chiều rộng và chiều cao tối thiểu.
     * @param rect RectF cần kiểm tra.
     * @param minWidth Chiều rộng tối thiểu.
     * @param minHeight Chiều cao tối thiểu.
     */
    private void ensureMinSize(RectF rect, float minWidth, float minHeight) {
        if (rect.width() < minWidth) {
            rect.right = rect.left + minWidth;
        }
        if (rect.height() < minHeight) {
            rect.bottom = rect.top + minHeight;
        }
    }

    // Các phương thức public

    /**
     * Thiết lập chế độ vẽ hoặc chỉnh sửa.
     * @param drawingMode true để bật chế độ vẽ, false để bật chế độ chỉnh sửa.
     */
    public void setDrawingMode(boolean drawingMode) {
        this.isDrawingMode = drawingMode;
        invalidate(); // Yêu cầu vẽ lại View
    }

    /**
     * Đặt khả năng thay đổi kích thước của khung chữ ký.
     * @param resizable true để cho phép thay đổi kích thước, false để khóa.
     */
    public void setFrameResizable(boolean resizable) {
        this.isFrameResizable = resizable;
        invalidate(); // Yêu cầu vẽ lại View
    }

    /**
     * Hiển thị hoặc ẩn khung chữ ký.
     * @param show true để hiển thị, false để ẩn.
     */
    public void showSignatureFrame(boolean show) {
        this.showSignatureFrame = show;
        invalidate(); // Yêu cầu vẽ lại View
    }

    /**
     * Xóa chữ ký hiện có, hộp giới hạn, hộp cắt và đặt lại các trạng thái.
     */
    public void clear() {
        signaturePath.reset(); // Xóa đường dẫn chữ ký
        boundingBox.setEmpty(); // Xóa hộp giới hạn
        cropBox.setEmpty(); // Xóa hộp cắt
        showBoundingBox = false; // Ẩn hộp giới hạn
        showCropHandles = false; // Ẩn tay cầm cắt
        isDrawingMode = true; // Chuyển về chế độ vẽ
        rotationAngle = 0f; // Đặt lại góc xoay
        invalidate(); // Yêu cầu vẽ lại View
        notifySignatureChanged(); // Thông báo chữ ký đã thay đổi (thành rỗng)
    }

    /**
     * Phát hiện và thiết lập hộp giới hạn (Bounding Box) cho chữ ký hoặc ảnh overlay.
     */
    public void detectBoundingBox() {
        if (signaturePath.isEmpty() && overlayBitmap == null) return; // Không có gì để phát hiện

        RectF detectedBox = new RectF();

        if (overlayBitmap != null) {
            detectedBox = findContentBounds(overlayBitmap); // Tìm giới hạn nội dung của ảnh overlay
            // Di chuyển hộp giới hạn đến vị trí tương đối với khung chữ ký
            detectedBox.offset(signatureFrame.left, signatureFrame.top);
        } else {
            signaturePath.computeBounds(detectedBox, true); // Tính toán giới hạn của đường dẫn chữ ký
            float padding = 10f; // Đặt giá trị padding bạn muốn (ví dụ: 10 pixels)
            // Bạn có thể điều chỉnh giá trị này.
            detectedBox.left -= padding;
            detectedBox.top -= padding;
            detectedBox.right += padding;
            detectedBox.bottom += padding;

            // Đảm bảo kích thước không âm hoặc bằng 0 sau khi thêm đệm
            if (detectedBox.width() <= 0 || detectedBox.height() <= 0) {
                // Xử lý trường hợp bounding box quá nhỏ hoặc không hợp lệ sau padding
                // Ví dụ: Đặt nó thành một kích thước mặc định nhỏ
                detectedBox.set(0, 0, 1, 1);
                }
        }

        boundingBox.set(detectedBox); // Đặt hộp giới hạn

        cropBox.set(boundingBox); // Đặt hộp cắt ban đầu bằng hộp giới hạn
        showBoundingBox = true; // Hiển thị hộp giới hạn
        invalidate(); // Yêu cầu vẽ lại View

        if (listener != null) {
            listener.onBoundingBoxDetected(new RectF(boundingBox)); // Thông báo hộp giới hạn đã được phát hiện
        }
    }

    /**
     * Tìm giới hạn của nội dung (pixel không trong suốt) trong một Bitmap.
     * @param bitmap Bitmap cần phân tích.
     * @return RectF chứa giới hạn của nội dung. Trả về RectF rỗng nếu không có nội dung.
     */
    private RectF findContentBounds(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int left = width, top = height, right = 0, bottom = 0;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (Color.alpha(bitmap.getPixel(x, y)) > 0) { // Nếu pixel không trong suốt
                    left = Math.min(left, x);
                    top = Math.min(top, y);
                    right = Math.max(right, x);
                    bottom = Math.max(bottom, y);
                }
            }
        }

        if (left >= right || top >= bottom) return new RectF(); // Không tìm thấy nội dung

        int padding = 20; // Thêm một chút padding
        return new RectF(
                Math.max(0, left - padding),
                Math.max(0, top - padding),
                Math.min(width, right + padding),
                Math.min(height, bottom + padding)
        );
    }

    /**
     * Chuyển sang chế độ cắt (crop mode).
     * Bật tay cầm cắt, ẩn chế độ vẽ và khóa khả năng thay đổi kích thước khung chữ ký.
     */
    public void showCropMode() {
        isDrawingMode = false; // Tắt chế độ vẽ
        if (boundingBox.isEmpty()) { // Nếu chưa có bounding box, phát hiện nó
            detectBoundingBox();
        }
        showCropHandles = true; // Hiển thị tay cầm cắt
        isFrameResizable = false; // Khóa thay đổi kích thước khung
        invalidate(); // Yêu cầu vẽ lại View
    }

    /**
     * Ẩn chế độ cắt (crop mode).
     * Ẩn tay cầm cắt, bật lại chế độ vẽ và cho phép thay đổi kích thước khung chữ ký.
     */
    public void hideCropMode() {
        showCropHandles = false; // Ẩn tay cầm cắt
        isDrawingMode = true; // Bật chế độ vẽ
        isFrameResizable = true; // Cho phép thay đổi kích thước khung
        invalidate(); // Yêu cầu vẽ lại View
    }

    /**
     * Lấy Bitmap đã được cắt theo hộp cắt hiện tại.
     * @return Bitmap đã cắt, hoặc null nếu không có gì để cắt.
     */
    public Bitmap getCroppedBitmap() {
        if (overlayBitmap != null && !signatureFrame.isEmpty()) {
            return cropBitmapFromFrame(); // Cắt ảnh overlay theo khung
        }

        if (!cropBox.isEmpty()) {
            return cropBitmapFromPath(); // Cắt chữ ký theo hộp cắt
        }

        return null;
    }

    /**
     * Cắt ảnh overlay dựa trên kích thước và vị trí của khung chữ ký.
     * @return Bitmap đã được cắt từ ảnh overlay.
     */
    private Bitmap cropBitmapFromFrame() {
        // Tạo một Bitmap tạm thời chứa toàn bộ View
        Bitmap fullView = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(fullView);
        // Vẽ ảnh overlay lên Bitmap tạm thời, căn chỉnh theo signatureFrame
        canvas.drawBitmap(overlayBitmap, null, signatureFrame, null);

        // Cắt phần Bitmap theo signatureFrame
        Bitmap cropped = Bitmap.createBitmap(fullView,
                (int) signatureFrame.left, (int) signatureFrame.top,
                (int) signatureFrame.width(), (int) signatureFrame.height());

        fullView.recycle(); // Giải phóng Bitmap tạm thời
        return cropped;
    }

    /**
     * Cắt chữ ký đã vẽ dựa trên kích thước và vị trí của hộp cắt.
     * @return Bitmap đã được cắt từ chữ ký.
     */
    private Bitmap cropBitmapFromPath() {
        // Tạo một Bitmap tạm thời để vẽ chữ ký lên đó
        Bitmap fullBitmap = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(fullBitmap);
        canvas.drawPath(signaturePath, signaturePaint); // Vẽ chữ ký lên Bitmap tạm thời

        // Cắt phần Bitmap theo cropBox
        Bitmap cropped = Bitmap.createBitmap(fullBitmap,
                (int) cropBox.left, (int) cropBox.top,
                (int) cropBox.width(), (int) cropBox.height());

        fullBitmap.recycle(); // Giải phóng Bitmap tạm thời
        return cropped;
    }

    /**
     * Lấy Bitmap của chữ ký (hoặc nội dung trong bounding box nếu có) với nền trong suốt.
     * @return Bitmap chứa chữ ký.
     */
    public Bitmap getSignatureBitmap() {
        // Chọn bounds để render: boundingBox nếu có, nếu không thì signatureFrame
        RectF bounds = boundingBox.isEmpty() ? signatureFrame : boundingBox;

        Bitmap bitmap = Bitmap.createBitmap(
                (int) bounds.width(),
                (int) bounds.height(),
                Bitmap.Config.ARGB_8888 // Định dạng với kênh alpha
        );

        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.TRANSPARENT); // Vẽ nền trong suốt
        // Dịch chuyển canvas để chữ ký nằm gọn trong Bitmap đã tạo
        canvas.translate(-bounds.left, -bounds.top);
        canvas.drawPath(signaturePath, signaturePaint); // Vẽ chữ ký

        return bitmap;
    }

    /**
     * Tải một Bitmap để làm ảnh overlay.
     * Khi tải ảnh, chữ ký hiện tại sẽ bị xóa và chế độ sẽ chuyển sang chỉnh sửa.
     * @param bitmap Bitmap cần tải.
     */
    public void loadBitmap(Bitmap bitmap) {
        if (bitmap == null) return;

        this.overlayBitmap = bitmap; // Đặt ảnh overlay
        signaturePath.reset(); // Xóa chữ ký hiện có

        // Đặt khung chữ ký bằng kích thước của ảnh overlay
        if (getWidth() > 0 && getHeight() > 0) { // Đảm bảo View đã có kích thước
            signatureFrame.set(0, 0, bitmap.getWidth(), bitmap.getHeight());
        }

        isDrawingMode = false; // Chuyển sang chế độ chỉnh sửa
        showSignatureFrame = true; // Đảm bảo khung chữ ký được hiển thị
        invalidate(); // Yêu cầu vẽ lại View
    }

    // Getters and setters (Các phương thức lấy và đặt giá trị)

    /**
     * Kiểm tra xem chữ ký có rỗng không (chưa có nét vẽ nào).
     * @return true nếu chữ ký rỗng, false nếu có nét vẽ.
     */
    public boolean isEmpty() { return signaturePath.isEmpty(); }

    /**
     * Lấy một bản sao của RectF đại diện cho khung chữ ký.
     * @return Bản sao của RectF khung chữ ký.
     */
    public RectF getSignatureFrame() { return new RectF(signatureFrame); }

    /**
     * Lấy một bản sao của RectF đại diện cho hộp giới hạn.
     * @return Bản sao của RectF hộp giới hạn.
     */
    public RectF getBoundingBox() { return new RectF(boundingBox); }

    /**
     * Lấy một bản sao của RectF đại diện cho hộp cắt.
     * @return Bản sao của RectF hộp cắt.
     */
    public RectF getCropBox() { return new RectF(cropBox); }

    /**
     * Lấy góc xoay hiện tại của View.
     * @return Góc xoay tính bằng radian.
     */
    public float getRotationAngle() { return rotationAngle; }

    /**
     * Thiết lập RectF cho khung chữ ký.
     * @param rectF RectF mới cho khung chữ ký.
     */
    public void setSignatureFrame(RectF rectF) {
        signatureFrame.set(rectF);
        constrainBoxes(); // Đảm bảo khung nằm trong giới hạn
        invalidate(); // Yêu cầu vẽ lại View
    }

    /**
     * Đặt listener cho các sự kiện thay đổi của chữ ký.
     * @param listener Đối tượng OnSignatureChangeListener.
     */
    public void setOnSignatureChangeListener(OnSignatureChangeListener listener) {
        this.listener = listener;
    }

    /**
     * Phương thức nội bộ để thông báo cho listener rằng chữ ký đã thay đổi.
     */
    private void notifySignatureChanged() {
        if (listener != null) {
            listener.onSignatureChanged();
        }
    }
}