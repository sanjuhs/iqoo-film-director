package dev.minifilm.director;

import static org.junit.Assert.*;
import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Proposal ownership on nonexistent synthetic sources. No media, model, preferences or UI. */
@RunWith(AndroidJUnit4.class)
public final class SubtitleProposalDraftTest {
    @Test public void proposedDisplayIsDeepImmutableAndNeverReplacesOriginalWordsOrMatchingBaseline(){
        Take source=source();List<SubtitleCue> originalList=source.subtitles;SubtitleCue originalCue=source.subtitles.get(0);
        SubtitleReviewDraft captured=SubtitleReviewDraft.capture(source);
        List<SubtitleCue> generated=new ArrayList<>(Arrays.asList(new SubtitleCue(800,1200,"Generated alternative")));
        SubtitleReviewDraft proposal=captured.withProposedCues(generated);
        assertTrue(captured.matches(source));assertTrue(proposal.matches(source));assertEquals(2,captured.cues.size());assertEquals(1,proposal.cues.size());
        assertEquals("Generated alternative",proposal.cues.get(0).text);assertEquals(800,proposal.cues.get(0).startMs);
        assertSame(originalList,source.subtitles);assertSame(originalCue,source.subtitles.get(0));assertEquals("Corrected words outside the cut",originalCue.text);
        assertEquals("creator-reviewed-offline-asr",source.captionOrigin);assertEquals("Fallback unchanged",source.caption);
        generated.get(0).text="Changed reader-owned result";generated.get(0).startMs=100;generated.clear();
        assertEquals("Generated alternative",proposal.cues.get(0).text);assertEquals(800,proposal.cues.get(0).startMs);
        assertThrows(UnsupportedOperationException.class,()->proposal.cues.clear());
        assertThrows(UnsupportedOperationException.class,()->proposal.reviewedShotIds.clear());
        // Merely substituting the displayed words is not a valid current original baseline.
        source.subtitles=new ArrayList<>(Collections.singletonList(new SubtitleCue(800,1200,"Generated alternative")));
        assertFalse(proposal.matches(source));assertFalse(captured.matches(source));
        source.subtitles=originalList;assertTrue(proposal.matches(source));
        assertEquals("Corrected words outside the cut",captured.cues.get(0).text);
    }

    @Test public void laterProposalViewsKeepOriginalIdentityAndFactsEvenIfTakeChangedBeforeCreation(){
        Take source=source();SubtitleReviewDraft captured=SubtitleReviewDraft.capture(source);
        SubtitleReviewDraft first=captured.withProposedCues(Collections.singletonList(new SubtitleCue(1000,1300,"First proposal")));
        SubtitleReviewDraft second=first.withProposedCues(Collections.emptyList());
        assertTrue(second.matches(source));assertEquals(0,second.cues.size());assertEquals("First proposal",first.cues.get(0).text);assertEquals(2,captured.cues.size());
        source.title="New title";source.subtitles.get(0).text="New corrected words";
        SubtitleReviewDraft late=first.withProposedCues(Collections.singletonList(new SubtitleCue(1100,1400,"Late proposal")));
        assertFalse(late.matches(source));assertEquals("Original title",late.title);assertEquals("creator-reviewed-offline-asr",late.captionOrigin);
        assertFalse(late.matches(source()));assertFalse(late.matches(null));
        source.title="Original title";source.subtitles.get(0).text="Corrected words outside the cut";assertTrue(late.matches(source));
        for(String fact:Arrays.asList("source","duration","in","out","selected","captureId","caption","origin","mapping","originalStart","originalEnd","originalWords","originalOrder","nullWords")){
            Take take=source();SubtitleReviewDraft proposed=SubtitleReviewDraft.capture(take).withProposedCues(Collections.singletonList(new SubtitleCue(500,900,"Display only")));
            switch(fact){case "source":take.uri=Uri.parse("content://synthetic/changed");break;case "duration":take.durationMs++;break;
                case "in":take.inMs++;break;case "out":take.outMs++;break;case "selected":take.selected=false;break;case "captureId":take.shotId="New capture";break;
                case "caption":take.caption="New fallback";break;case "origin":take.captionOrigin="New origin";break;case "mapping":take.reviewedShotIds=new ArrayList<>();break;
                case "originalStart":take.subtitles.get(0).startMs++;break;case "originalEnd":take.subtitles.get(0).endMs++;break;case "originalWords":take.subtitles.get(0).text="New words";break;
                case "originalOrder":take.subtitles=Arrays.asList(take.subtitles.get(1),take.subtitles.get(0));break;case "nullWords":take.subtitles=null;break;}
            assertFalse(fact,proposed.matches(take));assertEquals("Display only",proposed.cues.get(0).text);
        }
        Take moved=source();SubtitleReviewDraft proposal=SubtitleReviewDraft.capture(moved).withProposedCues(Collections.emptyList());
        List<Take> order=new ArrayList<>(Arrays.asList(source(),moved));Collections.reverse(order);assertTrue(proposal.matches(order.get(0)));
    }

    @Test public void malformedTimesOverlapAndBlankWordsStayEditableButUnreadableOrOverFiveHundredReject(){
        Take source=source();SubtitleReviewDraft captured=SubtitleReviewDraft.capture(source);
        List<SubtitleCue> malformed=Arrays.asList(new SubtitleCue(Long.MIN_VALUE,Long.MAX_VALUE,"  Exact words  "),
                new SubtitleCue(900,500,"Reversed"),new SubtitleCue(600,600,""),new SubtitleCue(100,1100,"Overlapping"));
        SubtitleReviewDraft proposal=captured.withProposedCues(malformed);
        assertTrue(proposal.matches(source));assertEquals(4,proposal.cues.size());
        for(int i=0;i<malformed.size();i++){assertEquals(malformed.get(i).startMs,proposal.cues.get(i).startMs);assertEquals(malformed.get(i).endMs,proposal.cues.get(i).endMs);assertEquals(malformed.get(i).text,proposal.cues.get(i).text);}
        List<SubtitleCue> many=new ArrayList<>();for(int i=0;i<500;i++)many.add(new SubtitleCue(-1,-1,"Correction "+i));
        assertEquals(500,captured.withProposedCues(many).cues.size());many.add(new SubtitleCue(0,0,"501st exact words"));
        assertTrue(assertThrows(IllegalArgumentException.class,()->captured.withProposedCues(many)).getMessage().contains("500"));assertEquals(501,many.size());
        for(List<SubtitleCue> bad:Arrays.<List<SubtitleCue>>asList(null,Collections.singletonList(null),Collections.singletonList(new SubtitleCue(1,2,null))))
            assertFalse(assertThrows(IllegalArgumentException.class,()->captured.withProposedCues(bad)).getMessage().trim().isEmpty());
        assertTrue(captured.matches(source));assertEquals(2,source.subtitles.size());assertEquals("Corrected words outside the cut",source.subtitles.get(0).text);
    }

    private static Take source(){Take take=new Take(Uri.parse("content://synthetic.nonexistent/proposed-subtitles"),"capture-id","Original title","Fallback unchanged",5000);
        take.inMs=500;take.outMs=4500;take.captionOrigin="creator-reviewed-offline-asr";
        take.reviewedShotIds=Arrays.asList("current-hero","unknown-old-id");
        // Fixed-size source list also represents another take's shared words: helper must not clear/mutate it.
        take.subtitles=Arrays.asList(new SubtitleCue(-25,300,"Corrected words outside the cut"),new SubtitleCue(1500,1800,"Corrected current words"));return take;}
}
