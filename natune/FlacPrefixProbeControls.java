import dev.purple.patches.natune.FlacPrefixProbe;
import dev.purple.patches.natune.FlacPrefixProbe.State;
import java.util.Arrays;

/** Public RFC9639 D.1 vector and malformed/range/container boundary controls. */
public final class FlacPrefixProbeControls {
    private static int checks;
    private static void need(boolean ok, String name) {
        checks++;
        if(!ok) throw new IllegalStateException("FLAC prefix control: "+name);
    }
    private static byte[] hex(String s) {
        byte[] b=new byte[s.length()/2];
        for(int i=0;i<b.length;i++) b[i]=(byte)Integer.parseInt(s.substring(i*2,i*2+2),16);
        return b;
    }
    private static byte[] example() {
        // RFC9639 AppendixD.1, one interchannel sample, stereo16-bit44100Hz.
        return hex("664c6143800000221000100000000f00000f0ac442f0000000013e84b41807dc690307586a3dad1a2e0ffff869180000bf0358fd03128baa9a");
    }
    private static State inspect(byte[] b) { return FlacPrefixProbe.inspect(b,b.length,0,true).state; }
    private static byte[] replace(int index,int value) {
        byte[] b=example();b[index]=(byte)value;return b;
    }
    public static void check() {
        byte[] b=example();byte[] unchanged=b.clone();
        FlacPrefixProbe.Result r=FlacPrefixProbe.inspect(b,b.length,0,true);
        need(r.state==State.HEADER_VERIFIED,"canonical RFC example header");
        need(r.sampleRate==44100 && r.channels==2 && r.bitsPerSample==16 && r.totalSamples==1,"exact streaminfo");
        need(r.firstFrameOffset==42 && r.headerEnd==49,"first frame boundaries");
        need(Arrays.equals(b,unchanged),"original bytes unchanged");
        for(int n=0;n<49;n++) {
            need(FlacPrefixProbe.inspect(b,n,0,false).state==State.NEED_MORE,"incremental prefix "+n);
            need(FlacPrefixProbe.inspect(b,n,0,true).state==State.TRUNCATED,"truncated prefix "+n);
        }
        need(FlacPrefixProbe.inspect(b,b.length,123,false).state==State.NEED_CANONICAL_PREFIX,"seek cannot impersonate start");
        for(String other:new String[]{"4944330400000000","4f67675300000000","0000001866747970","3c68746d6c3e0000","fff1508000000000"})
            need(inspect(hex(other))==State.CONTAINER_DECODER_REQUIRED,"non-native bytes need actual container decoder");
        need(inspect(replace(7,33))==State.MALFORMED,"wrong streaminfo length");
        need(inspect(replace(4,129))==State.MALFORMED,"first block must be streaminfo");
        need(inspect(replace(8,0))==State.MALFORMED,"minimum block below16");
        need(inspect(replace(10,0))==State.MALFORMED,"maximum below minimum");
        byte[] zeroRate=b.clone();zeroRate[18]=0;zeroRate[19]=0;zeroRate[20]&=15;
        need(inspect(zeroRate)==State.MALFORMED,"zero audio sample rate");
        byte[] tinyDepth=b.clone();tinyDepth[20]&=254;tinyDepth[21]&=15;
        need(inspect(tinyDepth)==State.MALFORMED,"invalid bit depth");
        need(inspect(replace(48,0))==State.MALFORMED,"frame header CRC");
        need(inspect(replace(43,250))==State.MALFORMED,"reserved sync bit");
        need(inspect(replace(44,9))==State.MALFORMED,"reserved block code");
        need(inspect(replace(44,111))==State.MALFORMED,"reserved sample-rate code");
        need(inspect(replace(45,25))==State.MALFORMED,"reserved frame bit");
        need(inspect(replace(45,22))==State.MALFORMED,"reserved bit-depth code");
        need(inspect(replace(45,184))==State.MALFORMED,"reserved channel assignment");
        need(inspect(replace(46,1))==State.MALFORMED,"first frame must start at sample0");
        need(inspect(replace(46,128))==State.MALFORMED,"invalid UTF8-coded frame index");
        need(inspect(Arrays.copyOf(b,b.length-1))==State.TRUNCATED,"declared minimum frame length");
        byte[] metadata=new byte[b.length+8];
        System.arraycopy(b,0,metadata,0,42);metadata[4]=0;
        metadata[42]=(byte)129;metadata[45]=4;
        System.arraycopy(b,42,metadata,50,b.length-42);
        FlacPrefixProbe.Result padded=FlacPrefixProbe.inspect(metadata,metadata.length,0,true);
        need(padded.state==State.HEADER_VERIFIED && padded.firstFrameOffset==50,"skip padding without modifying bytes");
        metadata[42]=0;
        need(inspect(metadata)==State.MALFORMED,"duplicate streaminfo");
        metadata[42]=(byte)255;
        need(inspect(metadata)==State.MALFORMED,"forbidden metadata type");
        byte[] changedPayload=replace(50,0);
        need(inspect(changedPayload)==State.HEADER_VERIFIED,"header evidence deliberately is not payload integrity");
        try { FlacPrefixProbe.inspect(b,b.length+1,0,false);need(false,"bad original bounds"); }
        catch(IllegalArgumentException expected) { need(true,"bad original bounds refuse"); }
        System.out.println("FLAC_PREFIX_CONTROLS\tpassed="+checks+"\tfull_media_admission=false");
    }
    public static void main(String[] args) { check(); }
}
