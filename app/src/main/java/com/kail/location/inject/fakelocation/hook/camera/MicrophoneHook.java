package com.kail.location.inject.fakelocation.hook.camera;

import android.media.AudioRecord;
import com.kail.location.lib.lhooker.LHooker;
import com.kail.location.utils.KailLog;

import java.nio.ByteBuffer;

/**
 * Microphone replacement hooks (ported from CamSwap's MicrophoneHandler to
 * the LHooker static-triplet convention).
 *
 *  - AudioRecord.&lt;init&gt; captures the app's requested sample rate /
 *    channel config so the PCM source can match them.
 *  - Every read() variant is after-processed: after the real read fills the
 *    buffer with real mic data, we overwrite it with our substitution
 *    (silence / decoded PCM), keyed off {@link CameraHookMain#micActive()}.
 */
public final class MicrophoneHook {

    private static final String TAG = "MicrophoneHook";
    private static volatile boolean installed;

    private MicrophoneHook() {
    }

    public static synchronized void hook(ClassLoader cl) {
        if (installed) return;
        try {
            // AudioRecord(int audioSource, int sampleRateInHz, int channelConfig,
            //             int audioFormat, int bufferSizeInBytes)
            LHooker.hookConstructor(AudioRecord.class,
                    new Class[]{Integer.TYPE, Integer.TYPE, Integer.TYPE, Integer.TYPE, Integer.TYPE},
                    MicrophoneHook.class, "init", "init_bak", "init_copy");

            LHooker.hookMethodAutoBackup(AudioRecord.class, "read", Integer.TYPE,
                    new Class[]{byte[].class, Integer.TYPE, Integer.TYPE},
                    MicrophoneHook.class, "readBytes");
            try {
                LHooker.hookMethodAutoBackup(AudioRecord.class, "read", Integer.TYPE,
                        new Class[]{byte[].class, Integer.TYPE, Integer.TYPE, Integer.TYPE},
                        MicrophoneHook.class, "readBytesNonBlocking");
            } catch (Throwable ignored) { }
            LHooker.hookMethodAutoBackup(AudioRecord.class, "read", Integer.TYPE,
                    new Class[]{short[].class, Integer.TYPE, Integer.TYPE},
                    MicrophoneHook.class, "readShorts");
            try {
                LHooker.hookMethodAutoBackup(AudioRecord.class, "read", Integer.TYPE,
                        new Class[]{short[].class, Integer.TYPE, Integer.TYPE, Integer.TYPE},
                        MicrophoneHook.class, "readShortsNonBlocking");
            } catch (Throwable ignored) { }
            LHooker.hookMethodAutoBackup(AudioRecord.class, "read", Integer.TYPE,
                    new Class[]{ByteBuffer.class, Integer.TYPE},
                    MicrophoneHook.class, "readBuffer");
            try {
                LHooker.hookMethodAutoBackup(AudioRecord.class, "read", Integer.TYPE,
                        new Class[]{ByteBuffer.class, Integer.TYPE, Integer.TYPE},
                        MicrophoneHook.class, "readBufferNonBlocking");
            } catch (Throwable ignored) { }
            try {
                LHooker.hookMethodAutoBackup(AudioRecord.class, "read", Integer.TYPE,
                        new Class[]{float[].class, Integer.TYPE, Integer.TYPE, Integer.TYPE},
                        MicrophoneHook.class, "readFloats");
            } catch (Throwable ignored) { }

            installed = true;
            KailLog.INSTANCE.i(null, TAG, "Microphone hooks installed");
        } catch (Throwable th) {
            KailLog.INSTANCE.e(null, TAG, "hook install failed", th);
        }
    }

    // ---------------------------------------------------------------
    // Constructor — capture the requested format.
    // ---------------------------------------------------------------
    public static void init(Object receiver, int audioSource, int sampleRateInHz,
                            int channelConfig, int audioFormat, int bufferSizeInBytes) {
        try {
            MicAudioSource.get().onAudioRecordCreated(sampleRateInHz, channelConfig);
        } catch (Throwable ignored) { }
        init_bak(receiver, audioSource, sampleRateInHz, channelConfig, audioFormat, bufferSizeInBytes);
    }

    public static void init_bak(Object receiver, int audioSource, int sampleRateInHz,
                                int channelConfig, int audioFormat, int bufferSizeInBytes) {
        pad();
        init_copy(receiver, audioSource, sampleRateInHz, channelConfig, audioFormat, bufferSizeInBytes);
    }

    public static void init_copy(Object receiver, int audioSource, int sampleRateInHz,
                                 int channelConfig, int audioFormat, int bufferSizeInBytes) {
        pad();
    }

    // ---------------------------------------------------------------
    // read(byte[], int, int) [+ sizeInBytes variant]
    // ---------------------------------------------------------------
    public static int readBytes(Object receiver, byte[] audioData, int offsetInBytes, int sizeInBytes) {
        int r = readBytes_bak(receiver, audioData, offsetInBytes, sizeInBytes);
        if (CameraHookMain.micActive() && r > 0) {
            startMicFeed();
            MicAudioSource.get().fillBytes(audioData, offsetInBytes, sizeInBytes);
        }
        return r;
    }

    public static int readBytes_bak(Object receiver, byte[] audioData, int offsetInBytes, int sizeInBytes) {
        pad();
        return readBytes_copy(receiver, audioData, offsetInBytes, sizeInBytes);
    }

    public static int readBytes_copy(Object receiver, byte[] audioData, int offsetInBytes, int sizeInBytes) {
        pad();
        return 0;
    }

    public static int readBytesNonBlocking(Object receiver, byte[] audioData, int offsetInBytes,
                                           int sizeInBytes, int readOptions) {
        int r = readBytesNonBlocking_bak(receiver, audioData, offsetInBytes, sizeInBytes, readOptions);
        if (CameraHookMain.micActive() && r > 0) {
            startMicFeed();
            MicAudioSource.get().fillBytes(audioData, offsetInBytes, sizeInBytes);
        }
        return r;
    }

    public static int readBytesNonBlocking_bak(Object receiver, byte[] audioData, int offsetInBytes,
                                               int sizeInBytes, int readOptions) {
        pad();
        return readBytesNonBlocking_copy(receiver, audioData, offsetInBytes, sizeInBytes, readOptions);
    }

    public static int readBytesNonBlocking_copy(Object receiver, byte[] audioData, int offsetInBytes,
                                                int sizeInBytes, int readOptions) {
        pad();
        return 0;
    }

    // ---------------------------------------------------------------
    // read(short[], int, int) [+ variant]
    // ---------------------------------------------------------------
    public static int readShorts(Object receiver, short[] audioData, int offsetInShorts, int sizeInShorts) {
        int r = readShorts_bak(receiver, audioData, offsetInShorts, sizeInShorts);
        if (CameraHookMain.micActive() && r > 0) {
            startMicFeed();
            MicAudioSource.get().fillShorts(audioData, offsetInShorts, sizeInShorts);
        }
        return r;
    }

    public static int readShorts_bak(Object receiver, short[] audioData, int offsetInShorts, int sizeInShorts) {
        pad();
        return readShorts_copy(receiver, audioData, offsetInShorts, sizeInShorts);
    }

    public static int readShorts_copy(Object receiver, short[] audioData, int offsetInShorts, int sizeInShorts) {
        pad();
        return 0;
    }

    public static int readShortsNonBlocking(Object receiver, short[] audioData, int offsetInShorts,
                                            int sizeInShorts, int readOptions) {
        int r = readShortsNonBlocking_bak(receiver, audioData, offsetInShorts, sizeInShorts, readOptions);
        if (CameraHookMain.micActive() && r > 0) {
            startMicFeed();
            MicAudioSource.get().fillShorts(audioData, offsetInShorts, sizeInShorts);
        }
        return r;
    }

    public static int readShortsNonBlocking_bak(Object receiver, short[] audioData, int offsetInShorts,
                                                int sizeInShorts, int readOptions) {
        pad();
        return readShortsNonBlocking_copy(receiver, audioData, offsetInShorts, sizeInShorts, readOptions);
    }

    public static int readShortsNonBlocking_copy(Object receiver, short[] audioData, int offsetInShorts,
                                                 int sizeInShorts, int readOptions) {
        pad();
        return 0;
    }

    // ---------------------------------------------------------------
    // read(ByteBuffer, int) [+ variant] — buffer holds PCM16 bytes.
    // ---------------------------------------------------------------
    public static int readBuffer(Object receiver, ByteBuffer audioBuffer, int sizeInBytes) {
        int r = readBuffer_bak(receiver, audioBuffer, sizeInBytes);
        if (CameraHookMain.micActive() && r > 0 && audioBuffer != null) {
            startMicFeed();
            overwriteByteBuffer(audioBuffer, r);
        }
        return r;
    }

    public static int readBuffer_bak(Object receiver, ByteBuffer audioBuffer, int sizeInBytes) {
        pad();
        return readBuffer_copy(receiver, audioBuffer, sizeInBytes);
    }

    public static int readBuffer_copy(Object receiver, ByteBuffer audioBuffer, int sizeInBytes) {
        pad();
        return 0;
    }

    public static int readBufferNonBlocking(Object receiver, ByteBuffer audioBuffer,
                                            int sizeInBytes, int readOptions) {
        int r = readBufferNonBlocking_bak(receiver, audioBuffer, sizeInBytes, readOptions);
        if (CameraHookMain.micActive() && r > 0 && audioBuffer != null) {
            startMicFeed();
            overwriteByteBuffer(audioBuffer, r);
        }
        return r;
    }

    public static int readBufferNonBlocking_bak(Object receiver, ByteBuffer audioBuffer,
                                                int sizeInBytes, int readOptions) {
        pad();
        return readBufferNonBlocking_copy(receiver, audioBuffer, sizeInBytes, readOptions);
    }

    public static int readBufferNonBlocking_copy(Object receiver, ByteBuffer audioBuffer,
                                                 int sizeInBytes, int readOptions) {
        pad();
        return 0;
    }

    // ---------------------------------------------------------------
    // read(float[], int, int, int)
    // ---------------------------------------------------------------
    public static int readFloats(Object receiver, float[] audioData, int offsetInFloats,
                                 int sizeInFloats, int readOptions) {
        int r = readFloats_bak(receiver, audioData, offsetInFloats, sizeInFloats, readOptions);
        if (CameraHookMain.micActive() && r > 0) {
            startMicFeed();
            MicAudioSource.get().fillFloats(audioData, offsetInFloats, sizeInFloats);
        }
        return r;
    }

    public static int readFloats_bak(Object receiver, float[] audioData, int offsetInFloats,
                                     int sizeInFloats, int readOptions) {
        pad();
        return readFloats_copy(receiver, audioData, offsetInFloats, sizeInFloats, readOptions);
    }

    public static int readFloats_copy(Object receiver, float[] audioData, int offsetInFloats,
                                      int sizeInFloats, int readOptions) {
        pad();
        return 0;
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------
    /** Starts the PCM decoder for the active mic mode (idempotent). */
    private static void startMicFeed() {
        try {
            String mode = CameraHookMain.micMode();
            if ("replace".equals(mode)) {
                MicAudioSource.get().ensureDecoding(CameraHookMain.micAudioPath());
            } else if ("video_sync".equals(mode)) {
                MicAudioSource.get().ensureDecoding(CameraHookMain.videoPath());
            }
        } catch (Throwable ignored) { }
    }

    private static void overwriteByteBuffer(ByteBuffer buf, int bytesRead) {
        try {
            byte[] tmp = new byte[bytesRead];
            MicAudioSource.get().fillBytes(tmp, 0, bytesRead);
            int pos = buf.position();
            // The real read already advanced position by bytesRead.
            int start = pos - bytesRead;
            if (start < 0) start = 0;
            ByteBuffer dup = buf.duplicate();
            dup.position(start);
            dup.put(tmp);
        } catch (Throwable ignored) { }
    }

    static void pad() {
        try {
            StringBuffer sb = new StringBuffer();
            sb.append('#'); sb.append('#'); sb.append('#'); sb.append('#');
            sb.append('#'); sb.append('#'); sb.append('#'); sb.toString();
            for (int i = 0; i < 100; i = i + 1 + 1) { }
            for (int i = 0; i < 100; i = i + 1 + 1) { }
        } catch (Throwable ignored) { }
    }
}
