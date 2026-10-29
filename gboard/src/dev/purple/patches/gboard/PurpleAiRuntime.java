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
    private static volatile WeakReference<InputMethodService> service = new WeakReference<>(null);
    private static volatile long generation;
    private static long operation;
    private static volatile BridgeClient bridge;
    private static volatile AtomicBoolean cancellation;
    private static volatile WhisperCapture capture;
    private static final AtomicBoolean busy = new AtomicBoolean();
    private static AlertDialog dialog;
    private static final Object BOOTSTRAP_LOCK = new Object();
    private static final SessionPolicy.BootstrapAttempt bootstrap = new SessionPolicy.BootstrapAttempt();
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
        synchronized (BOOTSTRAP_LOCK) {
            end();
            if (receiver instanceof InputMethodService && enabled((Context) receiver))
                service = new WeakReference<>((InputMethodService) receiver);
        }
    }
    public static void end() {
        synchronized (BOOTSTRAP_LOCK) {
            generation++;
            clearPendingBootstrap();
            service.clear();
        }
        cancel();
        dismiss();
    }
    /** Caller owns BOOTSTRAP_LOCK; canceling a callback does not stop an already launched service. */
    private static void clearPendingBootstrap() {
        bootstrap.invalidate();
        PendingIntent pending = callback; callback = null;
        if (pending != null) try { pending.cancel(); } catch (RuntimeException ignored) {}
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
                permissions(ime, SessionPolicy.PERMISSION_MICROPHONE); return;
            }
            submit(anchor, null, null);
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
                        if (anchor.matches(service.get(), generation)) chooseTranslationEffort(anchor, codes[which]);
                    }).setNegativeButton("Cancel", null).create());
        }
    }
    private static void chooseTranslationEffort(Snapshot anchor, String language) {
        InputMethodService ime = service.get();
        if (ime == null || !anchor.matches(ime, generation)) return;
        String[] labels = {"Medium", "Max"};
        String[] efforts = {"medium", "max"};
        show(new AlertDialog.Builder(ime).setTitle("Sol 6.1 reasoning")
                .setItems(labels, (d, which) -> {
                    if (which < 0 || which >= efforts.length) return;
                    if (anchor.matches(service.get(), generation)) submit(anchor, language, efforts[which]);
                    else notifyUser("The selection changed; select text again");
                }).setNegativeButton("Cancel", null).create());
    }
    private static void permissions(Context context, String scope) {
        if (SessionPolicy.permissionsFor(scope).length != 1) return;
        try { context.startActivity(new Intent().setClassName(context.getPackageName(),
                PurplePermissionActivity.class.getName()).putExtra(PurplePermissionActivity.EXTRA_SCOPE, scope)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
        catch (RuntimeException ignored) {
            notifyUser(SessionPolicy.PERMISSION_MICROPHONE.equals(scope)
                    ? "Allow microphone access for dictation in app permissions"
                    : "Allow local-tools access in app permissions");
        }
    }
    private static void offerConnection(InputMethodService ime) {
        final long offeredSession = generation;
        show(new AlertDialog.Builder(ime).setTitle("Connect local tools")
                .setMessage("Termux gives this keyboard permission to run commands as Termux. These actions use your existing local Whisper and Codex tools. Dictation asks for microphone access separately.")
                .setNegativeButton("Cancel", null).setPositiveButton("Connect", (d, w) -> connect(ime, offeredSession)).create());
    }
    private static void connect(InputMethodService ime, long offeredSession) {
        synchronized (BOOTSTRAP_LOCK) {
            if (!SessionPolicy.bootstrapOfferCurrent(offeredSession, generation, ime, service.get())
                    || !enabled(ime)) return;
            if (ime.checkSelfPermission("com.termux.permission.RUN_COMMAND") != PackageManager.PERMISSION_GRANTED) { permissions(ime, SessionPolicy.PERMISSION_LOCAL_TOOLS); return; }
            clearPendingBootstrap();
            try {
                String route = "purple-gboard-connect:" + UUID.randomUUID();
                bootstrap.begin(offeredSession, route, SystemClock.elapsedRealtime() + 30000);
                Intent result = new Intent().setClassName(ime.getPackageName(), PurpleBootstrapReceiver.class.getName())
                        .setData(Uri.parse(route));
                callback = PendingIntent.getBroadcast(ime, 0, result,
                        PendingIntent.FLAG_ONE_SHOT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0));
                Intent command = new Intent("com.termux.RUN_COMMAND").setClassName("com.termux", "com.termux.app.RunCommandService")
                        .putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/home/.local/bin/purple-gboard-service")
                        .putExtra("com.termux.RUN_COMMAND_ARGUMENTS", new String[]{"bootstrap"})
                        .putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
                        .putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
                        // Best effort on reviewed Termux source only; stdout remains a release blocker.
                        .putExtra("com.termux.RUN_COMMAND_BACKGROUND_CUSTOM_LOG_LEVEL", "0")
                        .putExtra("com.termux.RUN_COMMAND_PENDING_INTENT", callback);
                if (ime.startService(command) == null) throw new IllegalStateException();
            } catch (RuntimeException ignored) { clearPendingBootstrap(); notifyUser("Local tools are not available"); }
        }
    }
    static void bootstrapResult(Intent intent) {
        synchronized (BOOTSTRAP_LOCK) {
            try {
                String route = intent == null || intent.getData() == null ? null : intent.getData().toString();
                if (!bootstrap.consume(generation, service.get() != null, route, SystemClock.elapsedRealtime())) {
                    if (!bootstrap.pending()) clearPendingBootstrap();
                    return;
                }
                callback = null; // Consume the one-shot authority before parsing.
                Bundle result = intent.getBundleExtra("result");
                if (result == null || !SessionPolicy.bootstrapSuccess(result.get("exitCode"), result.get("err")))
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
            } catch (Exception ignored) { clearPendingBootstrap(); bridge = null; notifyUser("Local tools could not connect"); }
        }
    }
    private static void submit(Snapshot anchor, String language, String effort) {
        InputMethodService ime = service.get();
        if (language == null ? effort != null : !SessionPolicy.translationEffortAllowed(effort)) return;
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
                boolean reachedLimit = false;
                if (voice) {
                    MAIN.post(() -> { if (!stopped.get() && request == operation && dialog == progress)
                        progress.setMessage("Recording for up to " + SessionPolicy.AUDIO_SECONDS + " seconds. Tap Stop when finished."); });
                    audio = recorder.record(stopped);
                    reachedLimit = recorder.limitReached();
                    final boolean limited = reachedLimit;
                    MAIN.post(() -> { if (!stopped.get() && request == operation && dialog == progress) {
                        progress.setMessage(limited ? "Recording limit reached. Transcribing the captured audio…" : "Transcribing…");
                        progress.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                    }});
                }
                final boolean recordingLimited = reachedLimit;
                String text = currentBridge.execute(anchor.selected, language, effort, audio, stopped);
                MAIN.post(() -> {
                    if (request != operation || stopped.get() || !anchor.matches(service.get(), generation)) return;
                    dismiss();
                    InputMethodService current = service.get(); if (current == null) return;
                    TextView preview = new TextView(current); preview.setText(text); preview.setSaveEnabled(false);
                    preview.setTextIsSelectable(true); preview.setPadding(24,16,24,16);
                    AlertDialog.Builder result = new AlertDialog.Builder(current).setTitle(voice ? "Whisper transcript" : "Translation")
                            .setView(preview).setNegativeButton("Close", null);
                    if (recordingLimited) result.setMessage("Recording reached the " + SessionPolicy.AUDIO_SECONDS
                            + "-second limit. Only the captured audio is included.");
                    AlertDialog resultDialog = result.setPositiveButton("Insert", null).create();
                    SessionPolicy.InsertionAttempt insertion = new SessionPolicy.InsertionAttempt();
                    resultDialog.setOnShowListener(d -> {
                        android.widget.Button insert = resultDialog.getButton(AlertDialog.BUTTON_POSITIVE);
                        insert.setOnClickListener(v -> {
                            insert.setEnabled(false);
                            SessionPolicy.InsertionOutcome outcome = insertion.attempt(
                                    () -> request == operation && !stopped.get() && SessionPolicy.bounded(text)
                                            && anchor.matches(service.get(), generation),
                                    () -> anchor.connection.commitText(text, 1));
                            if (outcome == SessionPolicy.InsertionOutcome.REQUEST_SENT)
                                notifyUser("Insertion requested. Check the editor before closing this preview.");
                            else if (outcome == SessionPolicy.InsertionOutcome.EDITOR_CHANGED)
                                notifyUser("The editor changed. Your result remains in this preview.");
                            else if (outcome == SessionPolicy.InsertionOutcome.UNKNOWN)
                                notifyUser("Insertion was not confirmed. Check the editor before trying again.");
                        });
                    });
                    show(resultDialog);
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
    private static final class Snapshot {
        final InputConnection connection;
        final long session;
        final String before, after, selected;
        final int start, end, offset;
        Snapshot(InputConnection connection, long session, SessionPolicy.EditorSnapshot snapshot) {
            this.connection=connection; this.session=session; this.before=snapshot.before;
            this.after=snapshot.after; this.selected=snapshot.selected;
            start=snapshot.start; end=snapshot.end; offset=snapshot.offset;
        }
        static Snapshot capture(InputMethodService ime, long generation) {
            try {
                InputConnection connection = ime.getCurrentInputConnection(); if (connection == null) return null;
                ExtractedTextRequest request = new ExtractedTextRequest(); request.hintMaxChars=128; request.hintMaxLines=1;
                SessionPolicy.EditorSnapshot snapshot=SessionPolicy.captureEditor(new SessionPolicy.EditorReader() {
                    public SessionPolicy.EditorPosition position() {
                        ExtractedText value=connection.getExtractedText(request,0);
                        return value==null ? null : new SessionPolicy.EditorPosition(
                                value.selectionStart,value.selectionEnd,value.startOffset);
                    }
                    public CharSequence selected() { return connection.getSelectedText(0); }
                    public CharSequence before() { return connection.getTextBeforeCursor(64,0); }
                    public CharSequence after() { return connection.getTextAfterCursor(64,0); }
                });
                return snapshot==null ? null : new Snapshot(connection,generation,snapshot);
            } catch (RuntimeException ignored) { return null; }
        }
        boolean matches(InputMethodService ime, long current) {
            if (ime == null || session != current || ime.getCurrentInputConnection()!=connection) return false;
            EditorInfo info=ime.getCurrentInputEditorInfo();
            if (info==null || SessionPolicy.sensitive(info.inputType,info.imeOptions)) return false;
            Snapshot now=capture(ime,current);
            EditorInfo finalInfo=ime.getCurrentInputEditorInfo();
            return now!=null && now.connection==connection && ime.getCurrentInputConnection()==connection
                    && finalInfo!=null && !SessionPolicy.sensitive(finalInfo.inputType,finalInfo.imeOptions)
                    && now.start==start && now.end==end && now.offset==offset
                    && SessionPolicy.sameAnchor(session,current,before,after,selected,now.before,now.after,now.selected);
        }
    }
}
