package dev.purple.patches.natune;

import android.content.Context;
import android.widget.Toast;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Exact pinned N202610191717 settings-model ABI; no original list is mutated. */
public final class NatuneSettingsNavigation {
    private static final String KEY="purple_patch_settings";
    private NatuneSettingsNavigation(){}

    public static List<?> append(List<?> original,Context context) {
        if(original==null || context==null)return original;
        try {
            ClassLoader loader=context.getClassLoader();
            Class<?> group=Class.forName("r8.b0b",false,loader);
            Class<?> item=Class.forName("r8.c0b",false,loader);
            Class<?> callbackType=Class.forName("r8.si4",false,loader);
            Class<?> unitType=Class.forName("r8.wxc",false,loader);
            Method invoke=callbackType.getMethod("a");
            Field unitField=unitType.getField("a");
            if(!callbackType.isInterface() || invoke.getReturnType()!=Object.class ||
                    !Modifier.isStatic(unitField.getModifiers()) || unitField.getType()!=unitType)
                return original;
            Object unit=unitField.get(null);
            if(unit==null || unit.getClass()!=unitType)return original;
            Field children=group.getField("b"), key=item.getField("a"), icon=item.getField("b"), color=item.getField("g");
            if(children.getType()!=List.class || key.getType()!=String.class ||
                    icon.getType()!=int.class || color.getType()!=long.class)return original;
            Object sample=null;
            for(Object candidate:original) {
                if(candidate==null || candidate.getClass()!=group)return original;
                Object rows=children.get(candidate);
                if(!(rows instanceof List<?>))return original;
                for(Object row:(List<?>)rows) {
                    if(row==null || row.getClass()!=item)return original;
                    if(KEY.equals(key.get(row)))return original;
                    if(sample==null)sample=row;
                }
            }
            if(sample==null)return original;
            Constructor<?> itemConstructor=item.getConstructor(String.class,int.class,String.class,String.class,
                String.class,boolean.class,long.class,List.class,List.class,callbackType,int.class);
            Constructor<?> groupConstructor=group.getConstructor(String.class,List.class);
            Object callback=Proxy.newProxyInstance(loader,new Class<?>[]{callbackType},(proxy,method,args)->{
                if(method.getDeclaringClass()==Object.class) {
                    if(method.getName().equals("equals"))return proxy==args[0];
                    if(method.getName().equals("hashCode"))return System.identityHashCode(proxy);
                    if(method.getName().equals("toString"))return "Purple settings action";
                }
                if(!method.getName().equals("a") || method.getParameterTypes().length!=0)
                    throw new IllegalStateException("Unexpected settings callback");
                try { RuntimeSettings.open(context); }
                catch(RuntimeException failure) {
                    try { Toast.makeText(context,"Could not open Purple settings",Toast.LENGTH_SHORT).show(); }
                    catch(RuntimeException ignored) { /* Keep the original settings screen usable. */ }
                }
                return unit;
            });
            Object row=itemConstructor.newInstance(KEY,icon.getInt(sample),"Purple Patches",
                "FLAC and mobile audio settings",null,false,color.getLong(sample),
                Arrays.asList("purple","flac","mobile","audio"),Collections.emptyList(),callback,2048);
            Object section=groupConstructor.newInstance("Purple Patches",Collections.singletonList(row));
            ArrayList<Object> result=new ArrayList<>(original);
            result.add(section);
            return result;
        } catch(ReflectiveOperationException | RuntimeException unavailable) {
            // A mismatched release must keep its original settings, not crash or
            // silently substitute another signature. No private status is logged.
            return original;
        }
    }
}
