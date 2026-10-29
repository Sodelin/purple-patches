import android.content.ContentResolver;
import android.content.Context;
import android.widget.Toast;
import dev.purple.patches.natune.NatuneSettingsNavigation;
import dev.purple.patches.natune.RuntimeSettings;
import java.util.*;
import r8.*;

/** Synthetic settings models; does not launch Natune or an Android dialog. */
public final class NatuneSettingsNavigationControls {
    static int checks;
    static void need(boolean ok,String why){checks++;if(!ok)throw new IllegalStateException(why);}
    static final class App extends Context {
        public Context getApplicationContext(){return this;}
        public String getPackageName(){return "example.natune";}
        public ContentResolver getContentResolver(){return null;}
    }
    static c0b row(String key){return new c0b(key,19,"Original",null,null,false,123L,
        Collections.emptyList(),Collections.emptyList(),()->wxc.a,2048);}
    public static void main(String[] args){
        App context=new App();c0b old=row("original");b0b group=new b0b("Existing",List.of(old));
        List<?> original=List.of(group);List<?> result=NatuneSettingsNavigation.append(original,context);
        need(result!=original&&result.size()==2&&original.size()==1&&result.get(0)==group,"Preserve original list and group");
        b0b added=(b0b)result.get(1);c0b entry=(c0b)added.b.get(0);
        need(added.a.equals("Purple Patches")&&added.c&&added.b.size()==1,"Visible new section");
        need(entry.a.equals("purple_patch_settings")&&entry.c.equals("Purple Patches")&&!entry.k,"Visible exact row");
        need(entry.b==old.b&&entry.g==old.g&&entry.h.contains("flac"),"Reuse native visual identity and searchable terms");
        need(entry.j.a()==wxc.a&&RuntimeSettings.opened==context,"Action opens using exact original context and Unit");
        need(entry.j.equals(entry.j)&&!entry.j.equals(old.j),"Callback identity");
        int before=Toast.shown;RuntimeSettings.fail=true;
        need(entry.j.a()==wxc.a&&Toast.shown==before+1,"Failed dialog leaves callback safe and informs user");
        need(NatuneSettingsNavigation.append(result,context)==result,"Duplicate entry is not added");
        need(NatuneSettingsNavigation.append(original,null)==original,"Missing context preserves original");
        List<?> wrong=List.of("unexpected");
        need(NatuneSettingsNavigation.append(wrong,context)==wrong,"Unknown ABI preserves original");
        List<?> empty=List.of();need(NatuneSettingsNavigation.append(empty,context)==empty,"Empty model preserves original");
        System.out.println("NATUNE_SETTINGS_NAVIGATION_CONTROLS passed="+checks+" synthetic_abi=true android_ui_test=false");
    }
}
