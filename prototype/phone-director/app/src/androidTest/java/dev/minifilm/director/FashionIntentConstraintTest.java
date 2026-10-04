package dev.minifilm.director;

import static org.junit.Assert.*;
import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.lang.reflect.*;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Intent/prompt/grammar/parser regressions only; no model loading, recording or native inference. */
@RunWith(AndroidJUnit4.class)
public final class FashionIntentConstraintTest {
    private static final String BASELINE = "Make a stationary talking-fashion reel while I wear my burgundy waistcoat. "
            + "I want to describe my own layering choice. Stay in one marked spot: no walking or steps. "
            + "Small body turns are fine. No new props. The phone is already mounted.";

    @Test public void onlyExplicitPerformerIntentCountsAndSilentDeviceOrReferenceMentionsDoNotTriggerSpeech() throws Exception {
        String[] fashion = roles("Fashion");
        Object intent = intent(BASELINE, fashion); assertTrue(flag(intent,"stationary"));assertTrue(flag(intent,"speaking"));
        intent = intent("The phone is stationary; I will walk two small steps while wearing my outfit.",fashion);
        assertFalse("Mounted phone does not forbid performer walking",flag(intent,"stationary"));assertFalse(flag(intent,"speaking"));
        for(String silent:new String[]{"A silent fashion reel. I do not want to talk. Stay planted.",
                "I do not want to talk. Make a stationary fashion reel.","I want to describe my outfit, but make it silent."})
            assertFalse("Negated speech must not become a speaking cue",flag(intent(silent,fashion),"speaking"));
        intent=intent("Wear my outfit. Creator-reviewed reference moments: 0:01 a stationary talking-fashion reel",fashion);
        assertFalse(flag(intent,"stationary"));assertFalse(flag(intent,"speaking"));
        ReferenceSpeechContext.Reviewed speech=new ReferenceSpeechContext.Draft(Uri.parse("content://synthetic/reference"),
                java.util.Arrays.asList(new SubtitleCue(0,1000,"Synthetic words")),1).reviewed("I want to describe my outfit. Stay in one marked spot.");
        for(boolean board:new boolean[]{false,true}){
            String composed=ReferenceSpeechContext.composePlanBrief("A silent outfit reel; I will walk two steps.",
                    board?"0:01 stationary talking-fashion reel":"stationary talking-fashion reel",board,speech);
            intent=intent(composed,fashion);assertFalse("Reference speech/visual text is not performer intent",flag(intent,"stationary"));assertFalse(flag(intent,"speaking"));
        }
        intent=intent("I am not stationary. Small steps are allowed.",fashion);assertFalse(flag(intent,"stationary"));
        for(String style:new String[]{"Product reveal","Talking head","Introduction"}){
            intent=intent(BASELINE,roles(style));assertFalse(flag(intent,"stationary"));assertFalse(flag(intent,"speaking"));
        }
        assertFalse(LocalModelLease.isHeld());
    }

    @Test public void nativeSchemaRequiresInPlaceMovementOwnWordsSpeechAndSixToEightSecondClosing() throws Exception {
        String[] roles=roles("Fashion");Object intent=intent(BASELINE,roles);String[][] actions=actions(roles,intent);
        String grammar=(String)call("grammarForRoles",new Class<?>[]{String[].class,String[][].class,boolean.class,intent.getClass()},roles,actions,true,intent);
        assertTrue(grammar.contains("stationaryFields"));assertTrue(grammar.contains("speechFields"));
        assertTrue(grammar.contains("speechDuration ::= (\"6000\" | \"7000\" | \"8000\")"));
        assertTrue(grammar.contains("Tell in your own words "));assertFalse(grammar.contains("\"Walk \""));assertFalse(grammar.contains("\"Take \""));
        String prompt=(String)call("buildPrompt",new Class<?>[]{String.class,String.class,String[].class,String[][].class,boolean.class,boolean.class,boolean.class,intent.getClass()},BASELINE,"Fashion",roles,actions,false,true,true,intent);
        assertTrue(prompt.contains("THIS brief's actual speech topic"));assertTrue(prompt.contains("ONE short sentence"));
        assertTrue(prompt.contains("Do not supply their reason, opinion or benefit"));assertTrue(prompt.contains("6-8 seconds"));
        assertFalse("Active intent must not copy a generic complete-plan example",prompt.contains("[{\"title\""));
        assertFalse("Remove the observed sample-topic copying attractor",prompt.contains("your outfit choice"));
        assertTrue(prompt.contains("name the supplied speech subject"));
        String[] movement=(String[])constant("STATIONARY_MOVEMENT_INSTRUCTIONS"),captions=(String[])constant("STATIONARY_MOVEMENT_CAPTIONS");
        for(String direction:movement)for(String caption:captions){JSONArray draft=draft(roles);draft.getJSONObject(1).put("instruction",direction).put("caption",caption);
            List<Shot> parsed=parse(draft,roles,actions,intent);assertEquals(direction,parsed.get(1).instruction);assertEquals(caption,parsed.get(1).caption);assertEquals(8000,parsed.get(4).targetDurationMs);}
        for(String bad:new String[]{"Take two small steps.","Walk slowly in place."}){JSONArray draft=draft(roles);draft.getJSONObject(1).put("instruction",bad);reject(()->parse(draft,roles,actions,intent));}
        JSONArray defaultClosing=draft(roles);defaultClosing.getJSONObject(4).put("instruction","Look at the lens and smile.");reject(()->parse(defaultClosing,roles,actions,intent));
        JSONArray shortSpeech=draft(roles);shortSpeech.getJSONObject(4).put("duration_ms",4000);reject(()->parse(shortSpeech,roles,actions,intent));
        JSONArray otherRoleSteps=draft(roles);otherRoleSteps.getJSONObject(0).put("instruction","Stand and take two steps.");reject(()->parse(otherRoleSteps,roles,actions,intent));
        // These overloads preserve the original exact builder/schema path for other scene styles.
        for(String style:new String[]{"Fashion","Product reveal","Talking head","Introduction"}){
            String[] originalRoles=roles(style);Object none=intent("Wear my outfit.",originalRoles);String[][] originalActions=actions(originalRoles,none);
            String oldGrammar=(String)call("grammarForRoles",new Class<?>[]{String[].class,String[][].class,boolean.class},originalRoles,originalActions,false);
            String newGrammar=(String)call("grammarForRoles",new Class<?>[]{String[].class,String[][].class,boolean.class,none.getClass()},originalRoles,originalActions,false,none);
            assertEquals(oldGrammar,newGrammar);
            String oldPrompt=(String)call("buildPrompt",new Class<?>[]{String.class,String.class,String[].class,String[][].class,boolean.class,boolean.class,boolean.class},"Wear my outfit.",style,originalRoles,originalActions,false,false,true);
            String newPrompt=(String)call("buildPrompt",new Class<?>[]{String.class,String.class,String[].class,String[][].class,boolean.class,boolean.class,boolean.class,none.getClass()},"Wear my outfit.",style,originalRoles,originalActions,false,false,true,none);
            assertEquals(oldPrompt,newPrompt);
        }
        assertFalse(LocalModelLease.isHeld());
    }

    @Test public void reviewedConflictsRejectWithoutRewriteAndFinalMergedPlanStillValidates() throws Exception {
        String[] roles=roles("Fashion");Object intent=intent(BASELINE,roles);String[][] actions=actions(roles,intent);
        for(String roleCue:new String[]{"Movement: Take two small steps.","Hero: Stand and walk forward.","Closing: Wave at the lens."}){
            Object reviewed=call("parseReviewedCues",new Class<?>[]{String.class,String[].class},BASELINE+" Creator-reviewed reference moments: 0:01 "+roleCue,roles);
            reject(()->call("validateReviewedFashionIntent",new Class<?>[]{reviewed.getClass(),intent.getClass()},reviewed,intent));
        }
        String creator="Say one sentence about my own layering choice.";
        Object reviewed=call("parseReviewedCues",new Class<?>[]{String.class,String[].class},BASELINE+" Creator-reviewed reference moments: 0:01 Closing: "+creator,roles);
        call("validateReviewedFashionIntent",new Class<?>[]{reviewed.getClass(),intent.getClass()},reviewed,intent);
        List<Shot> plan=parse(draft(roles),roles,actions,intent);Shot generated=plan.get(4);
        plan.set(4,new Shot(generated.id,generated.title,creator,generated.caption,generated.targetDurationMs));
        call("validatePlanFashionIntent",new Class<?>[]{List.class,intent.getClass()},plan,intent);assertEquals(creator,plan.get(4).instruction);
        plan.get(0).instruction="Stand and take two steps.";reject(()->call("validatePlanFashionIntent",new Class<?>[]{List.class,intent.getClass()},plan,intent));
        assertEquals("Reject the contradictory creator text, never silently rewrite it","Stand and take two steps.",plan.get(0).instruction);
        assertFalse(LocalModelLease.isHeld());
    }

    private static JSONArray draft(String[] roles)throws Exception{
        String[] directions={"Stand wearing your outfit.","Turn your upper body slightly while staying in one spot.","Show one visible garment detail you choose.","Turn slightly sideways in place.","Tell in your own words one sentence about your layering choice."};
        String[] captions={"My outfit","In-place turn","Chosen detail","Side view","My layering choice"};JSONArray array=new JSONArray();
        for(int i=0;i<5;i++)array.put(new JSONObject().put("title",roles[i]).put("instruction",directions[i]).put("caption",captions[i]).put("duration_ms",i==4?8000:4000));return array;
    }
    @SuppressWarnings("unchecked")private static List<Shot> parse(JSONArray draft,String[] roles,String[][] actions,Object intent)throws Exception{return (List<Shot>)call("parse",new Class<?>[]{String.class,String[].class,String[][].class,boolean.class,intent.getClass()},draft.toString(),roles,actions,true,intent);}
    private static Object intent(String brief,String[] roles)throws Exception{return call("fashionIntent",new Class<?>[]{String.class,String[].class},brief,roles);}
    private static String[] roles(String style)throws Exception{return (String[])call("rolesForStyle",new Class<?>[]{String.class},style);}
    private static String[][] actions(String[] roles,Object intent)throws Exception{return (String[][])call("actionsForIntent",new Class<?>[]{String[].class,intent.getClass()},roles,intent);}
    private static Object constant(String name)throws Exception{Field f=LocalPlanner.class.getDeclaredField(name);f.setAccessible(true);return f.get(null);}
    private static boolean flag(Object value,String name)throws Exception{Field f=value.getClass().getDeclaredField(name);f.setAccessible(true);return f.getBoolean(value);}
    private static Object call(String name,Class<?>[] types,Object... values)throws Exception{Method m=LocalPlanner.class.getDeclaredMethod(name,types);m.setAccessible(true);try{return m.invoke(null,values);}catch(InvocationTargetException e){if(e.getCause() instanceof Exception)throw (Exception)e.getCause();throw e;}}
    private interface Action{void run()throws Exception;}
    private static void reject(Action action)throws Exception{try{action.run();fail("Contradictory constraint must reject before being applied");}catch(IllegalArgumentException expected){assertFalse(expected.getMessage().isEmpty());}}
}
