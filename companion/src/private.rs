use std::fs::{self, File, OpenOptions};
use std::io::{Read, Write};
use std::os::fd::AsRawFd;
use std::os::unix::fs::{MetadataExt, OpenOptionsExt, PermissionsExt};
use std::path::Path;
use std::thread;
use std::time::{Duration, Instant};

pub fn private_dir(path: &Path) -> Result<(), String> {
    let mut current = std::path::PathBuf::new();
    for component in path.components() {
        current.push(component);
        if current.is_symlink() {
            return Err("Private directory symlink refused".into());
        }
    }
    fs::create_dir_all(path).map_err(|e| e.to_string())?;
    let metadata = fs::symlink_metadata(path).map_err(|e| e.to_string())?;
    if !metadata.is_dir() || metadata.uid() != unsafe { libc::getuid() } {
        return Err("Private directory ownership mismatch".into());
    }
    fs::set_permissions(path, fs::Permissions::from_mode(0o700)).map_err(|e| e.to_string())
}

pub fn read(path: &Path, cap: usize) -> Result<Vec<u8>, String> {
    let file = OpenOptions::new()
        .read(true)
        .custom_flags(libc::O_NOFOLLOW)
        .open(path)
        .map_err(|_| "Private file unavailable")?;
    let metadata = file
        .metadata()
        .map_err(|_| "Private file metadata unavailable")?;
    if !metadata.is_file()
        || metadata.uid() != unsafe { libc::getuid() }
        || metadata.mode() & 0o077 != 0
        || metadata.len() > cap as u64
    {
        return Err("Private file ownership, permissions or size invalid".into());
    }
    let mut bytes = Vec::new();
    file.take((cap + 1) as u64)
        .read_to_end(&mut bytes)
        .map_err(|_| "Private file read failed")?;
    if bytes.len() > cap {
        return Err("Private file exceeded its accepted size".into());
    }
    Ok(bytes)
}

pub fn create(path: &Path, bytes: &[u8]) -> Result<(), String> {
    let mut file = OpenOptions::new()
        .create_new(true)
        .write(true)
        .mode(0o600)
        .custom_flags(libc::O_NOFOLLOW)
        .open(path)
        .map_err(|e| e.to_string())?;
    file.write_all(bytes)
        .and_then(|_| file.sync_all())
        .map_err(|e| e.to_string())
}

pub fn write_atomic(path: &Path, bytes: &[u8]) -> Result<(), String> {
    if path.is_symlink() {
        return Err("Private record symlink refused".into());
    }
    let parent = path.parent().ok_or("Private record requires a parent")?;
    let temporary = parent.join(format!(".record-{}", random_token()?));
    create(&temporary, bytes)?;
    let result = fs::rename(&temporary, path)
        .and_then(|_| File::open(parent)?.sync_all())
        .map_err(|e| e.to_string());
    if result.is_err() {
        let _ = fs::remove_file(temporary);
    }
    result
}

pub fn random_token() -> Result<String, String> {
    let mut bytes = [0u8; 32];
    File::open("/dev/urandom")
        .and_then(|mut file| file.read_exact(&mut bytes))
        .map_err(|e| e.to_string())?;
    Ok(bytes.iter().map(|b| format!("{b:02x}")).collect())
}

pub fn token_ok(token: &str) -> bool {
    token.len() == 64 && token.bytes().all(|b| b.is_ascii_hexdigit())
}

pub fn equal_secret(left: &[u8], right: &[u8]) -> bool {
    if left.len() != right.len() {
        return false;
    }
    left.iter()
        .zip(right)
        .fold(0u8, |diff, (a, b)| diff | (a ^ b))
        == 0
}

pub struct Lock(File);
impl Lock {
    pub fn wait(path: &Path, timeout: Duration) -> Result<Self, String> {
        let file = OpenOptions::new()
            .create(true)
            .truncate(false)
            .read(true)
            .write(true)
            .mode(0o600)
            .custom_flags(libc::O_NOFOLLOW)
            .open(path)
            .map_err(|e| e.to_string())?;
        let start = Instant::now();
        loop {
            if unsafe { libc::flock(file.as_raw_fd(), libc::LOCK_EX | libc::LOCK_NB) } == 0 {
                return Ok(Self(file));
            }
            if start.elapsed() >= timeout {
                return Err("Another owned operation holds the bridge lock".into());
            }
            thread::sleep(Duration::from_millis(20));
        }
    }
}
impl Drop for Lock {
    fn drop(&mut self) {
        unsafe { libc::flock(self.0.as_raw_fd(), libc::LOCK_UN) };
    }
}

#[cfg(test)]
pub struct Scratch(pub std::path::PathBuf);
#[cfg(test)]
impl Scratch {
    pub fn new() -> Self {
        let path =
            std::env::temp_dir().join(format!("purple-keyboard-test-{}", random_token().unwrap()));
        private_dir(&path).unwrap();
        Self(path)
    }
}
#[cfg(test)]
impl Drop for Scratch {
    fn drop(&mut self) {
        let _ = fs::remove_dir_all(&self.0);
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::os::unix::fs::symlink;
    #[test]
    fn private_records_refuse_symlinks_and_broad_permissions() {
        let scratch = Scratch::new();
        create(&scratch.0.join("record"), b"owned").unwrap();
        symlink(scratch.0.join("record"), scratch.0.join("link")).unwrap();
        assert!(read(&scratch.0.join("link"), 20).is_err());
        fs::set_permissions(scratch.0.join("record"), fs::Permissions::from_mode(0o644)).unwrap();
        assert!(read(&scratch.0.join("record"), 20).is_err());
        assert!(private_dir(&scratch.0.join("link")).is_err());
    }
    #[test]
    fn lock_excludes_other_holders() {
        let scratch = Scratch::new();
        let first = Lock::wait(&scratch.0.join("lock"), Duration::ZERO).unwrap();
        assert!(Lock::wait(&scratch.0.join("lock"), Duration::ZERO).is_err());
        drop(first);
        assert!(Lock::wait(&scratch.0.join("lock"), Duration::ZERO).is_ok());
    }
}
