package dev.jason.gboardpatches.extension.websearch;

import android.content.Context;

public final class GboardFloatingWebSearchAccessPointContribution {
    public static final GboardFloatingWebSearchAccessPointContribution INSTANCE = new GboardFloatingWebSearchAccessPointContribution();
    public Object extendOrderCatalog(Context context, Object value) { return value; }
    public void register(Object controller, Context context) { }
}
