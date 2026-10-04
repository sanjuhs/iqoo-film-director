package dev.minifilm.director;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/** Opt-in deterministic neutral/exposure heuristic. Not learned grading or scene understanding. */
public final class AutoColorBalance implements AutoCloseable {
    public interface Listener {
        void onComplete(List<Balance> balances, long elapsedMs);
        void onError(String message);
    }
    public static final class Balance {
        public final Uri uri;
        public final String shotId, reason;
        public final long inMs, outMs;
        public final boolean applied;
        public final float redScale, greenScale, blueScale, brightness;
        public final float neutralRed, neutralGreen, neutralBlue, meanLuma, lumaStdDev,
                neutralFraction, clippedFraction;
        public final int sampledFrames;
        public final List<Long> sampledAtMs;

        private Balance(Take cut, String reason, float r, float g, float b, float brightness,
                List<Stats> frames, List<Long> times) {
            uri = cut.uri; shotId = cut.shotId; inMs = cut.inMs; outMs = cut.outMs;
            this.reason = reason; redScale = r; greenScale = g; blueScale = b; this.brightness = brightness;
            applied = r != 1 || g != 1 || b != 1 || brightness != 0;
            sampledFrames = frames.size(); sampledAtMs = Collections.unmodifiableList(new ArrayList<>(times));
            float nr = 0, ng = 0, nb = 0, luma = 0, deviation = 0, neutral = 0, clipped = 0;
            for (Stats s : frames) {
                nr += s.r; ng += s.g; nb += s.b; luma += s.luma;
                deviation += s.deviation; neutral += s.neutral; clipped += s.clipped;
            }
            int divisor = Math.max(1, frames.size());
            neutralRed = nr / divisor; neutralGreen = ng / divisor; neutralBlue = nb / divisor;
            meanLuma = luma / divisor; lumaStdDev = deviation / divisor;
            neutralFraction = neutral / divisor; clippedFraction = clipped / divisor;
        }
        /** Pair to the source/range identity, never to an unrelated list position. */
        public boolean matches(Take cut) {
            return Objects.equals(uri, cut.uri) && Objects.equals(shotId, cut.shotId)
                    && inMs == cut.inMs && outMs == cut.outMs;
        }
    }

    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final AtomicInteger generation = new AtomicInteger();
    private volatile boolean closed;
    private Future<?> pending;

    public AutoColorBalance(Context context) { this.context = context.getApplicationContext(); }

    public synchronized void analyze(List<Take> takes, Listener listener) {
        cancel(); final int run = generation.get();
        try {
            if (closed) throw new IllegalStateException("Auto balance has been closed.");
            List<Take> snapshots = new ArrayList<>(); long total = 0;
            for (Take take : takes) {
                if (!take.selected) continue;
                if (take.uri == null || !("content".equals(take.uri.getScheme()) || "file".equals(take.uri.getScheme()))
                        || take.inMs < 0 || take.outMs > take.durationMs || take.outMs - take.inMs < 250)
                    throw new IllegalArgumentException("Auto balance needs valid local cuts of at least 0.25 seconds.");
                Take copy = new Take(take.uri, take.shotId, take.title, take.caption, take.durationMs);
                copy.inMs = take.inMs; copy.outMs = take.outMs; snapshots.add(copy); total += copy.outMs - copy.inMs;
            }
            if (snapshots.isEmpty() || snapshots.size() > 12 || total > 180_000)
                throw new IllegalArgumentException("Auto balance supports 1–12 selected cuts and at most three minutes.");
            pending = worker.submit(() -> {
                long start = SystemClock.elapsedRealtime(); List<Balance> result = new ArrayList<>();
                for (Take cut : snapshots) {
                    if (run != generation.get() || Thread.currentThread().isInterrupted()) return;
                    result.add(read(cut, run));
                }
                main.post(() -> {
                    if (!closed && run == generation.get())
                        listener.onComplete(Collections.unmodifiableList(result), SystemClock.elapsedRealtime() - start);
                });
            });
        } catch (Exception error) {
            String message = error.getMessage() == null ? "Auto balance could not start. Choose Clean or retry." : error.getMessage();
            main.post(() -> { if (!closed && run == generation.get()) listener.onError(message); });
        }
    }

    /** Invalidates callbacks; an already-running platform decode may finish its current frame. */
    public synchronized void cancel() {
        generation.incrementAndGet(); if (pending != null) pending.cancel(true); pending = null;
    }
    @Override public synchronized void close() { closed = true; cancel(); worker.shutdownNow(); }

    private Balance read(Take cut, int run) {
        List<Stats> frames = new ArrayList<>(); List<Long> times = new ArrayList<>();
        MediaMetadataRetriever source = new MediaMetadataRetriever();
        try {
            source.setDataSource(context, cut.uri);
            long duration = Long.parseLong(source.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
            if (cut.outMs > duration + 50) return identity(cut, "Clean retained: source duration changed.", frames, times);
            String transfer = source.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COLOR_TRANSFER);
            if (Integer.toString(MediaFormat.COLOR_TRANSFER_ST2084).equals(transfer)
                    || Integer.toString(MediaFormat.COLOR_TRANSFER_HLG).equals(transfer))
                return identity(cut, "Clean retained: HDR auto balance is not evaluated.", frames, times);
            long started = SystemClock.elapsedRealtime();
            for (int sixth : new int[] { 1, 3, 5 }) {
                if (run != generation.get() || Thread.currentThread().isInterrupted())
                    return identity(cut, "Clean retained: analysis cancelled.", frames, times);
                if (SystemClock.elapsedRealtime() - started > 15_000)
                    return identity(cut, "Clean retained: frame inspection took too long.", frames, times);
                long at = cut.inMs + (cut.outMs - cut.inMs) * sixth / 6;
                Bitmap bitmap = source.getScaledFrameAtTime(at * 1000, MediaMetadataRetriever.OPTION_CLOSEST, 160, 160);
                if (bitmap != null) {
                    try { frames.add(stats(bitmap)); times.add(at); } finally { bitmap.recycle(); }
                }
            }
            return decide(cut, frames, times);
        } catch (Exception error) {
            return identity(cut, "Clean retained: these local frames could not be read.", frames, times);
        } finally { try { source.release(); } catch (Exception ignored) { } }
    }

    /** Package-visible fixture entry: consumes measurements, never mutates supplied bitmaps. */
    static Balance measure(Take cut, List<Bitmap> bitmaps) {
        List<Stats> frames = new ArrayList<>();
        for (Bitmap bitmap : bitmaps) frames.add(stats(bitmap));
        return decide(cut, frames, Collections.emptyList());
    }

    private static Balance decide(Take cut, List<Stats> frames, List<Long> times) {
        if (frames.size() < 3) return identity(cut, "Clean retained: fewer than three readable samples.", frames, times);
        float minRG = Float.MAX_VALUE, maxRG = 0, minBG = Float.MAX_VALUE, maxBG = 0;
        for (Stats s : frames) {
            if (s.deviation < 12) return identity(cut, "Clean retained: uniform frame may be an intentional color.", frames, times);
            if (s.clipped > .08f || s.luma < 35 || s.luma > 225)
                return identity(cut, "Clean retained: clipped or extreme lighting is uncertain.", frames, times);
            if (s.neutral < .25f || s.quadrants < 3)
                return identity(cut, "Clean retained: insufficient distributed neutral detail; preserve garment colors.", frames, times);
            float rg = s.r / s.g, bg = s.b / s.g;
            minRG = Math.min(minRG, rg); maxRG = Math.max(maxRG, rg);
            minBG = Math.min(minBG, bg); maxBG = Math.max(maxBG, bg);
        }
        if (maxRG - minRG > .04f || maxBG - minBG > .04f)
            return identity(cut, "Clean retained: sampled lighting/color changes within this cut.", frames, times);
        Balance measured = identity(cut, "", frames, times);
        // Media3 RgbMatrix/Brightness operate in linear BT.709. Frame values are only an approximation.
        double r = linear(measured.neutralRed), g = linear(measured.neutralGreen), b = linear(measured.neutralBlue);
        double target = Math.cbrt(r * g * b);
        float red = bound(target / r, .94, 1.06), green = bound(target / g, .94, 1.06), blue = bound(target / b, .94, 1.06);
        float brightness = 0;
        if (measured.neutralFraction >= .65f) {
            float neutralLuma = .2126f * measured.neutralRed + .7152f * measured.neutralGreen + .0722f * measured.neutralBlue;
            if (neutralLuma < 80 && measured.meanLuma < 80)
                brightness = bound((linear(100) - target) * .30, 0, .04);
            else if (neutralLuma > 190 && measured.meanLuma > 190)
                brightness = bound((linear(180) - target) * .30, -.04, 0);
        }
        if (Math.abs(red - 1) < .005 && Math.abs(green - 1) < .005
                && Math.abs(blue - 1) < .005 && Math.abs(brightness) < .002)
            return identity(cut, "Clean retained: measured neutral detail already needs little change.", frames, times);
        return new Balance(cut, "Small neutral/exposure adjustment from three local samples; review the look.",
                red, green, blue, brightness, frames, times);
    }

    private static Balance identity(Take cut, String reason, List<Stats> frames, List<Long> times) {
        return new Balance(cut, reason, 1, 1, 1, 0, frames, times);
    }
    private static float bound(double value, double low, double high) { return (float) Math.max(low, Math.min(high, value)); }
    private static double linear(double encoded) {
        double v = encoded / 255; return v < .081 ? v / 4.5 : Math.pow((v + .099) / 1.099, 1 / .45);
    }

    private static Stats stats(Bitmap bitmap) {
        int width = bitmap.getWidth(), height = bitmap.getHeight();
        int[] pixels = new int[width * height]; bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        double sum = 0, squares = 0, r = 0, g = 0, b = 0;
        int neutral = 0, clipped = 0; int[] quadrants = new int[4];
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int color = pixels[y * width + x], red = Color.red(color), green = Color.green(color), blue = Color.blue(color);
            int max = Math.max(red, Math.max(green, blue)), min = Math.min(red, Math.min(green, blue));
            double luma = .2126 * red + .7152 * green + .0722 * blue;
            sum += luma; squares += luma * luma;
            if (max >= 248 || luma <= 12) clipped++;
            // Slightly stricter in shadows, where a color/noise cast is less reliable.
            double threshold = .10 + .08 * Math.min(1, luma / 180);
            if (luma >= 40 && luma <= 220 && max > 0 && (max - min) / (double) max <= threshold) {
                neutral++; r += red; g += green; b += blue;
                quadrants[(y >= height / 2 ? 2 : 0) + (x >= width / 2 ? 1 : 0)]++;
            }
        }
        Stats result = new Stats(); int count = Math.max(1, pixels.length);
        result.luma = (float) (sum / count); result.deviation = (float) Math.sqrt(Math.max(0, squares / count - Math.pow(sum / count, 2)));
        result.neutral = neutral / (float) count; result.clipped = clipped / (float) count;
        result.r = (float) (r / Math.max(1, neutral)); result.g = (float) (g / Math.max(1, neutral)); result.b = (float) (b / Math.max(1, neutral));
        for (int quadrant : quadrants) if (quadrant >= count / 40 && quadrant >= 4) result.quadrants++;
        return result;
    }
    private static final class Stats { float r, g, b, luma, deviation, neutral, clipped; int quadrants; }
}
