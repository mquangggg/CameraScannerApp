package com.example.camerascanner.activitysignature.signatureview.signature;

import android.graphics.Color;
import android.graphics.Paint;

public class SignaturePaintManager {
    private Paint signaturePaint, framePaint, boundingBoxPaint, cropHandlePaint, backgroundPaint;
    private static final int FRAME_BORDER_WIDTH = 8;
    private static final float DEFAULT_STROKE_WIDTH = 6f;
    private static final float MIN_STROKE_WIDTH = 1f;
    private static final float MAX_STROKE_WIDTH = 20f;

    // Màu mặc định
    private static final int DEFAULT_SIGNATURE_COLOR = Color.BLACK;
    private static final int DEFAULT_BACKGROUND_COLOR = Color.parseColor("#f8f8f8");

    public SignaturePaintManager() {
        initPaints();
    }

    private void initPaints() {
        signaturePaint = createPaint(DEFAULT_SIGNATURE_COLOR, DEFAULT_STROKE_WIDTH, Paint.Style.STROKE);
        framePaint = createPaint(Color.parseColor("#4CAF50"), FRAME_BORDER_WIDTH, Paint.Style.STROKE);
        framePaint.setPathEffect(new android.graphics.DashPathEffect(new float[]{20, 10}, 0));
        boundingBoxPaint = createPaint(Color.WHITE, 4f, Paint.Style.STROKE);
        cropHandlePaint = createPaint(Color.BLUE, 4f, Paint.Style.FILL_AND_STROKE);
        backgroundPaint = createPaint(DEFAULT_BACKGROUND_COLOR, 0f, Paint.Style.FILL);
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

    // --- Stroke Width Methods ---
    /**
     * Thiết lập độ dày nét vẽ cho chữ ký
     * @param width Độ dày mong muốn (sẽ được giới hạn trong khoảng cho phép)
     */
    public void setSignatureStrokeWidth(float width) {
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

    // --- Color Methods ---
    /**
     * Thiết lập màu sắc cho chữ ký
     * @param color Màu sắc mong muốn (int color)
     */
    public void setSignatureColor(int color) {
        signaturePaint.setColor(color);
    }

    /**
     * Lấy màu sắc hiện tại của chữ ký
     * @return Màu sắc hiện tại
     */
    public int getSignatureColor() {
        return signaturePaint.getColor();
    }

    /**
     * Thiết lập màu nền cho View
     * @param color Màu nền mong muốn
     */
    public void setBackgroundColor(int color) {
        backgroundPaint.setColor(color);
    }

    /**
     * Lấy màu nền hiện tại
     * @return Màu nền hiện tại
     */
    public int getBackgroundColor() {
        return backgroundPaint.getColor();
    }

    /**
     * Reset màu chữ ký về màu mặc định
     */
    public void resetSignatureColor() {
        signaturePaint.setColor(DEFAULT_SIGNATURE_COLOR);
    }

    /**
     * Reset màu nền về màu mặc định
     */
    public void resetBackgroundColor() {
        backgroundPaint.setColor(DEFAULT_BACKGROUND_COLOR);
    }

    /**
     * Reset tất cả về giá trị mặc định
     */
    public void resetToDefaults() {
        setSignatureColor(DEFAULT_SIGNATURE_COLOR);
        setSignatureStrokeWidth(DEFAULT_STROKE_WIDTH);
        setBackgroundColor(DEFAULT_BACKGROUND_COLOR);
    }

    // --- Frame và Crop Handle Customization ---
    /**
     * Thiết lập màu cho khung chữ ký
     * @param color Màu mong muốn
     */
    public void setFrameColor(int color) {
        framePaint.setColor(color);
    }

    /**
     * Lấy màu khung hiện tại
     * @return Màu khung hiện tại
     */
    public int getFrameColor() {
        return framePaint.getColor();
    }

    /**
     * Thiết lập màu cho các handle crop
     * @param color Màu mong muốn
     */
    public void setCropHandleColor(int color) {
        cropHandlePaint.setColor(color);
    }

    /**
     * Lấy màu handle crop hiện tại
     * @return Màu handle crop hiện tại
     */
    public int getCropHandleColor() {
        return cropHandlePaint.getColor();
    }

    /**
     * Thiết lập màu cho bounding box
     * @param color Màu mong muốn
     */
    public void setBoundingBoxColor(int color) {
        boundingBoxPaint.setColor(color);
    }

    /**
     * Lấy màu bounding box hiện tại
     * @return Màu bounding box hiện tại
     */
    public int getBoundingBoxColor() {
        return boundingBoxPaint.getColor();
    }

    // --- Getter methods cho Paint objects ---
    public Paint getSignaturePaint() { return signaturePaint; }
    public Paint getFramePaint() { return framePaint; }
    public Paint getBoundingBoxPaint() { return boundingBoxPaint; }
    public Paint getCropHandlePaint() { return cropHandlePaint; }
    public Paint getBackgroundPaint() { return backgroundPaint; }

    // --- Utility Methods ---
    /**
     * Kiểm tra xem màu có phải là màu tối không
     * @param color Màu cần kiểm tra
     * @return true nếu là màu tối
     */
    public static boolean isDarkColor(int color) {
        double darkness = 1 - (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255;
        return darkness >= 0.5;
    }

    /**
     * Lấy màu tương phản (trắng hoặc đen)
     * @param color Màu gốc
     * @return Màu tương phản
     */
    public static int getContrastColor(int color) {
        return isDarkColor(color) ? Color.WHITE : Color.BLACK;
    }
}