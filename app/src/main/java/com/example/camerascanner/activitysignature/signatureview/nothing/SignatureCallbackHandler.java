package com.example.camerascanner.activitysignature.signatureview.nothing;

import android.graphics.RectF;

import com.example.camerascanner.activitysignature.signatureview.SignatureView;

/**
 * Handles callbacks and notifications for signature events
 */
public class SignatureCallbackHandler {
    private SignatureView.OnSignatureChangeListener listener;

    public void setOnSignatureChangeListener(SignatureView.OnSignatureChangeListener listener) {
        this.listener = listener;
    }

    public void notifySignatureChanged() {
        if (listener != null) {
            listener.onSignatureChanged();
        }
    }

    public void notifyBoundingBoxDetected(RectF boundingBox) {
        if (listener != null) {
            listener.onBoundingBoxDetected(new RectF(boundingBox));
        }
    }

    public void notifyCropBoxChanged(RectF cropBox) {
        if (listener != null) {
            listener.onCropBoxChanged(new RectF(cropBox));
        }
    }

    public void notifyFrameResized(RectF frame) {
        if (listener != null) {
            listener.onFrameResized(new RectF(frame));
        }
    }

    public boolean hasListener() {
        return listener != null;
    }
}