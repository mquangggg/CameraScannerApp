package com.example.camerascanner.activitysignature.signatureview.signature;

import android.graphics.RectF;
import android.view.MotionEvent;

import com.example.camerascanner.activitysignature.signatureview.SignatureGeometryUtils;

/**
 * Lớp **SignatureTouchHandler** xử lý các tương tác chạm từ người dùng trên view chữ ký.
 * Nó quản lý cả chế độ vẽ tự do và chế độ chỉnh sửa (thay đổi kích thước, di chuyển, xoay khung/hộp cắt).
 */
public class SignatureTouchHandler {
    // Kích thước của tay cầm (handle) dùng để kéo, điều chỉnh.
    private static final int HANDLE_SIZE = 50;
    // Ngưỡng dung sai chạm, xác định khoảng cách tối đa từ điểm chạm đến tay cầm để được coi là chạm vào tay cầm.
    private static final int HANDLE_TOUCH_TOLERANCE = 80;

    // Biến lưu trữ ID của tay cầm đang được kích hoạt (đang được kéo). -1 nghĩa là không có tay cầm nào đang hoạt động.
    private int activeHandle = -1;
    // Tọa độ X và Y của lần chạm cuối cùng (dùng để tính toán độ dịch chuyển).
    private float lastTouchX, lastTouchY;
    // Khoảng cách ban đầu từ điểm chạm đến tâm của khung/hộp cắt khi bắt đầu thao tác.
    private float initialDistance = 0f;
    // Chiều rộng ban đầu của khung/hộp cắt khi bắt đầu thao tác.
    private float initialWidth = 0f;
    // Chiều cao ban đầu của khung/hộp cắt khi bắt đầu thao tác.
    private float initialHeight = 0f;
    // Góc ban đầu từ điểm chạm đến tâm của khung/hộp cắt khi bắt đầu thao tác.
    private float initialAngle = 0f;

    // Đối tượng quản lý trạng thái hiện tại của chữ ký (đường dẫn, khung, hộp, cờ, v.v.).
    private SignatureStateManager stateManager;
    // Đối tượng cung cấp các tiện ích hình học (ví dụ: tính khoảng cách, góc, xoay điểm).
    private SignatureGeometryUtils geometryUtils;

    /**
     * Constructor khởi tạo **SignatureTouchHandler**.
     *
     * @param stateManager Đối tượng quản lý trạng thái chữ ký.
     * @param geometryUtils Đối tượng cung cấp các tiện ích hình học.
     */
    public SignatureTouchHandler(SignatureStateManager stateManager, SignatureGeometryUtils geometryUtils) {
        this.stateManager = stateManager;
        this.geometryUtils = geometryUtils;
    }

    /**
     * Xử lý các sự kiện chạm khi ở chế độ vẽ.
     * Người dùng có thể vẽ tự do trên view. Nét vẽ chỉ được ghi nhận nếu nằm trong khung chữ ký.
     *
     * @param event Đối tượng MotionEvent chứa thông tin về sự kiện chạm.
     * @param x Tọa độ X của sự kiện chạm.
     * @param y Tọa độ Y của sự kiện chạm.
     * @return `true` nếu sự kiện được xử lý (nét vẽ được ghi nhận), `false` nếu không.
     */
    public boolean handleDrawingTouch(MotionEvent event, float x, float y) {
        // Nếu khung chữ ký hiển thị và điểm chạm nằm ngoài khung, bỏ qua sự kiện.
        if (stateManager.isShowSignatureFrame() && !stateManager.getSignatureFrame().contains(x, y)) {
            return false;
        }

        // Xử lý các loại hành động chạm khác nhau để vẽ đường dẫn.
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN: // Khi ngón tay chạm xuống.
                stateManager.getSignaturePath().moveTo(x, y); // Bắt đầu một đường nét mới tại điểm chạm.
                break;
            case MotionEvent.ACTION_MOVE: // Khi ngón tay di chuyển trong khi chạm.
                stateManager.getSignaturePath().lineTo(x, y); // Thêm một đoạn thẳng vào đường nét hiện tại đến điểm mới.
                break;
            case MotionEvent.ACTION_UP: // Khi ngón tay nhấc lên.
                stateManager.getSignaturePath().lineTo(x, y); // Hoàn thành đường nét tại điểm nhấc lên.
                break;
        }
        return true; // Đánh dấu sự kiện đã được xử lý.
    }

    /**
     * Xử lý các sự kiện chạm khi ở chế độ chỉnh sửa (thao tác với khung hoặc hộp cắt).
     *
     * @param event Đối tượng MotionEvent chứa thông tin về sự kiện chạm.
     * @param x Tọa độ X của sự kiện chạm.
     * @param y Tọa độ Y của sự kiện chạm.
     * @return `true` nếu sự kiện được xử lý, `false` nếu không.
     */
    public boolean handleEditingTouch(MotionEvent event, float x, float y) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN: // Khi ngón tay chạm xuống.
                return handleTouchDown(x, y);
            case MotionEvent.ACTION_MOVE: // Khi ngón tay di chuyển trong khi chạm.
                return handleTouchMove(x, y);
            case MotionEvent.ACTION_UP: // Khi ngón tay nhấc lên.
                return handleTouchUp();
        }
        return false; // Mặc định không xử lý nếu loại hành động không khớp.
    }

    /**
     * Xử lý sự kiện chạm xuống (ACTION_DOWN) trong chế độ chỉnh sửa.
     * Phương thức này xác định xem người dùng đang chạm vào một tay cầm (handle) hay vào thân của hộp/khung,
     * và lưu lại trạng thái ban đầu của thao tác.
     *
     * @param x Tọa độ X của điểm chạm.
     * @param y Tọa độ Y của điểm chạm.
     * @return `true` nếu một tay cầm hoặc thân hộp/khung được kích hoạt, `false` nếu không.
     */
    private boolean handleTouchDown(float x, float y) {
        lastTouchX = x; // Lưu lại tọa độ X chạm cuối cùng.
        lastTouchY = y; // Lưu lại tọa độ Y chạm cuối cùng.

        // Kiểm tra xem người dùng có chạm vào tay cầm cắt (crop handle) không.
        if (stateManager.isShowCropHandles()) {
            activeHandle = getSingleCropHandleAtPoint(x, y); // Lấy ID của tay cầm cắt nếu có.
            if (activeHandle != -1) { // Nếu chạm vào tay cầm cắt.
                RectF cropBox = stateManager.getCropBox();
                // Lưu các giá trị ban đầu để tính toán tỷ lệ và góc xoay sau này.
                initialDistance = geometryUtils.getDistance(x, y, cropBox.centerX(), cropBox.centerY());
                initialWidth = cropBox.width();
                initialHeight = cropBox.height();
                initialAngle = geometryUtils.getAngle(x, y, cropBox.centerX(), cropBox.centerY());
                return true; // Đánh dấu đã xử lý.
            }

            // Nếu không chạm vào tay cầm, kiểm tra xem có chạm vào thân của hộp cắt không.
            if (stateManager.getCropBox().contains(x, y)) {
                activeHandle = 100; // Đặt ID đặc biệt cho việc di chuyển hộp cắt.
                return true; // Đánh dấu đã xử lý.
            }
        }

        // Kiểm tra xem người dùng có chạm vào tay cầm khung (frame handle) không.
        // Chỉ kiểm tra nếu khung có thể thay đổi kích thước và đang hiển thị.
        if (stateManager.isFrameResizable() && stateManager.isShowSignatureFrame()) {
            activeHandle = getSingleFrameHandleAtPoint(x, y); // Lấy ID của tay cầm khung nếu có.
            if (activeHandle != -1) { // Nếu chạm vào tay cầm khung.
                RectF signatureFrame = stateManager.getSignatureFrame();
                // Lưu các giá trị ban đầu để tính toán tỷ lệ và góc xoay sau này.
                initialDistance = geometryUtils.getDistance(x, y, signatureFrame.centerX(), signatureFrame.centerY());
                initialWidth = signatureFrame.width();
                initialHeight = signatureFrame.height();
                initialAngle = geometryUtils.getAngle(x, y, signatureFrame.centerX(), signatureFrame.centerY());
                return true; // Đánh dấu đã xử lý.
            }

            // Nếu không chạm vào tay cầm, kiểm tra xem có chạm vào thân của khung chữ ký không.
            if (stateManager.getSignatureFrame().contains(x, y)) {
                activeHandle = 200; // Đặt ID đặc biệt cho việc di chuyển khung.
                return true; // Đánh dấu đã xử lý.
            }
        }

        return false; // Không có tay cầm hoặc thân hộp/khung nào được kích hoạt.
    }

    /**
     * Xử lý sự kiện di chuyển (ACTION_MOVE) trong chế độ chỉnh sửa.
     * Phương thức này cập nhật vị trí hoặc kích thước của hộp/khung dựa trên tay cầm đang hoạt động.
     *
     * @param x Tọa độ X hiện tại của điểm chạm.
     * @param y Tọa độ Y hiện tại của điểm chạm.
     * @return `true` nếu một tay cầm hoặc thân hộp/khung đang được kéo và đã được cập nhật, `false` nếu không.
     */
    private boolean handleTouchMove(float x, float y) {
        if (activeHandle == -1) return false; // Không có tay cầm nào đang hoạt động, không làm gì.

        float dx = x - lastTouchX; // Tính độ dịch chuyển theo trục X.
        float dy = y - lastTouchY; // Tính độ dịch chuyển theo trục Y.

        // Xử lý dựa trên ID của tay cầm đang hoạt động.
        if (activeHandle == 1) { // Tay cầm cắt (single crop handle).
            updateCropBoxWithSingleHandle(x, y); // Cập nhật kích thước và xoay hộp cắt.
        } else if (activeHandle == 100) { // Thân hộp cắt (di chuyển hộp cắt).
            stateManager.getCropBox().offset(dx, dy); // Di chuyển hộp cắt theo độ dịch chuyển.
        } else if (activeHandle == 2) { // Tay cầm khung (single frame handle).
            updateSignatureFrameWithSingleHandle(x, y); // Cập nhật kích thước và xoay khung.
        } else if (activeHandle == 200) { // Thân khung (di chuyển khung).
            stateManager.getSignatureFrame().offset(dx, dy); // Di chuyển khung theo độ dịch chuyển.
        }

        lastTouchX = x; // Cập nhật tọa độ X chạm cuối cùng.
        lastTouchY = y; // Cập nhật tọa độ Y chạm cuối cùng.
        return true; // Đánh dấu sự kiện đã được xử lý.
    }

    /**
     * Xử lý sự kiện nhấc ngón tay lên (ACTION_UP) trong chế độ chỉnh sửa.
     * Phương thức này đặt lại `activeHandle` về -1, kết thúc thao tác kéo.
     *
     * @return `true` nếu có tay cầm nào đó đang hoạt động trước khi nhấc ngón tay, `false` nếu không.
     */
    private boolean handleTouchUp() {
        if (activeHandle == -1) return false; // Không có tay cầm nào đang hoạt động, không làm gì.
        activeHandle = -1; // Đặt lại activeHandle, kết thúc thao tác.
        return true; // Đánh dấu sự kiện đã được xử lý.
    }

    /**
     * Xác định xem điểm chạm có nằm gần tay cầm cắt duy nhất hay không.
     *
     * @param x Tọa độ X của điểm chạm.
     * @param y Tọa độ Y của điểm chạm.
     * @return `1` nếu điểm chạm nằm gần tay cầm cắt, `-1` nếu không.
     */
    private int getSingleCropHandleAtPoint(float x, float y) {
        float tolerance = HANDLE_TOUCH_TOLERANCE / 2f; // Bán kính dung sai cho việc chạm vào tay cầm.
        RectF cropBox = stateManager.getCropBox(); // Lấy hộp cắt.

        // Xác định vị trí của tay cầm cắt (thường là góc dưới bên trái của hộp cắt).
        float handleX = cropBox.left;
        float handleY = cropBox.bottom;

        // Nếu hộp cắt đang bị xoay, tính toán lại vị trí của tay cầm sau khi xoay.
        if (stateManager.getRotationAngle() != 0f) {
            float[] point = geometryUtils.rotatePoint(handleX, handleY, cropBox.centerX(), cropBox.centerY(), stateManager.getRotationAngle());
            handleX = point[0];
            handleY = point[1];
        }

        // Kiểm tra xem điểm chạm có nằm trong khoảng dung sai so với tay cầm không.
        if (Math.abs(x - handleX) < tolerance && Math.abs(y - handleY) < tolerance) {
            return 1; // Trả về 1 nếu chạm vào tay cầm cắt.
        }
        return -1; // Không chạm vào tay cầm cắt.
    }

    /**
     * Xác định xem điểm chạm có nằm gần tay cầm khung duy nhất hay không.
     *
     * @param x Tọa độ X của điểm chạm.
     * @param y Tọa độ Y của điểm chạm.
     * @return `2` nếu điểm chạm nằm gần tay cầm khung, `-1` nếu không.
     */
    private int getSingleFrameHandleAtPoint(float x, float y) {
        float tolerance = HANDLE_TOUCH_TOLERANCE / 2f; // Bán kính dung sai cho việc chạm vào tay cầm.
        RectF signatureFrame = stateManager.getSignatureFrame(); // Lấy khung chữ ký.

        // Xác định vị trí của tay cầm khung (thường là góc dưới bên trái của khung).
        float handleX = signatureFrame.left;
        float handleY = signatureFrame.bottom;

        // Nếu khung đang bị xoay, tính toán lại vị trí của tay cầm sau khi xoay.
        if (stateManager.getRotationAngle() != 0f) {
            float[] point = geometryUtils.rotatePoint(handleX, handleY, signatureFrame.centerX(), signatureFrame.centerY(), stateManager.getRotationAngle());
            handleX = point[0];
            handleY = point[1];
        }

        // Kiểm tra xem điểm chạm có nằm trong khoảng dung sai so với tay cầm không.
        if (Math.abs(x - handleX) < tolerance && Math.abs(y - handleY) < tolerance) {
            return 2; // Trả về 2 nếu chạm vào tay cầm khung.
        }
        return -1; // Không chạm vào tay cầm khung.
    }

    /**
     * Cập nhật kích thước và góc xoay của hộp cắt dựa trên thao tác kéo tay cầm duy nhất.
     * Hộp cắt sẽ được thay đổi kích thước và xoay quanh tâm của nó.
     *
     * @param x Tọa độ X hiện tại của điểm chạm.
     * @param y Tọa độ Y hiện tại của điểm chạm.
     */
    private void updateCropBoxWithSingleHandle(float x, float y) {
        RectF cropBox = stateManager.getCropBox();
        float centerX = cropBox.centerX();  // Tâm X của hộp cắt.
        float centerY = cropBox.centerY();  // Tâm Y của hộp cắt.

        // Tính khoảng cách hiện tại từ điểm chạm đến tâm của hộp cắt.
        float currentDistance = geometryUtils.getDistance(x, y, centerX, centerY);
        // Tính tỷ lệ thay đổi kích thước dựa trên khoảng cách ban đầu và hiện tại.
        float scale = currentDistance / initialDistance;

        // Tính góc hiện tại từ điểm chạm đến tâm của hộp cắt.
        float currentAngle = geometryUtils.getAngle(x, y, centerX, centerY);
        // Cập nhật góc xoay của trạng thái quản lý (xoay tương đối so với góc ban đầu).
        stateManager.setRotationAngle(currentAngle - initialAngle);

        // Tính chiều rộng và chiều cao mới dựa trên tỷ lệ.
        float newWidth = initialWidth * scale;
        float newHeight = initialHeight * scale;

        // Cập nhật tọa độ của hộp cắt để nó được thay đổi kích thước và giữ nguyên tâm.
        cropBox.set(
                centerX - newWidth / 2,
                centerY - newHeight / 2,
                centerX + newWidth / 2,
                centerY + newHeight / 2
        );
    }

    /**
     * Cập nhật kích thước và góc xoay của khung chữ ký dựa trên thao tác kéo tay cầm duy nhất.
     * Khung sẽ được thay đổi kích thước và xoay quanh tâm của nó.
     *
     * @param x Tọa độ X hiện tại của điểm chạm.
     * @param y Tọa độ Y hiện tại của điểm chạm.
     */
    private void updateSignatureFrameWithSingleHandle(float x, float y) {
        RectF signatureFrame = stateManager.getSignatureFrame();
        float centerX = signatureFrame.centerX(); // Tâm X của khung.
        float centerY = signatureFrame.centerY(); // Tâm Y của khung.

        // Tính khoảng cách hiện tại từ điểm chạm đến tâm của khung.
        float currentDistance = geometryUtils.getDistance(x, y, centerX, centerY);
        // Tính tỷ lệ thay đổi kích thước dựa trên khoảng cách ban đầu và hiện tại.
        float scale = currentDistance / initialDistance;

        // Tính góc hiện tại từ điểm chạm đến tâm của khung.
        float currentAngle = geometryUtils.getAngle(x, y, centerX, centerY);
        // Cập nhật góc xoay của trạng thái quản lý (xoay tương đối so với góc ban đầu).
        stateManager.setRotationAngle(currentAngle - initialAngle);

        // Tính chiều rộng và chiều cao mới dựa trên tỷ lệ.
        float newWidth = initialWidth * scale;
        float newHeight = initialHeight * scale;

        // Cập nhật tọa độ của khung để nó được thay đổi kích thước và giữ nguyên tâm.
        signatureFrame.set(
                centerX - newWidth / 2,
                centerY - newHeight / 2,
                centerX + newWidth / 2,
                centerY + newHeight / 2
        );
    }

    /**
     * Trả về ID của tay cầm hiện đang hoạt động.
     * @return ID của tay cầm đang hoạt động, hoặc `-1` nếu không có tay cầm nào hoạt động.
     */
    public int getActiveHandle() { return activeHandle; }
}