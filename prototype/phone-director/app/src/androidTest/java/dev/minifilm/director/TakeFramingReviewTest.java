package dev.minifilm.director;

import static org.junit.Assert.*;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Looper;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Model-free boundary/pixel geometry/worker ownership checks. Actual decoded pose is tested separately. */
@RunWith(AndroidJUnit4.class)
public final class TakeFramingReviewTest {
    @Test public void selectionCopiesCutIdentityAndRejectsBadSourcesOrBoundsBeforeAnyRead(){
        Take take=take();TakeFramingReview.Selection selection=TakeFramingReview.Selection.capture(take);
        assertEquals(take.uri,selection.uri);assertEquals(9000,selection.durationMs);assertEquals(1000,selection.inMs);assertEquals(5000,selection.outMs);
        assertArrayEquals(new long[]{2000,3000,4000},TakeFramingReview.requestedTimes(selection));assertTrue(selection.matches(take));
        take.title="Edited heading";take.caption="Edited typography";take.selected=false;assertTrue(selection.matches(take));
        take.inMs=1100;assertFalse(selection.matches(take));take.inMs=1000;take.outMs=4900;assertFalse(selection.matches(take));
        take.outMs=5000;take.durationMs=8999;assertFalse(selection.matches(take));take.durationMs=9000;
        take.uri=Uri.parse("content://synthetic.review/another");assertFalse(selection.matches(take));assertFalse(selection.matches(null));
        rejected(null);Take invalid=take();invalid.uri=null;rejected(invalid);
        for(String uri:new String[]{"https://example.test/private.mp4","file:relative","file:///","file://remote.example/path.mp4","content:/missing-authority","content://synthetic.review"}){invalid=take();invalid.uri=Uri.parse(uri);rejected(invalid);}
        invalid=take();invalid.durationMs=180001;rejected(invalid);invalid=take();invalid.inMs=-1;rejected(invalid);
        invalid=take();invalid.outMs=9001;rejected(invalid);invalid=take();invalid.outMs=invalid.inMs+249;rejected(invalid);
        invalid=take();invalid.outMs=invalid.inMs;rejected(invalid);invalid=take();invalid.durationMs=0;rejected(invalid);
        Take shortest=take();shortest.inMs=0;shortest.outMs=250;
        assertArrayEquals(new long[]{62,125,187},TakeFramingReview.requestedTimes(TakeFramingReview.Selection.capture(shortest)));
    }

    @Test public void centerCropFillsPortraitFromLandscapeOrTallPixelsWithoutStretchingWholeSource(){
        Bitmap landscape=Bitmap.createBitmap(320,180,Bitmap.Config.ARGB_8888),tall=Bitmap.createBitmap(180,512,Bitmap.Config.ARGB_8888);
        Bitmap output=null,tallOutput=null,oversized=null;
        try{
            for(int y=0;y<180;y++)for(int x=0;x<320;x++)landscape.setPixel(x,y,x<100?Color.RED:x>=220?Color.BLUE:Color.GREEN);
            for(int y=0;y<512;y++)for(int x=0;x<180;x++)tall.setPixel(x,y,y<90?Color.RED:y>=422?Color.BLUE:Color.GREEN);
            output=TakeFramingReview.centerCrop(landscape);tallOutput=TakeFramingReview.centerCrop(tall);
            for(Bitmap crop:Arrays.asList(output,tallOutput)){
                assertEquals(288,crop.getWidth());assertEquals(512,crop.getHeight());assertNotSame(landscape,crop);assertNotSame(tall,crop);
                assertEquals(Color.GREEN,crop.getPixel(2,2));assertEquals(Color.GREEN,crop.getPixel(285,509));assertEquals(Color.GREEN,crop.getPixel(144,256));
            }
            assertEquals(Color.RED,landscape.getPixel(0,0));assertEquals(Color.BLUE,landscape.getPixel(319,179));assertEquals(Color.RED,tall.getPixel(0,0));
            oversized=Bitmap.createBitmap(513,1,Bitmap.Config.ARGB_8888);try{TakeFramingReview.centerCrop(oversized);fail("Oversized decode");}catch(IllegalArgumentException expected){}
            landscape.recycle();try{TakeFramingReview.centerCrop(landscape);fail("Disposed decode");}catch(IllegalArgumentException expected){}
        }finally{if(!landscape.isRecycled())landscape.recycle();tall.recycle();if(output!=null)output.recycle();if(tallOutput!=null)tallOutput.recycle();if(oversized!=null)oversized.recycle();}
    }

    @Test(timeout=15_000) public void cancelRetainsRunningUntilCleanupRecyclesLateResultAndAllowsIndependentReuse() throws Exception {
        TakeFramingReview.Selection selection=TakeFramingReview.Selection.capture(take());CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1),complete=new CountDownLatch(1);
        AtomicInteger calls=new AtomicInteger(),cancelledCallbacks=new AtomicInteger();AtomicReference<TakeFramingReview.Result> stale=new AtomicReference<>(),delivered=new AtomicReference<>();
        TakeFramingReview reviewer=new TakeFramingReview(context(),(snapshot,cancelled)->{
            int call=calls.incrementAndGet();if(call==1){entered.countDown();assertTrue(release.await(5,TimeUnit.SECONDS));assertTrue(cancelled.getAsBoolean());
                TakeFramingReview.Result result=result(snapshot);stale.set(result);return result;}return result(snapshot);
        });
        try{
            reviewer.inspect(selection,new TakeFramingReview.Listener(){public void onResult(TakeFramingReview.Result result,long elapsed){cancelledCallbacks.incrementAndGet();result.close();}public void onError(String error){cancelledCallbacks.incrementAndGet();}});
            assertTrue(entered.await(3,TimeUnit.SECONDS));reviewer.cancel();assertTrue("Cancelled worker has not released pixels",reviewer.isRunning());
            release.countDown();awaitStopped(reviewer);idle();assertEquals(0,cancelledCallbacks.get());assertNotNull(stale.get());for(TakeFramingReview.Moment m:stale.get().moments)assertTrue(m.thumbnail.isRecycled());
            reviewer.inspect(selection,new TakeFramingReview.Listener(){public void onResult(TakeFramingReview.Result result,long elapsed){assertSame(Looper.getMainLooper(),Looper.myLooper());assertFalse(reviewer.isRunning());assertSame(selection,result.selection);delivered.set(result);complete.countDown();}public void onError(String error){complete.countDown();fail(error);}});
            assertTrue(complete.await(3,TimeUnit.SECONDS));assertEquals(2,calls.get());assertNotNull(delivered.get());assertEquals(3,delivered.get().moments.size());
            for(TakeFramingReview.Moment m:delivered.get().moments)assertFalse(m.thumbnail.isRecycled());
            try{delivered.get().moments.clear();fail("Immutable moments");}catch(UnsupportedOperationException expected){}
            delivered.get().close();delivered.get().close();for(TakeFramingReview.Moment m:delivered.get().moments)assertTrue(m.thumbnail.isRecycled());
        }finally{release.countDown();reviewer.close();if(delivered.get()!=null)delivered.get().close();}
    }

    @Test(timeout=15_000) public void closeSuppressesQueuedValidationAndLateWorkerPublicationAndDoesNotReopen(){
        TakeFramingReview.Selection selection=TakeFramingReview.Selection.capture(take());AtomicInteger callbacks=new AtomicInteger(),calls=new AtomicInteger();
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);AtomicReference<TakeFramingReview.Result> late=new AtomicReference<>();
        TakeFramingReview reviewer=new TakeFramingReview(context(),(snapshot,cancelled)->{calls.incrementAndGet();entered.countDown();assertTrue(release.await(5,TimeUnit.SECONDS));TakeFramingReview.Result result=result(snapshot);late.set(result);return result;});
        TakeFramingReview.Listener listener=new TakeFramingReview.Listener(){public void onResult(TakeFramingReview.Result r,long ms){callbacks.incrementAndGet();r.close();}public void onError(String e){callbacks.incrementAndGet();}};
        try{
            reviewer.inspect(selection,listener);try{assertTrue(entered.await(3,TimeUnit.SECONDS));}catch(InterruptedException e){throw new AssertionError(e);}
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{reviewer.inspect(null,listener);reviewer.close();reviewer.close();reviewer.inspect(selection,listener);});
            release.countDown();try{awaitStopped(reviewer);}catch(Exception e){throw new AssertionError(e);}idle();assertEquals(1,calls.get());assertEquals(0,callbacks.get());assertNotNull(late.get());
            for(TakeFramingReview.Moment m:late.get().moments)assertTrue(m.thumbnail.isRecycled());
        }finally{release.countDown();reviewer.close();}
        TakeFramingReview invalidOnly=new TakeFramingReview(context(),(snapshot,cancelled)->{calls.incrementAndGet();return result(snapshot);});
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{invalidOnly.inspect(null,listener);invalidOnly.close();invalidOnly.inspect(selection,listener);});idle();assertEquals(1,calls.get());assertEquals(0,callbacks.get());
    }

    @Test(timeout=10_000) public void malformedInjectedResultIsDisposedAndErrorIsSafeMainThreadBeforeReuse() throws Exception {
        TakeFramingReview.Selection selection=TakeFramingReview.Selection.capture(take());Take other=take();other.inMs=500;
        AtomicReference<TakeFramingReview.Result> malformed=new AtomicReference<>();CountDownLatch error=new CountDownLatch(1);AtomicReference<String> message=new AtomicReference<>();
        TakeFramingReview reviewer=new TakeFramingReview(context(),(snapshot,cancelled)->{TakeFramingReview.Result value=result(TakeFramingReview.Selection.capture(other));malformed.set(value);return value;});
        try{
            reviewer.inspect(selection,new TakeFramingReview.Listener(){public void onResult(TakeFramingReview.Result r,long ms){r.close();fail("Wrong selection delivered");}public void onError(String e){assertSame(Looper.getMainLooper(),Looper.myLooper());assertFalse(reviewer.isRunning());message.set(e);error.countDown();}});
            assertTrue(error.await(3,TimeUnit.SECONDS));assertNotNull(message.get());assertFalse(message.get().contains("synthetic.review"));for(TakeFramingReview.Moment m:malformed.get().moments)assertTrue(m.thumbnail.isRecycled());
        }finally{reviewer.close();}
    }
    private static Take take(){Take take=new Take(Uri.parse("content://synthetic.review/labelled-not-read"),"synthetic","Synthetic","",9000);take.inMs=1000;take.outMs=5000;return take;}
    private static void rejected(Take take){try{TakeFramingReview.Selection.capture(take);fail("Bad selection accepted");}catch(IllegalArgumentException expected){assertNotNull(expected.getMessage());}}
    private static TakeFramingReview.Result result(TakeFramingReview.Selection selection){List<TakeFramingReview.Moment> moments=new ArrayList<>();for(long time:TakeFramingReview.requestedTimes(selection)){Bitmap thumb=Bitmap.createBitmap(144,256,Bitmap.Config.ARGB_8888);moments.add(new TakeFramingReview.Moment(time,"review needed","labelled synthetic ownership fixture",thumb));}return new TakeFramingReview.Result(selection,moments);}
    private static void awaitStopped(TakeFramingReview reviewer)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);while(reviewer.isRunning()&&System.nanoTime()<end)Thread.sleep(10);assertFalse(reviewer.isRunning());}
    private static void idle(){InstrumentationRegistry.getInstrumentation().waitForIdleSync();}
    private static Context context(){return InstrumentationRegistry.getInstrumentation().getTargetContext();}
}
