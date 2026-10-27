use crate::{jobs::Service, private, whisper, PROTOCOL, VERSION};
use serde_json::{json, Value};
use std::collections::HashMap;
use std::io::{Read, Write};
use std::net::TcpStream;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::time::{Duration, Instant};

const HEADER_CAP: usize = 8192;
const JSON_CAP: usize = 65536;
struct Request {
    method: String,
    path: String,
    headers: HashMap<String, String>,
    body: Vec<u8>,
}

fn request(stream: &mut TcpStream, token: &str) -> Result<Request, (u16, &'static str)> {
    stream
        .set_read_timeout(Some(Duration::from_secs(2)))
        .map_err(|_| (400, "Socket read unavailable"))?;
    stream
        .set_write_timeout(Some(Duration::from_secs(2)))
        .map_err(|_| (400, "Socket write unavailable"))?;
    let start = Instant::now();
    let mut bytes = Vec::new();
    // Header reads deliberately stop at the exact delimiter; no unbounded buffering.
    loop {
        if bytes.len() >= HEADER_CAP || start.elapsed() > Duration::from_secs(3) {
            return Err((413, "Request headers exceeded their bounds"));
        }
        let mut byte = [0u8; 1];
        if stream.read_exact(&mut byte).is_err() {
            return Err((400, "Incomplete HTTP headers"));
        }
        bytes.push(byte[0]);
        if bytes.ends_with(b"\r\n\r\n") {
            break;
        }
    }
    let text = std::str::from_utf8(&bytes).map_err(|_| (400, "Headers must be UTF-8"))?;
    let mut lines = text.split("\r\n");
    let line = lines.next().ok_or((400, "Missing HTTP request"))?;
    let parts: Vec<_> = line.split(' ').collect();
    if parts.len() != 3
        || parts[2] != "HTTP/1.1"
        || !parts[1].starts_with('/')
        || parts[1].contains(['?', '#', '%'])
    {
        return Err((400, "Invalid finite HTTP request"));
    }
    let mut headers = HashMap::new();
    for line in lines.filter(|s| !s.is_empty()) {
        let (name, value) = line.split_once(':').ok_or((400, "Invalid header"))?;
        if name.is_empty()
            || !name.bytes().all(|b| b.is_ascii_alphanumeric() || b == b'-')
            || value.bytes().any(|b| b < 32 && b != b'\t')
            || headers.len() >= 48
        {
            return Err((400, "Invalid header syntax"));
        }
        if headers
            .insert(name.to_ascii_lowercase(), value.trim().to_owned())
            .is_some()
        {
            return Err((400, "Duplicate headers are unsupported"));
        }
    }
    // Loopback is reachable by other Android applications. Bearer authentication is mandatory.
    // Browser callers are excluded even if they somehow possess a token; no CORS/OPTIONS route.
    if headers.contains_key("origin") || headers.contains_key("transfer-encoding") {
        return Err((403, "Browser Origin and transfer encodings are unsupported"));
    }
    let expected_host = format!(
        "127.0.0.1:{}",
        stream
            .local_addr()
            .map_err(|_| (400, "Local address unavailable"))?
            .port()
    );
    if headers.get("host") != Some(&expected_host) {
        return Err((403, "Exact loopback Host required"));
    }
    let bearer = headers
        .get("authorization")
        .and_then(|v| v.strip_prefix("Bearer "))
        .unwrap_or_default();
    if !private::equal_secret(bearer.as_bytes(), token.as_bytes()) {
        return Err((401, "Keyboard capability required"));
    }
    let length = match headers.get("content-length") {
        Some(value) if !value.is_empty() && value.bytes().all(|b| b.is_ascii_digit()) => value
            .parse::<usize>()
            .map_err(|_| (413, "Invalid body length"))?,
        Some(_) => return Err((400, "Invalid Content-Length")),
        None => 0,
    };
    let cap = if parts[1] == "/transcribe" {
        whisper::AUDIO_CAP
    } else {
        JSON_CAP
    };
    if length > cap {
        return Err((413, "Request body exceeded its accepted bound"));
    }
    if (parts[0] != "POST" || parts[1] == "/shutdown") && length != 0 {
        return Err((400, "Unexpected request body"));
    }
    if parts[0] == "POST" && matches!(parts[1], "/query" | "/transcribe") && length == 0 {
        return Err((400, "Request body is required"));
    }
    let mut body = vec![0u8; length];
    let mut offset = 0;
    while offset < length {
        if start.elapsed() > Duration::from_secs(8) {
            return Err((408, "Request upload timed out"));
        }
        let count = stream
            .read(&mut body[offset..])
            .map_err(|_| (400, "Incomplete request body"))?;
        if count == 0 {
            return Err((400, "Incomplete request body"));
        }
        offset += count;
    }
    Ok(Request {
        method: parts[0].into(),
        path: parts[1].into(),
        headers,
        body,
    })
}

pub fn handle(
    stream: &mut TcpStream,
    token: &str,
    service: &Arc<Service>,
    stop: &AtomicBool,
) -> Result<(), String> {
    let request = match request(stream, token) {
        Ok(request) => request,
        Err((code, error)) => return respond(stream, code, &json!({"error":error})),
    };
    let result = match (request.method.as_str(), request.path.as_str()) {
        ("GET", "/health") => Ok(json!({"ok":true,"protocol":PROTOCOL,"version":VERSION,"keyboard_data_path":"memory-only-v1","whisper_family":service.whisper_family()})),
        ("GET", "/models") => service.models(),
        ("POST", "/query") => {
            if !request
                .headers
                .get("content-type")
                .is_some_and(|v| v == "application/json" || v.starts_with("application/json;"))
            {
                return respond(
                    stream,
                    415,
                    &json!({"error":"Query requires application/json"}),
                );
            }
            service.query(&request.body)
        }
        ("POST", "/transcribe") => {
            if !request.headers.get("content-type").is_some_and(|v| {
                matches!(
                    v.as_str(),
                    "audio/wav" | "audio/x-wav" | "application/octet-stream"
                )
            }) {
                return respond(
                    stream,
                    415,
                    &json!({"error":"Transcription requires a PCM WAV body"}),
                );
            }
            service.transcribe(request.body)
        }
        ("POST", "/shutdown") => {
            stop.store(true, Ordering::Relaxed);
            Ok(json!({"stopping":true}))
        }
        (method, path) if path.starts_with("/jobs/") && matches!(method, "GET" | "DELETE") => {
            let id = &path[6..];
            if method == "GET" {
                service.job(id)
            } else {
                service.cancel(id)
            }
        }
        _ => return respond(stream, 404, &json!({"error":"Unknown keyboard operation"})),
    };
    match result {
        Ok(value) => respond(stream, 200, &value),
        Err(error) => {
            let code = if error == "Unknown request" {
                404
            } else if error.contains("already running") {
                409
            } else if error.contains("Whisper unavailable") || error.starts_with("Installed CLI") {
                503
            } else {
                400
            };
            respond(stream, code, &json!({"error":error}))
        }
    }
}

fn respond(stream: &mut TcpStream, code: u16, value: &Value) -> Result<(), String> {
    let body = serde_json::to_vec(value).map_err(|e| e.to_string())?;
    let reason = match code {
        200 => "OK",
        400 => "Bad Request",
        401 => "Unauthorized",
        403 => "Forbidden",
        404 => "Not Found",
        408 => "Request Timeout",
        409 => "Conflict",
        413 => "Content Too Large",
        415 => "Unsupported Media Type",
        503 => "Service Unavailable",
        _ => "Error",
    };
    write!(stream,"HTTP/1.1 {code} {reason}\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: {}\r\nConnection: close\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\n\r\n",body.len())
        .and_then(|_|stream.write_all(&body)).map_err(|_|"HTTP reply connection closed".into())
}

pub fn local_call(port: u16, token: &str, method: &str, path: &str) -> Result<Value, String> {
    let address = std::net::SocketAddr::from(([127, 0, 0, 1], port));
    let mut stream = TcpStream::connect_timeout(&address, Duration::from_secs(1))
        .map_err(|_| "Owned bridge is unreachable")?;
    stream
        .set_read_timeout(Some(Duration::from_secs(2)))
        .map_err(|_| "Health read unavailable")?;
    stream
        .set_write_timeout(Some(Duration::from_secs(2)))
        .map_err(|_| "Health write unavailable")?;
    write!(stream,"{method} {path} HTTP/1.1\r\nHost: 127.0.0.1:{port}\r\nAuthorization: Bearer {token}\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")
        .map_err(|_|"Owned bridge health write failed")?;
    let mut bytes = Vec::new();
    stream
        .take(16385)
        .read_to_end(&mut bytes)
        .map_err(|_| "Owned bridge health read failed")?;
    if bytes.len() > 16384 {
        return Err("Bridge health response exceeded its bound".into());
    }
    let delimiter = bytes
        .windows(4)
        .position(|part| part == b"\r\n\r\n")
        .ok_or("Invalid bridge health response")?;
    if !bytes.starts_with(b"HTTP/1.1 200 ") {
        return Err("Owned bridge rejected its private health capability".into());
    }
    serde_json::from_slice(&bytes[delimiter + 4..]).map_err(|_| "Invalid bridge health JSON".into())
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::net::TcpListener;
    fn exchange(headers: &str, path: &str, method: &str, body: &[u8]) -> (String, bool) {
        let scratch = private::Scratch::new();
        let listener = TcpListener::bind("127.0.0.1:0").unwrap();
        let port = listener.local_addr().unwrap().port();
        let token = "a".repeat(64);
        let stop = Arc::new(AtomicBool::new(false));
        let service = Service::new(&scratch.0, Arc::clone(&stop)).unwrap();
        let thread_stop = Arc::clone(&stop);
        let thread = std::thread::spawn(move || {
            let (mut stream, _) = listener.accept().unwrap();
            handle(&mut stream, &token, &service, &thread_stop).unwrap();
            service.shutdown();
        });
        let mut client = TcpStream::connect(("127.0.0.1", port)).unwrap();
        write!(client,"{method} {path} HTTP/1.1\r\nHost: 127.0.0.1:{port}\r\n{headers}Content-Length: {}\r\n\r\n",body.len()).unwrap();
        client.write_all(body).unwrap();
        let mut response = String::new();
        client.read_to_string(&mut response).unwrap();
        thread.join().unwrap();
        (response, stop.load(Ordering::Relaxed))
    }
    #[test]
    fn bearer_health_and_scoped_shutdown_are_real_http() {
        let auth = format!("Authorization: Bearer {}\r\n", "a".repeat(64));
        let (response, _) = exchange(&auth, "/health", "GET", b"");
        assert!(response.starts_with("HTTP/1.1 200 "));
        assert!(response.contains("\"protocol\":1"));
        let (response, stopping) = exchange(&auth, "/shutdown", "POST", b"");
        assert!(response.starts_with("HTTP/1.1 200 "));
        assert!(stopping);
    }
    #[test]
    fn rejects_no_auth_browser_origin_duplicate_length_and_unknown_routes() {
        let (response, _) = exchange("", "/health", "GET", b"");
        assert!(response.starts_with("HTTP/1.1 401 "));
        let auth = format!("Authorization: Bearer {}\r\n", "a".repeat(64));
        let (response, _) = exchange(
            &(auth.clone() + "Origin: https://example.org\r\n"),
            "/health",
            "GET",
            b"",
        );
        assert!(response.starts_with("HTTP/1.1 403 "));
        let (response, _) = exchange(
            &(auth.clone() + "Content-Length: 0\r\n"),
            "/health",
            "GET",
            b"",
        );
        assert!(response.starts_with("HTTP/1.1 400 "));
        let (response, _) = exchange(&auth, "/command", "POST", b"");
        assert!(response.starts_with("HTTP/1.1 404 "));
    }
    #[test]
    fn rejects_unbounded_and_invalid_audio_before_recording() {
        let auth = format!(
            "Authorization: Bearer {}\r\nContent-Type: audio/wav\r\n",
            "a".repeat(64)
        );
        let (response, _) = exchange(&auth, "/transcribe", "POST", b"not wav");
        assert!(response.starts_with("HTTP/1.1 400 "));
    }
}
