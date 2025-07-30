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
     * Thêm một đường thẳng vào danh sách crop lines trong magnifier
     */
    private void addLineToMagnifier(PointF p1, PointF p2, RectF sampleRect,
                                    float magnifierSize, ArrayList<PointF> cropLines) {

        // Tìm các điểm giao của đường thẳng với hình chữ nhật zoom
        ArrayList<PointF> intersections = getLineRectIntersections(p1, p2, sampleRect);

        if (intersections.size() >= 2) {
            // Chuyển đổi sang tọa độ magnifier
            PointF start = convertBitmapPointToMagnifierCoords(intersections.get(0), sampleRect, magnifierSize);
            PointF end = convertBitmapPointToMagnifierCoords(intersections.get(1), sampleRect, magnifierSize);

            cropLines.add(start);
            cropLines.add(end);
        }
    }

    /**
     * Tìm giao điểm của đường thẳng với hình chữ nhật
     */
    private ArrayList<PointF> getLineRectIntersections(PointF p1, PointF p2, RectF rect) {
        ArrayList<PointF> intersections = new ArrayList<>();

        // Kiểm tra giao với 4 cạnh của hình chữ nhật
        // Cạnh trái (x = rect.left)
        PointF leftIntersection = getLineSegmentIntersection(p1, p2,
                new PointF(rect.left, rect.top), new PointF(rect.left, rect.bottom));
        if (leftIntersection != null) intersections.add(leftIntersection);

        // Cạnh phải (x = rect.right)
        PointF rightIntersection = getLineSegmentIntersection(p1, p2,
                new PointF(rect.right, rect.top), new PointF(rect.right, rect.bottom));
        if (rightIntersection != null) intersections.add(rightIntersection);

        // Cạnh trên (y = rect.top)
        PointF topIntersection = getLineSegmentIntersection(p1, p2,
                new PointF(rect.left, rect.top), new PointF(rect.right, rect.top));
        if (topIntersection != null) intersections.add(topIntersection);

        // Cạnh dưới (y = rect.bottom)
        PointF bottomIntersection = getLineSegmentIntersection(p1, p2,
                new PointF(rect.left, rect.bottom), new PointF(rect.right, rect.bottom));
        if (bottomIntersection != null) intersections.add(bottomIntersection);

        // Thêm các điểm nằm trong rect
        if (rect.contains(p1.x, p1.y)) intersections.add(new PointF(p1.x, p1.y));
        if (rect.contains(p2.x, p2.y)) intersections.add(new PointF(p2.x, p2.y));

        return intersections;
    }

    /**
     * Tìm giao điểm của hai đoạn thẳng
     */
    private PointF getLineSegmentIntersection(PointF p1, PointF p2, PointF p3, PointF p4) {
        float x1 = p1.x, y1 = p1.y;
        float x2 = p2.x, y2 = p2.y;
        float x3 = p3.x, y3 = p3.y;
        float x4 = p4.x, y4 = p4.y;

        float denom = (x1 - x2) * (y3 - y4) - (y1 - y2) * (x3 - x4);
        if (Math.abs(denom) < 1e-6) return null; // Các đường song song

        float t = ((x1 - x3) * (y3 - y4) - (y1 - y3) * (x3 - x4)) / denom;
        float u = -((x1 - x2) * (y1 - y3) - (y1 - y2) * (x1 - x3)) / denom;

        if (u >= 0 && u <= 1) { // Giao điểm nằm trên đoạn thẳng p3-p4
            float intersectionX = x1 + t * (x2 - x1);
            float intersectionY = y1 + t * (y2 - y1);
            return new PointF(intersectionX, intersectionY);
        }

        return null;
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