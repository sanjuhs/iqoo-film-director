package dev.minifilm.director;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaMuxer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/** Makes an explicitly synthetic fixture using no camera, microphone or imported media. */
public final class DemoAssets {
    public interface Listener {
        void onReady(List<Take> takes);
        void onError(String message);
    }
    private static final int WIDTH = 360, HEIGHT = 640, FPS = 24, FRAMES = 72;
    private static final String[] LABELS = { "THE HOOK", "THE DETAIL", "THE REVEAL" };
    private static final String[] CAPTIONS = {
            "One idea. Three shots.", "Let the details tell the story.", "Your story, ready to share."
    };

    private DemoAssets() {}

    public static void create(Context context, Listener listener) {
        Context app = context.getApplicationContext();
        Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            try {
                File folder = new File(app.getFilesDir(), "synthetic-demo");
                if (!folder.exists() && !folder.mkdirs()) throw new IllegalStateException("Demo folder unavailable.");
                List<Take> takes = new ArrayList<>();
                for (int i = 0; i < LABELS.length; i++) {
                    File clip = new File(folder, "synthetic-" + (i + 1) + ".mp4");
                    if (!valid(clip)) {
                        try { encode(clip, i); }
                        catch (Exception e) { clip.delete(); throw e; }
                    }
                    Take take = new Take(Uri.fromFile(clip), "synthetic-" + (i + 1),
                            LABELS[i] + " · synthetic demo", CAPTIONS[i], 3000);
                    take.inMs = 500; take.outMs = 2500;
                    takes.add(take);
                }
                Log.i("MiniFilmExport", "SYNTHETIC_READY clips=3 sourceDurationMs=3000 trimmedDurationMs=2000");
                main.post(() -> listener.onReady(takes));
            } catch (Exception e) {
                Log.e("MiniFilmExport", "Synthetic fixture generation failed", e);
                main.post(() -> listener.onError("Could not generate synthetic demo: "
                        + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage())));
            }
        }, "Synthetic-demo").start();
    }

    private static boolean valid(File file) {
        if (!file.isFile() || file.length() < 1000) return false;
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(file.getAbsolutePath());
            return Long.parseLong(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)) >= 2900;
        } catch (Exception e) { return false; }
        finally { try { retriever.release(); } catch (Exception ignored) {} }
    }

    private static void encode(File destination, int shot) throws Exception {
        MediaCodec codec = null; MediaMuxer muxer = null; Bitmap frame = null;
        boolean codecStarted = false;
        MuxState state = new MuxState();
        try {
            MediaFormat format = MediaFormat.createVideoFormat("video/avc", WIDTH, HEIGHT);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
            format.setInteger(MediaFormat.KEY_BIT_RATE, 900_000);
            format.setInteger(MediaFormat.KEY_FRAME_RATE, FPS);
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
            codec = MediaCodec.createEncoderByType("video/avc");
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            muxer = new MediaMuxer(destination.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            codec.start(); codecStarted = true;
            frame = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(frame);
            int[] pixels = new int[WIDTH * HEIGHT];
            for (int index = 0; index < FRAMES; index++) {
                render(canvas, shot, index);
                frame.getPixels(pixels, 0, WIDTH, 0, 0, WIDTH, HEIGHT);
                int input = awaitInput(codec, muxer, state);
                Image image = codec.getInputImage(input);
                if (image == null) throw new IllegalStateException("Encoder does not expose flexible YUV input.");
                fillYuv(image, pixels);
                codec.queueInputBuffer(input, 0, WIDTH * HEIGHT * 3 / 2,
                        index * 1_000_000L / FPS, 0);
                drain(codec, muxer, state, false);
            }
            int end = awaitInput(codec, muxer, state);
            codec.queueInputBuffer(end, 0, 0, FRAMES * 1_000_000L / FPS, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            drain(codec, muxer, state, true);
        } finally {
            if (frame != null) frame.recycle();
            if (codec != null) { if (codecStarted) try { codec.stop(); } catch (Exception ignored) {} codec.release(); }
            if (muxer != null) {
                try { if (state.started) muxer.stop(); } finally { muxer.release(); }
            }
        }
    }

    private static int awaitInput(MediaCodec codec, MediaMuxer muxer, MuxState state) throws Exception {
        long deadline = System.nanoTime() + 10_000_000_000L;
        while (System.nanoTime() < deadline) {
            int index = codec.dequeueInputBuffer(20_000);
            if (index >= 0) return index;
            drain(codec, muxer, state, false);
        }
        throw new IllegalStateException("Synthetic encoder input timed out.");
    }

    private static void drain(MediaCodec codec, MediaMuxer muxer, MuxState state, boolean waitForEnd) throws Exception {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        long deadline = System.nanoTime() + 20_000_000_000L;
        while (System.nanoTime() < deadline) {
            int output = codec.dequeueOutputBuffer(info, waitForEnd ? 20_000 : 0);
            if (output == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!waitForEnd) return;
            } else if (output == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (state.started) throw new IllegalStateException("Encoder format changed twice.");
                state.track = muxer.addTrack(codec.getOutputFormat()); muxer.start(); state.started = true;
            } else if (output >= 0) {
                ByteBuffer buffer = codec.getOutputBuffer(output);
                if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) info.size = 0;
                if (info.size > 0) {
                    if (!state.started || buffer == null) throw new IllegalStateException("Encoder output unavailable.");
                    buffer.position(info.offset); buffer.limit(info.offset + info.size);
                    muxer.writeSampleData(state.track, buffer, info);
                }
                boolean ended = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                codec.releaseOutputBuffer(output, false);
                if (ended) return;
            }
        }
        throw new IllegalStateException("Synthetic encoder output timed out.");
    }

    private static void fillYuv(Image image, int[] rgb) {
        Image.Plane[] planes = image.getPlanes();
        if (planes.length < 3) throw new IllegalStateException("YUV input needs three planes.");
        ByteBuffer y = planes[0].getBuffer(), u = planes[1].getBuffer(), v = planes[2].getBuffer();
        int yBase = y.position(), uBase = u.position(), vBase = v.position();
        for (int row = 0; row < HEIGHT; row++) {
            for (int col = 0; col < WIDTH; col++) {
                int color = rgb[row * WIDTH + col];
                int red = Color.red(color), green = Color.green(color), blue = Color.blue(color);
                int yy = ((66 * red + 129 * green + 25 * blue + 128) >> 8) + 16;
                y.put(yBase + row * planes[0].getRowStride() + col * planes[0].getPixelStride(), (byte) yy);
                if ((row & 1) == 0 && (col & 1) == 0) {
                    int uu = ((-38 * red - 74 * green + 112 * blue + 128) >> 8) + 128;
                    int vv = ((112 * red - 94 * green - 18 * blue + 128) >> 8) + 128;
                    u.put(uBase + row / 2 * planes[1].getRowStride() + col / 2 * planes[1].getPixelStride(), (byte) uu);
                    v.put(vBase + row / 2 * planes[2].getRowStride() + col / 2 * planes[2].getPixelStride(), (byte) vv);
                }
            }
        }
    }

    private static void render(Canvas canvas, int shot, int frame) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        int[] backgrounds = { 0xFF151D32, 0xFF252032, 0xFF13302C };
        int[] accents = { 0xFFB9A3FF, 0xFFFFB48C, 0xFFBFFD7D };
        canvas.drawColor(backgrounds[shot]);
        p.setColor(accents[shot]);
        float phase = frame / (float) FRAMES;
        canvas.drawCircle(180 + (float) Math.sin(phase * Math.PI * 2) * 70, 260, 90, p);
        p.setColor(0xFF0F1420); canvas.drawRoundRect(95, 195, 265, 355, 28, 28, p);
        p.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
        p.setColor(accents[shot]); p.setTextSize(90); canvas.drawText("0" + (shot + 1), 115, 300, p);
        p.setColor(Color.WHITE); p.setTextSize(24); canvas.drawText("MINI FILM", 26, 57, p);
        p.setTextSize(28); canvas.drawText(LABELS[shot], 26, 429, p);
        p.setTextSize(14); p.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        canvas.drawText("SYNTHETIC DEMO · NO CAMERA", 26, 467, p);
        canvas.drawText("Generated entirely on this phone", 26, 493, p);
        p.setColor(0xFF394155); canvas.drawRoundRect(26, 544, 334, 552, 4, 4, p);
        p.setColor(accents[shot]); canvas.drawRoundRect(26, 544, 26 + 308 * phase, 552, 4, 4, p);
        p.setColor(Color.WHITE); p.setTextSize(13);
        canvas.drawText(String.format(java.util.Locale.US, "FRAME %02d / 72", frame + 1), 26, 589, p);
    }

    private static final class MuxState { int track = -1; boolean started; }
}
