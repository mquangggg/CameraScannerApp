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
    private Paint signaturePaint;
    private Paint framePaint;
    private Paint boundingBoxPaint;
    private Paint cropHandlePaint;
    private Paint backgroundPaint;

    // Drawing components
    private Path signaturePath;
    private RectF signatureFrame;
    private RectF boundingBox;
    private RectF cropBox;
    private Bitmap overlayBitmap;
    // Removed overlayX, overlayY as we will draw directly into signatureFrame

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
        initPaths();
    }

    private void initPaints() {
        // Paint cho chữ ký
        signaturePaint = new Paint();
        signaturePaint.setColor(Color.BLACK);
        signaturePaint.setStrokeWidth(6f);
        signaturePaint.setStyle(Paint.Style.STROKE);
        signaturePaint.setAntiAlias(true);
        signaturePaint.setStrokeJoin(Paint.Join.ROUND);
        signaturePaint.setStrokeCap(Paint.Cap.ROUND);

        // Paint cho khung signature
        framePaint = new Paint();
        framePaint.setColor(Color.parseColor("#4CAF50"));
        framePaint.setStrokeWidth(FRAME_BORDER_WIDTH);
        framePaint.setStyle(Paint.Style.STROKE);
        framePaint.setAntiAlias(true);
        framePaint.setPathEffect(new android.graphics.DashPathEffect(new float[]{20, 10}, 0));

        // Paint cho bounding box
        boundingBoxPaint = new Paint();
        boundingBoxPaint.setColor(Color.RED);
        boundingBoxPaint.setStrokeWidth(4f);
        boundingBoxPaint.setStyle(Paint.Style.STROKE);
        boundingBoxPaint.setAntiAlias(true);

        // Paint cho crop handles
        cropHandlePaint = new Paint();
        cropHandlePaint.setColor(Color.BLUE);
        cropHandlePaint.setStrokeWidth(4f);
        cropHandlePaint.setStyle(Paint.Style.FILL_AND_STROKE);
        cropHandlePaint.setAntiAlias(true);

        // Paint cho background
        backgroundPaint = new Paint();
        backgroundPaint.setColor(Color.parseColor("#f8f8f8"));
        backgroundPaint.setStyle(Paint.Style.FILL);
    }

    private void initPaths() {
        signaturePath = new Path();
        signatureFrame = new RectF();
        boundingBox = new RectF();
        cropBox = new RectF();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        // Khởi tạo khung signature ở giữa màn hình
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

        // Chỉ vẽ background khi ở chế độ vẽ (Drawing Mode)
        if (isDrawingMode) {
            canvas.drawRect(0, 0, getWidth(), getHeight(), backgroundPaint);
        }

        // Vẽ khung signature nếu được bật
        if (showSignatureFrame && isFrameResizable) {
            drawSignatureFrame(canvas);
        }

        // Clip vùng vẽ trong khung signature chỉ khi khung hiển thị
        canvas.save();
        if (showSignatureFrame) {
            canvas.clipRect(signatureFrame);
        }

        // Vẽ chữ ký
        canvas.drawPath(signaturePath, signaturePaint);

        // Vẽ overlayBitmap
        if (overlayBitmap != null) {
            // Sử dụng signatureFrame làm đích vẽ để chữ ký co giãn và di chuyển theo khung
            canvas.drawBitmap(overlayBitmap, null, signatureFrame, null);
        }

        canvas.restore();

        // Vẽ bounding box nếu được bật
        if (showBoundingBox && !boundingBox.isEmpty()) {
            canvas.drawRect(boundingBox, boundingBoxPaint);

            // Vẽ text thông tin
            Paint textPaint = new Paint();
            textPaint.setColor(Color.RED);
            textPaint.setTextSize(24);
            textPaint.setAntiAlias(true);
            canvas.drawText("Bounding Box", boundingBox.left, boundingBox.top - 10, textPaint);
        }

        // Vẽ crop box và handles nếu được bật
        if (showCropHandles && !cropBox.isEmpty()) {
            drawCropBox(canvas);
            drawCropHandles(canvas);
        }
    }

    private void drawSignatureFrame(Canvas canvas) {
        // Vẽ khung chính
        canvas.drawRect(signatureFrame, framePaint);

        // Vẽ handles để resize khung
        if (isFrameResizable) {
            drawFrameHandles(canvas);
        }

        // Vẽ text hướng dẫn
        if (signaturePath.isEmpty() && overlayBitmap == null) {
            Paint textPaint = new Paint();
            textPaint.setColor(Color.parseColor("#666666"));
            textPaint.setTextSize(32);
            textPaint.setAntiAlias(true);
            textPaint.setTextAlign(Paint.Align.CENTER);

            float textX = signatureFrame.centerX();
            float textY = signatureFrame.centerY() - 20;
            canvas.drawText("Ký tên ở đây", textX, textY, textPaint);

            textPaint.setTextSize(20);
            textPaint.setColor(Color.parseColor("#999999"));
            canvas.drawText("Kéo góc để thay đổi kích thước", textX, textY + 40, textPaint);
        }
    }

    private void drawFrameHandles(Canvas canvas) {
        Paint handlePaint = new Paint();
        handlePaint.setColor(Color.parseColor("#4CAF50"));
        handlePaint.setStyle(Paint.Style.FILL);
        handlePaint.setAntiAlias(true);

        float handleRadius = HANDLE_SIZE / 3f;

        // 4 góc của khung
        canvas.drawCircle(signatureFrame.left, signatureFrame.top, handleRadius, handlePaint);
        canvas.drawCircle(signatureFrame.right, signatureFrame.top, handleRadius, handlePaint);
        canvas.drawCircle(signatureFrame.left, signatureFrame.bottom, handleRadius, handlePaint);
        canvas.drawCircle(signatureFrame.right, signatureFrame.bottom, handleRadius, handlePaint);

        // 4 cạnh giữa
        canvas.drawCircle(signatureFrame.centerX(), signatureFrame.top, handleRadius, handlePaint);
        canvas.drawCircle(signatureFrame.centerX(), signatureFrame.bottom, handleRadius, handlePaint);
        canvas.drawCircle(signatureFrame.left, signatureFrame.centerY(), handleRadius, handlePaint);
        canvas.drawCircle(signatureFrame.right, signatureFrame.centerY(), handleRadius, handlePaint);
    }

    private void drawCropBox(Canvas canvas) {
        Paint cropBoxPaint = new Paint(boundingBoxPaint);
        cropBoxPaint.setColor(Color.parseColor("#2196F3"));
        cropBoxPaint.setStrokeWidth(6f);
        canvas.drawRect(cropBox, cropBoxPaint);

        // Vẽ text thông tin
        Paint textPaint = new Paint();
        textPaint.setColor(Color.parseColor("#2196F3"));
        textPaint.setTextSize(20);
        textPaint.setAntiAlias(true);
        canvas.drawText("Crop Area", cropBox.left, cropBox.top - 10, textPaint);
    }

    private void drawCropHandles(Canvas canvas) {
        float handleRadius = HANDLE_SIZE / 2f;

        // 4 góc crop box
        canvas.drawCircle(cropBox.left, cropBox.top, handleRadius, cropHandlePaint);
        canvas.drawCircle(cropBox.right, cropBox.top, handleRadius, cropHandlePaint);
        canvas.drawCircle(cropBox.left, cropBox.bottom, handleRadius, cropHandlePaint);
        canvas.drawCircle(cropBox.right, cropBox.bottom, handleRadius, cropHandlePaint);

        // Vẽ border cho handles
        Paint borderPaint = new Paint();
        borderPaint.setColor(Color.WHITE);
        borderPaint.setStrokeWidth(3f);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setAntiAlias(true);

        canvas.drawCircle(cropBox.left, cropBox.top, handleRadius, borderPaint);
        canvas.drawCircle(cropBox.right, cropBox.top, handleRadius, borderPaint);
        canvas.drawCircle(cropBox.left, cropBox.bottom, handleRadius, borderPaint);
        canvas.drawCircle(cropBox.right, cropBox.bottom, handleRadius, borderPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        // Nếu ở chế độ vẽ chữ ký
        if (isDrawingMode) {
            boolean allowDrawing = true;
            if (showSignatureFrame && !signatureFrame.contains(x, y)) {
                allowDrawing = false;
            }

            if (allowDrawing) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        signaturePath.moveTo(x, y);
                        if (listener != null) {
                            listener.onSignatureChanged();
                        }
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
        }
        // Nếu ở chế độ chỉnh sửa (crop, resize frame)
        else {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    return handleTouchDown(x, y);
                case MotionEvent.ACTION_MOVE:
                    return handleTouchMove(x, y);
                case MotionEvent.ACTION_UP:
                    return handleTouchUp(x, y);
            }
        }

        return super.onTouchEvent(event);
    }

    private boolean handleTouchDown(float x, float y) {
        lastTouchX = x;
        lastTouchY = y;

        // Kiểm tra crop handles trước
        if (showCropHandles) {
            activeHandle = getCropHandleAtPoint(x, y);
            if (activeHandle != -1) {
                return true;
            }
        }

        // Kiểm tra frame handles
        if (isFrameResizable && showSignatureFrame) {
            activeHandle = getFrameHandleAtPoint(x, y);
            if (activeHandle != -1) {
                return true;
            }
        }

        // Nếu không chạm vào handle nào, kiểm tra xem có chạm vào khung hoặc crop box để di chuyển không
        if (showCropHandles && cropBox.contains(x, y)) {
            activeHandle = 4; // Di chuyển crop box (handle type 4)
            return true;
        }

        if (showSignatureFrame && isFrameResizable && signatureFrame.contains(x, y)) {
            activeHandle = 12; // Di chuyển signature frame (handle type 12)
            return true;
        }

        return false;
    }

    private boolean handleTouchMove(float x, float y) {
        if (activeHandle != -1) {
            float dx = x - lastTouchX;
            float dy = y - lastTouchY;

            // 0-3: Crop handles
            if (activeHandle >= 0 && activeHandle <= 3) {
                updateCropBox(x, y);
            }
            // 4: Di chuyển Crop Box
            else if (activeHandle == 4) {
                cropBox.offset(dx, dy);
            }
            // 5-11: Frame handles
            else if (activeHandle >= 5 && activeHandle <= 11) {
                updateSignatureFrame(x, y);
            }
            // 12: Di chuyển Signature Frame
            else if (activeHandle == 12) {
                signatureFrame.offset(dx, dy);
                // Vì chúng ta vẽ overlayBitmap trực tiếp vào signatureFrame,
                // chúng ta không cần cập nhật overlayX, overlayY.
            }

            // Cập nhật lại vị trí cuối cùng
            lastTouchX = x;
            lastTouchY = y;

            // Đảm bảo các khung nằm trong giới hạn
            if (activeHandle >= 0 && activeHandle <= 4) {
                constrainCropBox();
            } else if (activeHandle >= 5 && activeHandle <= 12) {
                constrainSignatureFrame();
            }

            invalidate();
            return true;
        }

        return false;
    }

    private boolean handleTouchUp(float x, float y) {
        if (activeHandle != -1) {
            if (activeHandle >= 0 && activeHandle <= 4 && listener != null) {
                listener.onCropBoxChanged(new RectF(cropBox));
            } else if (activeHandle >= 5 && activeHandle <= 12 && listener != null) {
                listener.onFrameResized(new RectF(signatureFrame));
            }
            activeHandle = -1;
            return true;
        }

        return false;
    }

    private int getCropHandleAtPoint(float x, float y) {
        float tolerance = HANDLE_TOUCH_TOLERANCE / 2f;

        if (Math.abs(x - cropBox.left) < tolerance && Math.abs(y - cropBox.top) < tolerance) {
            return 0; // Top-left
        }
        if (Math.abs(x - cropBox.right) < tolerance && Math.abs(y - cropBox.top) < tolerance) {
            return 1; // Top-right
        }
        if (Math.abs(x - cropBox.left) < tolerance && Math.abs(y - cropBox.bottom) < tolerance) {
            return 2; // Bottom-left
        }
        if (Math.abs(x - cropBox.right) < tolerance && Math.abs(y - cropBox.bottom) < tolerance) {
            return 3; // Bottom-right
        }

        return -1;
    }

    private int getFrameHandleAtPoint(float x, float y) {
        float tolerance = HANDLE_TOUCH_TOLERANCE / 2f;

        // 4 góc
        if (Math.abs(x - signatureFrame.left) < tolerance && Math.abs(y - signatureFrame.top) < tolerance) {
            return 5; // Top-left
        }
        if (Math.abs(x - signatureFrame.right) < tolerance && Math.abs(y - signatureFrame.top) < tolerance) {
            return 6; // Top-right
        }
        if (Math.abs(x - signatureFrame.left) < tolerance && Math.abs(y - signatureFrame.bottom) < tolerance) {
            return 7; // Bottom-left
        }
        if (Math.abs(x - signatureFrame.right) < tolerance && Math.abs(y - signatureFrame.bottom) < tolerance) {
            return 8; // Bottom-right
        }

        // 4 cạnh giữa
        if (Math.abs(x - signatureFrame.centerX()) < tolerance && Math.abs(y - signatureFrame.top) < tolerance) {
            return 9; // Top
        }
        if (Math.abs(x - signatureFrame.centerX()) < tolerance && Math.abs(y - signatureFrame.bottom) < tolerance) {
            return 10; // Bottom
        }
        if (Math.abs(x - signatureFrame.left) < tolerance && Math.abs(y - signatureFrame.centerY()) < tolerance) {
            return 11; // Left
        }
        if (Math.abs(x - signatureFrame.right) < tolerance && Math.abs(y - signatureFrame.centerY()) < tolerance) {
            return 12; // Right
        }

        return -1;
    }

    private void updateCropBox(float x, float y) {
        float deltaX = x - lastTouchX;
        float deltaY = y - lastTouchY;

        switch (activeHandle) {
            case 0: // Top-left
                cropBox.left += deltaX;
                cropBox.top += deltaY;
                break;
            case 1: // Top-right
                cropBox.right += deltaX;
                cropBox.top += deltaY;
                break;
            case 2: // Bottom-left
                cropBox.left += deltaX;
                cropBox.bottom += deltaY;
                break;
            case 3: // Bottom-right
                cropBox.right += deltaX;
                cropBox.bottom += deltaY;
                break;
        }
    }

    private void updateSignatureFrame(float x, float y) {
        float deltaX = x - lastTouchX;
        float deltaY = y - lastTouchY;

        switch (activeHandle) {
            case 5: // Top-left corner
                signatureFrame.left += deltaX;
                signatureFrame.top += deltaY;
                break;
            case 6: // Top-right corner
                signatureFrame.right += deltaX;
                signatureFrame.top += deltaY;
                break;
            case 7: // Bottom-left corner
                signatureFrame.left += deltaX;
                signatureFrame.bottom += deltaY;
                break;
            case 8: // Bottom-right corner
                signatureFrame.right += deltaX;
                signatureFrame.bottom += deltaY;
                break;
            case 9: // Top edge
                signatureFrame.top += deltaY;
                break;
            case 10: // Bottom edge
                signatureFrame.bottom += deltaY;
                break;
            case 11: // Left edge
                signatureFrame.left += deltaX;
                break;
            case 12: // Right edge
                signatureFrame.right += deltaX;
                break;
        }
    }

    private void constrainCropBox() {
        // Đảm bảo crop box trong bounds của signature frame
        if (cropBox.left < signatureFrame.left) cropBox.left = signatureFrame.left;
        if (cropBox.top < signatureFrame.top) cropBox.top = signatureFrame.top;
        if (cropBox.right > signatureFrame.right) cropBox.right = signatureFrame.right;
        if (cropBox.bottom > signatureFrame.bottom) cropBox.bottom = signatureFrame.bottom;

        // Đảm bảo kích thước tối thiểu
        float minSize = HANDLE_SIZE * 2;
        if (cropBox.width() < minSize) {
            cropBox.right = cropBox.left + minSize;
        }
        if (cropBox.height() < minSize) {
            cropBox.bottom = cropBox.top + minSize;
        }
    }

    private void constrainSignatureFrame() {
        // Đảm bảo signature frame trong bounds của view
        if (signatureFrame.left < 0) signatureFrame.left = 0;
        if (signatureFrame.top < 0) signatureFrame.top = 0;
        if (signatureFrame.right > getWidth()) signatureFrame.right = getWidth();
        if (signatureFrame.bottom > getHeight()) signatureFrame.bottom = getHeight();

        // Đảm bảo kích thước tối thiểu
        float minWidth = 200f;
        float minHeight = 100f;
        if (signatureFrame.width() < minWidth) {
            signatureFrame.right = signatureFrame.left + minWidth;
        }
        if (signatureFrame.height() < minHeight) {
            signatureFrame.bottom = signatureFrame.top + minHeight;
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
        invalidate();

        if (listener != null) {
            listener.onSignatureChanged();
        }
    }

    public void detectBoundingBox() {
        if (signaturePath.isEmpty() && overlayBitmap == null) {
            return;
        }

        // --- Cập nhật logic phát hiện bounding box ---
        Bitmap bitmapToDetect = null;
        RectF drawingBounds = new RectF();
        float offsetX = 0;
        float offsetY = 0;

        if (overlayBitmap != null) {
            // Nếu có overlay bitmap, sử dụng nó và bounds của signatureFrame
            bitmapToDetect = overlayBitmap;
            offsetX = signatureFrame.left;
            offsetY = signatureFrame.top;
        } else {
            // Nếu chỉ có chữ ký (không có overlay)
            // Lấy bounds của Path
            signaturePath.computeBounds(drawingBounds, true);

            // Tạo bitmap từ toàn bộ vùng view để đảm bảo phát hiện chính xác
            bitmapToDetect = Bitmap.createBitmap(
                    getWidth(),
                    getHeight(),
                    Bitmap.Config.ARGB_8888
            );
            Canvas canvas = new Canvas(bitmapToDetect);
            canvas.drawPath(signaturePath, signaturePaint);

            offsetX = 0;
            offsetY = 0;
        }

        // Tìm bounding box dựa trên pixel
        if (bitmapToDetect != null) {
            RectF detectedBox = findContentBounds(bitmapToDetect);

            // Chuyển đổi tọa độ về view coordinate
            boundingBox.set(
                    detectedBox.left + offsetX,
                    detectedBox.top + offsetY,
                    detectedBox.right + offsetX,
                    detectedBox.bottom + offsetY
            );
        } else if (!drawingBounds.isEmpty()) {
            // Nếu không có bitmap (chỉ có path và chưa tạo bitmap), sử dụng bounds của path
            boundingBox.set(drawingBounds);
        }

        // Khởi tạo crop box
        cropBox.set(boundingBox);

        showBoundingBox = true;
        invalidate();

        if (listener != null) {
            listener.onBoundingBoxDetected(new RectF(boundingBox));
        }

        if (overlayBitmap == null && bitmapToDetect != null) {
            bitmapToDetect.recycle();
        }
        // ---------------------------------------------
    }

    private RectF findContentBounds(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        int left = width;
        int top = height;
        int right = 0;
        int bottom = 0;

        boolean hasContent = false;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = bitmap.getPixel(x, y);
                // Kiểm tra alpha (để xem có nội dung vẽ không)
                if (Color.alpha(pixel) > 0) {
                    hasContent = true;
                    left = Math.min(left, x);
                    top = Math.min(top, y);
                    right = Math.max(right, x);
                    bottom = Math.max(bottom, y);
                }
            }
        }

        if (!hasContent) {
            return new RectF();
        }

        // Thêm padding
        int padding = 20;
        left = Math.max(0, left - padding);
        top = Math.max(0, top - padding);
        right = Math.min(width, right + padding);
        bottom = Math.min(height, bottom + padding);

        return new RectF(left, top, right, bottom);
    }

    public void showCropMode() {
        // Tắt chế độ vẽ
        isDrawingMode = false;

        // Phát hiện bounding box nếu chưa có
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

    // Trong SignatureView.java, thay thế phương thức getCroppedBitmap() hiện tại
    public Bitmap getCroppedBitmap() {
        if (overlayBitmap != null && !signatureFrame.isEmpty()) {
            // Tính toán frame cần crop (đảm bảo nằm trong overlayBitmap theo tỉ lệ)
            int cropLeft = (int) signatureFrame.left;
            int cropTop = (int) signatureFrame.top;
            int cropWidth = (int) signatureFrame.width();
            int cropHeight = (int) signatureFrame.height();

            if (cropLeft < 0 || cropTop < 0 ||
                    cropLeft + cropWidth > getWidth() ||
                    cropTop + cropHeight > getHeight()) {
                return null;
            }

            // Tạo bitmap từ vùng overlayBitmap tương ứng với signatureFrame
            Bitmap fullView = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(fullView);
            canvas.drawBitmap(overlayBitmap, null, signatureFrame, null);

            // Crop đúng phần signatureFrame (từ canvas toàn màn hình)
            Bitmap cropped = Bitmap.createBitmap(
                    fullView,
                    cropLeft,
                    cropTop,
                    cropWidth,
                    cropHeight
            );

            fullView.recycle();
            return cropped;
        }

        // Nếu không có overlayBitmap, fallback về cropBox (vẽ chữ ký)
        if (!cropBox.isEmpty()) {
            Bitmap fullBitmap = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(fullBitmap);
            canvas.drawPath(signaturePath, signaturePaint);

            Bitmap cropped = Bitmap.createBitmap(fullBitmap,
                    (int) cropBox.left, (int) cropBox.top,
                    (int) cropBox.width(), (int) cropBox.height());
            fullBitmap.recycle();
            return cropped;
        }

        return null;
    }

    public Bitmap getSignatureBitmap() {
        // Cập nhật: Sử dụng boundingBox đã được phát hiện nếu có,
        // thay vì chỉ sử dụng signatureFrame mặc định.
        RectF bounds = boundingBox.isEmpty() ? signatureFrame : boundingBox;

        // Tạo bitmap chỉ chứa chữ ký trong vùng bounds
        Bitmap bitmap = Bitmap.createBitmap(
                (int) bounds.width(),
                (int) bounds.height(),
                Bitmap.Config.ARGB_8888
        );
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.TRANSPARENT);

        // Translate để vẽ từ (0,0) của vùng bounds
        canvas.translate(-bounds.left, -bounds.top);
        canvas.drawPath(signaturePath, signaturePaint);

        return bitmap;
    }

    // Getters và Setters mới
    public boolean isEmpty() {
        return signaturePath.isEmpty();
    }

    public RectF getSignatureFrame() {
        return new RectF(signatureFrame);
    }

    // Thêm phương thức setSignatureFrame
    public void setSignatureFrame(RectF rectF) {
        this.signatureFrame.set(rectF);
        // Cần đảm bảo khung không vượt quá giới hạn view nếu nó nằm ngoài màn hình
        constrainSignatureFrame();
        invalidate();
    }

    public RectF getBoundingBox() {
        return new RectF(boundingBox);
    }

    public RectF getCropBox() {
        return new RectF(cropBox);
    }

    public void setOnSignatureChangeListener(OnSignatureChangeListener listener) {
        this.listener = listener;
    }

    // Phương thức loadBitmap để hiển thị ảnh từ ImageSignPreviewActivity
    public void loadBitmap(Bitmap bitmap) {
        if (bitmap != null) {
            this.overlayBitmap = bitmap;
            // Xóa đường vẽ cũ nếu có, vì chúng ta đang tải một ảnh mới
            signaturePath.reset();

            // Cần đảm bảo khung chữ ký được thiết lập phù hợp với kích thước ảnh đã tải
            // (Đây là logic tối thiểu để đảm bảo ảnh được hiển thị)
            int width = getWidth();
            int height = getHeight();

            if (width > 0 && height > 0) {
                // Đặt khung ban đầu tại trung tâm hoặc vị trí mặc định
                float overlayWidth = bitmap.getWidth();
                float overlayHeight = bitmap.getHeight();

                // Đặt vị trí ban đầu của signatureFrame (ví dụ: ở góc trên bên trái hoặc trung tâm)
                // Tạm thời đặt tại (0,0) với kích thước gốc của bitmap, ImageSignPreviewActivity sẽ điều chỉnh sau
                signatureFrame.set(0, 0, overlayWidth, overlayHeight);
            }

            // Đặt ở chế độ không vẽ khi ảnh đã được tải
            isDrawingMode = false;
            showSignatureFrame = true;
            invalidate();
        }
    }

    // Đã loại bỏ setOverlayPosition(float x, float y)
}