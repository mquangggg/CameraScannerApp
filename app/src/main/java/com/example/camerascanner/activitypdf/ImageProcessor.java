package com.example.camerascanner.activitypdf;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.util.Log;

public class ImageProcessor {
    private static final String TAG = "ImageProcessor";

    // Enum cho các phương pháp chuyển đổi trắng đen
    public enum ConversionMethod {
        OTSU,           // Thuật toán Otsu (tự động tìm ngưỡng)
        ADAPTIVE,       // Ngưỡng thích ứng cục bộ
        SIMPLE,         // Ngưỡng cố định đơn giản
        ENHANCED        // Cải thiện độ tương phản trước khi chuyển đổi
    }

    /**
     * Chuyển đổi bitmap sang trắng đen với phương pháp mặc định (Otsu)
     */
    public static Bitmap convertToBlackAndWhite(Bitmap original) {
        return convertToBlackAndWhite(original, ConversionMethod.OTSU);
    }

    /**
     * Chuyển đổi bitmap sang trắng đen với phương pháp được chỉ định
     */
    public static Bitmap convertToBlackAndWhite(Bitmap original, ConversionMethod method) {
        if (original == null) {
            Log.e(TAG, "Input Bitmap is null for conversion.");
            return null;
        }

        switch (method) {
            case OTSU:
                return convertWithOtsu(original);
            case ADAPTIVE:
                return convertWithAdaptiveThreshold(original);
            case SIMPLE:
                return convertWithSimpleThreshold(original, 128);
            case ENHANCED:
                return convertWithEnhancement(original);
            default:
                return convertWithOtsu(original);
        }
    }

    /**
     * Chuyển đổi bằng thuật toán Otsu (tối ưu hiệu suất)
     */
    private static Bitmap convertWithOtsu(Bitmap original) {
        int width = original.getWidth();
        int height = original.getHeight();

        // Sử dụng int array để tối ưu hiệu suất
        int[] pixels = new int[width * height];
        original.getPixels(pixels, 0, width, 0, 0, width, height);

        // Tính ngưỡng Otsu tối ưu
        int otsuThreshold = findOtsuThresholdFast(pixels, width, height);
        Log.d(TAG, "Otsu Threshold found: " + otsuThreshold);

        // Áp dụng ngưỡng
        for (int i = 0; i < pixels.length; i++) {
            int pixel = pixels[i];
            int alpha = Color.alpha(pixel);
            int red = Color.red(pixel);
            int green = Color.green(pixel);
            int blue = Color.blue(pixel);

            // Tính độ xám
            int gray = (int) (0.299 * red + 0.587 * green + 0.114 * blue);

            // Áp dụng ngưỡng
            if (gray < otsuThreshold) {
                pixels[i] = Color.argb(alpha, 0, 0, 0); // Đen
            } else {
                pixels[i] = Color.argb(alpha, 255, 255, 255); // Trắng
            }
        }

        // Tạo bitmap mới
        Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        result.setPixels(pixels, 0, width, 0, 0, width, height);
        return result;
    }

    /**
     * Chuyển đổi bằng ngưỡng thích ứng cục bộ (tốt cho ảnh có ánh sáng không đều)
     */
    private static Bitmap convertWithAdaptiveThreshold(Bitmap original) {
        int width = original.getWidth();
        int height = original.getHeight();
        int[] pixels = new int[width * height];
        original.getPixels(pixels, 0, width, 0, 0, width, height);

        // Kích thước cửa sổ cho ngưỡng thích ứng
        int windowSize = Math.min(width, height) / 10; // 10% kích thước ảnh
        if (windowSize < 3) windowSize = 3;
        if (windowSize % 2 == 0) windowSize++; // Đảm bảo là số lẻ

        int[] grayPixels = new int[width * height];

        // Chuyển sang độ xám
        for (int i = 0; i < pixels.length; i++) {
            int pixel = pixels[i];
            int red = Color.red(pixel);
            int green = Color.green(pixel);
            int blue = Color.blue(pixel);
            grayPixels[i] = (int) (0.299 * red + 0.587 * green + 0.114 * blue);
        }

        // Áp dụng ngưỡng thích ứng
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int threshold = calculateLocalThreshold(grayPixels, x, y, width, height, windowSize);
                int currentGray = grayPixels[y * width + x];
                int alpha = Color.alpha(pixels[y * width + x]);

                if (currentGray < threshold) {
                    pixels[y * width + x] = Color.argb(alpha, 0, 0, 0); // Đen
                } else {
                    pixels[y * width + x] = Color.argb(alpha, 255, 255, 255); // Trắng
                }
            }
        }

        Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        result.setPixels(pixels, 0, width, 0, 0, width, height);
        return result;
    }

    /**
     * Chuyển đổi bằng ngưỡng cố định đơn giản
     */
    private static Bitmap convertWithSimpleThreshold(Bitmap original, int threshold) {
        int width = original.getWidth();
        int height = original.getHeight();
        int[] pixels = new int[width * height];
        original.getPixels(pixels, 0, width, 0, 0, width, height);

        for (int i = 0; i < pixels.length; i++) {
            int pixel = pixels[i];
            int alpha = Color.alpha(pixel);
            int red = Color.red(pixel);
            int green = Color.green(pixel);
            int blue = Color.blue(pixel);

            int gray = (int) (0.299 * red + 0.587 * green + 0.114 * blue);

            if (gray < threshold) {
                pixels[i] = Color.argb(alpha, 0, 0, 0);
            } else {
                pixels[i] = Color.argb(alpha, 255, 255, 255);
            }
        }

        Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        result.setPixels(pixels, 0, width, 0, 0, width, height);
        return result;
    }

    /**
     * Chuyển đổi với cải thiện độ tương phản trước
     */
    private static Bitmap convertWithEnhancement(Bitmap original) {
        // Bước 1: Cải thiện độ tương phản
        Bitmap enhanced = enhanceContrast(original, 1.5f);

        // Bước 2: Áp dụng Otsu
        Bitmap result = convertWithOtsu(enhanced);

        // Giải phóng bitmap tạm
        if (enhanced != original) {
            enhanced.recycle();
        }

        return result;
    }

    /**
     * Cải thiện độ tương phản của ảnh
     */
    private static Bitmap enhanceContrast(Bitmap original, float contrast) {
        Bitmap result = Bitmap.createBitmap(original.getWidth(), original.getHeight(), original.getConfig());
        Canvas canvas = new Canvas(result);

        Paint paint = new Paint();

        // Tạo ColorMatrix để điều chỉnh độ tương phản
        ColorMatrix colorMatrix = new ColorMatrix();
        float translate = (-.5f * contrast + .5f) * 255.f;
        colorMatrix.set(new float[]{
                contrast, 0, 0, 0, translate,
                0, contrast, 0, 0, translate,
                0, 0, contrast, 0, translate,
                0, 0, 0, 1, 0
        });

        paint.setColorFilter(new ColorMatrixColorFilter(colorMatrix));
        canvas.drawBitmap(original, 0, 0, paint);

        return result;
    }

    /**
     * Tính ngưỡng Otsu tối ưu hiệu suất
     */
    private static int findOtsuThresholdFast(int[] pixels, int width, int height) {
        int[] histogram = new int[256];
        long totalPixels = pixels.length;

        // Tính biểu đồ tần suất
        for (int pixel : pixels) {
            int red = Color.red(pixel);
            int green = Color.green(pixel);
            int blue = Color.blue(pixel);
            int gray = (int) (0.299 * red + 0.587 * green + 0.114 * blue);
            histogram[gray]++;
        }

        // Tính toán Otsu
        float maxVariance = 0;
        int threshold = 0;

        float sum = 0;
        for (int i = 0; i < 256; i++) {
            sum += i * histogram[i];
        }

        float sumB = 0;
        int wB = 0;
        int wF = 0;

        for (int t = 0; t < 256; t++) {
            wB += histogram[t];
            if (wB == 0) continue;

            wF = (int) totalPixels - wB;
            if (wF == 0) break;

            sumB += (float) (t * histogram[t]);

            float mB = sumB / wB;
            float mF = (sum - sumB) / wF;

            float varBetween = (float) wB * (float) wF * (mB - mF) * (mB - mF);

            if (varBetween > maxVariance) {
                maxVariance = varBetween;
                threshold = t;
            }
        }

        return threshold;
    }

    /**
     * Tính ngưỡng cục bộ cho ngưỡng thích ứng
     */
    private static int calculateLocalThreshold(int[] grayPixels, int x, int y, int width, int height, int windowSize) {
        int halfWindow = windowSize / 2;
        int sum = 0;
        int count = 0;

        for (int dy = -halfWindow; dy <= halfWindow; dy++) {
            for (int dx = -halfWindow; dx <= halfWindow; dx++) {
                int nx = x + dx;
                int ny = y + dy;

                if (nx >= 0 && nx < width && ny >= 0 && ny < height) {
                    sum += grayPixels[ny * width + nx];
                    count++;
                }
            }
        }

        return count > 0 ? sum / count : 128;
    }

    /**
     * Chuyển đổi trắng đen cho tài liệu (tối ưu cho văn bản)
     */
    public static Bitmap convertDocumentToBlackAndWhite(Bitmap original) {
        if (original == null) return null;

        // Sử dụng ngưỡng thích ứng cho tài liệu
        return convertWithAdaptiveThreshold(original);
    }

    /**
     * Chuyển đổi trắng đen nhanh (chất lượng thấp hơn nhưng nhanh)
     */
    public static Bitmap convertToBlackAndWhiteFast(Bitmap original) {
        if (original == null) return null;

        // Sử dụng ngưỡng cố định cho tốc độ
        return convertWithSimpleThreshold(original, 128);
    }
}