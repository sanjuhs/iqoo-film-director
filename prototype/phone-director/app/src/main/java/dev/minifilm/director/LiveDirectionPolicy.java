package dev.minifilm.director;

/** Pure cue admission, not an audio queue or scene-understanding model.
 * Callers own recording identity, opt-in, foreground/audio gates and stopping active speech.
 * ACTION/SHOT/HOLD execute the plan; only POSE carries a supplied learned pose observation.
 */
public final class LiveDirectionPolicy {
    public enum Kind { ACTION, SHOT, POSE, HOLD }
    public static final class Candidate {
        public final long epoch;
        public final Kind kind;
        public final String text;
        private final LiveDirectionPolicy owner;
        private final long generation, issuedElapsedMs, poseVersion;
        private Candidate(LiveDirectionPolicy owner,long epoch,long generation,Kind kind,String text,long issuedElapsedMs,long poseVersion){
            this.owner=owner;
            this.epoch=epoch;this.generation=generation;this.kind=kind;this.text=text;
            this.issuedElapsedMs=issuedElapsedMs;this.poseVersion=poseVersion;
        }
    }
    private static final long MAX_RECORD_MS=60_000,FRESH_MS=2_000,POSE_GAP_MS=6_000,SAME_POSE_GAP_MS=12_000;
    private long epoch,generation,started,targetDuration,lastAny=-1,lastPose=-1,poseObserved,poseVersion;
    private String instruction="",pendingPose,lastPoseText;
    private boolean active,actionDone,shotDone,holdDone;
    private int poseCount;

    /** Empty instruction skips SHOT when guided preparation already spoke it in full. */
    public synchronized void reset(long epoch,long startedElapsedMs,long targetDurationMs,String shotInstruction){
        if(epoch<=0||startedElapsedMs<0||targetDurationMs<2_000||targetDurationMs>MAX_RECORD_MS)
            throw new IllegalArgumentException("Use one bounded current recording.");
        if(shotInstruction==null||shotInstruction.length()>2_000)
            throw new IllegalArgumentException("Use a bounded shot direction, or an empty already-spoken direction.");
        clear();this.epoch=epoch;started=startedElapsedMs;targetDuration=targetDurationMs;
        instruction=shotInstruction;shotDone=instruction.trim().isEmpty();active=true;
    }
    /** One replaceable pending observation. Never preserve stale observations from another epoch. */
    public synchronized void offerPose(long epoch,String cue,long observedElapsedMs){
        if(!active||this.epoch!=epoch||elapsed(observedElapsedMs)<0||cue==null||cue.trim().isEmpty()||cue.length()>2_000)return;
        if(pendingPose!=null&&observedElapsedMs<poseObserved)return;
        pendingPose=cue;poseObserved=observedElapsedMs;poseVersion++;
    }
    /** No admission while speech is occupied. No invented progress, frame interpretation or readiness score. */
    public synchronized Candidate next(long epoch,long nowElapsedMs,boolean voiceIdle,boolean allowFinalHold){
        long offset=elapsed(nowElapsedMs);
        if(!active||this.epoch!=epoch||offset<0||!voiceIdle)return null;
        if(pendingPose!=null&&(nowElapsedMs<poseObserved||nowElapsedMs-poseObserved>FRESH_MS))pendingPose=null;
        if(!actionDone)return candidate(Kind.ACTION,"Action.",nowElapsedMs);
        if(!shotDone&&offset>=1_500&&nowElapsedMs-lastAny>=1_500)return candidate(Kind.SHOT,instruction,nowElapsedMs);
        long remaining=targetDuration-offset;
        if(allowFinalHold&&!holdDone&&targetDuration>=6_000&&remaining>=0&&remaining<=2_000)
            return candidate(Kind.HOLD,"Hold for the final beat.",nowElapsedMs);
        if(pendingPose==null||poseCount>=3||nowElapsedMs-lastAny<3_000
                ||lastPose>=0&&nowElapsedMs-lastPose<POSE_GAP_MS
                ||pendingPose.equals(lastPoseText)&&lastPose>=0&&nowElapsedMs-lastPose<SAME_POSE_GAP_MS)return null;
        return candidate(Kind.POSE,pendingPose,nowElapsedMs);
    }
    /** Commit only after the real speech engine accepts this candidate. Failed/busy speech is not counted. */
    public synchronized boolean commit(Candidate candidate,long admittedElapsedMs){
        if(candidate==null||candidate.owner!=this||!active||candidate.generation!=generation||candidate.epoch!=epoch
                ||elapsed(admittedElapsedMs)<0||admittedElapsedMs<candidate.issuedElapsedMs
                ||admittedElapsedMs-candidate.issuedElapsedMs>FRESH_MS)return false;
        switch(candidate.kind){
            case ACTION:
                if(actionDone)return false;actionDone=true;break;
            case SHOT:
                if(!actionDone||shotDone||elapsed(admittedElapsedMs)<1_500||admittedElapsedMs-lastAny<1_500)return false;
                shotDone=true;break;
            case HOLD:
                long remaining=targetDuration-elapsed(admittedElapsedMs);
                if(!actionDone||holdDone||targetDuration<6_000||remaining<0||remaining>2_000)return false;
                holdDone=true;break;
            case POSE:
                if(!actionDone||pendingPose==null||candidate.poseVersion!=poseVersion||!candidate.text.equals(pendingPose)
                        ||admittedElapsedMs<poseObserved||admittedElapsedMs-poseObserved>FRESH_MS||poseCount>=3
                        ||admittedElapsedMs-lastAny<3_000||lastPose>=0&&admittedElapsedMs-lastPose<POSE_GAP_MS
                        ||pendingPose.equals(lastPoseText)&&lastPose>=0&&admittedElapsedMs-lastPose<SAME_POSE_GAP_MS)return false;
                lastPose=admittedElapsedMs;lastPoseText=pendingPose;pendingPose=null;poseCount++;break;
        }
        lastAny=admittedElapsedMs;return true;
    }
    /** Invalidates even a retained candidate whose epoch is later reused. No speech is stopped here. */
    public synchronized void clear(){
        generation++;active=false;epoch=started=targetDuration=poseObserved=poseVersion=0;
        instruction="";pendingPose=lastPoseText=null;lastAny=lastPose=-1;poseCount=0;actionDone=shotDone=holdDone=false;
    }
    private Candidate candidate(Kind kind,String text,long now){return new Candidate(this,epoch,generation,kind,text,now,poseVersion);}
    private long elapsed(long now){if(now<started)return -1;long offset=now-started;return offset<=MAX_RECORD_MS?offset:-1;}
}
