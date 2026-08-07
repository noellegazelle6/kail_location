package com.kail.location.inject.fakelocation.hook.camera;

import android.graphics.SurfaceTexture;
import android.media.ImageReader;
import android.view.Surface;

import com.kail.location.lib.lhooker.LHooker;
import com.kail.location.utils.KailLog;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Camera2 preview-replacement hooks (subset of CamSwap's Camera2Handler).
 *
 * Strategy: every PREVIEW {@link Surface} the app uses is swapped for a
 * private per-surface drain (SurfaceTexture) so HAL output is discarded; the
 * real surface gets the replacement video played into it. {@link ImageReader}
 * surfaces (YUV analysis / JPEG capture) are tracked via hooked
 * {@code ImageReader.newInstance} and kept untouched, so capture/analysis
 * paths keep working.
 *
 * Hooked methods:
 *  - CaptureRequest.Builder.addTarget(Surface) / removeTarget(Surface) / build()
 *  - CameraDeviceImpl.createCaptureSession(List, StateCallback, Handler)
 *  - ImageReader.newInstance(int,int,int,int) and the API-29 usage variant
 */
public final class Camera2Hook {

    private static final String TAG = "Camera2Hook";

    private static volatile boolean installed;

    /** Surfaces produced by ImageReader.getSurface() — never redirected. */
    private static final Set<Surface> readerSurfaces = ConcurrentHashMap.newKeySet();

    /** Single shared drain surface — avoids "Broken pipe" from per-surface |
     *  SurfaceTexture creation on some camera HALs. */
    private static final Surface SHARED_DRAIN = createDrain();

    /** app surfaces currently redirected, with video playback attached. */
    private static final Set<Surface> redirected = ConcurrentHashMap.newKeySet();

    private static Surface createDrain() {
        SurfaceTexture st = new SurfaceTexture(0);
        st.setDefaultBufferSize(4096, 3072);
        return new Surface(st);
    }

    private Camera2Hook() {
    }

    public static synchronized void hook(ClassLoader cl) {
        if (installed) return;
        try {
            Class<?> builderClass = Class.forName("android.hardware.camera2.CaptureRequest$Builder", false, cl);
            LHooker.hookMethodAutoBackup(builderClass, "addTarget", Void.TYPE,
                    new Class[]{Surface.class}, Camera2Hook.class, "addTarget");
            LHooker.hookMethodAutoBackup(builderClass, "removeTarget", Void.TYPE,
                    new Class[]{Surface.class}, Camera2Hook.class, "removeTarget");
            LHooker.hookMethodAutoBackup(builderClass, "build",
                    Class.forName("android.hardware.camera2.CaptureRequest", false, cl),
                    null, Camera2Hook.class, "build");

            try {
                Class<?> deviceImpl = Class.forName("android.hardware.camera2.impl.CameraDeviceImpl", false, cl);
                Class<?> sessionStateCb = Class.forName("android.hardware.camera2.CameraCaptureSession$StateCallback", false, cl);
                LHooker.hookMethodAutoBackup(deviceImpl, "createCaptureSession", Void.TYPE,
                        new Class[]{List.class, sessionStateCb, android.os.Handler.class},
                        Camera2Hook.class, "createCaptureSession");

                // CameraX / API 28+ path: SessionConfiguration variant.
                // Default method calls createCaptureSessionByOutputConfigurations.
                LHooker.hookMethodAutoBackup(deviceImpl, "createCaptureSession", Void.TYPE,
                    new Class[]{Class.forName("android.hardware.camera2.params.SessionConfiguration", false, cl)},
                    Camera2Hook.class, "createCaptureSessionConfig");

                // OutputConfiguration-based session (API 23+, also called by CameraX).
                LHooker.hookMethodAutoBackup(deviceImpl, "createCaptureSessionByOutputConfigurations",
                    Void.TYPE,
                    new Class[]{List.class, sessionStateCb, android.os.Handler.class},
                    Camera2Hook.class, "createCaptureSessionByOutputs");
            } catch (Throwable th) {
                KailLog.INSTANCE.w(null, TAG, "createCaptureSession hooks unavailable", th);
            }

            // Track ImageReader surfaces so we keep them out of the redirect.
            try {
                LHooker.hookMethodAutoBackup(ImageReader.class, "newInstance",
                        ImageReader.class,
                        new Class[]{Integer.TYPE, Integer.TYPE, Integer.TYPE, Integer.TYPE},
                        Camera2Hook.class, "newInstance");
            } catch (Throwable th) {
                KailLog.INSTANCE.w(null, TAG, "ImageReader.newInstance(4) hook unavailable", th);
            }
            try {
                LHooker.hookMethodAutoBackup(ImageReader.class, "newInstance",
                        ImageReader.class,
                        new Class[]{Integer.TYPE, Integer.TYPE, Integer.TYPE, Integer.TYPE, Long.TYPE},
                        Camera2Hook.class, "newInstance5");
            } catch (Throwable th) {
                KailLog.INSTANCE.w(null, TAG, "ImageReader.newInstance(5) hook unavailable", th);
            }

            installed = true;
            KailLog.INSTANCE.i(null, TAG, "Camera2 hooks installed");
        } catch (Throwable th) {
            KailLog.INSTANCE.e(null, TAG, "hook install failed", th);
        }
    }

    // ---------------------------------------------------------------
    // addTarget(Surface)
    // ---------------------------------------------------------------
    public static void addTarget(Object receiver, Surface surface) {
        if (CameraHookMain.isActive() && surface != null && isPreviewSurface(surface)) {
            try {
                if (redirected.add(surface) && !feedSurface(surface)) {
                    redirected.remove(surface);
                }
                if (redirected.contains(surface)) {
                    Surface drain = drainFor(surface);
                    addTarget_bak(receiver, drain);
                    return;
                }
            } catch (Throwable th) {
                KailLog.INSTANCE.e(null, TAG, "addTarget hook", th);
            }
        }
        addTarget_bak(receiver, surface);
    }

    public static void addTarget_bak(Object receiver, Surface surface) {
        pad();
        addTarget_copy(receiver, surface);
    }

    public static void addTarget_copy(Object receiver, Surface surface) {
        pad();
    }

    // ---------------------------------------------------------------
    // removeTarget(Surface)
    // ---------------------------------------------------------------
    public static void removeTarget(Object receiver, Surface surface) {
        if (redirected.remove(surface)) {
            CameraPreviewRenderer.get().stopIntoSurface(surface);
            removeTarget_bak(receiver, SHARED_DRAIN);
            return;
        }
        removeTarget_bak(receiver, surface);
    }

    public static void removeTarget_bak(Object receiver, Surface surface) {
        pad();
        removeTarget_copy(receiver, surface);
    }

    public static void removeTarget_copy(Object receiver, Surface surface) {
        pad();
    }

    // ---------------------------------------------------------------
    // build()
    // ---------------------------------------------------------------
    public static Object build(Object receiver) {
        return build_bak(receiver);
    }

    public static Object build_bak(Object receiver) {
        pad();
        return build_copy(receiver);
    }

    public static Object build_copy(Object receiver) {
        pad();
        return null;
    }

    // ---------------------------------------------------------------
    // CameraDeviceImpl.createCaptureSession(List, StateCallback, Handler)
    // ---------------------------------------------------------------
    public static void createCaptureSession(Object receiver, List outputs, Object callback, android.os.Handler handler) {
        if (CameraHookMain.isActive() && outputs != null) {
            try {
                // Never mutate the caller's list — it may be immutable.
                java.util.ArrayList rewritten = null;
                for (int i = 0; i < outputs.size(); i++) {
                    Object o = outputs.get(i);
                    if (o instanceof Surface && isPreviewSurface((Surface) o)) {
                        Surface s = (Surface) o;
                        boolean firstTime = redirected.add(s);
                        if (firstTime && !feedSurface(s)) {
                            redirected.remove(s);
                        }
                        if (redirected.contains(s)) {
                            if (rewritten == null) {
                                rewritten = new java.util.ArrayList(outputs);
                            }
                            Surface drain = drainFor(s);
                            rewritten.set(i, drain);
                        }
                    }
                }
                if (rewritten != null) {
                    createCaptureSession_bak(receiver, rewritten, callback, handler);
                    return;
                }
            } catch (Throwable th) {
                KailLog.INSTANCE.e(null, TAG, "createCaptureSession hook", th);
            }
        }
        createCaptureSession_bak(receiver, outputs, callback, handler);
    }

    public static void createCaptureSession_bak(Object receiver, List outputs, Object callback, android.os.Handler handler) {
        pad();
        createCaptureSession_copy(receiver, outputs, callback, handler);
    }

    public static void createCaptureSession_copy(Object receiver, List outputs, Object callback, android.os.Handler handler) {
        pad();
    }

    // ---------------------------------------------------------------
    // CameraDeviceImpl.createCaptureSession(SessionConfiguration)
    // CameraX path on API 28+. Need to build a new SessionConfiguration
    // with preview surfaces replaced by drains.
    // ---------------------------------------------------------------
    public static void createCaptureSessionConfig(Object receiver, Object config) {
        if (CameraHookMain.isActive() && config != null) {
            try {
                Object outputs = config.getClass().getMethod("getOutputConfigurations").invoke(config);
                if (outputs instanceof List) {
                    List<?> list = (List<?>) outputs;
                    java.util.ArrayList rewritten = null;
                    for (int i = 0; i < list.size(); i++) {
                        Object o = list.get(i);
                        Surface s = (Surface) o.getClass().getMethod("getSurface").invoke(o);
                        if (!readerSurfaces.contains(s)) {
                            if (rewritten == null) rewritten = new java.util.ArrayList(list);
                            Surface drain = drainFor(s);
                            Class<?> outClz = o.getClass();
                            // Create a new OutputConfiguration with the drain surface.
                            Object newOut = outClz.getConstructor(Surface.class).newInstance(drain);
                            rewritten.set(i, newOut);
                            if (redirected.add(s)) {
                                feedSurface(s);
                            }
                        }
                    }
                    if (rewritten != null) {
                        // Build a new SessionConfiguration with the rewritten outputs.
                        Class<?> configClz = config.getClass();
                        Object sessionType = configClz.getMethod("getSessionType").invoke(config);
                        Object executor = configClz.getMethod("getExecutor").invoke(config);
                        Object cb = configClz.getMethod("getSessionCallback").invoke(config);
                        Object newConfig = configClz.getConstructor(
                                Integer.TYPE, List.class, java.util.concurrent.Executor.class,
                                Class.forName("android.hardware.camera2.CameraCaptureSession$StateCallback"))
                            .newInstance(sessionType, rewritten, executor, cb);
                        createCaptureSessionConfig_bak(receiver, newConfig);
                        return;
                    }
                }
            } catch (Throwable th) {
                KailLog.INSTANCE.e(null, TAG, "createCaptureSessionConfig", th);
            }
        }
        createCaptureSessionConfig_bak(receiver, config);
    }

    public static void createCaptureSessionConfig_bak(Object receiver, Object config) {
        pad();
        createCaptureSessionConfig_copy(receiver, config);
    }

    public static void createCaptureSessionConfig_copy(Object receiver, Object config) {
        pad();
    }

    // ---------------------------------------------------------------
    // CameraDeviceImpl.createCaptureSessionByOutputConfigurations
    // (API 23+, also called by CameraX's default Config path).
    // ---------------------------------------------------------------
    public static void createCaptureSessionByOutputs(Object receiver, List outputs, Object callback, android.os.Handler handler) {
        if (CameraHookMain.isActive() && outputs != null) {
            try {
                java.util.ArrayList rewritten = null;
                for (int i = 0; i < outputs.size(); i++) {
                    Object o = outputs.get(i);
                    Surface s = (Surface) o.getClass().getMethod("getSurface").invoke(o);
                    if (!readerSurfaces.contains(s)) {
                        if (rewritten == null) rewritten = new java.util.ArrayList(outputs);
                        Surface drain = drainFor(s);
                        Class<?> outClz = o.getClass();
                        Object newOut = outClz.getConstructor(Surface.class).newInstance(drain);
                        rewritten.set(i, newOut);
                        if (redirected.add(s)) {
                            feedSurface(s);
                        }
                    }
                }
                if (rewritten != null) {
                    createCaptureSessionByOutputs_bak(receiver, rewritten, callback, handler);
                    return;
                }
            } catch (Throwable th) {
                KailLog.INSTANCE.e(null, TAG, "createCaptureSessionByOutputs", th);
            }
        }
        createCaptureSessionByOutputs_bak(receiver, outputs, callback, handler);
    }

    public static void createCaptureSessionByOutputs_bak(Object receiver, List outputs, Object callback, android.os.Handler handler) {
        pad();
        createCaptureSessionByOutputs_copy(receiver, outputs, callback, handler);
    }

    public static void createCaptureSessionByOutputs_copy(Object receiver, List outputs, Object callback, android.os.Handler handler) {
        pad();
    }

    // ---------------------------------------------------------------
    // ImageReader.newInstance — static methods, no receiver param.
    // After-hook: register the returned reader's surface as "keep".
    // ---------------------------------------------------------------
    public static Object newInstance(int width, int height, int format, int maxImages) {
        Object reader = newInstance_bak(width, height, format, maxImages);
        trackReader(reader);
        return reader;
    }

    public static Object newInstance_bak(int width, int height, int format, int maxImages) {
        pad();
        return newInstance_copy(width, height, format, maxImages);
    }

    public static Object newInstance_copy(int width, int height, int format, int maxImages) {
        pad();
        return null;
    }

    public static Object newInstance5(int width, int height, int format, int maxImages, long usage) {
        Object reader = newInstance5_bak(width, height, format, maxImages, usage);
        trackReader(reader);
        return reader;
    }

    public static Object newInstance5_bak(int width, int height, int format, int maxImages, long usage) {
        pad();
        return newInstance5_copy(width, height, format, maxImages, usage);
    }

    public static Object newInstance5_copy(int width, int height, int format, int maxImages, long usage) {
        pad();
        return null;
    }

    private static void trackReader(Object reader) {
        if (reader == null) return;
        try {
            Surface s = ((ImageReader) reader).getSurface();
            if (s != null) {
                readerSurfaces.add(s);
            }
        } catch (Throwable th) {
            KailLog.INSTANCE.e(null, TAG, "trackReader", th);
        }
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------
    private static Surface drainFor(Surface appSurface) {
        return SHARED_DRAIN;
    }

    /**
     * A surface is a preview target unless it was produced by an ImageReader
     * (analysis/photo capture paths stay on real frames for now).
     */
    private static boolean isPreviewSurface(Surface s) {
        return !readerSurfaces.contains(s);
    }

    /** Feeds the active content type (local video / stream / image) into a surface. Returns true on success. */
    private static boolean feedSurface(Surface surface) {
        if (CameraHookMain.isImageMode()) {
            return CameraImageSource.get().drawInto(surface, CameraHookMain.imagePath(),
                    CameraHookConfig.get().rotationOffset);
        } else if (CameraHookMain.isStreamMode()) {
            return CameraPreviewRenderer.get().playStreamIntoSurface(surface, CameraHookMain.streamUrl());
        } else {
            return CameraPreviewRenderer.get().playIntoSurface(surface, CameraHookMain.videoPath());
        }
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
