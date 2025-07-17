package com.example.camerascanner.activitysignature.signatureview;

import android.graphics.Color;
import android.graphics.Paint;

public class SignaturePaintManager {
    private Paint signaturePaint, framePaint, boundingBoxPaint, cropHandlePaint, backgroundPaint;
    private static final int FRAME_BORDER_WIDTH = 8;

    public SignaturePaintManager() {
        initPaints();
    }

    private void initPaints() {
        signaturePaint = createPaint(Color.BLACK, 6f, Paint.Style.STROKE);
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

    public Paint getSignaturePaint() { return signaturePaint; }
    public Paint getFramePaint() { return framePaint; }
    public Paint getBoundingBoxPaint() { return boundingBoxPaint; }
    public Paint getCropHandlePaint() { return cropHandlePaint; }
    public Paint getBackgroundPaint() { return backgroundPaint; }
}