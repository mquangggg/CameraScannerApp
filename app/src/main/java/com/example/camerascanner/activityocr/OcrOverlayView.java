// File: com/example/camerascanner/activityocr/OcrOverlayView.java

package com.example.camerascanner.activityocr;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent; // Import mới
import android.view.ScaleGestureDetector; // Import mới
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

    // Biến mới cho Zoom và Pan
    private ScaleGestureDetector scaleGestureDetector;
    private float currentZoomScale = 1f; // Tỷ lệ zoom hiện tại của người dùng
    private float lastTouchX;
    private float lastTouchY;
    private static final int INVALID_POINTER_ID = -1;
    private int activePointerId = INVALID_POINTER_ID;

    // Giới hạn Zoom
    private static final float MIN_ZOOM_SCALE = 0.5f;
    private static final float MAX_ZOOM_SCALE = 5.0f;

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

        // Khởi tạo ScaleGestureDetector
        scaleGestureDetector = new ScaleGestureDetector(getContext(), new ScaleListener());
    }

    public void setOcrResult(Text result, int imageWidth, int imageHeight) {
        this.ocrTextResult = result;
        this.originalImageWidth = imageWidth;
        this.originalImageHeight = imageHeight;

        // Đặt lại zoom về 1f khi có ảnh mới
        this.currentZoomScale = 1f;
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

        float initialScale;
        float initialTranslateX;
        float initialTranslateY;

        if (imageAspectRatio > viewAspectRatio) {
            initialScale = viewWidth / originalImageWidth;
            initialTranslateY = (viewHeight - (originalImageHeight * initialScale)) / 2;
            initialTranslateX = 0;
        } else {
            initialScale = viewHeight / originalImageHeight;
            initialTranslateX = (viewWidth - (originalImageWidth * initialScale)) / 2;
            initialTranslateY = 0;
        }

        // Áp dụng scale ban đầu và zoom của người dùng
        scaleFactorX = initialScale * currentZoomScale;
        scaleFactorY = initialScale * currentZoomScale;

        // Cập nhật translateX và translateY dựa trên scale mới (tính toán lại để zoom vào giữa)
        // Cách đơn giản hóa: Giữ nguyên translation ban đầu và scale từ đó.
        // Để zoom vào giữa pinch, cần logic phức tạp hơn (xem onScale trong ScaleListener).
        translateX = initialTranslateX;
        translateY = initialTranslateY;

        // Sau khi scale, cần điều chỉnh lại translation để giữ ảnh trong tầm nhìn
        // (Không bắt buộc phải thực hiện ở đây mà có thể để cho logic Pan xử lý)
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        // Xử lý cử chỉ zoom
        scaleGestureDetector.onTouchEvent(ev);

        // Xử lý cử chỉ pan
        final int action = ev.getAction();
        switch (action & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN: {
                final float x = ev.getX();
                final float y = ev.getY();
                lastTouchX = x;
                lastTouchY = y;
                activePointerId = ev.getPointerId(0);
                break;
            }

            case MotionEvent.ACTION_MOVE: {
                final int pointerIndex = ev.findPointerIndex(activePointerId);
                if (pointerIndex == INVALID_POINTER_ID) {
                    break;
                }
                final float x = ev.getX(pointerIndex);
                final float y = ev.getY(pointerIndex);

                // Chỉ pan khi không zoom (hoặc khi chỉ có một ngón tay)
                if (!scaleGestureDetector.isInProgress()) {
                    float dx = x - lastTouchX;
                    float dy = y - lastTouchY;

                    translateX += dx;
                    translateY += dy;

                    // Giới hạn pan để không kéo ảnh ra ngoài màn hình quá nhiều
                    // Đây là một cách giới hạn cơ bản, có thể cần phức tạp hơn
                    // để giới hạn chính xác dựa trên kích thước ảnh và view.
                    translateX = Math.max(getWidth() - originalImageWidth * scaleFactorX, Math.min(0, translateX));
                    translateY = Math.max(getHeight() - originalImageHeight * scaleFactorY, Math.min(0, translateY));


                    invalidate();
                }

                lastTouchX = x;
                lastTouchY = y;
                break;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                activePointerId = INVALID_POINTER_ID;
                break;
            }

            case MotionEvent.ACTION_POINTER_UP: {
                final int pointerIndex = (ev.getAction() & MotionEvent.ACTION_POINTER_INDEX_MASK)
                        >> MotionEvent.ACTION_POINTER_INDEX_SHIFT;
                final int pointerId = ev.getPointerId(pointerIndex);
                if (pointerId == activePointerId) {
                    // This was our active pointer going up. Choose a new
                    // active pointer and adjust accordingly.
                    final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
                    lastTouchX = ev.getX(newPointerIndex);
                    lastTouchY = ev.getY(newPointerIndex);
                    activePointerId = ev.getPointerId(newPointerIndex);
                }
                break;
            }
        }
        return true; // Quan trọng: trả về true để tiêu thụ sự kiện chạm
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        private float focusX;
        private float focusY;

        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            focusX = detector.getFocusX();
            focusY = detector.getFocusY();
            return true; // Trả về true để nhận các sự kiện scale tiếp theo
        }

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            float previousZoomScale = currentZoomScale;
            currentZoomScale *= detector.getScaleFactor();
            currentZoomScale = Math.max(MIN_ZOOM_SCALE, Math.min(currentZoomScale, MAX_ZOOM_SCALE));

            // Điều chỉnh translation để zoom vào điểm lấy nét (focus point)
            // Đây là công thức zoom vào điểm lấy nét.
            translateX = focusX - ((focusX - translateX) * (currentZoomScale / previousZoomScale));
            translateY = focusY - ((focusY - translateY) * (currentZoomScale / previousZoomScale));

            calculateScaleFactors(); // Tính toán lại scaleFactorX, Y dựa trên currentZoomScale
            invalidate(); // Vẽ lại View
            return true; // Trả về true để nhận các sự kiện scale tiếp theo
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
        // Kích thước chữ cũng sẽ bị ảnh hưởng bởi currentZoomScale
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
                        @SuppressLint("DrawAllocation") RectF drawnRect = new RectF(
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