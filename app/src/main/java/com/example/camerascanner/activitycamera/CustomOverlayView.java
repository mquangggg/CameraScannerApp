package com.example.camerascanner.activitycamera; // Giữ package hiện tại của bạn

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path; // Thêm import cho Path
import android.graphics.PointF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import androidx.annotation.Nullable;

import org.opencv.core.MatOfPoint;
import org.opencv.core.Point; // Thêm import cho org.opencv.core.Point

import java.util.Arrays; // Thêm import cho Arrays

public class CustomOverlayView extends View {

    private Paint rectPaint;
    private MatOfPoint currentQuadrilateral; // Thay RectF bằng MatOfPoint

    // Các biến này không còn cần thiết nếu logic scale được xử lý bên ngoài hoặc trong hàm static
    // private int originalImageWidth;
    // private int originalImageHeight;
    // private int imageRotationDegrees;
    // private int previewViewWidth;
    // private int previewViewHeight;

    private static final String TAG = "CustomOverlayView";

    public CustomOverlayView(Context context) {
        super(context);
        init();
    }

    public CustomOverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        rectPaint = new Paint();
        rectPaint.setColor(Color.GREEN);
        rectPaint.setStyle(Paint.Style.STROKE);
        rectPaint.setStrokeWidth(5);
    }

    // Đặt hàm này để nhận MatOfPoint đã được scale và sẵn sàng vẽ
    public void setQuadrilateral(MatOfPoint points) {
        if (this.currentQuadrilateral != null) {
            this.currentQuadrilateral.release(); // Giải phóng MatOfPoint cũ nếu có
        }
        this.currentQuadrilateral = points;
        postInvalidate(); // Yêu cầu vẽ lại View trên luồng UI
    }

    public void clearBoundingBox() { // Đổi tên thành clearQuadrilateral nếu muốn nhất quán
        if (this.currentQuadrilateral != null) {
            this.currentQuadrilateral.release();
            this.currentQuadrilateral = null;
        }
        postInvalidate(); // Yêu cầu vẽ lại để xóa khung
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (currentQuadrilateral != null && !currentQuadrilateral.empty()) {
            Path path = new Path();
            Point[] points = currentQuadrilateral.toArray();

            // Đảm bảo có đúng 4 điểm
            if (points.length == 4) {
                // Các điểm được vẽ trực tiếp vì chúng đã được scale và sắp xếp trong CameraActivity
                path.moveTo((float) points[0].x, (float) points[0].y);
                path.lineTo((float) points[1].x, (float) points[1].y);
                path.lineTo((float) points[2].x, (float) points[2].y);
                path.lineTo((float) points[3].x, (float) points[3].y);
                path.close();
                canvas.drawPath(path, rectPaint);
            } else {
                Log.w(TAG, "currentQuadrilateral không chứa 4 điểm. Số điểm: " + points.length);
            }
        }
    }

    // HÀM SCALE NÀY ĐƯỢC ĐẶT TRONG CustomOverlayView
    // CameraActivity sẽ gọi hàm static này để nhận lại MatOfPoint đã được scale
    public static MatOfPoint scalePointsToOverlayView(MatOfPoint originalPoints, int matWidth, int matHeight, int viewWidth, int viewHeight) {
        if (originalPoints == null || originalPoints.empty()) {
            Log.w(TAG, "originalPoints là null hoặc rỗng, không thể scale điểm.");
            return null;
        }

        Point[] original = originalPoints.toArray();

        if (original.length != 4) {
            Log.w(TAG, "originalPoints không chứa 4 điểm, mà có: " + original.length + " điểm. Không thể scale.");
            return null;
        }

        int previewViewActualWidth = viewWidth;
        int previewViewActualHeight = viewHeight;

        // Tính toán tỷ lệ để hình ảnh từ camera khớp với PreviewView
        // Sử dụng Math.max để mô phỏng hành vi CENTER_CROP
        float scaleX = (float) previewViewActualWidth / matWidth;
        float scaleY = (float) previewViewActualHeight / matHeight;

        // Nhân thêm 1.2f (có thể điều chỉnh) để ảnh được phóng to hơn một chút,
        // thường dùng trong các trường hợp "centerCrop" để lấp đầy màn hình và cắt bớt phần thừa.
        float scale = Math.max(scaleX, scaleY) * 1.0f;

        // Tính toán kích thước của ảnh sau khi đã scale.
        float scaledImageWidth = matWidth * scale;
        float scaledImageHeight = matHeight * scale;

        // Tính toán offset (phần bù) để căn giữa phần ảnh được giữ lại trong PreviewView.
        // Đây là cách mô phỏng hành vi centerCrop: ảnh được phóng to để lấp đầy và phần thừa bị cắt.
        float startX = (previewViewActualWidth - scaledImageWidth) / 2f;
        float startY = (previewViewActualHeight - scaledImageHeight) / 2f;


        Log.d(TAG, "Scaling (CENTER_CROP): Camera Mat (" + matWidth + "x" + matHeight + ") -> PreviewView Actual Size (" + previewViewActualWidth + "x" + previewViewActualHeight + ")");
        Log.d(TAG, "Scale factor: " + scale + ", Offset X: " + startX + ", Offset Y: " + startY);

        Point[] scaledPoints = new Point[4];
        // Áp dụng tỷ lệ và offset cho từng điểm.
        for (int i = 0; i < 4; i++) {
            scaledPoints[i] = new Point(original[i].x * scale + startX, original[i].y * scale + startY);
        }

        MatOfPoint result = new MatOfPoint(scaledPoints);
        return result;
    }
}