package dev.purple.patches.natune;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Looper;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/** Native settings dialog shown from the original settings screen's context. */
public final class PurpleSettingsPage {
    private final Context context;
    private final LinearLayout content;
    private final int spacing;

    private PurpleSettingsPage(Context context) {
        this.context=context;
        spacing=(int)(16*context.getResources().getDisplayMetrics().density+0.5f);
        content=new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(spacing,spacing,spacing,spacing);
    }
    public static void show(Context context) {
        if(context==null || Looper.myLooper()!=Looper.getMainLooper())
            throw new IllegalStateException("Settings require the original UI context and thread");
        RuntimeSettings.initialize(context);
        PurpleSettingsPage page=new PurpleSettingsPage(context);
        ScrollView scroll=new ScrollView(context);scroll.addView(page.content);
        page.render();
        new AlertDialog.Builder(context).setTitle("Purple Patches").setView(scroll)
            .setPositiveButton("Done",null).show();
    }
    private void render() {
        content.removeAllViews();
        heading("Playback & downloads");
        if(RuntimeSettings.mobilePatchPresent()) {
            toggle("Full quality on mobile data", "Use your configured audio quality and preferred sources on cellular connections.",
                RuntimeSettings.fullQualityMobile(context), true);
        }
        if(RuntimeSettings.flacPatchPresent()) {
            toggle("Require FLAC for local playback", "Require the local decoder to receive FLAC audio. If unavailable, playback reports an error. This does not verify the whole file or affect remote casting. Changes apply to the next input format.",
                RuntimeSettings.requireFlac(context), false);
        }
        heading("Troubleshooting");
        text("Natune N202610191717 • Purple runtime settings v1");
        Button reset=new Button(context);reset.setText("Reset Purple settings");
        reset.setOnClickListener(v->new AlertDialog.Builder(context).setTitle("Reset Purple settings?")
            .setMessage("Restore these patch settings to their defaults. Your music and other Natune settings will be kept.")
            .setNegativeButton(android.R.string.cancel,null).setPositiveButton("Reset",(dialog,which)->{
                if(RuntimeSettings.resetPatchSettings(context))render();else failed();
            }).show());
        content.addView(reset);
    }
    private void heading(String title) {
        TextView t=new TextView(context);t.setText(title);t.setTextSize(20);t.setPadding(0,spacing,0,spacing/2);content.addView(t);
    }
    private void text(String value) {
        TextView t=new TextView(context);t.setText(value);t.setPadding(0,0,0,spacing);content.addView(t);
    }
    private void toggle(String title,String description,boolean checked,boolean mobile) {
        Switch control=new Switch(context);control.setText(title);control.setTextSize(17);control.setChecked(checked);
        control.setPadding(0,spacing/2,0,spacing/2);content.addView(control);text(description);
        control.setOnCheckedChangeListener((button,enabled)->{
            boolean stored=mobile?RuntimeSettings.setFullQualityMobile(context,enabled):RuntimeSettings.setRequireFlac(context,enabled);
            if(!stored){failed();render();}
        });
    }
    private void failed(){Toast.makeText(context,"Could not save Purple settings",Toast.LENGTH_SHORT).show();}
}
