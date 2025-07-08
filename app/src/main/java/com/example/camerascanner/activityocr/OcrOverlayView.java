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

    private int originalImageWidth;
    private int originalImageHeight;


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
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setAntiAlias(true);
        textPaint.setSubpixelText(true);

        rectPaint = new Paint();
        rectPaint.setColor(Color.RED);
        rectPaint.setStyle(Paint.Style.STROKE);
        rectPaint.setStrokeWidth(2f);
        rectPaint.setAlpha(120);
        rectPaint.setAntiAlias(true);
    }

    public void setOcrResult(Text result, int imageWidth, int imageHeight) {
        this.ocrTextResult = result;
        this.originalImageWidth = imageWidth;
        this.originalImageHeight = imageHeight;

        calculateScaleFactors();
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        calculateScaleFactors();
    }


    private void calculateScaleFactors() {
        if (originalImageWidth == 0 || originalImageHeight == 0 || getWidth() == 0 || getHeight() == 0) {
            scaleFactorX = 1f;
            scaleFactorY = 1f;
            translateX = 0f;
            translateY = 0f;
            return;
        }

        float viewWidth = getWidth();
        float viewHeight = getHeight();

        float imageAspectRatio = (float) originalImageWidth / originalImageHeight;
        float viewAspectRatio = viewWidth / viewHeight;

        if (imageAspectRatio > viewAspectRatio) {
            scaleFactorX = viewWidth / originalImageWidth;
            scaleFactorY = scaleFactorX;
            translateY = (viewHeight - (originalImageHeight * scaleFactorY)) / 2;
            translateX = 0;
        } else {
            scaleFactorY = viewHeight / originalImageHeight;
            scaleFactorX = scaleFactorY;
            translateX = (viewWidth - (originalImageWidth * scaleFactorX)) / 2;
            translateY = 0;
        }
    }


    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (ocrTextResult == null || ocrTextResult.getTextBlocks().isEmpty()) {
            return;
        }

        canvas.save();

        // Tính toán kích thước chữ cơ sở dựa trên chiều cao tỷ lệ của ảnh
        // Giả sử một chiều cao font mặc định trong ảnh gốc (ví dụ 20px)
        // và scale nó theo scaleFactorY. Điều này sẽ giữ kích thước chữ ổn định.
        final float ORIGINAL_REF_FONT_HEIGHT = 35f; // Chiều cao font tham chiếu trong ảnh gốc (pixel)
        float baseScaledTextSize = ORIGINAL_REF_FONT_HEIGHT * scaleFactorY;

        // Đặt giới hạn min/max cho kích thước chữ để đảm bảo dễ đọc và không quá to
        final float MIN_TEXT_SIZE = 18f; // Kích thước tối thiểu (sp)
        final float MAX_TEXT_SIZE = 60f; // Kích thước tối đa (sp)
        baseScaledTextSize = Math.max(MIN_TEXT_SIZE, Math.min(MAX_TEXT_SIZE, baseScaledTextSize));


        for (Text.TextBlock block : ocrTextResult.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                for (Text.Element element : line.getElements()) {
                    Rect elementRect = element.getBoundingBox();
                    if (elementRect != null) {
                        RectF drawnRect = new RectF(
                                elementRect.left * scaleFactorX + translateX,
                                elementRect.top * scaleFactorY + translateY,
                                elementRect.right * scaleFactorX + translateX,
                                elementRect.bottom * scaleFactorY + translateY
                        );

                        canvas.save();

                        Float angle = element.getAngle();
                        if (angle != null) {
                            canvas.rotate(angle, drawnRect.centerX(), drawnRect.centerY());
                        }

                        // VẼ KHUNG BAO QUANH CHỮ (bỏ comment nếu muốn thấy)
                        // canvas.drawRect(drawnRect, rectPaint);


                        // Đặt kích thước chữ
                        // Sử dụng kích thước chữ cơ sở đã tính toán.
                        // Thêm một giới hạn nhỏ để chữ không vượt quá chiều cao của từng khung nếu baseScaledTextSize quá lớn
                        // ví dụ: Math.min(baseScaledTextSize, drawnRect.height() * 0.9f)
                        // Nhưng thường thì chỉ cần baseScaledTextSize đã đủ.
                        textPaint.setTextSize(baseScaledTextSize);

                        textPaint.setColor(Color.BLACK);

                        // Tính toán vị trí chữ để căn giữa theo chiều dọc trong khung
                        Paint.FontMetrics fm = textPaint.getFontMetrics();
                        float textHeight = fm.descent - fm.ascent;
                        float textY = drawnRect.top + (drawnRect.height() - textHeight) / 2 - fm.ascent;

                        // VẼ VĂN BẢN
                        canvas.drawText(element.getText(), drawnRect.left, textY, textPaint);

                        canvas.restore();
                    }
                }
            }
        }
        canvas.restore();
    }
}