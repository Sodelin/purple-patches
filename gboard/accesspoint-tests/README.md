# Access-point integration controls

The hosted Gboard build materializes the pinned upstream source and overlay,
then compiles the actual `PurpleAccessPoints` and materialized upstream
`GboardAccessPointContributionsRuntime` against the small synthetic host types
in this directory. The checks cover stock entries, disabled and enabled actions,
the two callbacks, repeated extension, and a simulated optional dependency
linkage failure both before catalog creation and during registration. They
verify that the upstream editing contribution still runs in those cases.

Before the source correction, [hosted run 40968777447](https://github.com/Sodelin/purple-patches/actions/runs/40968777447)
compiled and materialized the pinned sources, then failed when the synthetic
optional dependency raised `NoClassDefFoundError` through the catalog hook.
That is a controlled source reproduction, not an observed phone failure.

The host types are controlled stubs. These tests do not load an APK, establish
that the real obfuscated classes have the stub behavior, prove atomic optional
registration, or replace Android stock-Gboard regression and rollback tests.
They do not execute a microphone, model, Termux service, or user account.
