package com.kail.location.inject.utils;

import java.io.File;

/**
 * Inject-side bridge to {@code libkail_native_hook.so} (native_hook/hook.cpp),
 * used for ROOT-mode step/gait simulation.
 *
 * Unlike the FakeLocation-native {@code libStepSensor} (which crashed
 * SensorService via fragile ELF PLT hooking), this library uses Dobby inline
 * hooks on {@code libsensorservice.so::convertToSensorEvent} and drives a
 * realistic {@code gait::SensorSimulator}. It is loaded INSIDE system_server by
 * {@link com.kail.location.inject.fakelocation.InjectDex} (system_server maps
 * libsensorservice.so) so the synthetic step-detector/counter events flow into
 * the global sensor stream.
 *
 * The JNI symbols are exported from hook.cpp under
 * {@code Java_com_kail_location_inject_utils_NativeStepHook_*}.
 */
public final class NativeStepHook {

    private static final String TAG = "NativeStepHook";
    private static final String HOOK_SO_DIR = "/data/kail-loc";
    private static final String LEGACY_SO_PATH = "/data/kail-loc/libkail_native_hook.so";

    private static volatile boolean loaded = false;
    private static volatile boolean hookInstalled = false;
    private static volatile boolean routeChannelArmed = false;
    private static volatile float lastSpm = 120.0f;
    private static volatile int lastMode = 0;
    private static volatile int lastScheme = 0;

    private NativeStepHook() {}

    public static native void nativeSetWriteOffset(long offset);
    public static native void nativeSetConvertOffset(long offset);
    public static native void nativeSetRouteSimulation(boolean active, float spm, int mode);
    public static native void nativeSetGaitParams(float spm, int mode, int scheme, boolean enable);
    public static native void nativeSetStepSensorHandles(int counterHandle, int detectorHandle);
    public static native void nativeSetMocking(int mocking);
    public static native void nativeSetStepSimEnabled(boolean enabled);
    public static native long nativeGetStepSynthEvents();
    public static native int nativeGetHookState();
    public static native void nativeReset();
    public static native boolean nativeInitHook();

    /** Load the SO once. Safe to call repeatedly. */
    public static synchronized boolean ensureLoaded() {
        if (loaded) return true;
        String soPath = resolveSoPath();
        try {
            System.load(soPath);
            loaded = true;
            InjectLog.i(TAG, "loaded " + soPath);
        } catch (Throwable t) {
            InjectLog.e(TAG, "load failed: " + soPath, t);
        }
        return loaded;
    }

    private static String resolveSoPath() {
        try {
            File dir = new File(HOOK_SO_DIR);
            File[] files = dir.listFiles((d, name) ->
                name.startsWith("libkail_native_hook_v") && name.endsWith(".so"));
            if (files != null && files.length > 0) {
                String path = files[0].getAbsolutePath();
                InjectLog.i(TAG, "resolved " + path);
                return path;
            }
        } catch (Throwable t) {
            InjectLog.w(TAG, "resolve so path failed: " + t.getMessage());
        }
        return LEGACY_SO_PATH;
    }

    /**
     * Install the sensor hook in the current process (system_server) using the
     * given libsensor.so / libsensorservice.so symbol offsets, then arm the
     * gait simulator at the requested cadence.
     *
     * @param writeOffset   send_objects offset in libsensor.so (may be 0)
     * @param convertOffset convertToSensorEvent offset in libsensorservice.so
     * @param spm           steps per minute
     */
    public static synchronized boolean install(long writeOffset, long convertOffset, float spm) {
        if (!ensureLoaded()) return false;
        if (hookInstalled) return true;
        try {
            if (writeOffset != 0) nativeSetWriteOffset(writeOffset);
            if (convertOffset != 0) nativeSetConvertOffset(convertOffset);
            boolean ok = nativeInitHook();
            hookInstalled = hookInstalled || ok;
            InjectLog.i(TAG, "nativeInitHook ok=" + ok + " write=0x" + Long.toHexString(writeOffset)
                    + " convert=0x" + Long.toHexString(convertOffset));
            return ok;
        } catch (Throwable t) {
            InjectLog.e(TAG, "install failed", t);
            return false;
        }
    }

    public static synchronized void start(float spm, int mode, int scheme) {
        if (!loaded) return;
        try {
            lastSpm = spm;
            lastMode = mode;
            lastScheme = scheme;
            nativeSetRouteSimulation(true, spm, mode);
            nativeSetGaitParams(spm, mode, scheme, true);
            nativeSetStepSimEnabled(true);
            routeChannelArmed = true;
            nativeSetMocking(1);
            InjectLog.i(TAG, "step sim started spm=" + spm + " mode=" + mode + " scheme=" + scheme);
        } catch (Throwable t) {
            InjectLog.e(TAG, "start failed", t);
        }
    }

    public static synchronized void configureStepSensorHandles(int counterHandle, int detectorHandle) {
        if (!loaded) return;
        try {
            nativeSetStepSensorHandles(counterHandle, detectorHandle);
            InjectLog.i(TAG, "step handles configured counter=" + counterHandle
                    + " detector=" + detectorHandle);
        } catch (Throwable t) {
            InjectLog.e(TAG, "configureStepSensorHandles failed", t);
        }
    }

    public static long getStepSynthEvents() {
        if (!loaded) return 0L;
        try {
            return nativeGetStepSynthEvents();
        } catch (Throwable t) {
            InjectLog.e(TAG, "getStepSynthEvents failed", t);
            return 0L;
        }
    }

    public static int getHookState() {
        if (!loaded) return 0;
        try {
            return nativeGetHookState();
        } catch (Throwable t) {
            InjectLog.e(TAG, "getHookState failed", t);
            return 0;
        }
    }

    public static boolean isSendObjectsHookInstalled() {
        return (getHookState() & 1) != 0;
    }

    public static boolean isConvertHookInstalled() {
        return (getHookState() & 2) != 0;
    }

    public static synchronized void stop() {
        if (!loaded) return;
        try {
            nativeSetStepSimEnabled(false);
            nativeSetMocking(0);
            if (routeChannelArmed) {
                nativeSetRouteSimulation(false, lastSpm, lastMode);
            }
            nativeReset();
            routeChannelArmed = false;
            InjectLog.i(TAG, "step sim stopped");
        } catch (Throwable t) {
            InjectLog.e(TAG, "stop failed", t);
        }
    }

    public static boolean isHookInstalled() {
        return hookInstalled;
    }
}
