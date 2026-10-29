package android.provider;
import android.content.Context;
import android.net.Uri;
import java.util.Set;

/** Host-only stand-in, never packaged in an APK. */
public final class MediaStore {
    private MediaStore(){}
    public static Set<String> getExternalVolumeNames(Context ignored){return Set.of("external_primary","removable");}
    public static final class MediaColumns {
        public static final String DISPLAY_NAME="_display_name", MIME_TYPE="mime_type", RELATIVE_PATH="relative_path",
            IS_PENDING="is_pending", OWNER_PACKAGE_NAME="owner_package_name";
        private MediaColumns(){}
    }
    public static final class Audio {
        private Audio(){}
        public static final class Media {
            private Media(){}
            public static Uri getContentUri(String volume){return Uri.parse("content://media/"+volume+"/audio/media");}
        }
    }
}
