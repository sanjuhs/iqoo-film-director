package dev.minifilm.director;

import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;

/** Maps CameraX raw-buffer crop bounds into ML Kit's rotated-image coordinates. */
public final class FramingGeometry {
    private final RectF visible;

    public FramingGeometry(int bufferWidth, int bufferHeight, Rect cropRect, int rotationDegrees) {
        if (bufferWidth <= 0 || bufferHeight <= 0 || cropRect == null
                || !(rotationDegrees == 0 || rotationDegrees == 90
                || rotationDegrees == 180 || rotationDegrees == 270)) {
            throw new IllegalArgumentException("Invalid camera frame geometry");
        }
        Rect crop = new Rect(cropRect);
        if (!crop.intersect(0, 0, bufferWidth, bufferHeight) || crop.isEmpty())
            throw new IllegalArgumentException("Camera crop has no visible image area");
        Matrix rotation = new Matrix();
        rotation.setRotate(rotationDegrees);
        RectF rotatedBuffer = new RectF(0, 0, bufferWidth, bufferHeight);
        rotation.mapRect(rotatedBuffer);
        rotation.postTranslate(-rotatedBuffer.left, -rotatedBuffer.top);
        visible = new RectF(crop);
        rotation.mapRect(visible);
        if (visible.width() <= 0 || visible.height() <= 0)
            throw new IllegalArgumentException("Rotated crop has no visible image area");
    }

    /** A copy; callers cannot mutate the shared frame's crop. */
    public RectF getVisibleBounds() { return new RectF(visible); }
    public float getWidth() { return visible.width(); }
    public float getHeight() { return visible.height(); }
    public boolean contains(float x, float y) {
        return Float.isFinite(x) && Float.isFinite(y) && visible.contains(x, y);
    }
    public float normalizeX(float rotatedImageX) { return (rotatedImageX - visible.left) / visible.width(); }
    public float normalizeY(float rotatedImageY) { return (rotatedImageY - visible.top) / visible.height(); }
}
