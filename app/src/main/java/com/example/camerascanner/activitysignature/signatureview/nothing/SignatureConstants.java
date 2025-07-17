package com.example.camerascanner.activitysignature.signatureview.nothing;

/**
 * Constants used throughout the signature view components
 */
public class SignatureConstants {
    // Handle dimensions
    public static final int HANDLE_SIZE = 50;
    public static final int HANDLE_TOUCH_TOLERANCE = 80;

    // Frame settings
    public static final int FRAME_BORDER_WIDTH = 8;
    public static final float INITIAL_FRAME_WIDTH = 600f;
    public static final float INITIAL_FRAME_HEIGHT = 200f;

    // Handle identifiers
    public static final int HANDLE_NONE = -1;
    public static final int HANDLE_CROP = 1;
    public static final int HANDLE_FRAME = 2;
    public static final int HANDLE_MOVE_CROP = 100;
    public static final int HANDLE_MOVE_FRAME = 200;

    // Minimum sizes
    public static final float MIN_FRAME_WIDTH = 200f;
    public static final float MIN_FRAME_HEIGHT = 100f;

    // Bitmap processing
    public static final int CONTENT_BOUNDS_PADDING = 20;

    // Colors (as hex strings)
    public static final String COLOR_SIGNATURE = "#000000";
    public static final String COLOR_FRAME = "#4CAF50";
    public static final String COLOR_BOUNDING_BOX = "#FFFFFF";
    public static final String COLOR_CROP_HANDLE = "#0000FF";
    public static final String COLOR_CROP_BOX = "#2196F3";
    public static final String COLOR_BACKGROUND = "#f8f8f8";
    public static final String COLOR_INSTRUCTION_TEXT = "#666666";
    public static final String COLOR_INSTRUCTION_SUBTEXT = "#999999";
    public static final String COLOR_HANDLE_BORDER = "#FFFFFF";

    // Text sizes
    public static final int TEXT_SIZE_INSTRUCTION = 32;
    public static final int TEXT_SIZE_INSTRUCTION_SUB = 20;
    public static final int TEXT_SIZE_BOUNDING_BOX = 24;
    public static final int TEXT_SIZE_CROP_BOX = 20;

    // Dash path effect
    public static final float[] DASH_PATH_INTERVALS = {20f, 10f};
    public static final float DASH_PATH_PHASE = 0f;

    // Stroke widths
    public static final float STROKE_WIDTH_SIGNATURE = 6f;
    public static final float STROKE_WIDTH_BOUNDING_BOX = 4f;
    public static final float STROKE_WIDTH_CROP_HANDLE = 4f;
    public static final float STROKE_WIDTH_CROP_BOX = 6f;
    public static final float STROKE_WIDTH_HANDLE_BORDER = 3f;
}