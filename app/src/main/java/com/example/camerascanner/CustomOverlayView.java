// CustomOverlayView.java
package com.example.camerascanner;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

public class CustomOverlayView extends View {

    private Paint rectPaint;
    private RectF currentBoundingBox; // Để lưu trữ bounding box đã được chuyển đổi

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
        rectPaint.setColor(Color.RED); // Màu sắc của khung nhận diện
        rectPaint.setStyle(Paint.Style.STROKE); // Chỉ vẽ viền
        rectPaint.setStrokeWidth(5f); // Độ dày của viền
        rectPaint.setAntiAlias(true); // Làm mịn đường viền
        currentBoundingBox = null; // Khởi tạo ban đầu là null
    }

    /**
     * Cập nhật bounding box cần vẽ lên lớp phủ.
     * @param boundingBox RectF chứa tọa độ của khung đã được chuyển đổi sang không gian của CustomOverlayView.
     */
    public void setBoundingBox(RectF boundingBox) {
        this.currentBoundingBox = boundingBox;
        // invalidate() sẽ được gọi từ MainActivity sau khi setBoundingBox
    }

    /**
     * Xóa bounding box khỏi lớp phủ (khi không có đối tượng nào được phát hiện).
     */
    public void clearBoundingBox() {
        this.currentBoundingBox = null;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // Chỉ vẽ khung nếu có bounding box được đặt
        if (currentBoundingBox != null) {
            canvas.drawRect(currentBoundingBox, rectPaint);
        }
    }
}