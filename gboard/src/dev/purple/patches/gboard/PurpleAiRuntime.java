package dev.purple.patches.gboard;

import android.Manifest;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.inputmethodservice.InputMethodService;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputType;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.widget.TextView;
import android.widget.Toast;
import java.lang.ref.WeakReference;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;

/** Two explicit IME actions; no chat, persistent text, token preferences or auto-typing calls. */
public final class PurpleAiRuntime {
    static final String FEATURE = "dev.purple.patches.gboard.enabled";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static WeakReference<InputMethodService> service = new WeakReference<>(null);
    private static long generation;
    private static long operation;
    private static volatile BridgeClient bridge;
    private static volatile AtomicBoolean cancellation;
    private static volatile WhisperCapture capture;
    private static final AtomicBoolean busy = new AtomicBoolean();
    private static AlertDialog dialog;
    private static String nonce;
    private static long connectDeadline;
    private static PendingIntent callback;
    private PurpleAiRuntime() {}

    public static boolean enabled(Context context) {
        try {
            Bundle metadata = context.getPackageManager().getApplicationInfo(context.getPackageName(),
                    PackageManager.GET_META_DATA).metaData;
            return metadata != null && metadata.getBoolean(FEATURE, false);
        } catch (Exception ignored) { return false; }
    }
    public static void start(Object receiver) {
        end();
        if (receiver instanceof InputMethodService && enabled((Context) receiver))
            service = new WeakReference<>((InputMethodService) receiver);
    }
    public static void end() {
        generation++;
        cancel();
        service.clear();
        dismiss();
    }
    private static void cancel() {
        operation++;
        AtomicBoolean current = cancellation; if (current != null) current.set(true);
        WhisperCapture currentCapture = capture; if (currentCapture != null) currentCapture.stop();
        BridgeClient currentBridge = bridge;
        if (currentBridge != null) currentBridge.disconnect();
    }
    private static void dismiss() {
        AlertDialog current = dialog; dialog = null;
        if (current != null) try { current.dismiss(); } catch (RuntimeException ignored) {}
    }
    private static void notifyUser(String value) {
        InputMethodService ime = service.get();
        if (ime != null) Toast.makeText(ime, value, Toast.LENGTH_LONG).show();
    }
    private static boolean show(AlertDialog value) {
        InputMethodService ime = service.get(); if (ime == null) return false;
        Window window = value.getWindow();
        if (window == null || ime.getWindow() == null || ime.getWindow().getWindow() == null) return false;
        window.setType(WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG);
        WindowManager.LayoutParams attributes = window.getAttributes();
        attributes.token = ime.getWindow().getWindow().getAttributes().token;
        window.setAttributes(attributes);
        // Field/text results must not enter screenshots or Android saved state.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        dismiss(); dialog = value;
        try { value.show(); return true; } catch (RuntimeException unavailable) { dialog = null; notifyUser("Open the keyboard again to use this action"); return false; }
    }
    public static void click(boolean whisper) {
        if (Looper.myLooper() != Looper.getMainLooper()) return;
        InputMethodService ime = service.get();
        if (ime == null || !enabled(ime)) return;
        if (busy.get()) { notifyUser("The previous action is still finishing"); return; }
        EditorInfo info = ime.getCurrentInputEditorInfo();
        if (info == null || SessionPolicy.sensitive(info.inputType, info.imeOptions)) {
            notifyUser("AI actions are unavailable in this private field"); return;
        }
        if (bridge == null) { offerConnection(ime); return; }
        Snapshot anchor = Snapshot.capture(ime, generation);
        if (anchor == null) { notifyUser("This editor does not expose a stable text selection"); return; }
        if (whisper) {
            if (ime.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                permissions(ime); return;
            }
            submit(anchor, null);
        } else {
            if (!SessionPolicy.bounded(anchor.selected)) { notifyUser("Select up to 8 KiB of text to translate"); return; }
            java.util.SortedMap<String,String> languages = new java.util.TreeMap<>();
            for (java.util.Locale locale : java.util.Locale.getAvailableLocales()) {
                String code = locale.getLanguage();
                if (!code.isEmpty()) languages.put(locale.getDisplayLanguage(), code);
            }
            String[] labels = languages.keySet().toArray(new String[0]);
            String[] codes = languages.values().toArray(new String[0]);
            show(new AlertDialog.Builder(ime).setTitle("Translate to…")
                    .setItems(labels, (d, which) -> {
                        if (anchor.matches(service.get(), generation)) submit(anchor, codes[which]);
                    }).setNegativeButton("Cancel", null).create());
        }
    }
    private static void permissions(Context context) {
        try { context.startActivity(new Intent().setClassName(context.getPackageName(),
                PurplePermissionActivity.class.getName()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
        catch (RuntimeException ignored) { notifyUser("Grant microphone and local-tools access in app permissions"); }
    }
    private static void offerConnection(InputMethodService ime) {
        show(new AlertDialog.Builder(ime).setTitle("Connect local tools")
                .setMessage("Connect to your existing local Whisper and Codex tools. Normal Gboard continues working without them.")
                .setNegativeButton("Cancel", null).setPositiveButton("Connect", (d, w) -> connect(ime)).create());
    }
    private static void connect(InputMethodService ime) {
        if (ime.checkSelfPermission("com.termux.permission.RUN_COMMAND") != PackageManager.PERMISSION_GRANTED) { permissions(ime); return; }
        if (callback != null) callback.cancel();
        nonce = UUID.randomUUID().toString(); connectDeadline = SystemClock.elapsedRealtime() + 30000;
        Intent result = new Intent().setClassName(ime.getPackageName(), PurpleBootstrapReceiver.class.getName())
                .setData(Uri.parse("purple-gboard-connect:" + nonce));
        callback = PendingIntent.getBroadcast(ime, 0, result,
                PendingIntent.FLAG_ONE_SHOT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0));
        Intent command = new Intent("com.termux.RUN_COMMAND").setClassName("com.termux", "com.termux.app.RunCommandService")
                .putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/home/.local/bin/purple-gboard-service")
                .putExtra("com.termux.RUN_COMMAND_ARGUMENTS", new String[]{"bootstrap"})
                .putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
                .putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
                .putExtra("com.termux.RUN_COMMAND_PENDING_INTENT", callback);
        try { if (ime.startService(command) == null) throw new IllegalStateException(); }
        catch (RuntimeException ignored) { callback.cancel(); callback = null; nonce = null; notifyUser("Local tools are not available"); }
    }
    static void bootstrapResult(Intent intent) {
        try {
            if (intent == null || nonce == null || SystemClock.elapsedRealtime() > connectDeadline
                    || !Uri.parse("purple-gboard-connect:" + nonce).equals(intent.getData())) return;
            nonce = null; callback = null; // Consume the one-shot authority before parsing.
            Bundle result = intent.getBundleExtra("result");
            if (result == null || result.getInt("exitCode", -1) != 0 || result.getInt("err", 0) != 0)
                throw new IllegalStateException();
            String output = result.getString("stdout");
            if (output == null || output.length() > 8192) throw new IllegalStateException();
            Object original = result.get("stdout_original_length");
            if (original != null && (!(original instanceof String)
                    || Long.parseLong((String)original) != output.length())) throw new IllegalStateException();
            JSONObject data = new JSONObject(output);
            if (!data.getBoolean("ok") || data.getInt("protocol") != 1) throw new IllegalStateException();
            bridge = new BridgeClient(data.getString("base_url"), data.getString("token"));
            notifyUser("Local tools connected. Choose Whisper or Translate.");
        } catch (Exception ignored) { bridge = null; notifyUser("Local tools could not connect"); }
    }
    private static void submit(Snapshot anchor, String language) {
        InputMethodService ime = service.get();
        if (ime == null || !anchor.matches(ime, generation) || !busy.compareAndSet(false, true)) return;
        final long request = ++operation;
        AtomicBoolean stopped = new AtomicBoolean(); cancellation = stopped;
        boolean voice = language == null;
        WhisperCapture recorder = voice ? new WhisperCapture() : null; capture = recorder;
        AlertDialog progress = new AlertDialog.Builder(ime).setTitle(voice ? "Whisper 3 dictation" : "AI Translate")
                .setMessage(voice ? "Preparing microphone…" : "Translating selected text…")
                .setNegativeButton("Cancel", (d,w) -> cancel())
                .setPositiveButton(voice ? "Stop recording" : "Working…", null).create();
        progress.setOnCancelListener(d -> cancel());
        progress.setOnShowListener(d -> {
            progress.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(voice);
            progress.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                recorder.stop(); progress.setMessage("Transcribing…"); v.setEnabled(false);
            });
        });
        if (!show(progress)) { cancel(); capture = null; busy.set(false); return; }
        BridgeClient currentBridge = bridge;
        new Thread(() -> {
            byte[] audio = null;
            try {
                // Check exact Whisper family and memory-only bridge before opening the microphone.
                currentBridge.verify(voice);
                if (stopped.get()) throw new InterruptedException();
                if (voice) {
                    MAIN.post(() -> { if (!stopped.get()) progress.setMessage("Recording — tap Stop when finished"); });
                    audio = recorder.record(stopped);
                }
                String text = currentBridge.execute(voice ? "transcribe" : "translate", anchor.selected, language, audio, stopped);
                MAIN.post(() -> {
                    if (request != operation || stopped.get() || !anchor.matches(service.get(), generation)) return;
                    dismiss();
                    InputMethodService current = service.get(); if (current == null) return;
                    TextView preview = new TextView(current); preview.setText(text); preview.setSaveEnabled(false);
                    preview.setTextIsSelectable(true); preview.setPadding(24,16,24,16);
                    show(new AlertDialog.Builder(current).setTitle(voice ? "Whisper transcript" : "Translation")
                            .setView(preview).setNegativeButton("Discard", null)
                            .setPositiveButton("Insert", (d,w) -> {
                                String replacement = text;
                                if (request == operation && !stopped.get() && SessionPolicy.bounded(replacement) && anchor.matches(service.get(), generation))
                                    anchor.connection.commitText(replacement, 1);
                                else notifyUser("The editor changed; original text was preserved");
                            }).create());
                });
            } catch (Exception failure) {
                MAIN.post(() -> { if (request == operation && !stopped.get() && anchor.session == generation) {
                    dismiss(); notifyUser(failure instanceof IllegalStateException ? failure.getMessage() : "Local action unavailable; original text preserved");
                }});
            } finally {
                if (audio != null) java.util.Arrays.fill(audio, (byte)0);
                if (recorder != null) recorder.stop();
                if (capture == recorder) capture = null;
                // Keep the action reserved until its queued UI result has been handled.
                MAIN.post(() -> busy.set(false));
            }
        }, "purple-gboard-action").start();
    }
    private static String text(CharSequence value) { return value == null ? "" : value.toString(); }
    private static final class Snapshot {
        final InputConnection connection;
        final long session;
        final String before, after, selected;
        final int start, end, offset;
        Snapshot(InputConnection connection, long session, String before, String after, String selected, ExtractedText position) {
            this.connection=connection; this.session=session; this.before=before; this.after=after; this.selected=selected;
            start=position.selectionStart; end=position.selectionEnd; offset=position.startOffset;
        }
        static Snapshot capture(InputMethodService ime, long generation) {
            try {
                InputConnection connection = ime.getCurrentInputConnection(); if (connection == null) return null;
                ExtractedTextRequest request = new ExtractedTextRequest(); request.hintMaxChars=128; request.hintMaxLines=1;
                ExtractedText position=connection.getExtractedText(request, 0);
                if (position==null) return null;
                String selected=text(connection.getSelectedText(0));
                if (selected.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > SessionPolicy.TEXT_BYTES) return null;
                return new Snapshot(connection, generation, text(connection.getTextBeforeCursor(64,0)),
                        text(connection.getTextAfterCursor(64,0)), selected, position);
            } catch (RuntimeException ignored) { return null; }
        }
        boolean matches(InputMethodService ime, long current) {
            if (ime == null || session != current || ime.getCurrentInputConnection()!=connection) return false;
            EditorInfo info=ime.getCurrentInputEditorInfo();
            if (info==null || SessionPolicy.sensitive(info.inputType,info.imeOptions)) return false;
            Snapshot now=capture(ime,current);
            return now!=null && now.start==start && now.end==end && now.offset==offset
                    && SessionPolicy.sameAnchor(session,current,before,after,selected,now.before,now.after,now.selected);
        }
    }
}
