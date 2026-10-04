package dev.minifilm.director;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Looper;
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

/** Synthetic creator metadata through the actual edit serializer and portable ZIP.
 * Framing intent is not media analysis, framing quality, or creator-plan approval. */
@RunWith(AndroidJUnit4.class)
public final class ShotFramingExportTest {
    private static final String HERO="synthetic-framing-current-hero",CLOSING="synthetic-framing-current-closing";
    private static final String LABEL="Synthetic manual framing plan · metadata only";
    private final Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
    private File source;private long bytes,mtime,duration;private String hash;private Map<String,?> preferences;

    @Before public void preserveExistingLabelledSourceAndDeniedCapture()throws Exception{
        denied();preferences=new HashMap<>(context.getSharedPreferences("shoot",0).getAll());
        source=new File(context.getFilesDir(),"fixtures/jacket-speech.mp4");
        assertTrue("Existing labelled synthetic speech fixture is required",source.isFile());
        bytes=source.length();mtime=source.lastModified();hash=sha(new FileInputStream(source));assertEquals(59313,bytes);
        assertEquals("fb95e4027757071bc33f02562945f02510756a41dbfd31badc16eced398143f0",hash);
        MediaMetadataRetriever m=new MediaMetadataRetriever();try{m.setDataSource(source.getAbsolutePath());duration=Long.parseLong(m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));assertEquals(5746,duration);}finally{m.release();}
    }
    @After public void preserveOriginalAndPreferences()throws Exception{
        if(hash!=null){assertEquals(bytes,source.length());assertEquals(mtime,source.lastModified());assertEquals(hash,sha(new FileInputStream(source)));}
        if(preferences!=null)assertEquals(preferences,context.getSharedPreferences("shoot",0).getAll());denied();
    }

    @Test public void immutableTargetReachesActualEditSerializerWithoutChangingCutOrAssignmentFacts()throws Exception{
        ArrayList<Shot> plan=plan();Shot hero=plan.get(0),closing=plan.get(1);
        ShotPlanSnapshot frozen=ShotPlanSnapshot.capture(plan,LABEL);
        hero.framingTarget="object_detail";closing.framingTarget="manual";plan.clear();
        assertEquals("full_outfit",frozen.entries.get(0).framingTarget);assertEquals("scene_default",frozen.entries.get(1).framingTarget);
        JSONObject independentlyMutable=frozen.toJson();assertPlan(independentlyMutable);
        independentlyMutable.getJSONArray("shots").getJSONObject(0).put("framingTarget","manual");
        assertPlan(frozen.toJson());
        Take cut=take();cut.reviewedShotIds.add(HERO);cut.subtitles.add(new SubtitleCue(800,1200,"Synthetic words kept"));
        JSONObject document=ReelExporter.editDocument("synthetic-framing-serializer",Collections.singletonList(cut),
                "Synthetic framing document","Clean",Collections.emptyList(),frozen);
        assertEquals("minifilm.edit.v1",document.getString("schema"));assertPlan(document.getJSONObject("shotPlan"));
        JSONObject exported=document.getJSONArray("cuts").getJSONObject(0);
        assertEquals("synthetic-private-original-shot",exported.getString("shotId"));assertEquals(cut.uri.toString(),exported.getString("sourceUri"));
        assertEquals(HERO,exported.getJSONArray("reviewedShotIds").getString(0));assertEquals("creator-reviewed",exported.getString("reviewedShotMappingOrigin"));
        assertEquals(500,exported.getLong("inMs"));assertEquals(2500,exported.getLong("outMs"));assertEquals(0,exported.getLong("timelineStartMs"));
        JSONObject cue=exported.getJSONArray("subtitles").getJSONObject(0);assertEquals("Synthetic words kept",cue.getString("text"));
        assertEquals(300,cue.getLong("timelineStartMs"));assertEquals(700,cue.getLong("timelineEndMs"));
        assertFalse(document.getJSONObject("shotPlan").has("approved"));assertFalse(document.getJSONObject("shotPlan").has("reviewed"));
        Log.i("MiniFilmShotFramingExport","SERIALIZER_PASS synthetic=true immutableFraming=true independentlyMutableJson=true mappingsAndTimelineUnchanged=true approvalInferred=false");
    }

    @Test public void invalidMutableFramingTargetsRejectInsteadOfBecomingSceneDefault(){
        for(String target:Arrays.asList(null,"","FULL_OUTFIT"," full_outfit ","auto_best_pose")){
            Shot shot=new Shot("synthetic-framing-invalid","Hero","Stand still","My outfit",4000);
            shot.framingTarget=target;
            IllegalArgumentException error=assertThrows(IllegalArgumentException.class,()->ShotPlanSnapshot.capture(Collections.singletonList(shot),LABEL));
            assertTrue(error.getMessage().contains("framing target"));assertEquals(target,shot.framingTarget);
        }
        for(String target:Arrays.asList("scene_default","full_outfit","face_shoulders","object_detail","manual")){
            Shot shot=new Shot("synthetic-framing-valid","Hero","Stand still","My outfit",4000,target);
            assertEquals(target,ShotPlanSnapshot.capture(Collections.singletonList(shot),LABEL).entries.get(0).framingTarget);
        }
    }

    @Test(timeout=45_000) public void actualPortableZipFreezesTargetsAndNotesBeforeSourceRead()throws Exception{
        ArrayList<Shot> plan=plan();Shot hero=plan.get(0),closing=plan.get(1);
        ShotPlanSnapshot frozen=ShotPlanSnapshot.capture(plan,LABEL);Take cut=take();cut.reviewedShotIds.add(HERO);
        CountDownLatch opened=new CountDownLatch(1),release=new CountDownLatch(1),done=new CountDownLatch(1);
        AtomicReference<File> output=new AtomicReference<>();AtomicReference<String> error=new AtomicReference<>();AtomicBoolean mainCallback=new AtomicBoolean();
        ProjectPackager packager=new ProjectPackager(context,(uri,signal)->{
            assertEquals(Uri.fromFile(source),uri);opened.countDown();
            if(!release.await(10,TimeUnit.SECONDS))throw new java.io.IOException("Synthetic framing source gate timed out");
            return new ProjectPackager.Source(new FileInputStream(source),bytes,"video/mp4");
        },ProjectPackager.MAX_ORIGINAL_BYTES);
        try{
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->packager.export(Collections.singletonList(cut),
                    "Synthetic framing package","Clean",frozen,new ProjectPackager.Listener(){
                public void onProgress(long n){}
                public void onComplete(File file,long originals){mainCallback.set(Looper.myLooper()==Looper.getMainLooper());output.set(file);if(originals!=bytes)error.set("Original byte count changed");done.countDown();}
                public void onError(String message){error.set(message);done.countDown();}
            }));
            assertTrue(opened.await(10,TimeUnit.SECONDS));
            hero.framingTarget="face_shoulders";closing.framingTarget="manual";Collections.reverse(plan);
            cut.reviewedShotIds.clear();cut.reviewedShotIds.add(CLOSING);cut.inMs=1000;cut.caption="Later words";
            release.countDown();assertTrue(done.await(20,TimeUnit.SECONDS));assertNull(error.get(),error.get());assertNotNull(output.get());assertTrue(mainCallback.get());
            try(ZipFile zip=new ZipFile(output.get())){
                assertEquals(4,zip.size());JSONObject project=new JSONObject(read(zip.getInputStream(zip.getEntry("project.json"))));
                assertEquals("minifilm.portable-edit.v1",project.getString("schema"));assertPlan(project.getJSONObject("shotPlan"));
                JSONObject exported=project.getJSONArray("cuts").getJSONObject(0);
                assertEquals(500,exported.getLong("inMs"));assertEquals(2500,exported.getLong("outMs"));assertEquals(2000,project.getLong("durationMs"));
                assertEquals(HERO,exported.getJSONArray("reviewedShotIds").getString(0));assertEquals("creator-reviewed",exported.getString("reviewedShotMappingOrigin"));
                assertEquals("My jacket",exported.getString("caption"));assertFalse(exported.has("shotId"));assertFalse(exported.has("sourceUri"));
                String notes=read(zip.getInputStream(zip.getEntry("shoot-notes.txt")));
                assertTrue(notes.contains("Framing target: Full outfit (creator choice; review the framing)"));
                assertTrue(notes.contains("Framing target: Scene default (suggested) (scene-default suggestion; review the framing)"));
                assertTrue(notes.contains("Creator assignment: 1. Hero"));assertTrue(notes.contains("0.500–2.500s"));
                assertFalse(notes.contains("Face & shoulders"));assertFalse(notes.contains("Manual preview only"));assertFalse(notes.contains("Later words"));
                JSONObject media=project.getJSONArray("media").getJSONObject(0);assertEquals(hash,media.getString("sha256"));
                assertEquals(hash,sha(zip.getInputStream(zip.getEntry(media.getString("path")))));
                assertEquals(bytes,project.getLong("originalBytes"));
                for(String text:Arrays.asList(project.toString(),notes)){assertFalse(text.contains(source.getAbsolutePath()));assertFalse(text.contains("file://"));assertFalse(text.contains("synthetic-private-original-shot"));}
            }
            preserveOriginalAndPreferences();
            Log.i("MiniFilmShotFramingExport","ZIP_PASS synthetic=true planShots=2 frozenTargets=true explicitAndDefaultNotes=true actualOriginalByteZip=true originalUnchanged=true captureOrInference=false");
        }finally{
            release.countDown();InstrumentationRegistry.getInstrumentation().runOnMainSync(packager::close);
            File file=output.get();if(file!=null){assertEquals(context.getCacheDir().getCanonicalFile(),file.getCanonicalFile().getParentFile());assertFalse(java.nio.file.Files.isSymbolicLink(file.toPath()));assertTrue(file.getName().matches("minifilm-pack-[0-9]+\\.zip"));if(file.exists())assertTrue("Remove only callback-owned synthetic ZIP",file.delete());}
        }
    }

    private ArrayList<Shot> plan(){return new ArrayList<>(Arrays.asList(new Shot(HERO,"Hero","Stand facing forward","My outfit",4000,"full_outfit"),new Shot(CLOSING,"Closing","Face forward","Final pose",5000)));}
    private Take take(){Take take=new Take(Uri.fromFile(source),"synthetic-private-original-shot","Synthetic cut","My jacket",duration);take.inMs=500;take.outMs=2500;return take;}
    private static void assertPlan(JSONObject json)throws Exception{
        assertEquals("minifilm.shot-plan.v1",json.getString("schema"));assertEquals(LABEL,json.getString("sourceLabel"));JSONArray shots=json.getJSONArray("shots");assertEquals(2,shots.length());
        assertEquals(HERO,shots.getJSONObject(0).getString("id"));assertEquals(CLOSING,shots.getJSONObject(1).getString("id"));
        assertEquals(1,shots.getJSONObject(0).getInt("order"));assertEquals(2,shots.getJSONObject(1).getInt("order"));
        assertEquals("full_outfit",shots.getJSONObject(0).getString("framingTarget"));assertEquals("scene_default",shots.getJSONObject(1).getString("framingTarget"));
    }
    private void denied(){assertEquals(PackageManager.PERMISSION_DENIED,context.checkSelfPermission(Manifest.permission.CAMERA));assertEquals(PackageManager.PERMISSION_DENIED,context.checkSelfPermission(Manifest.permission.RECORD_AUDIO));}
    private static String read(InputStream input)throws Exception{try(InputStream stream=input;ByteArrayOutputStream bytes=new ByteArrayOutputStream()){byte[] buffer=new byte[8192];int n;while((n=stream.read(buffer))!=-1)bytes.write(buffer,0,n);return bytes.toString(StandardCharsets.UTF_8.name());}}
    private static String sha(InputStream input)throws Exception{MessageDigest hash=MessageDigest.getInstance("SHA-256");try(InputStream stream=input){byte[] buffer=new byte[8192];int n;while((n=stream.read(buffer))!=-1)hash.update(buffer,0,n);}StringBuilder out=new StringBuilder();for(byte b:hash.digest())out.append(String.format(java.util.Locale.US,"%02x",b&255));return out.toString();}
}
