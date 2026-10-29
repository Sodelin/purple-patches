# Engine input completion

The keyboard companion sends the selected audio or translation request to an
owned child through a nonblocking stdin pipe. In source through `0.2.2-dev.5`,
the supervisor could observe the child exit successfully while only a prefix
had been written, reap it, and return the child's output as a successful result.
This was a source-level and synthetic-process finding, not an observed user
recording or model result.

The public synthetic fixture in `companion/src/memory.rs` writes a large input
whose first byte ends a shell `read`; it waits for that owned shell to exit
before the supervisor's exit check. On source `f2b5a72bea25658600846bb463191b0a47e382af`,
hosted run [40937199067](https://github.com/Sodelin/purple-patches/actions/runs/40937199067)
printed `PUBLIC_EARLY_EXIT_REPRO` and failed the new control. The earlier run
40937035071 also failed but redirected the individual test details, so it does
not by itself identify the failing assertion. No model or private input was used.

Source `0.2.2-dev.6` rejects an otherwise successful child exit when the parent
has not written the complete supplied input to the pipe. It does not claim that
the child actually consumed or interpreted every byte after the pipe accepted it;
that depends on the selected engine's own input protocol and remains a separate
runtime acceptance requirement. A failure does not replay the job. Existing
job-start uncertainty, cancellation, bootstrap, privacy, model and phone holds
remain unchanged.

Hosted run [40937358263](https://github.com/Sodelin/purple-patches/actions/runs/40937358263)
passed 45 public library controls, including the early-exit refusal and normal
pipe round trips, and cross-built both Android ARM64 executables. The seven
artifact members and all 13 recorded source hashes were independently checked
in memory against source `b151ab657890aef10b21bf664ebf5b16904f4e1e`; the two
deployment files also matched that source. See
[companion-022-dev6-verification.json](companion-022-dev6-verification.json).
The ELF machine headers were inspected, not executed. No Android engine,
microphone, Codex model, app or installed keyboard was exercised.
