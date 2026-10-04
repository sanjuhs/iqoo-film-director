package dev.minifilm.director;

import android.content.Context;
import android.net.Uri;
import android.util.AtomicFile;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Private capture metadata only. Never starts capture, changes listed edits, or deletes media. */
public final class CaptureTakeStore {
    private static final Object LOCK=new Object();
    private static final Set<String> ACTIVE=new HashSet<>(), OWNED_PENDING=new HashSet<>();
    private static final int MAX_METADATA_BYTES=65_536;
    private static final Pattern NAME=Pattern.compile("take-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.mp4");
    private final File directory;

    public static final class Result {
        public final List<Take> recovered;
        public final int unreadable;
        private Result(List<Take> recovered,int unreadable){this.recovered=Collections.unmodifiableList(recovered);this.unreadable=unreadable;}
    }
    public CaptureTakeStore(Context context){this(new File(context.getApplicationContext().getFilesDir(),"takes"));}
    /** Headless synthetic tests use a dedicated owned sandbox, not the production take folder. */
    CaptureTakeStore(File sandboxDirectory){if(sandboxDirectory==null)throw new NullPointerException("Take directory required");directory=sandboxDirectory.getAbsoluteFile();}

    /** Reserve a fresh filename and commit the shot facts before starting the recorder. */
    public void prepare(File source,Shot shot) throws IOException {
        synchronized(LOCK){
            if(Files.isSymbolicLink(directory.toPath())||!directory.isDirectory()&&!directory.mkdirs())throw new IOException("Take directory unavailable");
            File file=checked(source);String path=file.getPath();AtomicFile journal=journal(file);
            if(file.exists()||journal.getBaseFile().exists()||new File(journal.getBaseFile()+".new").exists()
                    ||new File(journal.getBaseFile()+".bak").exists()||ACTIVE.contains(path))throw new IOException("Take filename already exists");
            JSONObject metadata=metadata(file,shot,"pending",0);
            write(journal,metadata);OWNED_PENDING.add(path);ACTIVE.add(path);
        }
    }

    /** Publish final container facts even when its Activity/controller is already closed.
     * A metadata-write failure never turns a valid new recording into disposable media. */
    public long complete(File source){
        synchronized(LOCK){
            File file;
            try{file=checked(source);}catch(IOException invalid){return 0;}
            String path=file.getPath();
            long duration=CaptureController.finalizedVideoDuration(file);
            if(duration<=300){ACTIVE.remove(path);return 0;}
            try{
                JSONObject metadata=read(journal(file),file);
                if(metadata==null)metadata=metadata(file,null,"pending",0);
                metadata.put("state","ready").put("durationMs",duration);
                write(journal(file),metadata);
            }catch(Exception ignored){/* Pending facts/media remain available for a later recovery. */}
            finally{ACTIVE.remove(path);OWNED_PENDING.remove(path);}
            return duration;
        }
    }

    /** Failed-new-capture cleanup removes only this process's reserved metadata, never a video. */
    public void abort(File source){
        synchronized(LOCK){
            try{
                File file=checked(source);String path=file.getPath();ACTIVE.remove(path);
                if(OWNED_PENDING.remove(path))journal(file).delete();
            }catch(IOException ignored){/* Refuse unknown paths, including symlinks. */}
        }
    }

    /** Find finalized missing sources without rewriting any existing take's words, cuts or selection.
     * Pending sources still owned by a live process are skipped; a new process may validate an
     * interrupted pending source. Invalid/unknown files are left intact for explicit inspection. */
    public Result recover(List<Take> listed){
        synchronized(LOCK){
            ArrayList<Take> found=new ArrayList<>();int unreadable=0;
            Set<String> existing=new HashSet<>();Set<Uri> uris=new HashSet<>();
            if(listed!=null)for(Take take:listed)if(take!=null&&take.uri!=null){
                uris.add(take.uri);
                if("file".equals(take.uri.getScheme())&&take.uri.getPath()!=null)try{existing.add(new File(take.uri.getPath()).getCanonicalPath());}catch(IOException ignored){}
            }
            if(!directory.isDirectory()||Files.isSymbolicLink(directory.toPath()))return new Result(found,0);
            File[] candidates=directory.listFiles((dir,name)->NAME.matcher(name).matches());
            if(candidates==null)return new Result(found,0);Arrays.sort(candidates,(a,b)->a.getName().compareTo(b.getName()));
            for(File candidate:candidates){
                File file;
                try{file=checked(candidate);}catch(IOException invalid){unreadable++;continue;}
                String path=file.getPath();Uri uri=Uri.fromFile(file);
                if(ACTIVE.contains(path)||existing.contains(path)||uris.contains(uri))continue;
                long duration=CaptureController.finalizedVideoDuration(file);
                if(duration<=300){unreadable++;continue;}
                JSONObject metadata=null;boolean canCommit=true;
                try{metadata=read(journal(file),file);}catch(Exception ignored){canCommit=false;unreadable++;}
                if(metadata==null)try{metadata=metadata(file,null,"ready",duration);}catch(IOException invalid){unreadable++;continue;}
                if(canCommit)try{metadata.put("state","ready").put("durationMs",duration);write(journal(file),metadata);}catch(Exception ignored){/* Valid source and facts remain recoverable without deleting anything. */}
                Take take=new Take(uri,metadata.optString("shotId","recovered"),metadata.optString("title","Recovered take"),metadata.optString("caption",""),duration);
                take.selected=false;found.add(take);existing.add(path);uris.add(uri);
            }
            return new Result(found,unreadable);
        }
    }

    private File checked(File source) throws IOException {
        if(source==null||!NAME.matcher(source.getName()).matches()||Files.isSymbolicLink(source.toPath()))throw new IOException("Invalid take source");
        if(!source.getAbsoluteFile().toPath().normalize().toFile().equals(source.getAbsoluteFile())
                ||source.getParentFile()==null||Files.isSymbolicLink(source.getParentFile().toPath()))throw new IOException("Invalid take parent");
        File canonical=source.getCanonicalFile();
        if(!directory.getCanonicalFile().equals(canonical.getParentFile()))throw new IOException("Take must be a canonical direct child");
        return canonical;
    }
    private AtomicFile journal(File source) throws IOException {
        File base=new File(source.getParentFile(),source.getName()+".json");
        for(File part:new File[]{base,new File(base+".new"),new File(base+".bak")})
            if(Files.isSymbolicLink(part.toPath()))throw new IOException("Invalid take metadata");
        return new AtomicFile(base);
    }
    private static JSONObject metadata(File source,Shot shot,String state,long duration) throws IOException {
        if(shot!=null&&(shot.id==null||shot.title==null||shot.caption==null))throw new IOException("Invalid take facts");
        try{return new JSONObject().put("version",1).put("source",source.getName()).put("state",state).put("durationMs",duration)
                .put("shotId",shot==null?"recovered":shot.id).put("title",shot==null?"Recovered take":shot.title).put("caption",shot==null?"":shot.caption);}
        catch(Exception invalid){throw new IOException("Invalid take metadata");}
    }
    private static void write(AtomicFile journal,JSONObject metadata) throws IOException {
        byte[] bytes=metadata.toString().getBytes(StandardCharsets.UTF_8);
        if(bytes.length>MAX_METADATA_BYTES)throw new IOException("Take metadata too long");
        FileOutputStream output=null;
        try{output=journal.startWrite();output.write(bytes);journal.finishWrite(output);}
        catch(IOException|RuntimeException failure){if(output!=null)journal.failWrite(output);throw new IOException("Take metadata could not be saved");}
    }
    private static JSONObject read(AtomicFile journal,File source) throws IOException {
        if(!journal.getBaseFile().exists()&&!new File(journal.getBaseFile()+".bak").exists())return null;
        try(FileInputStream input=journal.openRead();ByteArrayOutputStream bytes=new ByteArrayOutputStream()){
            byte[] buffer=new byte[4096];int count;
            while((count=input.read(buffer))!=-1){if(bytes.size()+count>MAX_METADATA_BYTES)throw new IOException("Take metadata too long");bytes.write(buffer,0,count);}
            JSONObject metadata=new JSONObject(bytes.toString(StandardCharsets.UTF_8.name()));
            if(metadata.optInt("version",0)!=1||!source.getName().equals(metadata.optString("source",""))
                    ||!("pending".equals(metadata.optString("state",""))||"ready".equals(metadata.optString("state",""))))
                throw new IOException("Invalid take metadata");
            for(String key:new String[]{"shotId","title","caption"})if(!(metadata.opt(key) instanceof String))throw new IOException("Invalid take facts");
            return metadata;
        }catch(IOException failure){throw failure;}catch(Exception failure){throw new IOException("Invalid take metadata");}
    }
}
