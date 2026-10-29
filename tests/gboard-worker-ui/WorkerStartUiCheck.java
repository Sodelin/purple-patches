package dev.purple.patches.gboard;

import android.app.AlertDialog;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

/** Calls the actual private cleanup method with synthetic host-only UI state. */
public final class WorkerStartUiCheck {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static Field field(String name) throws Exception {
        Field result = PurpleAiRuntime.class.getDeclaredField(name);
        result.setAccessible(true);
        return result;
    }
    private static AtomicBoolean busy() throws Exception {
        return (AtomicBoolean) field("busy").get(null);
    }
    private static Method cleanup() throws Exception {
        Method method = PurpleAiRuntime.class.getDeclaredMethod("workerStartFailed", long.class,
                AtomicBoolean.class, AlertDialog.class, WhisperCapture.class, String.class);
        method.setAccessible(true);
        return method;
    }
    private static boolean recorderStopped(WhisperCapture recorder) throws Exception {
        Field session = WhisperCapture.class.getDeclaredField("session");
        session.setAccessible(true);
        Field stopped = CaptureSession.class.getDeclaredField("stopped");
        stopped.setAccessible(true);
        return ((AtomicBoolean) stopped.get(session.get(recorder))).get();
    }
    private static void current(boolean failDismiss) throws Exception {
        AtomicBoolean stopped = new AtomicBoolean();
        WhisperCapture recorder = new WhisperCapture();
        AlertDialog progress = new AlertDialog();
        progress.failDismiss = failDismiss;
        field("operation").setLong(null, 40);
        field("capture").set(null, recorder);
        field("dialog").set(null, progress);
        busy().set(true);
        cleanup().invoke(null, 40L, stopped, progress, recorder, "synthetic");
        check(stopped.get() && recorderStopped(recorder), "current request was not stopped");
        check(field("capture").get(null) == null && field("dialog").get(null) == null,
                "current capture or dialog was retained");
        check(progress.dismissed && !busy().get() && field("operation").getLong(null) == 41,
                "current cleanup state differs");
    }
    private static void stale() throws Exception {
        AtomicBoolean oldStopped = new AtomicBoolean();
        WhisperCapture oldRecorder = new WhisperCapture();
        WhisperCapture newRecorder = new WhisperCapture();
        AlertDialog oldDialog = new AlertDialog();
        AlertDialog newDialog = new AlertDialog();
        field("operation").setLong(null, 51);
        field("capture").set(null, newRecorder);
        field("dialog").set(null, newDialog);
        busy().set(true);
        cleanup().invoke(null, 50L, oldStopped, oldDialog, oldRecorder, "synthetic");
        check(oldStopped.get(), "old request was not stopped");
        check(field("capture").get(null) == newRecorder && field("dialog").get(null) == newDialog,
                "old request changed newer capture or dialog");
        check(busy().get(), "STALE_REQUEST_RELEASED_NEWER_BUSY");
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Select current or stale");
        if ("current".equals(args[0])) {
            current(false);
            current(true);
            System.out.println("CURRENT_CLEANUP_CONTROLS_PASSED");
        } else if ("stale".equals(args[0])) {
            stale();
            System.out.println("STALE_CLEANUP_PRESERVED_NEWER_BUSY");
        } else throw new IllegalArgumentException("Unknown mode");
    }
}
