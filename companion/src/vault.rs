//! Reuse the installed single-identity record CLI. No key creation or plaintext files.
use std::path::{Path,PathBuf};
use std::process::Command;
use std::sync::atomic::AtomicBool;
const FEATURECTL:&str="/data/data/com.termux/files/home/.local/bin/featurectl";
pub fn retain(bytes:&[u8])->Result<PathBuf,String>{
 let mut command=Command::new(FEATURECTL);command.arg("seal-stdin");
 let output=crate::memory::run(&mut command,bytes,&AtomicBool::new(false),65536)?;
 let receipt:serde_json::Value=serde_json::from_slice(&output).map_err(|_|"Invalid encrypted receipt")?;
 if receipt["state"]!="ENCRYPTED_AND_READ_BACK" {return Err("Encrypted receipt readback failed".into())}
 let path=PathBuf::from(receipt["record"].as_str().ok_or("Missing encrypted record")?);
 let home=std::env::var_os("HOME").ok_or("HOME unavailable")?;
 let records=PathBuf::from(home).join(".local/state/featurectl/records");
 if !path.is_absolute() || path.parent()!=Some(records.as_path()) || path.extension().is_none_or(|v|v!="age") {return Err("Unexpected encrypted record path".into())}
 Ok(path)
}
pub fn save(path:&Path,bytes:&[u8])->Result<(),String>{
 let record=retain(bytes)?;
 let ciphertext=crate::private::read(&record,65536)?;
 crate::private::write_atomic(path,&ciphertext)
}
pub fn open(path:&Path)->Result<Vec<u8>,String>{
 let bytes=crate::private::read(path,65536)?;
 if !bytes.starts_with(b"age-encryption.org/") {return Err("Encrypted bridge record required".into())}
 let mut command=Command::new(FEATURECTL);command.arg("unseal").arg(path).arg("--stdout");
 crate::memory::run(&mut command,b"",&AtomicBool::new(false),16384)
}
