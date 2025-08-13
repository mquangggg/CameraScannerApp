package com.example.camerascanner.activitysignature.signatureview;

import android.graphics.RectF;

/**
 * Lớp SignatureGeometryUtils cung cấp các phương thức tiện ích để xử lý các phép toán hình học,
 * chủ yếu liên quan đến khoảng cách, góc, xoay điểm và ràng buộc kích thước của các hình chữ nhật (RectF).
 */
public class SignatureGeometryUtils {

    // Kích thước tay cầm (handle) được sử dụng để điều chỉnh kích thước hoặc vị trí (ví dụ: các góc của hộp cắt).
    private static final int HANDLE_SIZE = 50;

    /**
     * Tính toán khoảng cách giữa hai điểm trong không gian 2D.
     * Sử dụng công thức khoảng cách Euclidean: sqrt((x2-x1)^2 + (y2-y1)^2).
     *
     * @param x1 Tọa độ X của điểm thứ nhất.
     * @param y1 Tọa độ Y của điểm thứ nhất.
     * @param x2 Tọa độ X của điểm thứ hai.
     * @param y2 Tọa độ Y của điểm thứ hai.
     * @return Khoảng cách giữa hai điểm.
     */
    public float getDistance(float x1, float y1, float x2, float y2) {
        return (float) Math.sqrt((x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2));
    }

    /**
     * Tính toán góc (tính bằng radian) của đường thẳng nối hai điểm.
     * Góc được tính theo hệ tọa độ Descartes, với 0 radian là trục X dương.
     *
     * @param x1 Tọa độ X của điểm thứ nhất.
     * @param y1 Tọa độ Y của điểm thứ nhất.
     * @param x2 Tọa độ X của điểm thứ hai.
     * @param y2 Tọa độ Y của điểm thứ hai.
     * @return Góc giữa hai điểm (tính bằng radian).
     */
    public float getAngle(float x1, float y1, float x2, float y2) {
        // atan2(deltaY, deltaX) trả về góc giữa điểm (x1, y1) và (x2, y2) so với trục X.
        return (float) Math.atan2(y1 - y2, x1 - x2);
    }

    /**
     * Xoay một điểm quanh một điểm trung tâm cho trước một góc.
     * Sử dụng công thức xoay 2D:
     * x' = (x - centerX) * cos(angle) - (y - centerY) * sin(angle) + centerX
     * y' = (x - centerX) * sin(angle) + (y - centerY) * cos(angle) + centerY
     *
     * @param x Tọa độ X của điểm cần xoay.
     * @param y Tọa độ Y của điểm cần xoay.
     * @param centerX Tọa độ X của điểm trung tâm xoay.
     * @param centerY Tọa độ Y của điểm trung tâm xoay.
     * @param angle Góc xoay (tính bằng radian).
     * @return Một mảng float chứa tọa độ X và Y đã xoay của điểm [rotatedX, rotatedY].
     */
    public float[] rotatePoint(float x, float y, float centerX, float centerY, float angle) {
        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);

        // Dịch chuyển điểm về gốc tọa độ trước khi xoay
        float translatedX = x - centerX;
        float translatedY = y - centerY;

        // Thực hiện phép xoay
        float rotatedX = translatedX * cos - translatedY * sin;
        float rotatedY = translatedX * sin + translatedY * cos;

        // Dịch chuyển điểm đã xoay trở lại vị trí ban đầu
        return new float[]{rotatedX + centerX, rotatedY + centerY};
    }

    /**
     * Ràng buộc kích thước và vị trí của hai hình chữ nhật (cropBox và signatureFrame)
     * để chúng nằm trong giới hạn của view hiển thị và có kích thước tối thiểu.
     *
     * @param cropBox RectF đại diện cho hộp cắt.
     * @param signatureFrame RectF đại diện cho khung chữ ký (thường là khung của ảnh gốc).
     * @param viewWidth Chiều rộng của view hiển thị.
     * @param viewHeight Chiều cao của view hiển thị.
     */
    public void constrainBoxes(RectF cropBox, RectF signatureFrame, int viewWidth, int viewHeight) {
        // Đảm bảo cropBox không vượt ra ngoài signatureFrame bằng cách lấy phần giao nhau.
        cropBox.intersect(signatureFrame);
        // Đảm bảo cropBox có kích thước tối thiểu bằng 2 lần HANDLE_SIZE (để có thể kéo các góc).
        ensureMinSize(cropBox, HANDLE_SIZE * 2);

        // Đảm bảo signatureFrame nằm hoàn toàn trong giới hạn của view hiển thị.
        signatureFrame.intersect(0, 0, viewWidth, viewHeight);
        // Đảm bảo signatureFrame có kích thước tối thiểu (ví dụ: 200x100 pixel).
        ensureMinSize(signatureFrame, 200, 100);
    }

    /**
     * Đảm bảo một hình chữ nhật có kích thước tối thiểu bằng nhau cho chiều rộng và chiều cao.
     * @param rect Hình chữ nhật cần kiểm tra và điều chỉnh.
     * @param minSize Kích thước tối thiểu (áp dụng cho cả chiều rộng và chiều cao).
     */
    private void ensureMinSize(RectF rect, float minSize) {
        // Gọi phương thức quá tải với cùng một giá trị cho minWidth và minHeight.
        ensureMinSize(rect, minSize, minSize);
    }

    /**
     * Đảm bảo một hình chữ nhật có chiều rộng và chiều cao tối thiểu cho trước.
     * Nếu chiều rộng hoặc chiều cao hiện tại nhỏ hơn giá trị tối thiểu,
     * cạnh phải/dưới của hình chữ nhật sẽ được điều chỉnh để đạt được kích thước tối thiểu.
     *
     * @param rect Hình chữ nhật cần kiểm tra và điều chỉnh.
     * @param minWidth Chiều rộng tối thiểu.
     * @param minHeight Chiều cao tối thiểu.
     */
    private void ensureMinSize(RectF rect, float minWidth, float minHeight) {
        if (rect.width() < minWidth) {
            // Nếu chiều rộng nhỏ hơn tối thiểu, mở rộng cạnh phải.
            rect.right = rect.left + minWidth;
        }
        if (rect.height() < minHeight) {
            // Nếu chiều cao nhỏ hơn tối thiểu, mở rộng cạnh dưới.
            rect.bottom = rect.top + minHeight;
        }
    }
}