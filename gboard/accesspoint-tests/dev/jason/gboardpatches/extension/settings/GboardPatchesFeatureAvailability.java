package dev.jason.gboardpatches.extension.settings;

import android.content.Context;

public final class GboardPatchesFeatureAvailability {
    public static final String FEATURE_MANUAL_INCOGNITO = "manual";
    public static final String FEATURE_FLOATING_WEB_SEARCH = "web";
    public static final String FEATURE_EDITING_ACCESS_POINTS = "editing";
    public static boolean hasFeature(Context context, String name) { return FEATURE_EDITING_ACCESS_POINTS.equals(name); }
}
