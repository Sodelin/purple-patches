package android.net;

/** Hosted control fixture ONLY. Never Android framework/runtime acceptance. */
public final class Uri {
    private final String literal;
    private Uri(String literal){this.literal=literal;}
    public static Uri parse(String literal){return new Uri(literal);}
    public String getScheme(){return java.net.URI.create(literal).getScheme();}
    public String getAuthority(){return java.net.URI.create(literal).getAuthority();}
}
