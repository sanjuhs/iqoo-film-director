package dev.minifilm.director;

import static org.junit.Assert.*;
import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Metadata-only synthetic tests. Public export refusal must precede source decoding,
 * encoder/output creation or gallery mutation; no Activity, recording, model or playback. */
@RunWith(AndroidJUnit4.class)
public final class EditDocumentBudgetTest {
    private final Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();

    @Test(timeout=15_000)
    public void exactPrettyUtf8ByteBoundaryIncludesLimitAndRejectsOneMoreByte() throws Exception {
        JSONObject empty=new JSONObject().put("words","");
        int overhead=empty.toString(2).getBytes(StandardCharsets.UTF_8).length;
        JSONObject exact=new JSONObject().put("words",repeat("a",EditDocumentBudget.MAX_BYTES-overhead));
        byte[] accepted=EditDocumentBudget.encode(exact);
        assertEquals(EditDocumentBudget.MAX_BYTES,accepted.length);
        assertArrayEquals(exact.toString(2).getBytes(StandardCharsets.UTF_8),accepted);
        exact.put("words",exact.getString("words")+"a");reject(exact);

        // Compact bytes fit exactly, but the actual pretty document exceeds the budget.
        int compactOverhead=empty.toString().getBytes(StandardCharsets.UTF_8).length;
        JSONObject prettyOverflow=new JSONObject().put("words",repeat("a",EditDocumentBudget.MAX_BYTES-compactOverhead));
        assertEquals(EditDocumentBudget.MAX_BYTES,prettyOverflow.toString().getBytes(StandardCharsets.UTF_8).length);
        assertTrue(prettyOverflow.toString(2).getBytes(StandardCharsets.UTF_8).length>EditDocumentBudget.MAX_BYTES);
        reject(prettyOverflow);

        int count=(EditDocumentBudget.MAX_BYTES-overhead)/3+1;
        JSONObject multibyte=new JSONObject().put("words",repeat("\u20ac",count));
        assertTrue("Character counting would incorrectly accept these complete words",multibyte.toString(2).length()<EditDocumentBudget.MAX_BYTES);
        assertTrue(multibyte.toString(2).getBytes(StandardCharsets.UTF_8).length>EditDocumentBudget.MAX_BYTES);
        String original=multibyte.getString("words");reject(multibyte);assertEquals(original,multibyte.getString("words"));
        Log.i("MiniFilmEditBudgetTest","BOUNDARY_PASS exactBytes=1048576 oneByteOverRejected=true prettyBytes=true utf8Bytes=true noTruncation=true");
    }

    @Test(timeout=15_000)
    public void acceptedRealSerializerPreservesIdWordsSourceTimesAndIndependentFrozenBytes() throws Exception {
        Take take=new Take(Uri.parse("content://dev.minifilm.synthetic/edit-budget-source"),"synthetic-shot","Synthetic cut","",180000);
        take.inMs=500;take.outMs=2500;take.captionOrigin="creator-reviewed-offline-asr";
        take.subtitles.add(new SubtitleCue(800,1200,"\u20ac \u00e9 \u4f60\u597d complete words"));
        take.subtitles.add(new SubtitleCue(5000,5200,"Preserved words outside this cut"));
        String id="12345678-1234-1234-1234-123456789abc";
        JSONObject document=ReelExporter.editDocument(id,Collections.singletonList(take),"Synthetic budget verification","Clean",Collections.emptyList());
        byte[] bytes=EditDocumentBudget.encode(document);assertTrue(bytes.length<EditDocumentBudget.MAX_BYTES);
        assertArrayEquals(document.toString(2).getBytes(StandardCharsets.UTF_8),bytes);
        document.put("title","Later caller metadata");take.subtitles.get(0).text="Later caller words";
        JSONObject frozen=new JSONObject(new String(bytes,StandardCharsets.UTF_8));
        assertEquals(id,frozen.getString("exportId"));assertEquals("Synthetic budget verification",frozen.getString("title"));
        JSONObject cut=frozen.getJSONArray("cuts").getJSONObject(0);
        assertEquals(500,cut.getLong("inMs"));assertEquals(2500,cut.getLong("outMs"));assertEquals(2000,frozen.getLong("durationMs"));
        JSONObject first=cut.getJSONArray("subtitles").getJSONObject(0),outside=cut.getJSONArray("subtitles").getJSONObject(1);
        assertEquals("\u20ac \u00e9 \u4f60\u597d complete words",first.getString("text"));assertEquals(300,first.getLong("timelineStartMs"));assertEquals(700,first.getLong("timelineEndMs"));
        assertEquals("Preserved words outside this cut",outside.getString("text"));assertFalse(outside.getBoolean("visibleInCut"));
        Log.i("MiniFilmEditBudgetTest","ACCEPTED_PASS synthetic=true prettyUtf8=true realSerializer=true frozenBytes=true outsideWordsPreserved=true");
    }

    @Test(timeout=30_000)
    public void largeAllowedOffCutWordsRejectPublicExportBeforeAnySourceOrOutputWork() throws Exception {
        assertDenied();SharedPreferences preferences=context.getSharedPreferences("shoot",0);
        Map<String,?> prefs=new HashMap<>(preferences.getAll());Map<String,String> files=outputFiles();Map<Long,String> rows=gallery();
        for(String words:Arrays.asList(repeat("a",1000),repeat("\u20ac",1000))){
            // Unresolvable labelled synthetic URI. There is no source to decode or upload.
            Uri source=Uri.parse("content://dev.minifilm.synthetic/nonexistent-edit-budget-source");
            List<SubtitleCue> shared=new ArrayList<>();
            for(int i=0;i<500;i++)shared.add(new SubtitleCue(1000+i*300L,1100+i*300L,words));
            List<Take> takes=new ArrayList<>();
            for(int i=0;i<3;i++){Take take=new Take(source,"synthetic-budget-"+i,"Synthetic budget cut","",180000);take.inMs=0;take.outMs=250;take.subtitles=shared;takes.add(take);}
            JSONObject allowed=ReelExporter.editDocument("12345678-1234-1234-1234-123456789abc",takes,"","Clean",Collections.emptyList());
            assertTrue("Previously accepted metadata exceeds the recovery/Files budget",allowed.toString(2).getBytes(StandardCharsets.UTF_8).length>EditDocumentBudget.MAX_BYTES);
            AtomicReference<String> error=new AtomicReference<>();AtomicInteger terminals=new AtomicInteger(),progress=new AtomicInteger();AtomicBoolean mainCallback=new AtomicBoolean();
            ReelExporter exporter=new ReelExporter(context);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
                exporter.export(takes,"","Clean",new ReelExporter.Listener(){
                    public void onProgress(int value){progress.incrementAndGet();}
                    public void onComplete(Uri video,Uri edit){terminals.incrementAndGet();error.set("Unexpected publication");}
                    public void onError(String message){terminals.incrementAndGet();error.set(message);mainCallback.set(Looper.myLooper()==Looper.getMainLooper());}
                });
                assertEquals(EditDocumentBudget.TOO_LARGE_MESSAGE,error.get());assertEquals(1,terminals.get());assertEquals(0,progress.get());assertTrue(mainCallback.get());
                assertNull(field(exporter,"transformer"));assertNull(field(exporter,"activeJournal"));assertNull(field(exporter,"activeFile"));assertEquals(false,field(exporter,"busy"));
                exporter.cancel();assertEquals("No active callback remains after refusal",1,terminals.get());
            });
            assertEquals(3,takes.size());assertEquals(500,shared.size());
            for(Take take:takes){assertSame(shared,take.subtitles);assertEquals(source,take.uri);assertEquals(0,take.inMs);assertEquals(250,take.outMs);assertTrue(take.selected);}
            for(int i=0;i<shared.size();i++){assertEquals(words,shared.get(i).text);assertEquals(1000+i*300L,shared.get(i).startMs);assertEquals(1100+i*300L,shared.get(i).endMs);}
            assertEquals(files,outputFiles());assertEquals(rows,gallery());assertEquals(prefs,preferences.getAll());assertDenied();
        }
        Log.i("MiniFilmEditBudgetTest","PREFLIGHT_PASS synthetic=true cases=2 cutsPerCase=3 cuesPerCut=500 utf8=true sourceExists=false sourceDecoded=false encoderStarted=false newOutputs=0 galleryUnchanged=true originalWordsUnchanged=true preferencesUnchanged=true microphoneOpened=false");
    }

    private static void reject(JSONObject document) throws Exception {try{EditDocumentBudget.encode(document);fail("Oversized edit document accepted");}catch(IllegalArgumentException expected){assertEquals(EditDocumentBudget.TOO_LARGE_MESSAGE,expected.getMessage());}}
    private static String repeat(String text,int count){StringBuilder result=new StringBuilder(text.length()*count);for(int i=0;i<count;i++)result.append(text);return result.toString();}
    private static Object field(Object object,String name){try{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}catch(Exception e){throw new AssertionError(e);}}
    private Map<String,String> outputFiles(){Map<String,String> files=new TreeMap<>();snapshotFiles(new File(context.getFilesDir(),"export-journal"),"journal/",false,files);snapshotFiles(new File(context.getFilesDir(),"exports"),"edit/",false,files);snapshotFiles(context.getCacheDir(),"cache/",true,files);return files;}
    private static void snapshotFiles(File directory,String prefix,boolean reelsOnly,Map<String,String> result){File[] files=directory.listFiles();if(files==null){assertFalse("Existing output directory could not be inventoried",directory.exists());return;}for(File file:files)if(!reelsOnly||file.getName().startsWith("reel-"))result.put(prefix+file.getName(),file.length()+":"+file.lastModified());}
    private Map<Long,String> gallery(){Map<Long,String> rows=new TreeMap<>();Bundle query=new Bundle();query.putInt(MediaStore.QUERY_ARG_MATCH_PENDING,MediaStore.MATCH_INCLUDE);query.putString(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION,MediaStore.Video.Media.OWNER_PACKAGE_NAME+"=? AND "+MediaStore.Video.Media.RELATIVE_PATH+"=?");query.putStringArray(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,new String[]{context.getPackageName(),"Movies/MiniFilm/"});
        try(Cursor cursor=context.getContentResolver().query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,new String[]{MediaStore.Video.Media._ID,MediaStore.Video.Media.SIZE,MediaStore.Video.Media.IS_PENDING,MediaStore.Video.Media.DISPLAY_NAME},query,null)){assertNotNull("Owned gallery query unavailable",cursor);assertTrue("Bounded owned-gallery inventory",cursor.getCount()<=256);while(cursor.moveToNext())rows.put(cursor.getLong(0),cursor.getLong(1)+":"+cursor.getInt(2)+":"+cursor.getString(3));}return rows;
    }
    private void assertDenied(){assertEquals(PackageManager.PERMISSION_DENIED,context.checkSelfPermission(Manifest.permission.CAMERA));assertEquals(PackageManager.PERMISSION_DENIED,context.checkSelfPermission(Manifest.permission.RECORD_AUDIO));}
}
