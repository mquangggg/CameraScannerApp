package com.example.camerascanner.activitycrop;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.ArrayList;

public class CustomCropView extends View {
    private Paint paint;
    private Path cropPath;
    private ArrayList<PointF> points;
    private PointF currentPoint;
    private int currentPointIndex = -1;
    private float touchTolerance = 50f;
    private final float magnifierRadius = 150f;

    private MagnifierView magnifierView;
    private Bitmap currentImageBitmap;
    private float[] imageToViewMatrixValues = new float[9];
    private float[] viewToImageMatrixValues = new float[9];

    public CustomCropView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        paint = new Paint();
        paint.setColor(0xFFFF0000);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(5f);
        paint.setAntiAlias(true);

        cropPath = new Path();
        points = new ArrayList<>();
    }

    public void setMagnifierView(MagnifierView magnifierView) {
        this.magnifierView = magnifierView;
    }

    public void setImageData(Bitmap imageBitmap, float[] imageToViewMatrix, float[] viewToImageMatrix) {
        this.currentImageBitmap = imageBitmap;
        if (imageToViewMatrix != null) System.arraycopy(imageToViewMatrix, 0, this.imageToViewMatrixValues, 0, 9);
        if (viewToImageMatrix != null) System.arraycopy(viewToImageMatrix, 0, this.viewToImageMatrixValues, 0, 9);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (points.size() == 4) {
            cropPath.reset();
            cropPath.moveTo(points.get(0).x, points.get(0).y);
            cropPath.lineTo(points.get(1).x, points.get(1).y);
            cropPath.lineTo(points.get(2).x, points.get(2).y);
            cropPath.lineTo(points.get(3).x, points.get(3).y);
            cropPath.close();
            canvas.drawPath(cropPath, paint);

            Paint pointPaint = new Paint(paint);
            pointPaint.setStyle(Paint.Style.FILL);
            pointPaint.setColor(0xFF00FF00);
            for (PointF point : points) {
                canvas.drawCircle(point.x, point.y, touchTolerance / 2, pointPaint);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                currentPointIndex = findPointIndexAt(x, y);
                if (currentPointIndex != -1) {
                    currentPoint = points.get(currentPointIndex);
                    if (magnifierView != null) {
                        showMagnifier(x, y, currentPointIndex);
                    }
                    return true;
                }
                if (points.size() < 4) {
                    points.add(new PointF(x, y));
                    invalidate();
                    return true;
                }
                break;

            case MotionEvent.ACTION_MOVE:
                if (currentPoint != null) {
                    currentPoint.x = Math.max(0, Math.min(getWidth(), x));
                    currentPoint.y = Math.max(0, Math.min(getHeight(), y));
                    invalidate();
                    if (magnifierView != null) {
                        showMagnifier(currentPoint.x, currentPoint.y, currentPointIndex);
                    }
                }
                return true;

            case MotionEvent.ACTION_UP:
                currentPoint = null;
                currentPointIndex = -1;
                if (magnifierView != null) {
                    magnifierView.hideMagnifier();
                }
                return true;
        }
        return super.onTouchEvent(event);
    }

    private int findPointIndexAt(float x, float y) {
        for (int i = 0; i < points.size(); i++) {
            PointF point = points.get(i);
            if (Math.abs(point.x - x) < touchTolerance && Math.abs(point.y - y) < touchTolerance) {
                return i;
            }
        }
        return -1;
    }

    private void showMagnifier(float currentTouchX, float currentTouchY, int pointIndex) {
        if (currentImageBitmap == null || magnifierView == null || points.size() != 4) return;

        float cropRegionSize = 100;

        float bitmapWidth = currentImageBitmap.getWidth();
        float bitmapHeight = currentImageBitmap.getHeight();

        float viewWidth = getWidth();
        float viewHeight = getHeight();

        Matrix viewToImageMatrix = new Matrix();
        viewToImageMatrix.setValues(viewToImageMatrixValues);

        float[] pointOnBitmap = new float[2];
        float[] viewPoint = {currentTouchX, currentTouchY};
        viewToImageMatrix.mapPoints(pointOnBitmap, viewPoint);

        float bitmapSampleX = pointOnBitmap[0];
        float bitmapSampleY = pointOnBitmap[1];

        float scaleFactorImageToView;
        float ratioBitmap = bitmapWidth / bitmapHeight;
        float ratioView = viewWidth / viewHeight;

        if (ratioView > ratioBitmap) {
            scaleFactorImageToView = viewHeight / bitmapHeight;
        } else {
            scaleFactorImageToView = viewWidth / bitmapWidth;
        }
        float sampleSizeOnBitmap = cropRegionSize / scaleFactorImageToView;

        RectF sampleRect = new RectF(
                bitmapSampleX - sampleSizeOnBitmap / 2,
                bitmapSampleY - sampleSizeOnBitmap / 2,
                bitmapSampleX + sampleSizeOnBitmap / 2,
                bitmapSampleY + sampleSizeOnBitmap / 2
        );

        sampleRect.left = Math.max(0, sampleRect.left);
        sampleRect.top = Math.max(0, sampleRect.top);
        sampleRect.right = Math.min(bitmapWidth, sampleRect.right);
        sampleRect.bottom = Math.min(bitmapHeight, sampleRect.bottom);

        // Tính toán các đường crop lines trong vùng zoom
        ArrayList<PointF> cropLinesInZoom = calculateCropLinesInZoomRegion(
                sampleRect, currentTouchX, currentTouchY, pointIndex, scaleFactorImageToView
        );

        // Truyền thông tin crop lines vào magnifier
        magnifierView.setMagnifiedRegion(currentImageBitmap, sampleRect,
                currentTouchX, currentTouchY, getWidth(), getHeight(),
                pointIndex, cropLinesInZoom);
    }

    private ArrayList<PointF> calculateCropLinesInZoomRegion(RectF sampleRect,
                                                             float touchX, float touchY,
                                                             int currentPointIndex,
                                                             float scaleFactorImageToView) {
        ArrayList<PointF> cropLines = new ArrayList<>();

        if (points.size() != 4) return cropLines;

        // Chuyển đổi các điểm crop từ view sang bitmap coordinates
        Matrix viewToImageMatrix = new Matrix();
        viewToImageMatrix.setValues(viewToImageMatrixValues);

        PointF[] bitmapPoints = new PointF[4];
        for (int i = 0; i < 4; i++) {
            float[] pts = {points.get(i).x, points.get(i).y};
            viewToImageMatrix.mapPoints(pts);
            bitmapPoints[i] = new PointF(pts[0], pts[1]);
        }

        // Tọa độ của điểm hiện tại trên bitmap (tức là tâm của vùng zoom)
        PointF currentPointOnBitmap = bitmapPoints[currentPointIndex];

        // Tính toán các đường liền kề với điểm hiện tại
        int prevIndex = (currentPointIndex + 3) % 4; // Điểm trước
        int nextIndex = (currentPointIndex + 1) % 4; // Điểm sau

        float magnifierSize = magnifierRadius * 2; // Size thực của magnifier

        // Chỉ vẽ đoạn từ điểm hiện tại đến điểm trước và từ điểm hiện tại đến điểm sau.
        // Các điểm này đã được căn chỉnh trong không gian của kính lúp.
        // Bạn cần đảm bảo rằng các điểm này được vẽ sao cho chúng xuất phát từ tâm của kính lúp
        // và hướng về các phía tương ứng.

        // Chuyển đổi điểm hiện tại trên bitmap sang tọa độ của kính lúp
        PointF centerInMagnifierCoords = convertBitmapPointToMagnifierCoords(currentPointOnBitmap, sampleRect, magnifierSize);

        // Chuyển đổi điểm trước và sau sang tọa độ của kính lúp
        PointF prevInMagnifierCoords = convertBitmapPointToMagnifierCoords(bitmapPoints[prevIndex], sampleRect, magnifierSize);
        PointF nextInMagnifierCoords = convertBitmapPointToMagnifierCoords(bitmapPoints[nextIndex], sampleRect, magnifierSize);

        // Thêm đường từ điểm hiện tại (tâm) đến điểm trước
        cropLines.add(centerInMagnifierCoords);
        cropLines.add(prevInMagnifierCoords);

        // Thêm đường từ điểm hiện tại (tâm) đến điểm sau
        cropLines.add(centerInMagnifierCoords);
        cropLines.add(nextInMagnifierCoords);

        return cropLines;
    }
    /**
     * Chuyển đổi điểm từ bitmap coordinates sang magnifier coordinates
     */
    private PointF convertBitmapPointToMagnifierCoords(PointF bitmapPoint, RectF sampleRect, float magnifierSize) {
        float relativeX = (bitmapPoint.x - sampleRect.left) / sampleRect.width();
        float relativeY = (bitmapPoint.y - sampleRect.top) / sampleRect.height();

        return new PointF(relativeX * magnifierSize, relativeY * magnifierSize);
    }

    public ArrayList<PointF> getCropPoints() {
        return new ArrayList<>(points);
    }

    public void clearPoints() {
        points.clear();
        invalidate();
    }

    public void addPoint(PointF point) {
        if (points.size() < 4) {
            points.add(point);
        }
    }
}