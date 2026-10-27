package dev.purple.patches.gboard;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
public final class PurpleBootstrapReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) { PurpleAiRuntime.bootstrapResult(intent); }
}
