package com.kail.location.inject.fakelocation.hook.camera;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.Image;

import com.kail.location.utils.KailLog;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Background thread that decodes the configured video into NV21 frames with
 * MediaCodec (no Surface) and exposes the latest frame via {@link #latestFrame()}.
 *
 * Ported from CamSwap's VideoToFrames (decode → YUV420Flexible Image → NV21),
 * plus crop-and-scale to the exact preview size the target app requested.
 * Frames loop while the video plays; the decoder restarts on EOS.
 */
public final class CameraFrameSource {

    private static final String TAG = "CameraFrameSrc";
    private static final long TIMEOUT_US = 10_000L;

    private static volatile CameraFrameSource instance;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread thread;

    private volatile byte[] nv21;
    private volatile int frameWidth;
    private volatile int frameHeight;
    private volatile int videoRotation; // metadata rotation, degrees

    // Requested output size (from the app's preview/callback size).
    private volatile int targetWidth;
    private volatile int targetHeight;

    private volatile String videoPath = "";
    private volatile int extraRotation; // user rotation offset, degrees

    private CameraFrameSource() {
    }

    public static CameraFrameSource get() {
        CameraFrameSource s = instance;
        if (s == null) {
            synchronized (CameraFrameSource.class) {
                s = instance;
                if (s == null) {
                    s = new CameraFrameSource();
                    instance = s;
                }
            }
        }
        return s;
    }

    public synchronized void configure(String path, int rotOffset) {
        if (path == null || path.isEmpty()) return;
        if (!path.equals(videoPath) || rotOffset != extraRotation) {
            videoPath = path;
            extraRotation = ((rotOffset % 360) + 360) % 360;
            restart();
        }
    }

    public synchronized void setTargetSize(int w, int h) {
        if (w > 0 && h > 0 && (w != targetWidth || h != targetHeight)) {
            targetWidth = w;
            targetHeight = h;
        }
    }

    public int targetWidth() { return targetWidth; }
    public int targetHeight() { return targetHeight; }

    public synchronized void start() {
        if (running.get() || videoPath.isEmpty()) return;
        running.set(true);
        thread = new Thread(this::decodeLoop, "kail-cam-decode");
        thread.start();
    }

    public synchronized void restart() {
        stop();
        if (!videoPath.isEmpty()) start();
    }

    public synchronized void stop() {
        running.set(false);
        Thread t = thread;
        if (t != null) {
            try { t.join(500); } catch (InterruptedException ignored) { }
            thread = null;
        }
        nv21 = null;
    }

    /** Latest full-size NV21 frame (already rotated by metadata+offset). */
    public byte[] latestFrame() {
        return nv21;
    }

    public int frameWidth() { return frameWidth; }
    public int frameHeight() { return frameHeight; }

    /**
     * Fills {@code dst} with the latest frame center-cropped/scaled to
     * (dstW,dstH). Returns false when no frame is available yet.
     */
    public boolean fillFrame(byte[] dst, int dstW, int dstH) {
        byte[] src = nv21;
        if (src == null || dst == null) return false;
        int sw = frameWidth, sh = frameHeight;
        if (sw <= 0 || sh <= 0) return false;
        if (sw == dstW && sh == dstH && dst.length == src.length) {
            System.arraycopy(src, 0, dst, 0, src.length);
            return true;
        }
        cropAndScaleNV21(src, sw, sh, dst, dstW, dstH);
        return true;
    }

    // ------------------------------------------------------------------
    // Decode loop
    // ------------------------------------------------------------------
    private void decodeLoop() {
        while (running.get()) {
            MediaExtractor extractor = null;
            MediaCodec codec = null;
            try {
                extractor = new MediaExtractor();
                extractor.setDataSource(videoPath);
                int track = -1;
                MediaFormat format = null;
                for (int i = 0; i < extractor.getTrackCount(); i++) {
                    MediaFormat f = extractor.getTrackFormat(i);
                    String mime = f.getString(MediaFormat.KEY_MIME);
                    if (mime != null && mime.startsWith("video/")) {
                        track = i;
                        format = f;
                        break;
                    }
                }
                if (track < 0 || format == null) {
                    KailLog.INSTANCE.e(null, TAG, "no video track in " + videoPath);
                    return;
                }
                extractor.selectTrack(track);
                videoRotation = format.containsKey(MediaFormat.KEY_ROTATION)
                        ? format.getInteger(MediaFormat.KEY_ROTATION) : 0;
                String mime = format.getString(MediaFormat.KEY_MIME);
                codec = MediaCodec.createDecoderByType(mime);
                // No surface: we want raw YUV buffers.
                codec.configure(format, null, null, 0);
                codec.start();

                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                boolean inputDone = false;
                while (running.get()) {
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
                        try {
                            Image img = codec.getOutputImage(outIdx);
                            if (img != null) {
                                consumeImage(img);
                                img.close();
                            }
                        } finally {
                            codec.releaseOutputBuffer(outIdx, false);
                        }
                        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            break; // restart for looping
                        }
                    } else if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        KailLog.INSTANCE.i(null, TAG, "format changed: " + codec.getOutputFormat());
                    }
                }
            } catch (Throwable th) {
                KailLog.INSTANCE.e(null, TAG, "decodeLoop error", th);
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

    private void consumeImage(Image img) {
        try {
            int w = img.getWidth();
            int h = img.getHeight();
            byte[] raw = imageToNV21(img);
            int totalRot = (videoRotation + extraRotation) % 360;
            if (totalRot != 0) {
                raw = rotateNV21(raw, w, h, totalRot);
                if (totalRot == 90 || totalRot == 270) {
                    int t = w; w = h; h = t;
                }
            }
            nv21 = raw;
            frameWidth = w;
            frameHeight = h;
        } catch (Throwable th) {
            KailLog.INSTANCE.e(null, TAG, "consumeImage", th);
        }
    }

    // ------------------------------------------------------------------
    // NV21 helpers (ported from CamSwap VideoToFrames)
    // ------------------------------------------------------------------
    static byte[] imageToNV21(Image image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int ySize = width * height;
        int uvSize = width * height / 4;
        byte[] nv21 = new byte[ySize + uvSize * 2];

        Image.Plane yPlane = image.getPlanes()[0];
        Image.Plane uPlane = image.getPlanes()[1];
        Image.Plane vPlane = image.getPlanes()[2];

        ByteBuffer yBuffer = yPlane.getBuffer();
        int yRowStride = yPlane.getRowStride();
        int yPixelStride = yPlane.getPixelStride();

        int pos = 0;
        if (yPixelStride == 1) {
            for (int row = 0; row < height; row++) {
                yBuffer.position(row * yRowStride);
                yBuffer.get(nv21, pos, width);
                pos += width;
            }
        } else {
            byte[] rowBuf = new byte[yRowStride];
            for (int row = 0; row < height; row++) {
                yBuffer.position(row * yRowStride);
                int len = Math.min(yRowStride, yBuffer.remaining());
                yBuffer.get(rowBuf, 0, len);
                for (int col = 0; col < width; col++) {
                    nv21[pos++] = rowBuf[col * yPixelStride];
                }
            }
        }

        int uvHeight = height / 2;
        int uvWidth = width / 2;
        ByteBuffer uBuffer = uPlane.getBuffer();
        ByteBuffer vBuffer = vPlane.getBuffer();
        int uRowStride = uPlane.getRowStride();
        int vRowStride = vPlane.getRowStride();
        int uPixelStride = uPlane.getPixelStride();
        int vPixelStride = vPlane.getPixelStride();

        byte[] uRow = new byte[uRowStride];
        byte[] vRow = new byte[vRowStride];
        for (int row = 0; row < uvHeight; row++) {
            uBuffer.position(row * uRowStride);
            vBuffer.position(row * vRowStride);
            int uLen = Math.min(uRowStride, uBuffer.remaining());
            int vLen = Math.min(vRowStride, vBuffer.remaining());
            uBuffer.get(uRow, 0, uLen);
            vBuffer.get(vRow, 0, vLen);
            for (int col = 0; col < uvWidth; col++) {
                nv21[pos++] = vRow[col * vPixelStride]; // V first in NV21
                nv21[pos++] = uRow[col * uPixelStride];
            }
        }
        return nv21;
    }

    static byte[] rotateNV21(byte[] input, int width, int height, int rotation) {
        byte[] output = new byte[input.length];
        int frameSize = width * height;
        boolean swap = rotation == 90 || rotation == 270;

        // Y plane
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int nx, ny;
                switch (rotation) {
                    case 90:  nx = height - 1 - y; ny = x; break;
                    case 180: nx = width - 1 - x;  ny = height - 1 - y; break;
                    case 270: nx = y;              ny = width - 1 - x; break;
                    default:  nx = x;              ny = y; break;
                }
                int outW = swap ? height : width;
                output[ny * outW + nx] = input[y * width + x];
            }
        }
        // VU interleaved plane
        int uvHeight = height / 2;
        int uvWidth = width / 2;
        for (int y = 0; y < uvHeight; y++) {
            for (int x = 0; x < uvWidth; x++) {
                int srcPos = frameSize + y * width + x * 2;
                int nx, ny;
                switch (rotation) {
                    case 90:  nx = uvHeight - 1 - y; ny = x; break;
                    case 180: nx = uvWidth - 1 - x;  ny = uvHeight - 1 - y; break;
                    case 270: nx = y;                ny = uvWidth - 1 - x; break;
                    default:  nx = x;                ny = y; break;
                }
                int outUvW = swap ? uvHeight : uvWidth;
                int dstPos = frameSize + ny * outUvW * 2 + nx * 2;
                output[dstPos] = input[srcPos];
                output[dstPos + 1] = input[srcPos + 1];
            }
        }
        return output;
    }

    /** Nearest-neighbor center-crop + scale, keeps target aspect. */
    static void cropAndScaleNV21(byte[] src, int sw, int sh, byte[] dst, int dw, int dh) {
        float srcAspect = (float) sw / sh;
        float dstAspect = (float) dw / dh;
        int cropW = sw, cropH = sh, cropX = 0, cropY = 0;
        if (srcAspect > dstAspect) {
            cropW = (int) (sh * dstAspect);
            cropX = (sw - cropW) / 2;
        } else if (srcAspect < dstAspect) {
            cropH = (int) (sw / dstAspect);
            cropY = (sh - cropH) / 2;
        }
        int sFrame = sw * sh;
        int dFrame = dw * dh;
        // Y
        for (int y = 0; y < dh; y++) {
            int sy = cropY + y * cropH / dh;
            for (int x = 0; x < dw; x++) {
                int sx = cropX + x * cropW / dw;
                dst[y * dw + x] = src[sy * sw + sx];
            }
        }
        // VU
        int sUvW = sw, dUvW = dw; // row stride in bytes (2 bytes per uv pair)
        for (int y = 0; y < dh / 2; y++) {
            int sy = cropY / 2 + y * (cropH / 2) / (dh / 2);
            for (int x = 0; x < dw / 2; x++) {
                int sx = cropX / 2 + x * (cropW / 2) / (dw / 2);
                int sPos = sFrame + sy * sUvW + sx * 2;
                int dPos = dFrame + y * dUvW + x * 2;
                dst[dPos] = src[sPos];
                dst[dPos + 1] = src[sPos + 1];
            }
        }
    }

    private static void sleepQuiet(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) { }
    }
}
