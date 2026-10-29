package dev.purple.patches.gboard;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.SystemClock;
import java.util.concurrent.atomic.AtomicBoolean;

/** Never writes audio to a file and never owns a recorder beyond one action. */
final class WhisperCapture {
    private final CaptureSession session = new CaptureSession();

    // The recording worker owns stop/release. UI cancellation never waits on AudioRecord.
    void stop() { session.stop(); }

    byte[] record(AtomicBoolean cancelled) throws Exception {
        return session.record(() -> {
            int minimum = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            if (minimum <= 0) throw new IllegalStateException("Microphone format unavailable");
            AudioRecord owned = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, 16000,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, Math.max(minimum, 8192));
            return new CaptureSession.Source() {
                public void start() {
                    if (owned.getState() != AudioRecord.STATE_INITIALIZED) throw new IllegalStateException("Microphone unavailable");
                    owned.startRecording();
                    if (owned.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING)
                        throw new IllegalStateException("Microphone did not start");
                }
                public int read(byte[] destination, int offset, int length) {
                    return owned.read(destination, offset, length, AudioRecord.READ_NON_BLOCKING);
                }
                public void close() {
                    try { owned.stop(); } catch (RuntimeException ignored) {}
                    owned.release();
                }
            };
        }, new CaptureSession.Clock() {
            public long nowMillis() { return SystemClock.elapsedRealtime(); }
            public void pause(long millis) throws InterruptedException { Thread.sleep(millis); }
        }, cancelled);
    }
}
