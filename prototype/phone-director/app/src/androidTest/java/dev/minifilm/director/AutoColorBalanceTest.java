package dev.minifilm.director;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;
import android.util.Log;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Synthetic neutral/cast/exposure fixtures only; no real-world grading-accuracy claim. */
@RunWith(AndroidJUnit4.class)
public final class AutoColorBalanceTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

    @Test(timeout = 120_000) public void realLocalFrameSamplingMeasuresCastAndDimDetailWithinConservativeBounds() throws Exception {
        Take cast = clip(0), dim = clip(1), intentional = clip(2);
        byte[] castOriginal = Files.readAllBytes(new File(cast.uri.getPath()).toPath());
        byte[] dimOriginal = Files.readAllBytes(new File(dim.uri.getPath()).toPath());
        try {
            List<AutoColorBalance.Balance> result = analyze(Arrays.asList(cast, dim, intentional));
            assertEquals(3, result.size());
            AutoColorBalance.Balance balanced = result.get(0), brighter = result.get(1), kept = result.get(2);
            assertTrue(balanced.matches(cast)); assertTrue(balanced.applied);
            assertTrue("Red cast should be gently reduced", balanced.redScale < 1);
            assertTrue("Blue deficiency should be gently lifted", balanced.blueScale > 1);
            assertTrue("Known neutral fixture must contain usable distributed detail", balanced.neutralFraction > .7f);
            assertTrue(brighter.applied); assertTrue("Known dim fixture receives a small exposure lift", brighter.brightness > 0);
            assertTrue(brighter.meanLuma < 80); assertIdentity(kept);
            for (AutoColorBalance.Balance balance : result) {
                assertBounds(balance); assertEquals(3, balance.sampledFrames);
                assertEquals(3, balance.sampledAtMs.size());
                for (long time : balance.sampledAtMs) assertTrue(time >= balance.inMs && time < balance.outMs);
            }
            assertArrayEquals(castOriginal, Files.readAllBytes(new File(cast.uri.getPath()).toPath()));
            assertArrayEquals(dimOriginal, Files.readAllBytes(new File(dim.uri.getPath()).toPath()));
            Log.i("MiniFilmAutoBalanceTest", "LOCAL_AUTO_PASS samplesPerCut=3 castRGB=" + balanced.neutralRed + ","
                    + balanced.neutralGreen + "," + balanced.neutralBlue + " gains=" + balanced.redScale + ","
                    + balanced.greenScale + "," + balanced.blueScale + " dimLuma=" + brighter.meanLuma
                    + " brightness=" + brighter.brightness + " intentionalFlatIdentity=true originalsUnchanged=true");
        } finally { delete(cast); delete(dim); delete(intentional); }
    }

    @Test public void uniformIntentionalColorsClippedHighlightsAndInsufficientNeutralsAbstain() {
        for (int mode : new int[] { 2, 3, 4 }) {
            Bitmap bitmap = fixture(mode); byte[] before = pixels(bitmap);
            try {
                AutoColorBalance.Balance balance = AutoColorBalance.measure(dummy(), Arrays.asList(bitmap, bitmap, bitmap));
                assertIdentity(balance); assertFalse(balance.reason.isEmpty());
                assertTrue(balance.reason, balance.reason.contains(mode == 2 ? "uniform" : mode == 3 ? "insufficient" : "clipped"));
                assertArrayEquals("Measurement must not recolor source", before, pixels(bitmap));
            } finally { bitmap.recycle(); }
        }
    }

    @Test public void inconsistentLightingAndTooFewSamplesAbstainAndAlreadyNeutralStaysClean() {
        Bitmap warm = fixture(0), cool = fixture(5), neutral = fixture(6);
        try {
            AutoColorBalance.Balance changed = AutoColorBalance.measure(dummy(), Arrays.asList(warm, cool, warm));
            assertIdentity(changed); assertTrue(changed.reason.contains("changes"));
            assertIdentity(AutoColorBalance.measure(dummy(), Arrays.asList(warm, warm)));
            assertIdentity(AutoColorBalance.measure(dummy(), Arrays.asList(neutral, neutral, neutral)));
        } finally { warm.recycle(); cool.recycle(); neutral.recycle(); }
    }

    @Test(timeout = 120_000) public void snapshotPairsByUriShotAndRangeAndIgnoresUnselectedRemoteInput() throws Exception {
        Take cast = clip(0), ignored = new Take(Uri.parse("https://example.invalid/private.mp4"), "remote", "", "", 1000);
        ignored.selected = false; String id = cast.shotId; long in = cast.inMs, out = cast.outMs;
        AutoColorBalance analyzer = new AutoColorBalance(context);
        try {
            CountDownLatch done = new CountDownLatch(1); AtomicReference<List<AutoColorBalance.Balance>> result = new AtomicReference<>();
            AtomicReference<String> error = new AtomicReference<>();
            analyzer.analyze(Arrays.asList(cast, ignored), listener(done, result, error));
            cast.shotId = "changed"; cast.inMs += 100; cast.outMs -= 100;
            assertTrue(done.await(30, TimeUnit.SECONDS)); assertNull(error.get(), error.get());
            assertEquals(1, result.get().size()); AutoColorBalance.Balance balance = result.get().get(0);
            assertFalse(balance.matches(cast)); assertEquals(id, balance.shotId); assertEquals(in, balance.inMs); assertEquals(out, balance.outMs);
            Take exact = new Take(cast.uri, id, "", "", 1000); exact.inMs = in; exact.outMs = out;
            assertTrue(balance.matches(exact)); exact.uri = Uri.parse("file:///unrelated.mp4"); assertFalse(balance.matches(exact));
        } finally { analyzer.close(); delete(cast); }
    }

    @Test(timeout = 120_000) public void cancellationSuppressesStaleCallbacksAndSelectedRemoteUriIsRejected() throws Exception {
        Take cast = clip(0); AutoColorBalance analyzer = new AutoColorBalance(context);
        CountDownLatch stale = new CountDownLatch(1);
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                analyzer.analyze(Collections.singletonList(cast), new AutoColorBalance.Listener() {
                    public void onComplete(List<AutoColorBalance.Balance> result, long elapsed) { stale.countDown(); }
                    public void onError(String error) { stale.countDown(); }
                });
                analyzer.cancel();
            });
            assertFalse("Cancelled work must not update the creator UI", stale.await(500, TimeUnit.MILLISECONDS));
            Take remote = new Take(Uri.parse("https://example.invalid/clip.mp4"), "remote", "", "", 1000);
            CountDownLatch done = new CountDownLatch(1); AtomicReference<List<AutoColorBalance.Balance>> result = new AtomicReference<>();
            AtomicReference<String> error = new AtomicReference<>();
            analyzer.analyze(Collections.singletonList(remote), listener(done, result, error));
            assertTrue(done.await(5, TimeUnit.SECONDS)); assertNull(result.get()); assertTrue(error.get().contains("local cuts"));
            assertTrue(new File(cast.uri.getPath()).exists());
        } finally { analyzer.close(); delete(cast); }
    }

    private List<AutoColorBalance.Balance> analyze(List<Take> takes) throws Exception {
        AutoColorBalance analyzer = new AutoColorBalance(context);
        CountDownLatch done = new CountDownLatch(1); AtomicReference<List<AutoColorBalance.Balance>> result = new AtomicReference<>();
        AtomicReference<String> error = new AtomicReference<>();
        try {
            analyzer.analyze(takes, listener(done, result, error)); assertTrue(done.await(45, TimeUnit.SECONDS));
            assertNull(error.get(), error.get()); assertNotNull(result.get()); return result.get();
        } finally { analyzer.close(); }
    }
    private static AutoColorBalance.Listener listener(CountDownLatch done,
            AtomicReference<List<AutoColorBalance.Balance>> result, AtomicReference<String> error) {
        return new AutoColorBalance.Listener() {
            public void onComplete(List<AutoColorBalance.Balance> balances, long elapsed) { result.set(balances); done.countDown(); }
            public void onError(String message) { error.set(message); done.countDown(); }
        };
    }
    private static void assertIdentity(AutoColorBalance.Balance b) {
        assertFalse(b.applied); assertEquals(1, b.redScale, 0); assertEquals(1, b.greenScale, 0);
        assertEquals(1, b.blueScale, 0); assertEquals(0, b.brightness, 0);
    }
    private static void assertBounds(AutoColorBalance.Balance b) {
        for (float gain : new float[] { b.redScale, b.greenScale, b.blueScale }) assertTrue(gain >= .94f && gain <= 1.06f);
        assertTrue(Math.abs(b.brightness) <= .04f); assertFalse(Float.isNaN(b.meanLuma));
    }
    private static Take dummy() { return new Take(Uri.parse("file:///synthetic.mp4"), "synthetic", "", "", 1000); }
    private static byte[] pixels(Bitmap bitmap) {
        ByteBuffer bytes = ByteBuffer.allocate(bitmap.getByteCount()); bitmap.copyPixelsToBuffer(bytes); return bytes.array();
    }
    private static Bitmap fixture(int mode) {
        Bitmap bitmap = Bitmap.createBitmap(96, 128, Bitmap.Config.ARGB_8888);
        for (int y = 0; y < 128; y++) for (int x = 0; x < 96; x++) {
            int tile = (x / 12 + y / 16) % 4, v = new int[] { 70, 100, 130, 160 }[tile];
            int r = v, g = v, b = v;
            if (mode == 0) { r = Math.round(v * 1.05f); b = Math.round(v * .95f); }
            if (mode == 1) r = g = b = new int[] { 42, 57, 72, 92 }[tile];
            if (mode == 2) { r = 170; g = 55; b = 45; }
            if (mode == 3) { r = new int[] { 70, 120, 180, 235 }[tile]; g = 35; b = 45; }
            if (mode == 4) r = g = b = tile == 0 ? 255 : v;
            if (mode == 5) { r = Math.round(v * .95f); b = Math.round(v * 1.05f); }
            bitmap.setPixel(x, y, Color.rgb(r, g, b));
        }
        return bitmap;
    }
    private Take clip(int mode) throws Exception {
        return createClip(context, mode);
    }
    static Take createClip(Context context, int mode) throws Exception {
        File directory = new File(context.getFilesDir(), "synthetic-auto-balance"); directory.mkdirs();
        File file = new File(directory, "balance-" + mode + "-" + UUID.randomUUID() + ".mp4");
        try { encode(file, mode); } catch (Exception error) { file.delete(); throw error; }
        Take take = new Take(Uri.fromFile(file), "balance-" + mode, "Synthetic color fixture", "", 1000);
        take.inMs = 100; take.outMs = 900; return take;
    }
    private static void delete(Take take) { new File(take.uri.getPath()).delete(); }

    private static void encode(File file, int mode) throws Exception {
        // Match the 360x640/24fps flexible-YUV path already exercised by DemoAssets on this phone.
        final int width = 360, height = 640, fps = 24;
        MediaCodec codec = MediaCodec.createEncoderByType("video/avc");
        MediaMuxer muxer = new MediaMuxer(file.getPath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        Bitmap original = fixture(mode), bitmap = Bitmap.createScaledBitmap(original, width, height, false);
        original.recycle(); int[] rgb = new int[width * height]; bitmap.getPixels(rgb, 0, width, 0, 0, width, height);
        State state = new State(); boolean started = false;
        try {
            MediaCodecInfo.CodecCapabilities capabilities = codec.getCodecInfo().getCapabilitiesForType("video/avc");
            boolean flexible = false;
            for (int color : capabilities.colorFormats)
                if (color == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible) flexible = true;
            assertTrue("Fixture encoder must support flexible YUV input", flexible);
            assertTrue("Fixture encoder must support the known 360x640 size", capabilities.getVideoCapabilities().isSizeSupported(width, height));
            assertTrue("Fixture encoder must support 360x640 at 24fps", capabilities.getVideoCapabilities().areSizeAndRateSupported(width, height, fps));
            MediaFormat format = MediaFormat.createVideoFormat("video/avc", width, height);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
            format.setInteger(MediaFormat.KEY_BIT_RATE, 900_000); format.setInteger(MediaFormat.KEY_FRAME_RATE, fps);
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE); codec.start(); started = true;
            for (int i = 0; i <= fps; i++) {
                long end = System.nanoTime() + 10_000_000_000L; int input;
                while ((input = codec.dequeueInputBuffer(20_000)) < 0) {
                    drain(codec, muxer, state, false); if (System.nanoTime() > end) throw new IllegalStateException("Fixture input timeout");
                }
                if (i == fps) codec.queueInputBuffer(input, 0, 0, 1_000_000, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                else {
                    Image image = codec.getInputImage(input); assertNotNull(image); fill(image, rgb, width, height);
                    codec.queueInputBuffer(input, 0, width * height * 3 / 2, i * 1_000_000L / fps, 0);
                }
                drain(codec, muxer, state, i == fps);
            }
        } finally {
            bitmap.recycle(); if (started) try { codec.stop(); } catch (Exception ignored) { }
            codec.release();
            try { if (state.started) muxer.stop(); } finally { muxer.release(); }
        }
    }
    private static void fill(Image image, int[] rgb, int width, int height) {
        Image.Plane[] planes = image.getPlanes();
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int color = rgb[y * width + x], r = Color.red(color), g = Color.green(color), b = Color.blue(color);
            put(planes[0], y, x, ((66 * r + 129 * g + 25 * b + 128) >> 8) + 16);
            if ((x & 1) == 0 && (y & 1) == 0) {
                put(planes[1], y / 2, x / 2, ((-38 * r - 74 * g + 112 * b + 128) >> 8) + 128);
                put(planes[2], y / 2, x / 2, ((112 * r - 94 * g - 18 * b + 128) >> 8) + 128);
            }
        }
    }
    private static void put(Image.Plane plane, int y, int x, int value) {
        ByteBuffer buffer = plane.getBuffer(); buffer.put(buffer.position() + y * plane.getRowStride() + x * plane.getPixelStride(), (byte) value);
    }
    private static void drain(MediaCodec codec, MediaMuxer muxer, State state, boolean eos) throws Exception {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo(); long end = System.nanoTime() + 10_000_000_000L;
        while (System.nanoTime() < end) {
            int output = codec.dequeueOutputBuffer(info, eos ? 20_000 : 0);
            if (output == MediaCodec.INFO_TRY_AGAIN_LATER) { if (!eos) return; }
            else if (output == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                state.track = muxer.addTrack(codec.getOutputFormat()); muxer.start(); state.started = true;
            } else if (output >= 0) {
                ByteBuffer bytes = codec.getOutputBuffer(output);
                if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) info.size = 0;
                if (info.size > 0) { assertTrue(state.started); bytes.position(info.offset); bytes.limit(info.offset + info.size); muxer.writeSampleData(state.track, bytes, info); }
                boolean finished = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                codec.releaseOutputBuffer(output, false); if (finished) return;
            }
        }
        throw new IllegalStateException("Fixture output timeout");
    }
    private static final class State { int track; boolean started; }
}
