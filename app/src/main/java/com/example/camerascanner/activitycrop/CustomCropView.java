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
// import java.util.List; // Không cần import này nữa

public class CustomCropView extends View {
    private Paint paint;
    private Path cropPath;
    private ArrayList<PointF> points;
    private PointF currentPoint;
    private int currentPointIndex = -1;
    private float touchTolerance = 50f;

    private MagnifierView magnifierView;
    private Bitmap currentImageBitmap;
    private float[] imageToViewMatrixValues = new float[9];
    private float[] viewToImageMatrixValues = new float[9];
    // private List<RectF> textBoundingBoxesInViewCoords; // Đã bỏ biến này

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
        // textBoundingBoxesInViewCoords = new ArrayList<>(); // Loại bỏ dòng này
    }

    public void setMagnifierView(MagnifierView magnifierView) {
        this.magnifierView = magnifierView;
    }

    public void setImageData(Bitmap imageBitmap, float[] imageToViewMatrix, float[] viewToImageMatrix) {
        this.currentImageBitmap = imageBitmap;
        if (imageToViewMatrix != null) System.arraycopy(imageToViewMatrix, 0, this.imageToViewMatrixValues, 0, 9);
        if (viewToImageMatrix != null) System.arraycopy(viewToImageMatrix, 0, this.viewToImageMatrixValues, 0, 9);
    }

    // Đã bỏ setter setTextBoundingBoxes

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
            pointPaint.setColor(0xFF00FFFF);
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
        if (currentImageBitmap == null || magnifierView == null) return;

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
        float ratioBitmap = (float) bitmapWidth / bitmapHeight;
        float ratioView = (float) viewWidth / viewHeight;

        if (ratioView > ratioBitmap) {
            scaleFactorImageToView = viewHeight / (float) bitmapHeight;
        } else {
            scaleFactorImageToView = viewWidth / (float) bitmapWidth;
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

        // Truyền các tham số cần thiết, đã bỏ textRects
        magnifierView.setMagnifiedRegion(currentImageBitmap, sampleRect,
                currentTouchX, currentTouchY, getWidth(), getHeight(),
                pointIndex);
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