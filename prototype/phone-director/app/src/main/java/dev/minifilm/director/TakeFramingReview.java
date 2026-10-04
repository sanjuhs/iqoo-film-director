package dev.minifilm.director;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;

/** Explicit sparse cut review, not a quality score or temporal/semantic coverage analysis.
 * Requested source seeks are not exact decoded PTS; the nearest frame may lie outside the cut.
 * Pixels use an approximate center 9:16 crop, before exporter overlays/color effects.
 */
public final class TakeFramingReview implements AutoCloseable {
    public interface Listener {
        /** Main thread. Receiver owns this result and closes it after detaching displayed bitmaps. */
        void onResult(Result result, long elapsedMs);
        void onError(String message);
    }
    public static final class Selection {
        public final Uri uri;
        public final long durationMs, inMs, outMs;
        private Selection(Take take) {
            uri=take.uri;durationMs=take.durationMs;inMs=take.inMs;outMs=take.outMs;
        }
        public static Selection capture(Take take) {
            if(take==null||take.uri==null)throw new IllegalArgumentException("Choose a saved take to review.");
            String scheme=take.uri.getScheme();
            if("file".equals(scheme)){
                String path=take.uri.getPath();
                if(path==null||!path.startsWith("/")||path.length()<2
                        ||take.uri.getAuthority()!=null&&!take.uri.getAuthority().isEmpty()
                        ||take.uri.getQuery()!=null||take.uri.getFragment()!=null)
                    throw new IllegalArgumentException("Choose a valid local video file.");
            }else if("content".equals(scheme)){
                if(take.uri.getAuthority()==null||take.uri.getAuthority().isEmpty()||take.uri.getPath()==null||take.uri.getPath().isEmpty())
                    throw new IllegalArgumentException("Choose a valid local video document.");
            }else throw new IllegalArgumentException("Choose a video saved on your phone.");
            if(take.durationMs<=0||take.durationMs>180_000||take.inMs<0||take.outMs>take.durationMs
                    ||take.outMs<=take.inMs||take.outMs-take.inMs<250)
                throw new IllegalArgumentException("Review a cut of at least 0.25 seconds inside a source up to three minutes.");
            return new Selection(take);
        }
        public boolean matches(Take take) {
            return take!=null&&uri.equals(take.uri)&&durationMs==take.durationMs&&inMs==take.inMs&&outMs==take.outMs;
        }
    }
    public static final class Moment {
        public final long requestedTimeMs;
        public final String label, reason;
        /** Owned by Result; do not recycle independently or retain after Result.close(). */
        public final Bitmap thumbnail;
        Moment(long time,String label,String reason,Bitmap thumbnail) {
            if(label==null||reason==null||thumbnail==null||thumbnail.isRecycled()
                    ||thumbnail.getWidth()<1||thumbnail.getHeight()<1||Math.max(thumbnail.getWidth(),thumbnail.getHeight())>256)
                throw new IllegalArgumentException("A bounded frame review is required.");
            requestedTimeMs=time;this.label=label;this.reason=reason;this.thumbnail=thumbnail;
        }
    }
    public static final class Result implements AutoCloseable {
        public final Selection selection;
        public final List<Moment> moments;
        // Package-private synthetic result seam transfers bitmap ownership only on successful construction.
        Result(Selection selection,List<Moment> moments) {
            if(selection==null||moments==null||moments.size()!=3)throw new IllegalArgumentException("Review three cut moments.");
            long[] times=requestedTimes(selection);
            for(int i=0;i<3;i++)if(moments.get(i)==null||moments.get(i).requestedTimeMs!=times[i])
                throw new IllegalArgumentException("Frame moments must match this selected cut.");
            this.selection=selection;this.moments=Collections.unmodifiableList(new ArrayList<>(moments));
        }
        @Override public synchronized void close(){for(Moment moment:moments)if(!moment.thumbnail.isRecycled())moment.thumbnail.recycle();}
    }
    /** Instrumentation may supply synthetic pixels/results; production always executes actual decode+pose. */
    interface Inspector { Result inspect(Selection selection,BooleanSupplier cancelled)throws Exception; }
    private final Context context;
    private final Inspector inspector;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Object lifecycle=new Object();
    private long generation;
    private boolean closed;
    private Request active;
    private static final class Request {
        final long generation;
        final Selection selection;
        final Listener listener;
        volatile boolean cancelled;
        Request(long generation,Selection selection,Listener listener){this.generation=generation;this.selection=selection;this.listener=listener;}
    }
    public TakeFramingReview(Context context){this(context,null);}
    TakeFramingReview(Context context,Inspector inspector){
        if(context==null)throw new IllegalArgumentException("An app context is required.");
        this.context=context.getApplicationContext();this.inspector=inspector==null?this::inspectPixels:inspector;
    }
    public void inspect(Selection selection,Listener listener){
        if(listener==null)throw new IllegalArgumentException("A frame review listener is required.");
        synchronized(lifecycle){
            if(closed)return;
            if(selection==null){error(listener,"Choose a saved take to review.",generation);return;}
            if(active!=null){error(listener,"The earlier frame review is still running or stopping.",generation);return;}
            Request request=new Request(++generation,selection,listener);active=request;
            worker.execute(()->run(request));
        }
    }
    /** True until cancelled worker cleanup or queued-result recycling finishes. */
    public boolean isRunning(){synchronized(lifecycle){return active!=null;}}
    public void cancel(){synchronized(lifecycle){++generation;if(active!=null)active.cancelled=true;}}
    @Override public void close(){synchronized(lifecycle){closed=true;cancel();worker.shutdown();}}
    private void run(Request request){
        long began=SystemClock.elapsedRealtime();Result result=null;String failure=null;
        try{
            if(request.cancelled)return;
            result=inspector.inspect(request.selection,()->request.cancelled);
            if(result==null||result.selection!=request.selection)throw new IllegalStateException("Invalid review result");
        }catch(Exception | LinkageError invalid){if(result!=null){result.close();result=null;}failure="This take could not be reviewed. Check its cut points and local video access.";}
        finally{
            synchronized(lifecycle){
                if(!current(request)){
                    if(result!=null)result.close();
                    if(active==request)active=null;
                    result=null;
                }
            }
        }
        final Result ready=result;final String error=failure;
        main.post(()->{
            synchronized(lifecycle){
                if(!current(request)){if(ready!=null)ready.close();if(active==request)active=null;return;}
                active=null;
                if(ready!=null)request.listener.onResult(ready,SystemClock.elapsedRealtime()-began);
                else request.listener.onError(error==null?"This take could not be reviewed.":error);
            }
        });
    }
    private boolean current(Request request){return !closed&&!request.cancelled&&active==request&&generation==request.generation;}
    private void error(Listener listener,String message,long expected){main.post(()->{synchronized(lifecycle){if(!closed&&generation==expected)listener.onError(message);}});}
    static long[] requestedTimes(Selection selection){long length=selection.outMs-selection.inMs;return new long[]{selection.inMs+length/4,selection.inMs+length/2,selection.inMs+3*length/4};}
    private static void check(BooleanSupplier cancelled)throws InterruptedException{if(cancelled.getAsBoolean())throw new InterruptedException("Cancelled frame review");}
    private Result inspectPixels(Selection selection,BooleanSupplier cancelled)throws Exception{
        MediaMetadataRetriever reader=new MediaMetadataRetriever();List<Moment> moments=new ArrayList<>();boolean transferred=false;
        try{
            check(cancelled);
            if("file".equals(selection.uri.getScheme()))reader.setDataSource(selection.uri.getPath());else reader.setDataSource(context,selection.uri);
            if(!"yes".equals(reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)))throw new IllegalArgumentException("No video");
            long duration=Long.parseLong(reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
            if(duration<=0||duration>180_000||selection.outMs>duration)throw new IllegalArgumentException("Source bounds changed");
            for(long time:requestedTimes(selection)){
                check(cancelled);Bitmap decoded=null,crop=null,thumbnail=null;
                try{
                    decoded=reader.getScaledFrameAtTime(time*1000L,MediaMetadataRetriever.OPTION_CLOSEST,512,512);
                    if(decoded==null)throw new IllegalStateException("No frame");check(cancelled);
                    crop=centerCrop(decoded);byte[] rgb=rgb(crop);
                    FramePoseFraming.Result framing=FramePoseFraming.inspect(rgb,crop.getWidth(),crop.getHeight(),cancelled);
                    check(cancelled);
                    thumbnail=Bitmap.createScaledBitmap(crop,144,256,true);
                    moments.add(new Moment(time,framing.label,framing.reason,thumbnail));thumbnail=null;
                }finally{
                    if(thumbnail!=null)thumbnail.recycle();if(crop!=null)crop.recycle();if(decoded!=null)decoded.recycle();
                }
            }
            Result result=new Result(selection,moments);transferred=true;return result;
        }finally{
            if(!transferred)for(Moment moment:moments)if(!moment.thumbnail.isRecycled())moment.thumbnail.recycle();
            try{reader.release();}catch(Exception ignored){}
        }
    }
    /** Approximate Presentation's center scale-to-fill, upright source pixels → 288x512, before overlays. */
    static Bitmap centerCrop(Bitmap source){
        if(source==null||source.isRecycled()||source.getWidth()<1||source.getHeight()<1||Math.max(source.getWidth(),source.getHeight())>512)
            throw new IllegalArgumentException("Use a decoded frame bounded to 512 pixels.");
        Bitmap target=Bitmap.createBitmap(288,512,Bitmap.Config.ARGB_8888);
        float scale=Math.max(288f/source.getWidth(),512f/source.getHeight());
        float width=source.getWidth()*scale,height=source.getHeight()*scale;
        Canvas canvas=new Canvas(target);canvas.drawColor(android.graphics.Color.BLACK);
        canvas.drawBitmap(source,null,new RectF((288-width)/2,(512-height)/2,(288+width)/2,(512+height)/2),new Paint(Paint.FILTER_BITMAP_FLAG));
        return target;
    }
    private static byte[] rgb(Bitmap bitmap){
        int[] pixels=new int[bitmap.getWidth()*bitmap.getHeight()];bitmap.getPixels(pixels,0,bitmap.getWidth(),0,0,bitmap.getWidth(),bitmap.getHeight());
        byte[] rgb=new byte[pixels.length*3];for(int i=0;i<pixels.length;i++){int color=pixels[i],alpha=color>>>24;
            rgb[3*i]=(byte)(((color>>>16)&255)*alpha/255);rgb[3*i+1]=(byte)(((color>>>8)&255)*alpha/255);rgb[3*i+2]=(byte)((color&255)*alpha/255);}
        return rgb;
    }
}
