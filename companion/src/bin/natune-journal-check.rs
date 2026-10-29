//! Finite public-fixture acceptance for the existing encrypted record backend.
//! Not an app endpoint, native writer permit, media operation or model trial.
use purple_keyboard_bridge::natune_journal::{Claim, Document, EncryptedFiles, Event, Journal, RemoteReceipt, Stage};
use sha2::{Digest, Sha256};
use std::fs::{self, DirBuilder};
use std::os::unix::fs::{DirBuilderExt, MetadataExt};
use std::path::{Path, PathBuf};

fn private_directory(path: &Path, fresh: bool) -> Result<(), String> {
    let mut current=PathBuf::new();
    for part in path.components() {
        current.push(part);
        if current.is_symlink() { return Err("Check directory symlink refused".into()); }
    }
    let result=DirBuilder::new().mode(0o700).recursive(!fresh).create(path);
    if let Err(error)=result {
        if fresh || error.kind()!=std::io::ErrorKind::AlreadyExists {
            return Err("Check directory unavailable or case already exists; do not replay".into());
        }
    }
    let metadata=fs::symlink_metadata(path).map_err(|_| "Check directory metadata unavailable")?;
    if !metadata.is_dir() || metadata.uid()!=unsafe{libc::getuid()} || metadata.mode()&0o077!=0 {
        return Err("Check directory ownership/mode invalid".into());
    }
    Ok(())
}

fn check() -> Result<(), String> {
    let args:Vec<String>=std::env::args().skip(1).collect();
    if args.len()!=2 || args[0]!="--case-id" || args[1].len()!=32 ||
        !args[1].bytes().all(|b| b.is_ascii_digit() || (b'a'..=b'f').contains(&b)) {
        return Err("Usage: natune-journal-check --case-id ORIGINAL-32-LOWERCASE-HEX-ID".into());
    }
    if !cfg!(target_os="android") || unsafe{libc::getuid()}<10000 ||
        std::env::var_os("HOME").as_deref()!=Some(std::ffi::OsStr::new("/data/data/com.termux/files/home")) {
        return Err("Existing Termux owner required".into());
    }
    let base=Path::new("/data/data/com.termux/files/home/.local/state/purple-natune-journal-check");
    private_directory(base,false)?;
    let case=base.join(&args[1]);
    private_directory(&case,true)?; // Existing/UNKNOWN case NEVER starts again.
    let claim=Claim {
        operation_id:"c".repeat(64),
        song_id:"PUBLIC-FIXTURE-NO-MEDIA-OPERATION".into(),
        rendition_key:"PUBLIC-FIXTURE-NOT-AN-APP-CACHE-KEY".into(),
        source_fingerprint:format!("{:x}",Sha256::digest(b"PUBLIC-FIXTURE-NOT-A-CACHE-SNAPSHOT")),
        destination_tree_uri:"content://fixture.invalid/tree/PUBLIC-FIXTURE".into(),
        requested_name:"PUBLIC-FIXTURE-NOT-CREATED.flac".into(),
        source_bytes:9,
        peak_bytes:1_048_576,
        media_store_audio:None,
    };
    let document=Document {
        uri:"content://fixture.invalid/document/PUBLIC-FIXTURE-NOT-CREATED".into(),
        bytes:9,
        sha256:format!("{:x}",Sha256::digest(b"123456789")),
        actual_display_name:None,
        flac_integrity:None,
    };
    let receipt=RemoteReceipt {
        receiver_id:"PUBLIC-FIXTURE-NOT-A-RECEIVER".into(),
        transfer_id:"PUBLIC-FIXTURE-NOT-A-TRANSFER".into(),
        bytes:document.bytes,
        sha256:document.sha256.clone(),
    };
    let open=|| Journal::open(EncryptedFiles::open(&case)?,2_097_152);
    let mut journal=open()?;
    journal.record(&claim.operation_id,None,Event::Begin{claim:claim.clone()})?;
    drop(journal);
    let mut journal=open()?;
    if journal.reserved_bytes()?!=claim.peak_bytes || journal.document(&claim.song_id,&claim.rendition_key)?.is_some() {
        return Err("Reserved-state recovery differs".into());
    }
    journal.record(&claim.operation_id,Some(0),Event::Destination{uri:document.uri.clone()})?;
    journal.record(&claim.operation_id,Some(1),Event::Verified{document:document.clone()})?;
    drop(journal);
    let mut journal=open()?;
    if journal.reserved_bytes()?!=claim.peak_bytes || journal.document(&claim.song_id,&claim.rendition_key)?.is_some() {
        return Err("Verified-state recovery differs".into());
    }
    journal.record(&claim.operation_id,Some(2),Event::RemoteVerified{receipt:receipt.clone()})?;
    if journal.record(&claim.operation_id,Some(3),Event::RemovalPending).is_ok() {
        return Err("Unbound original-removal gate unexpectedly opened".into());
    }
    drop(journal);
    let mut journal=open()?;
    if journal.reserved_bytes()?!=claim.peak_bytes || journal.document(&claim.song_id,&claim.rendition_key)?.is_some() ||
        journal.remote_receipt(&claim.song_id,&claim.rendition_key)?!=Some(&receipt) ||
        journal.snapshot(&claim.operation_id)?.map(|s|s.stage)!=Some(Stage::RemoteVerified) {
        return Err("Remote-observation held-state recovery differs".into());
    }
    // Synthetic observation only; no actual staging document or media exists.
    journal.record(&claim.operation_id,Some(3),Event::StageCleaned)?;
    drop(journal);
    let mut journal=open()?;
    if journal.reserved_bytes()?!=0 || journal.document(&claim.song_id,&claim.rendition_key)?.is_some() ||
        journal.remote_receipt(&claim.song_id,&claim.rendition_key)?!=Some(&receipt) ||
        journal.snapshot(&claim.operation_id)?.map(|s|s.stage)!=Some(Stage::StagedRemotely) {
        return Err("Remote-staging recovery differs".into());
    }
    if journal.record(&claim.operation_id,Some(4),Event::RemovalPending).is_ok() {
        return Err("Unbound original-removal gate unexpectedly opened after staging".into());
    }
    if journal.record(&claim.operation_id,None,Event::Begin{claim:claim.clone()}).is_ok() {
        return Err("Consumed operation unexpectedly readmitted".into());
    }
    drop(journal);
    let mut encrypted=0;
    for item in fs::read_dir(case.join("natune-move-index-v1")).map_err(|_| "Check records unavailable")? {
        let item=item.map_err(|_| "Check record unavailable")?;
        if item.file_name()==".lock" { continue; }
        let metadata=fs::symlink_metadata(item.path()).map_err(|_| "Check record metadata unavailable")?;
        if !metadata.is_file() || metadata.uid()!=unsafe{libc::getuid()} || metadata.mode()&0o077!=0 || metadata.len()>65536 {
            return Err("Check record metadata invalid".into());
        }
        let bytes=fs::read(item.path()).map_err(|_| "Check ciphertext unavailable")?;
        if !bytes.starts_with(b"age-encryption.org/") || bytes.windows(claim.song_id.len()).any(|w|w==claim.song_id.as_bytes()) {
            return Err("Check ciphertext format invalid".into());
        }
        encrypted+=1;
    }
    if encrypted!=5 { return Err("Check event count differs".into()); }
    println!("{}",serde_json::json!({
        "schema":"purple.natune.encrypted-journal-public-check.v3",
        "status":"PASS", "public_fixture_only":true, "encrypted_events":encrypted,
        "actual_vault_backend":true, "reopened_stages":4, "unbound_original_removal_refused":true,
        "remote_and_cleanup_observations":"synthetic_only",
        "original_app_data_access":false, "source_cache_removed":false,
        "actual_android_move":false, "model_invocation":false, "listener_started":false
    }));
    Ok(())
}

fn main() {
    if check().is_err() {
        // No arbitrary error/path/record payload dump. Existing case and global
        // encrypted records stay available for ORIGINAL-ID reconciliation.
        eprintln!("NATUNE_JOURNAL_CHECK_UNKNOWN_OR_REFUSED; preserve original case; no automatic replay");
        std::process::exit(1);
    }
}
