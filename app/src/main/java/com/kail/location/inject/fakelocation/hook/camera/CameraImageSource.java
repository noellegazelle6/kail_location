package com.kail.location.inject.fakelocation.hook.camera;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.view.Surface;

import com.kail.location.utils.KailLog;

import java.io.File;

/**
 * Static-image replacement source (replace_mode=image).
 *
 *  - For the NV21 preview-callback path: decode the image once, cache it, and
 *    convert/scale to NV21 at whatever size the app requests.
 *  - For the preview-surface path: draw the bitmap directly onto the app's
 *    Surface/SurfaceTexture via lockCanvas (re-posted periodically because
 *    camera stacks may recreate or clear the surface).
 */
public final class CameraImageSource {

    private static final String TAG = "CameraImageSrc";
    private static final CameraImageSource INSTANCE = new CameraImageSource();

    private volatile Bitmap bitmap;
    private volatile String loadedPath = "";
    private volatile int rotationOffset;

    // NV21 cache keyed by requested size.
    private volatile byte[] nv21Cache;
    private volatile int nv21W, nv21H;

    private CameraImageSource() {
    }

    public static CameraImageSource get() {
        return INSTANCE;
    }

    private synchronized Bitmap bitmapFor(String path, int rotOffset) {
        if (bitmap == null || !path.equals(loadedPath) || rotOffset != rotationOffset) {
            Bitmap raw = BitmapFactory.decodeFile(path);
            if (raw == null) {
                KailLog.INSTANCE.e(null, TAG, "decode failed: " + path);
                return null;
            }
            int rot = ((rotOffset % 360) + 360) % 360;
            if (rot != 0) {
                Matrix m = new Matrix();
                m.postRotate(rot);
                raw = Bitmap.createBitmap(raw, 0, 0, raw.getWidth(), raw.getHeight(), m, true);
            }
            bitmap = raw;
            loadedPath = path;
            rotationOffset = rotOffset;
            nv21Cache = null;
        }
        return bitmap;
    }

    // ---------------------------------------------------------------
    // NV21 path (Camera1 preview callbacks)
    // ---------------------------------------------------------------
    public boolean fillFrame(byte[] dst, int dstW, int dstH, String path, int rotOffset) {
        try {
            if (dst == null || dstW <= 0 || dstH <= 0) return false;
            if (nv21Cache == null || nv21W != dstW || nv21H != dstH) {
                Bitmap bmp = bitmapFor(path, rotOffset);
                if (bmp == null) return false;
                Bitmap scaled = centerCropScale(bmp, dstW, dstH);
                nv21Cache = argbToNv21(scaled, dstW, dstH);
                nv21W = dstW;
                nv21H = dstH;
                if (scaled != bmp) scaled.recycle();
            }
            if (nv21Cache == null || dst.length < nv21Cache.length) return false;
            System.arraycopy(nv21Cache, 0, dst, 0, nv21Cache.length);
            return true;
        } catch (Throwable th) {
            KailLog.INSTANCE.e(null, TAG, "fillFrame", th);
            return false;
        }
    }

    // ---------------------------------------------------------------
    // Surface path (Camera1 texture / Camera2 preview surface)
    // ---------------------------------------------------------------
    public boolean drawInto(Surface surface, String path, int rotOffset) {
        if (surface == null || !surface.isValid()) return false;
        try {
            Bitmap bmp = bitmapFor(path, rotOffset);
            if (bmp == null) return false;
            Canvas canvas = null;
            try {
                canvas = surface.lockCanvas(null);
                if (canvas == null) return false;
                Rect dst = new Rect(0, 0, canvas.getWidth(), canvas.getHeight());
                RectF src = centerCropRect(bmp.getWidth(), bmp.getHeight(),
                        (float) canvas.getWidth() / canvas.getHeight());
                canvas.drawColor(0xFF000000);
                canvas.drawBitmap(bmp, new Rect((int) src.left, (int) src.top, (int) src.right, (int) src.bottom),
                        dst, new Paint(Paint.FILTER_BITMAP_FLAG));
                return true;
            } finally {
                if (canvas != null) {
                    try { surface.unlockCanvasAndPost(canvas); } catch (Throwable ignored) { }
                }
            }
        } catch (Throwable th) {
            KailLog.INSTANCE.e(null, TAG, "drawInto", th);
            return false;
        }
    }

    public void drawInto(SurfaceTexture texture, String path, int rotOffset) {
        if (texture == null) return;
        try {
            Surface s = new Surface(texture);
            drawInto(s, path, rotOffset);
            s.release();
        } catch (Throwable th) {
            KailLog.INSTANCE.e(null, TAG, "drawInto(texture)", th);
        }
    }

    // ---------------------------------------------------------------
    // Pixel helpers
    // ---------------------------------------------------------------
    static Bitmap centerCropScale(Bitmap src, int dw, int dh) {
        RectF crop = centerCropRect(src.getWidth(), src.getHeight(), (float) dw / dh);
        Bitmap cropped = Bitmap.createBitmap(src, (int) crop.left, (int) crop.top,
                (int) (crop.right - crop.left), (int) (crop.bottom - crop.top));
        Bitmap scaled = Bitmap.createScaledBitmap(cropped, dw, dh, true);
        if (scaled != cropped) cropped.recycle();
        return scaled;
    }

    private static RectF centerCropRect(int sw, int sh, float dstAspect) {
        float srcAspect = (float) sw / sh;
        float w = sw, h = sh, x = 0, y = 0;
        if (srcAspect > dstAspect) {
            w = sh * dstAspect;
            x = (sw - w) / 2f;
        } else if (srcAspect < dstAspect) {
            h = sw / dstAspect;
            y = (sh - h) / 2f;
        }
        return new RectF(x, y, x + w, y + h);
    }

    /** ARGB bitmap → NV21. Standard BT.601 conversion. */
    static byte[] argbToNv21(Bitmap bmp, int w, int h) {
        int[] argb = new int[w * h];
        bmp.getPixels(argb, 0, w, 0, 0, w, h);
        byte[] nv21 = new byte[w * h * 3 / 2];
        int yIndex = 0;
        int uvIndex = w * h;
        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {
                int p = argb[j * w + i];
                int r = (p >> 16) & 0xFF;
                int g = (p >> 8) & 0xFF;
                int b = p & 0xFF;
                int y = ((66 * r + 129 * g + 25 * b + 128) >> 8) + 16;
                nv21[yIndex++] = (byte) Math.max(0, Math.min(255, y));
                if ((j % 2 == 0) && (i % 2 == 0)) {
                    int u = ((-38 * r - 74 * g + 112 * b + 128) >> 8) + 128;
                    int v = ((112 * r - 94 * g - 18 * b + 128) >> 8) + 128;
                    nv21[uvIndex++] = (byte) Math.max(0, Math.min(255, v)); // V first
                    nv21[uvIndex++] = (byte) Math.max(0, Math.min(255, u));
                }
            }
        }
        return nv21;
    }
}
