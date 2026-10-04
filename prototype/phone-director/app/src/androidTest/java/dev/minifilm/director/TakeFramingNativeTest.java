package dev.minifilm.director;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.util.AtomicFile;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Collections;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Actual bounded video decode + bundled pose on labelled synthetic/public fixtures only.
 * Requested seeks are not exact decoded PTS. No camera, microphone, Activity, playback,
 * VLM/LLM, downloaded weights, temporal coverage, framing taste or benefit claim.
 * Cancellation tests public callback/resource drain; they do not measure inference abort latency. */
@RunWith(AndroidJUnit4.class)
public final class TakeFramingNativeTest {
    private static final String PUBLIC_IMAGE_SHA = "012dff1fb8123ff9d29efb3b7aaef0a85a3cd637168b6051fd4b2c2e393dbe21";
    private static final String PUBLIC_VIDEO_SHA = "2f6415feb62b7d1bd51f377f6557de191b0e68262715514a7631663e4a173500";
    private static final String ROTATED_VIDEO_SHA = "6189a8f814fa114170d29b33bbe28b0a585882f4bba774a1f9e452e821b51e1c";
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final List<Original> originals = new ArrayList<>();
    private File flat, publicVideo;
    private Map<String, ?> preferences;
    private Map<String, String> models;

    @Before public void requireLabelledFixturesAndDeniedCapture() throws Exception {
        denied(); noInternet(); preferences = new HashMap<>(context.getSharedPreferences("shoot", 0).getAll());
        models = modelMetadata();
        flat = new File(context.getFilesDir(), "fixtures/jacket-speech.mp4");
        publicVideo = new File(context.getFilesDir(), "fixtures/take-framing-public.mp4");
        File image = new File(context.getFilesDir(), "public-pose-fixture.png");
        for (File file : Arrays.asList(flat, publicVideo, image)) { assertTrue("Root must install existing labelled test fixtures", file.isFile()); originals.add(new Original(file)); }
        assertEquals(PUBLIC_IMAGE_SHA, digest(image));
        assertEquals(PUBLIC_VIDEO_SHA, digest(publicVideo));
        assertTrue(Math.abs(duration(flat) - 5746) <= 200);
        assertTrue(Math.abs(duration(publicVideo) - 3000) <= 100);
    }
    @After public void noSourcePreferencesPermissionsOrWeightsChanged() throws Exception {
        for (Original source : originals) source.unchanged();
        if (preferences != null) assertEquals(preferences, context.getSharedPreferences("shoot", 0).getAll());
        if (models != null) assertEquals(models, modelMetadata());
        denied();
    }

    @Test(timeout = 60_000)
    public void flatSyntheticTrimHasThreeSourceRelativeUnclearSamplesAndOwnedBoundedThumbnails() throws Exception {
        Take take = new Take(Uri.fromFile(flat), "synthetic-framing-flat", "Synthetic flat fixture", "", duration(flat));
        take.inMs = 1000; take.outMs = 5000;
        TakeFramingReview.Selection selection = TakeFramingReview.Selection.capture(take);
        assertTrue(selection.matches(take));
        TakeFramingReview review = new TakeFramingReview(context);
        Outcome outcome = new Outcome();
        try {
            onMain(() -> {
                review.inspect(selection, outcome);
                take.uri = Uri.fromFile(publicVideo); take.durationMs = 3000; take.inMs = 500; take.outMs = 2500;
            });
            assertFalse("Caller mutation must invalidate matching but not replace the captured input", selection.matches(take));
            TakeFramingReview.Result result = outcome.await();
            assertSelection(selection, result, flat, duration(flat), 1000, 5000);
            assertMoments(result, new long[] { 2000, 3000, 4000 }, false);
            assertImmutableMomentList(result);
            closeAndAssertOwnedThumbnailsRecycled(result);
            Log.i("MiniFilmTakeFramingTest", "FLAT_PASS synthetic=true samples=3 sourceRelativeRequestedTimes=true selectionFrozen=true unclearOrReviewOnly=true thumbnailLongestMax=256 resultOwnership=true noVlm=true elapsedMs=" + outcome.elapsedMs);
        } finally { drainClosed(review); outcome.closeResult(); }
    }

    @Test(timeout = 60_000)
    public void publicPortraitInsideLandscapeGetsCenterCropBeforeConservativePoseHints() throws Exception {
        Take take = new Take(Uri.fromFile(publicVideo), "synthetic-public-framing", "Public image-derived fixture", "", duration(publicVideo));
        take.inMs = 500; take.outMs = 2500;
        TakeFramingReview.Selection selection = TakeFramingReview.Selection.capture(take);
        TakeFramingReview review = new TakeFramingReview(context); Outcome outcome = new Outcome();
        MediaMetadataRetriever reader = new MediaMetadataRetriever();
        try {
            reader.setDataSource(publicVideo.getAbsolutePath());
            assertEquals("640", reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
            assertEquals("360", reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
            onMain(() -> review.inspect(selection, outcome));
            TakeFramingReview.Result result = outcome.await();
            assertSelection(selection, result, publicVideo, duration(publicVideo), 500, 2500);
            assertMoments(result, new long[] { 1000, 1500, 2000 }, true);
            double maximumMeanError = 0;
            for (TakeFramingReview.Moment moment : result.moments) {
                Bitmap source = reader.getFrameAtTime(moment.requestedTimeMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST);
                assertNotNull(source);
                Bitmap expected = expectedCenterCrop(source, moment.thumbnail.getWidth(), moment.thumbnail.getHeight());
                Bitmap squeezedLandscape = Bitmap.createScaledBitmap(source, moment.thumbnail.getWidth(), moment.thumbnail.getHeight(), true);
                try {
                    double croppedError = meanRgbError(expected, moment.thumbnail);
                    maximumMeanError = Math.max(maximumMeanError, croppedError);
                    assertTrue("Thumbnail must agree with independently drawn center scale-to-fill", croppedError <= 12);
                    assertTrue("Thumbnail must not retain the landscape side padding as a squeezed full frame",
                            meanRgbError(squeezedLandscape, moment.thumbnail) > croppedError + 8);
                } finally { expected.recycle(); squeezedLandscape.recycle(); source.recycle(); }
            }
            closeAndAssertOwnedThumbnailsRecycled(result);
            Log.i("MiniFilmTakeFramingTest", "PUBLIC_CROP_PASS publicImageDerived=true samples=3 inputWidth=640 inputHeight=360 croppedThumbnailWidth=144 croppedThumbnailHeight=256 maxMeanRgbDifference="
                    + maximumMeanError + " centerCropMechanics=true labelsNeedCreatorReview=true noTemporalCoverageClaim=true elapsedMs=" + outcome.elapsedMs);
        } finally { reader.release(); drainClosed(review); outcome.closeResult(); }
    }

    @Test(timeout = 75_000)
    public void cancelledActualRequestDrainsBeforeReuseAndClosedReviewerPublishesNothing() throws Exception {
        Take take = new Take(Uri.fromFile(publicVideo), "synthetic-cancel-framing", "Public synthetic cancellation", "", duration(publicVideo));
        take.inMs = 500; take.outMs = 2500;
        TakeFramingReview.Selection selection = TakeFramingReview.Selection.capture(take);
        TakeFramingReview review = new TakeFramingReview(context);
        AtomicInteger staleTerminals = new AtomicInteger(); Outcome reuse = new Outcome();
        TakeFramingReview.Listener stale = new TakeFramingReview.Listener() {
            public void onResult(TakeFramingReview.Result result, long elapsedMs) { staleTerminals.incrementAndGet(); result.close(); }
            public void onError(String message) { staleTerminals.incrementAndGet(); }
        };
        try {
            onMain(() -> { review.inspect(selection, stale); review.cancel(); });
            awaitDrain(review);
            assertEquals(0, staleTerminals.get());
            onMain(() -> review.inspect(selection, reuse));
            TakeFramingReview.Result result = reuse.await();
            assertMoments(result, new long[] {1000,1500,2000}, true);
            assertEquals("No stale terminal may appear while the next real request completes", 0, staleTerminals.get());
            closeAndAssertOwnedThumbnailsRecycled(result);
            onMain(review::close); awaitDrain(review);
            onMain(() -> review.inspect(selection, stale));
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            assertFalse(review.isRunning()); assertEquals(0, staleTerminals.get());
            Log.i("MiniFilmTakeFramingTest", "CANCEL_REUSE_PASS publicImageDerived=true staleTerminals=0 realReuseSamples=3 closedPublishesNothing=true drainedBeforeReuse=true noInferenceAbortLatencyClaim=true");
        } finally { drainClosed(review); reuse.closeResult(); }
    }

    @Test(timeout = 180_000)
    public void actualCleanExportMatchesApproximateReviewCropOnNormalAndMetadataRotatedStaticPublicInputs() throws Exception {
        File rotated = new File(context.getFilesDir(), "fixtures/take-framing-public-rot90.mp4");
        assertTrue(rotated.isFile()); assertEquals(ROTATED_VIDEO_SHA, digest(rotated)); originals.add(new Original(rotated));
        File[] fixtures = {publicVideo, rotated};
        for (int fixtureIndex = 0; fixtureIndex < fixtures.length; fixtureIndex++) {
            File fixture = fixtures[fixtureIndex];
            MediaMetadataRetriever sourceMetadata = new MediaMetadataRetriever();
            try {
                sourceMetadata.setDataSource(fixture.getAbsolutePath());
                String rotation = sourceMetadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
                // This fixture's ffprobe display matrix is counterclockwise 90 degrees;
                // Android VIDEO_ROTATION reports the equivalent clockwise 270 degrees.
                assertEquals(fixtureIndex == 0 ? "0" : "270", rotation);
                assertEquals(3000, Long.parseLong(sourceMetadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)));
            } finally { sourceMetadata.release(); }
            String shotId = "synthetic-framing-parity-" + fixtureIndex;
            Take take = new Take(Uri.fromFile(fixture), shotId, "Synthetic crop parity", "", duration(fixture));
            take.inMs = 500; take.outMs = 2500;
            TakeFramingReview.Selection selection = TakeFramingReview.Selection.capture(take);
            TakeFramingReview review = new TakeFramingReview(context); Outcome sampled = new Outcome();
            ReelExporter exporter = new ReelExporter(context); Encoded encoded = new Encoded();
            try {
                onMain(() -> review.inspect(selection, sampled));
                TakeFramingReview.Result result = sampled.await();
                assertSelection(selection,result,fixture,3000,500,2500);
                assertEquals(3,result.moments.size());
                onMain(() -> exporter.export(Collections.singletonList(take), "", "Clean", encoded));
                assertTrue("Actual geometry export timed out",encoded.done.await(65,TimeUnit.SECONDS));
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                assertNull(encoded.error.get(),encoded.error.get());assertNotNull(encoded.video.get());assertNotNull(encoded.edit.get());assertTrue(encoded.main);
                JSONObject edit=new JSONObject(read(context.getContentResolver().openInputStream(encoded.edit.get())));
                detachOwnParityJournal(edit,encoded.video.get(),fixture,shotId);
                JSONArray cuts=edit.getJSONArray("cuts");assertEquals(1,cuts.length());
                assertEquals(2000,edit.getLong("durationMs"));assertEquals("",edit.getString("title"));assertEquals("Clean",edit.getString("look"));
                assertEquals("",cuts.getJSONObject(0).getString("caption"));assertEquals(0,cuts.getJSONObject(0).getJSONArray("subtitles").length());
                MediaMetadataRetriever output=new MediaMetadataRetriever();double maximumWhole=0,maximumCenter=0;long encodedDuration;
                try {
                    output.setDataSource(context,encoded.video.get());
                    assertEquals("720",output.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));assertEquals("1280",output.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
                    encodedDuration=Long.parseLong(output.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));assertTrue(Math.abs(encodedDuration-2000)<=200);
                    for(TakeFramingReview.Moment moment:result.moments){
                        Bitmap rendered=output.getScaledFrameAtTime((moment.requestedTimeMs-500)*1000,MediaMetadataRetriever.OPTION_CLOSEST,144,256);
                        assertNotNull("Real export must decode comparable sampled pixels",rendered);
                        Matrix turn=new Matrix();turn.postRotate(180);
                        Bitmap wrongOrientation=Bitmap.createBitmap(moment.thumbnail,0,0,moment.thumbnail.getWidth(),moment.thumbnail.getHeight(),turn,true);
                        try {
                            double whole=meanRgbError(moment.thumbnail,rendered),center=regionRgbError(moment.thumbnail,rendered,36,64,108,192);
                            maximumWhole=Math.max(maximumWhole,whole);maximumCenter=Math.max(maximumCenter,center);
                            assertTrue("Approximate reviewed crop must match compressed real Media3 output on this fixed fixture",whole<=14);
                            assertTrue("Center content must agree with actual export",center<=20);
                            assertTrue("Public source must have informative visible pixels",lumaVariance(moment.thumbnail)>100);
                            assertTrue("A flipped orientation must be measurably worse than the matching upright crop",meanRgbError(wrongOrientation,rendered)>whole+3);
                        }finally{rendered.recycle();wrongOrientation.recycle();}
                    }
                }finally{output.release();}
                Log.i("MiniFilmTakeFramingTest","EXPORT_CROP_PARITY_PASS publicImageDerived=true staticFixture=true metadataRotation="+(fixtureIndex==0?0:270)
                        +" samples=3 nominalDurationMs=2000 encodedDurationMs="+encodedDuration+" width=720 height=1280 maximumMeanRgbDifference="+maximumWhole
                        +" maximumCenterRgbDifference="+maximumCenter+" noHeadingOrCaption=true actualMedia3=true approximateCrop=true completedPairRetained=true ownJournalDetached=true");
            }finally{onMain(exporter::cancel);drainClosed(review);sampled.closeResult();}
        }
    }

    private void detachOwnParityJournal(JSONObject edit,Uri video,File fixture,String shotId)throws Exception{
        assertEquals("minifilm.edit.v1",edit.getString("schema"));assertTrue(edit.getBoolean("preEventResearch"));assertEquals("",edit.getString("title"));
        String id=edit.getString("exportId");assertTrue(id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));
        JSONArray cuts=edit.getJSONArray("cuts");assertEquals(1,cuts.length());JSONObject cut=cuts.getJSONObject(0);
        assertEquals(Uri.fromFile(fixture).toString(),cut.getString("sourceUri"));assertEquals(shotId,cut.getString("shotId"));assertEquals(500,cut.getLong("inMs"));assertEquals(2500,cut.getLong("outMs"));
        ExportRecovery.Journal journal=ExportRecovery.Journal.read(context,id);assertEquals("COMPLETE",journal.state);assertEquals(video,journal.videoUri);assertTrue(journal.expectedBytes>1000);assertTrue(journal.edit().isFile());
        new AtomicFile(new File(context.getFilesDir(),"export-journal/"+id+".json")).delete();
    }

    private static final class Encoded implements ReelExporter.Listener{
        final CountDownLatch done=new CountDownLatch(1);final AtomicReference<Uri> video=new AtomicReference<>(),edit=new AtomicReference<>();final AtomicReference<String> error=new AtomicReference<>();boolean main;
        public void onProgress(int percent){}
        public void onComplete(Uri savedVideo,Uri savedEdit){main=Looper.myLooper()==Looper.getMainLooper();video.set(savedVideo);edit.set(savedEdit);done.countDown();}
        public void onError(String message){error.set(message);done.countDown();}
    }
    private static String read(InputStream stream)throws Exception{assertNotNull(stream);try(InputStream input=stream;ByteArrayOutputStream output=new ByteArrayOutputStream()){byte[] block=new byte[4096];int n;while((n=input.read(block))!=-1)output.write(block,0,n);return output.toString("UTF-8");}}
    private static double regionRgbError(Bitmap first,Bitmap second,int left,int top,int right,int bottom){assertEquals(first.getWidth(),second.getWidth());assertEquals(first.getHeight(),second.getHeight());long sum=0;for(int y=top;y<bottom;y++)for(int x=left;x<right;x++){int a=first.getPixel(x,y),b=second.getPixel(x,y);for(int shift:new int[]{16,8,0})sum+=Math.abs(((a>>shift)&255)-((b>>shift)&255));}return sum/(double)((right-left)*(bottom-top)*3);}
    private static double lumaVariance(Bitmap frame){double sum=0,squares=0;int n=frame.getWidth()*frame.getHeight();for(int y=0;y<frame.getHeight();y++)for(int x=0;x<frame.getWidth();x++){int c=frame.getPixel(x,y);double l=(.2126*((c>>16)&255)+.7152*((c>>8)&255)+.0722*(c&255));sum+=l;squares+=l*l;}return squares/n-(sum/n)*(sum/n);}

    private static void assertSelection(TakeFramingReview.Selection expected, TakeFramingReview.Result result, File source, long duration, long in, long out) {
        assertSame(expected, result.selection); assertEquals(Uri.fromFile(source), result.selection.uri);
        assertEquals(duration, result.selection.durationMs); assertEquals(in, result.selection.inMs); assertEquals(out, result.selection.outMs);
    }
    private static void assertMoments(TakeFramingReview.Result result, long[] times, boolean publicPerson) {
        assertEquals(3, result.moments.size());
        for (int i = 0; i < times.length; i++) {
            TakeFramingReview.Moment moment = result.moments.get(i);
            assertEquals(times[i], moment.requestedTimeMs);
            assertTrue(moment.requestedTimeMs > result.selection.inMs && moment.requestedTimeMs < result.selection.outMs);
            assertNotNull(moment.thumbnail); assertFalse(moment.thumbnail.isRecycled());
            assertEquals(144, moment.thumbnail.getWidth()); assertEquals(256, moment.thumbnail.getHeight());
            assertTrue(Math.max(moment.thumbnail.getWidth(), moment.thumbnail.getHeight()) <= 256);
            assertNotNull(moment.reason); assertFalse(moment.reason.trim().isEmpty());
            if (!publicPerson) assertTrue("Flat synthetic scene cannot invent supported visible-body framing",
                    "empty or unclear".equals(moment.label) || "review needed".equals(moment.label));
            else {
                assertTrue("Public person may support a joint-based hint or conservatively abstain",
                        Arrays.asList("full-body", "waist-up", "head-and-shoulders", "review needed").contains(moment.label));
                if ("full-body".equals(moment.label)) assertTrue(moment.reason.contains("upper=3/3 hips=2/2 lower=4/4"));
                else if ("waist-up".equals(moment.label)) assertTrue(moment.reason.contains("upper=3/3 hips=2/2 lower=0/4"));
                else if ("head-and-shoulders".equals(moment.label)) assertTrue(moment.reason.contains("upper=3/3 hips=0/2 lower=0/4"));
            }
            Log.i("MiniFilmTakeFramingTest", "sampleIndex=" + i + " requestedTimeMs=" + moment.requestedTimeMs + " publicFixture=" + publicPerson
                    + " label=" + moment.label + " reason=" + moment.reason + " backendPreference=CPU poseLandmarkHeuristic=true");
        }
    }
    private static void assertImmutableMomentList(TakeFramingReview.Result result) { try { result.moments.add(null); fail("Mutable result list"); } catch (UnsupportedOperationException expected) { } }
    private static void closeAndAssertOwnedThumbnailsRecycled(TakeFramingReview.Result result) { List<Bitmap> bitmaps = new ArrayList<>(); for (TakeFramingReview.Moment moment : result.moments) bitmaps.add(moment.thumbnail); result.close(); result.close(); for (Bitmap bitmap : bitmaps) assertTrue(bitmap.isRecycled()); }
    // Independent full decoded frame → small crop oracle. It does not call production centerCrop.
    private static Bitmap expectedCenterCrop(Bitmap source, int width, int height) {
        Bitmap target = Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
        float scale = Math.max(width/(float)source.getWidth(), height/(float)source.getHeight());
        float w=source.getWidth()*scale,h=source.getHeight()*scale;
        new Canvas(target).drawBitmap(source,null,new RectF((width-w)/2,(height-h)/2,(width+w)/2,(height+h)/2),new Paint(Paint.FILTER_BITMAP_FLAG));
        return target;
    }
    private static double meanRgbError(Bitmap first, Bitmap second) { assertEquals(first.getWidth(),second.getWidth()); assertEquals(first.getHeight(),second.getHeight()); int size=first.getWidth()*first.getHeight(); int[] a=new int[size],b=new int[size]; first.getPixels(a,0,first.getWidth(),0,0,first.getWidth(),first.getHeight()); second.getPixels(b,0,second.getWidth(),0,0,second.getWidth(),second.getHeight()); long sum=0; for(int i=0;i<size;i++)for(int shift:new int[]{16,8,0})sum+=Math.abs(((a[i]>>shift)&255)-((b[i]>>shift)&255)); return sum/(double)(size*3); }
    private static void onMain(Runnable work) { InstrumentationRegistry.getInstrumentation().runOnMainSync(work); }
    private static void awaitDrain(TakeFramingReview review) throws Exception { long until=SystemClock.elapsedRealtime()+12_000; while(review.isRunning()&&SystemClock.elapsedRealtime()<until)Thread.sleep(20); InstrumentationRegistry.getInstrumentation().waitForIdleSync(); assertFalse("Actual cancelled worker/result must finish resource cleanup",review.isRunning()); }
    private static void drainClosed(TakeFramingReview review) throws Exception { onMain(review::close); awaitDrain(review); }
    private static final class Outcome implements TakeFramingReview.Listener {
        final CountDownLatch done=new CountDownLatch(1); final AtomicReference<TakeFramingReview.Result> result=new AtomicReference<>(); final AtomicReference<String> error=new AtomicReference<>(); boolean main;long elapsedMs;
        public void onResult(TakeFramingReview.Result ready,long elapsed) { main=Looper.myLooper()==Looper.getMainLooper();elapsedMs=elapsed;result.set(ready);done.countDown(); }
        public void onError(String message) { main=Looper.myLooper()==Looper.getMainLooper();error.set(message);done.countDown(); }
        TakeFramingReview.Result await() throws Exception { assertTrue("Actual framing result timed out",done.await(35,TimeUnit.SECONDS));assertNull(error.get(),error.get());assertTrue(main);assertNotNull(result.get());return result.get(); }
        void closeResult(){TakeFramingReview.Result ready=result.get();if(ready!=null)ready.close();}
    }
    private static long duration(File file) throws Exception { MediaMetadataRetriever reader=new MediaMetadataRetriever();try{reader.setDataSource(file.getAbsolutePath());return Long.parseLong(reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));}finally{reader.release();} }
    private static String digest(File file) throws Exception { MessageDigest digest=MessageDigest.getInstance("SHA-256");try(FileInputStream input=new FileInputStream(file)){byte[] block=new byte[8192];int n;while((n=input.read(block))!=-1)digest.update(block,0,n);}return ProjectPackager.hex(digest.digest()); }
    private static final class Original { final File file;final long bytes,mtime;final String hash; Original(File file)throws Exception{this.file=file;bytes=file.length();mtime=file.lastModified();hash=digest(file);}void unchanged()throws Exception{assertTrue(file.isFile());assertEquals(bytes,file.length());assertEquals(mtime,file.lastModified());assertEquals(hash,digest(file));} }
    private Map<String,String> modelMetadata(){Map<String,String> state=new HashMap<>();File root=context.getFilesDir();File core=new File(root,"director-model.gguf");if(core.exists())state.put("director-model.gguf",core.length()+":"+core.lastModified());collectModelMetadata(new File(root,"models"),"models",state);return state;}
    private static void collectModelMetadata(File file,String name,Map<String,String> state){if(!file.exists())return;if(file.isDirectory()){File[] children=file.listFiles();assertNotNull(children);for(File child:children)collectModelMetadata(child,name+"/"+child.getName(),state);}else state.put(name,file.length()+":"+file.lastModified());}
    private void denied(){assertEquals(PackageManager.PERMISSION_DENIED,context.checkSelfPermission(Manifest.permission.CAMERA));assertEquals(PackageManager.PERMISSION_DENIED,context.checkSelfPermission(Manifest.permission.RECORD_AUDIO));}
    private void noInternet()throws Exception{PackageInfo info=context.getPackageManager().getPackageInfo(context.getPackageName(),PackageManager.GET_PERMISSIONS);if(info.requestedPermissions!=null)for(String permission:info.requestedPermissions)assertNotEquals("No uploads/model fetching in this app",Manifest.permission.INTERNET,permission);}
}
