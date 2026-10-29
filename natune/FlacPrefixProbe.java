package dev.purple.patches.natune;

/**
 * Pure, memory-only native-FLAC prefix evidence, following RFC9639 sections8/9.
 * No URI, MIME, preference, credential, network or filesystem authority.
 * HEADER_VERIFIED is NOT complete media admission: a decoder must still validate
 * frame payloads/integrity, recording/duration and every other container route.
 */
public final class FlacPrefixProbe {
    public enum State { NEED_MORE, NEED_CANONICAL_PREFIX, CONTAINER_DECODER_REQUIRED, MALFORMED, TRUNCATED, HEADER_VERIFIED }
    public static final class Result {
        public final State state;
        public final int sampleRate, channels, bitsPerSample, firstFrameOffset, headerEnd;
        public final long totalSamples;
        private Result(State state, int rate, int channels, int bits, long samples, int frame, int end) {
            this.state=state; this.sampleRate=rate; this.channels=channels;
            this.bitsPerSample=bits; this.totalSamples=samples; this.firstFrameOffset=frame; this.headerEnd=end;
        }
        public long durationMs() { return sampleRate>0 && totalSamples>0 ? totalSamples*1000L/sampleRate : -1; }
    }
    private static Result state(State state) { return new Result(state,0,0,0,0,-1,-1); }
    private static int u(byte[] b,int i) { return b[i]&255; }
    private static int number(byte[] b,int p,int n) {
        int v=0; for(int i=0;i<n;i++) v=(v<<8)|u(b,p+i); return v;
    }
    private static Result more(boolean end) { return state(end?State.TRUNCATED:State.NEED_MORE); }
    private static int crc8(byte[] b,int from,int to) {
        int crc=0;
        for(int i=from;i<to;i++) {
            crc^=u(b,i);
            for(int bit=0;bit<8;bit++) crc=((crc<<1)^((crc&128)!=0?7:0))&255;
        }
        return crc;
    }
    public static Result inspect(byte[] b, int length, long absolutePosition, boolean endOfInput) {
        if(b==null || length<0 || length>b.length || absolutePosition<0)
            throw new IllegalArgumentException("Invalid original prefix bounds");
        if(absolutePosition!=0) return state(State.NEED_CANONICAL_PREFIX);
        final int[] magic={0x66,0x4c,0x61,0x43};
        for(int i=0;i<Math.min(4,length);i++)
            if(u(b,i)!=magic[i]) return state(State.CONTAINER_DECODER_REQUIRED);
        if(length<8) return more(endOfInput);
        if((u(b,4)&127)!=0 || number(b,5,3)!=34) return state(State.MALFORMED);
        if(length<42) return more(endOfInput);
        int minBlock=number(b,8,2), maxBlock=number(b,10,2);
        int minFrame=number(b,12,3), maxFrame=number(b,15,3);
        int rate=(number(b,18,3)>>>4), channels=((u(b,20)>>>1)&7)+1;
        int bits=(((u(b,20)&1)<<4)|(u(b,21)>>>4))+1;
        long samples=((long)(u(b,21)&15)<<32)|((long)number(b,22,2)<<16)|number(b,24,2);
        if(minBlock<16 || maxBlock<minBlock || rate==0 || bits<4 ||
           (minFrame!=0 && maxFrame!=0 && minFrame>maxFrame)) return state(State.MALFORMED);
        int p=42;
        boolean last=(u(b,4)&128)!=0;
        while(!last) {
            if(length-p<4) return more(endOfInput);
            int type=u(b,p)&127, size=number(b,p+1,3);
            if(type==0 || type==127) return state(State.MALFORMED);
            last=(u(b,p)&128)!=0;
            p+=4;
            if(size>length-p) return more(endOfInput);
            p+=size;
        }
        int frame=p;
        if(length-p<5) return more(endOfInput);
        if(u(b,p)!=255 || (u(b,p+1)&254)!=248 || (u(b,p+3)&1)!=0)
            return state(State.MALFORMED);
        boolean variable=(u(b,p+1)&1)!=0;
        int blockCode=u(b,p+2)>>>4, rateCode=u(b,p+2)&15;
        int channelCode=u(b,p+3)>>>4, depthCode=(u(b,p+3)>>>1)&7;
        if(blockCode==0 || rateCode==15 || channelCode>10 || depthCode==3)
            return state(State.MALFORMED);
        p+=4;
        int first=u(b,p++), count=1;
        long index;
        if(first<128) index=first;
        else {
            count=0; for(int mask=128;(first&mask)!=0 && mask!=0;mask>>>=1) count++;
            if(count<2 || count>7) return state(State.MALFORMED);
            if(length-p<count-1) return more(endOfInput);
            index=first&((1<<(7-count))-1);
            for(int i=1;i<count;i++) {
                int next=u(b,p++);
                if((next&192)!=128) return state(State.MALFORMED);
                index=(index<<6)|(next&63);
            }
            long[] minimum={0,0,128,2048,65536,2097152,67108864,2147483648L};
            if(index<minimum[count]) return state(State.MALFORMED);
        }
        // This is the canonical beginning, so the first frame/sample index is0.
        if(index!=0 || (!variable && count>6)) return state(State.MALFORMED);
        int block;
        if(blockCode==6 || blockCode==7) {
            int n=blockCode==6?1:2;
            if(length-p<n) return more(endOfInput);
            block=number(b,p,n)+1; p+=n;
        } else block=blockCode==1?192:blockCode<=5?576<<(blockCode-2):256<<(blockCode-8);
        int[] rates={rate,88200,176400,192000,8000,16000,22050,24000,32000,44100,48000,96000};
        int frameRate;
        if(rateCode>=12) {
            int n=rateCode==12?1:2;
            if(length-p<n) return more(endOfInput);
            frameRate=number(b,p,n)*(rateCode==12?1000:rateCode==14?10:1); p+=n;
        } else frameRate=rates[rateCode];
        int[] depths={bits,8,12,-1,16,20,24,32};
        int frameChannels=channelCode<8?channelCode+1:2;
        if(block>maxBlock || frameRate!=rate || frameChannels!=channels || depths[depthCode]!=bits)
            return state(State.MALFORMED);
        if(length<=p) return more(endOfInput);
        if(crc8(b,frame,p)!=u(b,p)) return state(State.MALFORMED);
        p++;
        if(endOfInput && (length-p<3 || (minFrame>0 && length-frame<minFrame))) return state(State.TRUNCATED);
        return new Result(State.HEADER_VERIFIED,rate,channels,bits,samples,frame,p);
    }
    private FlacPrefixProbe() {}
}
