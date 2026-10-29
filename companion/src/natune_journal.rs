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
}

#[derive(Clone, Debug, Serialize, Deserialize, PartialEq, Eq)]
#[serde(deny_unknown_fields)]
pub struct Document {
    pub uri: String,
    pub bytes: u64,
    pub sha256: String,
}

#[derive(Clone, Debug, Serialize, Deserialize, PartialEq, Eq)]
#[serde(tag = "kind", deny_unknown_fields)]
pub enum Event {
    Begin { claim: Claim },
    Destination { uri: String },
    Verified { document: Document },
    Indexed,
    RemovalPending,
    Moved,
    // Only before indexing; owner must verify partial/duplicate output cleanup.
    // A timeout or cancellation notification is NOT this observation.
    Abandoned,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Stage { Reserved, Destination, Verified, Indexed, RemovalPending, Moved, Abandoned }

#[derive(Clone, Debug)]
pub struct Snapshot {
    pub claim: Claim,
    pub generation: u64,
    pub stage: Stage,
    pub document: Option<Document>,
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
        self.rows.values().filter(|r| !matches!(r.stage, Stage::Moved | Stage::Abandoned))
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
            unsettled: !matches!(row.stage, Stage::Moved | Stage::Abandoned),
        }).collect();
        Ok(AdmissionRecovery { attempts, reserved_bytes })
    }
    /// Reference only: caller must check current grant/document/hash/owner state.
    /// No URI/length descriptor is promoted into a native write or removal permit.
    pub fn document(&self, song: &str, rendition: &str) -> Result<Option<&Document>, String> {
        self.certain()?;
        Ok(self.rows.values().find(|r| r.claim.song_id == song && r.claim.rendition_key == rendition &&
            matches!(r.stage, Stage::Indexed | Stage::RemovalPending | Stage::Moved))
            .and_then(|r| r.document.as_ref()))
    }
    /// expected_generation=None only for an ORIGINAL Begin. After an uncertain
    /// append this object refuses all access; reopen/reconcile, never auto-retry.
    pub fn record(&mut self, operation_id: &str, expected_generation: Option<u64>, event: Event)
            -> Result<(), String> {
        self.certain()?;
        // A v1 journal has no bound computer-copy receipt, phone playback
        // acceptance, or reconciled KEEP decision. A caller-supplied event is
        // not that proof. Keep historical pending records readable so their
        // original owners can reconcile them, but never start a NEW removal
        // until a versioned real-owner admission path has been integrated.
        if matches!(&event, Event::RemovalPending) {
            return Err("New original removal requires bound remote-copy, phone-playback and selection checks".into());
        }
        if !hex64(operation_id) { return Err("Move operation identity invalid".into()); }
        let prior = self.rows.get(operation_id);
        if prior.map(|r| r.generation) != expected_generation { return Err("Move generation conflict".into()); }
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
        let record = Record { schema: 1, operation_id: operation_id.to_owned(), generation,
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
            if record.schema != 1 || record.generation != 0 || record.previous_sha256.is_some() ||
                self.rows.contains_key(&record.operation_id) || claim.operation_id != record.operation_id ||
                !hex64(&claim.operation_id) || !hex64(&claim.source_fingerprint) ||
                !text(&claim.song_id,512) || !text(&claim.rendition_key,1024) ||
                !content_uri(&claim.destination_tree_uri) || !text(&claim.requested_name,512) ||
                claim.requested_name.contains(['/', '\\']) ||
                claim.source_bytes == 0 || claim.peak_bytes <= claim.source_bytes {
                return Err("Move claim invalid, repeated or requires explicit replacement custody".into());
            }
            return Ok(Snapshot { claim: claim.clone(), generation: 0, stage: Stage::Reserved,
                destination: None, document: None, last_hash: hash(bytes) });
        }
        let mut next = self.rows.get(&record.operation_id).ok_or("Move claim missing")?.clone();
        if record.schema != 1 || next.generation.checked_add(1) != Some(record.generation) ||
            record.previous_sha256.as_deref() != Some(next.last_hash.as_str()) {
            return Err("Move event chain differs".into());
        }
        next.stage = match (&next.stage, &record.event) {
            (Stage::Reserved, Event::Destination { uri }) if content_uri(uri) => {
                next.destination = Some(uri.clone()); Stage::Destination
            }
            (Stage::Destination, Event::Verified { document }) if
                next.destination.as_ref() == Some(&document.uri) && document.bytes == next.claim.source_bytes && hex64(&document.sha256) => {
                next.document = Some(document.clone()); Stage::Verified
            }
            (Stage::Verified, Event::Indexed) => Stage::Indexed,
            (Stage::Indexed, Event::RemovalPending) => Stage::RemovalPending,
            (Stage::RemovalPending, Event::Moved) => Stage::Moved,
            (Stage::Reserved | Stage::Destination | Stage::Verified, Event::Abandoned) => Stage::Abandoned,
            _ => return Err("Move lifecycle transition refused".into()),
        };
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
            source_bytes: 90, peak_bytes: 100 }
    }
    fn document() -> Document {
        Document { uri: "content://fixture.documents/document/song".into(), bytes:90, sha256:"b".repeat(64) }
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
