use crate::{cli, private, whisper};
use serde::Serialize;
use serde_json::{json, Value};
use std::collections::VecDeque;
use std::fs;
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Mutex};
use std::thread::{self, JoinHandle};

#[derive(Clone, Serialize)]
struct Job {
    id: String,
    state: &'static str,
    text: String,
    error: Option<String>,
}
struct Inner {
    jobs: VecDeque<Job>,
    active: Option<(String, Arc<AtomicBool>)>,
    worker: Option<JoinHandle<()>>,
    recovery_pending: Option<Vec<u8>>,
}
pub struct Service {
    state: PathBuf,
    inner: Mutex<Inner>,
    stopping: Arc<AtomicBool>,
}
enum Work {
    Query(cli::Query),
    Audio(Vec<u8>),
}

impl Service {
    pub fn new(state: &Path, stopping: Arc<AtomicBool>) -> Result<Arc<Self>, String> {
        Ok(Arc::new(Self {
            state: state.to_owned(),
            stopping,
            inner: Mutex::new(Inner {
                jobs: VecDeque::new(),
                active: None,
                worker: None,
                recovery_pending: None,
            }),
        }))
    }
    pub fn whisper_family(&self) -> Option<&'static str> { whisper::family(&self.state) }
    pub fn models(&self) -> Result<Value, String> {
        Ok(json!({"models":cli::models()?,"source":"installed-cli-cache"}))
    }
    pub fn query(self: &Arc<Self>, bytes: &[u8]) -> Result<Value, String> {
        let query: cli::Query =
            serde_json::from_slice(bytes).map_err(|_| "Invalid keyboard request JSON")?;
        query.validate(&cli::models()?)?;
        if query.mode != "translate" || query.prompt.len()>8192 || !query.context.is_empty() { return Err("Only explicit bounded translation is enabled".into()); }
        self.start(Work::Query(query))
    }
    pub fn transcribe(self: &Arc<Self>, bytes: Vec<u8>) -> Result<Value, String> {
        whisper::validate_wav(&bytes)?;
        // Check availability before retaining a recording or starting a job.
        whisper::available(&self.state)?;
        self.start(Work::Audio(bytes))
    }
    fn start(self: &Arc<Self>, work: Work) -> Result<Value, String> {
        let mut inner = self.inner.lock().map_err(|_| "Job state unavailable")?;
        if self.stopping.load(Ordering::Relaxed) {
            return Err("Bridge is stopping".into());
        }
        if inner.recovery_pending.is_some() {
            return Err("Encrypted recovery unavailable; further requests refused".into());
        }
        if inner.active.is_some() {
            return Err("A keyboard request is already running; cancel or finish it first".into());
        }
        if let Some(worker) = inner.worker.take() {
            let _ = worker.join();
        }
        let id = private::random_token()?;
        // Durable encrypted no-replay intent precedes any external engine invocation.
        // An interrupted worker remains recoverable even if completion sealing fails.
        let intent=serde_json::to_vec(&json!({"job_id":id,"state":"INTENT_UNKNOWN_UNTIL_COMPLETION_NO_REPLAY"})).unwrap();
        crate::vault::retain(&intent)?;
        let cancelled = Arc::new(AtomicBool::new(false));
        inner.active = Some((id.clone(), Arc::clone(&cancelled)));
        while inner.jobs.len() >= 8 {
            inner.jobs.pop_front();
        }
        inner.jobs.push_back(Job {
            id: id.clone(),
            state: "running",
            text: String::new(),
            error: None,
        });
        let service = Arc::clone(self);
        let worker_id = id.clone();
        let worker = thread::spawn(move || {
            let outcome = match work {
                Work::Query(query) => cli::query(&query, &service.state, &cancelled),
                Work::Audio(bytes) => whisper::transcribe(&service.state, &service.state, &cancelled, &bytes),
            };
            let mut pending=None;
            if outcome.is_err() {
                // Preserve an encrypted recovery/UNKNOWN receipt; never raw payloads.
                let receipt=serde_json::to_vec(&json!({"job_id":worker_id,"state":"FAILED_OR_CANCELLED_NO_REPLAY","cancelled":cancelled.load(Ordering::Relaxed)})).unwrap();
                if crate::vault::retain(&receipt).is_err() { pending=Some(receipt); }
            }
            let mut inner = service.inner.lock().unwrap();
            inner.recovery_pending=pending;
            if let Some(job) = inner.jobs.iter_mut().find(|job| job.id == worker_id) {
                if cancelled.load(Ordering::Relaxed) {
                    job.state = "cancelled";
                    job.error = None;
                } else {
                    match outcome {
                        Ok(text) => {
                            job.state = "completed";
                            job.text = text;
                        }
                        Err(error) => {
                            job.state = "failed";
                            job.error = Some(error);
                        }
                    }
                }
            }
            if inner
                .active
                .as_ref()
                .is_some_and(|(id, _)| id == &worker_id)
            {
                inner.active = None;
            }
        });
        inner.worker = Some(worker);
        Ok(json!({"id":id}))
    }
    pub fn job(&self, id: &str) -> Result<Value, String> {
        if !private::token_ok(id) {
            return Err("Unknown request".into());
        }
        let inner = self.inner.lock().map_err(|_| "Job state unavailable")?;
        inner
            .jobs
            .iter()
            .find(|job| job.id == id)
            .map(|job| serde_json::to_value(job).unwrap())
            .ok_or_else(|| "Unknown request".into())
    }
    pub fn cancel(&self, id: &str) -> Result<Value, String> {
        let mut inner = self.inner.lock().map_err(|_| "Job state unavailable")?;
        if !private::token_ok(id) || !inner.jobs.iter().any(|job| job.id == id) {
            return Err("Unknown request".into());
        }
        if let Some((active, cancelled)) = &inner.active {
            if active == id {
                cancelled.store(true, Ordering::Relaxed);
            }
        }
        let job = inner.jobs.iter_mut().find(|job| job.id == id).unwrap();
        // Keep running until the worker has actually killed/reaped and cleaned up.
        Ok(json!({"id":id,"state":job.state,"cancellation_requested":job.state=="running"}))
    }
    pub fn shutdown(&self) {
        let worker = {
            let mut inner = self.inner.lock().unwrap();
            if let Some((_, cancelled)) = &inner.active {
                cancelled.store(true, Ordering::Relaxed);
            }
            inner.worker.take()
        };
        if let Some(worker) = worker {
            let _ = worker.join();
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn restart_preserves_all_predecessor_history() {
        let scratch = private::Scratch::new();
        private::private_dir(&scratch.0.join("jobs")).unwrap();
        let owned = scratch
            .0
            .join("jobs")
            .join(private::random_token().unwrap());
        private::private_dir(&owned).unwrap();
        private::create(&owned.join("input.wav"), b"private fixture").unwrap();
        let unrelated = scratch.0.join("jobs/unrelated");
        private::private_dir(&unrelated).unwrap();
        let service = Service::new(&scratch.0, Arc::new(AtomicBool::new(false))).unwrap();
        assert!(owned.exists()); // Old held history is never deleted by new startup.
        assert!(unrelated.exists());
        assert!(service.job("../../file").is_err());
        service.shutdown();
    }
}
