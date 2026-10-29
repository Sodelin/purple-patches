# Codex translation: execution boundary

The current companion launches the installed Codex CLI for translation with
`--ephemeral`, a read-only sandbox selection, several disabled tool features,
and instructions telling the model not to use tools. These are useful controls,
but the source has not established an empty model-tool catalogue or complete
protection of Codex's own writers. In particular, a prompt instruction is not
an enforcement boundary, and disabling named features is not proof that every
native, hosted or dynamic tool is unavailable.

The installed CLI's help describes `--sandbox` as a policy for model-generated
shell commands. A bounded local check of its separate `codex sandbox` command
returned that sandboxing is unsupported on this Android operating system.
That observation does **not** establish that `codex exec --sandbox read-only`
will fail before a translation, nor does it establish the actual tool catalogue
of such a translation. The retained upstream source for the same CLI version
also selects its Linux sandbox only when Rust's target OS is `linux`; source
correspondence to the installed binary remains unverified. No model or account
request was made in these checks.

A separate, owner-retained Android Codex candidate was built with a configurable
startup policy intended to deny all model tools. It is not the executable that
the Gboard companion currently selects. Its historical evidence does not show
an observed empty tool catalogue for this Gboard request, authenticated
compiler-input custody, or complete writer protection. Merely pointing the
companion at that binary, adding a configuration string to the stock CLI, or
relying on the read-only flag would not establish the desired boundary.

Before a live translation, select and review an actual supported execution
profile and its owner/custody, prove the startup tool policy for the exact
selected executable and arguments, and separately resolve the existing
privacy-writer, account, cancellation and Android acceptance gates. Reuse the
retained candidate if it is suitable; do not replace the user's installed CLI
or silently add a paid API route. Existing model and device holds remain.
