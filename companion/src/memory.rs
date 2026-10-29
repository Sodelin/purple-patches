//! Bounded anonymous-memory inputs and owned process-pipe capture. No payload files.
use std::io::{Read, Write};
use std::os::fd::AsRawFd;
use std::os::unix::process::CommandExt;
use std::process::{Command, Stdio};
use std::sync::atomic::{AtomicBool, Ordering};
use std::thread;
use std::time::{Duration, Instant};

fn nonblock(fd:i32)->Result<(),String> {
    let flags=unsafe {libc::fcntl(fd,libc::F_GETFL)};
    if flags<0 || unsafe {libc::fcntl(fd,libc::F_SETFL,flags|libc::O_NONBLOCK)}<0 {Err("Pipe setup failed".into())} else {Ok(())}
}
fn drain(reader:&mut impl Read, target:&mut Vec<u8>, cap:usize)->Result<(),String> {
    loop {
        let mut chunk=[0u8;4096];
        match reader.read(&mut chunk) {
            Ok(0)=>return Ok(()),
            Ok(n)=>{if target.len()+n>cap{return Err("Engine pipe output exceeded its bound".into())}target.extend_from_slice(&chunk[..n]);},
            Err(e) if e.kind()==std::io::ErrorKind::WouldBlock=>return Ok(()),
            Err(e) if e.kind()==std::io::ErrorKind::Interrupted=>continue,
            Err(_)=>return Err("Engine pipe read failed".into()),
        }
    }
}
struct Owned(std::process::Child,bool);
impl Drop for Owned {
    fn drop(&mut self){if !self.1{return} unsafe {libc::kill(-(self.0.id() as i32),libc::SIGKILL);} let _=self.0.kill();let _=self.0.wait();}
}
pub fn run(command:&mut Command,input:&[u8],cancelled:&AtomicBool,cap:usize)->Result<Vec<u8>,String> {
    run_with_progress(command,input,cancelled,cap,|_,_|{})
}
// The no-op production observer permits deterministic supervision fixtures
// to wait for an owned child at a precise input-write boundary.
fn run_with_progress<F: FnMut(usize,u32)>(command:&mut Command,input:&[u8],cancelled:&AtomicBool,cap:usize,mut progress:F)->Result<Vec<u8>,String> {
    if cancelled.load(Ordering::Relaxed){return Err("cancelled".into())}
    command.stdin(Stdio::piped()).stdout(Stdio::piped()).stderr(Stdio::piped()).process_group(0);
    let parent=unsafe {libc::getpid()};
    unsafe {command.pre_exec(move||{
        if libc::prctl(libc::PR_SET_PDEATHSIG,libc::SIGKILL)!=0 || libc::getppid()!=parent {return Err(std::io::Error::other("Supervisor changed"));}
        let limit=libc::rlimit{rlim_cur:0,rlim_max:0};
        if libc::setrlimit(libc::RLIMIT_CORE,&limit)!=0{return Err(std::io::Error::last_os_error())}Ok(())
    });}
    let mut owned=Owned(command.spawn().map_err(|_|"Owned engine unavailable")?,true);
    let pid=owned.0.id();
    let mut stdin=owned.0.stdin.take();let mut stdout=owned.0.stdout.take().ok_or("Missing output pipe")?;
    let mut stderr=owned.0.stderr.take().ok_or("Missing diagnostic pipe")?;
    nonblock(stdout.as_raw_fd())?;nonblock(stderr.as_raw_fd())?;
    if let Some(ref stream)=stdin {nonblock(stream.as_raw_fd())?;}
    let mut sent=0;let mut output=Vec::new();let mut diagnostic=Vec::new();let started=Instant::now();
    loop {
        if let Some(ref mut writer)=stdin {
            match writer.write(&input[sent..]) {
                Ok(n)=>{sent+=n;if n>0{progress(sent,pid);}},
                Err(e) if e.kind()==std::io::ErrorKind::WouldBlock=>{},
                Err(e) if e.kind()==std::io::ErrorKind::Interrupted=>{},
                Err(_)=>{return Err("Engine input was not accepted".into())}
            }
            if sent==input.len(){stdin.take();}
        }
        drain(&mut stdout,&mut output,cap)?;drain(&mut stderr,&mut diagnostic,65536)?;
        if cancelled.load(Ordering::Relaxed){return Err("cancelled".into())}
        if started.elapsed()>Duration::from_secs(120){return Err("Engine request deadline exceeded".into())}
        let mut info:libc::siginfo_t=unsafe {std::mem::zeroed()};
        let result=unsafe {libc::waitid(libc::P_PID,pid, &mut info,libc::WEXITED|libc::WNOHANG|libc::WNOWAIT)};
        if result==0 && unsafe {info.si_pid()}!=0 {
            // Signal descendants while direct child remains waitable, preventing PID reuse.
            unsafe {libc::kill(-(pid as i32),libc::SIGKILL);}
            drain(&mut stdout,&mut output,cap)?;drain(&mut stderr,&mut diagnostic,65536)?;
            let status=owned.0.wait().map_err(|_|"Engine reap failed")?;
            // Do not let Drop signal a reaped/reused PID.
            owned.1=false;
            if !status.success(){return Err("Engine did not complete successfully".into())}
            // A successful exit cannot make a partial input write successful.
            // A completed pipe write still does not prove application-level consumption.
            if sent!=input.len(){return Err("Engine exited before all input was sent".into())}
            return Ok(output);
        }
        if result!=0 && std::io::Error::last_os_error().kind()!=std::io::ErrorKind::Interrupted{return Err("Owned supervision failed".into())}
        thread::sleep(Duration::from_millis(10));
    }
}
#[cfg(test)]mod tests{
 use super::*;
 #[test]fn pipe_input_roundtrip(){let mut c=Command::new("/bin/cat");assert_eq!(run(&mut c,b"public fixture",&AtomicBool::new(false),64).unwrap(),b"public fixture");}
 #[test]fn pipe_requests_do_not_reuse_input(){let mut first=Command::new("/bin/cat");let bytes=vec![b'x';2*1024*1024];assert_eq!(run(&mut first,&bytes,&AtomicBool::new(false),bytes.len()).unwrap(),bytes);let mut second=Command::new("/bin/cat");assert!(run(&mut second,b"",&AtomicBool::new(false),64).unwrap().is_empty());}
 #[test]fn failed_child_is_not_success(){let mut c=Command::new("/bin/sh");c.args(["-c","printf partial; exit 1"]);assert!(run(&mut c,b"",&AtomicBool::new(false),64).is_err());}
 #[test]fn output_overflow_refuses(){let mut c=Command::new("/bin/sh");c.args(["-c","printf 12345678"]);assert!(run(&mut c,b"",&AtomicBool::new(false),3).is_err());}
 #[test]fn cancellation_prevents_spawn(){let mut c=Command::new("/no/such/file");assert_eq!(run(&mut c,b"",&AtomicBool::new(true),64).unwrap_err(),"cancelled");}
 #[test]fn successful_child_exit_before_full_input_is_not_success(){
  let mut c=Command::new("/bin/sh");c.args(["-c","IFS= read -r line; printf partial"]);
  let input=vec![b'\n';4*1024*1024];let mut observed=false;
  let result=run_with_progress(&mut c,&input,&AtomicBool::new(false),64,|sent,pid|{
   if observed{return}
   assert!(sent<input.len(),"fixture requires a partial first pipe write");
   let deadline=Instant::now()+Duration::from_secs(5);
   loop{
    let mut info:libc::siginfo_t=unsafe{std::mem::zeroed()};
    let status=unsafe{libc::waitid(libc::P_PID,pid,&mut info,libc::WEXITED|libc::WNOHANG|libc::WNOWAIT)};
    assert_eq!(status,0,"owned child status unavailable");
    if unsafe{info.si_pid()}!=0{break}
    assert!(Instant::now()<deadline,"fixture child did not exit");
    thread::sleep(Duration::from_millis(5));
   }
   observed=true;
  });
  assert!(observed);
  if result.is_ok(){
   let _=std::io::stderr().write_all(b"PUBLIC_EARLY_EXIT_REPRO: successful child accepted with unsent input\n");
  }
  assert_eq!(result.unwrap_err(),"Engine exited before all input was sent");
 }
}
