package dev.minifilm.director;

import android.net.Uri;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Immutable original facts for a corrective subtitle editor. No media read or mutation.
 * Numeric timing errors, overlaps and blank words intentionally remain editable.
 * The UI must validate the entire corrected list before replacing any current words.
 */
public final class SubtitleReviewDraft {
    public static final String UNREADABLE_MESSAGE = "This subtitle draft could not be opened. Existing words and times were kept.";
    public final Uri uri;
    public final String shotId,title,caption,captionOrigin;
    public final long durationMs,inMs,outMs;
    public final boolean selected;
    public final List<String> reviewedShotIds;
    /** Immutable rows shown in the editor: original words or an unapplied proposal. */
    public final List<Cue> cues;
    private final List<Cue> baselineCues;
    private final Take sourceIdentity;

    public static final class Cue {
        public final long startMs,endMs;
        public final String text;
        private Cue(SubtitleCue source){startMs=source.startMs;endMs=source.endMs;text=source.text;}
    }

    private SubtitleReviewDraft(Take source){
        sourceIdentity=source;uri=source.uri;shotId=source.shotId;title=source.title;
        caption=source.caption;captionOrigin=source.captionOrigin;durationMs=source.durationMs;
        inMs=source.inMs;outMs=source.outMs;selected=source.selected;
        reviewedShotIds=Collections.unmodifiableList(new ArrayList<>(source.reviewedShotIds));
        List<Cue> copied=new ArrayList<>(source.subtitles.size());
        for(SubtitleCue cue:source.subtitles)copied.add(new Cue(cue));
        cues=Collections.unmodifiableList(copied);baselineCues=cues;
    }

    private SubtitleReviewDraft(SubtitleReviewDraft original,List<Cue> proposed){
        sourceIdentity=original.sourceIdentity;uri=original.uri;shotId=original.shotId;title=original.title;
        caption=original.caption;captionOrigin=original.captionOrigin;durationMs=original.durationMs;
        inMs=original.inMs;outMs=original.outMs;selected=original.selected;
        reviewedShotIds=original.reviewedShotIds;baselineCues=original.baselineCues;cues=proposed;
    }

    /** Show an unapplied proposal without recapturing any current Take facts. Matching always
     * checks the original captured words, even after chaining proposal views. Numeric errors,
     * overlaps and blank words remain visible for correction; only Save validates the result. */
    public SubtitleReviewDraft withProposedCues(List<SubtitleCue> proposed){
        if(proposed==null)throw new IllegalArgumentException(UNREADABLE_MESSAGE);
        if(proposed.size()>500)throw new IllegalArgumentException("Review at most 500 subtitle segments at a time. Existing words and times were kept.");
        List<Cue> copied=new ArrayList<>(proposed.size());
        for(SubtitleCue cue:proposed){
            if(cue==null||cue.text==null)throw new IllegalArgumentException(UNREADABLE_MESSAGE);
            copied.add(new Cue(cue));
        }
        return new SubtitleReviewDraft(this,Collections.unmodifiableList(copied));
    }

    public static SubtitleReviewDraft capture(Take source){
        if(source==null||source.uri==null)throw new IllegalArgumentException("Choose a saved take to review its subtitle words.");
        if(source.durationMs<=0)throw new IllegalArgumentException("Source duration is unavailable. Existing subtitles were kept.");
        if(source.subtitles==null||source.reviewedShotIds==null)throw new IllegalArgumentException(UNREADABLE_MESSAGE);
        if(source.subtitles.size()>500)throw new IllegalArgumentException("Review at most 500 subtitle segments at a time. Existing words and times were kept.");
        for(SubtitleCue cue:source.subtitles)if(cue==null||cue.text==null)throw new IllegalArgumentException(UNREADABLE_MESSAGE);
        // Do not apply SubtitleTimeline, range normalization or empty-word filtering here.
        // Those checks belong to the corrected Save result, not the draft being repaired.
        return new SubtitleReviewDraft(source);
    }

    /** The same Take and ordered facts must still be present; list position is irrelevant. */
    public boolean matches(Take source){
        if(source!=sourceIdentity||!Objects.equals(uri,source.uri)||durationMs!=source.durationMs
                ||inMs!=source.inMs||outMs!=source.outMs||selected!=source.selected
                ||!Objects.equals(shotId,source.shotId)||!Objects.equals(title,source.title)
                ||!Objects.equals(caption,source.caption)||!Objects.equals(captionOrigin,source.captionOrigin)
                ||!reviewedShotIds.equals(source.reviewedShotIds)||source.subtitles==null
                ||source.subtitles.size()!=baselineCues.size())return false;
        for(int i=0;i<baselineCues.size();i++){
            Cue before=baselineCues.get(i);SubtitleCue now=source.subtitles.get(i);
            if(now==null||before.startMs!=now.startMs||before.endMs!=now.endMs||!Objects.equals(before.text,now.text))return false;
        }
        return true;
    }
}
