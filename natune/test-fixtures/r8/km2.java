package r8;

import android.net.Uri;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Hosted ABI fixture, NOT actual Natune code/authority or an APK dependency. */
public final class km2 {
    public final Uri a;
    public final long b;
    public final int c;
    public final byte[] d;
    public final Map<String,String> e;
    public final long f,g;
    public final String h;
    public final int i;
    public static int constructed;
    public km2(Uri uri,long uriOffset,int method,byte[] body,Map<String,String> headers,
               long position,long length,String key,int flags) {
        constructed++;a=uri;b=uriOffset;c=method;d=body;
        e=Collections.unmodifiableMap(new HashMap<>(headers));f=position;g=length;h=key;i=flags;
    }
}
