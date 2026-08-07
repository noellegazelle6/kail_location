package com.kail.location.inject.fakelocation.hook.camera;

import com.kail.location.utils.KailLog;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Entry point for the virtual-camera feature inside an injected app process.
 * Invoked from
 * {@code com.kail.location.inject.fakelocation.hook.app.AppProcessHook#applyHookToApp}.
 *
 * Hooks are installed UNCONDITIONALLY (once per process) and the live config
 * is consulted on every hooked-method invocation via {@link #isActive()},
 * because the native apphook loader is guarded against re-injection — a
 * config that arrives after the first injection must still take effect.
 */
public final class CameraHookMain {

    private static final String TAG = "CameraHookMain";

    private static final AtomicBoolean hooked = new AtomicBoolean(false);
    private static volatile String currentPackage = "";

    /** Active-session state: re-picked whenever a camera session starts. */
    private static volatile boolean lastActive;
    private static volatile String sessionVideoPath = "";

    private CameraHookMain() {
    }

    public static void hook(String packageName, ClassLoader cl) {
        try {
            currentPackage = packageName;
            if (hooked.compareAndSet(false, true)) {
                Camera1Hook.hook(cl);
                Camera2Hook.hook(cl);
                MicrophoneHook.hook(cl);
                KailLog.INSTANCE.i(null, TAG, "camera hooks installed for " + packageName);
            }
        } catch (Throwable th) {
            KailLog.INSTANCE.e(null, TAG, "hook failed", th);
        }
    }

    /**
     * Whether the virtual camera should substitute frames for THIS process
     * right now. Re-reads the config (cached ~1.5s) on every call so the
     * controller can start/stop/reconfigure without re-injection.
     */
    public static boolean isActive() {
        try {
            CameraHookConfig cfg = CameraHookConfig.get();
            if (!cfg.appliesTo(currentPackage)) {
                lastActive = false;
                return false;
            }
            boolean active;
            if (cfg.isStreamMode()) {
                active = cfg.hasStream();
            } else if (cfg.isImageMode()) {
                active = cfg.hasImage();
            } else {
                active = cfg.hasVideo() || cfg.hasRandomPool();
            }
            if (active) {
                if (!lastActive) {
                    onSessionStart(cfg);
                }
                ensureSources(cfg);
            }
            lastActive = active;
            return active;
        } catch (Throwable th) {
            return false;
        }
    }

    /** New camera session: (re)roll the random-play pick. */
    private static void onSessionStart(CameraHookConfig cfg) {
        if (!cfg.isStreamMode() && !cfg.isImageMode() && cfg.hasRandomPool()) {
            sessionVideoPath = pickRandomVideo(cfg.videoDir);
            KailLog.INSTANCE.i(null, TAG, "random play picked: " + sessionVideoPath);
        } else {
            sessionVideoPath = cfg.videoPath;
        }
    }

    private static void ensureSources(CameraHookConfig cfg) {
        if (cfg.isStreamMode() || cfg.isImageMode()) {
            return; // no NV21 decoder needed for these paths
        }
        String path = sessionVideoPath.isEmpty() ? cfg.videoPath : sessionVideoPath;
        CameraFrameSource.get().configure(path, cfg.rotationOffset);
        CameraFrameSource.get().start();
    }

    private static String pickRandomVideo(String dir) {
        try {
            File[] files = new File(dir).listFiles();
            if (files == null || files.length == 0) return "";
            List<String> videos = new ArrayList<>();
            for (File f : files) {
                if (f.isFile() && f.length() > 0) videos.add(f.getAbsolutePath());
            }
            if (videos.isEmpty()) return "";
            return videos.get(new Random().nextInt(videos.size()));
        } catch (Throwable th) {
            return "";
        }
    }

    // ------------------------------------------------------------------
    // Accessors used by the hooks on every intercepted call.
    // ------------------------------------------------------------------
    public static boolean isImageMode() {
        CameraHookConfig cfg = CameraHookConfig.get();
        return cfg.isImageMode() && cfg.hasImage();
    }

    public static boolean isStreamMode() {
        CameraHookConfig cfg = CameraHookConfig.get();
        return cfg.isStreamMode() && cfg.hasStream();
    }

    /** Local-video path for this session (random pick or configured video). */
    public static String videoPath() {
        String p = sessionVideoPath.isEmpty() ? CameraHookConfig.get().videoPath : sessionVideoPath;
        return p;
    }

    public static String streamUrl() {
        return CameraHookConfig.get().streamUrl;
    }

    public static String imagePath() {
        return CameraHookConfig.get().imagePath;
    }

    public static boolean videoSoundEnabled() {
        return CameraHookConfig.get().videoSound;
    }

    /** Mic substitution mode for MicAudioSource ("off" never reaches here). */
    public static String micMode() {
        return CameraHookConfig.get().micMode;
    }

    public static String micAudioPath() {
        return CameraHookConfig.get().audioPath;
    }

    /**
     * Whether mic substitution applies to this process right now. Cheap;
     * called from every AudioRecord.read hook.
     */
    public static boolean micActive() {
        try {
            CameraHookConfig cfg = CameraHookConfig.get();
            return cfg.appliesTo(currentPackage) && cfg.micHookEnabled();
        } catch (Throwable th) {
            return false;
        }
    }
}
