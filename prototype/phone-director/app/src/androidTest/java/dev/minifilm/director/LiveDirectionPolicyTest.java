package dev.minifilm.director;

import static org.junit.Assert.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Deterministic cue admission only: no ML inference, recording or audible output. */
@RunWith(AndroidJUnit4.class)
public final class LiveDirectionPolicyTest {
    private static final long START=100_000,EPOCH=7;

    @Test public void acceptedActionAndPlanCueRunOnceAndBusySpeechDoesNotConsumeThem(){
        LiveDirectionPolicy policy=new LiveDirectionPolicy();policy.reset(EPOCH,START,20_000,"Turn slightly sideways.");
        assertNull(policy.next(EPOCH,START,false,true));
        LiveDirectionPolicy.Candidate action=policy.next(EPOCH,START,true,true);assertEquals(LiveDirectionPolicy.Kind.ACTION,action.kind);
        assertEquals("Action.",action.text);assertTrue(policy.commit(action,START));assertFalse(policy.commit(action,START));
        assertNull(policy.next(EPOCH,START+1_499,true,true));assertNull(policy.next(EPOCH,START+1_500,false,true));
        LiveDirectionPolicy.Candidate shot=policy.next(EPOCH,START+1_500,true,true);
        assertEquals(LiveDirectionPolicy.Kind.SHOT,shot.kind);assertEquals("Turn slightly sideways.",shot.text);
        assertTrue(policy.commit(shot,START+1_500));assertFalse(policy.commit(shot,START+1_500));
        policy.offerPose(EPOCH,"Relax your shoulders.",START+3_000);
        assertNull("Pose must not immediately follow the scene instruction",policy.next(EPOCH,START+4_499,true,false));
        assertEquals(LiveDirectionPolicy.Kind.POSE,policy.next(EPOCH,START+4_500,true,false).kind);
    }

    @Test public void latestFreshPoseReplacesOlderCueSpacingRepeatsAndThreeHintCapAreMeasuredAtCommit(){
        LiveDirectionPolicy policy=new LiveDirectionPolicy();policy.reset(EPOCH,START,60_000,"");
        assertTrue(policy.commit(policy.next(EPOCH,START,true,false),START));
        policy.offerPose(EPOCH,"Old hint",START+2_000);policy.offerPose(EPOCH,"New hint",START+3_000);
        policy.offerPose(EPOCH,"Out of order hint",START+2_500);
        LiveDirectionPolicy.Candidate first=policy.next(EPOCH,START+3_000,true,false);assertEquals("New hint",first.text);
        assertTrue(policy.commit(first,START+3_000));
        policy.offerPose(EPOCH,"Different hint",START+8_999);assertNull(policy.next(EPOCH,START+8_999,true,false));
        policy.offerPose(EPOCH,"New hint",START+9_000);assertNull("Identical consecutive cue needs twelve seconds",policy.next(EPOCH,START+9_000,true,false));
        policy.offerPose(EPOCH,"New hint",START+15_000);assertTrue(policy.commit(policy.next(EPOCH,START+15_000,true,false),START+15_000));
        policy.offerPose(EPOCH,"Another hint",START+21_000);assertTrue(policy.commit(policy.next(EPOCH,START+21_000,true,false),START+21_000));
        policy.offerPose(EPOCH,"Fourth hint",START+40_000);assertNull("At most three spoken pose hints per recording",policy.next(EPOCH,START+40_000,true,false));
    }

    @Test public void staleReplacedFutureAndCrossEpochObservationsCannotBecomeAcceptedSpeech(){
        LiveDirectionPolicy policy=new LiveDirectionPolicy();policy.reset(EPOCH,START,20_000,"");
        assertTrue(policy.commit(policy.next(EPOCH,START,true,false),START));
        policy.offerPose(EPOCH,"Stale hint",START+1_000);assertNull(policy.next(EPOCH,START+3_001,true,false));
        policy.offerPose(EPOCH+1,"Wrong recording",START+3_001);assertNull(policy.next(EPOCH,START+3_001,true,false));
        policy.offerPose(EPOCH,"Latest hint",START+4_000);LiveDirectionPolicy.Candidate pending=policy.next(EPOCH,START+4_000,true,false);
        policy.offerPose(EPOCH,"Corrected hint",START+4_001);assertFalse(policy.commit(pending,START+4_001));
        assertEquals("Corrected hint",policy.next(EPOCH,START+4_001,true,false).text);
        policy.offerPose(EPOCH,"Future observation",START+8_000);assertNull(policy.next(EPOCH,START+7_000,true,false));
        assertNull(policy.next(EPOCH,START-1,true,false));assertNull(policy.next(EPOCH,START+60_001,true,false));
        policy.clear();assertFalse(policy.commit(pending,START+4_001));assertNull(policy.next(EPOCH,START+4_002,true,true));
        policy.reset(EPOCH,START,20_000,"");assertFalse("Reusing an epoch cannot revive an old candidate",policy.commit(pending,START+4_001));
        LiveDirectionPolicy other=new LiveDirectionPolicy();other.reset(EPOCH,START,20_000,"");
        assertFalse("A matching epoch cannot transfer admission to another helper",other.commit(policy.next(EPOCH,START,true,false),START));
    }

    @Test public void finalBeatIsBoundedOptionalAndAlreadySpokenSequenceInstructionIsSkipped(){
        LiveDirectionPolicy policy=new LiveDirectionPolicy();policy.reset(EPOCH,START,6_000,"");
        assertTrue(policy.commit(policy.next(EPOCH,START,true,true),START));assertNull(policy.next(EPOCH,START+1_500,true,true));
        assertNull(policy.next(EPOCH,START+3_999,true,true));assertNull("Quiet-tail mode can exclude final cue",policy.next(EPOCH,START+4_000,true,false));
        LiveDirectionPolicy.Candidate hold=policy.next(EPOCH,START+4_000,true,true);assertEquals(LiveDirectionPolicy.Kind.HOLD,hold.kind);
        assertTrue(policy.commit(hold,START+4_000));assertNull(policy.next(EPOCH,START+5_000,true,true));
        policy.reset(EPOCH,START,4_000,"");assertTrue(policy.commit(policy.next(EPOCH,START,true,true),START));
        assertNull("Short planned takes do not get a final-hold cue",policy.next(EPOCH,START+3_000,true,true));
        policy.reset(EPOCH,START,6_000,"");assertTrue(policy.commit(policy.next(EPOCH,START,true,true),START));
        assertNull("A planned-end cue must not be offered after its target end",policy.next(EPOCH,START+6_001,true,true));
    }
}
