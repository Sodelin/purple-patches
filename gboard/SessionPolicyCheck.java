import dev.purple.patches.gboard.SessionPolicy;
public class SessionPolicyCheck {
    private static void check(boolean value) { if (!value) throw new AssertionError(); }
    public static void main(String[] args) {
        for (int value:new int[]{0,1|128,1|144,1|224,2|16}) check(SessionPolicy.sensitive(value,0));
        check(SessionPolicy.sensitive(1,0x1000000));check(!SessionPolicy.sensitive(1,0));
        check(SessionPolicy.localEndpoint("http://127.0.0.1:12345"));
        for (String value:new String[]{"http://localhost:1","https://127.0.0.1:1","http://127.0.0.1:1/evil","http://127.0.0.1:1?q=x","http://user@127.0.0.1:1","http://127.0.0.1:65536","http://192.168.1.1:3"}) check(!SessionPolicy.localEndpoint(value));
        check(SessionPolicy.sameAnchor(1,1,"a","b","c","a","b","c"));
        check(!SessionPolicy.sameAnchor(1,2,"a","b","c","a","b","c"));
        check(!SessionPolicy.sameAnchor(1,1,"a","b","c","a","b","changed"));
        check(SessionPolicy.bounded("hello"));check(!SessionPolicy.bounded(" "));check(!SessionPolicy.bounded("界".repeat(3000)));
        System.out.println("Editor privacy, localhost, stale selection and UTF-8 limits passed");
    }
}
