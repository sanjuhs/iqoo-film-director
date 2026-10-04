package dev.minifilm.director;

import android.graphics.Rect;
import android.graphics.RectF;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Android coordinate transforms only: no camera, pose inference or accuracy claim. */
@RunWith(AndroidJUnit4.class)
public final class FramingGeometryTest {
    @Test public void asymmetricBufferCropMapsToEachRotatedImageOrientation() {
        Rect raw = new Rect(100, 20, 620, 460);
        assertBounds(new FramingGeometry(640, 480, raw, 0), 100, 20, 620, 460);
        assertBounds(new FramingGeometry(640, 480, raw, 90), 20, 100, 460, 620);
        assertBounds(new FramingGeometry(640, 480, raw, 180), 20, 20, 540, 460);
        assertBounds(new FramingGeometry(640, 480, raw, 270), 20, 20, 460, 540);
        assertEquals("Mapping must not mutate CameraX's raw crop", new Rect(100, 20, 620, 460), raw);
    }

    @Test public void portraitViewportExcludesLandmarksVisibleOnlyInTheUncroppedAnalysisBuffer() {
        // Landscape sensor buffer rotated to 480x640; the shared 9:16 viewport is 360x640.
        FramingGeometry geometry = new FramingGeometry(640, 480, new Rect(0, 60, 640, 420), 90);
        assertBounds(geometry, 60, 0, 420, 640);
        assertEquals(9f / 16f, geometry.getWidth() / geometry.getHeight(), .0001f);
        assertTrue(geometry.contains(240, 320));
        assertFalse("A model landmark in the hidden left strip is outside the reel", geometry.contains(20, 320));
        assertFalse("A model landmark in the hidden right strip is outside the reel", geometry.contains(450, 320));
        assertFalse("Right and bottom bounds are exclusive pixels", geometry.contains(420, 320));
        assertFalse(geometry.contains(240, 640));
        assertEquals(0f, geometry.normalizeX(60), .0001f);
        assertEquals(.5f, geometry.normalizeX(240), .0001f);
        assertEquals(.5f, geometry.normalizeY(320), .0001f);
        assertTrue("Normalization must retain evidence that a point is outside the visible crop",
                geometry.normalizeX(20) < 0);
    }

    @Test public void offsetCropNormalizesHeadAndShoulderPositionsRelativeToVisibleBounds() {
        FramingGeometry geometry = new FramingGeometry(640, 480, new Rect(100, 20, 620, 460), 90);
        // Visible rotated bounds 20..460 by 100..620; the top is not the full-buffer origin.
        assertEquals(0f, geometry.normalizeY(100), .0001f);
        assertEquals(.5f, geometry.normalizeY(360), .0001f);
        float leftShoulder = geometry.normalizeX(130), rightShoulder = geometry.normalizeX(350);
        assertEquals(.5f, (leftShoulder + rightShoulder) / 2f, .0001f);
        assertEquals(.5f, Math.abs(350 - 130) / geometry.getWidth(), .0001f);
        assertFalse(geometry.contains(Float.NaN, 100));
        assertFalse(geometry.contains(100, Float.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class,
                () -> new FramingGeometry(640, 480, new Rect(800, 0, 900, 200), 90));
        assertThrows(IllegalArgumentException.class,
                () -> new FramingGeometry(640, 480, new Rect(0, 0, 640, 480), 45));
    }

    private static void assertBounds(FramingGeometry geometry, float left, float top, float right, float bottom) {
        RectF bounds = geometry.getVisibleBounds();
        assertEquals(left, bounds.left, .0001f); assertEquals(top, bounds.top, .0001f);
        assertEquals(right, bounds.right, .0001f); assertEquals(bottom, bounds.bottom, .0001f);
    }
}
