package com.kail.location.inject.fakelocation.hook.camera;

import android.hardware.Camera;

import com.kail.location.utils.KailLog;

/**
 * Hook body for {@code onPreviewFrame(byte[], Camera)} installed on the
 * app's own PreviewCallback implementation class at runtime. Overwrites the
 * incoming byte[] with the current video frame before the app consumes it.
 */
public final class Camera1PreviewCallbackHook {

    private static final String TAG = "Cam1PreviewCb";

    private Camera1PreviewCallbackHook() {
    }

    public static void onPreviewFrame(Object receiver, byte[] data, Camera camera) {
        if (CameraHookMain.isActive() && data != null) {
            try {
                if (CameraHookMain.isImageMode()) {
                    // Static image → NV21 at the callback's size.
                    int[] dims = dimsFromNv21Length(data.length);
                    if (dims[0] > 0) {
                        CameraImageSource.get().fillFrame(data, dims[0], dims[1],
                                CameraHookMain.imagePath(), CameraHookConfig.get().rotationOffset);
                    }
                } else if (CameraHookMain.isStreamMode()) {
                    // Stream mode has no local NV21 source; leave real frames.
                } else {
                    CameraFrameSource src = CameraFrameSource.get();
                    int tw = src.targetWidth();
                    int th = src.targetHeight();
                    if (tw <= 0 || th <= 0) {
                        // Derive expected NV21 size from the buffer length.
                        int[] dims = dimsFromNv21Length(data.length);
                        tw = dims[0];
                        th = dims[1];
                    }
                    if (tw > 0 && th > 0 && data.length == tw * th * 3 / 2) {
                        src.fillFrame(data, tw, th);
                    } else {
                        // Best effort: copy raw frame prefix.
                        byte[] frame = src.latestFrame();
                        if (frame != null) {
                            System.arraycopy(frame, 0, data, 0, Math.min(frame.length, data.length));
                        }
                    }
                }
            } catch (Throwable th) {
                KailLog.INSTANCE.e(null, TAG, "onPreviewFrame hook", th);
            }
        }
        onPreviewFrame_bak(receiver, data, camera);
    }

    public static void onPreviewFrame_bak(Object receiver, byte[] data, Camera camera) {
        pad();
        onPreviewFrame_copy(receiver, data, camera);
    }

    public static void onPreviewFrame_copy(Object receiver, byte[] data, Camera camera) {
        pad();
    }

    /** Common NV21 buffer-length → dimensions (w*h*3/2). */
    private static int[] dimsFromNv21Length(int len) {
        // 176x144=38016, 320x240=115200, 480x320=230400, 640x480=460800,
        // 1280x720=1382400, 1920x1080=3110400
        switch (len) {
            case 38016: return new int[]{176, 144};
            case 115200: return new int[]{320, 240};
            case 230400: return new int[]{480, 320};
            case 460800: return new int[]{640, 480};
            case 1382400: return new int[]{1280, 720};
            case 3110400: return new int[]{1920, 1080};
            default: return new int[]{0, 0};
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
