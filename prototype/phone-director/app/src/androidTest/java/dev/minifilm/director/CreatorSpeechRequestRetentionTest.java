package dev.minifilm.director;

import static org.junit.Assert.*;
import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Literal author retention/merge boundaries only. No model access or native generation. */
@RunWith(AndroidJUnit4.class)
public final class CreatorSpeechRequestRetentionTest {
    private static final String PREFIX="Make a stationary talking-fashion reel. Stay planted. ";

    @Test public void supportedFirstPersonRequestsPreserveOneLiteralClauseAndResearchModeDoesNotMerge() throws Exception {
        String[] prefixes={"I want to ","I plan to ","I would like to ","I will "};
        String[] verbs={"describe","explain","tell","say","share","talk","speak"};
        for(String prefix:prefixes)for(String verb:verbs){
            String exact=verb+" my own layering choice.";
            assertEquals(Character.toUpperCase(exact.charAt(0))+exact.substring(1),request(PREFIX+prefix+exact+" No new props.",true));
        }
        assertEquals("Describe   my own choice.",request(PREFIX+"I would  like to describe   my own choice.",true));
        assertEquals("Explain my  own styling choice!",request(PREFIX+"I will explain my  own styling choice! No new props.",true));
        assertEquals("Describe my own layering choice",request(PREFIX+"I want to describe my own layering choice",true));
        assertNull(request(PREFIX+"I want to describe my own layering choice.",false));
        for(String brief:new String[]{"A silent fashion reel. I want to describe my outfit.",
                "I do not want to talk. Wear my outfit.","Make a talking-fashion reel. I want to whisper about my outfit.",
                "Make a talking-fashion reel about my outfit.",
                "Wear my outfit. Creator-reviewed reference speech: I want to describe my outfit.",
                "Wear my outfit. Reference notes (reviewed when visual AI is used; approximate): I will explain my outfit.",
                "Wear my outfit. Creator-reviewed reference moments: 0:01 I will explain my outfit."})assertNull(request(brief,true));
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        LocalPlanner ordinary=new LocalPlanner(context),research=new LocalPlanner(context,false);
        try{assertTrue((Boolean)field(ordinary,"retainCreatorSpeech"));assertFalse((Boolean)field(research,"retainCreatorSpeech"));
            assertEquals(0L,field(ordinary,"handle"));assertEquals(0L,field(research,"handle"));}
        finally{ordinary.close();research.close();assertTrue(((ExecutorService)field(ordinary,"worker")).awaitTermination(2,TimeUnit.SECONDS));assertTrue(((ExecutorService)field(research,"worker")).awaitTermination(2,TimeUnit.SECONDS));}
        assertFalse(LocalModelLease.isHeld());
    }

    @Test public void ambiguousOverlongAndConflictingRecognizedRequestsRejectWithoutShortening() throws Exception {
        for(String bad:new String[]{
                "I want to describe my outfit. I will explain my choice.",
                "I want to describe.",
                "I want to describe "+repeat("very ",24)+"long topic.",
                "I want to describe my outfit and explain my choice.",
                "I want to describe my outfit; share my choice.",
                "I want to describe my\nlayering choice.",
                "I want to describe my <|topic|>.",
                "I want to describe my {topic}.",
                "I want to describe my \"topic\".",
                "I want to describe Dr.Layering.",
                "I want to describe my choice while walking.",
                "I want to describe my choice while holding the camera."}){
            String original=PREFIX+bad;reject(()->request(original,true));assertEquals(PREFIX+bad,original);
        }
        // Recognition uses the original bounded creator context, not a clipped-away request.
        reject(()->request(PREFIX+repeat("outfit ",75)+"I want to describe my choice.",true));
        assertFalse(LocalModelLease.isHeld());
    }

    @Test public void namedClosingWinsAndOnlyAuthorInstructionCaptionChangeAfterActualDraftBoundary() throws Exception {
        String named="Say one sentence about my own styling choice.";
        String brief=PREFIX+"I want to describe my own layering choice. Creator-reviewed reference moments: 0:01 Closing: "+named;
        assertNull(request(brief,true)); // Native caption remains native when a named reviewed instruction wins.
        List<Shot> nativePlan=plan();Shot generatedClosing=nativePlan.get(4);
        @SuppressWarnings("unchecked") List<Shot> unchanged=(List<Shot>)call("applyCreatorSpeechRequest",new Class<?>[]{List.class,String.class},nativePlan,null);
        assertSame(nativePlan,unchanged);
        String author="Describe my own layering choice.";
        @SuppressWarnings("unchecked") List<Shot> retained=(List<Shot>)call("applyCreatorSpeechRequest",new Class<?>[]{List.class,String.class},nativePlan,author);
        assertNotSame(nativePlan,retained);assertEquals(5,retained.size());
        for(int i=0;i<4;i++)assertSame("All other generated objects/fields stay exact",nativePlan.get(i),retained.get(i));
        Shot closing=retained.get(4);assertNotSame(generatedClosing,closing);assertEquals(author,closing.instruction);assertEquals("My own words",closing.caption);
        assertEquals(generatedClosing.id,closing.id);assertEquals(generatedClosing.title,closing.title);assertEquals(generatedClosing.targetDurationMs,closing.targetDurationMs);assertEquals(generatedClosing.framingTarget,closing.framingTarget);
        assertEquals("Tell in your own words about the brief's topic.",generatedClosing.instruction);assertEquals("Native topic",generatedClosing.caption);
        Object intent=intent(PREFIX+"I want to describe my own layering choice.");validate(retained,intent);
        closing.instruction="Describe my choice while walking.";reject(()->validate(retained,intent));
        closing.instruction="Describe my choice while holding the camera.";reject(()->validate(retained,intent));
        closing.instruction=author;closing.targetDurationMs=4000;reject(()->validate(retained,intent));
        assertFalse(LocalModelLease.isHeld());
    }

    private static List<Shot> plan(){String[] roles={"Hero pose","Movement","Detail","Side pose","Closing"};String[] cues={"Stand wearing your outfit.","Turn slightly in place, keeping your feet planted.","Show one visible garment detail you choose.","Turn slightly sideways.","Tell in your own words about the brief's topic."};List<Shot> result=new ArrayList<>();for(int i=0;i<5;i++)result.add(new Shot("ai-shot-"+(i+1),roles[i],cues[i],i==4?"Native topic":"Native pose",i==4?8000:4000,i==4?FramingTarget.FACE_SHOULDERS:FramingTarget.SCENE_DEFAULT));return result;}
    private static String request(String brief,boolean enabled)throws Exception{String[] roles=roles();Object intent=intent(brief),reviewed=call("parseReviewedCues",new Class<?>[]{String.class,String[].class},brief,roles);call("validateReviewedFashionIntent",new Class<?>[]{reviewed.getClass(),intent.getClass()},reviewed,intent);return (String)call("creatorSpeechRequest",new Class<?>[]{String.class,intent.getClass(),reviewed.getClass(),boolean.class},brief,intent,reviewed,enabled);}
    private static Object intent(String brief)throws Exception{return call("fashionIntent",new Class<?>[]{String.class,String[].class},brief,roles());}
    private static String[] roles()throws Exception{return (String[])call("rolesForStyle",new Class<?>[]{String.class},"Fashion");}
    private static void validate(List<Shot> plan,Object intent)throws Exception{call("validatePlanFashionIntent",new Class<?>[]{List.class,intent.getClass()},plan,intent);}
    private static Object field(Object object,String name)throws Exception{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    private static Object call(String name,Class<?>[] types,Object... values)throws Exception{Method m=LocalPlanner.class.getDeclaredMethod(name,types);m.setAccessible(true);try{return m.invoke(null,values);}catch(InvocationTargetException e){if(e.getCause() instanceof Exception)throw (Exception)e.getCause();throw e;}}
    private static String repeat(String value,int n){StringBuilder b=new StringBuilder();for(int i=0;i<n;i++)b.append(value);return b.toString();}
    private interface Action{void run()throws Exception;}
    private static void reject(Action action)throws Exception{try{action.run();fail("Recognized conflicting/ambiguous request must reject");}catch(IllegalArgumentException expected){assertNotNull(expected.getMessage());assertFalse(expected.getMessage().isEmpty());}}
}
