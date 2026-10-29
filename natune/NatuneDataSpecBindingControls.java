import dev.purple.patches.natune.NatuneDataSpecBinding;
import android.net.Uri;
import r8.km2;
import java.io.IOException;
import java.util.Map;

/** Actual adapter against closed hosted ABI fixtures; no real Android authority. */
public final class NatuneDataSpecBindingControls {
    static int checks;
    interface Task {void run() throws Exception;}
    static void need(boolean yes,String label){checks++;if(!yes)throw new IllegalStateException(label);}
    static void refuses(Task task,String label) throws Exception {
        try{task.run();throw new IllegalStateException("Expected refusal: "+label);}
        catch(IOException expected){need(true,label);}
    }
    static km2 spec(long uriOffset,int method,byte[] body,Map<String,String> headers,long position,long length){
        return new km2(Uri.parse("https://example.invalid/controlled-media"),uriOffset,method,body,headers,position,length,"fixture-key",12);
    }
    public static void main(String[] args) throws Exception {
        km2 original=spec(0,1,null,Map.of("X-Control","unchanged"),50,7);
        NatuneDataSpecBinding binding=new NatuneDataSpecBinding(original);
        need(binding.originalPosition()==50 && binding.originalLength()==7 && binding.originalAbsolutePosition()==50,"original bounds");
        km2 prefix=(km2)binding.canonicalResourceRange(0,256);
        need(prefix.a==original.a && prefix.b==0 && prefix.c==1 && prefix.f==0 && prefix.g==256,"actual field mapping for canonical copy");
        need(prefix.e.equals(original.e) && prefix.h.equals(original.h) && prefix.i==12,"headers/key/flags preserved");
        need(original.f==50 && original.g==7,"original spec unchanged");
        km2 range=(km2)binding.copyRange(51,-1);
        need(range.f==51 && range.g==-1 && range.a==original.a,"exact requested range");
        byte[] body={1,2};km2 post=spec(0,2,body,Map.of(),0,-1);
        NatuneDataSpecBinding postBinding=new NatuneDataSpecBinding(post);body[0]=99;
        km2 copied=(km2)postBinding.copyRange(0,-1);
        need(copied.c==2 && copied.d[0]==1,"original body snapshot");
        copied.d[0]=88;
        need(((km2)postBinding.copyRange(0,-1)).d[0]==1,"output mutation cannot alter snapshot");
        int before=km2.constructed;
        refuses(()->postBinding.canonicalResourceRange(0,49),"no automatic POST replay");
        need(km2.constructed==before,"refused canonical POST has no constructor effect");
        NatuneDataSpecBinding fragment=new NatuneDataSpecBinding(spec(200,1,null,Map.of(),10,50));
        need(fragment.uriPositionOffset()==200 && fragment.originalAbsolutePosition()==210,"resource offset distinct from request position");
        km2 fragmentCopy=(km2)fragment.copyRange(11,10);
        need(fragmentCopy.b==200 && fragmentCopy.f==11,"fragment offset preserved in ordinary copy");
        refuses(()->fragment.canonicalResourceRange(0,49),"fragment cannot masquerade as full resource");
        NatuneDataSpecBinding explicitRange=new NatuneDataSpecBinding(spec(0,1,null,Map.of("rAnGe","bytes=1-2"),0,-1));
        refuses(()->explicitRange.canonicalResourceRange(0,49),"explicit Range header cannot disagree with probe");
        NatuneDataSpecBinding getBody=new NatuneDataSpecBinding(spec(0,1,new byte[]{1},Map.of(),0,-1));
        refuses(()->getBody.canonicalResourceRange(0,49),"GET body cannot be silently replayed");
        NatuneDataSpecBinding head=new NatuneDataSpecBinding(spec(0,3,null,Map.of(),0,-1));
        refuses(()->head.canonicalResourceRange(0,49),"HEAD is not media data");
        refuses(()->binding.copyRange(-1,1),"negative range position");
        refuses(()->binding.copyRange(0,0),"zero range length");
        refuses(()->binding.copyRange(0,-2),"invalid unset length");
        refuses(()->new NatuneDataSpecBinding(new Object()),"wrong target class");
        refuses(()->new NatuneDataSpecBinding(null),"missing original request");
        refuses(()->new NatuneDataSpecBinding(spec(0,1,null,Map.of(),-1,1)),"invalid original position");
        refuses(()->new NatuneDataSpecBinding(spec(Long.MAX_VALUE,1,null,Map.of(),1,1)),"original absolute overflow");
        NatuneDataSpecBinding large=new NatuneDataSpecBinding(spec(Long.MAX_VALUE,1,null,Map.of(),0,1));
        refuses(()->large.copyRange(1,1),"copied absolute overflow");
        System.out.println("NATUNE_DATASPEC_CONTROLS\tpassed="+checks+"\tandroid_fixture_only=true\tapp_authority=false");
    }
}
