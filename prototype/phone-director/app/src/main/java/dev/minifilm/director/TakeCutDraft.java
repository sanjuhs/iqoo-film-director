package dev.minifilm.director;

import android.net.Uri;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Metadata-only extra-cut draft. No source read, copy, deletion or timestamp rewriting. */
public final class TakeCutDraft {
    public static final String STALE_MESSAGE = "This take changed. Open another moment again to keep your latest edits.";
    public final Uri uri;
    public final String shotId, title, caption, captionOrigin;
    public final long durationMs, inMs, outMs;
    public final boolean selected;
    public final List<String> reviewedShotIds;
    private final Take sourceIdentity;
    private final List<CueFact> cues;

    private static final class CueFact {
        final long start, end; final String text;
        CueFact(SubtitleCue cue) { start=cue.startMs;end=cue.endMs;text=cue.text; }
    }

    private TakeCutDraft(Take source) {
        sourceIdentity=source;uri=source.uri;shotId=source.shotId;title=source.title;
        caption=source.caption;captionOrigin=source.captionOrigin;durationMs=source.durationMs;
        inMs=source.inMs;outMs=source.outMs;selected=source.selected;
        reviewedShotIds=Collections.unmodifiableList(new ArrayList<>(source.reviewedShotIds));
        List<CueFact> copy=new ArrayList<>();for(SubtitleCue cue:source.subtitles)copy.add(new CueFact(cue));
        cues=Collections.unmodifiableList(copy);
    }

    public static TakeCutDraft capture(Take source) {
        if(source==null)throw new IllegalArgumentException("Choose a take for another moment.");
        requireLocal(source.uri);requireRange(source.durationMs,source.inMs,source.outMs);
        if(source.shotId==null||source.title==null||source.caption==null||source.captionOrigin==null
                ||source.reviewedShotIds==null||source.subtitles==null)
            throw new IllegalArgumentException("Review this take's saved words and assignments before adding another moment.");
        for(String id:source.reviewedShotIds)if(id==null)
            throw new IllegalArgumentException("Review this take's saved shot assignments.");
        if(source.subtitles.size()>500)throw new IllegalArgumentException("Use at most 500 subtitle segments per cut.");
        for(SubtitleCue cue:source.subtitles)if(cue==null||cue.text==null||cue.startMs<0
                ||cue.endMs<=cue.startMs||cue.endMs>source.durationMs)
            throw new IllegalArgumentException("Review subtitle timestamps inside this source before adding another moment.");
        SubtitleTimeline.requireNonOverlapping(source.subtitles);
        return new TakeCutDraft(source);
    }

    /** Exact take identity and ordered editing facts, independent of its current list position. */
    public boolean matches(Take source) {
        if(source!=sourceIdentity||!uri.equals(source.uri)||durationMs!=source.durationMs
                ||inMs!=source.inMs||outMs!=source.outMs||selected!=source.selected
                ||!Objects.equals(shotId,source.shotId)||!Objects.equals(title,source.title)
                ||!Objects.equals(caption,source.caption)||!Objects.equals(captionOrigin,source.captionOrigin)
                ||!reviewedShotIds.equals(source.reviewedShotIds)||source.subtitles==null
                ||source.subtitles.size()!=cues.size())return false;
        for(int i=0;i<cues.size();i++){
            CueFact frozen=cues.get(i);SubtitleCue current=source.subtitles.get(i);
            if(current==null||current.startMs!=frozen.start||current.endMs!=frozen.end
                    ||!Objects.equals(current.text,frozen.text))return false;
        }
        return true;
    }

    /** New range may lie anywhere in the original, not merely inside the first cut.
     * Keeps subtitle source times/words even outside the new range; the renderer clips visibility.
     * The new cut stays unselected until the creator chooses it for the reel.
     */
    public Take create(long startMs,long endMs,String words) {
        return create(startMs,endMs,title,words);
    }

    public Take create(long startMs,long endMs,String newTitle,String words) {
        if(!matches(sourceIdentity))throw new IllegalArgumentException(STALE_MESSAGE);
        requireRange(durationMs,startMs,endMs);
        if(newTitle==null)throw new IllegalArgumentException("Enter a take title, or leave it empty.");
        if(words==null)throw new IllegalArgumentException("Enter caption text, or leave it empty.");
        Take cut=new Take(uri,shotId,newTitle,words,durationMs);cut.inMs=startMs;cut.outMs=endMs;cut.selected=false;
        // This field also describes retained timed words. Editing fallback typography
        // cannot relabel an unchanged ASR draft as reviewed/manual speech text.
        cut.captionOrigin=!cues.isEmpty()||caption.equals(words)?captionOrigin:"manual";
        cut.reviewedShotIds=new ArrayList<>(reviewedShotIds);
        for(CueFact cue:cues)cut.subtitles.add(new SubtitleCue(cue.start,cue.end,cue.text));
        return cut;
    }

    private static void requireRange(long duration,long start,long end) {
        // Source length has no new limit. Existing reel export bounds selected timeline length.
        if(duration<=0||start<0||end>duration||end<=start||end-start<250)
            throw new IllegalArgumentException("Use a valid cut at least 0.25 sec long within this take.");
    }
    private static void requireLocal(Uri uri) {
        if(uri==null)throw new IllegalArgumentException("Choose a saved local video.");
        if("file".equals(uri.getScheme())){
            String path=uri.getPath();if(path!=null&&path.startsWith("/")&&path.length()>1
                    &&(uri.getAuthority()==null||uri.getAuthority().isEmpty())
                    &&uri.getQuery()==null&&uri.getFragment()==null)return;
        }else if("content".equals(uri.getScheme())&&uri.getAuthority()!=null&&!uri.getAuthority().isEmpty()
                &&uri.getPath()!=null&&!uri.getPath().isEmpty())return;
        throw new IllegalArgumentException("Choose a video saved on your phone.");
    }
}
