package dev.purple.patches.natune;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/**
 * Decoder-input transport: preserves inspected bytes and binds range opens to
 * the same original rendition. This does NOT validate complete codec payloads
 * or authorize a source. Actual Natune DataSource/cache and decoder bindings
 * remain mandatory. No URL, MIME, logging, persistence or automatic fallback.
 */
public final class FlacProbeStream extends InputStream {
    /** Original caller cancellation/deadline check; not a new timeout or permit. */
    public interface Stop { void check() throws IOException; }
    /** Host-captured original source factory; must honor the exact start offset. */
    public interface Factory { Source open(long start) throws IOException; }
    public interface Source {
        // A real host-derived immutable rendition revision, NOT merely mediaId,
        // filename, provider name or an unvalidated request field.
        String renditionKey();
        long startOffset();
        long totalLength();       // Entire rendition length, or -1 when unknown.
        long responseLength();    // This bounded response's length, or -1.
        int read(byte[] buffer, int offset, int length) throws IOException;
        void close() throws IOException;
    }
    public final FlacPrefixProbe.Result prefixEvidence;
    private final Source source;
    private final Stop stop;
    private final String rendition;
    private final long start, total, expectedResponse;
    private final long requestedLength;
    private byte[] replay;
    private byte[] scratch;
    private final int replayLength;
    private int replayOffset;
    private long sourceBytes;
    private long delivered;
    private boolean closed, eof, rangeEnded;

    private FlacProbeStream(Source source, Stop stop, String rendition, long start,
                            long total, byte[] replay, int replayLength,
                            FlacPrefixProbe.Result evidence,long requestedLength) throws IOException {
        this.source=source;this.stop=stop;this.rendition=rendition;this.start=start;
        this.total=total;this.expectedResponse=source.responseLength();
        this.requestedLength=requestedLength;
        if(expectedResponse < -1 || (expectedResponse>=0 && replayLength>expectedResponse))
            throw new IOException("Invalid original response length");
        this.replay=replay;this.replayLength=replayLength;this.sourceBytes=replayLength;
        this.prefixEvidence=evidence;
    }
    private static String identity(Source source, long start, String expected, long expectedTotal) throws IOException {
        if(source==null) throw new IOException("Original source missing");
        String key=source.renditionKey();long length=source.totalLength();
        if(key==null || key.isEmpty() || source.startOffset()!=start || length < -1 ||
           source.responseLength() < -1 || (length>=0 && start>length))
            throw new IOException("Original source/range binding invalid");
        if(expected!=null && (!expected.equals(key) || (expectedTotal>=0 && length>=0 && expectedTotal!=length)))
            throw new IOException("Original rendition changed");
        return key;
    }
    private static void closeOnFailure(Source source, Throwable failure) {
        if(source!=null) try { source.close(); } catch(Throwable closing) { if(closing!=failure)failure.addSuppressed(closing); }
    }
    public static FlacProbeStream open(Factory factory, long start, int prefixLimit, Stop stop) throws IOException {
        return open(factory,start,-1,prefixLimit,stop);
    }
    public static FlacProbeStream open(Factory factory,long start,long requestedLength,int prefixLimit,Stop stop) throws IOException {
        if(factory==null || stop==null || start<0 || prefixLimit<49 ||
           (requestedLength<=0 && requestedLength!=-1) ||
           (requestedLength>0 && start>Long.MAX_VALUE-requestedLength))
            throw new IllegalArgumentException("Original stream bindings/budget required");
        Source owned=null;
        try {
            stop.check();owned=factory.open(0);stop.check();
            String key=identity(owned,0,null,-1);long total=owned.totalLength();
            if(total>=0 && start>total)throw new IOException("Requested range exceeds original rendition length");
            byte[] prefix=new byte[Math.min(prefixLimit,256)];int used=0;
            FlacPrefixProbe.Result evidence;
            while(true) {
                stop.check();identity(owned,0,key,total);
                if(used==prefix.length) {
                    if(used==prefixLimit) throw new IOException("Original prefix budget exhausted; decoder evidence unavailable");
                    prefix=Arrays.copyOf(prefix,(int)Math.min((long)prefixLimit,(long)prefix.length*2));
                }
                int count=owned.read(prefix,used,prefix.length-used);
                stop.check();identity(owned,0,key,total);
                if(count==0 || count < -1 || count>prefix.length-used)
                    throw new IOException("Original source violated blocking read contract");
                if(count>0) used+=count;
                evidence=FlacPrefixProbe.inspect(prefix,used,0,count==-1);
                if(evidence.state==FlacPrefixProbe.State.MALFORMED || evidence.state==FlacPrefixProbe.State.TRUNCATED)
                    throw new IOException("Malformed or truncated native FLAC prefix");
                if(evidence.state!=FlacPrefixProbe.State.NEED_MORE) break;
            }
            if(start!=0) {
                // No simultaneous second reader or silently changed source. A
                // first range cannot certify itself from a previous cache entry.
                Source canonical=owned;owned=null;canonical.close();
                Arrays.fill(prefix,(byte)0);stop.check();
                owned=factory.open(start);stop.check();identity(owned,start,key,total);
                prefix=new byte[0];used=0;
            }
            FlacProbeStream result=new FlacProbeStream(owned,stop,key,start,total,prefix,used,evidence,requestedLength);
            owned=null;return result;
        } catch(IOException|RuntimeException|Error error) { closeOnFailure(owned,error);throw error; }
    }
    private void current() throws IOException {
        if(closed) throw new IOException("Original stream closed");
        stop.check();identity(source,start,rendition,total);
    }
    @Override public int read(byte[] buffer,int offset,int length) throws IOException {
        if(buffer==null) throw new NullPointerException("buffer");
        if(offset<0 || length<0 || offset>buffer.length-length) throw new IndexOutOfBoundsException();
        if(length==0) return 0;
        try {
            current();
            if(requestedLength>=0 && delivered==requestedLength){rangeEnded=true;return -1;}
            int allowed=requestedLength<0?length:(int)Math.min((long)length,requestedLength-delivered);
            if(replayOffset<replayLength) {
                int n=Math.min(allowed,replayLength-replayOffset);
                System.arraycopy(replay,replayOffset,buffer,offset,n);replayOffset+=n;delivered+=n;return n;
            }
            if(eof) return -1;
            // Keep data private until the original stop/identity is rechecked.
            // Bounded per-read scratch is buffering, not an input/file-size cap.
            int requested=Math.min(allowed,65536);
            if(scratch==null || scratch.length<requested)scratch=new byte[requested];
            int n=source.read(scratch,0,requested);current();
            if(n==0 || n < -1 || n>requested) throw new IOException("Original source violated blocking read contract");
            if(n==-1) {
                if(expectedResponse>=0 && sourceBytes!=expectedResponse)
                    throw new IOException("Original response length mismatch");
                eof=true;return -1;
            }
            if(expectedResponse>=0 && n>expectedResponse-sourceBytes)
                throw new IOException("Original response exceeded its declared length");
            sourceBytes+=n;delivered+=n;System.arraycopy(scratch,0,buffer,offset,n);return n;
        } catch(IOException|RuntimeException|Error error) {
            try { close(); } catch(Throwable closing) { if(closing!=error)error.addSuppressed(closing); }
            throw error;
        }
    }
    @Override public int read() throws IOException {
        byte[] one=new byte[1];return read(one,0,1)==-1?-1:one[0]&255;
    }
    @Override public void close() throws IOException {
        if(closed) return;closed=true;Arrays.fill(replay,(byte)0);
        if(scratch!=null)Arrays.fill(scratch,(byte)0);source.close();
    }
    /** Response EOF only; never a complete codec/recording admission claim. */
    public boolean responseEnded() { return eof; }
    /** Original requested range ended; distinct from source EOF/codec proof. */
    public boolean requestedRangeEnded() { return rangeEnded; }
}
