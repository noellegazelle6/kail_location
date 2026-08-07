package com.kail.location.inject.fakelocation.hook.camera;

import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.view.SurfaceHolder;

import com.kail.location.lib.lhooker.LHooker;
import com.kail.location.utils.KailLog;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

/**
 * Camera1 (android.hardware.Camera) hooks, ported from CamSwap's
 * Camera1Handler to LHooker's static hook/_bak/_copy convention.
 *
 * Behaviour:
 *  - setPreviewTexture: swap the app's SurfaceTexture for a dummy so HAL
 *    frames go nowhere; remember the real one and start the video player.
 *  - setPreviewDisplay: redirect to a fake SurfaceTexture path.
 *  - startPreview: size the real texture's buffer + start playback.
 *  - setPreviewCallback(WithBuffer)/setOneShotPreviewCallback: hook the
 *    callback implementation's onPreviewFrame so the byte[] gets the video's
 *    NV21 frame before the app sees it.
 *  - stopPreview/release: stop playback.
 */
public final class Camera1Hook {

    private static final String TAG = "Camera1Hook";

    private static volatile SurfaceTexture realTexture;
    private static volatile SurfaceHolder realHolder;
    private static SurfaceTexture fakeTexture;
    private static android.view.Surface fakeSurface;
    private static volatile int displayOrientation;
    private static volatile boolean installed;

    private static final Set<String> hookedCallbackClasses = new HashSet<>();

    private Camera1Hook() {
    }

    public static synchronized void hook(ClassLoader cl) {
        if (installed) return;
        try {
            // setPreviewTexture(SurfaceTexture)
            LHooker.hookMethodAutoBackup(Camera.class, "setPreviewTexture",
                    Void.TYPE, new Class[]{SurfaceTexture.class},
                    Camera1Hook.class, "setPreviewTexture");
            // startPreview()
            LHooker.hookMethodAutoBackup(Camera.class, "startPreview",
                    Void.TYPE, null, Camera1Hook.class, "startPreview");
            // stopPreview()
            LHooker.hookMethodAutoBackup(Camera.class, "stopPreview",
                    Void.TYPE, null, Camera1Hook.class, "stopPreview");
            // release()
            LHooker.hookMethodAutoBackup(Camera.class, "release",
                    Void.TYPE, null, Camera1Hook.class, "release");
            // setPreviewDisplay(SurfaceHolder)
            LHooker.hookMethodAutoBackup(Camera.class, "setPreviewDisplay",
                    Void.TYPE, new Class[]{SurfaceHolder.class},
                    Camera1Hook.class, "setPreviewDisplay");
            // setDisplayOrientation(int)
            LHooker.hookMethodAutoBackup(Camera.class, "setDisplayOrientation",
                    Void.TYPE, new Class[]{Integer.TYPE},
                    Camera1Hook.class, "setDisplayOrientation");
            // preview-callback registration variants
            LHooker.hookMethodAutoBackup(Camera.class, "setPreviewCallback",
                    Void.TYPE, new Class[]{Camera.PreviewCallback.class},
                    Camera1Hook.class, "setPreviewCallback");
            LHooker.hookMethodAutoBackup(Camera.class, "setOneShotPreviewCallback",
                    Void.TYPE, new Class[]{Camera.PreviewCallback.class},
                    Camera1Hook.class, "setOneShotPreviewCallback");
            LHooker.hookMethodAutoBackup(Camera.class, "setPreviewCallbackWithBuffer",
                    Void.TYPE, new Class[]{Camera.PreviewCallback.class},
                    Camera1Hook.class, "setPreviewCallbackWithBuffer");
            installed = true;
            KailLog.INSTANCE.i(null, TAG, "Camera1 hooks installed");
        } catch (Throwable th) {
            KailLog.INSTANCE.e(null, TAG, "hook install failed", th);
        }
    }

    // ---------------------------------------------------------------
    // setPreviewTexture(SurfaceTexture)
    // ---------------------------------------------------------------
    public static void setPreviewTexture(Object receiver, SurfaceTexture texture) {
        if (!CameraHookMain.isActive()) {
            setPreviewTexture_bak(receiver, texture);
            return;
        }
        try {
            realTexture = texture;
            ensureFakeTexture();
            if (texture != null) {
                // Route HAL frames to the dummy; feed the real one our content.
                setPreviewTexture_bak(receiver, fakeTexture);
                feedTexture(texture);
            } else {
                setPreviewTexture_bak(receiver, null);
            }
        } catch (Throwable th) {
            KailLog.INSTANCE.e(null, TAG, "setPreviewTexture hook", th);
            setPreviewTexture_bak(receiver, texture);
        }
    }

    public static void setPreviewTexture_bak(Object receiver, SurfaceTexture texture) {
        pad();
        setPreviewTexture_copy(receiver, texture);
    }

    public static void setPreviewTexture_copy(Object receiver, SurfaceTexture texture) {
        pad();
    }

    // ---------------------------------------------------------------
    // setPreviewDisplay(SurfaceHolder)
    // ---------------------------------------------------------------
    public static void setPreviewDisplay(Object receiver, SurfaceHolder holder) {
        if (!CameraHookMain.isActive()) {
            setPreviewDisplay_bak(receiver, holder);
            return;
        }
        try {
            realHolder = holder;
            ensureFakeTexture();
            // Pretend success; steer the camera at the fake texture.
            setPreviewTexture_bak(receiver, fakeTexture);
            if (holder != null) {
                feedSurface(holder.getSurface());
            }
        } catch (Throwable th) {
            KailLog.INSTANCE.e(null, TAG, "setPreviewDisplay hook", th);
            try { setPreviewDisplay_bak(receiver, holder); } catch (Throwable ignored) { }
        }
    }

    public static void setPreviewDisplay_bak(Object receiver, SurfaceHolder holder) {
        pad();
        setPreviewDisplay_copy(receiver, holder);
    }

    public static void setPreviewDisplay_copy(Object receiver, SurfaceHolder holder) {
        pad();
    }

    // ---------------------------------------------------------------
    // setDisplayOrientation(int)
    // ---------------------------------------------------------------
    public static void setDisplayOrientation(Object receiver, int orientation) {
        displayOrientation = orientation;
        setDisplayOrientation_bak(receiver, orientation);
    }

    public static void setDisplayOrientation_bak(Object receiver, int orientation) {
        pad();
        setDisplayOrientation_copy(receiver, orientation);
    }

    public static void setDisplayOrientation_copy(Object receiver, int orientation) {
        pad();
    }

    // ---------------------------------------------------------------
    // startPreview()
    // ---------------------------------------------------------------
    public static void startPreview(Object receiver) {
        if (CameraHookMain.isActive()) {
            try {
                Camera cam = (Camera) receiver;
                Camera.Parameters params = cam.getParameters();
                if (params != null) {
                    Camera.Size sz = params.getPreviewSize();
                    if (sz != null) {
                        SurfaceTexture st = realTexture;
                        if (st != null) {
                            st.setDefaultBufferSize(sz.width, sz.height);
                        }
                        CameraFrameSource.get().setTargetSize(sz.width, sz.height);
                    }
                }
                // (Re)start feeding content into the real texture/surface.
                SurfaceTexture st = realTexture;
                if (st != null) {
                    feedTexture(st);
                } else if (realHolder != null) {
                    feedSurface(realHolder.getSurface());
                }
            } catch (Throwable th) {
                KailLog.INSTANCE.e(null, TAG, "startPreview hook", th);
            }
        }
        startPreview_bak(receiver);
    }

    public static void startPreview_bak(Object receiver) {
        pad();
        startPreview_copy(receiver);
    }

    public static void startPreview_copy(Object receiver) {
        pad();
    }

    // ---------------------------------------------------------------
    // stopPreview() / release()
    // ---------------------------------------------------------------
    public static void stopPreview(Object receiver) {
        CameraPreviewRenderer.get().stopAll();
        stopPreview_bak(receiver);
    }

    public static void stopPreview_bak(Object receiver) {
        pad();
        stopPreview_copy(receiver);
    }

    public static void stopPreview_copy(Object receiver) {
        pad();
    }

    public static void release(Object receiver) {
        CameraPreviewRenderer.get().stopAll();
        realTexture = null;
        realHolder = null;
        release_bak(receiver);
    }

    public static void release_bak(Object receiver) {
        pad();
        release_copy(receiver);
    }

    public static void release_copy(Object receiver) {
        pad();
    }

    // ---------------------------------------------------------------
    // Preview-callback registration — hook the callback's onPreviewFrame.
    // ---------------------------------------------------------------
    public static void setPreviewCallback(Object receiver, Camera.PreviewCallback cb) {
        hookPreviewCallback(cb);
        setPreviewCallback_bak(receiver, cb);
    }

    public static void setPreviewCallback_bak(Object receiver, Camera.PreviewCallback cb) {
        pad();
        setPreviewCallback_copy(receiver, cb);
    }

    public static void setPreviewCallback_copy(Object receiver, Camera.PreviewCallback cb) {
        pad();
    }

    public static void setOneShotPreviewCallback(Object receiver, Camera.PreviewCallback cb) {
        hookPreviewCallback(cb);
        setOneShotPreviewCallback_bak(receiver, cb);
    }

    public static void setOneShotPreviewCallback_bak(Object receiver, Camera.PreviewCallback cb) {
        pad();
        setOneShotPreviewCallback_copy(receiver, cb);
    }

    public static void setOneShotPreviewCallback_copy(Object receiver, Camera.PreviewCallback cb) {
        pad();
    }

    public static void setPreviewCallbackWithBuffer(Object receiver, Camera.PreviewCallback cb) {
        hookPreviewCallback(cb);
        setPreviewCallbackWithBuffer_bak(receiver, cb);
    }

    public static void setPreviewCallbackWithBuffer_bak(Object receiver, Camera.PreviewCallback cb) {
        pad();
        setPreviewCallbackWithBuffer_copy(receiver, cb);
    }

    public static void setPreviewCallbackWithBuffer_copy(Object receiver, Camera.PreviewCallback cb) {
        pad();
    }

    // ---------------------------------------------------------------
    // Dynamic per-callback-class hook of onPreviewFrame(byte[], Camera).
    // The hook body lives in Camera1PreviewCallbackHook (its _bak/_copy).
    // ---------------------------------------------------------------
    static void hookPreviewCallback(Camera.PreviewCallback cb) {
        if (cb == null || !CameraHookMain.isActive()) return;
        try {
            Class<?> cls = cb.getClass();
            synchronized (hookedCallbackClasses) {
                // Walk up the hierarchy: the callback may inherit onPreviewFrame
                // from a base class instead of declaring it directly.
                Class<?> declaring = cls;
                Method target = null;
                while (declaring != null && declaring != Object.class) {
                    try {
                        target = declaring.getDeclaredMethod("onPreviewFrame", byte[].class, Camera.class);
                        break;
                    } catch (NoSuchMethodException e) {
                        declaring = declaring.getSuperclass();
                    }
                }
                if (target == null) {
                    KailLog.INSTANCE.w(null, TAG, "onPreviewFrame not found on " + cls.getName());
                    return;
                }
                String key = declaring.getName();
                if (hookedCallbackClasses.contains(key)) return;
                Method hook = Camera1PreviewCallbackHook.class.getDeclaredMethod(
                        "onPreviewFrame", Object.class, byte[].class, Camera.class);
                Method bak = Camera1PreviewCallbackHook.class.getDeclaredMethod(
                        "onPreviewFrame_bak", Object.class, byte[].class, Camera.class);
                Method copy = Camera1PreviewCallbackHook.class.getDeclaredMethod(
                        "onPreviewFrame_copy", Object.class, byte[].class, Camera.class);
                LHooker.hookMethod(target, hook, bak, copy);
                hookedCallbackClasses.add(key);
                KailLog.INSTANCE.i(null, TAG, "hooked onPreviewFrame on " + key);
            }
        } catch (Throwable th) {
            KailLog.INSTANCE.e(null, TAG, "hookPreviewCallback failed", th);
        }
    }

    private static void ensureFakeTexture() {
        if (fakeTexture == null) {
            fakeTexture = new SurfaceTexture(10);
            fakeSurface = new android.view.Surface(fakeTexture);
        }
    }

    /** Feeds the active content type into the app's real preview texture. */
    private static void feedTexture(SurfaceTexture texture) {
        if (CameraHookMain.isImageMode()) {
            CameraImageSource.get().drawInto(texture, CameraHookMain.imagePath(),
                    CameraHookConfig.get().rotationOffset);
        } else if (CameraHookMain.isStreamMode()) {
            CameraPreviewRenderer.get().playStreamInto(texture, CameraHookMain.streamUrl());
        } else {
            CameraPreviewRenderer.get().playInto(texture, CameraHookMain.videoPath());
        }
    }

    /** Feeds the active content type into the app's real preview surface. */
    private static void feedSurface(android.view.Surface surface) {
        if (CameraHookMain.isImageMode()) {
            CameraImageSource.get().drawInto(surface, CameraHookMain.imagePath(),
                    CameraHookConfig.get().rotationOffset);
        } else if (CameraHookMain.isStreamMode()) {
            CameraPreviewRenderer.get().playStreamIntoSurface(surface, CameraHookMain.streamUrl());
        } else {
            CameraPreviewRenderer.get().playIntoSurface(surface, CameraHookMain.videoPath());
        }
    }

    /** Filler so the native engine has room to install the trampoline. */
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
