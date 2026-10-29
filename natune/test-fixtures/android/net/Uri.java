package android.net;

/** Hosted control fixture ONLY. Never Android framework/runtime acceptance. */
public final class Uri {
    private final String literal;
    private Uri(String literal){this.literal=literal;}
    public static Uri parse(String literal){return new Uri(literal);}
}
