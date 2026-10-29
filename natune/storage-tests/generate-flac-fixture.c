#include <FLAC/stream_encoder.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdlib.h>

/* Public synthetic samples only; the hosted workflow owns the output path. */
int main(int argc, char **argv) {
    if (argc != 3) return 2;
    char *end = NULL;
    long bits = strtol(argv[1], &end, 10);
    if (end == argv[1] || *end != '\0' || bits < 4 || bits > 32) return 2;
    FLAC__int32 pcm[16000];
    uint64_t mask = (UINT64_C(1) << bits) - 1;
    int64_t half = INT64_C(1) << (bits - 1);
    for (uint64_t frame = 0; frame < 8000; frame++) {
        for (uint64_t channel = 0; channel < 2; channel++) {
            int64_t sample = (int64_t) ((frame * UINT64_C(104729) + channel * UINT64_C(7919)) & mask) - half;
            pcm[frame * 2 + channel] = (FLAC__int32) sample;
        }
    }
    FLAC__StreamEncoder *encoder = FLAC__stream_encoder_new();
    if (encoder == NULL) return 1;
    int ok = FLAC__stream_encoder_set_channels(encoder, 2) &&
        FLAC__stream_encoder_set_bits_per_sample(encoder, (uint32_t) bits) &&
        FLAC__stream_encoder_set_sample_rate(encoder, 8000) &&
        FLAC__stream_encoder_set_streamable_subset(encoder, false) &&
        FLAC__stream_encoder_set_total_samples_estimate(encoder, 8000) &&
        FLAC__stream_encoder_set_verify(encoder, true) &&
        FLAC__stream_encoder_init_file(encoder, argv[2], NULL, NULL) == FLAC__STREAM_ENCODER_INIT_STATUS_OK;
    if (ok) ok = FLAC__stream_encoder_process_interleaved(encoder, pcm, 8000) &&
        FLAC__stream_encoder_finish(encoder);
    FLAC__stream_encoder_delete(encoder);
    return ok ? 0 : 1;
}
