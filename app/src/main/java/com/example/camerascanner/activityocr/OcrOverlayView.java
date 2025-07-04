// File: com/example/camerascanner/activityocr/OcrOverlayView.java

package com.example.camerascanner.activityocr;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import com.google.mlkit.vision.text.Text;

public class OcrOverlayView extends View {

    private Text ocrTextResult;
    private float scaleFactorX = 1f;
    private float scaleFactorY = 1f;
    private float translateX = 0f;
    private float translateY = 0f;

    private Paint textPaint;
    private Paint rectPaint;

    public OcrOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public OcrOverlayView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        textPaint = new Paint();
        textPaint.setColor(Color.BLACK);
        // textPaint.setTextSize(24f); // Kích thước ban đầu, sẽ bị ghi đè
        textPaint.setStyle(Paint.Style.FILL);

        rectPaint = new Paint();
        rectPaint.setColor(Color.YELLOW);
        rectPaint.setStyle(Paint.Style.STROKE);
        rectPaint.setStrokeWidth(2f);
    }

    public void setOcrResult(Text ocrText, int imageWidth, int imageHeight) {
        this.ocrTextResult = ocrText;

        float viewWidth = getWidth();
        float viewHeight = getHeight();

        if (imageWidth > 0 && imageHeight > 0 && viewWidth > 0 && viewHeight > 0) {
            float imageAspectRatio = (float) imageWidth / imageHeight;
            float viewAspectRatio = viewWidth / viewHeight;

            if (imageAspectRatio > viewAspectRatio) {
                scaleFactorX = viewWidth / imageWidth;
                scaleFactorY = scaleFactorX;
                translateY = (viewHeight - (imageHeight * scaleFactorY)) / 2;
                translateX = 0;
            } else {
                scaleFactorY = viewHeight / imageHeight;
                scaleFactorX = scaleFactorY;
                translateX = (viewWidth - (imageWidth * scaleFactorX)) / 2;
                translateY = 0;
            }
        } else {
            scaleFactorX = 1f;
            scaleFactorY = 1f;
            translateX = 0;
            translateY = 0;
        }

        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (ocrTextResult == null) {
            return;
        }

        for (Text.TextBlock block : ocrTextResult.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                for (Text.Element element : line.getElements()) {
                    Rect boundingBox = element.getBoundingBox();
                    if (boundingBox != null) {
                        float left = boundingBox.left * scaleFactorX + translateX;
                        float top = boundingBox.top * scaleFactorY + translateY;
                        float right = boundingBox.right * scaleFactorX + translateX;
                        float bottom = boundingBox.bottom * scaleFactorY + translateY;

                        RectF drawnRect = new RectF(left, top, right, bottom);

                        canvas.drawRect(drawnRect, rectPaint);

                        // GIẢM KÍCH THƯỚC CHỮ Ở ĐÂY
                        // Thay đổi 0.8f thành một giá trị nhỏ hơn, ví dụ 0.6f hoặc 0.7f
                        textPaint.setTextSize(element.getBoundingBox().height() * scaleFactorY * 0.8f); // Đã giảm từ 0.8f xuống 0.65f

                        Paint.FontMetrics fm = textPaint.getFontMetrics();
                        float textHeight = fm.descent - fm.ascent;
                        float textY = top + (drawnRect.height() - textHeight) / 2 - fm.ascent;

                        canvas.drawText(element.getText(), left, textY, textPaint);
                    }
                }
            }
        }
    }
}