import dev.purple.patches.gboard.SessionPolicy;
public class SessionPolicyCheck {
    private static void check(boolean value) { if (!value) throw new AssertionError(); }
    private static final class Reader implements SessionPolicy.EditorReader {
        SessionPolicy.EditorPosition first=new SessionPolicy.EditorPosition(3,5,0), last=first;
        CharSequence selected="ok", before="left", after="right";
        int positions;
        boolean mutateSelected, failBefore;
        public SessionPolicy.EditorPosition position() { return positions++==0 ? first : last; }
        public CharSequence selected() { return selected; }
        public CharSequence before() {
            if (failBefore) throw new IllegalStateException("editor unavailable");
            if (mutateSelected) ((StringBuilder)selected).append('!');
            return before;
        }
        public CharSequence after() { return after; }
    }
    public static void main(String[] args) throws Exception {
        for (int value:new int[]{0,1|128,1|144,1|224,2|16}) check(SessionPolicy.sensitive(value,0));
        check(SessionPolicy.sensitive(1,0x1000000));check(!SessionPolicy.sensitive(1,0));
        check(SessionPolicy.localEndpoint("http://127.0.0.1:12345"));
        for (String value:new String[]{"http://localhost:1","https://127.0.0.1:1","http://127.0.0.1:1/evil","http://127.0.0.1:1?q=x","http://user@127.0.0.1:1","http://127.0.0.1:65536","http://192.168.1.1:3"}) check(!SessionPolicy.localEndpoint(value));
        check(SessionPolicy.sameAnchor(1,1,"a","b","c","a","b","c"));
        check(!SessionPolicy.sameAnchor(1,2,"a","b","c","a","b","c"));
        check(!SessionPolicy.sameAnchor(1,1,"a","b","c","a","b","changed"));
        check(!SessionPolicy.sameAnchor(1,1,null,"b","c",null,"b","c"));
        check(SessionPolicy.validEditorRead(4,4,0,"left","right",null));
        check(SessionPolicy.validEditorRead(3,5,0,"left","right","\ud83d\ude00"));
        check(SessionPolicy.validEditorRead(5,3,0,"left","right","\ud83d\ude00"));
        check(!SessionPolicy.validEditorRead(3,5,0,"left","right",null));
        check(!SessionPolicy.validEditorRead(3,5,0,"left","right","x"));
        check(!SessionPolicy.validEditorRead(3,3,0,null,"right",null));
        check(!SessionPolicy.validEditorRead(3,3,0,"left",null,null));
        check(!SessionPolicy.validEditorRead(-1,-1,0,"left","right",null));
        check(!SessionPolicy.validEditorRead(3,3,-1,"left","right",null));
        check(!SessionPolicy.validEditorRead(1,1,Integer.MAX_VALUE,"left","right",null));
        Reader valid=new Reader(); SessionPolicy.EditorSnapshot snapshot=SessionPolicy.captureEditor(valid);
        check(snapshot!=null && snapshot.start==3 && snapshot.end==5 && snapshot.selected.equals("ok"));
        Reader moved=new Reader(); moved.last=new SessionPolicy.EditorPosition(4,6,0);
        check(SessionPolicy.captureEditor(moved)==null);
        Reader offsetMoved=new Reader(); offsetMoved.last=new SessionPolicy.EditorPosition(3,5,1);
        check(SessionPolicy.captureEditor(offsetMoved)==null);
        Reader missingPosition=new Reader(); missingPosition.last=null; check(SessionPolicy.captureEditor(missingPosition)==null);
        Reader failure=new Reader(); failure.failBefore=true; check(SessionPolicy.captureEditor(failure)==null);
        Reader nullAfter=new Reader(); nullAfter.after=null; check(SessionPolicy.captureEditor(nullAfter)==null);
        Reader missingSelection=new Reader(); missingSelection.selected=null; check(SessionPolicy.captureEditor(missingSelection)==null);
        Reader collapsed=new Reader(); collapsed.first=collapsed.last=new SessionPolicy.EditorPosition(4,4,0); collapsed.selected=null;
        check(SessionPolicy.captureEditor(collapsed).selected.equals(""));
        Reader mutable=new Reader(); mutable.selected=new StringBuilder("ok"); mutable.mutateSelected=true;
        SessionPolicy.EditorSnapshot frozen=SessionPolicy.captureEditor(mutable);
        check(frozen!=null && frozen.selected.equals("ok") && !frozen.selected.contentEquals(mutable.selected));
        Reader oversized=new Reader(); oversized.before="x".repeat(65); check(SessionPolicy.captureEditor(oversized)==null);
        Reader multibyte=new Reader(); multibyte.first=multibyte.last=new SessionPolicy.EditorPosition(0,3000,0);
        multibyte.selected="界".repeat(3000); check(SessionPolicy.captureEditor(multibyte)==null);
        check(SessionPolicy.bootstrapSuccess(0,-1));
        check(SessionPolicy.whisperPreflightSupported("full-v3-pin-engine-sha-v1"));
        for (String value : new String[]{null,"","full-v3-pin-engine-sha-v0","memory-only-v1"})
            check(!SessionPolicy.whisperPreflightSupported(value));
        String companion = java.nio.file.Files.readString(java.nio.file.Path.of("companion/src/whisper.rs"));
        check(companion.contains("pub const PREFLIGHT_CAPABILITY: &str = \"" + SessionPolicy.WHISPER_PREFLIGHT + "\";"));
        for (Object err:new Object[]{null,0,1,"-1"}) check(!SessionPolicy.bootstrapSuccess(0,err));
        for (Object exit:new Object[]{null,1,-1,"0"}) check(!SessionPolicy.bootstrapSuccess(exit,-1));
        check(SessionPolicy.bounded("hello"));check(!SessionPolicy.bounded(" "));check(!SessionPolicy.bounded("界".repeat(3000)));
        System.out.println("Editor privacy, localhost, stale selection and UTF-8 limits passed");
    }
}
