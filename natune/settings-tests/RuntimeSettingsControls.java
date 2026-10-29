import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import dev.purple.patches.natune.PurpleSettingsPage;
import dev.purple.patches.natune.RuntimeSettings;
import java.util.*;

/** Actual settings source with only its patch-selected markers projected true. */
public final class RuntimeSettingsControls {
    static int checks;
    static void need(boolean ok,String why){checks++;if(!ok)throw new IllegalStateException(why);}
    static final class Prefs implements SharedPreferences {
        final Map<String,Object> entries=new HashMap<>();
        boolean fail;
        public boolean getBoolean(String key,boolean fallback){
            Object value=entries.get(key);if(value==null)return fallback;
            if(!(value instanceof Boolean))throw new ClassCastException();return (Boolean)value;
        }
        public Editor edit(){
            return new Editor(){
                final Map<String,Boolean> additions=new HashMap<>();boolean clear;
                public Editor putBoolean(String key,boolean value){additions.put(key,value);return this;}
                public Editor clear(){clear=true;return this;}
                public boolean commit(){if(fail)return false;if(clear)entries.clear();entries.putAll(additions);return true;}
            };
        }
    }
    static final class App extends Context {
        final Prefs owned=new Prefs(),original=new Prefs();
        public Context getApplicationContext(){return this;}
        public String getPackageName(){return "example.natune";}
        public ContentResolver getContentResolver(){return null;}
        public SharedPreferences getSharedPreferences(String name,int mode){
            if(!name.equals("purple_patches_settings_v1")||mode!=Context.MODE_PRIVATE)throw new IllegalStateException("Wrong preference owner");
            return owned;
        }
    }
    public static void main(String[] args){
        App app=new App();app.original.entries.put("download_setting",true);
        need(RuntimeSettings.mobilePatchPresent()&&RuntimeSettings.flacPatchPresent(),"Selected markers");
        need(RuntimeSettings.requireFlac(),"Pre-bootstrap FLAC guard stays closed");
        RuntimeSettings.initialize(app);
        need(RuntimeSettings.fullQualityMobile(app)&&RuntimeSettings.requireFlac(),"Selected defaults");
        need(!RuntimeSettings.mobilePolicy(app,true)&&!RuntimeSettings.mobilePolicy(app,false),"Enabled mobile policy");
        need(RuntimeSettings.setFullQualityMobile(app,false),"Disable mobile policy");
        need(RuntimeSettings.mobilePolicy(app,true)&&!RuntimeSettings.mobilePolicy(app,false),"Disabled policy returns exact stock result");
        need(RuntimeSettings.setRequireFlac(app,false)&&!RuntimeSettings.requireFlac(),"Disable FLAC guard");
        need(RuntimeSettings.setRequireFlac(app,true)&&RuntimeSettings.requireFlac(),"Re-enable FLAC guard");
        app.owned.fail=true;
        need(!RuntimeSettings.setRequireFlac(app,false)&&RuntimeSettings.requireFlac(),"Failed preference commit retains value");
        app.owned.fail=false;app.owned.entries.put("require_flac","invalid");
        need(RuntimeSettings.requireFlac(),"Malformed value keeps FLAC guard closed");
        need(RuntimeSettings.resetPatchSettings(app)&&RuntimeSettings.fullQualityMobile(app)&&RuntimeSettings.requireFlac(),"Reset own settings to defaults");
        need(Boolean.TRUE.equals(app.original.entries.get("download_setting")),"Reset preserves original app settings");
        RuntimeSettings.open(app);need(PurpleSettingsPage.opened==app,"Pass original UI context to page");
        System.out.println("NATUNE_RUNTIME_SETTINGS_CONTROLS passed="+checks+" synthetic_preferences=true device_ui_test=false");
    }
}
