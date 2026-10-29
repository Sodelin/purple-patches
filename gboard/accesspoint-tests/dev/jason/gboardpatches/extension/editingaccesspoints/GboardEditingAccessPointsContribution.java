package dev.jason.gboardpatches.extension.editingaccesspoints;

import android.content.Context;

public final class GboardEditingAccessPointsContribution {
    public static final GboardEditingAccessPointsContribution INSTANCE = new GboardEditingAccessPointsContribution();
    public static int registrations;
    public Object extendOrderCatalog(Context context, Object value) { return value; }
    public void register(Object controller, Context context) { registrations++; }
}
