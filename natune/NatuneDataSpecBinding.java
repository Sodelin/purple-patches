package dev.purple.patches.natune;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Exact N202610191717 DataSpec adapter, derived from verified DEX constructor,
 * buildUpon and subrange instructions plus Media3 1.10.1 source. Copies only an
 * already-owned spec; no URL argument, network access, persistence or logging.
 * APK/version admission and the actual stream/decoder host remain separate.
 */
public final class NatuneDataSpecBinding {
    private final Constructor<?> constructor;
    private final Object uri;
    private final long uriOffset, position, length;
    private final int method, flags;
    private final byte[] body;
    private final Map<String,String> headers;
    private final String key;

    public NatuneDataSpecBinding(Object original) throws IOException {
        if(original==null || !original.getClass().getName().equals("r8.km2"))
            throw new IOException("Pinned Natune DataSpec class required");
        Class<?> type=original.getClass();
        if(!Modifier.isPublic(type.getModifiers()) || !Modifier.isFinal(type.getModifiers()))
            throw new IOException("Pinned Natune DataSpec modifiers changed");
        try {
            Map<String,Class<?>> shape=new HashMap<>();
            shape.put("b",long.class);shape.put("c",int.class);shape.put("d",byte[].class);
            shape.put("e",Map.class);shape.put("f",long.class);shape.put("g",long.class);
            shape.put("h",String.class);shape.put("i",int.class);
            Set<String> observed=new HashSet<>();
            for(Field field:type.getDeclaredFields()) {
                if(Modifier.isStatic(field.getModifiers()))continue;
                if(field.getModifiers()!=(Modifier.PUBLIC|Modifier.FINAL))
                    throw new IOException("Pinned Natune DataSpec field modifiers changed");
                String name=field.getName();Class<?> actual=field.getType();
                if(name.equals("a")?!actual.getName().equals("android.net.Uri"):actual!=shape.get(name))
                    throw new IOException("Pinned Natune DataSpec field type changed");
                observed.add(name);
            }
            if(observed.size()!=9 || !observed.containsAll(shape.keySet()) || !observed.contains("a"))
                throw new IOException("Pinned Natune DataSpec fields changed");
            Class<?> uriType=type.getField("a").getType();
            constructor=type.getConstructor(uriType,long.class,int.class,byte[].class,Map.class,
                                             long.class,long.class,String.class,int.class);
            uri=type.getField("a").get(original);
            uriOffset=type.getField("b").getLong(original);method=type.getField("c").getInt(original);
            byte[] suppliedBody=(byte[])type.getField("d").get(original);
            body=suppliedBody==null?null:suppliedBody.clone();
            Object suppliedHeaders=type.getField("e").get(original);
            if(!(suppliedHeaders instanceof Map))throw new IOException("Original request headers missing");
            Map<String,String> copied=new HashMap<>();
            for(Map.Entry<?,?> entry:((Map<?,?>)suppliedHeaders).entrySet()) {
                if(!(entry.getKey() instanceof String) || !(entry.getValue() instanceof String))
                    throw new IOException("Original header types invalid");
                copied.put((String)entry.getKey(),(String)entry.getValue());
            }
            headers=Collections.unmodifiableMap(copied);
            position=type.getField("f").getLong(original);length=type.getField("g").getLong(original);
            key=(String)type.getField("h").get(original);flags=type.getField("i").getInt(original);
            if(uri==null || method<1 || method>3 || position<0 || (length<=0 && length!=-1) ||
               uriOffset>Long.MAX_VALUE-position || uriOffset+position<0)
                throw new IOException("Original request bounds invalid");
        } catch(ReflectiveOperationException error) {
            throw new IOException("Pinned Natune DataSpec ABI unavailable");
        }
    }
    public long originalPosition(){return position;}
    public long originalLength(){return length;}
    public long uriPositionOffset(){return uriOffset;}
    public long originalAbsolutePosition(){return uriOffset+position;}

    /**
     * Native request conversion for an owner-selected complete shared document.
     * This preserves the original song/cache key, byte range and flags rather
     * than replacing library identity with the content URI. HTTP headers/body
     * are not forwarded to the document provider. No network/file URI fallback.
     *
     * The caller MUST own the actual verified song/rendition -> document map,
     * grant, index and recovery state. A URI and length are not verification or
     * access authority. Call before SchemeRoutingDataSource selects its factory;
     * redirecting below CacheDataSource could serve another cached rendition.
     */
    public Object forSharedDocument(Object contentUri,long completeBytes) throws IOException {
        if(completeBytes<=0 || uriOffset!=0 || method!=1 || (body!=null && body.length!=0))
            throw new IOException("Complete canonical GET resource required");
        if(position>completeBytes || (length!=-1 && length>completeBytes-position))
            throw new IOException("Original range exceeds shared document");
        for(String name:headers.keySet())if(name.equalsIgnoreCase("Range"))
            throw new IOException("Original explicit Range header requires its owner");
        try {
            Class<?> uriType=constructor.getParameterTypes()[0];
            if(contentUri==null || !uriType.isInstance(contentUri))
                throw new IOException("Native content URI required");
            Object scheme=uriType.getMethod("getScheme").invoke(contentUri);
            Object authority=uriType.getMethod("getAuthority").invoke(contentUri);
            if(!(scheme instanceof String) || !"content".equalsIgnoreCase((String)scheme) ||
                    !(authority instanceof String) || ((String)authority).isEmpty())
                throw new IOException("Shared document content URI required");
            return constructor.newInstance(contentUri,0L,1,null,Collections.emptyMap(),
                                           position,length,key,flags);
        } catch(ReflectiveOperationException error) {
            throw new IOException("Native shared document request unavailable",error);
        }
    }

    /** Preserves original URI, URI offset, method/body, headers, key and flags. */
    public Object copyRange(long start,long count) throws IOException {
        if(start<0 || (count<=0 && count!=-1) || uriOffset>Long.MAX_VALUE-start || uriOffset+start<0)
            throw new IOException("Requested range bounds invalid");
        try {
            // The original constructor copies headers, but retains body arrays.
            // Supply a new body copy for each output to preserve our snapshot.
            return constructor.newInstance(uri,uriOffset,method,body==null?null:body.clone(),
                                           headers,start,count,key,flags);
        } catch(ReflectiveOperationException error) {
            throw new IOException("Pinned Natune range construction failed");
        }
    }
    /**
     * Canonical prefix source selection belongs ABOVE cache/decryption fragments.
     * No fragment, POST body or explicit Range header is silently reinterpreted.
     */
    public Object canonicalResourceRange(long start,long count) throws IOException {
        if(uriOffset!=0) throw new IOException("Original canonical-resource owner required for URI fragment");
        if(method!=1 || (body!=null && body.length!=0))
            throw new IOException("Canonical probe cannot replay a non-GET original operation");
        for(String name:headers.keySet())if(name.equalsIgnoreCase("Range"))
            throw new IOException("Original explicit Range header needs its selected owner binding");
        return copyRange(start,count);
    }
}
