package com.example.camerascanner.activitycrop;
import androidx.annotation.Nullable;

import java.util.ArrayList;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;

public class MagnifierView extends View {

    private Bitmap magnifiedBitmap;
    private RectF srcRect;
    private RectF destRect;
    private Paint borderPaint;
    private float magnifierRadius = 150f;
    private ArrayList<PointF> cropLines; // Các đường crop lines

    public MagnifierView(Context context) {
        super(context);
        init();
    }

    public MagnifierView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        // Paint cho viền ngoài của kính lúp
        borderPaint = new Paint();
        borderPaint.setColor(Color.WHITE);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(6f);
        borderPaint.setAntiAlias(true);

        srcRect = new RectF();
        destRect = new RectF();
        cropLines = new ArrayList<>();
        setVisibility(GONE);
    }

    public void setMagnifiedRegion(Bitmap bitmapToMagnify, RectF sourceRectOnBitmap,
                                   float currentTouchX, float currentTouchY,
                                   int viewWidth, int viewHeight, int pointIndex,
                                   ArrayList<PointF> cropLinesInZoom) {
        if (bitmapToMagnify == null || bitmapToMagnify.isRecycled()) {
            setVisibility(GONE);
            return;
        }

        this.magnifiedBitmap = bitmapToMagnify;
        this.srcRect.set(sourceRectOnBitmap);
        this.cropLines.clear();
        if (cropLinesInZoom != null) {
            this.cropLines.addAll(cropLinesInZoom);
        }

        float size = magnifierRadius * 2;
        float offset = 120f; // Khoảng cách cố định từ điểm chạm lên trên

        // Căn giữa theo X với điểm chạm
        float x = currentTouchX - size / 2;

        // LUÔN hiển thị phía trên điểm chạm với khoảng cách cố định
        float y = currentTouchY - size - offset;

        // Đảm bảo không vượt quá biên trái/phải của màn hình
        if (x < 10) {
            x = 10; // Để lại một chút margin
        } else if (x + size > viewWidth - 10) {
            x = viewWidth - size - 10;
        }

        // Nếu kính lúp bị đẩy ra ngoài phía trên, đặt ở vị trí cao nhất có thể
        if (y < 10) {
            y = 10; // Để lại một chút margin từ đỉnh màn hình
        }

        setX(x);
        setY(y);

        destRect.set(0, 0, size, size);

        setVisibility(VISIBLE);
        invalidate();
    }

    public void hideMagnifier() {
        setVisibility(GONE);
        if (magnifiedBitmap != null) {
            magnifiedBitmap = null;
        }
        cropLines.clear();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int size = (int) (magnifierRadius * 2);
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (magnifiedBitmap != null && !magnifiedBitmap.isRecycled()) {
            float centerX = getWidth() / 2f;
            float centerY = getHeight() / 2f;

            // Vẽ ảnh phóng to
            canvas.drawBitmap(magnifiedBitmap,
                    new Rect((int)srcRect.left, (int)srcRect.top, (int)srcRect.right, (int)srcRect.bottom),
                    destRect, null);

            // Vẽ các đường crop lines nếu có
            drawCropLines(canvas);

            // Vẽ crosshair và đường kẻ crop
            drawCrosshair(canvas, centerX, centerY);

            // Vẽ viền ngoài của kính lúp
            canvas.drawRect(destRect, borderPaint);

            // Vẽ thêm một viền bóng để nổi bật hơn
            Paint shadowPaint = new Paint();
            shadowPaint.setColor(Color.argb(100, 0, 0, 0)); // Đen trong suốt
            shadowPaint.setStyle(Paint.Style.STROKE);
            shadowPaint.setStrokeWidth(8f);
            shadowPaint.setAntiAlias(true);

            RectF shadowRect = new RectF(destRect);
            shadowRect.inset(-4f, -4f); // Mở rộng ra một chút
            canvas.drawRect(shadowRect, shadowPaint);
        }
    }


    /**
     * Vẽ các đường crop lines trong zoom
     */
    private void drawCropLines(Canvas canvas) {
        if (cropLines.size() < 2) return;

        Paint cropLinePaint = new Paint();
        cropLinePaint.setColor(Color.RED); // Đỏ rõ ràng
        cropLinePaint.setStyle(Paint.Style.STROKE);
        cropLinePaint.setStrokeWidth(8f); // Dày hơn để dễ thấy
        cropLinePaint.setAntiAlias(true);

        // Vẽ các đường crop (mỗi cặp điểm tạo thành 1 đường)
        for (int i = 0; i < cropLines.size() - 1; i += 2) {
            PointF start = cropLines.get(i);
            PointF end = cropLines.get(i + 1);
            if (start != null && end != null) {
                canvas.drawLine(start.x, start.y, end.x, end.y, cropLinePaint);
            }
        }
    }

    /**
     * Vẽ  đường kẻ crop đơn giản
     */
    private void drawCrosshair(Canvas canvas, float centerX, float centerY) {

        // Vẽ tâm xanh ở giữa
        Paint centerPaint = new Paint();
        centerPaint.setColor(Color.CYAN);
        centerPaint.setStyle(Paint.Style.FILL);
        centerPaint.setAntiAlias(true);

        float centerRadius = 4f;
        canvas.drawCircle(centerX, centerY, centerRadius, centerPaint);
    }
}