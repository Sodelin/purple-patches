package android.app;

import android.content.DialogInterface;

/** Tracks only the dismiss call used by the selected cleanup method. */
public class Dialog implements DialogInterface {
    public boolean dismissed;
    public boolean failDismiss;
    public void cancel() {}
    public void dismiss() {
        dismissed = true;
        if (failDismiss) throw new OutOfMemoryError("synthetic dismiss failure");
    }
}
