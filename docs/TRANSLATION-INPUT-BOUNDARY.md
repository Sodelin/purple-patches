# Selected text in the translation request

The keyboard's translation action sends the user's selected text in the
companion's `prompt` field and sends an empty `context`. Before companion
`0.2.2-dev.7`, the CLI input serializer placed that selected text under the
JSON key `request`, while its common developer instruction described only
`context` as quoted data. A selected sentence that itself contained a question
or command was therefore not explicitly identified as the text to translate.
This is a source-level mismatch, not an observed model response or incident.

The new source serializes the exact selected text as `source_text`, together
with the fixed `translate` operation and selected target language. A fixed
developer instruction asks the CLI to translate commands and questions as
text rather than perform or answer them. Nonempty extra context, a missing or
invalid target, and selected text over the existing 8 KiB limit are refused.
Other companion modes keep their previous payload shape. The selected text is
still sent to the CLI through standard input, not as a shell command or CLI
argument.

The public controls exercise exact JSON round trips for ordinary, multiline,
Unicode and instruction-like selections, boundary conditions, target validation,
and the absence of selected text from command arguments. They establish the
source and serialization behavior, not the model's translation quality or its
resistance to prompt injection. Prompt wording is not a no-tools guarantee.
The independent CLI tool-catalogue, privacy-writer, account, cancellation,
device and installation gates remain open; Codex may send submitted text to
the configured service. No model request or phone action is needed for these
source controls.
