package com.example.camerascanner;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

    public class SignatureView extends View {
        private Paint paint;
        private Path path;

        public SignatureView(Context context, @Nullable AttributeSet attrs) {
            super(context, attrs);
            path = new Path();
            paint = new Paint();
            paint.setColor(0xFF000000); // Màu đen
            paint.setStrokeWidth(8f);
            paint.setStyle(Paint.Style.STROKE);
            paint.setAntiAlias(true);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.drawPath(path, paint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float x = event.getX();
            float y = event.getY();

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    path.moveTo(x, y);
                    break;
                case MotionEvent.ACTION_MOVE:
                case MotionEvent.ACTION_UP:
                    path.lineTo(x, y);
                    break;
            }

            invalidate();
            return true;
        }

        public Path getPath() {
            return path;
        }

        public void clear() {
            path.reset();
            invalidate();
        }
    }

