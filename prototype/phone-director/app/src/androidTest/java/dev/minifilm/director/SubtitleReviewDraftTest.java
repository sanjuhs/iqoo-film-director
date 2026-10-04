package dev.minifilm.director;

import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import static org.junit.Assert.*;

/** Corrective editor snapshots on nonexistent synthetic sources; no file/provider/model work. */
@RunWith(AndroidJUnit4.class)
public final class SubtitleReviewDraftTest {
    @Test public void independentImmutableFactsKeepExactWordsAndOrderedAssignmentsWithoutChangingSource(){
        Take source=source();SubtitleReviewDraft snapshot=SubtitleReviewDraft.capture(source);
        assertTrue(snapshot.matches(source));assertEquals(source.uri,snapshot.uri);assertEquals(source.durationMs,snapshot.durationMs);
        assertEquals(source.inMs,snapshot.inMs);assertEquals(source.outMs,snapshot.outMs);assertEquals(source.selected,snapshot.selected);
        assertEquals(source.shotId,snapshot.shotId);assertEquals(source.title,snapshot.title);assertEquals(source.caption,snapshot.caption);assertEquals(source.captionOrigin,snapshot.captionOrigin);
        assertNotSame(source.subtitles,snapshot.cues);assertNotSame(source.reviewedShotIds,snapshot.reviewedShotIds);
        assertEquals(Arrays.asList("current-hero","unknown-old-id","current-hero"),snapshot.reviewedShotIds);
        assertEquals("  First exact words!\nNo truncation.  ",snapshot.cues.get(0).text);
        assertThrows(UnsupportedOperationException.class,()->snapshot.cues.clear());
        assertThrows(UnsupportedOperationException.class,()->snapshot.reviewedShotIds.clear());
        for(java.lang.reflect.Field field:SubtitleReviewDraft.Cue.class.getFields())assertTrue(Modifier.isFinal(field.getModifiers()));
        source.subtitles.get(0).text="New current words";source.subtitles.get(0).startMs=750;source.reviewedShotIds.clear();
        assertEquals("  First exact words!\nNo truncation.  ",snapshot.cues.get(0).text);assertEquals(700,snapshot.cues.get(0).startMs);
        assertEquals(3,snapshot.reviewedShotIds.size());assertFalse(snapshot.matches(source));
    }

    @Test public void existingInvalidTimesOverlapAndBlankWordsRemainAvailableForCorrection(){
        Take source=source();source.subtitles.clear();
        source.subtitles.add(new SubtitleCue(-100,7000,"Outside source words"));
        source.subtitles.add(new SubtitleCue(1000,500,"Reversed time words"));
        source.subtitles.add(new SubtitleCue(1000,1000,"Zero-duration words"));
        source.subtitles.add(new SubtitleCue(600,900,"Nested words"));
        source.subtitles.add(new SubtitleCue(Long.MIN_VALUE,Long.MAX_VALUE,"Large invalid timing"));
        source.subtitles.add(new SubtitleCue(2000,3000,""));
        SubtitleReviewDraft snapshot=SubtitleReviewDraft.capture(source);assertTrue(snapshot.matches(source));assertEquals(6,snapshot.cues.size());
        for(int i=0;i<source.subtitles.size();i++){SubtitleCue original=source.subtitles.get(i);SubtitleReviewDraft.Cue draft=snapshot.cues.get(i);
            assertEquals(original.startMs,draft.startMs);assertEquals(original.endMs,draft.endMs);assertEquals(original.text,draft.text);}
        assertEquals("",snapshot.cues.get(5).text);assertEquals(-100,snapshot.cues.get(0).startMs);assertEquals(7000,snapshot.cues.get(0).endMs);
        // A UI can now display and correct all rows. The helper deliberately offers
        // no partial mutation/apply operation that could lose words on failed Save.
        assertEquals("Outside source words",source.subtitles.get(0).text);assertEquals(-100,source.subtitles.get(0).startMs);
    }

    @Test public void everyCurrentEditingFactAndSourceIdentityProtectsOldDialogFromOverwritingNewWork(){
        for(String fact:Arrays.asList("uri","duration","in","out","selected","id","title","caption","origin","mapping","mappingOrder","cueWords","cueStart","cueEnd","cueOrder","cueCount","nullCue","nullList")){
            Take source=source();SubtitleReviewDraft snapshot=SubtitleReviewDraft.capture(source);
            switch(fact){
                case "uri":source.uri=Uri.parse("content://synthetic/other");break;case "duration":source.durationMs++;break;
                case "in":source.inMs++;break;case "out":source.outMs++;break;case "selected":source.selected=false;break;
                case "id":source.shotId="different-original-id";break;case "title":source.title="Edited title";break;
                case "caption":source.caption="Edited fallback";break;case "origin":source.captionOrigin="creator-reviewed-offline-asr";break;
                case "mapping":source.reviewedShotIds.add("new-assignment");break;case "mappingOrder":java.util.Collections.swap(source.reviewedShotIds,0,1);break;
                case "cueWords":source.subtitles.get(0).text="New words";break;case "cueStart":source.subtitles.get(0).startMs++;break;
                case "cueEnd":source.subtitles.get(0).endMs++;break;case "cueOrder":java.util.Collections.reverse(source.subtitles);break;
                case "cueCount":source.subtitles.add(new SubtitleCue(5000,5200,"New cue"));break;
                case "nullCue":source.subtitles.set(0,null);break;case "nullList":source.subtitles=null;break;
            }
            assertFalse(fact,snapshot.matches(source));
        }
        Take source=source();SubtitleReviewDraft snapshot=SubtitleReviewDraft.capture(source);assertFalse(snapshot.matches(null));assertFalse(snapshot.matches(source()));
        ArrayList<Take> ordered=new ArrayList<>(Arrays.asList(source(),source));java.util.Collections.reverse(ordered);assertTrue(snapshot.matches(ordered.get(0)));
    }

    @Test public void onlyUnreadableStructureAndBoundedCountRejectWhileEmptyAndFiveHundredRemainEditable(){
        Take empty=source();empty.subtitles.clear();assertEquals(0,SubtitleReviewDraft.capture(empty).cues.size());
        Take many=source();many.subtitles.clear();for(int i=0;i<500;i++)many.subtitles.add(new SubtitleCue(-1,-1,"Correct me "+i));
        assertEquals(500,SubtitleReviewDraft.capture(many).cues.size());many.subtitles.add(new SubtitleCue(0,0,"501st exact words"));
        assertTrue(assertThrows(IllegalArgumentException.class,()->SubtitleReviewDraft.capture(many)).getMessage().contains("500"));
        assertEquals(501,many.subtitles.size());assertEquals("501st exact words",many.subtitles.get(500).text);
        assertThrows(IllegalArgumentException.class,()->SubtitleReviewDraft.capture(null));
        for(String bad:Arrays.asList("source","duration","nullList","nullMappings","nullCue","nullWords")){
            Take source=source();switch(bad){case "source":source.uri=null;break;case "duration":source.durationMs=0;break;
                case "nullList":source.subtitles=null;break;case "nullMappings":source.reviewedShotIds=null;break;
                case "nullCue":source.subtitles.set(0,null);break;case "nullWords":source.subtitles.get(0).text=null;break;}
            assertFalse(assertThrows(bad,IllegalArgumentException.class,()->SubtitleReviewDraft.capture(source)).getMessage().trim().isEmpty());
        }
    }

    private static Take source(){Take source=new Take(Uri.parse("content://synthetic.nonexistent/subtitles"),"original-capture-id","Synthetic original","Fallback words",5746);
        source.inMs=500;source.outMs=5500;source.captionOrigin="whisper-tiny.en-draft";
        source.reviewedShotIds.addAll(Arrays.asList("current-hero","unknown-old-id","current-hero"));
        source.subtitles.add(new SubtitleCue(700,1100,"  First exact words!\nNo truncation.  "));
        source.subtitles.add(new SubtitleCue(4300,4700,"Last exact words"));return source;}
}
