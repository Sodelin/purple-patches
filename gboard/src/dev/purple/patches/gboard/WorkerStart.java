package dev.purple.patches.gboard;

/** Does not dispatch a worker action until the thread start has returned successfully. */
final class WorkerStart {
    interface Starter { void start(Runnable work, String name); }
    private static final class Gate { boolean accepted; }

    private WorkerStart() {}

    static boolean launch(Runnable work, String name) {
        return launch(null, work, name);
    }

    // A starter must schedule asynchronously and return without waiting for the work.
    static boolean launch(Starter starter, Runnable work, String name) {
        try {
            Gate gate = new Gate();
            Runnable guarded = () -> {
                synchronized (gate) { if (!gate.accepted) return; }
                work.run();
            };
            synchronized (gate) {
                if (starter == null) new Thread(guarded, name).start();
                else starter.start(guarded, name);
                gate.accepted = true;
            }
        } catch (RuntimeException | OutOfMemoryError failure) {
            return false;
        }
        return true;
    }
}
