import dev.purple.patches.natune.FlacProbeStream;
import dev.purple.patches.natune.FlacPrefixProbe.State;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Source/stop stand-ins; real prefix/parser/adapter code, no app/codec acceptance. */
public final class FlacProbeStreamControls {
    static int checks;
    static void need(boolean yes,String name) {
        checks++;if(!yes) throw new IllegalStateException("Stream control: "+name);
    }
    interface Task { void run() throws Exception; }
    static void refuses(Task task,String name) throws Exception {
        try { task.run();throw new IllegalStateException("Expected refusal: "+name); }
        catch(IOException expected) { need(true,name); }
    }
    static final class Reader implements FlacProbeStream.Source {
        byte[] bytes;String key="rfc-d1-revision1";long start,total,response;
        int cursor,chunk=3,closes,reads;boolean zeroRead,changeAfterRead,cancelAfterRead,closed;
        boolean[] cancelled;
        Reader(byte[] bytes,long start,boolean[] cancelled) {
            this.bytes=bytes;this.start=start;this.cursor=(int)start;
            this.total=bytes.length;this.response=Math.max(0,bytes.length-start);this.cancelled=cancelled;
        }
        public String renditionKey(){return key;}
        public long startOffset(){return start;}
        public long totalLength(){return total;}
        public long responseLength(){return response;}
        public int read(byte[] out,int offset,int length) throws IOException {
            if(closed) throw new IOException("Already closed");reads++;
            if(zeroRead)return 0;
            if(cursor>=bytes.length)return -1;
            int n=Math.min(length,Math.min(chunk,bytes.length-cursor));
            System.arraycopy(bytes,cursor,out,offset,n);cursor+=n;
            if(changeAfterRead)key="changed";
            if(cancelAfterRead)cancelled[0]=true;
            return n;
        }
        public void close(){closed=true;closes++;}
    }
    static final class Factory implements FlacProbeStream.Factory {
        byte[] bytes=FlacPrefixProbeControls.example();
        List<Long> opens=new ArrayList<>();List<Reader> readers=new ArrayList<>();
        boolean[] cancelled={false};boolean changedRange,wrongRange,zeroRead,cancelPrefix,truncateResponse,shortDeclaration;
        long totalAdjustment;
        public Reader open(long start) {
            opens.add(start);Reader r=new Reader(bytes,start,cancelled);
            if(start!=0 && changedRange)r.key="other-rendition";
            if(start!=0 && wrongRange)r.start=0;
            if(start!=0)r.total+=totalAdjustment;
            r.zeroRead=zeroRead;r.cancelAfterRead=cancelPrefix;
            if(truncateResponse)r.response+=2;
            if(shortDeclaration)r.response=1;
            readers.add(r);return r;
        }
        FlacProbeStream stream(long start,int limit) throws IOException {
            return FlacProbeStream.open(this,start,limit,()->{if(cancelled[0])throw new IOException("Original stopped");});
        }
    }
    static byte[] consume(FlacProbeStream stream,int chunk) throws Exception {
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] scratch=new byte[chunk];
        int n;while((n=stream.read(scratch,0,scratch.length))!=-1)out.write(scratch,0,n);
        return out.toByteArray();
    }
    public static void check() throws Exception {
        for(int size:new int[]{1,2,7,128}) {
            Factory f=new Factory();byte[] original=f.bytes.clone();
            try(FlacProbeStream s=f.stream(0,256)) {
                need(s.prefixEvidence.state==State.HEADER_VERIFIED,"real RFC header");
                need(Arrays.equals(consume(s,size),original),"inspected bytes replay exactly, chunk="+size);
                need(s.responseEnded(),"EOF recorded only after actual read");
                need(f.opens.equals(List.of(0L)),"no duplicate zero-position open");
            }
            need(f.readers.get(0).closes==1 && Arrays.equals(f.bytes,original),"close once/original unchanged");
        }
        Factory range=new Factory();
        try(FlacProbeStream s=range.stream(50,256)) {
            need(range.opens.equals(List.of(0L,50L)),"canonical proof before original range");
            need(range.readers.get(0).closes==1,"canonical reader closed before range");
            need(Arrays.equals(consume(s,4),Arrays.copyOfRange(range.bytes,50,range.bytes.length)),"range has no prepended metadata bytes");
        }
        Factory changed=new Factory();changed.changedRange=true;
        refuses(()->changed.stream(50,256),"changed range rendition");
        need(changed.readers.stream().allMatch(r->r.closes==1),"both failed-range readers closed");
        Factory wrong=new Factory();wrong.wrongRange=true;
        refuses(()->wrong.stream(50,256),"server ignored requested range");
        Factory resized=new Factory();resized.totalAdjustment=1;
        refuses(()->resized.stream(50,256),"same key changed known total length");
        Factory zero=new Factory();zero.zeroRead=true;
        refuses(()->zero.stream(0,256),"zero-progress positive-size source read");
        need(zero.readers.get(0).reads==1 && zero.readers.get(0).closes==1,"no spin or replay after invalid source");
        Factory cancelled=new Factory();cancelled.cancelled[0]=true;
        refuses(()->cancelled.stream(0,256),"original stop before source open");
        need(cancelled.opens.isEmpty(),"stopped operation has no source side effect");
        Factory cancelPrefix=new Factory();cancelPrefix.cancelPrefix=true;
        refuses(()->cancelPrefix.stream(0,256),"original stop during prefix IO");
        need(cancelPrefix.readers.get(0).closes==1,"cancelled prefix source closed");
        Factory shortResponse=new Factory();shortResponse.shortDeclaration=true;
        refuses(()->shortResponse.stream(0,256),"prefix exceeds original response declaration");
        Factory truncation=new Factory();truncation.truncateResponse=true;
        FlacProbeStream truncated=truncation.stream(0,256);
        refuses(()->consume(truncated,8),"short response cannot report EOF success");
        need(!truncated.responseEnded() && truncation.readers.get(0).closes==1,"failed response retained as failed");
        Factory midCancel=new Factory();FlacProbeStream stream=midCancel.stream(0,49);
        byte[] first=new byte[49];need(stream.read(first,0,49)==49,"replay without another source read");
        byte[] target={(byte)99};Reader live=midCancel.readers.get(0);live.cancelAfterRead=true;
        refuses(()->stream.read(target,0,1),"stop after source read, before caller delivery");
        need(target[0]==99 && live.closes==1,"cancelled bytes not copied to caller");
        Factory mutable=new Factory();FlacProbeStream m=mutable.stream(0,49);m.read(new byte[49],0,49);
        mutable.readers.get(0).changeAfterRead=true;
        refuses(()->m.read(new byte[1],0,1),"rendition changes during read");
        Factory bounded=new Factory();
        // Insert a padding block so49bytes cannot reach the first frame header.
        byte[] original=bounded.bytes;bounded.bytes=new byte[original.length+8];
        System.arraycopy(original,0,bounded.bytes,0,42);bounded.bytes[4]=0;
        bounded.bytes[42]=(byte)129;bounded.bytes[45]=4;
        System.arraycopy(original,42,bounded.bytes,50,original.length-42);
        refuses(()->bounded.stream(0,49),"original prefix budget cannot become false codec evidence");
        need(bounded.readers.get(0).closes==1,"budget refusal closes exact source");
        Factory ogg=new Factory();ogg.bytes=new byte[]{79,103,103,83,0,0,0,0};
        try(FlacProbeStream s=ogg.stream(0,256)) {
            need(s.prefixEvidence.state==State.CONTAINER_DECODER_REQUIRED,"Ogg not mislabeled native FLAC");
            need(Arrays.equals(consume(s,8),ogg.bytes),"container decoder receives original bytes");
        }
        Factory closing=new Factory();FlacProbeStream c=closing.stream(0,256);c.close();c.close();
        need(closing.readers.get(0).closes==1,"idempotent close");
        refuses(c::read,"closed stream cannot resume");
        System.out.println("FLAC_STREAM_CONTROLS\tpassed="+checks+"\tapp_binding=false\tfull_codec_admission=false");
    }
    public static void main(String[] args) throws Exception { check(); }
}
