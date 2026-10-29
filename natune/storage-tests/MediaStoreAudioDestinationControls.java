import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import dev.purple.patches.natune.MediaStoreAudioDestination;
import dev.purple.patches.natune.VerifiedStreamCopy;
import java.io.*;
import java.util.Arrays;

/** Synthetic host stand-ins only: no Android provider, music or device is used. */
public final class MediaStoreAudioDestinationControls {
    private static int checks;
    interface Task { void run() throws Exception; }
    static void need(boolean condition,String why){checks++;if(!condition)throw new IllegalStateException(why);}
    static void refuses(Task task,String why)throws Exception{
        try{task.run();}catch(IOException expected){need(true,why);return;}
        throw new IllegalStateException("Expected refusal: "+why);
    }
    static final class App extends Context {
        final Store store=new Store();
        public Context getApplicationContext(){return this;}
        public String getPackageName(){return "example.natune";}
        public ContentResolver getContentResolver(){return store;}
    }
    static final class Store extends ContentResolver {
        String owner="example.natune",folder,returned="content://media/external_primary/audio/media/42";
        int pending=1,inserts,opens,reads,updates;
        boolean missing,duplicate,nullOutput,nullInput,corruptRead,updateZeroAfterChange,throwAfterUpdate;
        byte[] body=new byte[0];
        ContentValues inserted;
        public Uri insert(Uri collection,ContentValues values){
            inserts++;inserted=values;folder=(String)values.get(MediaStore.MediaColumns.RELATIVE_PATH);
            return returned==null?null:Uri.parse(returned);
        }
        public Cursor query(Uri ignored,String[] columns,String selection,String[] args,String order){
            if(missing)return null;
            return new Cursor(){
                public boolean moveToFirst(){return true;}
                public boolean moveToNext(){return duplicate;}
                public String getString(int i){return i==0?owner:i==2?folder:null;}
                public int getInt(int i){return pending;}
                public void close(){}
            };
        }
        public OutputStream openOutputStream(Uri ignored,String mode){
            opens++;
            if(!"w".equals(mode))throw new IllegalStateException("Unexpected mode");
            if(nullOutput)return null;
            return new ByteArrayOutputStream(){public void close()throws IOException{super.close();body=toByteArray();}};
        }
        public InputStream openInputStream(Uri ignored){
            reads++;if(nullInput)return null;
            byte[] data=body.clone();if(corruptRead&&data.length>0)data[0]^=1;
            return new ByteArrayInputStream(data);
        }
        public int update(Uri ignored,ContentValues values,String selection,String[] args){
            updates++;pending=(Integer)values.get(MediaStore.MediaColumns.IS_PENDING);
            if(throwAfterUpdate)throw new IllegalStateException("Uncertain provider outcome");
            return updateZeroAfterChange?0:1;
        }
    }
    static final class Source implements VerifiedStreamCopy.SourceLease {
        final byte[] bytes;
        boolean changed;
        Source(byte[] bytes){this.bytes=bytes.clone();}
        public long length(){return bytes.length;}
        public boolean complete(){return true;}
        public void assertUnchanged()throws IOException{if(changed)throw new IOException("Changed source");}
        public InputStream open(){return new ByteArrayInputStream(bytes);}
    }
    static MediaStoreAudioDestination create(App app)throws IOException{
        return MediaStoreAudioDestination.create(app,"external_primary","Music/Selected","song.flac","audio/flac");
    }
    public static void main(String[] args)throws Exception{
        byte[] bytes=new byte[70037];for(int i=0;i<bytes.length;i++)bytes[i]=(byte)(i*29);
        Source source=new Source(bytes);App app=new App();var output=create(app);
        need(app.store.inserts==1 && app.store.pending==1 && app.store.folder.equals("Music/Selected/") &&
            "song.flac".equals(app.store.inserted.get(MediaStore.MediaColumns.DISPLAY_NAME)) &&
            "audio/flac".equals(app.store.inserted.get(MediaStore.MediaColumns.MIME_TYPE)) &&
            Integer.valueOf(1).equals(app.store.inserted.get(MediaStore.MediaColumns.IS_PENDING)),"New selected pending audio");
        var proof=VerifiedStreamCopy.copy(source,output,()->{});
        need(Arrays.equals(bytes,app.store.body)&&app.store.pending==1&&app.store.updates==0,"Copy and independent pending readback");
        output.publish(proof,source,()->{});
        need(app.store.pending==0&&app.store.updates==1&&Arrays.equals(source.bytes,bytes),"Publish only verified original; source retained");
        refuses(()->output.publish(proof,source,()->{}),"Do not repeat publication");
        refuses(output::openNewOutput,"Do not reopen output");

        App invalid=new App();
        for(String path:new String[]{"", "/Music", "Music/../Else", "Music//Bad", "Music\\Bad", "."})
            refuses(()->MediaStoreAudioDestination.create(invalid,"external_primary",path,"song.flac","audio/flac"),"Invalid folder");
        refuses(()->MediaStoreAudioDestination.create(invalid,"missing","Music","song.flac","audio/flac"),"Unselected volume");
        refuses(()->MediaStoreAudioDestination.create(invalid,"external_primary","Music","../song.flac","audio/flac"),"Invalid filename");
        need(invalid.store.inserts==0,"Invalid inputs cause no insertion");
        int version=Build.VERSION.SDK_INT;Build.VERSION.SDK_INT=28;
        refuses(()->create(invalid),"Unsupported Android release");Build.VERSION.SDK_INT=version;

        for(String bad:new String[]{"content://other/external_primary/audio/media/42",
                "content://media/removable/audio/media/42","content://media/external_primary/audio/media",
                "content://media/external_primary/audio/media/nope"}){
            App wrong=new App();wrong.store.returned=bad;
            refuses(()->create(wrong),"Wrong returned row");need(wrong.store.opens==0,"No output to wrong row");
        }
        App wrongOwner=new App();wrongOwner.store.owner="another.app";
        refuses(()->create(wrongOwner),"Wrong row owner");need(wrongOwner.store.opens==0,"No output to other owner");
        App duplicate=new App();duplicate.store.duplicate=true;
        refuses(()->create(duplicate),"Duplicate query rows");

        App tampered=new App();Source a=new Source(bytes);var d=create(tampered);
        var p=VerifiedStreamCopy.copy(a,d,()->{});tampered.store.body[0]^=1;
        refuses(()->d.publish(p,a,()->{}),"Changed destination cannot publish");
        need(tampered.store.updates==0&&tampered.store.pending==1,"Tamper retains pending row and source");
        App foreign=new App();Source b=new Source(bytes);var e=create(foreign);
        var ep=VerifiedStreamCopy.copy(b,e,()->{});
        refuses(()->e.publish(p,a,()->{}),"Proof is bound to destination");
        need(foreign.store.updates==0,"Foreign proof cannot publish");
        b.changed=true;
        refuses(()->e.publish(ep,b,()->{}),"Changed source cannot publish");
        need(foreign.store.updates==0&&foreign.store.pending==1,"Changed source stays pending");

        App uncertain=new App();Source c=new Source(bytes);var u=create(uncertain);
        var up=VerifiedStreamCopy.copy(c,u,()->{});uncertain.store.updateZeroAfterChange=true;
        refuses(()->u.publish(up,c,()->{}),"Uncertain update result");
        refuses(()->u.publish(up,c,()->{}),"Unknown publication is not replayed");
        need(uncertain.store.updates==1&&Arrays.equals(c.bytes,bytes),"Single uncertain update; source retained");
        App thrown=new App();Source t=new Source(bytes);var td=create(thrown);
        var tp=VerifiedStreamCopy.copy(t,td,()->{});thrown.store.throwAfterUpdate=true;
        try{td.publish(tp,t,()->{});throw new IllegalStateException("Provider failure was ignored");}
        catch(IllegalStateException expected){need(expected.getMessage().equals("Uncertain provider outcome"),"Propagate provider uncertainty");}
        refuses(()->td.publish(tp,t,()->{}),"Provider exception is not replayed");
        need(thrown.store.updates==1&&Arrays.equals(t.bytes,bytes),"Throwing update remains unresolved; source retained");
        App unavailable=new App();unavailable.store.nullOutput=true;var n=create(unavailable);
        refuses(()->VerifiedStreamCopy.copy(new Source(bytes),n,()->{}),"Null output");
        refuses(n::openNewOutput,"Uncertain output is not reopened");
        need(unavailable.store.pending==1&&unavailable.store.updates==0,"Failed output remains pending");
        System.out.println("MEDIASTORE_COPY_CONTROLS passed="+checks+
            " synthetic_provider=true device_provider_test=false media_removed=false");
    }
}
