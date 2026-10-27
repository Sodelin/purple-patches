//! Dedicated keyboard lifecycle and finite authenticated HTTP adapter.
//! No API key or shared app-server control is exposed to the Android client.
mod cli;
mod memory;
mod vault;
mod http;
mod jobs;
mod private;
mod whisper;

use private::{private_dir, read, write_atomic, Lock};
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use std::fs::{self, OpenOptions};
use std::io::Write;
use std::net::{Ipv4Addr, SocketAddrV4, TcpListener};
use std::os::unix::fs::MetadataExt;
use std::os::unix::fs::OpenOptionsExt;
use std::os::unix::process::CommandExt;
use std::path::{Path, PathBuf};
use std::process::{Command, Stdio};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::thread;
use std::time::{Duration, Instant};

pub const PROTOCOL: u32 = 1;
pub const VERSION: &str = env!("CARGO_PKG_VERSION");
const RECORD: &str = "bridge.age";

#[derive(Clone, Debug, Serialize, Deserialize, PartialEq)]
#[serde(deny_unknown_fields)]
struct Identity {
    pid: u32,
    birth_ticks: u64,
    uid: u32,
    executable: PathBuf,
    executable_device: u64,
    executable_inode: u64,
    boot: String,
}

impl Identity {
    fn read(pid: u32) -> Result<Self, String> {
        let root = PathBuf::from(format!("/proc/{pid}"));
        let stat = fs::read_to_string(root.join("stat")).map_err(|_| "Process unavailable")?;
        // comm may contain spaces and ')' characters. The last ')' precedes state.
        let rest = stat.rsplit_once(')').ok_or("Invalid process identity")?.1;
        let birth_ticks = rest
            .split_whitespace()
            .nth(19)
            .and_then(|s| s.parse::<u64>().ok())
            .ok_or("Invalid process birth")?;
        let uid = fs::read_to_string(root.join("status"))
            .map_err(|_| "Process status unavailable")?
            .lines()
            .find_map(|line| {
                line.strip_prefix("Uid:")
                    .and_then(|s| s.split_whitespace().next())
            })
            .and_then(|s| s.parse::<u32>().ok())
            .ok_or("Invalid process UID")?;
        let executable = fs::read_link(root.join("exe")).map_err(|_| "Executable unavailable")?;
        let executable = PathBuf::from(executable.to_string_lossy().trim_end_matches(" (deleted)"));
        let metadata =
            fs::metadata(root.join("exe")).map_err(|_| "Executable metadata unavailable")?;
        Ok(Self {
            pid,
            birth_ticks,
            uid,
            executable,
            executable_device: metadata.dev(),
            executable_inode: metadata.ino(),
            boot: fs::read_to_string("/proc/sys/kernel/random/boot_id")
                .map_err(|_| "Boot identity unavailable")?
                .trim()
                .to_owned(),
        })
    }
    fn current(&self) -> bool {
        Self::read(self.pid).is_ok_and(|identity| identity == *self)
            && self.uid == unsafe { libc::getuid() }
            && std::env::current_exe().is_ok_and(|exe| exe == self.executable)
    }
}

#[derive(Clone, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
struct Record {
    protocol: u32,
    #[serde(default)]
    version: String,
    port: u16,
    token: String,
    identity: Identity,
}

impl Record {
    fn load(state: &Path) -> Result<Self, String> {
        let record: Self = serde_json::from_slice(&vault::open(&state.join(RECORD))?)
            .map_err(|_| "Invalid bridge record")?;
        if record.protocol != PROTOCOL || record.port == 0 || !private::token_ok(&record.token) {
            return Err("Invalid bridge record values".into());
        }
        Ok(record)
    }
    fn healthy(&self) -> bool {
        self.version == VERSION
            && self.identity.current()
            && http::local_call(self.port, &self.token, "GET", "/health").is_ok_and(|v| {
                v["ok"] == true && v["protocol"] == PROTOCOL && v["version"] == VERSION
            })
    }
    fn bootstrap(&self) -> Value {
        json!({"ok":true,"base_url":format!("http://127.0.0.1:{}",self.port),"token":self.token,"protocol":PROTOCOL})
    }
}

fn absolute_state(path: &str) -> Result<PathBuf, String> {
    let path = PathBuf::from(path);
    if !path.is_absolute()
        || path
            .components()
            .any(|c| c == std::path::Component::ParentDir)
    {
        return Err("State must be an absolute path without parent traversal".into());
    }
    private_dir(&path)?;
    fs::canonicalize(path).map_err(|e| e.to_string())
}

pub fn main_command(args: Vec<String>) -> Result<(), String> {
    unsafe {
        libc::umask(0o077);
    }
    if args.len() != 3 || args[1] != "--state" {
        return Err("Usage: purple-keyboard-bridge bootstrap|serve|stop|status --state ABS".into());
    }
    let state = absolute_state(&args[2])?;
    let value = match args[0].as_str() {
        "bootstrap" => bootstrap(&state)?,
        "serve" => return serve(&state),
        "status" => status(&state),
        "stop" => stop(&state)?,
        _ => return Err("Unknown bridge command".into()),
    };
    // Only bootstrap emits the capability, directly to its private app callback.
    let mut stdout = std::io::stdout().lock();
    serde_json::to_writer(&mut stdout, &value).map_err(|e| e.to_string())?;
    stdout.write_all(b"\n").map_err(|e| e.to_string())
}

fn bootstrap(state: &Path) -> Result<Value, String> {
    let _lock = Lock::wait(&state.join("bootstrap.lock"), Duration::from_secs(10))?;
    if state.join(RECORD).exists() {
        let record = Record::load(state)?;
        if record.healthy() {
            return Ok(record.bootstrap());
        }
        if record.identity.current() {
            if record.version != VERSION {
                return Err(
                    "Owned bridge revision differs; stop it before activating this update".into(),
                );
            }
            return Err(
                "Owned bridge is alive but not responding; no duplicate was started".into(),
            );
        }
        fs::remove_file(state.join(RECORD)).map_err(|e| e.to_string())?;
    }
    let mut command = Command::new(std::env::current_exe().map_err(|e| e.to_string())?);
    command
        .args(["serve", "--state"])
        .arg(state)
        .stdin(Stdio::null())
        .stdout(Stdio::null())
        .stderr(Stdio::null());
    unsafe {
        command.pre_exec(|| {
            if libc::setsid() < 0 {
                return Err(std::io::Error::last_os_error());
            }
            Ok(())
        });
    }
    let mut child = command.spawn().map_err(|e| e.to_string())?;
    let start = Instant::now();
    while start.elapsed() < Duration::from_secs(8) {
        if let Ok(record) = Record::load(state) {
            if record.identity.pid == child.id() && record.healthy() {
                return Ok(record.bootstrap());
            }
        }
        if child.try_wait().map_err(|e| e.to_string())?.is_some() {
            return Err("Bridge exited during startup; no diagnostic payload was persisted".into());
        }
        thread::sleep(Duration::from_millis(50));
    }
    // The Child handle still owns this PID; no foreign process can be killed.
    let _ = child.kill();
    let _ = child.wait();
    Err("Bridge did not pass authenticated startup health within 8 seconds".into())
}

fn status(state: &Path) -> Value {
    match Record::load(state) {
        Ok(record) => json!({"running":record.healthy(),"protocol":PROTOCOL,
            "owned_identity_alive":record.identity.current()}),
        Err(_) => json!({"running":false,"protocol":PROTOCOL,"owned_identity_alive":false}),
    }
}

fn stop(state: &Path) -> Result<Value, String> {
    let _lock = Lock::wait(&state.join("bootstrap.lock"), Duration::from_secs(10))?;
    let record = match Record::load(state) {
        Ok(record) => record,
        Err(_) if !state.join(RECORD).exists() => return Ok(json!({"stopped":true})),
        Err(error) => return Err(error),
    };
    if !record.identity.current() {
        fs::remove_file(state.join(RECORD)).map_err(|e| e.to_string())?;
        return Ok(json!({"stopped":true,"stale_record_removed":true}));
    }
    let response = http::local_call(record.port, &record.token, "POST", "/shutdown")?;
    if response["stopping"] != true {
        return Err("Owned bridge did not acknowledge scoped stop".into());
    }
    let start = Instant::now();
    while record.identity.current() && start.elapsed() < Duration::from_secs(8) {
        thread::sleep(Duration::from_millis(50));
    }
    if record.identity.current() {
        return Err("Owned bridge acknowledged stop but is still shutting down".into());
    }
    Ok(json!({"stopped":true}))
}

fn serve(state: &Path) -> Result<(), String> {
    let _lock = Lock::wait(&state.join("server.lock"), Duration::from_millis(1))?;
    let listener =
        TcpListener::bind(SocketAddrV4::new(Ipv4Addr::LOCALHOST, 0)).map_err(|e| e.to_string())?;
    listener.set_nonblocking(true).map_err(|e| e.to_string())?;
    let record = Record {
        protocol: PROTOCOL,
        version: VERSION.into(),
        port: listener.local_addr().map_err(|e| e.to_string())?.port(),
        token: private::random_token()?,
        identity: Identity::read(std::process::id())?,
    };
    vault::save(&state.join(RECORD), &serde_json::to_vec(&record).unwrap())?;
    let stop = Arc::new(AtomicBool::new(false));
    for signal in [libc::SIGTERM, libc::SIGINT, libc::SIGHUP] {
        signal_hook::flag::register(signal, Arc::clone(&stop)).map_err(|e| e.to_string())?;
    }
    let service = jobs::Service::new(state, Arc::clone(&stop))?;
    while !stop.load(Ordering::Relaxed) {
        match listener.accept() {
            Ok((mut stream, peer)) if peer.ip().is_loopback() => {
                if let Err(error) = http::handle(&mut stream, &record.token, &service, &stop) {
                    // Fixed diagnostics only; request bodies and capabilities are never logged.
                    eprintln!("HTTP request ended: {error}");
                }
            }
            Ok(_) => {}
            Err(error) if error.kind() == std::io::ErrorKind::WouldBlock => {
                thread::sleep(Duration::from_millis(20));
            }
            Err(error) => return Err(error.to_string()),
        }
    }
    service.shutdown();
    if Record::load(state).is_ok_and(|current| current.identity == record.identity) {
        let _ = fs::remove_file(state.join(RECORD));
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn identity_is_birth_bound() {
        let mut identity = Identity::read(std::process::id()).unwrap();
        assert!(identity.current());
        identity.birth_ticks += 1;
        assert!(!identity.current());
        identity.birth_ticks -= 1;
        identity.boot.push('x');
        assert!(!identity.current());
    }
    #[test]
    fn plaintext_predecessor_is_not_adopted() {
        let scratch=private::Scratch::new();
        private::write_atomic(&scratch.0.join(RECORD), b"{\"old\":true}").unwrap();
        assert!(Record::load(&scratch.0).is_err());
        assert!(scratch.0.join(RECORD).exists());
    }
}
