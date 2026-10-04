package dev.minifilm.director;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

/** Synthetic local files only. This verifies metadata validation, not live capture. */
@RunWith(AndroidJUnit4.class)
public final class CaptureFinalizationTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

    @Test public void finalizedVideoUsesActualSyntheticContainerDurationAndLeavesOriginalUnchanged() throws Exception {
        File source = new File(context.getFilesDir(), "fixtures/jacket-speech.mp4");
        assertTrue("Root must install the labelled synthetic spoken fixture", source.isFile());
        byte[] before = digest(source);
        MediaMetadataRetriever metadata = new MediaMetadataRetriever();
        long actual;
        try {
            metadata.setDataSource(source.getAbsolutePath());
            assertEquals("yes", metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO));
            assertTrue(Integer.parseInt(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)) > 0);
            assertTrue(Integer.parseInt(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)) > 0);
            actual = Long.parseLong(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
        } finally { metadata.release(); }
        assertTrue("Known synthetic speech container should remain approximately 5.75 seconds", actual >= 5700 && actual <= 5800);
        assertEquals("Trim bounds must come from the finalized container", actual, CaptureController.finalizedVideoDuration(source));
        assertArrayEquals("Metadata validation must not change original bytes", before, digest(source));
    }

    @Test public void emptyMalformedMissingAndNonVideoFilesReturnNoInventedDurationAndRemainIntact() throws Exception {
        File empty = File.createTempFile("capture-finalize-empty-", ".mp4", context.getCacheDir());
        File invalid = File.createTempFile("capture-finalize-invalid-", ".mp4", context.getCacheDir());
        File audio = File.createTempFile("capture-finalize-audio-", ".wav", context.getCacheDir());
        try {
            try (FileOutputStream output = new FileOutputStream(invalid)) {
                output.write("Synthetic text, not a video container".getBytes(StandardCharsets.UTF_8));
            }
            // Valid 100ms mono PCM WAV has duration but no video; no stats fallback is allowed.
            try (FileOutputStream output = new FileOutputStream(audio)) {
                byte[] wav = new byte[44 + 3200];
                ascii(wav, 0, "RIFF"); little(wav, 4, wav.length - 8, 4); ascii(wav, 8, "WAVE");
                ascii(wav, 12, "fmt "); little(wav, 16, 16, 4); little(wav, 20, 1, 2); little(wav, 22, 1, 2);
                little(wav, 24, 16000, 4); little(wav, 28, 32000, 4); little(wav, 32, 2, 2); little(wav, 34, 16, 2);
                ascii(wav, 36, "data"); little(wav, 40, 3200, 4); output.write(wav);
            }
            byte[] invalidBefore = digest(invalid), audioBefore = digest(audio);
            assertEquals(0, CaptureController.finalizedVideoDuration(null));
            assertEquals(0, CaptureController.finalizedVideoDuration(new File(context.getCacheDir(), "missing-synthetic-finalized-recording.mp4")));
            assertEquals(0, CaptureController.finalizedVideoDuration(empty));
            assertEquals(0, CaptureController.finalizedVideoDuration(invalid));
            assertEquals(0, CaptureController.finalizedVideoDuration(audio));
            assertTrue(empty.isFile()); assertEquals(0, empty.length());
            assertArrayEquals(invalidBefore, digest(invalid)); assertArrayEquals(audioBefore, digest(audio));
        } finally { empty.delete(); invalid.delete(); audio.delete(); }
    }

    private static byte[] digest(File source) throws Exception {
        MessageDigest hash = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new FileInputStream(source)) {
            byte[] bytes = new byte[8192]; int count; while ((count = input.read(bytes)) != -1) hash.update(bytes, 0, count);
        }
        return hash.digest();
    }
    private static void ascii(byte[] bytes, int offset, String text) {
        for (int i = 0; i < text.length(); i++) bytes[offset + i] = (byte) text.charAt(i);
    }
    private static void little(byte[] bytes, int offset, int value, int length) {
        for (int i = 0; i < length; i++) bytes[offset + i] = (byte) (value >>> (i * 8));
    }
}
