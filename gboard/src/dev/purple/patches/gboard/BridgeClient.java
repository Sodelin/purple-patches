package dev.purple.patches.gboard;

import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/** Memory-only transport; a bridge must attest the selected data-path contract. */
final class BridgeClient {
    private final String endpoint, token;
    private volatile HttpURLConnection connection;
    private volatile String job;
    BridgeClient(String endpoint, String token) {
        if (!SessionPolicy.localEndpoint(endpoint) || token == null || !token.matches("[A-Za-z0-9_-]{32,256}"))
            throw new IllegalArgumentException("Invalid local connection");
        this.endpoint = endpoint; this.token = token;
    }
    private JSONObject request(String method, String path, byte[] body, String contentType) throws Exception {
        HttpURLConnection http = (HttpURLConnection) new URL(endpoint + path).openConnection();
        connection = http;
        try {
            http.setInstanceFollowRedirects(false);
            http.setConnectTimeout(3000); http.setReadTimeout(5000);
            http.setRequestMethod(method);
            http.setRequestProperty("Authorization", "Bearer " + token);
            if (body != null) {
                http.setDoOutput(true); http.setFixedLengthStreamingMode(body.length);
                http.setRequestProperty("Content-Type", contentType);
                try (java.io.OutputStream out = http.getOutputStream()) { out.write(body); }
            }
            if (http.getResponseCode() != 200 && http.getResponseCode() != 202)
                throw new IllegalStateException("The local bridge rejected this request");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (InputStream input = http.getInputStream()) {
                byte[] chunk = new byte[4096]; int count;
                while ((count = input.read(chunk)) != -1) {
                    if (bytes.size() + count > 131072) throw new IllegalStateException("Response too large");
                    bytes.write(chunk, 0, count);
                }
            }
            return new JSONObject(bytes.toString(StandardCharsets.UTF_8.name()));
        } finally { http.disconnect(); if (connection == http) connection = null; }
    }
    void verify(boolean voice) throws Exception {
        JSONObject health = request("GET", "/health", null, null);
        if (!"memory-only-v1".equals(health.optString("keyboard_data_path")))
            throw new IllegalStateException("Update the local bridge before using keyboard text or audio");
        if (voice && !SessionPolicy.whisperPreflightSupported(health.optString("whisper_preflight")))
            throw new IllegalStateException("Update the local bridge before using Whisper 3");
        if (voice && !"whisper-large-v3".equals(health.optString("whisper_family")))
            throw new IllegalStateException("Whisper 3 is not configured; another model will not be substituted");
    }
    String execute(String mode, String text, String language, byte[] wav, AtomicBoolean cancelled) throws Exception {
        verify(wav != null);
        if (cancelled.get()) throw new InterruptedException();
        JSONObject started;
        if (wav == null) {
            JSONObject data = new JSONObject().put("mode", "translate").put("prompt", text)
                    .put("context", "").put("target_language", language)
                    .put("model", "gpt-6.1-sol").put("effort", "medium").put("tier", "standard");
            started = request("POST", "/query", data.toString().getBytes(StandardCharsets.UTF_8), "application/json");
        } else started = request("POST", "/transcribe", wav, "audio/wav");
        job = started.getString("id");
        if (!job.matches("[A-Za-z0-9_-]{1,96}")) throw new IllegalStateException("Invalid job identity");
        long end = android.os.SystemClock.elapsedRealtime() + 120000;
        try {
            while (android.os.SystemClock.elapsedRealtime() < end && !cancelled.get()) {
                JSONObject result = request("GET", "/jobs/" + job, null, null);
                switch (result.getString("state")) {
                    case "completed":
                        String value = result.getString("text");
                        if (!SessionPolicy.bounded(value)) throw new IllegalStateException("Empty or oversized result");
                        return value;
                    case "failed": throw new IllegalStateException("The local text/audio request failed");
                    case "cancelled": throw new InterruptedException();
                    default: Thread.sleep(250);
                }
            }
            throw new IllegalStateException(cancelled.get() ? "Cancelled" : "Request timed out");
        } finally {
            // Explicit cancel is idempotent for this owned job; no request replay.
            if (job != null) { try { request("DELETE", "/jobs/" + job, null, null); } catch (Exception ignored) {} }
            job = null;
        }
    }
    void disconnect() { HttpURLConnection current = connection; if (current != null) current.disconnect(); }
}
