package dev.purple.patches.natune;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.Configuration;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/** Actual Android page; manifest/navigation admission is owned by the patch.
 * No Intent payload, URI, account, media library or diagnostic export is read. */
public final class PurpleSettingsActivity extends Activity {
    private LinearLayout content;
    private int spacing;
    @Override public void onCreate(Bundle state) {
        boolean dark=(getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;
        setTheme(dark?android.R.style.Theme_DeviceDefault:android.R.style.Theme_DeviceDefault_Light);
        super.onCreate(state);
        RuntimeSettings.initialize(this);
        setTitle("Purple Patches");
        if(getActionBar()!=null)getActionBar().setDisplayHomeAsUpEnabled(true);
        render();
    }
    private void render() {
        spacing=(int)(16*getResources().getDisplayMetrics().density+0.5f);
        ScrollView scroll=new ScrollView(this);
        content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(spacing,spacing,spacing,spacing);scroll.addView(content);setContentView(scroll);
        heading("Playback & downloads");
        if(RuntimeSettings.mobilePatchPresent()) {
            toggle("Full quality on mobile data", "Use your configured audio quality and preferred sources on cellular connections.",
                RuntimeSettings.fullQualityMobile(this), true);
        }
        if(RuntimeSettings.flacPatchPresent()) {
            toggle("Require FLAC for local playback", "Play verified FLAC formats. If FLAC is unavailable, playback reports an error. Changes apply when the next input format is evaluated.",
                RuntimeSettings.requireFlac(this), false);
        }
        heading("Troubleshooting");
        text("Natune N202610191717 • Purple runtime settings v1");
        Button reset=new Button(this);reset.setText("Reset Purple settings");
        reset.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Reset Purple settings?")
            .setMessage("Restore these patch settings to their defaults. Your music and other Natune settings will be kept.")
            .setNegativeButton(android.R.string.cancel,null).setPositiveButton("Reset",(dialog,which)->{
                if(RuntimeSettings.resetPatchSettings(this))render();else failed();
            }).show());
        content.addView(reset);
    }
    private void heading(String title) {
        TextView t=new TextView(this);t.setText(title);t.setTextSize(20);t.setPadding(0,spacing,0,spacing/2);content.addView(t);
    }
    private void text(String value) {
        TextView t=new TextView(this);t.setText(value);t.setPadding(0,0,0,spacing);content.addView(t);
    }
    private void toggle(String title,String description,boolean checked,boolean mobile) {
        Switch control=new Switch(this);control.setText(title);control.setTextSize(17);control.setChecked(checked);
        control.setPadding(0,spacing/2,0,spacing/2);content.addView(control);text(description);
        control.setOnCheckedChangeListener((button,enabled)->{
            boolean stored=mobile?RuntimeSettings.setFullQualityMobile(this,enabled):RuntimeSettings.setRequireFlac(this,enabled);
            if(!stored) {failed();render();}
        });
    }
    private void failed() {Toast.makeText(this,"Could not save Purple settings",Toast.LENGTH_SHORT).show();}
    @Override public boolean onOptionsItemSelected(android.view.MenuItem item) {
        if(item.getItemId()==android.R.id.home){finish();return true;}
        return super.onOptionsItemSelected(item);
    }
}
