use crate::{cli, private};
use serde::Deserialize;
use std::fs;
use std::path::{Path, PathBuf};
use std::process::{Command, Stdio};
use std::os::unix::fs::OpenOptionsExt;
use std::sync::atomic::AtomicBool;

pub const AUDIO_CAP: usize = 10 * 1024 * 1024;
#[derive(Deserialize)]
#[serde(deny_unknown_fields)]
struct Config {
    schema: String,
    binary: PathBuf,
    model: PathBuf,
    binary_sha256: String,
    model_sha256: String,
    engine_commit: String,
    max_audio_seconds: u32,
    language: String,
    #[serde(default)]
    model_family: String,
}

pub fn validate_wav(bytes: &[u8]) -> Result<(), String> {
    fn u16_at(bytes: &[u8], offset: usize) -> u16 {
        u16::from_le_bytes(bytes[offset..offset + 2].try_into().unwrap())
    }
    fn u32_at(bytes: &[u8], offset: usize) -> u32 {
        u32::from_le_bytes(bytes[offset..offset + 4].try_into().unwrap())
    }
    if bytes.len() < 44
        || bytes.len() > AUDIO_CAP
        || &bytes[..4] != b"RIFF"
        || &bytes[8..12] != b"WAVE"
        || u32_at(bytes, 4) as usize != bytes.len() - 8
    {
        return Err("Audio must be a bounded complete PCM WAV recording".into());
    }
    let mut offset = 12usize;
    let mut format = false;
    let mut data = None;
    while offset < bytes.len() {
        if bytes.len() - offset < 8 {
            return Err("Truncated WAV chunk".into());
        }
        let tag = &bytes[offset..offset + 4];
        let length = u32_at(bytes, offset + 4) as usize;
        let begin = offset + 8;
        let end = begin.checked_add(length).ok_or("WAV chunk overflow")?;
        if end > bytes.len() {
            return Err("Truncated WAV chunk body".into());
        }
        if tag == b"fmt " {
            if format
                || length < 16
                || u16_at(bytes, begin) != 1
                || u16_at(bytes, begin + 2) != 1
                || u32_at(bytes, begin + 4) != 16000
                || u32_at(bytes, begin + 8) != 32000
                || u16_at(bytes, begin + 12) != 2
                || u16_at(bytes, begin + 14) != 16
            {
                return Err("Recording must be PCM16 mono at 16000 Hz".into());
            }
            format = true;
        } else if tag == b"data" {
            if data.is_some() || length == 0 || !length.is_multiple_of(2) || length > 90 * 32000 {
                return Err("Recording is empty, invalid, or exceeds 90 seconds".into());
            }
            data = Some(length);
        }
        offset = end.checked_add(length % 2).ok_or("WAV padding overflow")?;
        if offset > bytes.len() {
            return Err("Truncated WAV padding".into());
        }
    }
    if !format || data.is_none() {
        return Err("WAV requires a valid format and audio data".into());
    }
    Ok(())
}

fn config(state: &Path) -> Result<Config, String> {
    let root = fs::canonicalize(state.join("whisper"))
        .map_err(|_| "Whisper unavailable: local engine/model is not installed")?;
    let bytes = private::read(&root.join("config.json"), 16384)
        .map_err(|_| "Whisper unavailable: local engine configuration is absent or not private")?;
    let config: Config = serde_json::from_slice(&bytes)
        .map_err(|_| "Whisper unavailable: invalid local engine configuration")?;
    let hash_ok = |hash: &str| hash.len() == 64 && hash.bytes().all(|b| b.is_ascii_hexdigit());
    if config.schema != "purple_keyboard_whisper_v1"
        || config.model_family != "whisper-large-v3"
        || config.language != "auto"
        || config.max_audio_seconds != 90
        || config.engine_commit.len() != 40
        || !config.engine_commit.bytes().all(|b| b.is_ascii_hexdigit())
        || !hash_ok(&config.binary_sha256)
        || !hash_ok(&config.model_sha256)
    {
        return Err("Whisper unavailable: configuration contract mismatch".into());
    }
    for path in [&config.binary, &config.model] {
        if !path.is_absolute()
            || path.is_symlink()
            || !path.is_file()
            || !fs::canonicalize(path).is_ok_and(|p| p.starts_with(&root))
        {
            return Err(
                "Whisper unavailable: engine/model must belong to the private Whisper directory"
                    .into(),
            );
        }
    }
    if model_family(&config.model)? != "whisper-large-v3" { return Err("Whisper 3 model architecture required; no fallback".into()); }
    // Streaming hashes use bounded memory; no model/audio leaves this device.
    if cli::sha256_file(&config.binary)
        .map_err(|_| "Whisper unavailable: pinned binary cannot be read")?
        != config.binary_sha256
        || cli::sha256_file(&config.model)
            .map_err(|_| "Whisper unavailable: pinned model cannot be read")?
            != config.model_sha256
    {
        return Err("Whisper unavailable: installed engine/model bytes changed".into());
    }
    Ok(config)
}

pub fn available(state: &Path) -> Result<(), String> {
    config(state).map(|_| ())
}

pub fn family(state:&Path)->Option<&'static str> {
    let root=fs::canonicalize(state.join("whisper")).ok()?;
    let value:Config=serde_json::from_slice(&private::read(&root.join("config.json"),16384).ok()?).ok()?;
    let model=fs::canonicalize(&value.model).ok()?;
    if !model.starts_with(root) || value.model_family!="whisper-large-v3" {return None}
    model_family(&model).ok()
}
fn model_family(path:&Path)->Result<&'static str,String>{
    use std::io::Read;
    let mut file=std::fs::OpenOptions::new().read(true).custom_flags(libc::O_NOFOLLOW).open(path).map_err(|_|"Model unavailable")?;
    let mut header=[0u8;48];file.read_exact(&mut header).map_err(|_|"Invalid Whisper model header")?;
    classify_header(&header)
}
fn classify_header(header:&[u8])->Result<&'static str,String>{
    let number=|at:usize|i32::from_le_bytes(header[at..at+4].try_into().unwrap());
    if header.len()<48 || header[..4]!=0x67676d6cu32.to_le_bytes() {return Err("Unsupported Whisper model container".into())}
    if number(12)==1280 && number(20)==32 && matches!(number(36),32|4) && number(40)==128 {Ok("whisper-large-v3")}
    else {Err("Whisper 3 full or turbo required; another model was not substituted".into())}
}
pub fn transcribe(state:&Path,_directory:&Path,cancelled:&AtomicBool,bytes:&[u8])->Result<String,String>{
    validate_wav(bytes)?;
    let config=config(state)?;
    let input=crate::memory::MemoryFile::new(bytes)?;
    let mut command=Command::new(config.binary);
    command.arg("-m").arg(config.model).arg("-f").arg(input.path())
        .args(["--language","auto","--no-timestamps","--threads","4","--no-prints"]);
    let output=crate::memory::run(&mut command,b"",cancelled,cli::RESULT_CAP)?;
    let text=String::from_utf8(output).map_err(|_|"Invalid transcript UTF8")?;
    let text=text.trim();
    if text.is_empty(){return Err("Whisper did not recognize speech".into())}
    Ok(text.into())
}

#[cfg(test)]
mod tests {
    use super::*;
    pub fn wav(samples: usize) -> Vec<u8> {
        let n = samples * 2;
        let mut bytes = Vec::new();
        bytes.extend(b"RIFF");
        bytes.extend(((36 + n) as u32).to_le_bytes());
        bytes.extend(b"WAVEfmt ");
        bytes.extend(16u32.to_le_bytes());
        bytes.extend(1u16.to_le_bytes());
        bytes.extend(1u16.to_le_bytes());
        bytes.extend(16000u32.to_le_bytes());
        bytes.extend(32000u32.to_le_bytes());
        bytes.extend(2u16.to_le_bytes());
        bytes.extend(16u16.to_le_bytes());
        bytes.extend(b"data");
        bytes.extend((n as u32).to_le_bytes());
        bytes.resize(44 + n, 0);
        bytes
    }
    #[test]
    fn v3_header_admission_refuses_base_and_other_generations() {
        let mut header=[0u8;48];header[..4].copy_from_slice(&0x67676d6cu32.to_le_bytes());
        for (offset,value) in [(12,1280i32),(20,32),(36,32),(40,128)] {header[offset..offset+4].copy_from_slice(&value.to_le_bytes());}
        assert_eq!(classify_header(&header).unwrap(),"whisper-large-v3");
        header[36..40].copy_from_slice(&4i32.to_le_bytes());assert!(classify_header(&header).is_ok());
        header[40..44].copy_from_slice(&80i32.to_le_bytes());assert!(classify_header(&header).is_err());
        assert!(classify_header(&[]).is_err());
    }
    #[test]
    fn wav_validates_duration_rate_channels_format_and_chunk_bounds() {
        let bytes = wav(16000);
        assert!(validate_wav(&bytes).is_ok());
        let mut bad = bytes.clone();
        bad[22] = 2;
        assert!(validate_wav(&bad).is_err());
        let mut bad = bytes.clone();
        bad[24] = 0;
        assert!(validate_wav(&bad).is_err());
        let mut bad = bytes.clone();
        bad[20] = 3;
        assert!(validate_wav(&bad).is_err());
        let mut bad = bytes.clone();
        bad.pop();
        assert!(validate_wav(&bad).is_err());
        assert!(validate_wav(&wav(90 * 16000 + 1)).is_err());
        assert!(validate_wav(&wav(0)).is_err());
    }
}
