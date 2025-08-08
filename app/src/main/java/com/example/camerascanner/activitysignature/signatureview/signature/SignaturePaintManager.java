package com.example.camerascanner.activitysignature.signatureview.signature;

import android.graphics.Color;
import android.graphics.Paint;

public class SignaturePaintManager {
    private Paint signaturePaint, framePaint, boundingBoxPaint, cropHandlePaint, backgroundPaint;
    private static final int FRAME_BORDER_WIDTH = 8;
    private static final float DEFAULT_STROKE_WIDTH = 6f;
    private static final float MIN_STROKE_WIDTH = 1f;
    private static final float MAX_STROKE_WIDTH = 20f;

    public SignaturePaintManager() {
        initPaints();
    }

    private void initPaints() {
        signaturePaint = createPaint(Color.BLACK, DEFAULT_STROKE_WIDTH, Paint.Style.STROKE);
        framePaint = createPaint(Color.parseColor("#4CAF50"), FRAME_BORDER_WIDTH, Paint.Style.STROKE);
        framePaint.setPathEffect(new android.graphics.DashPathEffect(new float[]{20, 10}, 0));
        boundingBoxPaint = createPaint(Color.WHITE, 4f, Paint.Style.STROKE);
        cropHandlePaint = createPaint(Color.BLUE, 4f, Paint.Style.FILL_AND_STROKE);
        backgroundPaint = createPaint(Color.parseColor("#f8f8f8"), 0f, Paint.Style.FILL);
    }

    private Paint createPaint(int color, float strokeWidth, Paint.Style style) {
        Paint paint = new Paint();
        paint.setColor(color);
        paint.setStrokeWidth(strokeWidth);
        paint.setStyle(style);
        paint.setAntiAlias(true);
        if (style == Paint.Style.STROKE) {
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeCap(Paint.Cap.ROUND);
        }
        return paint;
    }

    /**
     * Thiết lập độ dày nét vẽ cho chữ ký
     * @param width Độ dày mong muốn (sẽ được giới hạn trong khoảng cho phép)
     */
    public void setSignatureStrokeWidth(float width) {
        // Giới hạn độ dày trong khoảng cho phép
        float constrainedWidth = Math.max(MIN_STROKE_WIDTH, Math.min(MAX_STROKE_WIDTH, width));
        signaturePaint.setStrokeWidth(constrainedWidth);
    }

    /**
     * Lấy độ dày nét vẽ hiện tại của chữ ký
     * @return Độ dày nét vẽ hiện tại
     */
    public float getSignatureStrokeWidth() {
        return signaturePaint.getStrokeWidth();
    }

    /**
     * Lấy độ dày tối thiểu cho phép
     * @return Độ dày tối thiểu
     */
    public float getMinStrokeWidth() {
        return MIN_STROKE_WIDTH;
    }

    /**
     * Lấy độ dày tối đa cho phép
     * @return Độ dày tối đa
     */
    public float getMaxStrokeWidth() {
        return MAX_STROKE_WIDTH;
    }

    /**
     * Lấy độ dày mặc định
     * @return Độ dày mặc định
     */
    public float getDefaultStrokeWidth() {
        return DEFAULT_STROKE_WIDTH;
    }

    // Các getter methods hiện có
    public Paint getSignaturePaint() { return signaturePaint; }
    public Paint getFramePaint() { return framePaint; }
    public Paint getBoundingBoxPaint() { return boundingBoxPaint; }
    public Paint getCropHandlePaint() { return cropHandlePaint; }
    public Paint getBackgroundPaint() { return backgroundPaint; }
}