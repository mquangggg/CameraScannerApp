package com.example.camerascanner.activitysignature;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.Nullable;

public class SignatureView extends View {

    // Paint objects
    private Paint signaturePaint, framePaint, boundingBoxPaint, cropHandlePaint, backgroundPaint;

    // Drawing components
    private Path signaturePath;
    private RectF signatureFrame, boundingBox, cropBox;
    private Bitmap overlayBitmap;

    // State management
    private boolean isDrawingMode = true;
    private boolean showSignatureFrame = true;
    private boolean showBoundingBox = false;
    private boolean showCropHandles = false;
    private boolean isFrameResizable = true;

    // Touch handling
    private static final int HANDLE_SIZE = 50;
    private static final int HANDLE_TOUCH_TOLERANCE = 80;
    private static final int FRAME_BORDER_WIDTH = 8;

    private int activeHandle = -1;
    private float lastTouchX, lastTouchY;
    private float initialFrameWidth = 600f;
    private float initialFrameHeight = 200f;

    // Rotation and scaling
    private float rotationAngle = 0f;
    private float initialDistance = 0f;
    private float initialWidth = 0f;
    private float initialHeight = 0f;
    private float initialAngle = 0f;

    // Callbacks
    private OnSignatureChangeListener listener;

    public interface OnSignatureChangeListener {
        void onSignatureChanged();
        void onBoundingBoxDetected(RectF boundingBox);
        void onCropBoxChanged(RectF cropBox);
        void onFrameResized(RectF frame);
    }

    public SignatureView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        initPaints();
        initComponents();
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

    private void initComponents() {
        signaturePath = new Path();
        signatureFrame = new RectF();
        boundingBox = new RectF();
        cropBox = new RectF();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float centerX = w / 2f;
        float centerY = h / 2f;
        signatureFrame.set(
                centerX - initialFrameWidth / 2,
                centerY - initialFrameHeight / 2,
                centerX + initialFrameWidth / 2,
                centerY + initialFrameHeight / 2
        );
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (isDrawingMode) {
            canvas.drawRect(0, 0, getWidth(), getHeight(), backgroundPaint);
        }

        if (showSignatureFrame && isFrameResizable) {
            drawSignatureFrame(canvas);
        }

        canvas.save();

        // Áp dụng rotation cho signature frame
        if (rotationAngle != 0f) {
            canvas.rotate((float) Math.toDegrees(rotationAngle), signatureFrame.centerX(), signatureFrame.centerY());
        }

        if (showSignatureFrame) {
            canvas.clipRect(signatureFrame);
        }

        canvas.drawPath(signaturePath, signaturePaint);

        if (overlayBitmap != null) {
            canvas.drawBitmap(overlayBitmap, null, signatureFrame, null);
        }

        canvas.restore();

        if (showBoundingBox && !boundingBox.isEmpty()) {
            drawBoundingBox(canvas);
        }

        if (showCropHandles && !cropBox.isEmpty()) {
            drawCropBox(canvas);
            drawSingleCropHandle(canvas);
        }
    }

    private void drawSignatureFrame(Canvas canvas) {
        canvas.save();

        // Áp dụng rotation cho frame
        if (rotationAngle != 0f) {
            canvas.rotate((float) Math.toDegrees(rotationAngle), signatureFrame.centerX(), signatureFrame.centerY());
        }

        canvas.drawRect(signatureFrame, framePaint);
        canvas.restore();

        if (isFrameResizable) {
            drawSingleFrameHandle(canvas);
        }

        if (signaturePath.isEmpty() && overlayBitmap == null) {
            drawInstructionText(canvas);
        }
    }

    private void drawInstructionText(Canvas canvas) {
        Paint textPaint = new Paint();
        textPaint.setAntiAlias(true);
        textPaint.setTextAlign(Paint.Align.CENTER);

        textPaint.setColor(Color.parseColor("#666666"));
        textPaint.setTextSize(32);
        canvas.drawText("Ký tên ở đây", signatureFrame.centerX(), signatureFrame.centerY() - 20, textPaint);

        textPaint.setColor(Color.parseColor("#999999"));
        textPaint.setTextSize(20);
        canvas.drawText("Kéo góc để thay đổi kích thước và xoay", signatureFrame.centerX(), signatureFrame.centerY() + 20, textPaint);
    }

    private void drawSingleFrameHandle(Canvas canvas) {
        Paint handlePaint = createPaint(Color.parseColor("#4CAF50"), 0f, Paint.Style.FILL);
        float handleRadius = HANDLE_SIZE / 2f;

        // Tính toán vị trí handle sau khi xoay
        float handleX = signatureFrame.left;
        float handleY = signatureFrame.bottom;

        if (rotationAngle != 0f) {
            float[] point = rotatePoint(handleX, handleY, signatureFrame.centerX(), signatureFrame.centerY(), rotationAngle);
            handleX = point[0];
            handleY = point[1];
        }

        // Vẽ handle tại vị trí đã xoay
        canvas.drawCircle(handleX, handleY, handleRadius, handlePaint);

        // Vẽ viền trắng cho dễ nhìn
        Paint borderPaint = createPaint(Color.WHITE, 3f, Paint.Style.STROKE);
        canvas.drawCircle(handleX, handleY, handleRadius, borderPaint);
    }

    private void drawBoundingBox(Canvas canvas) {
        canvas.drawRect(boundingBox, boundingBoxPaint);
        Paint textPaint = createPaint(Color.WHITE, 0f, Paint.Style.FILL);
        textPaint.setTextSize(24);
        canvas.drawText("Bounding Box", boundingBox.left, boundingBox.top - 10, textPaint);
    }

    private void drawCropBox(Canvas canvas) {
        Paint cropBoxPaint = createPaint(Color.parseColor("#2196F3"), 6f, Paint.Style.STROKE);
        canvas.drawRect(cropBox, cropBoxPaint);

        Paint textPaint = createPaint(Color.parseColor("#2196F3"), 0f, Paint.Style.FILL);
        textPaint.setTextSize(20);
        canvas.drawText("Crop Area", cropBox.left, cropBox.top - 10, textPaint);
    }

    private void drawSingleCropHandle(Canvas canvas) {
        float handleRadius = HANDLE_SIZE / 2f;
        Paint borderPaint = createPaint(Color.WHITE, 3f, Paint.Style.STROKE);

        // Tính toán vị trí handle sau khi xoay
        float handleX = cropBox.left;
        float handleY = cropBox.bottom;

        if (rotationAngle != 0f) {
            float[] point = rotatePoint(handleX, handleY, cropBox.centerX(), cropBox.centerY(), rotationAngle);
            handleX = point[0];
            handleY = point[1];
        }

        // Chỉ vẽ 1 điểm ở góc dưới bên trái của crop box
        canvas.drawCircle(handleX, handleY, handleRadius, cropHandlePaint);
        canvas.drawCircle(handleX, handleY, handleRadius, borderPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        if (isDrawingMode) {
            return handleDrawingTouch(event, x, y);
        } else {
            return handleEditingTouch(event, x, y);
        }
    }

    private boolean handleDrawingTouch(MotionEvent event, float x, float y) {
        if (showSignatureFrame && !signatureFrame.contains(x, y)) {
            return false;
        }

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                signaturePath.moveTo(x, y);
                notifySignatureChanged();
                break;
            case MotionEvent.ACTION_MOVE:
                signaturePath.lineTo(x, y);
                break;
            case MotionEvent.ACTION_UP:
                signaturePath.lineTo(x, y);
                break;
        }
        invalidate();
        return true;
    }

    private boolean handleEditingTouch(MotionEvent event, float x, float y) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                return handleTouchDown(x, y);
            case MotionEvent.ACTION_MOVE:
                return handleTouchMove(x, y);
            case MotionEvent.ACTION_UP:
                return handleTouchUp();
        }
        return false;
    }

    private boolean handleTouchDown(float x, float y) {
        lastTouchX = x;
        lastTouchY = y;

        if (showCropHandles) {
            activeHandle = getSingleCropHandleAtPoint(x, y);
            if (activeHandle != -1) {
                // Lưu trạng thái ban đầu để tính toán rotation và scale
                initialDistance = getDistance(x, y, cropBox.centerX(), cropBox.centerY());
                initialWidth = cropBox.width();
                initialHeight = cropBox.height();
                initialAngle = getAngle(x, y, cropBox.centerX(), cropBox.centerY());
                return true;
            }

            if (cropBox.contains(x, y)) {
                activeHandle = 100; // Move crop box
                return true;
            }
        }

        if (isFrameResizable && showSignatureFrame) {
            activeHandle = getSingleFrameHandleAtPoint(x, y);
            if (activeHandle != -1) {
                // Lưu trạng thái ban đầu để tính toán rotation và scale
                initialDistance = getDistance(x, y, signatureFrame.centerX(), signatureFrame.centerY());
                initialWidth = signatureFrame.width();
                initialHeight = signatureFrame.height();
                initialAngle = getAngle(x, y, signatureFrame.centerX(), signatureFrame.centerY());
                return true;
            }

            if (signatureFrame.contains(x, y)) {
                activeHandle = 200; // Move signature frame
                return true;
            }
        }

        return false;
    }

    private boolean handleTouchMove(float x, float y) {
        if (activeHandle == -1) return false;

        float dx = x - lastTouchX;
        float dy = y - lastTouchY;

        if (activeHandle == 1) { // Crop handle
            updateCropBoxWithSingleHandle(x, y);
        } else if (activeHandle == 100) { // Move crop box
            cropBox.offset(dx, dy);
        } else if (activeHandle == 2) { // Frame handle
            updateSignatureFrameWithSingleHandle(x, y);
        } else if (activeHandle == 200) { // Move signature frame
            signatureFrame.offset(dx, dy);
        }

        lastTouchX = x;
        lastTouchY = y;

        constrainBoxes();
        invalidate();
        return true;
    }

    private boolean handleTouchUp() {
        if (activeHandle == -1) return false;

        if (listener != null) {
            if (activeHandle <= 100) {
                listener.onCropBoxChanged(new RectF(cropBox));
            } else {
                listener.onFrameResized(new RectF(signatureFrame));
            }
        }

        activeHandle = -1;
        return true;
    }

    private int getSingleCropHandleAtPoint(float x, float y) {
        float tolerance = HANDLE_TOUCH_TOLERANCE / 2f;

        // Tính toán vị trí handle thực tế sau khi xoay
        float handleX = cropBox.left;
        float handleY = cropBox.bottom;

        if (rotationAngle != 0f) {
            float[] point = rotatePoint(handleX, handleY, cropBox.centerX(), cropBox.centerY(), rotationAngle);
            handleX = point[0];
            handleY = point[1];
        }

        // Kiểm tra điểm touch có trong vùng handle không
        if (Math.abs(x - handleX) < tolerance && Math.abs(y - handleY) < tolerance) {
            return 1;
        }
        return -1;
    }

    private int getSingleFrameHandleAtPoint(float x, float y) {
        float tolerance = HANDLE_TOUCH_TOLERANCE / 2f;

        // Tính toán vị trí handle thực tế sau khi xoay
        float handleX = signatureFrame.left;
        float handleY = signatureFrame.bottom;

        if (rotationAngle != 0f) {
            float[] point = rotatePoint(handleX, handleY, signatureFrame.centerX(), signatureFrame.centerY(), rotationAngle);
            handleX = point[0];
            handleY = point[1];
        }

        // Kiểm tra điểm touch có trong vùng handle không
        if (Math.abs(x - handleX) < tolerance && Math.abs(y - handleY) < tolerance) {
            return 2;
        }
        return -1;
    }

    private void updateCropBoxWithSingleHandle(float x, float y) {
        float centerX = cropBox.centerX();
        float centerY = cropBox.centerY();

        // Tính toán khoảng cách từ center đến điểm touch
        float currentDistance = getDistance(x, y, centerX, centerY);

        // Tính tỉ lệ scale
        float scale = currentDistance / initialDistance;

        // Tính toán góc xoay hiện tại
        float currentAngle = getAngle(x, y, centerX, centerY);
        rotationAngle = currentAngle - initialAngle;

        // Cập nhật kích thước crop box
        float newWidth = initialWidth * scale;
        float newHeight = initialHeight * scale;

        cropBox.set(
                centerX - newWidth / 2,
                centerY - newHeight / 2,
                centerX + newWidth / 2,
                centerY + newHeight / 2
        );
    }

    private void updateSignatureFrameWithSingleHandle(float x, float y) {
        float centerX = signatureFrame.centerX();
        float centerY = signatureFrame.centerY();

        // Tính toán khoảng cách từ center đến điểm touch
        float currentDistance = getDistance(x, y, centerX, centerY);

        // Tính tỉ lệ scale
        float scale = currentDistance / initialDistance;

        // Tính toán góc xoay hiện tại
        float currentAngle = getAngle(x, y, centerX, centerY);
        rotationAngle = currentAngle - initialAngle;

        // Cập nhật kích thước signature frame
        float newWidth = initialWidth * scale;
        float newHeight = initialHeight * scale;

        signatureFrame.set(
                centerX - newWidth / 2,
                centerY - newHeight / 2,
                centerX + newWidth / 2,
                centerY + newHeight / 2
        );
    }

    private float getDistance(float x1, float y1, float x2, float y2) {
        return (float) Math.sqrt((x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2));
    }

    private float getAngle(float x1, float y1, float x2, float y2) {
        return (float) Math.atan2(y1 - y2, x1 - x2);
    }

    // Hàm xoay điểm quanh một tâm
    private float[] rotatePoint(float x, float y, float centerX, float centerY, float angle) {
        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);

        float translatedX = x - centerX;
        float translatedY = y - centerY;

        float rotatedX = translatedX * cos - translatedY * sin;
        float rotatedY = translatedX * sin + translatedY * cos;

        return new float[]{rotatedX + centerX, rotatedY + centerY};
    }

    private void constrainBoxes() {
        // Constrain crop box
        cropBox.intersect(signatureFrame);
        ensureMinSize(cropBox, HANDLE_SIZE * 2);

        // Constrain signature frame
        signatureFrame.intersect(0, 0, getWidth(), getHeight());
        ensureMinSize(signatureFrame, 200, 100);
    }

    private void ensureMinSize(RectF rect, float minSize) {
        ensureMinSize(rect, minSize, minSize);
    }

    private void ensureMinSize(RectF rect, float minWidth, float minHeight) {
        if (rect.width() < minWidth) {
            rect.right = rect.left + minWidth;
        }
        if (rect.height() < minHeight) {
            rect.bottom = rect.top + minHeight;
        }
    }

    // Public methods
    public void setDrawingMode(boolean drawingMode) {
        this.isDrawingMode = drawingMode;
        invalidate();
    }

    public void setFrameResizable(boolean resizable) {
        this.isFrameResizable = resizable;
        invalidate();
    }

    public void showSignatureFrame(boolean show) {
        this.showSignatureFrame = show;
        invalidate();
    }

    public void clear() {
        signaturePath.reset();
        boundingBox.setEmpty();
        cropBox.setEmpty();
        showBoundingBox = false;
        showCropHandles = false;
        isDrawingMode = true;
        rotationAngle = 0f;
        invalidate();
        notifySignatureChanged();
    }

    public void detectBoundingBox() {
        if (signaturePath.isEmpty() && overlayBitmap == null) return;

        RectF detectedBox = new RectF();

        if (overlayBitmap != null) {
            detectedBox = findContentBounds(overlayBitmap);
            detectedBox.offset(signatureFrame.left, signatureFrame.top);
        } else {
            signaturePath.computeBounds(detectedBox, true);
        }

        boundingBox.set(detectedBox);
        cropBox.set(boundingBox);
        showBoundingBox = true;
        invalidate();

        if (listener != null) {
            listener.onBoundingBoxDetected(new RectF(boundingBox));
        }
    }

    private RectF findContentBounds(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int left = width, top = height, right = 0, bottom = 0;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (Color.alpha(bitmap.getPixel(x, y)) > 0) {
                    left = Math.min(left, x);
                    top = Math.min(top, y);
                    right = Math.max(right, x);
                    bottom = Math.max(bottom, y);
                }
            }
        }

        if (left >= right || top >= bottom) return new RectF();

        int padding = 20;
        return new RectF(
                Math.max(0, left - padding),
                Math.max(0, top - padding),
                Math.min(width, right + padding),
                Math.min(height, bottom + padding)
        );
    }

    public void showCropMode() {
        isDrawingMode = false;
        if (boundingBox.isEmpty()) {
            detectBoundingBox();
        }
        showCropHandles = true;
        isFrameResizable = false;
        invalidate();
    }

    public void hideCropMode() {
        showCropHandles = false;
        isDrawingMode = true;
        isFrameResizable = true;
        invalidate();
    }

    public Bitmap getCroppedBitmap() {
        if (overlayBitmap != null && !signatureFrame.isEmpty()) {
            return cropBitmapFromFrame();
        }

        if (!cropBox.isEmpty()) {
            return cropBitmapFromPath();
        }

        return null;
    }

    private Bitmap cropBitmapFromFrame() {
        Bitmap fullView = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(fullView);
        canvas.drawBitmap(overlayBitmap, null, signatureFrame, null);

        Bitmap cropped = Bitmap.createBitmap(fullView,
                (int) signatureFrame.left, (int) signatureFrame.top,
                (int) signatureFrame.width(), (int) signatureFrame.height());

        fullView.recycle();
        return cropped;
    }

    private Bitmap cropBitmapFromPath() {
        Bitmap fullBitmap = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(fullBitmap);
        canvas.drawPath(signaturePath, signaturePaint);

        Bitmap cropped = Bitmap.createBitmap(fullBitmap,
                (int) cropBox.left, (int) cropBox.top,
                (int) cropBox.width(), (int) cropBox.height());

        fullBitmap.recycle();
        return cropped;
    }

    public Bitmap getSignatureBitmap() {
        RectF bounds = boundingBox.isEmpty() ? signatureFrame : boundingBox;

        Bitmap bitmap = Bitmap.createBitmap(
                (int) bounds.width(),
                (int) bounds.height(),
                Bitmap.Config.ARGB_8888
        );

        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.TRANSPARENT);
        canvas.translate(-bounds.left, -bounds.top);
        canvas.drawPath(signaturePath, signaturePaint);

        return bitmap;
    }

    public void loadBitmap(Bitmap bitmap) {
        if (bitmap == null) return;

        this.overlayBitmap = bitmap;
        signaturePath.reset();

        if (getWidth() > 0 && getHeight() > 0) {
            signatureFrame.set(0, 0, bitmap.getWidth(), bitmap.getHeight());
        }

        isDrawingMode = false;
        showSignatureFrame = true;
        invalidate();
    }

    // Getters and setters
    public boolean isEmpty() { return signaturePath.isEmpty(); }
    public RectF getSignatureFrame() { return new RectF(signatureFrame); }
    public RectF getBoundingBox() { return new RectF(boundingBox); }
    public RectF getCropBox() { return new RectF(cropBox); }
    public float getRotationAngle() { return rotationAngle; }

    public void setSignatureFrame(RectF rectF) {
        signatureFrame.set(rectF);
        constrainBoxes();
        invalidate();
    }

    public void setOnSignatureChangeListener(OnSignatureChangeListener listener) {
        this.listener = listener;
    }

    private void notifySignatureChanged() {
        if (listener != null) {
            listener.onSignatureChanged();
        }
    }
}