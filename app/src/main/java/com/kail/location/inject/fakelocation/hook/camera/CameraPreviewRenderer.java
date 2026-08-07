package com.kail.location.inject.fakelocation.hook.camera;

import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.view.Surface;

import com.kail.location.utils.KailLog;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Plays the replacement video into a target preview {@link SurfaceTexture}
 * using a looping {@link MediaPlayer}. This is the preview path for both
 * Camera1 (real SurfaceTexture captured in setPreviewTexture) and Camera2
 * (preview surface captured in addTarget / createCaptureSession).
 *
 * Simplified relative to CamSwap's GLVideoRenderer+SurfaceRelay chain: we let
 * MediaPlayer render directly onto a Surface created over the app's
 * SurfaceTexture. MediaPlayer handles scaling/letter-boxing to the buffer
 * size the app set via setDefaultBufferSize, which is acceptable for the
 * MVP; a GL pass (for rotation) can be layered on later without changing the
 * hook surface.
 */
public final class CameraPreviewRenderer {

    private static final String TAG = "CamPreview";
    private static final CameraPreviewRenderer INSTANCE = new CameraPreviewRenderer();

    private final Map<SurfaceTexture, MediaPlayer> players = new ConcurrentHashMap<>();
    private final Map<Surface, MediaPlayer> surfacePlayers = new ConcurrentHashMap<>();

    private CameraPreviewRenderer() {
    }

    public static CameraPreviewRenderer get() {
        return INSTANCE;
    }

    /** Starts (or restarts) playback of {@code videoPath} into {@code st}. */
    public synchronized void playInto(SurfaceTexture st, String videoPath) {
        if (st == null || videoPath == null || videoPath.isEmpty()) return;
        if (!new File(videoPath).exists()) return;
        stopInto(st);
        try {
            final Surface surface = new Surface(st);
            final MediaPlayer mp = new MediaPlayer();
            mp.setDataSource(videoPath);
            mp.setSurface(surface);
            mp.setLooping(true);
            applyVolume(mp);
            mp.setOnPreparedListener(p -> {
                try { p.start(); } catch (Throwable th) { KailLog.INSTANCE.e(null, TAG, "start", th); }
            });
            mp.setOnErrorListener((p, what, extra) -> {
                KailLog.INSTANCE.e(null, TAG, "MediaPlayer error " + what + "/" + extra);
                return true;
            });
            mp.setOnCompletionListener(p -> {
                // looping should prevent this; surface release on cleanup
                try { surface.release(); } catch (Throwable ignored) { }
            });
            mp.prepareAsync();
            players.put(st, mp);
            KailLog.INSTANCE.i(null, TAG, "playInto started: " + videoPath);
        } catch (Throwable th) {
            KailLog.INSTANCE.e(null, TAG, "playInto failed", th);
        }
    }

    /** Camera2 path: play into a preview {@link Surface} directly. Returns true on success. */
    public synchronized boolean playIntoSurface(Surface surface, String videoPath) {
        if (surface == null || videoPath == null || videoPath.isEmpty()) return false;
        if (!new File(videoPath).exists()) return false;
        stopIntoSurface(surface);
        try {
            final MediaPlayer mp = new MediaPlayer();
            mp.setDataSource(videoPath);
            mp.setSurface(surface);
            mp.setLooping(true);
            applyVolume(mp);
            mp.setOnPreparedListener(p -> {
                try { p.start(); } catch (Throwable th) { KailLog.INSTANCE.e(null, TAG, "start", th); }
            });
            mp.setOnErrorListener((p, what, extra) -> {
                KailLog.INSTANCE.e(null, TAG, "MediaPlayer error " + what + "/" + extra);
                return true;
            });
            mp.prepareAsync();
            surfacePlayers.put(surface, mp);
            KailLog.INSTANCE.i(null, TAG, "playIntoSurface started: " + videoPath);
            return true;
        } catch (Throwable th) {
            KailLog.INSTANCE.e(null, TAG, "playIntoSurface failed", th);
            return false;
        }
    }

    /**
     * Stream path (media_source=stream): play a network URL (HTTP/HLS/RTSP as
     * supported by the platform MediaPlayer) into the preview surface.
     */
    public synchronized boolean playStreamIntoSurface(Surface surface, String url) {
        if (surface == null || url == null || url.isEmpty()) return false;
        stopIntoSurface(surface);
        try {
            final MediaPlayer mp = new MediaPlayer();
            mp.setDataSource(url);
            mp.setSurface(surface);
            mp.setLooping(false);
            applyVolume(mp);
            mp.setOnPreparedListener(p -> {
                try { p.start(); } catch (Throwable th) { KailLog.INSTANCE.e(null, TAG, "start", th); }
            });
            mp.setOnErrorListener((p, what, extra) -> {
                KailLog.INSTANCE.e(null, TAG, "stream MediaPlayer error " + what + "/" + extra);
                return true;
            });
            mp.setOnCompletionListener(p -> {
                // Auto-reconnect: restart the stream on EOS.
                try {
                    p.reset();
                    p.setDataSource(url);
                    applyVolume(p);
                    p.setSurface(surface);
                    p.prepareAsync();
                } catch (Throwable th) {
                    KailLog.INSTANCE.e(null, TAG, "stream reconnect", th);
                }
            });
            mp.prepareAsync();
            surfacePlayers.put(surface, mp);
            KailLog.INSTANCE.i(null, TAG, "playStreamIntoSurface started: " + url);
            return true;
        } catch (Throwable th) {
            KailLog.INSTANCE.e(null, TAG, "playStreamIntoSurface failed", th);
            return false;
        }
    }

    /** Camera1 texture variant of the stream path. */
    public synchronized void playStreamInto(SurfaceTexture st, String url) {
        if (st == null || url == null || url.isEmpty()) return;
        try {
            final Surface surface = new Surface(st);
            playStreamIntoSurface(surface, url);
        } catch (Throwable th) {
            KailLog.INSTANCE.e(null, TAG, "playStreamInto failed", th);
        }
    }

    private void applyVolume(MediaPlayer mp) {
        try {
            if (CameraHookMain.videoSoundEnabled()) {
                mp.setVolume(1f, 1f);
            } else {
                mp.setVolume(0f, 0f);
            }
        } catch (Throwable ignored) { }
    }

    public synchronized void stopInto(SurfaceTexture st) {
        MediaPlayer mp = players.remove(st);
        if (mp != null) {
            try { mp.stop(); } catch (Throwable ignored) { }
            try { mp.release(); } catch (Throwable ignored) { }
        }
    }

    public synchronized void stopIntoSurface(Surface surface) {
        MediaPlayer mp = surfacePlayers.remove(surface);
        if (mp != null) {
            try { mp.stop(); } catch (Throwable ignored) { }
            try { mp.release(); } catch (Throwable ignored) { }
        }
    }

    public synchronized void stopAll() {
        for (SurfaceTexture st : players.keySet()) stopInto(st);
        for (Surface s : surfacePlayers.keySet()) stopIntoSurface(s);
        players.clear();
        surfacePlayers.clear();
    }
}
