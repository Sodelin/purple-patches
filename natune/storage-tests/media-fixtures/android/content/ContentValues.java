package android.content;

/** Host-only stand-in, never packaged in an APK. */
public final class ContentValues {
    private final java.util.Map<String,Object> values=new java.util.HashMap<>();
    public void put(String key,String value){values.put(key,value);}
    public void put(String key,Integer value){values.put(key,value);}
    public Object get(String key){return values.get(key);}
}
