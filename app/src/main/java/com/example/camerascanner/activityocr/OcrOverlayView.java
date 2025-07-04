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
    private Paint rectPaint; // Giữ lại nếu bạn muốn vẽ khung bao quanh chữ, nếu không có thể xóa

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
        textPaint.setColor(Color.BLACK); // Đảm bảo chữ màu đen
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setAntiAlias(true); // Làm mịn chữ

        rectPaint = new Paint();
        rectPaint.setColor(Color.YELLOW); // Màu khung bao quanh, bạn có thể thay đổi hoặc xóa
        rectPaint.setStyle(Paint.Style.STROKE);
        rectPaint.setStrokeWidth(2f);
    }

    /**
     * Đặt kết quả OCR và kích thước ảnh gốc để tính toán tỉ lệ và vị trí.
     * @param ocrText Kết quả Text từ ML Kit.
     * @param imageWidth Chiều rộng của ảnh gốc.
     * @param imageHeight Chiều cao của ảnh gốc.
     */
    public void setOcrResult(Text ocrText, int imageWidth, int imageHeight) {
        this.ocrTextResult = ocrText;

        // Đảm bảo View đã có kích thước trước khi tính toán
        if (getWidth() == 0 || getHeight() == 0) {
            // Nếu View chưa được layout, đợi nó có kích thước rồi gọi lại setOcrResult
            post(() -> setOcrResult(ocrText, imageWidth, imageHeight));
            return;
        }

        float viewWidth = getWidth();
        float viewHeight = getHeight();

        if (imageWidth > 0 && imageHeight > 0 && viewWidth > 0 && viewHeight > 0) {
            float imageAspectRatio = (float) imageWidth / imageHeight;
            float viewAspectRatio = viewWidth / viewHeight;

            // Tính toán scale factor để ảnh vừa khít với view trong khi giữ tỉ lệ
            if (imageAspectRatio > viewAspectRatio) {
                scaleFactorX = viewWidth / imageWidth;
                scaleFactorY = scaleFactorX;
                // Tính toán translation để căn giữa ảnh theo chiều dọc (nếu cần)
                translateY = (viewHeight - (imageHeight * scaleFactorY)) / 2;
                translateX = 0;
            } else {
                scaleFactorY = viewHeight / imageHeight;
                scaleFactorX = scaleFactorY;
                // Tính toán translation để căn giữa ảnh theo chiều ngang (nếu cần)
                translateX = (viewWidth - (imageWidth * scaleFactorX)) / 2;
                translateY = 0;
            }
        } else {
            // Đặt lại các giá trị mặc định nếu kích thước không hợp lệ
            scaleFactorX = 1f;
            scaleFactorY = 1f;
            translateX = 0;
            translateY = 0;
        }

        invalidate(); // Yêu cầu vẽ lại View
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Đổ toàn bộ canvas bằng màu trắng làm nền
        canvas.drawColor(Color.WHITE);

        if (ocrTextResult == null) {
            return;
        }

        for (Text.TextBlock block : ocrTextResult.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                for (Text.Element element : line.getElements()) {
                    Rect boundingBox = element.getBoundingBox();
                    if (boundingBox != null) {
                        // Áp dụng scaling và translation tổng thể
                        float left = boundingBox.left * scaleFactorX + translateX;
                        float top = boundingBox.top * scaleFactorY + translateY;
                        float right = boundingBox.right * scaleFactorX + translateX;
                        float bottom = boundingBox.bottom * scaleFactorY + translateY;

                        RectF drawnRect = new RectF(left, top, right, bottom);

                        // LƯU TRẠNG THÁI HIỆN TẠI CỦA CANVAS
                        canvas.save();

                        // XOAY CANVAS THEO GÓC CỦA CHỮ
                        // ML Kit trả về góc nghiêng. Giá trị 0 độ là văn bản ngang,
                        // góc dương là quay theo chiều kim đồng hồ.
                        // canvas.rotate() cũng quay theo chiều kim đồng hồ.
                        // Góc xoay cần được áp dụng quanh tâm của bounding box của element.
                        // Lưu ý: element.getAngle() có thể trả về null, nên cần kiểm tra.
                        Float angle = element.getAngle();
                        if (angle != null) {
                            canvas.rotate(angle, drawnRect.centerX(), drawnRect.centerY());
                        }

                        // VẼ KHUNG BAO QUANH CHỮ (TÙY CHỌN - có thể bỏ qua nếu bạn chỉ muốn chữ)
                        // canvas.drawRect(drawnRect, rectPaint); // Bỏ comment nếu muốn thấy khung

                        // Đặt kích thước chữ dựa trên chiều cao đã scale của khung bao quanh
                        // Nhân với 0.8f để chữ không quá sát viền, bạn có thể điều chỉnh giá trị này
                        textPaint.setTextSize(element.getBoundingBox().height() * scaleFactorY * 0.7f);
                        textPaint.setColor(Color.BLACK); // Đảm bảo màu chữ là đen

                        // Tính toán vị trí chữ để căn giữa theo chiều dọc trong khung
                        Paint.FontMetrics fm = textPaint.getFontMetrics();
                        float textHeight = fm.descent - fm.ascent;
                        float textY = drawnRect.top + (drawnRect.height() - textHeight) / 2 - fm.ascent;

                        // VẼ VĂN BẢN
                        canvas.drawText(element.getText(), drawnRect.left, textY, textPaint);

                        // PHỤC HỒI TRẠNG THÁI CANVAS TRƯỚC KHI XOAY
                        canvas.restore();
                    }
                }
            }
        }
    }
}