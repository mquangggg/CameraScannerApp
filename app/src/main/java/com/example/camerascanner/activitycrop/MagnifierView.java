package com.example.camerascanner.activitycrop;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

// import java.util.ArrayList; // Không cần nữa nếu không có text bounding boxes
// import java.util.List; // Không cần nữa nếu không có text bounding boxes

public class MagnifierView extends View {

    private Bitmap magnifiedBitmap;
    private RectF srcRect;
    private RectF destRect;
    private Paint borderPaint;
    private Paint cropPointPaint;
    private float magnifierRadius = 150f;
    // private List<RectF> textBoundingBoxesInViewCoords; //

    public MagnifierView(Context context) {
        super(context);
        init();
    }

    public MagnifierView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    private void init() {
        borderPaint = new Paint();
        borderPaint.setColor(Color.WHITE);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(4f);
        borderPaint.setAntiAlias(true);

        cropPointPaint = new Paint();
        cropPointPaint.setColor(Color.RED);
        cropPointPaint.setStyle(Paint.Style.STROKE);
        cropPointPaint.setStrokeWidth(6f);
        cropPointPaint.setAntiAlias(true);

        srcRect = new RectF();
        destRect = new RectF();
        // textBoundingBoxesInViewCoords = new ArrayList<>(); //
        setVisibility(GONE);
    }

    // Đã bỏ setter setTextBoundingBoxes

    /**
     * Cập nhật hình ảnh và vị trí cho kính lúp.
     * @param bitmapToMagnify Bitmap chứa vùng ảnh đã cắt và cần phóng đại.
     * @param sourceRectOnBitmap RectF của vùng ảnh (trên bitmapToMagnify) cần được phóng đại.
     * @param currentTouchX Tọa độ X của điểm chạm (trên màn hình/CustomCropView).
     * @param currentTouchY Tọa độ Y của điểm chạm (trên màn hình/CustomCropView).
     * @param viewWidth Chiều rộng của CustomCropView.
     * @param viewHeight Chiều cao của CustomCropView.
     * @param pointIndex Chỉ số của điểm crop đang được kéo (0: TL, 1: TR, 2: BR, 3: BL).
     */
    public void setMagnifiedRegion(Bitmap bitmapToMagnify, RectF sourceRectOnBitmap,
                                   float currentTouchX, float currentTouchY,
                                   int viewWidth, int viewHeight, int pointIndex) { // Đã bỏ List<RectF> textRects
        if (bitmapToMagnify == null || bitmapToMagnify.isRecycled()) {
            setVisibility(GONE);
            return;
        }

        this.magnifiedBitmap = bitmapToMagnify;
        this.srcRect.set(sourceRectOnBitmap);

        float size = magnifierRadius * 2;
        float offset = 70f; // Khoảng cách offset từ điểm chạm

        float x;
        float y;

        // Tính toán vị trí Y dựa trên pointIndex (để không bị ngón tay che)
        // 0: Top-Left, 1: Top-Right -> Kính lúp ở PHÍA TRÊN (để không che vùng dưới)
        if (pointIndex == 0 || pointIndex == 1) {
            y = currentTouchY - size - offset;
        }
        // 2: Bottom-Right, 3: Bottom-Left -> Kính lúp ở PHÍA DƯỚI (để không che vùng trên)
        else { // pointIndex == 2 || pointIndex == 3
            y = currentTouchY + offset;
        }

        // Căn giữa theo X với điểm chạm
        x = currentTouchX - size / 2;

        // Đảm bảo không vượt quá biên của màn hình/view
        // Điều chỉnh X
        if (x < 0) {
            x = 0;
        } else if (x + size > viewWidth) {
            x = viewWidth - size;
        }

        // Điều chỉnh Y
        if (y < 0) { // Nếu kính lúp đi ra khỏi biên trên
            y = 0; // Đẩy vào biên trên
        } else if (y + size > viewHeight) { // Nếu kính lúp đi ra khỏi biên dưới
            y = viewHeight - size; // Đẩy vào biên dưới
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
            canvas.drawBitmap(magnifiedBitmap, new Rect((int)srcRect.left, (int)srcRect.top, (int)srcRect.right, (int)srcRect.bottom), destRect, null);

            float centerX = getWidth() / 2f;
            float centerY = getHeight() / 2f;
            float frameSize = 20f;
            canvas.drawRect(centerX - frameSize / 2, centerY - frameSize / 2,
                    centerX + frameSize / 2, centerY + frameSize / 2,
                    cropPointPaint);

            canvas.drawRect(destRect, borderPaint);
        }
    }
}