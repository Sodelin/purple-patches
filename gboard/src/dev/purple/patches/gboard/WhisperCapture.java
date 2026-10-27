package dev.purple.patches.gboard;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicBoolean;

/** Never writes audio to a file and never owns a recorder beyond one action. */
final class WhisperCapture {
    private volatile AudioRecord recorder;
    private final AtomicBoolean stop = new AtomicBoolean();
    void stop() { stop.set(true); AudioRecord r = recorder; if (r != null) try { r.stop(); } catch (RuntimeException ignored) {} }
    byte[] record(AtomicBoolean cancelled) throws Exception {
        int minimum = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (minimum <= 0) throw new IllegalStateException("Microphone format unavailable");
        AudioRecord owned = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, 16000,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, Math.max(minimum, 8192));
        recorder = owned;
        ByteArrayOutputStream pcm = new ByteArrayOutputStream();
        try {
            if (owned.getState() != AudioRecord.STATE_INITIALIZED) throw new IllegalStateException("Microphone unavailable");
            if (stop.get() || cancelled.get()) throw new InterruptedException();
            owned.startRecording();
            if (owned.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) throw new IllegalStateException("Microphone did not start");
            byte[] block = new byte[4096]; int cap = SessionPolicy.AUDIO_SECONDS * 32000;
            while (!stop.get() && !cancelled.get() && pcm.size() < cap) {
                int count = owned.read(block, 0, Math.min(block.length, cap - pcm.size()));
                if (count < 0) { if (stop.get()) break; throw new IllegalStateException("Recording failed"); }
                if (count > 0) pcm.write(block, 0, count);
            }
            if (cancelled.get()) throw new InterruptedException();
            if (pcm.size() < 2) throw new IllegalStateException("No audio recorded");
            byte[] audio = pcm.toByteArray();
            ByteBuffer wav = ByteBuffer.allocate(44 + audio.length).order(ByteOrder.LITTLE_ENDIAN);
            wav.put(new byte[]{'R','I','F','F'}).putInt(36 + audio.length).put(new byte[]{'W','A','V','E','f','m','t',' '})
                    .putInt(16).putShort((short)1).putShort((short)1).putInt(16000).putInt(32000)
                    .putShort((short)2).putShort((short)16).put(new byte[]{'d','a','t','a'}).putInt(audio.length).put(audio);
            java.util.Arrays.fill(audio, (byte)0);
            return wav.array();
        } finally {
            try { owned.stop(); } catch (RuntimeException ignored) {}
            owned.release(); recorder = null;
        }
    }
}
