package android.content;

import android.content.pm.ApplicationInfo;

public class Context {
    public static final String ACTIVITY_SERVICE = "activity";
    public Object getSystemService(String name) { return null; }
    public ApplicationInfo getApplicationInfo() { return new ApplicationInfo(); }
}
