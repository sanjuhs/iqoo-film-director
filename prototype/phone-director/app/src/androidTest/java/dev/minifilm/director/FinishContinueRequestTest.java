package dev.minifilm.director;

import static org.junit.Assert.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;

/** One-shot authorization bookkeeping only, not recording or container validation. */
@RunWith(AndroidJUnit4.class)
public final class FinishContinueRequestTest {
    @Test public void stopIsOnceOnlyAndSavedAcknowledgementRequiresExactStartedSession(){
        Object owner=new Object();AtomicInteger stops=new AtomicInteger();
        MainActivity.FinishContinueRequest request=new MainActivity.FinishContinueRequest(owner,5,9,12);
        assertFalse(request.acceptSaved(owner,5,9,12,5746));
        assertTrue(request.requestStop(stops::incrementAndGet));assertFalse(request.requestStop(stops::incrementAndGet));assertEquals(1,stops.get());
        assertFalse(request.acceptSaved(new Object(),5,9,12,5746));assertFalse(request.acceptSaved(owner,6,9,12,5746));
        assertFalse(request.acceptSaved(owner,5,10,12,5746));assertFalse(request.acceptSaved(owner,5,9,13,5746));
        assertFalse(request.acceptSaved(owner,5,9,12,0));assertFalse(request.acceptSaved(owner,5,9,12,300));
        assertTrue(request.acceptSaved(owner,5,9,12,5746));assertFalse(request.acceptSaved(owner,5,9,12,5746));
    }
    @Test public void cancellationCannotAuthorizeLaterAcknowledgementOrAReplacementRequest(){
        Object owner=new Object();AtomicInteger stops=new AtomicInteger();
        MainActivity.FinishContinueRequest before=new MainActivity.FinishContinueRequest(owner,2,3,1);before.cancel();
        assertFalse(before.requestStop(stops::incrementAndGet));assertEquals(0,stops.get());
        MainActivity.FinishContinueRequest old=new MainActivity.FinishContinueRequest(owner,2,3,2);old.requestStop(stops::incrementAndGet);old.cancel();
        MainActivity.FinishContinueRequest next=new MainActivity.FinishContinueRequest(owner,2,4,3);next.requestStop(stops::incrementAndGet);
        assertFalse(old.acceptSaved(owner,2,3,2,1000));assertFalse(next.acceptSaved(owner,2,3,2,1000));
        assertTrue(next.acceptSaved(owner,2,4,3,1000));assertEquals(2,stops.get());
    }
    @Test public void throwingStopIsNotRetriedAndCancellationFailsClosed(){
        Object owner=new Object();AtomicInteger calls=new AtomicInteger();
        MainActivity.FinishContinueRequest request=new MainActivity.FinishContinueRequest(owner,1,1,1);
        try{request.requestStop(()->{calls.incrementAndGet();throw new IllegalStateException("Synthetic failure");});fail();}catch(IllegalStateException expected){}
        request.cancel();assertFalse(request.requestStop(calls::incrementAndGet));assertFalse(request.acceptSaved(owner,1,1,1,1000));assertEquals(1,calls.get());
    }
}
