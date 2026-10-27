package dev.purple.patches.gboard;
import android.app.Activity;
import android.os.Bundle;
public final class PurplePermissionActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        requestPermissions(new String[]{"android.permission.RECORD_AUDIO", "com.termux.permission.RUN_COMMAND"}, 7);
    }
    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results); finish();
    }
}
