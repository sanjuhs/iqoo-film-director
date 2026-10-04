package dev.minifilm.director;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Looper;
import android.util.AtomicFile;
import android.util.Log;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipFile;
import static org.junit.Assert.*;

/** Two explicitly chosen disjoint moments from one existing synthetic original.
 * Tests actual edit/ZIP/encoder behavior; caption pixels are appearance evidence, not OCR,
 * speech review, semantic editing quality, recording, or a benefit to a real creator. */
@RunWith(AndroidJUnit4.class)
public final class RepeatedCutExportTest {
    private static final String TITLE="Synthetic separate moments verification";
    private static final String SHOT="synthetic-separate-moments-source";
    private final Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
    private File source;private long bytes,mtime;private String hash;private Map<String,?> preferences;

    @Before public void existingSyntheticOriginalOnly()throws Exception{
        denied();preferences=new HashMap<>(context.getSharedPreferences("shoot",0).getAll());
        source=new File(context.getFilesDir(),"fixtures/jacket-speech.mp4");assertTrue(source.isFile());
        bytes=source.length();mtime=source.lastModified();hash=sha(new FileInputStream(source));
        assertEquals(59313,bytes);assertEquals("fb95e4027757071bc33f02562945f02510756a41dbfd31badc16eced398143f0",hash);
        MediaMetadataRetriever m=new MediaMetadataRetriever();try{m.setDataSource(source.getAbsolutePath());assertEquals("5746",m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));}finally{m.release();}
    }
    @After public void sourcePreferencesAndCapturePermissionsStayIntact()throws Exception{
        if(hash!=null){assertEquals(bytes,source.length());assertEquals(mtime,source.lastModified());assertEquals(hash,sha(new FileInputStream(source)));}
        if(preferences!=null)assertEquals(preferences,context.getSharedPreferences("shoot",0).getAll());denied();
    }

    @Test public void actualEditSerializerMapsDisjointRangesWithoutChangingOriginalWordTimes()throws Exception{
        List<Take> cuts=cuts();JSONObject edit=ReelExporter.editDocument("synthetic-repeated-serializer",cuts,TITLE,"Clean",Collections.emptyList());
        assertTimeline(edit);JSONArray json=edit.getJSONArray("cuts");
        for(int i=0;i<2;i++){assertEquals(Uri.fromFile(source).toString(),json.getJSONObject(i).getString("sourceUri"));assertEquals(SHOT,json.getJSONObject(i).getString("shotId"));}
        assertNotSame(cuts.get(0).subtitles,cuts.get(1).subtitles);assertNotSame(cuts.get(0).subtitles.get(0),cuts.get(1).subtitles.get(0));
    }

    @Test(timeout=45_000) public void realPortableZipCopiesOneOriginalAndKeepsTwoIndependentCutRanges()throws Exception{
        List<Take> cuts=cuts();ProjectPackager packager=new ProjectPackager(context);
        CountDownLatch done=new CountDownLatch(1);AtomicReference<File> output=new AtomicReference<>();AtomicReference<String> error=new AtomicReference<>();AtomicBoolean main=new AtomicBoolean();
        try{
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->packager.export(cuts,TITLE,"Clean",new ProjectPackager.Listener(){
                public void onProgress(long n){}
                public void onComplete(File file,long originals){output.set(file);main.set(Looper.myLooper()==Looper.getMainLooper());if(originals!=bytes)error.set("Repeated cuts duplicated original bytes");done.countDown();}
                public void onError(String message){error.set(message);done.countDown();}
            }));
            assertTrue(done.await(30,TimeUnit.SECONDS));assertNull(error.get(),error.get());assertNotNull(output.get());assertTrue(main.get());
            try(ZipFile zip=new ZipFile(output.get())){
                assertEquals(3,zip.size());JSONObject edit=new JSONObject(read(zip.getInputStream(zip.getEntry("project.json"))));assertTimeline(edit);
                JSONArray media=edit.getJSONArray("media");assertEquals(1,media.length());assertEquals(bytes,edit.getLong("originalBytes"));
                assertEquals(hash,media.getJSONObject(0).getString("sha256"));assertEquals(hash,sha(zip.getInputStream(zip.getEntry(media.getJSONObject(0).getString("path")))));
                JSONArray json=edit.getJSONArray("cuts");assertEquals(json.getJSONObject(0).getString("mediaPath"),json.getJSONObject(1).getString("mediaPath"));
                assertEquals(media.getJSONObject(0).getString("path"),json.getJSONObject(0).getString("mediaPath"));
                for(int i=0;i<2;i++){assertFalse(json.getJSONObject(i).has("sourceUri"));assertFalse(json.getJSONObject(i).has("shotId"));}
            }
            Log.i("MiniFilmRepeatedCut","ZIP_PASS synthetic=true selectedCuts=2 disjointRanges=true nominalDurationMs=2000 originalCopies=1 originalBytes=59313 independentSubtitles=true originalUnchanged=true");
        }finally{
            InstrumentationRegistry.getInstrumentation().runOnMainSync(packager::close);
            File file=output.get();if(file!=null){assertEquals(context.getCacheDir().getCanonicalFile(),file.getCanonicalFile().getParentFile());assertFalse(java.nio.file.Files.isSymbolicLink(file.toPath()));assertTrue(file.getName().matches("minifilm-pack-[0-9]+\\.zip"));if(file.exists())assertTrue(file.delete());}
        }
    }

    @Test(timeout=100_000) public void actualEncodedRepeatedSourceCutsPublishTimedCaptionsAndPreserveOriginal()throws Exception{
        List<Take> cuts=cuts();ReelExporter exporter=new ReelExporter(context);
        CountDownLatch done=new CountDownLatch(1);AtomicReference<Uri> video=new AtomicReference<>(),document=new AtomicReference<>();AtomicReference<String> error=new AtomicReference<>();AtomicBoolean main=new AtomicBoolean();
        try{
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->exporter.export(cuts,"","Clean",new ReelExporter.Listener(){
                public void onProgress(int n){}
                public void onComplete(Uri v,Uri e){video.set(v);document.set(e);main.set(Looper.myLooper()==Looper.getMainLooper());done.countDown();}
                public void onError(String message){error.set(message);done.countDown();}
            }));
            assertTrue(done.await(80,TimeUnit.SECONDS));assertNull(error.get(),error.get());assertNotNull(video.get());assertNotNull(document.get());assertTrue(main.get());
            JSONObject edit=new JSONObject(read(context.getContentResolver().openInputStream(document.get())));
            detachOwnCompletedJournal(edit,video.get());assertTimeline(edit);
            MediaMetadataRetriever metadata=new MediaMetadataRetriever();long encoded;int firstWords,lastWords,quiet;
            try{
                metadata.setDataSource(context,video.get());encoded=Long.parseLong(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
                assertTrue("Two one-second cuts must produce roughly two seconds",Math.abs(encoded-2000)<=200);
                assertEquals("720",metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));assertEquals("1280",metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
                firstWords=captionPixels(metadata,400);lastWords=captionPixels(metadata,1500);quiet=captionPixels(metadata,800);
                assertTrue("First trimmed source cue must appear",firstWords>quiet+200);
                assertTrue("Second source cue must appear after the first cut",lastWords>quiet+200);
            }finally{metadata.release();}
            sourcePreferencesAndCapturePermissionsStayIntact();
            Log.i("MiniFilmRepeatedCut",new JSONObject().put("case","encoded_repeated_source").put("synthetic",true).put("cuts",2)
                    .put("nominalDurationMs",2000).put("encodedDurationMs",encoded).put("width",720).put("height",1280)
                    .put("firstCaptionPixels",firstWords).put("lastCaptionPixels",lastWords).put("outsideCaptionPixels",quiet)
                    .put("originalUnchanged",true).put("completedPairRetained",true).put("ownJournalDetached",true).put("audioPlayed",false).toString());
        }finally{InstrumentationRegistry.getInstrumentation().runOnMainSync(exporter::cancel);}
    }

    private List<Take> cuts(){
        Take first=new Take(Uri.fromFile(source),SHOT,"Synthetic first moment","First fallback words",5746);
        first.inMs=500;first.outMs=1500;first.captionOrigin="creator-reviewed-offline-asr";first.reviewedShotIds.add("synthetic-current-hero");
        first.subtitles.add(new SubtitleCue(700,1100,"Intro saved words"));first.subtitles.add(new SubtitleCue(4300,4700,"Closing saved words"));
        Take second=TakeCutDraft.capture(first).create(4000,5000,"Synthetic closing moment","Closing fallback words");
        assertFalse(second.selected);assertEquals("creator-reviewed-offline-asr",second.captionOrigin);
        second.selected=true; // Explicit fixture choice equivalent to checking the new cut in Assemble.
        return Arrays.asList(first,second);
    }
    private static void assertTimeline(JSONObject edit)throws Exception{
        assertTrue(edit.getBoolean("preEventResearch"));assertEquals(2000,edit.getLong("durationMs"));JSONArray cuts=edit.getJSONArray("cuts");assertEquals(2,cuts.length());
        for(int i=0;i<2;i++){
            JSONObject cut=cuts.getJSONObject(i);assertEquals(i==0?500:4000,cut.getLong("inMs"));assertEquals(i==0?1500:5000,cut.getLong("outMs"));assertEquals(i*1000,cut.getLong("timelineStartMs"));
            assertEquals(i==0?"Synthetic first moment":"Synthetic closing moment",cut.getString("title"));
            assertEquals(i==0?"First fallback words":"Closing fallback words",cut.getString("caption"));assertEquals("creator-reviewed-offline-asr",cut.getString("captionOrigin"));
            assertEquals("synthetic-current-hero",cut.getJSONArray("reviewedShotIds").getString(0));assertEquals("creator-reviewed",cut.getString("reviewedShotMappingOrigin"));
            JSONArray words=cut.getJSONArray("subtitles");assertEquals(2,words.length());
            for(int j=0;j<2;j++){
                JSONObject cue=words.getJSONObject(j);assertEquals(j==0?700:4300,cue.getLong("sourceStartMs"));assertEquals(j==0?1100:4700,cue.getLong("sourceEndMs"));
                assertEquals(j==0?"Intro saved words":"Closing saved words",cue.getString("text"));assertEquals(i==j,cue.getBoolean("visibleInCut"));
                if(i==j){assertEquals(i==0?200:1300,cue.getLong("timelineStartMs"));assertEquals(i==0?600:1700,cue.getLong("timelineEndMs"));}
                else{assertFalse(cue.has("timelineStartMs"));assertFalse(cue.has("timelineEndMs"));}
            }
        }
    }
    private void detachOwnCompletedJournal(JSONObject edit,Uri video)throws Exception{
        assertEquals("minifilm.edit.v1",edit.getString("schema"));assertTrue(edit.getBoolean("preEventResearch"));assertEquals("",edit.getString("title"));
        String id=edit.getString("exportId");assertTrue(id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));
        JSONArray cuts=edit.getJSONArray("cuts");assertEquals(2,cuts.length());
        for(int i=0;i<2;i++){assertEquals(Uri.fromFile(source).toString(),cuts.getJSONObject(i).getString("sourceUri"));assertEquals(SHOT,cuts.getJSONObject(i).getString("shotId"));assertEquals(i==0?500:4000,cuts.getJSONObject(i).getLong("inMs"));assertEquals(i==0?1500:5000,cuts.getJSONObject(i).getLong("outMs"));}
        ExportRecovery.Journal journal=ExportRecovery.Journal.read(context,id);assertEquals("COMPLETE",journal.state);assertEquals(video,journal.videoUri);assertTrue(journal.expectedBytes>1000);assertTrue(journal.edit().isFile());
        new AtomicFile(new File(context.getFilesDir(),"export-journal/"+id+".json")).delete();
    }
    private static int captionPixels(MediaMetadataRetriever m,long ms){Bitmap frame=m.getFrameAtTime(ms*1000,MediaMetadataRetriever.OPTION_CLOSEST);assertNotNull(frame);try{assertEquals(720,frame.getWidth());assertEquals(1280,frame.getHeight());int white=0;for(int y=1000;y<1120;y++)for(int x=50;x<670;x++){int rgb=frame.getPixel(x,y);if((rgb>>16&255)>200&&(rgb>>8&255)>200&&(rgb&255)>200)white++;}return white;}finally{frame.recycle();}}
    private void denied(){assertEquals(PackageManager.PERMISSION_DENIED,context.checkSelfPermission(Manifest.permission.CAMERA));assertEquals(PackageManager.PERMISSION_DENIED,context.checkSelfPermission(Manifest.permission.RECORD_AUDIO));}
    private static String read(InputStream input)throws Exception{assertNotNull(input);try(InputStream stream=input;ByteArrayOutputStream bytes=new ByteArrayOutputStream()){byte[] buffer=new byte[8192];int n;while((n=stream.read(buffer))!=-1)bytes.write(buffer,0,n);return bytes.toString(StandardCharsets.UTF_8.name());}}
    private static String sha(InputStream input)throws Exception{MessageDigest digest=MessageDigest.getInstance("SHA-256");try(InputStream stream=input){byte[] buffer=new byte[8192];int n;while((n=stream.read(buffer))!=-1)digest.update(buffer,0,n);}StringBuilder out=new StringBuilder();for(byte b:digest.digest())out.append(String.format(java.util.Locale.US,"%02x",b&255));return out.toString();}
}
