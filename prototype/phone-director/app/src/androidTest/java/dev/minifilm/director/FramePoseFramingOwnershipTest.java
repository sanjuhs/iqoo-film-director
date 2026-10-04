package dev.minifilm.director;

import static org.junit.Assert.*;
import android.graphics.Bitmap;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.pose.Pose;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Controlled Android tasks exercise real ownership code with isolated gates; no model or media access. */
@RunWith(AndroidJUnit4.class)
public final class FramePoseFramingOwnershipTest {
    private static final byte[] RGB=new byte[2*2*3];

    @Test(timeout=8_000) public void cancelKeepsPendingImageClientAndGateUntilLateCompletionThenAllowsReuse() throws Exception {
        FramePoseFraming.Gate gate=new FramePoseFraming.Gate();TaskCompletionSource<Pose> task=new TaskCompletionSource<>();
        Factory factory=new Factory(task.getTask());AtomicBoolean cancelled=new AtomicBoolean();ExecutorService worker=Executors.newSingleThreadExecutor();
        try{
            Future<FramePoseFraming.Result> result=worker.submit(()->inspect(factory,gate,cancelled,1000));
            assertTrue(factory.entered.await(2,TimeUnit.SECONDS));cancelled.set(true);
            assertReviewNeeded(result.get(2,TimeUnit.SECONDS));Client pending=factory.clients.get(0);
            assertFalse(task.getTask().isComplete());assertNotNull(pending.bitmap);assertFalse(pending.bitmap.isRecycled());assertEquals(0,pending.closes.get());assertEquals(0,gate.availablePermits());
            assertReviewNeeded(inspect(factory,gate,new AtomicBoolean(),40));assertEquals("Second call cannot construct a backend",1,factory.creates.get());
            task.setException(new IllegalStateException("labelled synthetic completion"));
            assertReleased(gate,pending);assertFalse(task.trySetException(new IllegalStateException("duplicate synthetic completion")));assertEquals(1,pending.closes.get());
            assertReviewNeeded(inspect(factory,gate,new AtomicBoolean(),100));assertEquals(2,factory.creates.get());assertReleased(gate,factory.clients.get(1));
        }finally{task.trySetException(new IllegalStateException("synthetic test cleanup"));worker.shutdownNow();assertTrue(worker.awaitTermination(2,TimeUnit.SECONDS));}
    }

    @Test(timeout=8_000) public void timeoutDoesNotReleaseBackendAndConcurrentCallerCreatesNothingUntilCanceledTaskCompletes() throws Exception {
        FramePoseFraming.Gate gate=new FramePoseFraming.Gate();CancellationTokenSource cancellation=new CancellationTokenSource();
        TaskCompletionSource<Pose> task=new TaskCompletionSource<>(cancellation.getToken());Factory factory=new Factory(task.getTask());
        try{
            assertReviewNeeded(inspect(factory,gate,new AtomicBoolean(),200));assertEquals(1,factory.creates.get());Client pending=factory.clients.get(0);
            assertFalse(task.getTask().isComplete());assertFalse(pending.bitmap.isRecycled());assertEquals(0,pending.closes.get());assertEquals(0,gate.availablePermits());
            ExecutorService worker=Executors.newSingleThreadExecutor();try{
                Future<FramePoseFraming.Result> waiting=worker.submit(()->inspect(factory,gate,new AtomicBoolean(),40));
                assertReviewNeeded(waiting.get(2,TimeUnit.SECONDS));assertEquals(1,factory.creates.get());assertFalse(pending.bitmap.isRecycled());
            }finally{worker.shutdownNow();assertTrue(worker.awaitTermination(2,TimeUnit.SECONDS));}
            cancellation.cancel();awaitReleased(gate,pending);assertTrue(task.getTask().isCanceled());
        }finally{cancellation.cancel();}
    }

    @Test(timeout=8_000) public void waiterCancellationAndSetupFailuresCannotStartExtraBackendOrLeakAdmission() throws Exception {
        FramePoseFraming.Gate gate=new FramePoseFraming.Gate();TaskCompletionSource<Pose> task=new TaskCompletionSource<>();Factory holder=new Factory(task.getTask());
        AtomicBoolean waiterCancelled=new AtomicBoolean();AtomicInteger waiterPolls=new AtomicInteger();CountDownLatch waited=new CountDownLatch(1);Factory waitingFactory=new Factory(Tasks.forException(new IllegalStateException("synthetic ready failure")));
        ExecutorService worker=Executors.newSingleThreadExecutor();
        try{
            assertReviewNeeded(inspect(holder,gate,new AtomicBoolean(),200));Client pending=holder.clients.get(0);
            Future<FramePoseFraming.Result> waiting=worker.submit(()->FramePoseFraming.inspectWith(RGB,2,2,()->{if(waiterPolls.incrementAndGet()>=3)waited.countDown();return waiterCancelled.get();},waitingFactory,gate,1000));
            assertTrue("Waiter must poll while admission is held",waited.await(2,TimeUnit.SECONDS));waiterCancelled.set(true);assertReviewNeeded(waiting.get(2,TimeUnit.SECONDS));assertEquals(0,waitingFactory.creates.get());assertEquals(0,gate.availablePermits());assertFalse(pending.bitmap.isRecycled());
            task.setException(new IllegalStateException("synthetic holder release"));assertReleased(gate,pending);
            AtomicInteger creationAttempts=new AtomicInteger();FramePoseFraming.Factory badFactory=()->{creationAttempts.incrementAndGet();throw new IllegalStateException("synthetic setup failure");};
            assertReviewNeeded(FramePoseFraming.inspectWith(RGB,2,2,()->false,badFactory,gate,100));assertEquals(1,creationAttempts.get());assertEquals(1,gate.availablePermits());
            Factory processFailure=new Factory(Tasks.forException(new IllegalStateException("unused task")));processFailure.failProcess=true;
            assertReviewNeeded(inspect(processFailure,gate,new AtomicBoolean(),100));assertReleased(gate,processFailure.clients.get(0));
            AtomicBoolean preCancelled=new AtomicBoolean(true);assertReviewNeeded(inspect(waitingFactory,gate,preCancelled,100));assertEquals(0,waitingFactory.creates.get());assertEquals(1,gate.availablePermits());
        }finally{task.trySetException(new IllegalStateException("synthetic test cleanup"));worker.shutdownNow();assertTrue(worker.awaitTermination(2,TimeUnit.SECONDS));}
    }

    @Test(timeout=8_000) public void alreadyCompletedSuccessErrorAndCancelledTasksDisposeOnceWithoutHoldingGate() throws Exception {
        CancellationTokenSource cancellation=new CancellationTokenSource();TaskCompletionSource<Pose> cancelledTask=new TaskCompletionSource<>(cancellation.getToken());cancellation.cancel();awaitComplete(cancelledTask.getTask());
        // Null is an explicitly malformed successful payload, not a fabricated detected pose.
        // It still exercises completion-before-listener ownership; real poses are covered by native tests.
        List<Task<Pose>> tasks=java.util.Arrays.asList(Tasks.forResult((Pose)null),Tasks.forException(new IllegalStateException("synthetic ready error")),cancelledTask.getTask());
        for(Task<Pose> task:tasks){FramePoseFraming.Gate gate=new FramePoseFraming.Gate();Factory factory=new Factory(task);
            assertReviewNeeded(inspect(factory,gate,new AtomicBoolean(),100));assertEquals(1,factory.creates.get());assertReleased(gate,factory.clients.get(0));
            assertReviewNeeded(inspect(factory,gate,new AtomicBoolean(),100));assertEquals(2,factory.creates.get());assertReleased(gate,factory.clients.get(1));}
    }

    @Test(timeout=8_000) public void cleanupFailureKeepsIsolatedGateClosedAndCannotPoisonOtherGate(){
        FramePoseFraming.Gate isolated=new FramePoseFraming.Gate();TaskCompletionSource<Pose> task=new TaskCompletionSource<>();Factory broken=new Factory(task.getTask());broken.failClose=true;
        try{
            assertReviewNeeded(inspect(broken,isolated,new AtomicBoolean(),200));Client pending=broken.clients.get(0);assertEquals(0,pending.closes.get());
            task.setException(new IllegalStateException("synthetic completed task"));assertEquals(1,pending.closes.get());assertEquals("Unconfirmed cleanup fails closed",0,isolated.availablePermits());
            assertReviewNeeded(inspect(broken,isolated,new AtomicBoolean(),30));assertEquals(1,broken.creates.get());assertEquals(1,pending.closes.get());
            FramePoseFraming.Gate independent=new FramePoseFraming.Gate();Factory healthy=new Factory(Tasks.forException(new IllegalStateException("synthetic other gate")));
            assertReviewNeeded(inspect(healthy,independent,new AtomicBoolean(),100));assertReleased(independent,healthy.clients.get(0));
            // This isolated gate is intentionally never released. Production's static gate is never touched.
        }finally{task.trySetException(new IllegalStateException("synthetic test cleanup"));}
    }

    private static FramePoseFraming.Result inspect(Factory factory,FramePoseFraming.Gate gate,AtomicBoolean cancelled,long budget){return FramePoseFraming.inspectWith(RGB,2,2,cancelled::get,factory,gate,budget);}
    private static void assertReviewNeeded(FramePoseFraming.Result result){assertEquals("review needed",result.label);assertEquals(0,result.confidence,0);assertEquals(0,result.visibleLandmarks);assertNotNull(result.reason);assertFalse(result.reason.isEmpty());}
    private static void assertReleased(FramePoseFraming.Gate gate,Client client){assertEquals(1,client.closes.get());assertNotNull(client.bitmap);assertTrue(client.bitmap.isRecycled());assertEquals(1,gate.availablePermits());}
    private static void awaitComplete(Task<Pose> task)throws Exception{long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);while(!task.isComplete()&&System.nanoTime()<until)Thread.sleep(5);assertTrue(task.isComplete());}
    private static void awaitReleased(FramePoseFraming.Gate gate,Client client)throws Exception{long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);while(gate.availablePermits()!=1&&System.nanoTime()<until)Thread.sleep(5);assertReleased(gate,client);}
    private static final class Factory implements FramePoseFraming.Factory {
        final Task<Pose> task;final AtomicInteger creates=new AtomicInteger();final CountDownLatch entered=new CountDownLatch(1);final List<Client> clients=new CopyOnWriteArrayList<>();
        volatile boolean failProcess,failClose;
        Factory(Task<Pose> task){this.task=task;}
        public FramePoseFraming.Client create(){creates.incrementAndGet();Client client=new Client(this);clients.add(client);return client;}
    }
    private static final class Client implements FramePoseFraming.Client {
        final Factory factory;final AtomicInteger closes=new AtomicInteger();volatile Bitmap bitmap;
        Client(Factory factory){this.factory=factory;}
        public Task<Pose> process(Bitmap input){bitmap=input;factory.entered.countDown();if(factory.failProcess)throw new IllegalStateException("synthetic process setup failure");return factory.task;}
        public void close(){closes.incrementAndGet();if(factory.failClose)throw new IllegalStateException("synthetic unconfirmed close");}
    }
}
