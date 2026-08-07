package com.kail.location.inject.fakelocation.hook.camera;

import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;

import com.kail.location.utils.KailLog;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * PCM provider for the microphone hooks.
 *
 * Modes (from camera_config.json mic_mode):
 *  - "mute":       fill buffers with silence
 *  - "replace":    decode mic_audio file (mp3/aac/…) to PCM16, loop
 *  - "video_sync": decode the audio track of the current session video, loop
 *
 * Decoding happens on a background thread into a ring buffer; read hooks copy
 * from it. Non-PCM16 read targets are handled per-hook (shorts / float).
 */
public final class MicAudioSource {

    private static final String TAG = "MicAudioSrc";
    private static final long TIMEOUT_US = 10_000L;
    private static final int RING_CAPACITY = 1 << 21; // 2 MB PCM ring

    private static volatile MicAudioSource instance;

    private final Object lock = new Object();
    private final byte[] ring = new byte[RING_CAPACITY];
    private int ringHead;   // write position
    private int ringTail;   // read position
    private int ringSize;

    private final AtomicBoolean decoding = new AtomicBoolean(false);
    private Thread decodeThread;
    private volatile String activeSourcePath = "";

    // Active AudioRecord capture params (from the hooked constructor).
    private volatile int sampleRate = 44100;
    private volatile int channelCount = 1;

    private MicAudioSource() {
    }

    public static MicAudioSource get() {
        MicAudioSource s = instance;
        if (s == null) {
            synchronized (MicAudioSource.class) {
                s = instance;
                if (s == null) {
                    s = new MicAudioSource();
                    instance = s;
                }
            }
        }
        return s;
    }

    /** Called from the AudioRecord constructor hook. */
    public void onAudioRecordCreated(int rate, int channelConfig) {
        sampleRate = rate > 0 ? rate : 44100;
        channelCount = (channelConfig == AudioFormat.CHANNEL_IN_MONO) ? 1 : 2;
    }

    /** Ensure the decoder is running for the given source file (may be ""). */
    public void ensureDecoding(String sourcePath) {
        if (sourcePath == null || sourcePath.isEmpty() || !new File(sourcePath).exists()) {
            return;
        }
        if (sourcePath.equals(activeSourcePath) && decoding.get()) {
            return;
        }
        synchronized (lock) {
            ringHead = ringTail = ringSize = 0;
        }
        activeSourcePath = sourcePath;
        decoding.set(true);
        decodeThread = new Thread(() -> decodeLoop(sourcePath), "kail-mic-decode");
        decodeThread.start();
    }

    public void stop() {
        decoding.set(false);
        activeSourcePath = "";
    }

    // ---------------------------------------------------------------
    // Buffer fill entry points used by MicrophoneHook
    // ---------------------------------------------------------------
    public void fillBytes(byte[] buf, int off, int size) {
        if ("mute".equals(currentMode())) {
            Arrays.fill(buf, off, off + size, (byte) 0);
            return;
        }
        pumpRingInto(buf, off, size);
    }

    public void fillShorts(short[] buf, int off, int size) {
        if ("mute".equals(currentMode())) {
            Arrays.fill(buf, off, off + size, (short) 0);
            return;
        }
        byte[] tmp = new byte[size * 2];
        pumpRingInto(tmp, 0, tmp.length);
        for (int i = 0; i < size; i++) {
            buf[off + i] = (short) ((tmp[i * 2] & 0xFF) | (tmp[i * 2 + 1] << 8));
        }
    }

    public void fillFloats(float[] buf, int off, int size) {
        // Float capture: synthesize from PCM16 ring normalized to [-1,1].
        if ("mute".equals(currentMode())) {
            Arrays.fill(buf, off, off + size, 0f);
            return;
        }
        byte[] tmp = new byte[size * 2];
        pumpRingInto(tmp, 0, tmp.length);
        for (int i = 0; i < size; i++) {
            short s = (short) ((tmp[i * 2] & 0xFF) | (tmp[i * 2 + 1] << 8));
            buf[off + i] = s / 32768f;
        }
    }

    private String currentMode() {
        try {
            return CameraHookMain.micMode();
        } catch (Throwable th) {
            return "mute";
        }
    }

    private void pumpRingInto(byte[] dst, int off, int size) {
        synchronized (lock) {
            int avail = ringSize;
            int n = Math.min(size, avail);
            for (int i = 0; i < n; i++) {
                dst[off + i] = ring[(ringTail + i) % RING_CAPACITY];
            }
            ringTail = (ringTail + n) % RING_CAPACITY;
            ringSize -= n;
            // Pad the remainder with silence when underrun.
            if (n < size) {
                Arrays.fill(dst, off + n, off + size, (byte) 0);
            }
        }
    }

    private void ringWrite(byte[] data, int len) {
        synchronized (lock) {
            // Drop oldest on overflow.
            if (ringSize + len > RING_CAPACITY) {
                int drop = ringSize + len - RING_CAPACITY;
                ringTail = (ringTail + drop) % RING_CAPACITY;
                ringSize -= drop;
            }
            for (int i = 0; i < len; i++) {
                ring[ringHead] = data[i];
                ringHead = (ringHead + 1) % RING_CAPACITY;
            }
            ringSize += len;
        }
    }

    // ---------------------------------------------------------------
    // Decode loop: file (mp3/mp4 audio track) → PCM16 ring, looping.
    // ---------------------------------------------------------------
    private void decodeLoop(String path) {
        while (decoding.get()) {
            MediaExtractor extractor = null;
            MediaCodec codec = null;
            try {
                extractor = new MediaExtractor();
                extractor.setDataSource(path);
                int track = -1;
                MediaFormat format = null;
                for (int i = 0; i < extractor.getTrackCount(); i++) {
                    MediaFormat f = extractor.getTrackFormat(i);
                    String mime = f.getString(MediaFormat.KEY_MIME);
                    if (mime != null && mime.startsWith("audio/")) {
                        track = i;
                        format = f;
                        break;
                    }
                }
                if (track < 0 || format == null) {
                    KailLog.INSTANCE.w(null, TAG, "no audio track in " + path);
                    sleepQuiet(2000);
                    continue;
                }
                extractor.selectTrack(track);
                codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));
                codec.configure(format, null, null, 0);
                codec.start();

                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                boolean inputDone = false;
                while (decoding.get()) {
                    if (!inputDone) {
                        int inIdx = codec.dequeueInputBuffer(TIMEOUT_US);
                        if (inIdx >= 0) {
                            ByteBuffer buf = codec.getInputBuffer(inIdx);
                            int size = extractor.readSampleData(buf, 0);
                            if (size < 0) {
                                codec.queueInputBuffer(inIdx, 0, 0, 0,
                                        MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                inputDone = true;
                            } else {
                                codec.queueInputBuffer(inIdx, 0, size,
                                        extractor.getSampleTime(), 0);
                                extractor.advance();
                            }
                        }
                    }
                    int outIdx = codec.dequeueOutputBuffer(info, TIMEOUT_US);
                    if (outIdx >= 0) {
                        ByteBuffer out = codec.getOutputBuffer(outIdx);
                        if (out != null && info.size > 0) {
                            byte[] chunk = new byte[info.size];
                            out.position(info.offset);
                            out.get(chunk);
                            ringWrite(chunk, chunk.length);
                        }
                        codec.releaseOutputBuffer(outIdx, false);
                        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            break; // loop the file
                        }
                    }
                }
            } catch (Throwable th) {
                KailLog.INSTANCE.e(null, TAG, "decodeLoop", th);
                sleepQuiet(1000);
            } finally {
                if (codec != null) {
                    try { codec.stop(); } catch (Throwable ignored) { }
                    try { codec.release(); } catch (Throwable ignored) { }
                }
                if (extractor != null) {
                    try { extractor.release(); } catch (Throwable ignored) { }
                }
            }
        }
    }

    private static void sleepQuiet(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) { }
    }
}
