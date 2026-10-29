#include <FLAC/stream_decoder.h>
#include <jni.h>
#include <limits.h>
#include <stdbool.h>
#include <stdint.h>
#include <string.h>

/* A synchronous JNI/libFLAC bridge. No path, log, key, network or persistent
 * record is created. The caller owns the original Java stream and stop check. */
typedef struct {
    JNIEnv *env;
    jobject reader;
    jmethodID read_method, check_method;
    int failed, eof, info_seen, checksum_present;
    uint32_t rate, channels, bits;
    uint64_t expected_samples, samples;
} Context;

static int current(Context *ctx) {
    (*ctx->env)->CallVoidMethod(ctx->env, ctx->reader, ctx->check_method);
    if ((*ctx->env)->ExceptionCheck(ctx->env)) { ctx->failed = 1; return 0; }
    return 1;
}

static FLAC__StreamDecoderReadStatus read_data(const FLAC__StreamDecoder *decoder,
        FLAC__byte buffer[], size_t *bytes, void *data) {
    (void) decoder;
    Context *ctx = data;
    if (ctx->failed || *bytes == 0) { ctx->failed = 1; return FLAC__STREAM_DECODER_READ_STATUS_ABORT; }
    size_t want = *bytes < 65536u ? *bytes : 65536u;
    jbyteArray chunk = (*ctx->env)->NewByteArray(ctx->env, (jsize) want);
    if (chunk == NULL) { ctx->failed = 1; return FLAC__STREAM_DECODER_READ_STATUS_ABORT; }
    jint n = (*ctx->env)->CallIntMethod(ctx->env, ctx->reader, ctx->read_method, chunk);
    if ((*ctx->env)->ExceptionCheck(ctx->env)) {
        ctx->failed = 1;
        (*ctx->env)->DeleteLocalRef(ctx->env, chunk);
        return FLAC__STREAM_DECODER_READ_STATUS_ABORT;
    }
    if (n == -1) {
        ctx->eof = 1; *bytes = 0;
        (*ctx->env)->DeleteLocalRef(ctx->env, chunk);
        return FLAC__STREAM_DECODER_READ_STATUS_END_OF_STREAM;
    }
    if (n <= 0 || (size_t) n > want) {
        ctx->failed = 1;
        (*ctx->env)->DeleteLocalRef(ctx->env, chunk);
        return FLAC__STREAM_DECODER_READ_STATUS_ABORT;
    }
    (*ctx->env)->GetByteArrayRegion(ctx->env, chunk, 0, n, (jbyte *) buffer);
    (*ctx->env)->DeleteLocalRef(ctx->env, chunk);
    if ((*ctx->env)->ExceptionCheck(ctx->env)) { ctx->failed = 1; return FLAC__STREAM_DECODER_READ_STATUS_ABORT; }
    *bytes = (size_t) n;
    return FLAC__STREAM_DECODER_READ_STATUS_CONTINUE;
}

static void metadata(const FLAC__StreamDecoder *decoder,
        const FLAC__StreamMetadata *metadata, void *data) {
    (void) decoder;
    Context *ctx = data;
    if (metadata->type != FLAC__METADATA_TYPE_STREAMINFO) return;
    if (ctx->info_seen) { ctx->failed = 1; return; }
    ctx->info_seen = 1;
    const FLAC__StreamMetadata_StreamInfo *info = &metadata->data.stream_info;
    ctx->rate = info->sample_rate; ctx->channels = info->channels;
    ctx->bits = info->bits_per_sample; ctx->expected_samples = info->total_samples;
    for (size_t i = 0; i < sizeof(info->md5sum); i++)
        if (info->md5sum[i] != 0) ctx->checksum_present = 1;
    if (ctx->rate == 0 || ctx->channels < 1 || ctx->channels > 8 ||
            ctx->bits < 4 || ctx->bits > 32) ctx->failed = 1;
}

static FLAC__StreamDecoderWriteStatus write_audio(const FLAC__StreamDecoder *decoder,
        const FLAC__Frame *frame, const FLAC__int32 *const samples[], void *data) {
    (void) decoder;
    Context *ctx = data;
    if (ctx->failed || !ctx->info_seen || !current(ctx) || frame == NULL || samples == NULL ||
            frame->header.channels != ctx->channels || frame->header.sample_rate != ctx->rate ||
            frame->header.bits_per_sample != ctx->bits || frame->header.blocksize == 0 ||
            frame->header.number_type != FLAC__FRAME_NUMBER_TYPE_SAMPLE_NUMBER ||
            frame->header.number.sample_number != ctx->samples ||
            frame->header.blocksize > (uint64_t) INT64_MAX - ctx->samples) {
        ctx->failed = 1; return FLAC__STREAM_DECODER_WRITE_STATUS_ABORT;
    }
    for (uint32_t i = 0; i < ctx->channels; i++)
        if (samples[i] == NULL) { ctx->failed = 1; return FLAC__STREAM_DECODER_WRITE_STATUS_ABORT; }
    ctx->samples += frame->header.blocksize;
    if ((ctx->expected_samples != 0 && ctx->samples > ctx->expected_samples) || !current(ctx)) {
        ctx->failed = 1; return FLAC__STREAM_DECODER_WRITE_STATUS_ABORT;
    }
    return FLAC__STREAM_DECODER_WRITE_STATUS_CONTINUE;
}

static void decoder_error(const FLAC__StreamDecoder *decoder,
        FLAC__StreamDecoderErrorStatus status, void *data) {
    (void) decoder; (void) status;
    ((Context *) data)->failed = 1;
}

JNIEXPORT jlongArray JNICALL
Java_dev_purple_patches_natune_FlacIntegrity_decode(JNIEnv *env, jclass type, jobject reader) {
    (void) type;
    Context ctx = {0}; ctx.env = env; ctx.reader = reader;
    jclass cls = (*env)->GetObjectClass(env, reader);
    if (cls == NULL) return NULL;
    ctx.read_method = (*env)->GetMethodID(env, cls, "read", "([B)I");
    if (ctx.read_method == NULL) { (*env)->DeleteLocalRef(env, cls); return NULL; }
    ctx.check_method = (*env)->GetMethodID(env, cls, "check", "()V");
    (*env)->DeleteLocalRef(env, cls);
    if (ctx.read_method == NULL || ctx.check_method == NULL) return NULL;
    FLAC__StreamDecoder *decoder = FLAC__stream_decoder_new();
    int initialized = 0, success = 0;
    if (decoder != NULL && FLAC__stream_decoder_set_md5_checking(decoder, true) &&
            FLAC__stream_decoder_init_stream(decoder, read_data, NULL, NULL, NULL, NULL,
                write_audio, metadata, decoder_error, &ctx) == FLAC__STREAM_DECODER_INIT_STATUS_OK) {
        initialized = 1;
        FLAC__bool processed = FLAC__stream_decoder_process_until_end_of_stream(decoder);
        FLAC__StreamDecoderState state = FLAC__stream_decoder_get_state(decoder);
        FLAC__bool finished = FLAC__stream_decoder_finish(decoder);
        initialized = 0;
        success = processed && finished && state == FLAC__STREAM_DECODER_END_OF_STREAM &&
            ctx.eof && !ctx.failed && ctx.info_seen && ctx.samples > 0 &&
            (ctx.expected_samples == 0 || ctx.samples == ctx.expected_samples);
    }
    if (decoder != NULL) {
        if (initialized) (void) FLAC__stream_decoder_finish(decoder);
        FLAC__stream_decoder_delete(decoder);
    }
    if (!success || (*env)->ExceptionCheck(env)) {
        if (!(*env)->ExceptionCheck(env)) {
            jclass error = (*env)->FindClass(env, "java/io/IOException");
            if (error != NULL) { (*env)->ThrowNew(env, error, "Complete FLAC validation failed"); (*env)->DeleteLocalRef(env, error); }
        }
        return NULL;
    }
    jlong values[6] = {ctx.checksum_present, ctx.expected_samples != 0, (jlong) ctx.samples,
        (jlong) ctx.rate, (jlong) ctx.channels, (jlong) ctx.bits};
    jlongArray result = (*env)->NewLongArray(env, 6);
    if (result != NULL) (*env)->SetLongArrayRegion(env, result, 0, 6, values);
    return result;
}
