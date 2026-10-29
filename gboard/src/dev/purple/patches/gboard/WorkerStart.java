package dev.purple.patches.gboard;

/** Reports failure to create/start a worker before any worker action is dispatched. */
final class WorkerStart {
    interface Starter { void start(Runnable work, String name); }

    private WorkerStart() {}

    static boolean launch(Runnable work, String name) {
        return launch(null, work, name);
    }

    // A starter must either start the worker or throw before starting it.
    static boolean launch(Starter starter, Runnable work, String name) {
        try {
            if (starter == null) new Thread(work, name).start();
            else starter.start(work, name);
        } catch (RuntimeException | OutOfMemoryError failure) {
            return false;
        }
        return true;
    }
}
