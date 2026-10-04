package dev.minifilm.director;

import android.Manifest;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.core.content.FileProvider;
import androidx.lifecycle.Lifecycle;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Field;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Geometry/lifecycle checks on labelled, silent synthetic sources only. No playback,
 * recording, model, pixel-exact export parity, or real-creator benefit is claimed. */
@RunWith(AndroidJUnit4.class)
public final class PreviewCropUiTest {
    private Context context;
    private Map<String,?> preferences;
    private SourceProof normal, rotated;

    @Before public void verifySyntheticInputsAndDeniedCapture() throws Exception {
        context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        KeyguardManager keyguard=(KeyguardManager)context.getSystemService(Context.KEYGUARD_SERVICE);
        assertFalse("UI checks require the own unlocked emulator",keyguard!=null&&keyguard.isKeyguardLocked());
        denied(); preferences=new HashMap<>(context.getSharedPreferences("shoot",0).getAll());
        normal=new SourceProof(new File(context.getFilesDir(),"fixtures/preview-crop-landscape.mp4"),
                "a093ebb0015492d558bbb8c44b8c63e4f6d6067bfe34260596cd42bb53f87bde",307173,0,640,360);
        rotated=new SourceProof(new File(context.getFilesDir(),"fixtures/preview-crop-rotated.mp4"),
                // ffprobe describes CCW90; Android metadata describes equivalent CW270.
                "85aaebc2959775f8b6d0db637c9af517b5bef7def320b7d7698e75bbd1bf5c15",264444,270,360,640);
    }

    @After public void preserveSourcesPreferencesAndPermissions() throws Exception {
        if(normal!=null)normal.unchanged();if(rotated!=null)rotated.unchanged();
        if(context!=null){assertEquals(preferences,context.getSharedPreferences("shoot",0).getAll());denied();}
    }

    @Test(timeout=60_000) public void normalAndMetadataRotatedCutsUseClippedNineSixteenViewportWithReachableControls() throws Exception {
        for(SourceProof source:new SourceProof[]{normal,rotated}){
            try(ActivityScenario<PreviewActivity> scenario=ActivityScenario.launch(intent(source,true,true))){
                ready(scenario);
                scenario.onActivity(a->{assertCrop(a);assertPausedCut(a);});
                source.unchanged();
                Log.i("MiniFilmPreviewCrop",new JSONObject().put("case",source==normal?"normal":"metadata_rotated")
                        .put("cropMode",true).put("sourceRotationDegrees",source.rotation)
                        .put("rangeStartMs",500).put("rangeEndMs",2500).put("paused",true)
                        .put("geometryOnly",true).put("audioPlayed",false).put("sourceUnchanged",true).toString());
            }
        }
    }

    @Test(timeout=30_000) public void defaultFinalReelModeKeepsFitWithoutCropLabelOrViewport() {
        try(ActivityScenario<PreviewActivity> scenario=ActivityScenario.launch(intent(normal,false,false))){
            ready(scenario);
            scenario.onActivity(a->{
                PlayerView view=(PlayerView)field(a,"playerView");ExoPlayer player=(ExoPlayer)field(a,"player");
                assertEquals(AspectRatioFrameLayout.RESIZE_MODE_FIT,view.getResizeMode());
                assertNull(field(a,"cropViewport"));assertFalse((Boolean)field(a,"cropToReel"));
                assertNull(find(a.getWindow().getDecorView(),TextView.class,"Reel crop preview · framing only"));
                assertFalse(player.getPlayWhenReady());assertFalse(player.isPlaying());
                assertTrue(player.getDuration()>=2900&&player.getDuration()<=3100);
                assertVisible(find(a.getWindow().getDecorView(),Button.class,"‹  Back"));
            });
        }
    }

    @Test(timeout=45_000) public void cropBackgroundAndRecreationReleasePlayerAndRestorePausedCutPosition() {
        AtomicReference<PreviewActivity> oldActivity=new AtomicReference<>();
        AtomicReference<ExoPlayer> oldPlayer=new AtomicReference<>();
        try(ActivityScenario<PreviewActivity> scenario=ActivityScenario.launch(intent(rotated,true,true))){
            ready(scenario);scenario.onActivity(a->{oldActivity.set(a);oldPlayer.set((ExoPlayer)field(a,"player"));oldPlayer.get().seekTo(700);});
            idle();scenario.moveToState(Lifecycle.State.CREATED);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
                assertNull(field(oldActivity.get(),"player"));
                assertNull(((PlayerView)field(oldActivity.get(),"playerView")).getPlayer());
                long position=(Long)field(oldActivity.get(),"positionMs");assertTrue(position>=650&&position<=750);
            });
            scenario.moveToState(Lifecycle.State.RESUMED);ready(scenario);
            scenario.onActivity(a->{assertNotSame(oldPlayer.get(),field(a,"player"));assertCrop(a);assertPausedCut(a);
                assertTrue(((ExoPlayer)field(a,"player")).getCurrentPosition()>=650);});
            scenario.recreate();ready(scenario);
            scenario.onActivity(a->{assertNotSame(oldActivity.get(),a);assertCrop(a);assertPausedCut(a);
                long position=((ExoPlayer)field(a,"player")).getCurrentPosition();assertTrue(position>=650&&position<=750);});
        }
    }

    private Intent intent(SourceProof proof,boolean crop,boolean range){
        Uri uri=FileProvider.getUriForFile(context,context.getPackageName()+".files",proof.file);
        Intent intent=new Intent(context,PreviewActivity.class).setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if(crop)intent.putExtra(PreviewActivity.EXTRA_CROP_TO_REEL,true);
        if(range)intent.putExtra(PreviewActivity.EXTRA_START_MS,500L).putExtra(PreviewActivity.EXTRA_END_MS,2500L);
        return intent;
    }

    private static void assertCrop(PreviewActivity activity){
        PlayerView player=(PlayerView)field(activity,"playerView");
        FrameLayout viewport=(FrameLayout)field(activity,"cropViewport");assertNotNull(viewport);
        assertEquals(AspectRatioFrameLayout.RESIZE_MODE_ZOOM,player.getResizeMode());
        assertTrue(viewport.getClipChildren());assertTrue(viewport.getClipToPadding());
        assertEquals(9f/16f,viewport.getWidth()/(float)viewport.getHeight(),.005f);
        assertEquals(viewport.getWidth(),player.getWidth());assertEquals(viewport.getHeight(),player.getHeight());
        ViewGroup available=(ViewGroup)viewport.getParent();
        assertTrue(viewport.getWidth()<=available.getWidth());assertTrue(viewport.getHeight()<=available.getHeight());
        assertEquals(available.getWidth()/2f,viewport.getLeft()+viewport.getWidth()/2f,1.1f);
        assertEquals(available.getHeight()/2f,viewport.getTop()+viewport.getHeight()/2f,1.1f);
        Rect visible=new Rect();assertTrue(viewport.getGlobalVisibleRect(visible));
        assertEquals(viewport.getWidth(),visible.width());assertEquals(viewport.getHeight(),visible.height());
        View surface=player.getVideoSurfaceView();assertNotNull(surface);
        assertTrue("Landscape surface fills the crop height",surface.getHeight()>=viewport.getHeight()-2);
        assertTrue("Landscape surface is wider than the 9:16 viewport",surface.getWidth()>viewport.getWidth()*2);
        assertEquals(16f/9f,surface.getWidth()/(float)surface.getHeight(),.04f);
        Rect surfaceVisible=new Rect();assertTrue(surface.getGlobalVisibleRect(surfaceVisible));
        assertTrue("Visible video stays inside the clipping viewport",visible.contains(surfaceVisible));
        TextView label=find(activity.getWindow().getDecorView(),TextView.class,"Reel crop preview · framing only");
        assertNotNull(label);assertTrue(label.getText().toString().contains("Source audio is unchanged"));
        assertTrue(label.getText().toString().contains("Export text and color are not shown"));assertVisible(label);
        assertVisible(find(activity.getWindow().getDecorView(),Button.class,"‹  Back"));
        View play=player.findViewById(androidx.media3.ui.R.id.exo_play_pause);
        View progress=player.findViewById(androidx.media3.ui.R.id.exo_progress);
        assertVisible(play);assertVisible(progress);
    }

    private static void assertPausedCut(PreviewActivity activity){
        ExoPlayer player=(ExoPlayer)field(activity,"player");assertNotNull(player);
        assertEquals(Player.STATE_READY,player.getPlaybackState());assertFalse(player.getPlayWhenReady());assertFalse(player.isPlaying());
        assertEquals(500L,player.getCurrentMediaItem().clippingConfiguration.startPositionMs);
        assertEquals(2500L,player.getCurrentMediaItem().clippingConfiguration.endPositionMs);
        assertEquals(2000L,player.getDuration(),50L);
        assertNull(player.getPlayerError());
    }

    private static void ready(ActivityScenario<PreviewActivity> scenario){
        long until=SystemClock.elapsedRealtime()+15_000;AtomicBoolean ready=new AtomicBoolean();
        while(SystemClock.elapsedRealtime()<until){scenario.onActivity(a->{
            ExoPlayer player=(ExoPlayer)field(a,"player");
            if(player!=null&&player.getPlayerError()!=null)fail("Synthetic media preparation failed: "+player.getPlayerError().errorCode);
            FrameLayout viewport=(FrameLayout)field(a,"cropViewport");
            View surface=((PlayerView)field(a,"playerView")).getVideoSurfaceView();
            ready.set(player!=null&&player.getPlaybackState()==Player.STATE_READY&&surface!=null&&surface.getWidth()>0
                    &&(viewport==null||viewport.getWidth()>1&&viewport.getHeight()>1
                    &&surface.getHeight()>=viewport.getHeight()-2&&surface.getWidth()>viewport.getWidth()*2));
        });if(ready.get()){idle();awaitFullyVisibleControls(scenario);return;}SystemClock.sleep(50);}
        fail("Synthetic local player did not prepare and lay out in 15 seconds");
    }
    private static void awaitFullyVisibleControls(ActivityScenario<PreviewActivity> scenario){
        AtomicBoolean crop=new AtomicBoolean();
        scenario.onActivity(a->{crop.set(field(a,"cropViewport")!=null);if(crop.get())((PlayerView)field(a,"playerView")).showController();});
        if(!crop.get())return;
        // READY is a video state, not completion of Media3's controller translation.
        // Show once, then measure the actual unclipped controls on the main thread;
        // do not restart its show animation from the later geometry assertion.
        long until=SystemClock.elapsedRealtime()+2_000;AtomicBoolean visible=new AtomicBoolean();int stable=0;
        while(SystemClock.elapsedRealtime()<until){
            scenario.onActivity(a->{PlayerView player=(PlayerView)field(a,"playerView");
                visible.set(fullyVisible(player.findViewById(androidx.media3.ui.R.id.exo_play_pause))
                        &&fullyVisible(player.findViewById(androidx.media3.ui.R.id.exo_progress)));});
            stable=visible.get()?stable+1:0;
            if(stable>=2){scenario.onActivity(a->{PlayerView player=(PlayerView)field(a,"playerView");
                assertVisible(player.findViewById(androidx.media3.ui.R.id.exo_play_pause));
                assertVisible(player.findViewById(androidx.media3.ui.R.id.exo_progress));});return;}
            SystemClock.sleep(50);
        }
        fail("Crop play and scrub controls never became fully visible within 2 seconds");
    }
    private static void idle(){InstrumentationRegistry.getInstrumentation().waitForIdleSync();}
    private void denied(){assertEquals(PackageManager.PERMISSION_DENIED,context.checkSelfPermission(Manifest.permission.CAMERA));assertEquals(PackageManager.PERMISSION_DENIED,context.checkSelfPermission(Manifest.permission.RECORD_AUDIO));}
    private static Object field(Object object,String name){try{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}catch(Exception e){throw new AssertionError(e);}}
    private static <T extends View>T find(View root,Class<T> type,String text){
        if(type.isInstance(root)&&(text==null||root instanceof TextView&&((TextView)root).getText().toString().startsWith(text)))return type.cast(root);
        if(root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int i=0;i<group.getChildCount();i++){T found=find(group.getChildAt(i),type,text);if(found!=null)return found;}}
        return null;
    }
    private static void assertVisible(View view){assertNotNull(view);Rect rect=new Rect();assertTrue(view.isShown());assertTrue(view.getGlobalVisibleRect(rect));assertEquals(view.getWidth(),rect.width());assertEquals(view.getHeight(),rect.height());}
    private static boolean fullyVisible(View view){Rect rect=new Rect();return view!=null&&view.getWidth()>0&&view.getHeight()>0&&view.isShown()&&view.getGlobalVisibleRect(rect)&&view.getWidth()==rect.width()&&view.getHeight()==rect.height();}
    private static String hash(File file)throws Exception{MessageDigest digest=MessageDigest.getInstance("SHA-256");try(FileInputStream input=new FileInputStream(file)){byte[] buffer=new byte[8192];int n;while((n=input.read(buffer))!=-1)digest.update(buffer,0,n);}StringBuilder out=new StringBuilder();for(byte b:digest.digest())out.append(String.format(java.util.Locale.US,"%02x",b&255));return out.toString();}
    private static final class SourceProof{
        final File file;final String sha;final long bytes,mtime;final int rotation;
        SourceProof(File file,String sha,long expectedBytes,int rotation,int width,int height)throws Exception{
            this.file=file;this.sha=sha;this.rotation=rotation;assertTrue(file.isFile());assertEquals(expectedBytes,file.length());assertEquals(sha,hash(file));bytes=file.length();mtime=file.lastModified();
            MediaMetadataRetriever m=new MediaMetadataRetriever();try{m.setDataSource(file.getAbsolutePath());assertEquals(width,Integer.parseInt(m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)));assertEquals(height,Integer.parseInt(m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)));assertEquals(rotation,Integer.parseInt(m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)));assertEquals(3000,Long.parseLong(m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)),100);assertNotEquals("Synthetic fixture must have no audio","yes",m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO));}finally{m.release();}
        }
        void unchanged()throws Exception{assertEquals(bytes,file.length());assertEquals(mtime,file.lastModified());assertEquals(sha,hash(file));}
    }
}
