package android.net;

/** Hosted control fixture ONLY. Never Android framework/runtime acceptance. */
public final class Uri {
    private final String literal;
    private Uri(String literal){this.literal=literal;}
    public static Uri parse(String literal){return new Uri(literal);}
    public String getScheme(){return java.net.URI.create(literal).getScheme();}
    public String getAuthority(){return java.net.URI.create(literal).getAuthority();}
    @Override public String toString(){return literal;}
    public java.util.List<String> getPathSegments(){
        String path=java.net.URI.create(literal).getPath();
        if(path==null||path.equals("/"))return java.util.List.of();
        return java.util.Arrays.asList(path.substring(1).split("/"));
    }
}
