package com.example.camerascanner.activitysignature.signatureview.nothing;

import com.example.camerascanner.activitysignature.signatureview.SignatureBitmapProcessor;
import com.example.camerascanner.activitysignature.signatureview.SignatureStateManager;

/**
 * Manages different modes and states of the signature view
 */
public class SignatureModeManager {
    private SignatureStateManager stateManager;
    private SignatureBitmapProcessor bitmapProcessor;

    public SignatureModeManager(SignatureStateManager stateManager, SignatureBitmapProcessor bitmapProcessor) {
        this.stateManager = stateManager;
        this.bitmapProcessor = bitmapProcessor;
    }

    public void enterDrawingMode() {
        stateManager.setDrawingMode(true);
        stateManager.setShowCropHandles(false);
        stateManager.setFrameResizable(true);
    }

    public void enterEditingMode() {
        stateManager.setDrawingMode(false);
        stateManager.setFrameResizable(true);
        stateManager.setShowCropHandles(false);
    }

    public void enterCropMode() {
        stateManager.setDrawingMode(false);
        stateManager.setFrameResizable(false);

        // Auto-detect bounding box if not already detected
        if (stateManager.getBoundingBox().isEmpty()) {
            bitmapProcessor.detectBoundingBox();
        }

        stateManager.setShowCropHandles(true);
    }

    public void exitCropMode() {
        stateManager.setShowCropHandles(false);
        stateManager.setDrawingMode(true);
        stateManager.setFrameResizable(true);
    }

    public void toggleFrameVisibility() {
        stateManager.setShowSignatureFrame(!stateManager.isShowSignatureFrame());
    }

    public void resetToInitialState() {
        stateManager.clear();
        enterDrawingMode();
    }

    // Mode checking methods
    public boolean isInDrawingMode() {
        return stateManager.isDrawingMode();
    }

    public boolean isInEditingMode() {
        return !stateManager.isDrawingMode() && !stateManager.isShowCropHandles();
    }

    public boolean isInCropMode() {
        return !stateManager.isDrawingMode() && stateManager.isShowCropHandles();
    }

    public boolean isFrameVisible() {
        return stateManager.isShowSignatureFrame();
    }

    public boolean isFrameResizable() {
        return stateManager.isFrameResizable();
    }
}