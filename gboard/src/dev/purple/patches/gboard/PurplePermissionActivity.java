package dev.purple.patches.gboard;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
public final class PurplePermissionActivity extends Activity {
    static final String EXTRA_SCOPE = "dev.purple.patches.gboard.permission_scope";
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        Intent intent = getIntent();
        String[] permissions = SessionPolicy.permissionsFor(intent == null ? null : intent.getStringExtra(EXTRA_SCOPE));
        if (permissions.length != 1 || checkSelfPermission(permissions[0]) == PackageManager.PERMISSION_GRANTED) {
            finish(); return;
        }
        try { requestPermissions(permissions, 7); }
        catch (RuntimeException unavailable) { finish(); }
    }
    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results); finish();
    }
}
