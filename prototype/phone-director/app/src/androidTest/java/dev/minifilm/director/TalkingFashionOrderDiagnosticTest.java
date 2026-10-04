package dev.minifilm.director;

import static org.junit.Assert.*;
import android.Manifest;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import android.util.Log;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Pattern;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;

/** ONE raw native ordering diagnostic. Only exact title order and grammar root order change.
 * Raw JSON is retained before parsing/assertions; canonicalization reorders objects, never fields.
 * This repeated failed brief is a regression diagnostic, not fresh unseen quality evidence.
 */
@RunWith(AndroidJUnit4.class)
public final class TalkingFashionOrderDiagnosticTest {
    private static final String TAG="MiniFilmSpeechOrder";
    private static final String SHA="57d1997790d1744fba5b40a7317df71ea5e2acee28c47e78f0cce39c0703f8cf";
    private static final String BRIEF="Make a stationary talking-fashion reel while I wear my burgundy waistcoat. I want to describe my own layering choice. Stay in one marked spot: no walking or steps. Small body turns are fine. No new props. The phone is already mounted.";
    private static final String OLD_ORDER="Exact title order: Hero pose, Movement, Detail, Side pose, Closing.";
    private static final String NEW_ORDER="Exact title order: Closing, Hero pose, Movement, Detail, Side pose.";
    private static final String OLD_ROOT="root ::= \"[\" ws shot0 \",\" ws shot1 \",\" ws shot2 \",\" ws shot3 \",\" ws shot4 \"]\" ws\n";
    private static final String NEW_ROOT="root ::= \"[\" ws shot4 \",\" ws shot0 \",\" ws shot1 \",\" ws shot2 \",\" ws shot3 \"]\" ws\n";

    @Test(timeout=180_000) public void oneRawClosingFirstDrawMustNameLayeringBeforeCanonicalOrderValidation()throws Exception{
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();assertGates(context);
        assertFalse(LocalModelLease.isHeld());File model=new File(context.getFilesDir(),"director-model.gguf");
        assertEquals(563036064L,model.length());assertEquals(SHA,hash(model));long modified=model.lastModified();
        Map<String,?> preferences=new HashMap<>(context.getSharedPreferences("shoot",0).getAll());
        String[] roles=(String[])constant("FASHION_ROLES");
        Object intent=call("fashionIntent",new Class<?>[]{String.class,String[].class},BRIEF,roles);
        Class<?> intentType=intent.getClass();
        String[][] actions=(String[][])call("actionsForIntent",new Class<?>[]{String[].class,intentType},roles,intent);
        String originalPrompt=(String)call("buildPrompt",new Class<?>[]{String.class,String.class,String[].class,String[][].class,boolean.class,boolean.class,boolean.class,intentType},BRIEF,"Fashion",roles,actions,false,true,true,intent);
        String originalGrammar=(String)call("grammarForRoles",new Class<?>[]{String[].class,String[][].class,boolean.class,intentType},roles,actions,true,intent);
        assertTrue(originalPrompt.contains(OLD_ORDER));assertEquals(originalPrompt.indexOf(OLD_ORDER),originalPrompt.lastIndexOf(OLD_ORDER));
        assertTrue(originalGrammar.startsWith(OLD_ROOT));
        String prompt=originalPrompt.replace(OLD_ORDER,NEW_ORDER);
        String grammar=NEW_ROOT+originalGrammar.substring(OLD_ROOT.length());
        assertEquals("No prompt changes beyond declared order",originalPrompt,prompt.replace(NEW_ORDER,OLD_ORDER));
        assertEquals("No schema changes beyond root order",originalGrammar,OLD_ROOT+grammar.substring(NEW_ROOT.length()));
        File dir=new File(context.getFilesDir(),"test-evidence");assertTrue(dir.isDirectory()||dir.mkdirs());
        File evidenceFile=File.createTempFile("talking-fashion-order-",".json",dir);
        JSONObject record=new JSONObject().put("syntheticOnly",true).put("brief",BRIEF).put("backend","local CPU")
                .put("modelSha256",SHA).put("modelBytes",563036064L).put("maxOutputTokens",580)
                .put("contextTokens",1536).put("cpuThreads",4).put("emptyThinkAdapter",true)
                .put("changedOnlyTitleAndRootOrder",true).put("generationOrder",NEW_ORDER)
                .put("nativeCalls",0).put("retry",false).put("fallback",false).put("fieldRewrite",false)
                .put("creatorOverride",false).put("generalQualityClaim",false).put("status","pending");
        write(evidenceFile,record);LocalModelLease.Token lease=LocalModelLease.acquire("closing-first-diagnostic",()->Thread.currentThread().isInterrupted(),5000);
        long handle=0;boolean freed=false;long began=SystemClock.elapsedRealtime();
        try{
            handle=((Number)call("nativeLoad",new Class<?>[]{String.class},model.getAbsolutePath())).longValue();assertNotEquals(0,handle);
            long generateBegan=SystemClock.elapsedRealtime();record.put("nativeCalls",1);write(evidenceFile,record);
            byte[] bytes=(byte[])call("nativeGenerate",new Class<?>[]{long.class,byte[].class,byte[].class,int.class},handle,
                    prompt.getBytes(StandardCharsets.UTF_8),grammar.getBytes(StandardCharsets.UTF_8),580);
            String raw=new String(bytes,StandardCharsets.UTF_8);
            record.put("rawNativeJSON",raw).put("nativeGenerationMs",SystemClock.elapsedRealtime()-generateBegan)
                    .put("loadAndGenerationMs",SystemClock.elapsedRealtime()-began).put("status","raw_native_retained");
            // Untouched native bytes are saved before any JSON, role or speech assertion.
            write(evidenceFile,record);logComplete(record.toString());
            JSONArray generated=new JSONArray(raw);assertEquals(5,generated.length());
            String rawObjectsBefore=generated.toString();JSONObject closing=generated.getJSONObject(0);
            boolean rawFirstClosing="Closing".equals(closing.getString("title"));
            boolean ownWords=closing.getString("instruction").startsWith("Tell in your own words ");
            boolean actualTopic=Pattern.compile("\\blayering\\b",Pattern.CASE_INSENSITIVE).matcher(closing.getString("instruction")).find();
            long duration=closing.getLong("duration_ms");
            record.put("rawFirstClosing",rawFirstClosing).put("rawOwnWords",ownWords).put("rawLayeringTopicNamed",actualTopic)
                    .put("rawSpeechDurationValid",duration>=6000&&duration<=8000);write(evidenceFile,record);
            JSONArray canonical=new JSONArray();Set<String> seen=new HashSet<>();
            for(String role:roles){JSONObject match=null;for(int i=0;i<generated.length();i++){
                JSONObject candidate=generated.getJSONObject(i);if(role.equals(candidate.getString("title"))){assertNull("No duplicate role objects",match);match=candidate;}}
                assertNotNull("Missing canonical role",match);assertTrue(seen.add(match.getString("title")));canonical.put(match);}
            @SuppressWarnings("unchecked") List<Shot> parsed=(List<Shot>)call("parse",new Class<?>[]{String.class,String[].class,String[][].class,boolean.class,intentType},canonical.toString(),roles,actions,true,intent);
            call("validatePlanFashionIntent",new Class<?>[]{List.class,intentType},parsed,intent);
            assertEquals("Object fields must be untouched by reordering/parsing",rawObjectsBefore,generated.toString());
            assertEquals(5,parsed.size());for(int i=0;i<roles.length;i++)assertEquals(roles[i],parsed.get(i).title);
            record.put("canonicalProductionParsePassed",true).put("canonicalObjects",canonical)
                    .put("status",actualTopic?"topic_named_manual_quality_review_pending":"topic_not_named");
            write(evidenceFile,record);logComplete(record.toString());
            assertTrue("Raw native first role must be Closing",rawFirstClosing);
            assertTrue("Raw native speech cue must request creator's own words",ownWords);
            assertTrue("Raw native speech duration remains a 6-8 second draft",duration>=6000&&duration<=8000);
            assertTrue("Ordering diagnostic failed to preserve actual supplied layering topic; raw output retained",actualTopic);
        }catch(Exception|Error failure){record.put("failureClass",failure.getClass().getSimpleName()).put("status","diagnostic_failed")
                .put("wallElapsedMs",SystemClock.elapsedRealtime()-began);write(evidenceFile,record);Log.w(TAG,"synthetic_only=true diagnostic_failed type="+failure.getClass().getSimpleName());throw failure;
        }finally{
            // JNI is synchronous; only release its full-lifetime lease after nativeFree returns.
            if(handle!=0){call("nativeFree",new Class<?>[]{long.class},handle);handle=0;freed=true;}else freed=true;
            if(freed)lease.close();
            boolean prefs=preferences.equals(context.getSharedPreferences("shoot",0).getAll());
            boolean weights=model.length()==563036064L&&model.lastModified()==modified&&SHA.equals(hash(model));
            record.put("nativeFreeReturned",freed).put("nativeHandleZeroAfterFree",handle==0).put("leaseReleased",!LocalModelLease.isHeld())
                    .put("preferencesPreserved",prefs).put("modelHashSizeMtimePreserved",weights);write(evidenceFile,record);
            Log.i(TAG,"synthetic_only=true final_cleanup nativeFree="+freed+" leaseReleased="+!LocalModelLease.isHeld()+" evidence_file="+evidenceFile.getName());
            assertTrue("Creator preferences must remain unchanged",prefs);assertTrue("Default weights must remain unchanged",weights);assertFalse(LocalModelLease.isHeld());assertGates(context);
        }
    }
    private static void logComplete(String value){int parts=(value.length()+767)/768;for(int from=0,part=1;from<value.length();from+=768,part++)Log.i(TAG,"synthetic_only=true raw_record_part="+part+"/"+parts+" "+value.substring(from,Math.min(from+768,value.length())));}
    private static Object constant(String name)throws Exception{Field f=LocalPlanner.class.getDeclaredField(name);f.setAccessible(true);return f.get(null);}
    private static Object call(String name,Class<?>[] types,Object... args)throws Exception{Method m=LocalPlanner.class.getDeclaredMethod(name,types);m.setAccessible(true);try{return m.invoke(null,args);}catch(InvocationTargetException e){if(e.getCause() instanceof Error)throw (Error)e.getCause();if(e.getCause() instanceof Exception)throw (Exception)e.getCause();throw e;}}
    private static void write(File file,JSONObject record)throws Exception{try(FileOutputStream stream=new FileOutputStream(file,false)){stream.write(record.toString(2).getBytes(StandardCharsets.UTF_8));stream.flush();}}
    private static String hash(File file)throws Exception{MessageDigest digest=MessageDigest.getInstance("SHA-256");try(InputStream stream=new FileInputStream(file)){byte[] buffer=new byte[65536];int n;while((n=stream.read(buffer))!=-1)digest.update(buffer,0,n);}StringBuilder out=new StringBuilder();for(byte b:digest.digest())out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();}
    private static void assertGates(Context c)throws Exception{assertEquals(PackageManager.PERMISSION_DENIED,c.checkSelfPermission(Manifest.permission.CAMERA));assertEquals(PackageManager.PERMISSION_DENIED,c.checkSelfPermission(Manifest.permission.RECORD_AUDIO));PackageInfo info=c.getPackageManager().getPackageInfo(c.getPackageName(),PackageManager.GET_PERMISSIONS);if(info.requestedPermissions!=null)for(String p:info.requestedPermissions)assertNotEquals(Manifest.permission.INTERNET,p);}
}
