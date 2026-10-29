//! Encrypted durable Move state, not an Android writer/deletion capability.
//!
//! A future authenticated Natune owner supplies actual native observations.
//! This module starts no service, runs no model and exposes no keyboard route.
//! Journal events describe outcomes; replay NEVER performs copy/removal work.
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use std::collections::{BTreeMap, BTreeSet};
use std::fs::{self, File};
use std::path::{Path, PathBuf};
use std::time::Duration;

// Existing vault::open plaintext ceiling. This bounds one event, not music size.
const EVENT_CAP: usize = 16384;

#[derive(Clone, Debug, Serialize, Deserialize, PartialEq, Eq)]
#[serde(deny_unknown_fields)]
pub struct Claim {
    pub operation_id: String,
    pub song_id: String,
    pub rendition_key: String,
    pub source_fingerprint: String,
    pub destination_tree_uri: String,
    pub requested_name: String,
    pub source_bytes: u64,
    pub peak_bytes: u64,
    /// Schema 3 only. The legacy URI field holds the actual MediaStore audio
    /// collection URI for this volume, not a claimed SAF tree grant.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub media_store_audio: Option<MediaStoreAudioSelection>,
}

/// Frozen selected destination context, not a provider grant or evidence that
/// the returned row belongs to this operation. The Android owner must check
/// the actual volume, row, owner, folder, MIME, source and current grant.
#[derive(Clone, Debug, Serialize, Deserialize, PartialEq, Eq)]
#[serde(deny_unknown_fields)]
pub struct MediaStoreAudioSelection {
    pub volume: String,
    pub relative_folder: String,
    pub mime_type: String,
    #[serde(default, skip_serializing_if = "is_false")]
    pub require_full_flac: bool,
}

fn is_false(value: &bool) -> bool { !*value }

/// Supplied by the actual selected decoder owner after complete verification.
/// A serialized observation is not proof of a real decoder or recording match.
#[derive(Clone, Debug, Serialize, Deserialize, PartialEq, Eq)]
#[serde(deny_unknown_fields)]
pub struct FlacIntegrityEvidence {
    pub samples: u64,
    pub sample_rate: u32,
    pub channels: u8,
    pub bits_per_sample: u8,
}

#[derive(Clone, Debug, Serialize, Deserialize, PartialEq, Eq)]
#[serde(deny_unknown_fields)]
pub struct Document {
    pub uri: String,
    pub bytes: u64,
    pub sha256: String,
    /// Schema 3 records the actual provider-selected name after readback.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub actual_display_name: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub flac_integrity: Option<FlacIntegrityEvidence>,
}

/// An observation supplied by the selected receiver's real owner, never a
/// credential, automatic removal permit, or proof by itself. Its byte extent
/// and digest must match this operation's independently verified local copy.
#[derive(Clone, Debug, Serialize, Deserialize, PartialEq, Eq)]
#[serde(deny_unknown_fields)]
pub struct RemoteReceipt {
    pub receiver_id: String,
    pub transfer_id: String,
    pub bytes: u64,
    pub sha256: String,
}

#[derive(Clone, Debug, Serialize, Deserialize, PartialEq, Eq)]
#[serde(tag = "kind", deny_unknown_fields)]
pub enum Event {
    Begin { claim: Claim },
    Destination { uri: String },
    Verified { document: Document },
    // Version 2 observations for a verified computer handoff. The real owner
    // must bind these observations to the actual receiver and cleanup result.
    RemoteVerified { receipt: RemoteReceipt },
    StageCleaned,
    Indexed,
    RemovalPending,
    Moved,
    // Only before indexing; owner must verify partial/duplicate output cleanup.
    // A timeout or cancellation notification is NOT this observation.
    Abandoned,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Stage { Reserved, Destination, Verified, RemoteVerified, StagedRemotely, Indexed, RemovalPending, Moved, Abandoned }

#[derive(Clone, Debug)]
pub struct Snapshot {
    pub claim: Claim,
    pub generation: u64,
    pub stage: Stage,
    pub document: Option<Document>,
    pub remote: Option<RemoteReceipt>,
    schema: u32,
    local_cleaned: bool,
    destination: Option<String>,
    last_hash: String,
}

/// Minimal in-memory projection for restoring the app-side budget mirror.
/// This does not authenticate a host, grant removal, or serialize itself.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct RecoveryAttempt {
    pub operation_id: String,
    pub source_bytes: u64,
    pub peak_bytes: u64,
    pub unsettled: bool,
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct AdmissionRecovery {
    pub attempts: Vec<RecoveryAttempt>,
    pub reserved_bytes: u64,
}

#[derive(Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
struct Record {
    schema: u32,
    operation_id: String,
    generation: u64,
    previous_sha256: Option<String>,
    event: Event,
}

/// The production store seals BEFORE exclusive file creation and verifies readback.
/// A substitute Store in controls does not prove actual encryption/host admission.
pub trait Store {
    fn load(&mut self) -> Result<Vec<(String, Vec<u8>)>, String>;
    fn append(&mut self, name: &str, plaintext: &[u8]) -> Result<(), String>;
}

pub struct EncryptedFiles {
    root: PathBuf,
    _lock: crate::private::Lock,
}
impl EncryptedFiles {
    /// Only the real Termux owner may supply its dedicated private state parent.
    /// This does not create an Android delegation, permission or new identity.
    pub fn open(owner_state: &Path) -> Result<Self, String> {
        let root = owner_state.join("natune-move-index-v1");
        crate::private::private_dir(&root)?;
        let lock = crate::private::Lock::wait(&root.join(".lock"), Duration::from_secs(2))?;
        Ok(Self { root, _lock: lock })
    }
}
impl Store for EncryptedFiles {
    fn load(&mut self) -> Result<Vec<(String, Vec<u8>)>, String> {
        let mut entries = Vec::new();
        for entry in fs::read_dir(&self.root).map_err(|_| "Move journal unavailable")? {
            let entry = entry.map_err(|_| "Move journal entry unavailable")?;
            let name = entry.file_name().into_string().map_err(|_| "Move journal name invalid")?;
            if name == ".lock" { continue; }
            if !valid_name(&name) { return Err("Unexpected Move journal entry".into()); }
            // vault::open checks private file metadata, cap, age format and actual decrypt.
            entries.push((name, crate::vault::open(&entry.path())?));
        }
        Ok(entries)
    }
    fn append(&mut self, name: &str, plaintext: &[u8]) -> Result<(), String> {
        if !valid_name(name) || plaintext.len() > EVENT_CAP { return Err("Move event bounds invalid".into()); }
        let encrypted = crate::vault::retain(plaintext)?;
        let ciphertext = crate::private::read(&encrypted, 65536)?;
        let path = self.root.join(name);
        // Never overwrite history, including uncertain/partially created files.
        crate::private::create(&path, &ciphertext)?;
        File::open(&self.root).and_then(|f| f.sync_all()).map_err(|_| "Move event durability unknown")?;
        if crate::vault::open(&path)? != plaintext { return Err("Move event readback differs".into()); }
        Ok(())
    }
}

pub struct Journal<S: Store> {
    store: S,
    rows: BTreeMap<String, Snapshot>,
    pool_bytes: u64,
    uncertain: bool,
}
impl<S: Store> Journal<S> {
    pub fn open(mut store: S, pool_bytes: u64) -> Result<Self, String> {
        if pool_bytes == 0 { return Err("Positive temporary pool required".into()); }
        let mut entries = store.load()?;
        entries.sort_by(|a,b| a.0.cmp(&b.0));
        let mut journal = Self { store, rows: BTreeMap::new(), pool_bytes, uncertain: false };
        for (name, bytes) in entries {
            let record = decode(&name, &bytes)?;
            let snapshot = journal.project(&record, &bytes)?;
            journal.rows.insert(record.operation_id, snapshot);
        }
        let mut active_keys = BTreeSet::new();
        for row in journal.rows.values().filter(|r| r.stage != Stage::Abandoned) {
            if !active_keys.insert((&row.claim.song_id, &row.claim.rendition_key)) {
                return Err("Competing Move claims require reconciliation".into());
            }
        }
        let mut owned_documents = BTreeSet::new();
        for row in journal.rows.values().filter(|r| !r.local_cleaned && r.stage != Stage::Abandoned) {
            if let Some(uri) = &row.destination {
                if !owned_documents.insert(uri) {
                    return Err("One local document is claimed by multiple Move operations".into());
                }
            }
        }
        let mut transfers = BTreeSet::new();
        for row in journal.rows.values() {
            if let Some(remote) = &row.remote {
                if !transfers.insert((&remote.receiver_id, &remote.transfer_id)) {
                    return Err("Remote receipt identity reused across Move claims".into());
                }
            }
        }
        // Recovery preserves oversized historical holds; a lower pool must not
        // prevent reconciliation. New admissions still refuse overcommit.
        journal.reserved_bytes()?;
        Ok(journal)
    }
    pub fn snapshot(&self, id: &str) -> Result<Option<&Snapshot>, String> {
        self.certain()?;
        Ok(self.rows.get(id))
    }
    pub fn reserved_bytes(&self) -> Result<u64, String> {
        self.certain()?;
        self.rows.values().filter(|r| !r.local_cleaned && !matches!(r.stage, Stage::Moved | Stage::Abandoned))
            .try_fold(0u64, |total,r| total.checked_add(r.claim.peak_bytes).ok_or_else(|| "Move holds overflow".into()))
    }
    /// Includes settled IDs so a restarted mirror cannot readmit them. The
    /// actual owner must freeze admissions while transferring this complete
    /// snapshot into its paired Java counter. A snapshot is not a permit, an
    /// Android binding, or evidence of current disk headroom.
    pub fn admission_recovery(&self) -> Result<AdmissionRecovery, String> {
        self.certain()?;
        let reserved_bytes = self.reserved_bytes()?;
        let attempts = self.rows.values().map(|row| RecoveryAttempt {
            operation_id: row.claim.operation_id.clone(),
            source_bytes: row.claim.source_bytes,
            peak_bytes: row.claim.peak_bytes,
            unsettled: !row.local_cleaned && !matches!(row.stage, Stage::Moved | Stage::Abandoned),
        }).collect();
        Ok(AdmissionRecovery { attempts, reserved_bytes })
    }
    /// Reference only: caller must check current grant/document/hash/owner state.
    /// No URI/length descriptor is promoted into a native write or removal permit.
    pub fn document(&self, song: &str, rendition: &str) -> Result<Option<&Document>, String> {
        self.certain()?;
        Ok(self.rows.values().find(|r| !r.local_cleaned && r.claim.song_id == song && r.claim.rendition_key == rendition &&
            matches!(r.stage, Stage::Indexed | Stage::RemovalPending | Stage::Moved))
            .and_then(|r| r.document.as_ref()))
    }
    /// Recovery reference only; the real owner must revalidate the selected
    /// receiver, saved bytes and current original before any source removal.
    pub fn remote_receipt(&self, song: &str, rendition: &str) -> Result<Option<&RemoteReceipt>, String> {
        self.certain()?;
        Ok(self.rows.values().find(|r| r.claim.song_id == song && r.claim.rendition_key == rendition && r.remote.is_some())
            .and_then(|r| r.remote.as_ref()))
    }
    /// expected_generation=None only for an ORIGINAL Begin. After an uncertain
    /// append this object refuses all access; reopen/reconcile, never auto-retry.
    pub fn record(&mut self, operation_id: &str, expected_generation: Option<u64>, event: Event)
            -> Result<(), String> {
        self.certain()?;
        // A v1 journal has no bound computer-copy receipt or reconciled
        // selection/KEEP decision. A caller-supplied event is
        // not that proof. Keep historical pending records readable so their
        // original owners can reconcile them, but never start a NEW removal
        // until a versioned real-owner admission path has been integrated.
        if matches!(&event, Event::RemovalPending) {
            return Err("New original removal requires bound remote-copy and selection checks".into());
        }
        if !hex64(operation_id) { return Err("Move operation identity invalid".into()); }
        let prior = self.rows.get(operation_id);
        if prior.map(|r| r.generation) != expected_generation { return Err("Move generation conflict".into()); }
        if let Event::Destination { uri } = &event {
            if self.rows.values().any(|row| row.claim.operation_id != operation_id && !row.local_cleaned &&
                row.stage != Stage::Abandoned && row.destination.as_ref() == Some(uri)) {
                return Err("Local destination already owned by another Move operation".into());
            }
        }
        let generation = match expected_generation {
            None => 0,
            Some(g) => g.checked_add(1).ok_or("Move generation overflow")?,
        };
        if let Event::Begin { claim } = &event {
            if self.rows.values().any(|r| r.stage != Stage::Abandoned &&
                    r.claim.song_id == claim.song_id && r.claim.rendition_key == claim.rendition_key) {
                return Err("Existing Move mapping/claim requires reconciliation or replacement custody".into());
            }
            let reserved = self.reserved_bytes()?;
            if reserved > self.pool_bytes || claim.peak_bytes > self.pool_bytes - reserved {
                return Err("Temporary pool busy or track oversized".into());
            }
        }
        let schema = if prior.is_some_and(|p| p.schema == 3) ||
            matches!(&event, Event::Begin { claim } if claim.media_store_audio.is_some()) {
            3
        } else if prior.is_some_and(|p| p.schema == 2) || matches!(&event, Event::RemoteVerified { .. }) {
            2
        } else { 1 };
        let record = Record { schema, operation_id: operation_id.to_owned(), generation,
            previous_sha256: prior.map(|r| r.last_hash.clone()), event };
        let bytes = serde_json::to_vec(&record).map_err(|_| "Move event encoding failed")?;
        if bytes.len() > EVENT_CAP { return Err("Move event too large".into()); }
        let projected = self.project(&record, &bytes)?;
        let name = format!("{}-{:016x}.age", operation_id, generation);
        self.uncertain = true;
        self.store.append(&name, &bytes)?;
        self.rows.insert(operation_id.to_owned(), projected);
        self.uncertain = false;
        Ok(())
    }
    fn certain(&self) -> Result<(), String> {
        if self.uncertain { Err("Move persistence UNKNOWN; reopen and reconcile, no replay".into()) } else { Ok(()) }
    }
    fn project(&self, record: &Record, bytes: &[u8]) -> Result<Snapshot, String> {
        if let Event::Begin { claim } = &record.event {
            let selected_destination_ok = match (record.schema, &claim.media_store_audio) {
                (1, None) => true,
                (3, Some(selection)) => media_store_selection(claim, selection),
                _ => false,
            };
            if !selected_destination_ok || record.generation != 0 || record.previous_sha256.is_some() ||
                self.rows.contains_key(&record.operation_id) || claim.operation_id != record.operation_id ||
                !hex64(&claim.operation_id) || !hex64(&claim.source_fingerprint) ||
                !text(&claim.song_id,512) || !text(&claim.rendition_key,1024) ||
                !content_uri(&claim.destination_tree_uri) || !text(&claim.requested_name,512) ||
                claim.requested_name.contains(['/', '\\']) ||
                claim.source_bytes == 0 || claim.peak_bytes <= claim.source_bytes {
                return Err("Move claim invalid, repeated or requires explicit replacement custody".into());
            }
            return Ok(Snapshot { claim: claim.clone(), generation: 0, stage: Stage::Reserved,
                destination: None, document: None, remote: None, schema: record.schema, local_cleaned: false, last_hash: hash(bytes) });
        }
        let mut next = self.rows.get(&record.operation_id).ok_or("Move claim missing")?.clone();
        let schema_ok = match (next.schema, record.schema) {
            (1, 1) => !matches!(&record.event, Event::RemoteVerified { .. } | Event::StageCleaned),
            (1, 2) => matches!(&record.event, Event::RemoteVerified { .. }),
            (2, 2) => true,
            (3, 3) => true,
            _ => false,
        };
        if !schema_ok || next.generation.checked_add(1) != Some(record.generation) ||
            record.previous_sha256.as_deref() != Some(next.last_hash.as_str()) {
            return Err("Move event chain differs".into());
        }
        next.stage = match (&next.stage, &record.event) {
            (Stage::Reserved, Event::Destination { uri }) if destination_matches(&next.claim, uri) => {
                next.destination = Some(uri.clone()); Stage::Destination
            }
            (Stage::Destination, Event::Verified { document }) if
                next.destination.as_ref() == Some(&document.uri) && document.bytes == next.claim.source_bytes &&
                hex64(&document.sha256) && document_name_matches(&next.claim, document) &&
                document_flac_matches(&next.claim, document) => {
                next.document = Some(document.clone()); Stage::Verified
            }
            (Stage::Verified, Event::RemoteVerified { receipt }) if
                text(&receipt.receiver_id,128) && text(&receipt.transfer_id,128) && hex64(&receipt.sha256) &&
                next.document.as_ref().is_some_and(|d| d.bytes == receipt.bytes && d.sha256 == receipt.sha256) &&
                !self.rows.values().any(|row| row.claim.operation_id != record.operation_id &&
                    row.remote.as_ref().is_some_and(|r| r.receiver_id == receipt.receiver_id && r.transfer_id == receipt.transfer_id)) => {
                next.remote = Some(receipt.clone()); Stage::RemoteVerified
            }
            (Stage::RemoteVerified, Event::StageCleaned) => { next.local_cleaned = true; Stage::StagedRemotely }
            (Stage::Verified, Event::Indexed) => Stage::Indexed,
            (Stage::Indexed, Event::RemovalPending) => Stage::RemovalPending,
            (Stage::StagedRemotely, Event::RemovalPending) => Stage::RemovalPending,
            (Stage::RemovalPending, Event::Moved) => Stage::Moved,
            (Stage::Reserved | Stage::Destination | Stage::Verified, Event::Abandoned) => Stage::Abandoned,
            _ => return Err("Move lifecycle transition refused".into()),
        };
        next.schema = record.schema;
        next.generation = record.generation;
        next.last_hash = hash(bytes);
        Ok(next)
    }
}
fn hash(bytes: &[u8]) -> String { format!("{:x}",Sha256::digest(bytes)) }
fn hex64(s: &str) -> bool { s.len()==64 && s.bytes().all(|b| b.is_ascii_digit() || (b'a'..=b'f').contains(&b)) }
fn text(s: &str, cap: usize) -> bool { !s.is_empty() && s.len()<=cap && !s.chars().any(char::is_control) }
fn content_uri(s: &str) -> bool {
    text(s,4096) && s.strip_prefix("content://").and_then(|tail| tail.split_once('/'))
        .is_some_and(|(authority,path)| !authority.is_empty() && !path.is_empty())
}
fn media_store_selection(claim: &Claim, selected: &MediaStoreAudioSelection) -> bool {
    // Android's returned mounted-volume name is still checked by the real
    // owner. These characters preserve its exact, unescaped URI segment.
    let volume_ok = !selected.volume.is_empty() && selected.volume.bytes().all(|b|
        b.is_ascii_alphanumeric() || b == b'_' || b == b'-');
    let folder = selected.relative_folder.strip_suffix('/');
    let folder_ok = folder.is_some_and(|path| !path.is_empty() &&
        path.split('/').all(|part| text(part,4096) && part != "." && part != ".." &&
            !part.contains('\\')));
    let mime_ok = selected.mime_type.strip_prefix("audio/").is_some_and(|subtype|
        !subtype.is_empty() && subtype != "*" && subtype.bytes().all(|b| b.is_ascii_alphanumeric() ||
            matches!(b, b'!' | b'#' | b'$' | b'%' | b'&' | b'\'' | b'*' | b'+' | b'-' |
                b'.' | b'^' | b'_' | b'`' | b'|' | b'~')));
    volume_ok && folder_ok && mime_ok && (!selected.require_full_flac || selected.mime_type == "audio/flac") &&
        media_display_name(&claim.requested_name) && content_uri(&claim.destination_tree_uri) &&
        claim.destination_tree_uri == format!("content://media/{}/audio/media", selected.volume)
}
fn media_display_name(name: &str) -> bool {
    text(name,512) && !name.trim().is_empty() && name != "." && name != ".." && !name.contains(['/', '\\'])
}
fn document_name_matches(claim: &Claim, document: &Document) -> bool {
    match (&claim.media_store_audio, &document.actual_display_name) {
        (Some(_), Some(name)) => media_display_name(name),
        (None, None) => true,
        _ => false,
    }
}
fn document_flac_matches(claim: &Claim, document: &Document) -> bool {
    match (&claim.media_store_audio, &document.flac_integrity) {
        (None, None) => true,
        (Some(selected), None) => !selected.require_full_flac,
        (Some(selected), Some(info)) => selected.mime_type == "audio/flac" &&
            info.samples > 0 && info.samples < (1u64 << 36) && info.sample_rate > 0 &&
            info.sample_rate < (1u32 << 20) && (1..=8).contains(&info.channels) &&
            (4..=32).contains(&info.bits_per_sample),
        _ => false,
    }
}
fn destination_matches(claim: &Claim, uri: &str) -> bool {
    match &claim.media_store_audio {
        None => content_uri(uri),
        Some(selected) => {
            let prefix = format!("content://media/{}/audio/media/", selected.volume);
            uri.strip_prefix(&prefix).is_some_and(|id|
                !id.is_empty() && id.bytes().all(|b| b.is_ascii_digit()) &&
                id.parse::<i64>().is_ok_and(|n| n > 0))
        }
    }
}
fn valid_name(name: &str) -> bool {
    name.len()==85 && name.is_ascii() && hex64(&name[..64]) && &name[64..65]=="-" && &name[81..]==".age" &&
        name.as_bytes()[65..81].iter().all(|b| b.is_ascii_digit() || (b'a'..=b'f').contains(b))
}
fn decode(name: &str, bytes: &[u8]) -> Result<Record,String> {
    if !valid_name(name) || bytes.len()>EVENT_CAP { return Err("Move journal event invalid".into()); }
    let record: Record = serde_json::from_slice(bytes).map_err(|_| "Move journal decoding failed")?;
    if name != format!("{}-{:016x}.age",record.operation_id,record.generation) { return Err("Move event filename differs".into()); }
    Ok(record)
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::sync::{Arc, Mutex};
    use std::sync::atomic::{AtomicBool, Ordering};

    /// Public fixture events remain in RAM. No substitute encryption is called
    /// a vault test and no installed record CLI/identity is used on the runner.
    #[derive(Clone, Default)]
    struct Memory {
        events: Arc<Mutex<BTreeMap<String,Vec<u8>>>>,
        fail_after_write: Arc<AtomicBool>,
    }
    impl Store for Memory {
        fn load(&mut self) -> Result<Vec<(String,Vec<u8>)>,String> {
            Ok(self.events.lock().unwrap().iter().map(|(n,b)| (n.clone(),b.clone())).collect())
        }
        fn append(&mut self, name: &str, bytes: &[u8]) -> Result<(),String> {
            let mut events = self.events.lock().unwrap();
            if events.contains_key(name) { return Err("Already persisted".into()); }
            events.insert(name.to_owned(),bytes.to_vec());
            if self.fail_after_write.swap(false,Ordering::SeqCst) { return Err("Durability/readback UNKNOWN".into()); }
            Ok(())
        }
    }
    fn claim(digit: char) -> Claim {
        Claim { operation_id: digit.to_string().repeat(64), song_id: format!("fixture-song-{digit}"),
            rendition_key: format!("fixture-source:fixture-song-{digit}"), source_fingerprint: "a".repeat(64),
            destination_tree_uri: "content://fixture.documents/tree/music".into(), requested_name: "song.flac".into(),
            source_bytes: 90, peak_bytes: 100, media_store_audio: None }
    }
    fn document() -> Document {
        Document { uri: "content://fixture.documents/document/song".into(), bytes:90, sha256:"b".repeat(64), actual_display_name:None, flac_integrity:None }
    }
    fn media_claim(digit: char) -> Claim {
        let mut c = claim(digit);
        c.destination_tree_uri = "content://media/external_primary/audio/media".into();
        c.media_store_audio = Some(MediaStoreAudioSelection {
            volume: "external_primary".into(), relative_folder: "Music/Selected/".into(),
            mime_type: "audio/flac".into(), require_full_flac:false,
        });
        c
    }
    fn media_document() -> Document {
        Document { uri: "content://media/external_primary/audio/media/42".into(),
            actual_display_name:Some("song (1).flac".into()), ..document() }
    }
    fn receipt() -> RemoteReceipt {
        RemoteReceipt { receiver_id:"public-fixture-receiver".into(), transfer_id:"public-fixture-transfer".into(),
            bytes:document().bytes, sha256:document().sha256 }
    }
    fn begin(j: &mut Journal<Memory>, c: &Claim) {
        j.record(&c.operation_id,None,Event::Begin{claim:c.clone()}).unwrap();
    }
    fn step(j: &mut Journal<Memory>, c: &Claim, event: Event) {
        let generation = j.snapshot(&c.operation_id).unwrap().unwrap().generation;
        j.record(&c.operation_id,Some(generation),event).unwrap();
    }
    fn verify(j: &mut Journal<Memory>, c: &Claim) {
        step(j,c,Event::Destination{uri:document().uri});
        step(j,c,Event::Verified{document:document()});
    }
    // Construct a SYNTHETIC old-schema event solely to test replay of a
    // pre-existing history. This cannot start a real removal or create an
    // application grant, and is not available outside this RAM fixture.
    fn old_fixture_step(j: &mut Journal<Memory>, c: &Claim, event: Event) {
        let prior=j.rows.get(&c.operation_id).unwrap();
        let record=Record { schema:1, operation_id:c.operation_id.clone(), generation:prior.generation+1,
            previous_sha256:Some(prior.last_hash.clone()), event };
        let bytes=serde_json::to_vec(&record).unwrap();
        let projected=j.project(&record,&bytes).unwrap();
        j.store.append(&format!("{}-{:016x}.age",c.operation_id,record.generation),&bytes).unwrap();
        j.rows.insert(c.operation_id.clone(),projected);
    }
    #[test]
    fn media_store_claim_retains_exact_target_across_remote_cleanup_and_restart() {
        let memory=Memory::default(); let c=media_claim('a'); let mut j=Journal::open(memory.clone(),1000).unwrap();
        begin(&mut j,&c);
        assert_eq!(j.snapshot(&c.operation_id).unwrap().unwrap().schema,3);
        step(&mut j,&c,Event::Destination{uri:media_document().uri});
        step(&mut j,&c,Event::Verified{document:media_document()});
        step(&mut j,&c,Event::RemoteVerified{receipt:receipt()});
        assert!(j.record(&c.operation_id,Some(3),Event::RemovalPending).is_err());
        drop(j);
        let mut restored=Journal::open(memory.clone(),1000).unwrap();
        let row=restored.snapshot(&c.operation_id).unwrap().unwrap();
        assert_eq!(row.claim,c); assert_eq!(row.schema,3);
        assert_eq!(row.document.as_ref().unwrap().actual_display_name.as_deref(),Some("song (1).flac"));
        assert_eq!(row.stage,Stage::RemoteVerified); assert_eq!(restored.reserved_bytes().unwrap(),100);
        step(&mut restored,&c,Event::StageCleaned);
        drop(restored);
        let again=Journal::open(memory,1000).unwrap();
        assert_eq!(again.snapshot(&c.operation_id).unwrap().unwrap().stage,Stage::StagedRemotely);
        assert_eq!(again.snapshot(&c.operation_id).unwrap().unwrap().claim,c);
        assert_eq!(again.reserved_bytes().unwrap(),0);
    }
    #[test]
    fn media_store_verified_name_is_required_and_legacy_documents_stay_unchanged() {
        let legacy=document();
        let legacy_json=serde_json::to_string(&legacy).unwrap();
        assert!(!legacy_json.contains("actual_display_name"));
        assert!(!legacy_json.contains("flac_integrity"));
        assert_eq!(serde_json::from_str::<Document>(&legacy_json).unwrap(),legacy);
        let memory=Memory::default(); let c=media_claim('e'); let mut j=Journal::open(memory.clone(),1000).unwrap();
        begin(&mut j,&c); step(&mut j,&c,Event::Destination{uri:media_document().uri});
        for name in [None,Some("".into()),Some("../other".into()),Some("\u{00a0}".into()),Some("a".repeat(513))] {
            let mut doc=media_document(); doc.actual_display_name=name;
            assert!(j.record(&c.operation_id,Some(1),Event::Verified{document:doc}).is_err());
        }
        assert_eq!(memory.events.lock().unwrap().len(),2);
        step(&mut j,&c,Event::Verified{document:media_document()});
        let legacy_claim=claim('f'); begin(&mut j,&legacy_claim);
        step(&mut j,&legacy_claim,Event::Destination{uri:document().uri});
        let mut wrong=document(); wrong.actual_display_name=Some("song.flac".into());
        assert!(j.record(&legacy_claim.operation_id,Some(1),Event::Verified{document:wrong}).is_err());
        step(&mut j,&legacy_claim,Event::Verified{document:document()});
    }
    #[test]
    fn strict_flac_claim_requires_valid_complete_decoder_observation() {
        let memory=Memory::default(); let mut c=media_claim('a');
        c.media_store_audio.as_mut().unwrap().require_full_flac=true;
        let mut j=Journal::open(memory.clone(),1000).unwrap();
        begin(&mut j,&c); step(&mut j,&c,Event::Destination{uri:media_document().uri});
        assert!(j.record(&c.operation_id,Some(1),Event::Verified{document:media_document()}).is_err());
        let evidence=FlacIntegrityEvidence { samples:90, sample_rate:44100, channels:2, bits_per_sample:24 };
        for invalid in [
            FlacIntegrityEvidence{samples:0,..evidence.clone()},
            FlacIntegrityEvidence{samples:1u64<<36,..evidence.clone()},
            FlacIntegrityEvidence{sample_rate:0,..evidence.clone()},
            FlacIntegrityEvidence{sample_rate:1u32<<20,..evidence.clone()},
            FlacIntegrityEvidence{channels:0,..evidence.clone()},
            FlacIntegrityEvidence{bits_per_sample:3,..evidence.clone()},
        ] {
            let mut doc=media_document(); doc.flac_integrity=Some(invalid);
            assert!(j.record(&c.operation_id,Some(1),Event::Verified{document:doc}).is_err());
        }
        assert_eq!(memory.events.lock().unwrap().len(),2);
        let mut doc=media_document(); doc.flac_integrity=Some(evidence.clone());
        step(&mut j,&c,Event::Verified{document:doc.clone()});
        drop(j);
        let restored=Journal::open(memory,1000).unwrap();
        let row=restored.snapshot(&c.operation_id).unwrap().unwrap();
        assert!(row.claim.media_store_audio.as_ref().unwrap().require_full_flac);
        assert_eq!(row.document.as_ref(),Some(&doc));
        assert_eq!(row.document.as_ref().unwrap().flac_integrity.as_ref(),Some(&evidence));
    }
    #[test]
    fn flac_policy_does_not_change_legacy_or_accept_wrong_mime() {
        let default=media_claim('b');
        let selection=default.media_store_audio.as_ref().unwrap();
        let json=serde_json::to_string(selection).unwrap();
        assert!(!json.contains("require_full_flac"));
        assert_eq!(serde_json::from_str::<MediaStoreAudioSelection>(&json).unwrap(),selection.clone());
        let memory=Memory::default(); let mut j=Journal::open(memory.clone(),1000).unwrap();
        let mut wrong=media_claim('c'); let selected=wrong.media_store_audio.as_mut().unwrap();
        selected.require_full_flac=true; selected.mime_type="audio/mpeg".into();
        assert!(j.record(&wrong.operation_id,None,Event::Begin{claim:wrong.clone()}).is_err());
        assert!(memory.events.lock().unwrap().is_empty());
        let legacy=claim('d'); begin(&mut j,&legacy);
        step(&mut j,&legacy,Event::Destination{uri:document().uri});
        let mut doc=document(); doc.flac_integrity=Some(FlacIntegrityEvidence {
            samples:90, sample_rate:44100, channels:2, bits_per_sample:24,
        });
        assert!(j.record(&legacy.operation_id,Some(1),Event::Verified{document:doc}).is_err());
        step(&mut j,&legacy,Event::Verified{document:document()});
    }
    #[test]
    fn media_store_claim_rejects_mismatched_selection_before_persistence() {
        for defect in ["collection","volume","folder","path_traversal","mime","wildcard","name"] {
            let memory=Memory::default(); let mut j=Journal::open(memory.clone(),1000).unwrap(); let mut c=media_claim('a');
            match defect {
                "collection" => c.destination_tree_uri="content://media/other/audio/media".into(),
                "volume" => c.media_store_audio.as_mut().unwrap().volume="other/volume".into(),
                "folder" => c.media_store_audio.as_mut().unwrap().relative_folder="Music/Selected".into(),
                "path_traversal" => c.media_store_audio.as_mut().unwrap().relative_folder="Music/../Other/".into(),
                "mime" => c.media_store_audio.as_mut().unwrap().mime_type="video/mp4".into(),
                "wildcard" => c.media_store_audio.as_mut().unwrap().mime_type="audio/*".into(),
                _ => c.requested_name="..".into(),
            }
            assert!(j.record(&c.operation_id,None,Event::Begin{claim:c.clone()}).is_err(),"{defect}");
            assert!(memory.events.lock().unwrap().is_empty(),"{defect}");
        }
    }
    #[test]
    fn media_store_row_must_match_selected_collection_and_positive_id() {
        let memory=Memory::default(); let c=media_claim('b'); let mut j=Journal::open(memory.clone(),1000).unwrap();
        begin(&mut j,&c);
        for uri in ["content://media/other/audio/media/42", "content://other/external_primary/audio/media/42",
            "content://media/external_primary/audio/media/0", "content://media/external_primary/audio/media/-1",
            "content://media/external_primary/audio/media/42?other=1", "content://media/external_primary/audio/media/1/2"] {
            assert!(j.record(&c.operation_id,Some(0),Event::Destination{uri:uri.into()}).is_err(),"{uri}");
        }
        assert_eq!(memory.events.lock().unwrap().len(),1);
        step(&mut j,&c,Event::Destination{uri:media_document().uri});
        assert_eq!(j.snapshot(&c.operation_id).unwrap().unwrap().stage,Stage::Destination);
    }
    #[test]
    fn media_store_schema_cannot_downgrade_and_legacy_claim_bytes_stay_unchanged() {
        let legacy=claim('c');
        let legacy_json=serde_json::to_string(&legacy).unwrap();
        assert!(!legacy_json.contains("media_store_audio"));
        assert_eq!(serde_json::from_str::<Claim>(&legacy_json).unwrap(),legacy);
        for bad_schema in [1,2] {
            let memory=Memory::default(); let c=media_claim('d'); let mut j=Journal::open(memory.clone(),1000).unwrap();
            begin(&mut j,&c); step(&mut j,&c,Event::Destination{uri:media_document().uri});
            drop(j);
            let name=format!("{}-{:016x}.age",c.operation_id,1);
            let mut events=memory.events.lock().unwrap();
            let mut value:serde_json::Value=serde_json::from_slice(&events[&name]).unwrap();
            value["schema"]=bad_schema.into(); events.insert(name,serde_json::to_vec(&value).unwrap());
            drop(events);
            assert!(Journal::open(memory,1000).is_err());
        }
    }
    #[test]
    fn new_original_removal_is_refused_and_keeps_identity_and_charge() {
        let memory=Memory::default(); let mut j=Journal::open(memory.clone(),1000).unwrap(); let c=claim('1');
        begin(&mut j,&c);
        assert_eq!(j.reserved_bytes().unwrap(),100);
        assert!(j.document(&c.song_id,&c.rendition_key).unwrap().is_none());
        verify(&mut j,&c);
        assert!(j.document(&c.song_id,&c.rendition_key).unwrap().is_none());
        step(&mut j,&c,Event::Indexed);
        assert_eq!(j.document(&c.song_id,&c.rendition_key).unwrap(),Some(&document()));
        assert!(j.document(&c.song_id,"other-rendition").unwrap().is_none());
        assert!(j.record(&c.operation_id,Some(3),Event::Abandoned).is_err());
        assert!(j.record(&c.operation_id,Some(3),Event::RemovalPending).is_err());
        assert_eq!(j.reserved_bytes().unwrap(),100);
        assert_eq!(memory.events.lock().unwrap().len(),4);
        let mut restored=Journal::open(memory,1000).unwrap();
        assert_eq!(restored.document(&c.song_id,&c.rendition_key).unwrap(),Some(&document()));
        assert_eq!(restored.snapshot(&c.operation_id).unwrap().unwrap().stage,Stage::Indexed);
        assert_eq!(restored.reserved_bytes().unwrap(),100);
        assert!(restored.record(&c.operation_id,Some(3),Event::RemovalPending).is_err());
        assert!(restored.record(&c.operation_id,None,Event::Begin{claim:c.clone()}).is_err());
        assert!(restored.record(&c.operation_id,Some(3),Event::Moved).is_err());
    }
    #[test]
    fn restart_reconstructs_every_unsettled_charge_without_side_effect_replay() {
        let memory=Memory::default(); let c=claim('2'); let mut j=Journal::open(memory.clone(),1000).unwrap();
        begin(&mut j,&c);
        for event in [Event::Destination{uri:document().uri}, Event::Verified{document:document()}, Event::Indexed] {
            j=Journal::open(memory.clone(),1000).unwrap();
            assert_eq!(j.reserved_bytes().unwrap(),100);
            step(&mut j,&c,event);
        }
        let restored=Journal::open(memory.clone(),50).unwrap();
        assert_eq!(restored.reserved_bytes().unwrap(),100);
        assert_eq!(memory.events.lock().unwrap().len(),4); // Loading wrote NOTHING.
    }
    #[test]
    fn admission_recovery_includes_pending_and_consumed_terminal_ids() {
        let memory=Memory::default(); let mut j=Journal::open(memory.clone(),1000).unwrap();
        let pending=claim('1'); let moved=claim('2'); let abandoned=claim('3');
        begin(&mut j,&pending); begin(&mut j,&moved); begin(&mut j,&abandoned);
        verify(&mut j,&moved); step(&mut j,&moved,Event::Indexed);
        old_fixture_step(&mut j,&moved,Event::RemovalPending); step(&mut j,&moved,Event::Moved);
        step(&mut j,&abandoned,Event::Abandoned);
        let restored=Journal::open(memory.clone(),50).unwrap();
        let snapshot=restored.admission_recovery().unwrap();
        assert_eq!(snapshot.reserved_bytes,100);
        assert_eq!(snapshot.attempts,vec![
            RecoveryAttempt{operation_id:pending.operation_id,source_bytes:90,peak_bytes:100,unsettled:true},
            RecoveryAttempt{operation_id:moved.operation_id,source_bytes:90,peak_bytes:100,unsettled:false},
            RecoveryAttempt{operation_id:abandoned.operation_id,source_bytes:90,peak_bytes:100,unsettled:false},
        ]);
        assert_eq!(memory.events.lock().unwrap().len(),9); // Read-only recovery.
    }
    #[test]
    fn legacy_pending_replays_without_starting_removal_and_can_be_reconciled() {
        let memory=Memory::default(); let mut j=Journal::open(memory.clone(),1000).unwrap(); let c=claim('b');
        begin(&mut j,&c); verify(&mut j,&c); step(&mut j,&c,Event::Indexed);
        old_fixture_step(&mut j,&c,Event::RemovalPending);
        drop(j);
        let mut restored=Journal::open(memory.clone(),1000).unwrap();
        assert_eq!(restored.snapshot(&c.operation_id).unwrap().unwrap().stage,Stage::RemovalPending);
        assert_eq!(restored.reserved_bytes().unwrap(),100);
        assert_eq!(memory.events.lock().unwrap().len(),5); // Replay performs no side effect.
        // This event is a synthetic owner observation, not removal authority.
        step(&mut restored,&c,Event::Moved);
        assert_eq!(restored.reserved_bytes().unwrap(),0);
    }
    #[test]
    fn remote_verified_and_cleaned_releases_only_temporary_charge_across_restart() {
        let memory=Memory::default(); let c=claim('c'); let mut j=Journal::open(memory.clone(),100).unwrap();
        begin(&mut j,&c); verify(&mut j,&c);
        step(&mut j,&c,Event::RemoteVerified{receipt:receipt()});
        assert_eq!(j.reserved_bytes().unwrap(),100);
        assert_eq!(j.snapshot(&c.operation_id).unwrap().unwrap().stage,Stage::RemoteVerified);
        assert_eq!(j.remote_receipt(&c.song_id,&c.rendition_key).unwrap(),Some(&receipt()));
        drop(j);
        let mut restored=Journal::open(memory.clone(),100).unwrap();
        assert_eq!(restored.reserved_bytes().unwrap(),100);
        step(&mut restored,&c,Event::StageCleaned);
        assert_eq!(restored.reserved_bytes().unwrap(),0);
        assert!(restored.document(&c.song_id,&c.rendition_key).unwrap().is_none());
        assert_eq!(restored.remote_receipt(&c.song_id,&c.rendition_key).unwrap(),Some(&receipt()));
        let generation=restored.snapshot(&c.operation_id).unwrap().unwrap().generation;
        assert!(restored.record(&c.operation_id,Some(generation),Event::RemovalPending).is_err());
        assert!(restored.record(&c.operation_id,Some(generation),Event::Moved).is_err());
        drop(restored);
        let mut again=Journal::open(memory.clone(),100).unwrap();
        assert_eq!(again.snapshot(&c.operation_id).unwrap().unwrap().stage,Stage::StagedRemotely);
        assert_eq!(again.admission_recovery().unwrap().reserved_bytes,0);
        assert!(!again.admission_recovery().unwrap().attempts[0].unsettled);
        let another=claim('d'); begin(&mut again,&another); // Separate track can use the recovered budget.
        assert_eq!(again.reserved_bytes().unwrap(),100);
        assert!(again.record(&c.operation_id,None,Event::Begin{claim:c.clone()}).is_err());
    }
    #[test]
    fn remote_receipt_must_match_verified_copy_and_transfer_identity_is_unique() {
        let memory=Memory::default(); let mut j=Journal::open(memory.clone(),1000).unwrap(); let a=claim('c');
        begin(&mut j,&a); verify(&mut j,&a);
        assert!(j.record(&a.operation_id,Some(2),Event::StageCleaned).is_err());
        for wrong in [RemoteReceipt{bytes:89,..receipt()}, RemoteReceipt{sha256:"c".repeat(64),..receipt()},
                RemoteReceipt{receiver_id:String::new(),..receipt()}, RemoteReceipt{transfer_id:String::new(),..receipt()}] {
            assert!(j.record(&a.operation_id,Some(2),Event::RemoteVerified{receipt:wrong}).is_err());
        }
        assert_eq!(memory.events.lock().unwrap().len(),3);
        step(&mut j,&a,Event::RemoteVerified{receipt:receipt()});
        let b=claim('d'); begin(&mut j,&b);
        let other=Document{uri:"content://fixture.documents/document/other".into(),..document()};
        step(&mut j,&b,Event::Destination{uri:other.uri.clone()});
        step(&mut j,&b,Event::Verified{document:other});
        assert!(j.record(&b.operation_id,Some(2),Event::RemoteVerified{receipt:receipt()}).is_err());
        assert_eq!(memory.events.lock().unwrap().len(),7);
    }
    #[test]
    fn live_destination_ownership_refuses_conflicts_and_replays_cleaned_reuse() {
        let memory=Memory::default(); let mut j=Journal::open(memory.clone(),1000).unwrap();
        // Old operation sorts AFTER the later one during replay.
        let old=claim('f'); let later=claim('0'); begin(&mut j,&old); verify(&mut j,&old);
        begin(&mut j,&later);
        assert!(j.record(&later.operation_id,Some(0),Event::Destination{uri:document().uri}).is_err());
        step(&mut j,&old,Event::RemoteVerified{receipt:receipt()}); step(&mut j,&old,Event::StageCleaned);
        step(&mut j,&later,Event::Destination{uri:document().uri});
        drop(j);
        let restored=Journal::open(memory.clone(),1000).unwrap();
        assert_eq!(restored.snapshot(&old.operation_id).unwrap().unwrap().stage,Stage::StagedRemotely);
        assert_eq!(restored.snapshot(&later.operation_id).unwrap().unwrap().stage,Stage::Destination);
        assert_eq!(restored.reserved_bytes().unwrap(),100);
    }
    #[test]
    fn remote_cleanup_unknown_refuses_replay_and_schema_downgrade() {
        let memory=Memory::default(); let c=claim('e'); let mut j=Journal::open(memory.clone(),1000).unwrap();
        begin(&mut j,&c); verify(&mut j,&c); step(&mut j,&c,Event::RemoteVerified{receipt:receipt()});
        memory.fail_after_write.store(true,Ordering::SeqCst);
        assert!(j.record(&c.operation_id,Some(3),Event::StageCleaned).is_err());
        assert!(j.reserved_bytes().is_err());
        assert!(j.record(&c.operation_id,Some(3),Event::StageCleaned).is_err());
        let restored=Journal::open(memory.clone(),1000).unwrap();
        assert_eq!(restored.snapshot(&c.operation_id).unwrap().unwrap().stage,Stage::StagedRemotely);
        assert_eq!(restored.reserved_bytes().unwrap(),0);
        let name=format!("{}-{:016x}.age",c.operation_id,4);
        let mut events=memory.events.lock().unwrap();
        let mut value:serde_json::Value=serde_json::from_slice(&events[&name]).unwrap();
        value["schema"]=1.into(); events.insert(name,serde_json::to_vec(&value).unwrap());
        drop(events);
        assert!(Journal::open(memory,1000).is_err());
    }
    #[test]
    fn accepted_but_uncertain_persistence_poisoned_until_explicit_recovery() {
        let memory=Memory::default(); let c=claim('3'); let mut j=Journal::open(memory.clone(),1000).unwrap();
        memory.fail_after_write.store(true,Ordering::SeqCst);
        assert!(j.record(&c.operation_id,None,Event::Begin{claim:c.clone()}).is_err());
        assert!(j.reserved_bytes().is_err());
        assert!(j.snapshot(&c.operation_id).is_err());
        assert!(j.document(&c.song_id,&c.rendition_key).is_err());
        assert!(j.admission_recovery().is_err());
        assert!(j.record(&c.operation_id,None,Event::Begin{claim:c.clone()}).is_err());
        assert_eq!(memory.events.lock().unwrap().len(),1);
        let mut restored=Journal::open(memory,1000).unwrap();
        assert_eq!(restored.reserved_bytes().unwrap(),100);
        assert!(restored.record(&c.operation_id,None,Event::Begin{claim:c.clone()}).is_err());
        let mut replacement=c.clone(); replacement.operation_id="4".repeat(64);
        assert!(restored.record(&replacement.operation_id,None,Event::Begin{claim:replacement.clone()}).is_err());
    }
    #[test]
    fn size_admission_happens_before_persistence_and_is_adaptive() {
        let memory=Memory::default(); let mut j=Journal::open(memory.clone(),150).unwrap();
        let a=claim('4'); begin(&mut j,&a);
        let mut b=claim('5');
        assert!(j.record(&b.operation_id,None,Event::Begin{claim:b.clone()}).is_err());
        assert_eq!(memory.events.lock().unwrap().len(),1);
        b.source_bytes=49; b.peak_bytes=50; begin(&mut j,&b);
        assert_eq!(j.reserved_bytes().unwrap(),150);
        let mut c=claim('6'); c.source_bytes=u64::MAX-1; c.peak_bytes=u64::MAX;
        assert!(j.record(&c.operation_id,None,Event::Begin{claim:c.clone()}).is_err());
        assert_eq!(memory.events.lock().unwrap().len(),2);
    }
    #[test]
    fn abandoned_original_id_stays_consumed_but_a_new_authorized_attempt_can_recover() {
        let memory=Memory::default(); let mut j=Journal::open(memory.clone(),1000).unwrap(); let old=claim('f');
        begin(&mut j,&old); step(&mut j,&old,Event::Abandoned);
        let mut new=old.clone(); new.operation_id="0".repeat(64);
        begin(&mut j,&new);
        // Lexically the new operation is loaded FIRST; recovery is per-operation.
        let restored=Journal::open(memory,1000).unwrap();
        assert_eq!(restored.reserved_bytes().unwrap(),100);
        assert_eq!(restored.snapshot(&old.operation_id).unwrap().unwrap().stage,Stage::Abandoned);
    }
    #[test]
    fn wrong_generation_destination_length_digest_or_transition_never_persist() {
        let memory=Memory::default(); let mut j=Journal::open(memory.clone(),1000).unwrap(); let c=claim('7'); begin(&mut j,&c);
        assert!(j.record(&c.operation_id,Some(1),Event::Destination{uri:document().uri}).is_err());
        assert!(j.record(&c.operation_id,Some(0),Event::Moved).is_err());
        assert!(j.record(&c.operation_id,Some(0),Event::Indexed).is_err());
        assert!(j.record(&c.operation_id,Some(0),Event::Destination{uri:"https://fixture.invalid/song".into()}).is_err());
        assert_eq!(memory.events.lock().unwrap().len(),1);
        step(&mut j,&c,Event::Destination{uri:document().uri});
        for doc in [Document{bytes:89,..document()},Document{sha256:"no-digest".into(),..document()},
                Document{uri:"content://fixture.documents/document/OTHER".into(),..document()}] {
            assert!(j.record(&c.operation_id,Some(1),Event::Verified{document:doc}).is_err());
        }
        assert_eq!(memory.events.lock().unwrap().len(),2);
    }
    #[test]
    fn missing_middle_chain_modified_record_and_wrong_filename_refuse_recovery() {
        for defect in ["missing","chain","unknown_field","filename","unicode_name"] {
            let memory=Memory::default(); let mut j=Journal::open(memory.clone(),1000).unwrap(); let c=claim('8'); begin(&mut j,&c); verify(&mut j,&c);
            let name=format!("{}-{:016x}.age",c.operation_id,1);
            let mut events=memory.events.lock().unwrap();
            match defect {
                "missing" => { events.remove(&name); }
                "filename" => { let bytes=events.remove(&name).unwrap(); events.insert(format!("{}-{:016x}.age","9".repeat(64),1),bytes); }
                "unicode_name" => { events.insert(format!("{}é{}","x".repeat(63),"x".repeat(20)),b"{}".to_vec()); }
                _ => {
                    let mut value: serde_json::Value=serde_json::from_slice(&events[&name]).unwrap();
                    if defect=="chain" { value["previous_sha256"]="c".repeat(64).into(); }
                    else { value["unrecognized"]=true.into(); }
                    events.insert(name,serde_json::to_vec(&value).unwrap());
                }
            }
            drop(events);
            assert!(Journal::open(memory,1000).is_err(),"{defect}");
        }
    }
    #[test]
    fn source_claim_and_planned_destination_validation_precedes_any_write() {
        for defect in ["unknown_size","no_overhead","fingerprint","tree","name"] {
            let memory=Memory::default(); let mut j=Journal::open(memory.clone(),1000).unwrap(); let mut c=claim('a');
            match defect {
                "unknown_size" => c.source_bytes=0,
                "no_overhead" => c.peak_bytes=c.source_bytes,
                "fingerprint" => c.source_fingerprint="missing".into(),
                "tree" => c.destination_tree_uri="file:///NOT-a-grant".into(),
                _ => c.requested_name="../song.flac".into(),
            }
            assert!(j.record(&c.operation_id,None,Event::Begin{claim:c.clone()}).is_err());
            assert!(memory.events.lock().unwrap().is_empty());
        }
    }
}
