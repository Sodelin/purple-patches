package android.os;

/** No Android thread or message queue is started by this fixture. */
public final class Handler {
    public Handler(Looper looper) {}
    public boolean post(Runnable work) { work.run(); return true; }
}
