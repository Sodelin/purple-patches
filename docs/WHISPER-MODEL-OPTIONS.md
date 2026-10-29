# Whisper model options for a shared patch

Research checked 2026-10-29. The current Gboard candidate and companion implement
only Nolan's explicitly selected full Whisper large-v3 F16 model. The choices
below are design candidates, not supported or tested settings in that build.
No model has been downloaded or executed as part of this research.

## Choices worth evaluating

| Choice | What it offers | Published file size | Important qualification |
| --- | --- | --- | --- |
| Whisper large-v3, F16 | Full multilingual model selected for Nolan | 2.9 GiB | File size is not total running memory; phone latency and battery use are unmeasured. |
| Whisper large-v3, q5 | The same model with lower-precision weights | 1.1 GiB | Quantization can affect accuracy and performance; do not describe it as identical output to F16. |
| Whisper large-v3 Turbo, F16 / q5 | Faster multilingual transcription candidate | 1.5 GiB / 547 MiB | Turbo is not trained for Whisper's speech-translation task. Sol text translation is a separate feature. |
| Distil-Whisper large-v3.5, GGML | English-focused distilled candidate | 1.52 GB (about 1.42 GiB) | Authors document whisper.cpp support, but our pinned engine and Android build have not been checked with these weights. |
| Small/base/tiny | Much smaller multilingual or English-only models | See model list | Accuracy depends on language, acoustics and model; not all smaller phones have identical constraints. |

Published sizes come from [whisper.cpp's model list](https://huggingface.co/ggerganov/whisper.cpp/blob/main/README.md)
and the [Distil-v3.5 GGML repository](https://huggingface.co/distil-whisper/distil-large-v3.5-ggml/tree/main).
They are storage sizes, not measured Android peak RAM or live speed. OpenAI's
[model documentation](https://github.com/openai/whisper) describes Turbo and the
English-only variants. The [Distil-v3.5 model card](https://huggingface.co/distil-whisper/distil-large-v3.5)
documents its English focus and whisper.cpp integration.

In the Distil authors' seven short-form English test sets, their reported average
normalized word error rate was 7.14% for large-v3 and 7.10% for distil-v3.5;
individual datasets differed. Their out-of-distribution long-form averages were
10.25% for Turbo and 11.39% for distil-v3.5. These benchmarks do not establish
accuracy or speed for a particular speaker, Android phone, engine, quantization,
microphone or noise environment. The authors also describe speculative decoding
with full large-v3 as producing identical output under their supported setup;
our engine does not yet establish that capability.

## Integration requirements

Keep Nolan's full-large-v3 choice unchanged. For a future shared release, offer
an explicit choice with language, estimated storage and accuracy/performance
tradeoffs; avoid silently switching or claiming a universal default before actual
device evidence. Each supported entry needs a pinned trusted source, exact bytes,
size, digest, format, language/task capability and compatible engine version.
Model selection must be reflected in versioned companion readiness and checked
before microphone use, with full integrity verification before inference. A
missing, corrupt or unsupported selection must fail clearly without falling back
to a different model. Keep model acquisition optional, user-initiated and subject
to actual free space and existing privacy/model holds.

Before advertising a choice, verify its engine compatibility and accuracy,
latency, memory, battery and cancellation behavior on representative authorized
devices. Keep the current Gboard full-v3 work and acceptance independent from
that future expansion; the model-testing and device-installation holds remain.
