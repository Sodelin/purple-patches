package dev.purple.patches.natune;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Objects;

/**
 * Exact N202610191717 coroutine cancellation ABI. The selecting Morphe patch
 * must pin the original APK/signer and pass the ORIGINAL export continuation.
 * No private reflection, context reconstruction or substitute Job is used.
 * Additional owner checks must include headroom/admission/deadline policy.
 * This class alone is neither a UI hook nor a physical storage quota.
 */
public final class NatuneCoroutineStop implements VerifiedStreamCopy.StopCheck {
    private final Object continuation;
    private final Method contextGetter;
    private final Method ensureActive;
    private final VerifiedStreamCopy.StopCheck owner;

    private NatuneCoroutineStop(Object continuation, Method contextGetter,
            Method ensureActive, VerifiedStreamCopy.StopCheck owner) {
        this.continuation = continuation;
        this.contextGetter = contextGetter;
        this.ensureActive = ensureActive;
        this.owner = owner;
    }

    public static NatuneCoroutineStop bind(Object continuation,
            VerifiedStreamCopy.StopCheck owner) throws IOException {
        Objects.requireNonNull(continuation, "continuation");
        Objects.requireNonNull(owner, "owner");
        owner.check();
        try {
            ClassLoader loader = continuation.getClass().getClassLoader();
            Class<?> base = Class.forName("r8.cf2", false, loader);
            Class<?> context = Class.forName("r8.yg2", false, loader);
            Class<?> nativeOwner = Class.forName("r8.w21", false, loader);
            if (!base.isInstance(continuation)) throw new IOException("Original continuation required");
            Method getter = base.getMethod("getContext");
            Method check = nativeOwner.getMethod("J", context);
            if (getter.getReturnType() != context || Modifier.isStatic(getter.getModifiers()) ||
                    check.getReturnType() != Void.TYPE || !Modifier.isStatic(check.getModifiers())) {
                throw new IOException("Original coroutine ABI differs");
            }
            NatuneCoroutineStop result = new NatuneCoroutineStop(continuation, getter, check, owner);
            result.check();
            return result;
        } catch (ReflectiveOperationException | SecurityException failure) {
            throw new IOException("Original coroutine ABI unavailable", failure);
        }
    }

    @Override public void check() throws IOException {
        owner.check();
        try {
            Object context = contextGetter.invoke(continuation);
            if (context == null) throw new IOException("Original context unavailable");
            ensureActive.invoke(null, context);
        } catch (InvocationTargetException failure) {
            // Preserve original Kotlin Job cancellation identity/type, rather
            // than turning it into a non-cancellation IO error or retry signal.
            Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw new IOException("Original context check failed", cause);
        } catch (ReflectiveOperationException | SecurityException failure) {
            throw new IOException("Original coroutine invocation unavailable", failure);
        }
        // An owner stop during the native check must not admit subsequent IO.
        owner.check();
    }
}
