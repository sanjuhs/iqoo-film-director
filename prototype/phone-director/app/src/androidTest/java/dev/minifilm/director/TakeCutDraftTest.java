package dev.minifilm.director;

import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.ArrayList;
import java.util.Arrays;
import static org.junit.Assert.*;

/** Pure metadata on deliberately nonexistent local URIs: no provider/file read or native work. */
@RunWith(AndroidJUnit4.class)
public final class TakeCutDraftTest {
    @Test public void draftAloneChangesNothingAndCreatesIndependentUnselectedDisjointCuts(){
        Take source=source();TakeCutDraft draft=TakeCutDraft.capture(source);
        assertTrue(draft.matches(source));assertEquals(500,source.inMs);assertEquals(1500,source.outMs);
        assertEquals("Source caption",source.caption);assertTrue(source.selected);assertEquals(2,source.subtitles.size());
        Take first=draft.create(4000,5000,source.caption),second=draft.create(5000,5746,"New manual words");
        assertNotSame(source,first);assertNotSame(first,second);assertEquals(source.uri,first.uri);
        assertEquals(source.shotId,first.shotId);assertEquals(source.title,first.title);assertEquals(5746,first.durationMs);
        assertFalse(first.selected);assertFalse(second.selected);assertEquals("creator-reviewed-offline-asr",first.captionOrigin);assertEquals("creator-reviewed-offline-asr",second.captionOrigin);
        assertNotSame(source.subtitles,first.subtitles);assertNotSame(first.subtitles,second.subtitles);
        assertNotSame(source.subtitles.get(0),first.subtitles.get(0));assertNotSame(first.subtitles.get(0),second.subtitles.get(0));
        assertNotSame(source.reviewedShotIds,first.reviewedShotIds);assertNotSame(first.reviewedShotIds,second.reviewedShotIds);
        first.subtitles.get(0).text="Edited independent words";first.subtitles.get(1).startMs=4200;first.reviewedShotIds.clear();
        assertEquals("Original first words",source.subtitles.get(0).text);assertEquals("Original first words",second.subtitles.get(0).text);
        assertEquals(4300,source.subtitles.get(1).startMs);assertEquals(4300,second.subtitles.get(1).startMs);
        assertEquals(Arrays.asList("current-hero","old-plan-id"),source.reviewedShotIds);
        assertEquals(source.reviewedShotIds,second.reviewedShotIds);assertTrue(draft.matches(source));
        assertThrows(UnsupportedOperationException.class,()->draft.reviewedShotIds.clear());
        Take named=draft.create(4000,5000,"My separate moment","Changed fallback caption");
        assertEquals("My separate moment",named.title);assertEquals("Changed fallback caption",named.caption);
        assertEquals("creator-reviewed-offline-asr",named.captionOrigin);
        source.subtitles.clear();source.captionOrigin="manual-caption-original";TakeCutDraft noTimedWords=TakeCutDraft.capture(source);
        assertEquals("manual-caption-original",noTimedWords.create(4000,5000,source.caption).captionOrigin);
        assertEquals("manual",noTimedWords.create(4000,5000,"Changed caption").captionOrigin);
    }

    @Test public void exactSourceIdentityAndEveryEditedFactInvalidateSaveButListPositionDoesNot(){
        for(String field:Arrays.asList("uri","shotId","title","caption","origin","duration","in","out","selected","mapping","cueWords","cueStart","cueEnd","cueOrder")){
            Take source=source();TakeCutDraft draft=TakeCutDraft.capture(source);
            switch(field){
                case "uri":source.uri=Uri.parse("content://synthetic/other");break;
                case "shotId":source.shotId="other";break;case "title":source.title="other";break;
                case "caption":source.caption="other";break;case "origin":source.captionOrigin="manual";break;
                case "duration":source.durationMs++;break;case "in":source.inMs++;break;case "out":source.outMs++;break;
                case "selected":source.selected=false;break;case "mapping":source.reviewedShotIds.add("other");break;
                case "cueWords":source.subtitles.get(0).text="other";break;case "cueStart":source.subtitles.get(0).startMs++;break;
                case "cueEnd":source.subtitles.get(0).endMs++;break;case "cueOrder":java.util.Collections.reverse(source.subtitles);break;
            }
            assertFalse(field,draft.matches(source));assertEquals(TakeCutDraft.STALE_MESSAGE,
                    assertThrows(field,IllegalArgumentException.class,()->draft.create(4000,5000,"New words")).getMessage());
        }
        Take source=source();TakeCutDraft draft=TakeCutDraft.capture(source);assertFalse(draft.matches(source()));assertFalse(draft.matches(null));
        ArrayList<Take> list=new ArrayList<>(Arrays.asList(source,source()));java.util.Collections.reverse(list);
        assertTrue(draft.matches(list.get(1)));assertFalse(draft.create(4000,5000,source.caption).selected);
    }

    @Test public void exactEndMinimumAndLongOriginalBoundsWorkWithoutArbitrarySourceCap(){
        Take source=source();TakeCutDraft draft=TakeCutDraft.capture(source);
        assertEquals(5746,draft.create(5496,5746,"End").outMs);
        for(long[] range:new long[][]{{-1,500},{0,0},{500,499},{0,249},{5000,5747},{Long.MIN_VALUE,Long.MAX_VALUE}})
            assertThrows(IllegalArgumentException.class,()->draft.create(range[0],range[1],"Words"));
        assertThrows(IllegalArgumentException.class,()->draft.create(4000,5000,null));
        source.durationMs=600000;source.subtitles.clear();source.outMs=300000;
        assertEquals(600000,TakeCutDraft.capture(source).create(599750,600000,"Last quarter second").outMs);
    }

    @Test public void malformedRemoteAndSubtitleInputsRejectWithoutDiscardingOriginalMetadata(){
        for(Uri uri:Arrays.asList(null,Uri.parse("https://example.invalid/video.mp4"),Uri.parse("file://remote/video"),
                Uri.parse("file:///"),Uri.parse("file:///synthetic.mp4?x=1"),Uri.parse("content:///missing-authority"))){
            Take source=source();source.uri=uri;assertThrows(IllegalArgumentException.class,()->TakeCutDraft.capture(source));assertEquals(uri,source.uri);
        }
        for(SubtitleCue bad:Arrays.asList(null,new SubtitleCue(-1,10,"bad"),new SubtitleCue(10,10,"bad"),new SubtitleCue(10,5747,"bad"),new SubtitleCue(10,20,null))){
            Take source=source();source.subtitles.add(bad);assertThrows(IllegalArgumentException.class,()->TakeCutDraft.capture(source));assertSame(bad,source.subtitles.get(2));
        }
        Take source=source();source.subtitles.add(new SubtitleCue(800,950,"Overlapping original words"));
        assertEquals(SubtitleTimeline.OVERLAP_MESSAGE,assertThrows(IllegalArgumentException.class,()->TakeCutDraft.capture(source)).getMessage());
        assertEquals("Overlapping original words",source.subtitles.get(2).text);
    }

    private static Take source(){Take source=new Take(Uri.parse("content://synthetic.nonexistent/cut-source"),"original-shot-provenance","Original take","Source caption",5746);
        source.inMs=500;source.outMs=1500;source.captionOrigin="creator-reviewed-offline-asr";
        source.reviewedShotIds.addAll(Arrays.asList("current-hero","old-plan-id"));
        source.subtitles.add(new SubtitleCue(700,1000,"Original first words"));source.subtitles.add(new SubtitleCue(4300,4700,"Original last words"));return source;}
}
