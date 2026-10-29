package android.widget;
import android.content.Context;

/** Host-only stand-in, never packaged in an APK. */
public final class Toast {
    public static final int LENGTH_SHORT=0;
    public static int shown;
    private Toast(){}
    public static Toast makeText(Context context,String text,int duration){return new Toast();}
    public void show(){shown++;}
}
