package dev.purple.patches.gboard;

import android.content.Context;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Narrow contribution adapter for the pinned upstream Gboard AP bindings. */
public final class PurpleAccessPoints {
    private static final String[] TOKENS = {"purple_whisper", "purple_translate"};
    private static final String[] LABELS = {"Whisper 3", "AI Translate"};
    private static final int[] ICONS = {android.R.drawable.ic_btn_speak_now, android.R.drawable.ic_menu_edit};
    private PurpleAccessPoints() {}
    public static Object extend(Context context, Object original) {
        try {
            if (!PurpleAiRuntime.enabled(context) || !(original instanceof Collection<?>)) return original;
            List<String> values = new ArrayList<>();
            for (Object value : (Collection<?>)original) {
                if (!(value instanceof String)) return original;
                if (!values.contains((String)value)) values.add((String)value);
            }
            for (String token : TOKENS) if (!values.contains(token)) values.add(token);
            Class<?> type = Class.forName("wpa", false, original.getClass().getClassLoader());
            Method copy = type.getDeclaredMethod("n", Collection.class);
            if (!type.isInstance(original) || copy.getReturnType()!=type || !Modifier.isStatic(copy.getModifiers())) return original;
            copy.setAccessible(true); return copy.invoke(null, values);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) { return original; }
    }
    public static void register(Object controller, Context context) {
        try {
            if (!PurpleAiRuntime.enabled(context) || controller == null) return;
            ClassLoader loader=controller.getClass().getClassLoader();
            Class<?> descriptor=Class.forName("mtr",false,loader), builder=Class.forName("mtm",false,loader);
            Class<?> controllerType=Class.forName("mwv",false,loader);
            if (!controllerType.isInstance(controller)) return;
            Method factory=descriptor.getDeclaredMethod("c"), token=builder.getDeclaredMethod("l",String.class),
                    icon=builder.getDeclaredMethod("i",int.class), runnable=builder.getDeclaredMethod("q",Runnable.class),
                    build=builder.getDeclaredMethod("a"), register=controllerType.getDeclaredMethod("g",descriptor,boolean.class);
            Field label=builder.getDeclaredField("d");
            if (factory.getReturnType()!=builder || build.getReturnType()!=descriptor || label.getType()!=String.class
                    || !Modifier.isStatic(factory.getModifiers()) || Modifier.isStatic(label.getModifiers())) return;
            for (Method method:new Method[]{factory,token,icon,runnable,build,register}) method.setAccessible(true);
            label.setAccessible(true);
            for (int i=0;i<TOKENS.length;i++) {
                final boolean whisper=i==0;
                Object value=factory.invoke(null);token.invoke(value,TOKENS[i]);icon.invoke(value,ICONS[i]);
                label.set(value,LABELS[i]);runnable.invoke(value,(Runnable)()->PurpleAiRuntime.click(whisper));
                register.invoke(controller,build.invoke(value),false);
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            // An unavailable optional action cannot prevent normal Gboard startup.
        }
    }
}
