package com.example.camerascanner.activitycamera;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.MotionEvent;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;

public class ResizableSignatureView extends AppCompatImageView {

    private float startX, startY;
    private float dX, dY;
    private boolean isMoving = false;

    private float minWidth = 100;
    private float minHeight = 50;

    private Paint borderPaint;

    public ResizableSignatureView(Context context) {
        super(context);
        init();
    }

    public ResizableSignatureView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ResizableSignatureView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        borderPaint = new Paint();
        borderPaint.setColor(Color.BLACK);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(4);

        setScaleType(ScaleType.FIT_CENTER);
        setBackgroundColor(Color.TRANSPARENT);
        setPadding(8, 8, 8, 8);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawRect(0, 0, getWidth(), getHeight(), borderPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                startX = event.getRawX();
                startY = event.getRawY();
                dX = getX() - startX;
                dY = getY() - startY;
                isMoving = true;
                return true;

            case MotionEvent.ACTION_MOVE:
                if (isMoving) {
                    float newX = event.getRawX() + dX;
                    float newY = event.getRawY() + dY;
                    setX(newX);
                    setY(newY);
                    invalidate();
                }
                return true;

            case MotionEvent.ACTION_UP:
                isMoving = false;
                return true;
        }
        return super.onTouchEvent(event);
    }

    public void setSignatureImage(Uri imageUri) {
        setImageURI(imageUri);
    }
}
