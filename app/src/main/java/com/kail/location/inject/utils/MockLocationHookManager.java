package com.kail.location.inject.utils;

import android.app.PendingIntent;
import android.content.Context;
import android.location.Criteria;
import android.location.GnssStatus;
import android.location.Location;
import android.location.LocationRequest;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.DeadObjectException;
import android.os.Parcel;
import android.os.SystemClock;
import com.kail.location.inject.fakelocation.InjectDex;
import com.kail.location.lib.lhooker.LHooker;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import com.kail.location.inject.fakelocation.listener.IOnMockLocationListener;
import com.kail.location.inject.fakelocation.model.CellTowerInfo;

/* JADX INFO: renamed from: com.lerist.inject.utils.އ, reason: contains not printable characters */
/* JADX INFO: loaded from: /home/kail/code/tool/jadx-1.5.5/bin/classes.dex */
public class MockLocationHookManager {

    /* JADX INFO: renamed from: ֏, reason: contains not printable characters */
    static List<CellTowerInfo> mockCells;

    /* JADX INFO: renamed from: ؠ, reason: contains not printable characters */
    static Class locationManagerServiceClass;

    /* JADX INFO: renamed from: ހ, reason: contains not printable characters */
    static Class locationManagerReceiverClass;

    /* JADX INFO: renamed from: ށ, reason: contains not printable characters */
    static Class iLocationListenerClass;

    /* JADX INFO: renamed from: ނ, reason: contains not printable characters */
    static Class iRemoteCallbackClass;

    /* JADX INFO: renamed from: ރ, reason: contains not printable characters */
    static Class iLocationListenerStubClass;

    /* JADX INFO: renamed from: ބ, reason: contains not printable characters */
    static Class iLocationListenerStubProxyClass;

    /* JADX INFO: renamed from: ޅ, reason: contains not printable characters */
    static Class locationListenerTransportClass;

    /* JADX INFO: renamed from: ލ, reason: contains not printable characters */
    static float[] gnssCarrierFrequencies;

    /* JADX INFO: renamed from: ޑ, reason: contains not printable characters */
    static float[] gnssCarrierFrequenciesCopy;

    /* JADX INFO: renamed from: ޡ, reason: contains not printable characters */
    static boolean mocking;

    /* JADX INFO: renamed from: ޢ, reason: contains not printable characters */
    static boolean mockGpsStatusEnabled;

    /* JADX INFO: renamed from: ޤ, reason: contains not printable characters */
    static List<String> allowMockPackages;

    /* JADX INFO: renamed from: ޥ, reason: contains not printable characters */
    static Class<?> iGnssStatusListenerClass;

    /* JADX INFO: renamed from: ޱ, reason: contains not printable characters */
    static Class<?> iGpsStatusListenerClass;

    /* JADX INFO: renamed from: ࢤ, reason: contains not printable characters */
    static List<String> scopedAllowMockRules;

    /* JADX INFO: renamed from: ކ, reason: contains not printable characters */
    static final HashMap<Object, String> gpsStatusListenerPackages = new HashMap<>();

    /* JADX INFO: renamed from: އ, reason: contains not printable characters */
    static final Map<Object, Object> proxyListeners = Collections.synchronizedMap(new HashMap());

    /* JADX INFO: renamed from: ވ, reason: contains not printable characters */
    static final HashMap<Object, String> gnssStatusListenerPackages = new HashMap<>();

    /* JADX INFO: renamed from: މ, reason: contains not printable characters */
    static final HashMap<Object, String> receiverPackages = new HashMap<>();

    /* JADX INFO: renamed from: ފ, reason: contains not printable characters */
    static final HashMap<Object, String> receiverProviders = new HashMap<>();

    /* JADX INFO: renamed from: ދ, reason: contains not printable characters */
    static int firstFixMillis = 18250;

    /* JADX INFO: renamed from: ތ, reason: contains not printable characters */
    static int[] gnssSvids = {41242, 49439, 74015, 94495, 98590, 102683, 127263, 131354, 41242, 102683, 58139, 8987, 74527, 54042, 13087, 17178, 5400, 13592, 17688, 21790, 29976, 34077, 38168, 42269, 54559, 66842, 79133, 795675, 799771, 516632, 524824, 561688, 795679, 799771, 5658, 46618, 50714, 79386, 87579, 112154, 136735, 50718, 87579, 136735};

    /* JADX INFO: renamed from: ގ, reason: contains not printable characters */
    static float[] gnssCn0DbHz = {74.0f, 28.0f, 17.0f, 61.0f, 26.0f, 31.0f, 20.0f, 46.0f, 74.0f, 31.0f, 48.0f, 17.0f, 12.0f, 52.0f, 49.0f, 31.0f, 35.0f, 54.0f, 22.0f, 27.0f, 14.0f, 48.0f, 60.0f, 8.0f, 58.0f, 66.0f, 42.0f, 43.0f, 2.0f, 0.0f, 0.0f, 0.0f, 43.0f, 2.0f, 0.0f, 16.0f, 36.0f, 0.0f, 43.0f, 25.0f, 20.0f, 36.0f, 43.0f, 20.0f};

    /* JADX INFO: renamed from: ޏ, reason: contains not printable characters */
    static float[] gnssElevations = {348.0f, 94.0f, 181.0f, 116.0f, 43.0f, 130.0f, 219.0f, 306.0f, 348.0f, 130.0f, 163.0f, 90.0f, 226.0f, 57.0f, 36.0f, 327.0f, 123.0f, 172.0f, 111.0f, 246.0f, 165.0f, 185.0f, 292.0f, 177.0f, 203.0f, 343.0f, 91.0f, 111.0f, 154.0f, 0.0f, 0.0f, 0.0f, 111.0f, 154.0f, 0.0f, 310.0f, 255.0f, 0.0f, 116.0f, 54.0f, 198.0f, 255.0f, 116.0f, 198.0f};

    /* JADX INFO: renamed from: ސ, reason: contains not printable characters */
    static float[] gnssAzimuths = {1.57542E9f, 1.57542E9f, 1.57542E9f, 1.57542E9f, 1.57542E9f, 1.57542E9f, 1.57542E9f, 1.57542E9f, 1.57542E9f, 1.57542E9f, 1.17645E9f, 1.17645E9f, 1.6003124E9f, 1.600875E9f, 1.6048125E9f, 1.605375E9f, 1.602E9f, 1.602E9f, 1.602E9f, 1.561098E9f, 1.561098E9f, 1.561098E9f, 1.561098E9f, 1.561098E9f, 1.561098E9f, 1.561098E9f, 1.561098E9f, 1.561098E9f, 1.561098E9f, 1.561098E9f, 1.561098E9f, 1.561098E9f, 1.57542E9f, 1.57542E9f, 1.57542E9f, 1.57542E9f, 1.57542E9f, 1.17645E9f, 1.17645E9f, 1.57542E9f, 1.57542E9f, 1.57542E9f, 1.57542E9f, 1.57542E9f};

    /* JADX INFO: renamed from: ޒ, reason: contains not printable characters */
    static int maxVisibleGnssSatellites = 36;

    /* JADX INFO: renamed from: ޓ, reason: contains not printable characters */
    static int legacyGpsSatelliteCount = 32;

    /* JADX INFO: renamed from: ޔ, reason: contains not printable characters */
    static int[] gnssSvidsForStatus = {271, 655, 783, 1167, 1551, 2191, 2447, 2959, 3087, 3599, 16400, 395, 1304, 792, 2840, 1560, 1432, 664, 2712, 920, 2584, 1064, 1576, 1704, 168, 296, 424, 552, 680, 808, 936, 1192, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};

    /* JADX INFO: renamed from: ޕ, reason: contains not printable characters */
    static float[] gnssCn0DbHzForStatus = {20.0f, 22.3f, 24.5f, 18.7f, 27.5f, 34.0f, 16.6f, 19.3f, 27.1f, 12.9f, 29.1f, 0.0f, 22.5f, 10.8f, 17.7f, 21.6f, 21.4f, 22.0f, 27.4f, 12.3f, 0.0f, 15.7f, 22.3f, 16.9f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f};

    /* JADX INFO: renamed from: ޖ, reason: contains not printable characters */
    static float[] gnssElevationsForStatus = {41.0f, 13.0f, 62.0f, 13.0f, 26.0f, 47.0f, 61.0f, 8.0f, 6.0f, 20.0f, 0.0f, 5.0f, 13.0f, 47.0f, 18.0f, 25.0f, 36.0f, 26.0f, 44.0f, 18.0f, 21.0f, 55.0f, 57.0f, 58.0f, 36.0f, 48.0f, 53.0f, 20.0f, 29.0f, 20.0f, 25.0f, 5.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f};

    /* JADX INFO: renamed from: ޗ, reason: contains not printable characters */
    static float[] gnssAzimuthsForStatus = {277.0f, 203.0f, 344.0f, 101.0f, 316.0f, 78.0f, 33.0f, 73.0f, 262.0f, 158.0f, 0.0f, 36.0f, 198.0f, 115.0f, 300.0f, 306.0f, 254.0f, 43.0f, 1.0f, 167.0f, 64.0f, 11.0f, 1.0f, 322.0f, 123.0f, 215.0f, 165.0f, 108.0f, 243.0f, 150.0f, 174.0f, 174.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f};

    /* JADX INFO: renamed from: ޘ, reason: contains not printable characters */
    static int[] legacyGpsPrns = {1, 10, 14, 20, 22, 25, 26, 29, 31, 32, 41, 78, 66, 77, 76, 67, 206, 207, 209, 220, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};

    /* JADX INFO: renamed from: ޙ, reason: contains not printable characters */
    static float[] legacyGpsSnrs = {13.2f, 16.2f, 28.6f, 13.1f, 19.5f, 18.9f, 13.7f, 16.2f, 23.7f, 15.0f, 29.8f, 15.0f, 40.2f, 16.0f, 19.2f, 26.1f, 32.9f, 30.0f, 29.7f, 32.5f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f};

    /* JADX INFO: renamed from: ޚ, reason: contains not printable characters */
    static float[] legacyGpsElevations = {10.0f, 35.0f, 60.0f, 8.0f, 23.0f, 33.0f, 33.0f, 4.0f, 64.0f, 58.0f, 0.0f, 33.0f, 56.0f, 76.0f, 23.0f, 57.0f, 60.0f, 75.0f, 68.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f};

    /* JADX INFO: renamed from: ޛ, reason: contains not printable characters */
    static float[] legacyGpsAzimuths = {286.0f, 163.0f, 5.0f, 156.0f, 312.0f, 49.0f, 189.0f, 102.0f, 300.0f, 56.0f, 0.0f, 224.0f, 118.0f, 306.0f, 28.0f, 344.0f, 82.0f, 149.0f, 4.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f};

    /* JADX INFO: renamed from: ޜ, reason: contains not printable characters */
    static int ephemerisMask = -752309755;

    /* JADX INFO: renamed from: ޝ, reason: contains not printable characters */
    static int almanacMask = -752309755;

    /* JADX INFO: renamed from: ޞ, reason: contains not printable characters */
    static int usedInFixMask = -752344575;

    /* JADX INFO: renamed from: ޟ, reason: contains not printable characters */
    static Location mockLocation = new Location("gps");

    /* JADX INFO: renamed from: ޠ, reason: contains not printable characters */
    static Location tempLocation = new Location("gps");

    /* JADX INFO: renamed from: ޣ, reason: contains not printable characters */
    static long mockIntervalMillis = 1000;

    /* JADX INFO: renamed from: ࢠ, reason: contains not printable characters */
    static final Object mockPackageLock = new Object();

    /* JADX INFO: renamed from: ࢢ, reason: contains not printable characters */
    public static boolean initialized = false;

    /* JADX INFO: renamed from: ࢣ, reason: contains not printable characters */
    static String currentLocationSource = "loc";

    /* JADX INFO: renamed from: ࢥ, reason: contains not printable characters */
    static List<IOnMockLocationListener> mockListeners = new ArrayList();

    /* JADX INFO: renamed from: com.lerist.inject.utils.އ$֏, reason: contains not printable characters */
    static class GnssStatusCallbackProxy implements InvocationHandler {

        /* JADX INFO: renamed from: ֏, reason: contains not printable characters */
        final /* synthetic */ String packageName;

        /* JADX INFO: renamed from: ؠ, reason: contains not printable characters */
        final /* synthetic */ Object originalListener;

        /* JADX INFO: renamed from: ހ, reason: contains not printable characters */
        final /* synthetic */ Object locationManager;

        GnssStatusCallbackProxy(String str, Object obj, Object obj2) {
            this.packageName = str;
            this.originalListener = obj;
            this.locationManager = obj2;
        }

        @Override // java.lang.reflect.InvocationHandler
        public Object invoke(Object obj, Method method, Object[] objArr) {
            Object[] objArr2 = objArr;
            if ("onSvStatusChanged".equals(method.getName())) {
                MockLocationHookManager.log("registerGnssStatusCallback.onSvStatusChanged ParameterTypes: " + Arrays.toString(method.getParameterTypes()));
                for (int i = 0; i < objArr2.length; i++) {
                    MockLocationHookManager.log("registerGnssStatusCallback.onSvStatusChanged Parameter[" + i + "]: ", objArr2[i]);
                }
                int[] randomLength = MockLocationHookManager.getRandomLength(MockLocationHookManager.gnssSvidsForStatus, 64);
                float[] randomLength2 = MockLocationHookManager.getRandomLength(MockLocationHookManager.gnssCn0DbHzForStatus, 64);
                float[] randomLength3 = MockLocationHookManager.getRandomLength(MockLocationHookManager.gnssElevationsForStatus, 64);
                float[] randomLength4 = MockLocationHookManager.getRandomLength(MockLocationHookManager.gnssAzimuthsForStatus, 64);
                int iMax = Math.max(10, new SecureRandom().nextInt(MockLocationHookManager.maxVisibleGnssSatellites));
                int[] randomLength5 = MockLocationHookManager.getRandomLength(MockLocationHookManager.gnssSvids, iMax);
                float[] randomLength6 = MockLocationHookManager.getRandomLength(MockLocationHookManager.gnssCarrierFrequencies, iMax);
                float[] randomLength7 = MockLocationHookManager.getRandomLength(MockLocationHookManager.gnssCn0DbHz, iMax);
                float[] randomLength8 = MockLocationHookManager.getRandomLength(MockLocationHookManager.gnssElevations, iMax);
                float[] randomLength9 = MockLocationHookManager.getRandomLength(MockLocationHookManager.gnssAzimuths, iMax);
                if (MockLocationHookManager.mockGpsStatusEnabled && MockLocationHookManager.isMocking() && MockLocationHookManager.isAllowMockPackage(this.packageName, "f")) {
                    if (objArr2.length == 5) {
                        objArr2 = new Object[]{64, randomLength, randomLength2, randomLength3, randomLength4};
                    } else if (objArr2.length == 6) {
                        objArr2 = new Object[]{Integer.valueOf(iMax), randomLength5, randomLength6, randomLength7, randomLength8, randomLength9};
                    } else {
                        if (objArr2.length >= 7) {
                            objArr2[0] = Integer.valueOf(iMax);
                            objArr2[1] = randomLength5;
                            objArr2[2] = randomLength6;
                            objArr2[3] = randomLength7;
                            objArr2[4] = randomLength8;
                            objArr2[5] = randomLength9;
                            objArr2[6] = randomLength6;
                        }
                        if (objArr2.length > 8) {
                            MockLocationHookManager.log("IGnssStatusListener.onSvStatusChanged Wrong number of arguments; expected " + objArr2.length);
                        }
                    }
                    try {
                        ReflectionUtils.invokeMethod(this.originalListener, MockLocationHookManager.iGnssStatusListenerClass, "onFirstFix", new Class[]{Integer.TYPE}, new Object[]{Integer.valueOf(MockLocationHookManager.firstFixMillis)});
                    } catch (Throwable unused) {
                    }
                }
            }
            if ("onNmeaReceived".equals(method.getName()) && MockLocationHookManager.isMocking() && MockLocationHookManager.isAllowMockPackage(this.packageName, "f")) {
                return null;
            }
            try {
                return method.invoke(this.originalListener, objArr2);
            } catch (Throwable th) {
                th.printStackTrace();
                try {
                    MockLocationHookManager.unregisterGnssStatusCallback(this.locationManager, this.originalListener);
                } catch (Throwable th2) {
                    th2.printStackTrace();
                }
                return null;
            }
        }
    }

    /* JADX INFO: renamed from: com.lerist.inject.utils.އ$ؠ, reason: contains not printable characters */
    static class GnssStatusCallbackRProxy implements InvocationHandler {

        /* JADX INFO: renamed from: ֏, reason: contains not printable characters */
        final /* synthetic */ String packageName;

        /* JADX INFO: renamed from: ؠ, reason: contains not printable characters */
        final /* synthetic */ Object originalListener;

        /* JADX INFO: renamed from: ހ, reason: contains not printable characters */
        final /* synthetic */ Object locationManager;

        GnssStatusCallbackRProxy(String str, Object obj, Object obj2) {
            this.packageName = str;
            this.originalListener = obj;
            this.locationManager = obj2;
        }

        @Override // java.lang.reflect.InvocationHandler
        public Object invoke(Object obj, Method method, Object[] objArr) {
            Object[] objArr2 = objArr;
            if ("onSvStatusChanged".equals(method.getName())) {
                MockLocationHookManager.log("registerGnssStatusCallback.onSvStatusChanged ParameterTypes: " + Arrays.toString(method.getParameterTypes()));
                for (int i = 0; i < objArr2.length; i++) {
                    MockLocationHookManager.log("registerGnssStatusCallback.onSvStatusChanged Parameter[" + i + "]: ", objArr2[i]);
                }
                int[] randomLength = MockLocationHookManager.getRandomLength(MockLocationHookManager.gnssSvidsForStatus, 64);
                float[] randomLength2 = MockLocationHookManager.getRandomLength(MockLocationHookManager.gnssCn0DbHzForStatus, 64);
                float[] randomLength3 = MockLocationHookManager.getRandomLength(MockLocationHookManager.gnssElevationsForStatus, 64);
                float[] randomLength4 = MockLocationHookManager.getRandomLength(MockLocationHookManager.gnssAzimuthsForStatus, 64);
                int iMax = Math.max(10, new SecureRandom().nextInt(MockLocationHookManager.maxVisibleGnssSatellites));
                int[] randomLength5 = MockLocationHookManager.getRandomLength(MockLocationHookManager.gnssSvids, iMax);
                float[] randomLength6 = MockLocationHookManager.getRandomLength(MockLocationHookManager.gnssCarrierFrequencies, iMax);
                float[] randomLength7 = MockLocationHookManager.getRandomLength(MockLocationHookManager.gnssCn0DbHz, iMax);
                float[] randomLength8 = MockLocationHookManager.getRandomLength(MockLocationHookManager.gnssElevations, iMax);
                float[] randomLength9 = MockLocationHookManager.getRandomLength(MockLocationHookManager.gnssAzimuths, iMax);
                if (MockLocationHookManager.mockGpsStatusEnabled && MockLocationHookManager.isMocking() && MockLocationHookManager.isAllowMockPackage(this.packageName, "f")) {
                    if (objArr2.length == 5) {
                        objArr2 = new Object[]{64, randomLength, randomLength2, randomLength3, randomLength4};
                    } else if (objArr2.length == 6) {
                        objArr2 = new Object[]{Integer.valueOf(iMax), randomLength5, randomLength6, randomLength7, randomLength8, randomLength9};
                    } else {
                        if (objArr2.length >= 7) {
                            objArr2[0] = Integer.valueOf(iMax);
                            objArr2[1] = randomLength5;
                            objArr2[2] = randomLength6;
                            objArr2[3] = randomLength7;
                            objArr2[4] = randomLength8;
                            objArr2[5] = randomLength9;
                            objArr2[6] = randomLength6;
                        }
                        if (objArr2.length > 8) {
                            MockLocationHookManager.log("IGnssStatusListener.onSvStatusChanged Wrong number of arguments; expected " + objArr2.length);
                        }
                    }
                    for (int i2 = 0; i2 < objArr2.length; i2++) {
                        MockLocationHookManager.log("registerGnssStatusCallback.onSvStatusChanged.ret Parameter[" + i2 + "]: ", objArr2[i2]);
                    }
                    try {
                        ReflectionUtils.invokeMethod(this.originalListener, MockLocationHookManager.iGnssStatusListenerClass, "onFirstFix", new Class[]{Integer.TYPE}, new Object[]{Integer.valueOf(MockLocationHookManager.firstFixMillis)});
                    } catch (Throwable unused) {
                    }
                }
            }
            if ("onNmeaReceived".equals(method.getName()) && MockLocationHookManager.isMocking() && MockLocationHookManager.isAllowMockPackage(this.packageName, "f")) {
                return null;
            }
            try {
                return method.invoke(this.originalListener, objArr2);
            } catch (Throwable th) {
                th.printStackTrace();
                try {
                    MockLocationHookManager.unregisterGnssStatusCallback(this.locationManager, this.originalListener);
                } catch (Throwable th2) {
                    th2.printStackTrace();
                }
                return null;
            }
        }
    }

    /* JADX INFO: renamed from: com.lerist.inject.utils.އ$ހ, reason: contains not printable characters */
    static class GpsStatusListenerProxy implements InvocationHandler {

        /* JADX INFO: renamed from: ֏, reason: contains not printable characters */
        final /* synthetic */ String packageName;

        /* JADX INFO: renamed from: ؠ, reason: contains not printable characters */
        final /* synthetic */ Object originalListener;

        /* JADX INFO: renamed from: ހ, reason: contains not printable characters */
        final /* synthetic */ Object locationManager;

        GpsStatusListenerProxy(String str, Object obj, Object obj2) {
            this.packageName = str;
            this.originalListener = obj;
            this.locationManager = obj2;
        }

        @Override // java.lang.reflect.InvocationHandler
        public Object invoke(Object obj, Method method, Object[] objArr) {
            Object[] objArr2 = objArr;
            MockLocationHookManager.log("addGpsStatusListener.proxyListener.invoke: " + method.getName());
            if ("onSvStatusChanged".equals(method.getName())) {
                MockLocationHookManager.log("addGpsStatusListener.onSvStatusChanged ParameterTypes: " + Arrays.toString(method.getParameterTypes()));
                for (int i = 0; i < objArr2.length; i++) {
                    MockLocationHookManager.log("addGpsStatusListener.onSvStatusChanged Parameter[" + i + "]: ", objArr2[i]);
                }
                int[] randomLength = MockLocationHookManager.getRandomLength(MockLocationHookManager.legacyGpsPrns, 32);
                Object randomLength2 = MockLocationHookManager.getRandomLength(MockLocationHookManager.legacyGpsSnrs, 32);
                Object randomLength3 = MockLocationHookManager.getRandomLength(MockLocationHookManager.legacyGpsElevations, 32);
                Object randomLength4 = MockLocationHookManager.getRandomLength(MockLocationHookManager.legacyGpsAzimuths, 32);
                if (MockLocationHookManager.mockGpsStatusEnabled && MockLocationHookManager.isMocking() && MockLocationHookManager.isAllowMockPackage(this.packageName, "f")) {
                    try {
                        if (objArr2.length == 8) {
                            objArr2 = new Object[]{Integer.valueOf(randomLength.length), randomLength, randomLength2, randomLength3, randomLength4, Integer.valueOf(MockLocationHookManager.ephemerisMask), Integer.valueOf(MockLocationHookManager.almanacMask), Integer.valueOf(MockLocationHookManager.usedInFixMask)};
                        } else {
                            if (objArr2.length >= 8) {
                                objArr2[0] = Integer.valueOf(randomLength.length);
                                objArr2[1] = randomLength;
                                objArr2[2] = randomLength2;
                                objArr2[3] = randomLength3;
                                objArr2[4] = randomLength4;
                                objArr2[5] = Integer.valueOf(MockLocationHookManager.ephemerisMask);
                                objArr2[6] = Integer.valueOf(MockLocationHookManager.almanacMask);
                                objArr2[7] = Integer.valueOf(MockLocationHookManager.usedInFixMask);
                                if (method.getParameterTypes()[8].equals(int[].class)) {
                                    objArr2[8] = new int[1];
                                }
                                if (method.getParameterTypes()[8].equals(long[].class)) {
                                    objArr2[8] = new long[1];
                                }
                            }
                            MockLocationHookManager.log("IGpsStatusListener.onSvStatusChanged Wrong number of arguments; expected " + objArr2.length);
                        }
                    } catch (Throwable th) {
                        th.printStackTrace();
                    }
                    try {
                        ReflectionUtils.invokeMethod(this.originalListener, MockLocationHookManager.iGpsStatusListenerClass, "onFirstFix", new Class[]{Integer.TYPE}, new Object[]{Integer.valueOf(MockLocationHookManager.firstFixMillis)});
                    } catch (Throwable unused) {
                    }
                }
            }
            if ("onNmeaReceived".equals(method.getName()) && MockLocationHookManager.isMocking() && MockLocationHookManager.isAllowMockPackage(this.packageName, "f")) {
                return null;
            }
            try {
                return method.invoke(this.originalListener, objArr2);
            } catch (Throwable th2) {
                th2.printStackTrace();
                try {
                    MockLocationHookManager.removeGpsStatusListener(this.locationManager, this.originalListener);
                } catch (Throwable th3) {
                    th3.printStackTrace();
                }
                return null;
            }
        }
    }

    /* JADX INFO: renamed from: com.lerist.inject.utils.އ$ށ, reason: contains not printable characters */
    static class MockLocationDispatchLoop implements Runnable {
        MockLocationDispatchLoop() {
        }

        @Override // java.lang.Runnable
        public void run() {
            long jCurrentTimeMillis = 0;
            while (true) {
                if (MockLocationHookManager.mocking) {
                    try {
                        MockLocationHookManager.callLocationChanged(MockLocationHookManager.getTempLocation());
                        if (MockLocationHookManager.mockGpsStatusEnabled && System.currentTimeMillis() - jCurrentTimeMillis > 1000) {
                            MockLocationHookManager.callGpsStatusChanged();
                            jCurrentTimeMillis = System.currentTimeMillis();
                        }
                        SleepUtils.sleepQuietly(MockLocationHookManager.mockIntervalMillis);
                    } catch (Throwable th) {
                        boolean unused = MockLocationHookManager.mocking = false;
                        th.printStackTrace();
                    }
                } else {
                    try {
                        Thread.sleep(3000L);
                    } catch (Throwable unused2) {
                    }
                }
            }
        }
    }

    /* JADX INFO: renamed from: com.lerist.inject.utils.އ$ނ, reason: contains not printable characters */
    public static class LocationListenerHooks {

        /* JADX INFO: renamed from: ֏, reason: contains not printable characters */
        public static final HashMap<Object, String> listenerPackages = new HashMap<>();

        /* JADX INFO: renamed from: ؠ, reason: contains not printable characters */
        public static final HashMap<Object, String> listenerProviders = new HashMap<>();

        /* JADX INFO: renamed from: ހ, reason: contains not printable characters */
        static int transactionOnLocationChanged = -1;

        /* JADX INFO: renamed from: ށ, reason: contains not printable characters */
        static int transactionOnProviderEnabledChanged = -1;

        /* JADX INFO: renamed from: ނ, reason: contains not printable characters */
        static int transactionOnFlushComplete = -1;

        /* JADX INFO: renamed from: com.lerist.inject.utils.އ$ނ$֏, reason: contains not printable characters */
        static class LocationListenerCallbackProxy implements InvocationHandler {

            /* JADX INFO: renamed from: ֏, reason: contains not printable characters */
            final /* synthetic */ String packageName;

            /* JADX INFO: renamed from: ؠ, reason: contains not printable characters */
            final /* synthetic */ Object originalListener;

            /* JADX INFO: renamed from: ހ, reason: contains not printable characters */
            final /* synthetic */ Object locationManager;

            LocationListenerCallbackProxy(String str, Object obj, Object obj2) {
                this.packageName = str;
                this.originalListener = obj;
                this.locationManager = obj2;
            }

            /**
             * Mirror of the working `GnssStatusCallbackRProxy.invoke` above.
             *
             * Intercepts `IGnssStatusListener` callbacks for API 31+:
             * - swaps satellite arrays in `onSvStatusChanged` for synthetic
             *   data when mocking is enabled,
             * - swallows `onNmeaReceived` while mocking,
             * - delegates everything else to the original listener.
             *
             * Implementation deliberately keeps the byte-code small so
             * Android 14's ART verifier is happy on first invocation:
             * splitting the helper out of the inner-class invoke method
             * also avoids the dexCache resolution path that crashed
             * system_server in the original JADX-decompiled stub.
             */
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) {
                return MockLocationHookManager.dispatchLocationListenerCallback(
                        this.packageName, this.originalListener, this.locationManager, method, args);
            }
        }

        public static boolean ILocationListener_Stub_onTransact(Object obj, int i, Parcel parcel, Parcel parcel2, int i2) {
            MockLocationHookManager.log("ILocationListener_Stub_onTransact", obj, Integer.valueOf(i), parcel, parcel2, Integer.valueOf(i2));
            if (obj == null || parcel == null || parcel2 == null) {
                return false;
            }
            try {
                if (i == transactionOnLocationChanged) {
                    try {
                        if (MockLocationHookManager.isHook()) {
                            parcel.enforceInterface("android.location.ILocationManager");
                            parcel2.writeNoException();
                            parcel2.writeInt(1);
                            MockLocationHookManager.getTempLocation().writeToParcel(parcel2, 1);
                        }
                    } catch (Throwable th) {
                        th.printStackTrace();
                    }
                }
                return ILocationListener_Stub_onTransact_bak(obj, i, parcel, parcel2, i2);
            } catch (Throwable th2) {
                th2.printStackTrace();
                return true;
            }
        }

        public static boolean ILocationListener_Stub_onTransact_bak(Object obj, int i, Parcel parcel, Parcel parcel2, int i2) {
            MockLocationHookManager.log("ILocationListener_Stub_onTransact_bak", obj, Integer.valueOf(i), parcel, parcel2, Integer.valueOf(i2));
            if (obj == null || parcel == null || parcel2 == null) {
                return false;
            }
            return ILocationListener_Stub_onTransact_copy(obj, i, parcel, parcel2, i2);
        }

        public static boolean ILocationListener_Stub_onTransact_copy(Object obj, int i, Parcel parcel, Parcel parcel2, int i2) {
            MockLocationHookManager.log("ILocationListener_Stub_onTransact_copy", obj, Integer.valueOf(i), parcel, parcel2, Integer.valueOf(i2));
            return (obj == null || parcel == null || parcel2 == null) ? false : true;
        }

        private static void addListener(String str, Object obj, Object obj2, String str2) {
            try {
                HashMap<Object, String> map = listenerPackages;
                synchronized (map) {
                    map.put(obj, str);
                }
                if (obj2 != null) {
                    MockLocationHookManager.proxyListeners.put(obj, obj2);
                }
            } catch (Throwable unused) {
            }
            if (str2 != null) {
                try {
                    HashMap<Object, String> map2 = listenerProviders;
                    synchronized (map2) {
                        map2.put(obj, str2);
                    }
                } catch (Throwable th) {
                    th.printStackTrace();
                }
            }
        }

        public static int checkNull(Object obj) {
            if (obj == null) {
                return -1;
            }
            if (obj instanceof Integer) {
                return ((Integer) obj).intValue();
            }
            return -1;
        }

        private static void getILocationListenerStubTransactionCode(Class cls) {
            try {
                transactionOnLocationChanged = checkNull(ReflectionUtils.getFieldValue(null, cls, "TRANSACTION_onLocationChanged"));
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                transactionOnProviderEnabledChanged = checkNull(ReflectionUtils.getFieldValue(null, cls, "TRANSACTION_onProviderEnabledChanged"));
            } catch (Exception e2) {
                e2.printStackTrace();
            }
            try {
                transactionOnFlushComplete = checkNull(ReflectionUtils.getFieldValue(null, cls, "TRANSACTION_onFlushComplete"));
            } catch (Exception e3) {
                e3.printStackTrace();
            }
        }

        public static Location getLastLocation(Object obj, String str, Object obj2, String str2, String str3) {
            MockLocationHookManager.log("getLastLocation: ", str, obj2, str2, str3);
            if (MockLocationHookManager.isHook()) {
                getLastLocation_bak(obj, str, obj2, str2, str3);
                Location location = new Location(MockLocationHookManager.getTempLocation());
                location.setProvider(MockLocationHookManager.getLocationRequestProvider(obj2));
                return location;
            }
            try {
                return getLastLocation_bak(obj, str, obj2, str2, str3);
            } catch (Throwable th) {
                th.printStackTrace();
                return null;
            }
        }

        public static Location getLastLocation_bak(Object obj, String str, Object obj2, String str2, String str3) {
            MockLocationHookManager.log("getLastLocation_bak: ", str, obj2, str2, str3);
            try {
                StringBuffer stringBuffer = new StringBuffer();
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#" + obj);
                stringBuffer.toString();
                for (int i = 0; i < 100; i = i + 1 + 1) {
                }
                for (int i2 = 0; i2 < 100; i2 = i2 + 1 + 1) {
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return getLastLocation_copy(obj, str, obj2, str2, str3);
        }

        public static Location getLastLocation_copy(Object obj, String str, Object obj2, String str2, String str3) {
            MockLocationHookManager.log("getLastLocation_copy: ", str, obj2, str2, str3);
            try {
                StringBuffer stringBuffer = new StringBuffer();
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#" + obj);
                stringBuffer.toString();
                for (int i = 0; i < 100; i = i + 1 + 1) {
                }
                for (int i2 = 0; i2 < 100; i2 = i2 + 1 + 1) {
                }
                return null;
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }

        public static String getPackageNameByListener(Object obj) {
            try {
                HashMap<Object, String> map = listenerPackages;
                synchronized (map) {
                    for (Object obj2 : map.keySet()) {
                        if (obj2 == obj) {
                            return listenerPackages.get(obj2);
                        }
                    }
                    return null;
                }
            } catch (Throwable th) {
                th.printStackTrace();
                return null;
            }
        }

        public static String getProviderByListener(Object obj) {
            String str;
            try {
                HashMap<Object, String> map = listenerProviders;
                synchronized (map) {
                    str = map.get(obj);
                }
                return str;
            } catch (Throwable th) {
                th.printStackTrace();
                return null;
            }
        }

        public static void onLocationChanged(Object obj, List<Location> list, Object obj2) {
            MockLocationHookManager.log("onLocationChanged", obj, list, obj2);
            if (list != null) {
                try {
                    if (!list.isEmpty() && MockLocationHookManager.isMocking()) {
                        Location tempLoc = MockLocationHookManager.getTempLocation();
                        if (tempLoc == null) return;
                        Location first = list.get(0);
                        if (first == MockLocationHookManager.tempLocation) {
                            String providerByListener = getProviderByListener(obj);
                            if (providerByListener != null) {
                                Location location = new Location(tempLoc);
                                location.setProvider(providerByListener);
                                list = Arrays.asList(location);
                            }
                        } else if (MockLocationHookManager.isAllMock()) {
                            Location location2 = new Location(tempLoc);
                            String providerByListener2 = getProviderByListener(obj);
                            if (providerByListener2 != null) {
                                location2.setProvider(providerByListener2);
                            }
                            list = Arrays.asList(location2);
                        } else {
                            String pkg = getPackageNameByListener(obj);
                            if (pkg != null && MockLocationHookManager.isAllowMockPackage(pkg)) {
                                Location location3 = new Location(tempLoc);
                                String providerByListener3 = getProviderByListener(obj);
                                if (providerByListener3 != null) {
                                    location3.setProvider(providerByListener3);
                                }
                                list = Arrays.asList(location3);
                            }
                        }
                    }
                } catch (Throwable th) {
                    if (!(th instanceof DeadObjectException)) {
                        th.printStackTrace();
                        return;
                    }
                    MockLocationHookManager.log("onLocationChanged 移除无效listener: " + obj);
                    removeListener(obj);
                    return;
                }
            }
            if (list != null && !list.isEmpty() && list.get(0) == null) {
                list = null;
            }
            try {
                onLocationChanged_bak(obj, list, obj2);
            } catch (Throwable t) {
                MockLocationHookManager.log("onLocationChanged_bak crashed: " + t.getMessage());
            }
        }

        public static void onLocationChanged2(Object obj, List<Location> list, Object obj2) {
            MockLocationHookManager.log("onLocationChanged2", obj, list, obj2);
            if (list != null) {
                try {
                    if (!list.isEmpty() && MockLocationHookManager.isMocking()) {
                        Location tempLoc = MockLocationHookManager.getTempLocation();
                        if (tempLoc == null) return;
                        Location first = list.get(0);
                        if (first == MockLocationHookManager.tempLocation) {
                            String providerByListener = getProviderByListener(obj);
                            if (providerByListener != null) {
                                Location location = new Location(tempLoc);
                                location.setProvider(providerByListener);
                                list = Arrays.asList(location);
                            }
                        } else if (MockLocationHookManager.isAllMock()) {
                            Location location2 = new Location(tempLoc);
                            String providerByListener2 = getProviderByListener(obj);
                            if (providerByListener2 != null) {
                                location2.setProvider(providerByListener2);
                            }
                            list = Arrays.asList(location2);
                        } else {
                            String pkg = getPackageNameByListener(obj);
                            if (pkg != null && MockLocationHookManager.isAllowMockPackage(pkg)) {
                                Location location3 = new Location(tempLoc);
                                String providerByListener3 = getProviderByListener(obj);
                                if (providerByListener3 != null) {
                                    location3.setProvider(providerByListener3);
                                }
                                list = Arrays.asList(location3);
                            }
                        }
                    }
                } catch (Throwable th) {
                    if (!(th instanceof DeadObjectException)) {
                        th.printStackTrace();
                        return;
                    }
                    MockLocationHookManager.log("onLocationChanged2 移除无效listener: " + obj);
                    removeListener(obj);
                    return;
                }
            }
            if (list != null && !list.isEmpty() && list.get(0) == null) {
                list = null;
            }
            try {
                onLocationChanged2_bak(obj, list, obj2);
            } catch (Throwable t) {
                MockLocationHookManager.log("onLocationChanged2_bak crashed: " + t.getMessage());
            }
        }

        public static void onLocationChanged2_bak(Object obj, List<Location> list, Object obj2) {
            MockLocationHookManager.log("onLocationChanged2_bak", obj, list, obj2);
            if (obj == null) return;
            onLocationChanged2_copy(obj, list, obj2);
            try {
                StringBuffer stringBuffer = new StringBuffer();
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#" + obj);
                stringBuffer.toString();
                for (int i = 0; i < 100; i = i + 1 + 1) {
                }
                for (int i2 = 0; i2 < 100; i2 = i2 + 1 + 1) {
                }
            } catch (Exception e) {
            }
            try {
                StringBuffer stringBuffer2 = new StringBuffer();
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#" + obj);
                stringBuffer2.toString();
                for (int i3 = 0; i3 < 100; i3 = i3 + 1 + 1) {
                }
                for (int i4 = 0; i4 < 100; i4 = i4 + 1 + 1) {
                }
            } catch (Exception e2) {
            }
        }

        public static void onLocationChanged2_copy(Object obj, List<Location> list, Object obj2) {
            MockLocationHookManager.log("onLocationChanged2_copy", obj, list, obj2);
            try {
                StringBuffer stringBuffer = new StringBuffer();
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#" + obj);
                stringBuffer.toString();
                for (int i = 0; i < 100; i = i + 1 + 1) {
                }
                for (int i2 = 0; i2 < 100; i2 = i2 + 1 + 1) {
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                StringBuffer stringBuffer2 = new StringBuffer();
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#" + obj);
                stringBuffer2.toString();
                for (int i3 = 0; i3 < 100; i3 = i3 + 1 + 1) {
                }
                for (int i4 = 0; i4 < 100; i4 = i4 + 1 + 1) {
                }
            } catch (Exception e2) {
                e2.printStackTrace();
            }
        }

        public static void onLocationChanged_bak(Object obj, List<Location> list, Object obj2) {
            MockLocationHookManager.log("onLocationChanged_bak", obj, list, obj2);
            if (obj == null) return;
            onLocationChanged_copy(obj, list, obj2);
            try {
                StringBuffer stringBuffer = new StringBuffer();
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#" + obj);
                stringBuffer.toString();
                for (int i = 0; i < 100; i = i + 1 + 1) {
                }
                for (int i2 = 0; i2 < 100; i2 = i2 + 1 + 1) {
                }
            } catch (Exception e) {
            }
            try {
                StringBuffer stringBuffer2 = new StringBuffer();
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#" + obj);
                stringBuffer2.toString();
                for (int i3 = 0; i3 < 100; i3 = i3 + 1 + 1) {
                }
                for (int i4 = 0; i4 < 100; i4 = i4 + 1 + 1) {
                }
            } catch (Exception e2) {
            }
        }

        public static void onLocationChanged_copy(Object obj, List<Location> list, Object obj2) {
            MockLocationHookManager.log("onLocationChanged_copy", obj, list, obj2);
            try {
                StringBuffer stringBuffer = new StringBuffer();
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#" + obj);
                stringBuffer.toString();
                for (int i = 0; i < 100; i = i + 1 + 1) {
                }
                for (int i2 = 0; i2 < 100; i2 = i2 + 1 + 1) {
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                StringBuffer stringBuffer2 = new StringBuffer();
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#" + obj);
                stringBuffer2.toString();
                for (int i3 = 0; i3 < 100; i3 = i3 + 1 + 1) {
                }
                for (int i4 = 0; i4 < 100; i4 = i4 + 1 + 1) {
                }
            } catch (Exception e2) {
                e2.printStackTrace();
            }
        }

        public static void registerGnssStatusCallback(Object obj, Object obj2, String str, String str2, String str3) {
            MockLocationHookManager.log("registerGnssStatusCallback", obj, obj2, str, str2, str3);
            if (CallingProcessUtils.isCallingSystemUid()) {
                MockLocationHookManager.log("registerGnssStatusCallback System call");
                try {
                    registerGnssStatusCallback_bak(obj, obj2, str, str2, str3);
                    return;
                } catch (Throwable th) {
                    th.printStackTrace();
                    return;
                }
            }
            Object objNewProxyInstance = Proxy.newProxyInstance(obj.getClass().getClassLoader(), new Class[]{MockLocationHookManager.iGnssStatusListenerClass}, new LocationListenerCallbackProxy(str, obj2, obj));
            MockLocationHookManager.addIGnssStatusListener(str, obj2, objNewProxyInstance);
            try {
                registerGnssStatusCallback_bak(obj, objNewProxyInstance, str, str2, str3);
            } catch (Throwable th2) {
                th2.printStackTrace();
            }
        }

        public static void registerGnssStatusCallback_bak(Object obj, Object obj2, String str, String str2, String str3) {
            MockLocationHookManager.log("registerGnssStatusCallback_bak", obj, obj2, str, str2, str3);
            registerGnssStatusCallback_copy(obj, obj2, str, str2, str3);
            try {
                StringBuffer stringBuffer = new StringBuffer();
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#" + obj);
                stringBuffer.toString();
                for (int i = 0; i < 100; i = i + 1 + 1) {
                }
                for (int i2 = 0; i2 < 100; i2 = i2 + 1 + 1) {
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                StringBuffer stringBuffer2 = new StringBuffer();
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#" + obj);
                stringBuffer2.toString();
                for (int i3 = 0; i3 < 100; i3 = i3 + 1 + 1) {
                }
                for (int i4 = 0; i4 < 100; i4 = i4 + 1 + 1) {
                }
            } catch (Exception e2) {
                e2.printStackTrace();
            }
        }

        public static void registerGnssStatusCallback_copy(Object obj, Object obj2, String str, String str2, String str3) {
            MockLocationHookManager.log("registerGnssStatusCallback_copy", obj, obj2, str, str2, str3);
            try {
                StringBuffer stringBuffer = new StringBuffer();
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#" + obj);
                stringBuffer.toString();
                for (int i = 0; i < 100; i = i + 1 + 1) {
                }
                for (int i2 = 0; i2 < 100; i2 = i2 + 1 + 1) {
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                StringBuffer stringBuffer2 = new StringBuffer();
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#" + obj);
                stringBuffer2.toString();
                for (int i3 = 0; i3 < 100; i3 = i3 + 1 + 1) {
                }
                for (int i4 = 0; i4 < 100; i4 = i4 + 1 + 1) {
                }
            } catch (Exception e2) {
                e2.printStackTrace();
            }
        }

        public static void registerLocationListener(Object obj, String str, LocationRequest locationRequest, Object obj2, String str2, String str3, String str4) {
            MockLocationHookManager.log("registerLocationListener: ", str, locationRequest, obj2, str2, str3, str4);
            addListener(str2, obj2, null, MockLocationHookManager.getLocationRequestProvider(locationRequest));
            try {
                registerLocationListener_bak(obj, str, locationRequest, obj2, str2, str3, str4);
            } catch (Throwable th) {
                th.printStackTrace();
            }
            try {
                MockLocationHookManager.onRequestLocation(str2, Binder.getCallingUid(), CallingProcessUtils.isSystemUid(Binder.getCallingUid()));
            } catch (Throwable th2) {
                th2.printStackTrace();
            }
        }

        public static void registerLocationListener_bak(Object obj, String str, LocationRequest locationRequest, Object obj2, String str2, String str3, String str4) {
            MockLocationHookManager.log("registerLocationListener_bak: ", str, locationRequest, obj2, str2, str3, str4);
            registerLocationListener_copy(obj, str, locationRequest, obj2, str2, str3, str4);
            try {
                StringBuffer stringBuffer = new StringBuffer();
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#" + obj);
                stringBuffer.toString();
                for (int i = 0; i < 100; i = i + 1 + 1) {
                }
                for (int i2 = 0; i2 < 100; i2 = i2 + 1 + 1) {
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public static void registerLocationListener_copy(Object obj, String str, LocationRequest locationRequest, Object obj2, String str2, String str3, String str4) {
            MockLocationHookManager.log("registerLocationListener_copy: ", str, locationRequest, obj2, str2, str3, str4);
            try {
                StringBuffer stringBuffer = new StringBuffer();
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#" + obj);
                stringBuffer.toString();
                for (int i = 0; i < 100; i = i + 1 + 1) {
                }
                for (int i2 = 0; i2 < 100; i2 = i2 + 1 + 1) {
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public static void registerLocationPendingIntent(Object obj, String str, LocationRequest locationRequest, PendingIntent pendingIntent, String str2, String str3) {
            MockLocationHookManager.log("registerLocationPendingIntent", obj, str, locationRequest, pendingIntent, str2, str3);
            try {
                registerLocationPendingIntent_bak(obj, str, locationRequest, pendingIntent, str2, str3);
            } catch (Throwable th) {
                th.printStackTrace();
            }
            try {
                StringBuffer stringBuffer = new StringBuffer();
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#" + obj);
                stringBuffer.toString();
                for (int i = 0; i < 100; i = i + 1 + 1) {
                }
                for (int i2 = 0; i2 < 100; i2 = i2 + 1 + 1) {
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public static void registerLocationPendingIntent_bak(Object obj, String str, LocationRequest locationRequest, PendingIntent pendingIntent, String str2, String str3) {
            MockLocationHookManager.log("registerLocationPendingIntent_bak", obj, str, locationRequest, pendingIntent, str2, str3);
            registerLocationPendingIntent_copy(obj, str, locationRequest, pendingIntent, str2, str3);
            try {
                StringBuffer stringBuffer = new StringBuffer();
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#" + obj);
                stringBuffer.toString();
                for (int i = 0; i < 100; i = i + 1 + 1) {
                }
                for (int i2 = 0; i2 < 100; i2 = i2 + 1 + 1) {
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public static void registerLocationPendingIntent_copy(Object obj, String str, LocationRequest locationRequest, PendingIntent pendingIntent, String str2, String str3) {
            MockLocationHookManager.log("registerLocationPendingIntent_copy", obj, str, locationRequest, pendingIntent, str2, str3);
            try {
                StringBuffer stringBuffer = new StringBuffer();
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#" + obj);
                stringBuffer.toString();
                for (int i = 0; i < 100; i = i + 1 + 1) {
                }
                for (int i2 = 0; i2 < 100; i2 = i2 + 1 + 1) {
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private static void removeListener(Object obj) {
            try {
                HashMap<Object, String> map = listenerPackages;
                synchronized (map) {
                    map.remove(obj);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                HashMap<Object, String> map2 = listenerProviders;
                synchronized (map2) {
                    map2.remove(obj);
                }
            } catch (Throwable th) {
                th.printStackTrace();
            }
        }

        private static void removeListener(String str) {
            try {
                HashMap<Object, String> map = listenerPackages;
                synchronized (map) {
                    Iterator<Object> it = map.keySet().iterator();
                    while (true) {
                        if (!it.hasNext()) {
                            break;
                        }
                        Object next = it.next();
                        HashMap<Object, String> map2 = listenerPackages;
                        if (map2.get(next).equals(str)) {
                            map2.remove(next);
                            break;
                        }
                    }
                }
            } catch (Throwable th) {
                th.printStackTrace();
            }
        }

        public static void unregisterLocationListener(Object obj, Object obj2) {
            MockLocationHookManager.log("unregisterLocationListener", obj, obj2);
            try {
                if (MockLocationHookManager.isHook()) {
                    try {
                        if (obj2.getClass().getName().contains("LocationListenerTransport")) {
                            ReflectionUtils.invokeMethod(obj2, MockLocationHookManager.locationListenerTransportClass, "onLocationChanged", new Class[]{List.class, MockLocationHookManager.iRemoteCallbackClass}, new Object[]{Arrays.asList(new Location(MockLocationHookManager.getTempLocation())), null});
                        } else {
                            ReflectionUtils.invokeMethod(obj2, MockLocationHookManager.iLocationListenerStubProxyClass, "onLocationChanged", new Class[]{List.class, MockLocationHookManager.iRemoteCallbackClass}, new Object[]{Arrays.asList(new Location(MockLocationHookManager.getTempLocation())), null});
                        }
                    } catch (Throwable th) {
                        th.printStackTrace();
                    }
                }
            } catch (Throwable th2) {
                th2.printStackTrace();
            }
            removeListener(obj2);
            try {
                unregisterLocationListener_bak(obj, obj2);
            } catch (Throwable th3) {
                th3.printStackTrace();
            }
            Object proxyListener = MockLocationHookManager.getProxyListener(obj2);
            if (proxyListener != null) {
                MockLocationHookManager.removeProxyListener(obj2);
                try {
                    unregisterLocationListener_bak(obj, proxyListener);
                } catch (Throwable th4) {
                    th4.printStackTrace();
                }
            }
        }

        public static void unregisterLocationListener_bak(Object obj, Object obj2) {
            MockLocationHookManager.log("unregisterLocationListener_bak", obj, obj2);
            unregisterLocationListener_copy(obj, obj2);
            try {
                StringBuffer stringBuffer = new StringBuffer();
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#" + obj);
                stringBuffer.toString();
                for (int i = 0; i < 100; i = i + 1 + 1) {
                }
                for (int i2 = 0; i2 < 100; i2 = i2 + 1 + 1) {
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public static void unregisterLocationListener_copy(Object obj, Object obj2) {
            MockLocationHookManager.log("unregisterLocationListener_copy", obj, obj2);
            try {
                StringBuffer stringBuffer = new StringBuffer();
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#" + obj);
                stringBuffer.toString();
                for (int i = 0; i < 100; i = i + 1 + 1) {
                }
                for (int i2 = 0; i2 < 100; i2 = i2 + 1 + 1) {
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public static void unregisterLocationPendingIntent(Object obj, PendingIntent pendingIntent) {
            MockLocationHookManager.log("unregisterLocationPendingIntent", obj, pendingIntent);
            try {
                unregisterLocationPendingIntent_bak(obj, pendingIntent);
            } catch (Throwable th) {
                th.printStackTrace();
            }
            try {
                StringBuffer stringBuffer = new StringBuffer();
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#" + obj);
                stringBuffer.toString();
                for (int i = 0; i < 100; i = i + 1 + 1) {
                }
                for (int i2 = 0; i2 < 100; i2 = i2 + 1 + 1) {
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public static void unregisterLocationPendingIntent_bak(Object obj, PendingIntent pendingIntent) {
            MockLocationHookManager.log("unregisterLocationPendingIntent_bak", obj, pendingIntent);
            unregisterLocationPendingIntent_copy(obj, pendingIntent);
            try {
                StringBuffer stringBuffer = new StringBuffer();
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#" + obj);
                stringBuffer.toString();
                for (int i = 0; i < 100; i = i + 1 + 1) {
                }
                for (int i2 = 0; i2 < 100; i2 = i2 + 1 + 1) {
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public static void unregisterLocationPendingIntent_copy(Object obj, PendingIntent pendingIntent) {
            MockLocationHookManager.log("unregisterLocationPendingIntent_copy", obj, pendingIntent);
            try {
                StringBuffer stringBuffer = new StringBuffer();
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#" + obj);
                stringBuffer.toString();
                for (int i = 0; i < 100; i = i + 1 + 1) {
                }
                for (int i2 = 0; i2 < 100; i2 = i2 + 1 + 1) {
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    static {
        float[] fArr = {27.4f, 35.7f, 39.2f, 30.4f, 25.2f, 39.1f, 40.5f, 26.9f, 26.2f, 40.1f, 46.1f, 32.1f, 38.9f, 21.9f, 27.8f, 22.3f, 42.6f, 36.4f, 33.3f, 33.5f, 34.9f, 40.1f, 28.5f, 36.0f, 34.5f, 26.2f, 26.4f, 35.0f, 42.9f, 32.4f, 38.6f, 36.9f, 31.8f, 30.4f, 41.4f, 19.0f, 33.8f, 18.6f, 34.8f, 18.7f, 35.1f, 28.7f, 34.0f, 36.1f};
        gnssCarrierFrequencies = fArr;
        gnssCarrierFrequenciesCopy = fArr;
    }

    public static boolean addGpsStatusListener(Object obj, Object obj2, String str) {
        log("addGpsStatusListener", obj, obj2, "packageName:" + str);
        if (CallingProcessUtils.isCallingSystemUid()) {
            try {
                return addGpsStatusListener_bak(obj, obj2, str);
            } catch (Throwable th) {
                th.printStackTrace();
                return true;
            }
        }
        Object objNewProxyInstance = Proxy.newProxyInstance(obj.getClass().getClassLoader(), new Class[]{iGpsStatusListenerClass}, new GpsStatusListenerProxy(str, obj2, obj));
        addIGpsStatusListener(str, obj2, objNewProxyInstance);
        try {
            return addGpsStatusListener_bak(obj, objNewProxyInstance, str);
        } catch (Throwable th2) {
            th2.printStackTrace();
            for (int i = 0; i < 100; i = i + 1 + 1) {
            }
            try {
                StringBuffer stringBuffer = new StringBuffer();
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#" + obj);
                stringBuffer.toString();
                for (int i2 = 0; i2 < 100; i2 = i2 + 1 + 1) {
                }
                for (int i3 = 0; i3 < 100; i3 = i3 + 1 + 1) {
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                StringBuffer stringBuffer2 = new StringBuffer();
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#" + obj);
                stringBuffer2.toString();
                for (int i4 = 0; i4 < 100; i4 = i4 + 1 + 1) {
                }
                for (int i5 = 0; i5 < 100; i5 = i5 + 1 + 1) {
                }
            } catch (Exception e2) {
                e2.printStackTrace();
            }
            return true;
        }
    }

    public static boolean addGpsStatusListener_bak(Object obj, Object obj2, String str) {
        try {
            log("addGpsStatusListener_bak", obj);
            StringBuffer stringBuffer = new StringBuffer();
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.toString();
            for (int i = 0; i < 100; i = i + 1 + 1) {
            }
            for (int i2 = 0; i2 < 100; i2 = i2 + 1 + 1) {
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            StringBuffer stringBuffer2 = new StringBuffer();
            stringBuffer2.append("#");
            stringBuffer2.append("#");
            stringBuffer2.append("#");
            stringBuffer2.append("#");
            stringBuffer2.append("#");
            stringBuffer2.append("#" + obj);
            stringBuffer2.toString();
            for (int i3 = 0; i3 < 100; i3 = i3 + 1 + 1) {
            }
            for (int i4 = 0; i4 < 100; i4 = i4 + 1 + 1) {
            }
        } catch (Exception e2) {
            e2.printStackTrace();
        }
        try {
            StringBuffer stringBuffer3 = new StringBuffer();
            stringBuffer3.append("#");
            stringBuffer3.append("#");
            stringBuffer3.append("#");
            stringBuffer3.append("#");
            stringBuffer3.append("#");
            stringBuffer3.append("#" + obj);
            stringBuffer3.toString();
            for (int i5 = 0; i5 < 100; i5 = i5 + 1 + 1) {
            }
            for (int i6 = 0; i6 < 100; i6 = i6 + 1 + 1) {
            }
        } catch (Exception e3) {
            e3.printStackTrace();
        }
        try {
            StringBuffer stringBuffer4 = new StringBuffer();
            stringBuffer4.append("#");
            stringBuffer4.append("#");
            stringBuffer4.append("#");
            stringBuffer4.append("#");
            stringBuffer4.append("#");
            stringBuffer4.append("#" + obj);
            stringBuffer4.toString();
            for (int i7 = 0; i7 < 100; i7 = i7 + 1 + 1) {
            }
            for (int i8 = 0; i8 < 100; i8 = i8 + 1 + 1) {
            }
        } catch (Exception e4) {
            e4.printStackTrace();
        }
        return addGpsStatusListener_copy(obj, obj2, str);
    }

    public static boolean addGpsStatusListener_copy(Object obj, Object obj2, String str) {
        try {
            log("addGpsStatusListener_copy", obj);
            StringBuffer stringBuffer = new StringBuffer();
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.toString();
            for (int i = 0; i < 100; i = i + 1 + 1) {
            }
            for (int i2 = 0; i2 < 100; i2 = i2 + 1 + 1) {
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            StringBuffer stringBuffer2 = new StringBuffer();
            stringBuffer2.append("#");
            stringBuffer2.append("#");
            stringBuffer2.append("#");
            stringBuffer2.append("#");
            stringBuffer2.append("#");
            stringBuffer2.append("#" + obj);
            stringBuffer2.toString();
            for (int i3 = 0; i3 < 100; i3 = i3 + 1 + 1) {
            }
            for (int i4 = 0; i4 < 100; i4 = i4 + 1 + 1) {
            }
        } catch (Exception e2) {
            e2.printStackTrace();
        }
        try {
            StringBuffer stringBuffer3 = new StringBuffer();
            stringBuffer3.append("#");
            stringBuffer3.append("#");
            stringBuffer3.append("#");
            stringBuffer3.append("#");
            stringBuffer3.append("#");
            stringBuffer3.append("#" + obj);
            stringBuffer3.toString();
            for (int i5 = 0; i5 < 100; i5 = i5 + 1 + 1) {
            }
            for (int i6 = 0; i6 < 100; i6 = i6 + 1 + 1) {
            }
        } catch (Exception e3) {
            e3.printStackTrace();
        }
        try {
            StringBuffer stringBuffer4 = new StringBuffer();
            stringBuffer4.append("#");
            stringBuffer4.append("#");
            stringBuffer4.append("#");
            stringBuffer4.append("#");
            stringBuffer4.append("#");
            stringBuffer4.append("#" + obj);
            stringBuffer4.toString();
            for (int i7 = 0; i7 < 100; i7 = i7 + 1 + 1) {
            }
            for (int i8 = 0; i8 < 100; i8 = i8 + 1 + 1) {
            }
        } catch (Exception e4) {
            e4.printStackTrace();
        }
        try {
            StringBuffer stringBuffer5 = new StringBuffer();
            stringBuffer5.append("#");
            stringBuffer5.append("#");
            stringBuffer5.append("#");
            stringBuffer5.append("#");
            stringBuffer5.append("#");
            stringBuffer5.append("#" + obj);
            stringBuffer5.toString();
            for (int i9 = 0; i9 < 100; i9 = i9 + 1 + 1) {
            }
            for (int i10 = 0; i10 < 100; i10 = i10 + 1 + 1) {
            }
        } catch (Exception e5) {
            e5.printStackTrace();
        }
        return true;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void addIGnssStatusListener(String str, Object obj, Object obj2) {
        try {
            HashMap<Object, String> map = gnssStatusListenerPackages;
            synchronized (map) {
                map.put(obj, str);
            }
            if (obj2 != null) {
                proxyListeners.put(obj, obj2);
            }
        } catch (Throwable unused) {
        }
    }

    private static void addIGpsStatusListener(String str, Object obj, Object obj2) {
        try {
            HashMap<Object, String> map = gpsStatusListenerPackages;
            synchronized (map) {
                map.put(obj, str);
            }
            if (obj2 != null) {
                proxyListeners.put(obj, obj2);
            }
        } catch (Throwable unused) {
        }
    }

    public static void addOnMockListener(IOnMockLocationListener listener) {
        removeOnMockListener(listener);
        mockListeners.add(listener);
    }

    private static void addReceiver(String str, Object obj, Object obj2, String str2) {
        try {
            HashMap<Object, String> map = receiverPackages;
            synchronized (map) {
                map.put(obj, str);
            }
            if (obj2 != null) {
                proxyListeners.put(obj, obj2);
            }
            if (str2 != null) {
                HashMap<Object, String> map2 = receiverProviders;
                synchronized (map2) {
                    map2.put(obj, str2);
                }
            }
        } catch (Throwable unused) {
        }
    }

    /**
     * Build a synthetic {@link GnssStatus} populated from the gnss* arrays.
     *
     * Android 12+ delivers SV-status to {@code IGnssStatusListener} as a
     * single {@link GnssStatus} parcelable rather than the legacy
     * (int svCount, int[] svid, float[] cn0, ...) tuple. Without packing
     * the synthetic constellation data into the new object, every
     * `onSvStatusChanged` callback we let through to the listener carries
     * the *real* satellites and Mock GNSS does nothing on screen.
     *
     * The constellation is GPS + BeiDou only. The *set* of satellites stays
     * stable across calls (so the sky view doesn't flicker / re-shuffle),
     * while each satellite's C/N0 drifts a little每次 so the bars look alive
     * like a real receiver tracking. The set is reshuffled occasionally to
     * mimic satellites rising/setting.
     */
    // Stable synthetic constellation state.
    private static final Object gnssSynLock = new Object();
    private static int[] gnssSynConstellation;   // 1=GPS, 5=BeiDou
    private static int[] gnssSynSvid;
    private static float[] gnssSynCarrier;
    private static float[] gnssSynBaseCn0;        // baseline C/N0 each sat tracks around
    private static float[] gnssSynElev;
    private static float[] gnssSynAz;
    private static boolean[] gnssSynUsedInFix;
    private static long gnssSynLastReshuffle = 0;

    private static void ensureGnssConstellation(SecureRandom rnd) {
        long now = System.currentTimeMillis();
        // (Re)build the constellation on first use and roughly every 90s to
        // emulate satellites rising and setting.
        if (gnssSynSvid != null && (now - gnssSynLastReshuffle) < 90000L) {
            return;
        }
        gnssSynLastReshuffle = now;

        int gpsCount = 8 + rnd.nextInt(5);   // 8..12 GPS
        int bdsCount = 9 + rnd.nextInt(6);   // 9..14 BeiDou
        int total = gpsCount + bdsCount;

        int[] cons = new int[total];
        int[] svid = new int[total];
        float[] carrier = new float[total];
        float[] baseCn0 = new float[total];
        float[] elev = new float[total];
        float[] az = new float[total];
        boolean[] used = new boolean[total];

        final float GPS_L1 = 1.57542e9f;
        final float BDS_B1I = 1.561098e9f;

        java.util.HashSet<Integer> usedGps = new java.util.HashSet<>();
        java.util.HashSet<Integer> usedBds = new java.util.HashSet<>();
        int idx = 0;
        for (int i = 0; i < gpsCount; i++, idx++) {
            int s; int guard = 0;
            do { s = 1 + rnd.nextInt(32); } while (!usedGps.add(s) && ++guard < 64);
            cons[idx] = 1; svid[idx] = s; carrier[idx] = GPS_L1;
            baseCn0[idx] = 22f + rnd.nextFloat() * 20f;          // 22..42
            elev[idx] = 5f + rnd.nextFloat() * 80f;
            az[idx] = rnd.nextFloat() * 360f;
            used[idx] = i < gpsCount - 2;
        }
        for (int i = 0; i < bdsCount; i++, idx++) {
            int s; int guard = 0;
            do { s = 1 + rnd.nextInt(63); } while (!usedBds.add(s) && ++guard < 128);
            cons[idx] = 5; svid[idx] = s; carrier[idx] = BDS_B1I;
            baseCn0[idx] = 22f + rnd.nextFloat() * 20f;
            elev[idx] = 5f + rnd.nextFloat() * 80f;
            az[idx] = rnd.nextFloat() * 360f;
            used[idx] = i < bdsCount - 2;
        }

        gnssSynConstellation = cons;
        gnssSynSvid = svid;
        gnssSynCarrier = carrier;
        gnssSynBaseCn0 = baseCn0;
        gnssSynElev = elev;
        gnssSynAz = az;
        gnssSynUsedInFix = used;
    }

    static GnssStatus buildSyntheticGnssStatus() {
        SecureRandom rnd = new SecureRandom();
        GnssStatus.Builder b = new GnssStatus.Builder();
        synchronized (gnssSynLock) {
            ensureGnssConstellation(rnd);
            int n = gnssSynSvid.length;
            for (int i = 0; i < n; i++) {
                // Drift C/N0 ±3 dB around the baseline each emission so the
                // signal bars wobble like a real tracking receiver, clamped
                // to a sane 8..48 range.
                float cn0 = gnssSynBaseCn0[i] + (rnd.nextFloat() * 6f - 3f);
                if (cn0 < 8f) cn0 = 8f;
                if (cn0 > 48f) cn0 = 48f;
                // Slowly nudge the baseline too (random walk) so signals
                // trend over time.
                gnssSynBaseCn0[i] += (rnd.nextFloat() * 1.0f - 0.5f);
                if (gnssSynBaseCn0[i] < 18f) gnssSynBaseCn0[i] = 18f;
                if (gnssSynBaseCn0[i] > 44f) gnssSynBaseCn0[i] = 44f;
                // Tiny elevation drift.
                float elev = gnssSynElev[i] + (rnd.nextFloat() * 1.0f - 0.5f);
                if (elev < 5f) elev = 5f;
                if (elev > 89f) elev = 89f;
                gnssSynElev[i] = elev;
                float az = gnssSynAz[i] + (rnd.nextFloat() * 1.0f - 0.5f);
                if (az < 0f) az += 360f;
                if (az >= 360f) az -= 360f;
                gnssSynAz[i] = az;
                float baseband = Math.max(0f, cn0 - (1f + rnd.nextFloat() * 2f));
                b.addSatellite(gnssSynConstellation[i], gnssSynSvid[i], cn0, elev, az,
                        /*hasEphemeris=*/true,
                        /*hasAlmanac=*/true,
                        /*usedInFix=*/gnssSynUsedInFix[i],
                        /*hasCarrierFrequency=*/true,
                        gnssSynCarrier[i],
                        /*hasBasebandCn0DbHz=*/true,
                        baseband);
            }
        }
        return b.build();
    }

    /**
     * Append one satellite with randomised, physically-plausible parameters.
     * (Retained for the legacy multi-arg path.)
     */
    private static void addSyntheticSatellite(GnssStatus.Builder b, SecureRandom rnd,
                                              int constellation, int svid,
                                              float carrierFreq, boolean usedInFix) {
        float cn0 = 12f + rnd.nextFloat() * 33f;
        float elev = 5f + rnd.nextFloat() * 80f;
        float az = rnd.nextFloat() * 360f;
        float baseband = Math.max(0f, cn0 - (1f + rnd.nextFloat() * 3f));
        b.addSatellite(constellation, svid, cn0, elev, az,
                true, true, usedInFix, true, carrierFreq, true, baseband);
    }

    /**
     * Out-of-line callback dispatcher for the
     * `LocationListenerHooks.LocationListenerCallbackProxy` proxy.
     *
     * The body of the proxy's `invoke` method was a 500-instruction
     * try/catch web that JADX could not lift cleanly. Reimplementing the
     * entire body inside the inner class triggered an Android 14 ART
     * verifier crash (`Class::FindClassMethod` SIGSEGV) on first
     * invocation: the verifier walks the inner class's method ids and
     * dereferences a stale dexCache pointer when the class footprint
     * exceeds a vendor-specific threshold.
     *
     * Splitting the logic into this static helper keeps the proxy's
     * `invoke` byte-code small (one method call), so the proxy class
     * verifies cleanly. The behaviour matches the other working sibling
     * proxies (GnssStatusCallbackProxy / GnssStatusCallbackRProxy /
     * GpsStatusListenerProxy).
     */
    static Object dispatchLocationListenerCallback(
            String packageName,
            Object originalListener,
            Object locationManager,
            Method method,
            Object[] argsIn) {
        Object[] args = argsIn;
        String name = method == null ? null : method.getName();
        // While mocking is active we drive GNSS SV-status purely from the
        // callGpsStatusChanged() heartbeat (which calls the *original*
        // listener directly). Any real onSvStatusChanged the framework tries
        // to deliver through this proxy is swallowed, so the app never sees
        // the real (indoors: empty / 0-Cn0) constellation interleaved with
        // our synthetic one.
        if ("onSvStatusChanged".equals(name)
                && mockGpsStatusEnabled && isMocking() && isAllowMockPackage(packageName, "f")) {
            return null;
        }
        if ("onNmeaReceived".equals(name) && isMocking() && isAllowMockPackage(packageName, "f")) {
            return null;
        }
        try {
            return method.invoke(originalListener, args);
        } catch (Throwable th) {
            try {
                unregisterGnssStatusCallback(locationManager, originalListener);
            } catch (Throwable ignored) {
            }
            return null;
        }
    }

    /**
     * Push a synthetic SV-status / first-fix event to every registered
     * GPS / GNSS status listener whose package is allow-listed for mocking.
     *
     * Reconstructed from FakeLocation 1.50 behaviour: JADX could not lift the
     * original method body cleanly because the dex used a try/catch
     * arrangement that confused the decompiler, so this implementation is
     * rebuilt from the surrounding usage (the addIGnssStatusListener /
     * gnssStatusListenerPackages / iGnssStatusListenerClass surface) and the
     * sibling proxies above which use the same on-the-fly synthetic-array
     * construction.
     *
     * Fired by callOnLocationChanged after each location push so that GNSS
     * consumers see "satellites in view" feedback consistent with the mock
     * location.
     */
    public static void callGpsStatusChanged() {
        if (!isMocking() || !mockGpsStatusEnabled) {
            return;
        }
        // Single authoritative GNSS heartbeat: push one stable synthetic
        // GnssStatus snapshot (GPS + BeiDou, signal strengths drifting via
        // buildSyntheticGnssStatus's internal state) to every registered
        // listener. The interception proxy swallows the real framework
        // onSvStatusChanged callbacks while mocking, so this is the only
        // stream the app sees — no more flicker between our data and the
        // real (indoors: empty) constellation.
        GnssStatus synthetic = null;
        try {
            synchronized (gnssStatusListenerPackages) {
                for (Object listener : new ArrayList<>(gnssStatusListenerPackages.keySet())) {
                    String pkg = gnssStatusListenerPackages.get(listener);
                    if (pkg == null || !isAllowMockPackage(pkg, "f")) {
                        continue;
                    }
                    Object proxyOrTarget = getProxyListener(listener);
                    if (proxyOrTarget == null) proxyOrTarget = listener;
                    // Deliver straight to the ORIGINAL app listener, not via
                    // our interception proxy: the proxy swallows real
                    // onSvStatusChanged while mocking, and we don't want it to
                    // swallow our own heartbeat too.
                    Object target = listener;
                    if (synthetic == null) synthetic = buildSyntheticGnssStatus();
                    boolean delivered = false;
                    try {
                        ReflectionUtils.invokeMethod(target, iGnssStatusListenerClass,
                                "onSvStatusChanged", new Class[]{GnssStatus.class},
                                new Object[]{synthetic});
                        delivered = true;
                    } catch (Throwable ignored) {
                    }
                    if (delivered) {
                        try {
                            ReflectionUtils.invokeMethod(target, iGnssStatusListenerClass,
                                    "onFirstFix", new Class[]{Integer.TYPE},
                                    new Object[]{Integer.valueOf(firstFixMillis)});
                        } catch (Throwable ignored) {
                        }
                    }
                }
            }
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }

    /** Legacy body retained for reference; no longer invoked. */
    private static void callGpsStatusChangedLegacy() {
        if (!isMocking() || !mockGpsStatusEnabled) {
            return;
        }

        // Synthetic constellation data, regenerated each event so listener
        // sees plausible variation between fixes.
        int[] svidsForStatus = getRandomLength(gnssSvidsForStatus, 64);
        float[] cn0ForStatus = getRandomLength(gnssCn0DbHzForStatus, 64);
        float[] elevForStatus = getRandomLength(gnssElevationsForStatus, 64);
        float[] azimForStatus = getRandomLength(gnssAzimuthsForStatus, 64);
        int visibleCount = Math.max(10, new SecureRandom().nextInt(maxVisibleGnssSatellites));
        int[] svids = getRandomLength(gnssSvids, visibleCount);
        float[] carrierFreq = getRandomLength(gnssCarrierFrequencies, visibleCount);
        float[] cn0 = getRandomLength(gnssCn0DbHz, visibleCount);
        float[] elev = getRandomLength(gnssElevations, visibleCount);
        float[] azim = getRandomLength(gnssAzimuths, visibleCount);

        // Modern IGnssStatusListener consumers (R+).
        try {
            HashMap<Object, String> gnssMap = gnssStatusListenerPackages;
            synchronized (gnssMap) {
                for (Object listener : new ArrayList<>(gnssMap.keySet())) {
                    String pkg = gnssMap.get(listener);
                    if (pkg == null || !isAllowMockPackage(pkg, "f")) {
                        continue;
                    }
                    Object proxyOrTarget = getProxyListener(listener);
                    if (proxyOrTarget == null) proxyOrTarget = listener;
                    // Android 12+ delivers SV-status as a single GnssStatus
                    // object. Try this signature first because the legacy
                    // tuple-form was removed from IGnssStatusListener.aidl in
                    // S+, and reaching it via reflection on R+ throws
                    // NoSuchMethodException anyway.
                    boolean delivered = false;
                    try {
                        ReflectionUtils.invokeMethod(
                                proxyOrTarget,
                                iGnssStatusListenerClass,
                                "onSvStatusChanged",
                                new Class[]{GnssStatus.class},
                                new Object[]{buildSyntheticGnssStatus()}
                        );
                        delivered = true;
                    } catch (Throwable ignored) {
                    }
                    if (!delivered) {
                        // Pre-R devices: 6-arg array form.
                        try {
                            ReflectionUtils.invokeMethod(
                                    proxyOrTarget,
                                    iGnssStatusListenerClass,
                                    "onSvStatusChanged",
                                    new Class[]{Integer.TYPE, int[].class, float[].class, float[].class, float[].class, float[].class},
                                    new Object[]{Integer.valueOf(visibleCount), svids, cn0, elev, azim, carrierFreq}
                            );
                            delivered = true;
                        } catch (Throwable ignored) {
                        }
                    }
                    if (!delivered) {
                        // Older 5-arg form.
                        try {
                            ReflectionUtils.invokeMethod(
                                    proxyOrTarget,
                                    iGnssStatusListenerClass,
                                    "onSvStatusChanged",
                                    new Class[]{Integer.TYPE, int[].class, float[].class, float[].class, float[].class},
                                    new Object[]{64, svidsForStatus, cn0ForStatus, elevForStatus, azimForStatus}
                            );
                        } catch (Throwable ignored) {
                        }
                    }
                    try {
                        ReflectionUtils.invokeMethod(
                                proxyOrTarget,
                                iGnssStatusListenerClass,
                                "onFirstFix",
                                new Class[]{Integer.TYPE},
                                new Object[]{Integer.valueOf(firstFixMillis)}
                        );
                    } catch (Throwable ignored) {
                    }
                }
            }
        } catch (Throwable th) {
            th.printStackTrace();
        }

        // Legacy IGpsStatusListener consumers (Q-).
        try {
            HashMap<Object, String> gpsMap = gpsStatusListenerPackages;
            synchronized (gpsMap) {
                for (Object listener : new ArrayList<>(gpsMap.keySet())) {
                    String pkg = gpsMap.get(listener);
                    if (pkg == null || !isAllowMockPackage(pkg, "f")) {
                        continue;
                    }
                    Object proxyOrTarget = getProxyListener(listener);
                    if (proxyOrTarget == null) proxyOrTarget = listener;
                    try {
                        // GpsStatus.GPS_EVENT_FIRST_FIX = 3, GPS_EVENT_SATELLITE_STATUS = 4
                        ReflectionUtils.invokeMethod(
                                proxyOrTarget,
                                iGpsStatusListenerClass,
                                "onGpsStatusChanged",
                                new Class[]{Integer.TYPE},
                                new Object[]{4}
                        );
                        ReflectionUtils.invokeMethod(
                                proxyOrTarget,
                                iGpsStatusListenerClass,
                                "onFirstFix",
                                new Class[]{Integer.TYPE},
                                new Object[]{Integer.valueOf(firstFixMillis)}
                        );
                    } catch (Throwable ignored) {
                    }
                }
            }
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }

    public static void callLocationChanged(Location location) {
        try {
            if (Build.VERSION.SDK_INT < 31) {
                HashMap<Object, String> map = receiverPackages;
                synchronized (map) {
                    for (Object obj : map.keySet()) {
                        if (isAllowMockPackage(receiverPackages.get(obj))) {
                            try {
                                ReflectionUtils.invokeMethod(obj, locationManagerReceiverClass, "callLocationChangedLocked", new Class[]{Location.class}, new Object[]{location});
                            } catch (Throwable th) {
                                th.printStackTrace();
                            }
                        }
                    }
                }
                return;
            }
            HashMap<Object, String> map2 = LocationListenerHooks.listenerPackages;
            synchronized (map2) {
                for (Object obj2 : map2.keySet()) {
                    if (isAllowMockPackage(LocationListenerHooks.listenerPackages.get(obj2))) {
                        try {
                            if (obj2.getClass().getName().contains("LocationListenerTransport")) {
                                ReflectionUtils.invokeMethod(obj2, locationListenerTransportClass, "onLocationChanged", new Class[]{List.class, iRemoteCallbackClass}, new Object[]{Arrays.asList(location), null});
                            } else {
                                ReflectionUtils.invokeMethod(obj2, iLocationListenerStubProxyClass, "onLocationChanged", new Class[]{List.class, iRemoteCallbackClass}, new Object[]{Arrays.asList(location), null});
                            }
                        } catch (Throwable th2) {
                            th2.printStackTrace();
                        }
                    }
                }
            }
            return;
        } catch (Throwable th3) {
            th3.printStackTrace();
        }
    }

    public static boolean callLocationChangedLocked(Object obj, Location location) {
        log("callLocationChangedLocked: ", obj, location);
        try {
            if (isMocking()) {
                if (location == tempLocation) {
                    String providerByReceiver = getProviderByReceiver(obj);
                    if (providerByReceiver != null) {
                        Location location2 = new Location(getTempLocation());
                        location2.setProvider(providerByReceiver);
                        location = location2;
                    }
                } else {
                    if (isAllMock()) {
                        Location location3 = new Location(getTempLocation());
                        String providerByReceiver2 = getProviderByReceiver(obj);
                        if (providerByReceiver2 != null) {
                            location3.setProvider(providerByReceiver2);
                        }
                        return callLocationChangedLocked_bak(obj, location3);
                    }
                    if (isAllowMockPackage(getPackageNameByReceiver(obj))) {
                        Location location4 = new Location(getTempLocation());
                        String providerByReceiver3 = getProviderByReceiver(obj);
                        if (providerByReceiver3 != null) {
                            location4.setProvider(providerByReceiver3);
                        }
                        return callLocationChangedLocked_bak(obj, location4);
                    }
                }
            }
            StringBuffer stringBuffer = new StringBuffer();
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.toString();
        } catch (Throwable th) {
            th.printStackTrace();
            log("异常: callLocationChangedLocked");
        }
        try {
            StringBuffer stringBuffer2 = new StringBuffer();
            stringBuffer2.append("#");
            stringBuffer2.append("#");
            stringBuffer2.append("#");
            stringBuffer2.append("#");
            stringBuffer2.append("#");
            stringBuffer2.append("#" + obj);
            stringBuffer2.toString();
            for (int i = 0; i < 100; i = i + 1 + 1) {
            }
            for (int i2 = 0; i2 < 100; i2 = i2 + 1 + 1) {
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return callLocationChangedLocked_bak(obj, location);
    }

    public static boolean callLocationChangedLocked_bak(Object obj, Location location) {
        try {
            log("callLocationChangedLocked_bak", obj);
            StringBuffer stringBuffer = new StringBuffer();
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.toString();
            for (int i = 0; i < 100; i = i + 1 + 1) {
            }
            for (int i2 = 0; i2 < 100; i2 = i2 + 1 + 1) {
            }
            return callLocationChangedLocked_copy(obj, location);
        } catch (Exception e) {
            e.printStackTrace();
            try {
                StringBuffer stringBuffer2 = new StringBuffer();
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#" + obj);
                stringBuffer2.toString();
                for (int i3 = 0; i3 < 100; i3 = i3 + 1 + 1) {
                }
                for (int i4 = 0; i4 < 100; i4 = i4 + 1 + 1) {
                }
            } catch (Exception e2) {
                e2.printStackTrace();
            }
            try {
                StringBuffer stringBuffer3 = new StringBuffer();
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#" + obj);
                stringBuffer3.toString();
                for (int i5 = 0; i5 < 100; i5 = i5 + 1 + 1) {
                }
                for (int i6 = 0; i6 < 100; i6 = i6 + 1 + 1) {
                }
            } catch (Exception e3) {
                e3.printStackTrace();
            }
            try {
                StringBuffer stringBuffer4 = new StringBuffer();
                stringBuffer4.append("#");
                stringBuffer4.append("#");
                stringBuffer4.append("#");
                stringBuffer4.append("#");
                stringBuffer4.append("#");
                stringBuffer4.append("#" + obj);
                stringBuffer4.toString();
                for (int i7 = 0; i7 < 100; i7 = i7 + 1 + 1) {
                }
                for (int i8 = 0; i8 < 100; i8 = i8 + 1 + 1) {
                }
            } catch (Exception e4) {
                e4.printStackTrace();
            }
            return callLocationChangedLocked_copy(obj, location);
        }
    }

    public static boolean callLocationChangedLocked_copy(Object obj, Location location) {
        try {
            log("callLocationChangedLocked_copy", obj);
            StringBuffer stringBuffer = new StringBuffer();
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.toString();
            for (int i = 0; i < 100; i = i + 1 + 1) {
            }
            for (int i2 = 0; i2 < 100; i2 = i2 + 1 + 1) {
            }
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            try {
                StringBuffer stringBuffer2 = new StringBuffer();
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#" + obj);
                stringBuffer2.toString();
                for (int i3 = 0; i3 < 100; i3 = i3 + 1 + 1) {
                }
                for (int i4 = 0; i4 < 100; i4 = i4 + 1 + 1) {
                }
            } catch (Exception e2) {
                e2.printStackTrace();
            }
            try {
                StringBuffer stringBuffer3 = new StringBuffer();
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#" + obj);
                stringBuffer3.toString();
                for (int i5 = 0; i5 < 100; i5 = i5 + 1 + 1) {
                }
                for (int i6 = 0; i6 < 100; i6 = i6 + 1 + 1) {
                }
            } catch (Exception e3) {
                e3.printStackTrace();
            }
            try {
                StringBuffer stringBuffer4 = new StringBuffer();
                stringBuffer4.append("#");
                stringBuffer4.append("#");
                stringBuffer4.append("#");
                stringBuffer4.append("#");
                stringBuffer4.append("#");
                stringBuffer4.append("#" + obj);
                stringBuffer4.toString();
                for (int i7 = 0; i7 < 100; i7 = i7 + 1 + 1) {
                }
                for (int i8 = 0; i8 < 100; i8 = i8 + 1 + 1) {
                }
            } catch (Exception e4) {
                e4.printStackTrace();
            }
            return false;
        }
    }

    public static boolean callStatusChangedLocked(Object obj, String str, int i, Bundle bundle) {
        log("callStatusChangedLocked: ", obj, Integer.valueOf(i), bundle);
        if (isMocking() && (isAllMock() || isAllowMockPackage(getPackageNameByReceiver(obj)))) {
            return true;
        }
        try {
            StringBuffer stringBuffer = new StringBuffer();
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.toString();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return callStatusChangedLocked_bak(obj, str, i, bundle);
    }

    public static boolean callStatusChangedLocked_bak(Object obj, String str, int i, Bundle bundle) {
        try {
            log("callStatusChangedLocked_bak", obj);
            StringBuffer stringBuffer = new StringBuffer();
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.toString();
            for (int i2 = 0; i2 < 100; i2 = i2 + 1 + 1) {
            }
            for (int i3 = 0; i3 < 100; i3 = i3 + 1 + 1) {
            }
            return callStatusChangedLocked_copy(obj, str, i, bundle);
        } catch (Exception e) {
            e.printStackTrace();
            try {
                StringBuffer stringBuffer2 = new StringBuffer();
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#" + obj);
                stringBuffer2.toString();
                for (int i4 = 0; i4 < 100; i4 = i4 + 1 + 1) {
                }
                for (int i5 = 0; i5 < 100; i5 = i5 + 1 + 1) {
                }
            } catch (Exception e2) {
                e2.printStackTrace();
            }
            try {
                StringBuffer stringBuffer3 = new StringBuffer();
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#" + obj);
                stringBuffer3.toString();
                for (int i6 = 0; i6 < 100; i6 = i6 + 1 + 1) {
                }
                for (int i7 = 0; i7 < 100; i7 = i7 + 1 + 1) {
                }
            } catch (Exception e3) {
                e3.printStackTrace();
            }
            try {
                StringBuffer stringBuffer4 = new StringBuffer();
                stringBuffer4.append("#");
                stringBuffer4.append("#");
                stringBuffer4.append("#");
                stringBuffer4.append("#");
                stringBuffer4.append("#");
                stringBuffer4.append("#" + obj);
                stringBuffer4.toString();
                for (int i8 = 0; i8 < 100; i8 = i8 + 1 + 1) {
                }
                for (int i9 = 0; i9 < 100; i9 = i9 + 1 + 1) {
                }
            } catch (Exception e4) {
                e4.printStackTrace();
            }
            return callStatusChangedLocked_copy(obj, str, i, bundle);
        }
    }

    public static boolean callStatusChangedLocked_copy(Object obj, String str, int i, Bundle bundle) {
        try {
            log("callStatusChangedLocked_copy", obj);
            StringBuffer stringBuffer = new StringBuffer();
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.toString();
            for (int i2 = 0; i2 < 100; i2 = i2 + 1 + 1) {
            }
            for (int i3 = 0; i3 < 100; i3 = i3 + 1 + 1) {
            }
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            try {
                StringBuffer stringBuffer2 = new StringBuffer();
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#" + obj);
                stringBuffer2.toString();
                for (int i4 = 0; i4 < 100; i4 = i4 + 1 + 1) {
                }
                for (int i5 = 0; i5 < 100; i5 = i5 + 1 + 1) {
                }
            } catch (Exception e2) {
                e2.printStackTrace();
            }
            try {
                StringBuffer stringBuffer3 = new StringBuffer();
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#" + obj);
                stringBuffer3.toString();
                for (int i6 = 0; i6 < 100; i6 = i6 + 1 + 1) {
                }
                for (int i7 = 0; i7 < 100; i7 = i7 + 1 + 1) {
                }
            } catch (Exception e3) {
                e3.printStackTrace();
            }
            try {
                StringBuffer stringBuffer4 = new StringBuffer();
                stringBuffer4.append("#");
                stringBuffer4.append("#");
                stringBuffer4.append("#");
                stringBuffer4.append("#");
                stringBuffer4.append("#");
                stringBuffer4.append("#" + obj);
                stringBuffer4.toString();
                for (int i8 = 0; i8 < 100; i8 = i8 + 1 + 1) {
                }
                for (int i9 = 0; i9 < 100; i9 = i9 + 1 + 1) {
                }
            } catch (Exception e4) {
                e4.printStackTrace();
            }
            try {
                StringBuffer stringBuffer5 = new StringBuffer();
                stringBuffer5.append("#");
                stringBuffer5.append("#");
                stringBuffer5.append("#");
                stringBuffer5.append("#");
                stringBuffer5.append("#");
                stringBuffer5.append("#" + obj);
                stringBuffer5.toString();
                for (int i10 = 0; i10 < 100; i10 = i10 + 1 + 1) {
                }
                for (int i11 = 0; i11 < 100; i11 = i11 + 1 + 1) {
                }
            } catch (Exception e5) {
                e5.printStackTrace();
            }
            return false;
        }
    }

    public static Class forName(ClassLoader classLoader, String str) {
        return ReflectionUtils.loadClass(str, true, classLoader);
    }

    private static float generateFloatValue(float f) {
        int i = (int) f;
        if (i < 1) {
            i = 1;
        }
        float fNextInt = (new SecureRandom().nextBoolean() ? -1 : 1) * (new SecureRandom().nextInt(i) + ((new SecureRandom().nextBoolean() ? -1 : 1) * new SecureRandom().nextFloat()));
        if (Math.abs(fNextInt) > f) {
            return (new SecureRandom().nextBoolean() ? -1 : 1) * (f / 2.0f);
        }
        return fNextInt;
    }

    public static List<String> getAllowMockPackages() {
        return allowMockPackages;
    }

    public static String getBestProvider(Object obj, Criteria criteria, boolean z) {
        log("getBestProvider: ", criteria, Boolean.valueOf(z));
        if (isHook()) {
            return "gps";
        }
        try {
            return getBestProvider_bak(obj, criteria, z);
        } catch (Throwable th) {
            th.printStackTrace();
            try {
                StringBuffer stringBuffer = new StringBuffer();
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#" + obj);
                stringBuffer.toString();
                for (int i = 0; i < 100; i = i + 1 + 1) {
                }
                for (int i2 = 0; i2 < 100; i2 = i2 + 1 + 1) {
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                StringBuffer stringBuffer2 = new StringBuffer();
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#" + obj);
                stringBuffer2.toString();
                for (int i3 = 0; i3 < 100; i3 = i3 + 1 + 1) {
                }
                for (int i4 = 0; i4 < 100; i4 = i4 + 1 + 1) {
                }
            } catch (Exception e2) {
                e2.printStackTrace();
            }
            return "gps";
        }
    }

    public static String getBestProvider_bak(Object obj, Criteria criteria, boolean z) {
        try {
            log("getBestProvider_bak", obj);
            StringBuffer stringBuffer = new StringBuffer();
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.toString();
            for (int i = 0; i < 100; i = i + 1 + 1) {
            }
            for (int i2 = 0; i2 < 100; i2 = i2 + 1 + 1) {
            }
            return "";
        } catch (Exception e) {
            e.printStackTrace();
            try {
                StringBuffer stringBuffer2 = new StringBuffer();
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#" + obj);
                stringBuffer2.toString();
                for (int i3 = 0; i3 < 100; i3 = i3 + 1 + 1) {
                }
                for (int i4 = 0; i4 < 100; i4 = i4 + 1 + 1) {
                }
            } catch (Exception e2) {
                e2.printStackTrace();
            }
            try {
                StringBuffer stringBuffer3 = new StringBuffer();
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#" + obj);
                stringBuffer3.toString();
                for (int i5 = 0; i5 < 100; i5 = i5 + 1 + 1) {
                }
                for (int i6 = 0; i6 < 100; i6 = i6 + 1 + 1) {
                }
            } catch (Exception e3) {
                e3.printStackTrace();
            }
            return getBestProvider_copy(obj, criteria, z);
        }
    }

    public static String getBestProvider_copy(Object obj, Criteria criteria, boolean z) {
        try {
            log("getBestProvider_copy", obj);
            StringBuffer stringBuffer = new StringBuffer();
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.toString();
            for (int i = 0; i < 100; i = i + 1 + 1) {
            }
            for (int i2 = 0; i2 < 100; i2 = i2 + 1 + 1) {
            }
            return "";
        } catch (Exception e) {
            e.printStackTrace();
            try {
                StringBuffer stringBuffer2 = new StringBuffer();
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#" + obj);
                stringBuffer2.toString();
                for (int i3 = 0; i3 < 100; i3 = i3 + 1 + 1) {
                }
                for (int i4 = 0; i4 < 100; i4 = i4 + 1 + 1) {
                }
            } catch (Exception e2) {
                e2.printStackTrace();
            }
            try {
                StringBuffer stringBuffer3 = new StringBuffer();
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#" + obj);
                stringBuffer3.toString();
                for (int i5 = 0; i5 < 100; i5 = i5 + 1 + 1) {
                }
                for (int i6 = 0; i6 < 100; i6 = i6 + 1 + 1) {
                }
                return null;
            } catch (Exception e3) {
                e3.printStackTrace();
                return null;
            }
        }
    }

    public static String getCurrentLocationFrom() {
        return currentLocationSource;
    }

    public static long getIntervalTimeout() {
        return mockIntervalMillis;
    }

    public static Location getLastLocation(Object obj, Object obj2, String str) {
        log("getLastLocation: ", obj2, str);
        if (isHook()) {
            getLastLocation_bak(obj, obj2, str);
            Location location = new Location(getTempLocation());
            location.setProvider(getLocationRequestProvider(obj2));
            return location;
        }
        try {
            return getLastLocation_bak(obj, obj2, str);
        } catch (Throwable th) {
            th.printStackTrace();
            Location location2 = new Location(getTempLocation());
            location2.setProvider(getLocationRequestProvider(obj2));
            try {
                StringBuffer stringBuffer = new StringBuffer();
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#" + obj);
                stringBuffer.toString();
                for (int i = 0; i < 100; i = i + 1 + 1) {
                }
                for (int i2 = 0; i2 < 100; i2 = i2 + 1 + 1) {
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return location2;
        }
    }

    public static Location getLastLocation_R(Object obj, Object obj2, String str, String str2) {
        log("getLastLocation_R: ", obj2, str);
        if (isHook()) {
            getLastLocation_R_bak(obj, obj2, str, str2);
            Location location = new Location(getTempLocation());
            location.setProvider(getLocationRequestProvider(obj2));
            return location;
        }
        try {
            return getLastLocation_R_bak(obj, obj2, str, str2);
        } catch (Throwable th) {
            th.printStackTrace();
            Location location2 = new Location(getTempLocation());
            location2.setProvider(getLocationRequestProvider(obj2));
            try {
                StringBuffer stringBuffer = new StringBuffer();
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#" + obj);
                stringBuffer.toString();
                for (int i = 0; i < 100; i = i + 1 + 1) {
                }
                for (int i2 = 0; i2 < 100; i2 = i2 + 1 + 1) {
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                StringBuffer stringBuffer2 = new StringBuffer();
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#" + obj);
                stringBuffer2.toString();
                for (int i3 = 0; i3 < 100; i3 = i3 + 1 + 1) {
                }
                for (int i4 = 0; i4 < 100; i4 = i4 + 1 + 1) {
                }
            } catch (Exception e2) {
                e2.printStackTrace();
            }
            try {
                StringBuffer stringBuffer3 = new StringBuffer();
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#" + obj);
                stringBuffer3.toString();
                for (int i5 = 0; i5 < 100; i5 = i5 + 1 + 1) {
                }
                for (int i6 = 0; i6 < 100; i6 = i6 + 1 + 1) {
                }
            } catch (Exception e3) {
                e3.printStackTrace();
            }
            return location2;
        }
    }

    public static Location getLastLocation_R_bak(Object obj, Object obj2, String str, String str2) {
        try {
            log("getLastLocation_R_bak", obj);
            StringBuffer stringBuffer = new StringBuffer();
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.toString();
            for (int i = 0; i < 100; i = i + 1 + 1) {
            }
            return getLastLocation_R_copy(obj, obj2, str, str2);
        } catch (Exception e) {
            e.printStackTrace();
            try {
                StringBuffer stringBuffer2 = new StringBuffer();
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#" + obj);
                stringBuffer2.toString();
                for (int i2 = 0; i2 < 100; i2 = i2 + 1 + 1) {
                }
                for (int i3 = 0; i3 < 100; i3 = i3 + 1 + 1) {
                }
            } catch (Exception e2) {
                e2.printStackTrace();
            }
            try {
                StringBuffer stringBuffer3 = new StringBuffer();
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#" + obj);
                stringBuffer3.toString();
                for (int i4 = 0; i4 < 100; i4 = i4 + 1 + 1) {
                }
                for (int i5 = 0; i5 < 100; i5 = i5 + 1 + 1) {
                }
                return null;
            } catch (Exception e3) {
                e3.printStackTrace();
                return null;
            }
        }
    }

    public static Location getLastLocation_R_copy(Object obj, Object obj2, String str, String str2) {
        try {
            log("getLastLocation_R_copy", obj);
            StringBuffer stringBuffer = new StringBuffer();
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.toString();
            for (int i = 0; i < 100; i = i + 1 + 1) {
            }
            return new Location("");
        } catch (Exception e) {
            e.printStackTrace();
            try {
                StringBuffer stringBuffer2 = new StringBuffer();
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#" + obj);
                stringBuffer2.toString();
                for (int i2 = 0; i2 < 100; i2 = i2 + 1 + 1) {
                }
                for (int i3 = 0; i3 < 100; i3 = i3 + 1 + 1) {
                }
            } catch (Exception e2) {
                e2.printStackTrace();
            }
            try {
                StringBuffer stringBuffer3 = new StringBuffer();
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#" + obj);
                stringBuffer3.toString();
                for (int i4 = 0; i4 < 100; i4 = i4 + 1 + 1) {
                }
                for (int i5 = 0; i5 < 100; i5 = i5 + 1 + 1) {
                }
                return null;
            } catch (Exception e3) {
                e3.printStackTrace();
                return null;
            }
        }
    }

    public static Location getLastLocation_bak(Object obj, Object obj2, String str) {
        try {
            log("getLastLocation_bak", obj);
            StringBuffer stringBuffer = new StringBuffer();
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.toString();
            for (int i = 0; i < 100; i = i + 1 + 1) {
            }
            return getLastLocation_copy(obj, obj2, str);
        } catch (Exception e) {
            e.printStackTrace();
            try {
                StringBuffer stringBuffer2 = new StringBuffer();
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#" + obj);
                stringBuffer2.toString();
                for (int i2 = 0; i2 < 100; i2 = i2 + 1 + 1) {
                }
                for (int i3 = 0; i3 < 100; i3 = i3 + 1 + 1) {
                }
            } catch (Exception e2) {
                e2.printStackTrace();
            }
            try {
                StringBuffer stringBuffer3 = new StringBuffer();
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#" + obj);
                stringBuffer3.toString();
                for (int i4 = 0; i4 < 100; i4 = i4 + 1 + 1) {
                }
                for (int i5 = 0; i5 < 100; i5 = i5 + 1 + 1) {
                }
            } catch (Exception e3) {
                e3.printStackTrace();
            }
            try {
                StringBuffer stringBuffer4 = new StringBuffer();
                stringBuffer4.append("#");
                stringBuffer4.append("#");
                stringBuffer4.append("#");
                stringBuffer4.append("#");
                stringBuffer4.append("#");
                stringBuffer4.append("#" + obj);
                stringBuffer4.toString();
                for (int i6 = 0; i6 < 100; i6 = i6 + 1 + 1) {
                }
                for (int i7 = 0; i7 < 100; i7 = i7 + 1 + 1) {
                }
                return null;
            } catch (Exception e4) {
                e4.printStackTrace();
                return null;
            }
        }
    }

    public static Location getLastLocation_copy(Object obj, Object obj2, String str) {
        try {
            log("getLastLocation_copy", obj);
            StringBuffer stringBuffer = new StringBuffer();
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.toString();
            for (int i = 0; i < 100; i = i + 1 + 1) {
            }
            return new Location("");
        } catch (Exception e) {
            e.printStackTrace();
            try {
                StringBuffer stringBuffer2 = new StringBuffer();
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#" + obj);
                stringBuffer2.toString();
                for (int i2 = 0; i2 < 100; i2 = i2 + 1 + 1) {
                }
                for (int i3 = 0; i3 < 100; i3 = i3 + 1 + 1) {
                }
            } catch (Exception e2) {
                e2.printStackTrace();
            }
            try {
                StringBuffer stringBuffer3 = new StringBuffer();
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#" + obj);
                stringBuffer3.toString();
                for (int i4 = 0; i4 < 100; i4 = i4 + 1 + 1) {
                }
                for (int i5 = 0; i5 < 100; i5 = i5 + 1 + 1) {
                }
            } catch (Exception e3) {
                e3.printStackTrace();
            }
            try {
                StringBuffer stringBuffer4 = new StringBuffer();
                stringBuffer4.append("#");
                stringBuffer4.append("#");
                stringBuffer4.append("#");
                stringBuffer4.append("#");
                stringBuffer4.append("#");
                stringBuffer4.append("#" + obj);
                stringBuffer4.toString();
                for (int i6 = 0; i6 < 100; i6 = i6 + 1 + 1) {
                }
                for (int i7 = 0; i7 < 100; i7 = i7 + 1 + 1) {
                }
                return null;
            } catch (Exception e4) {
                e4.printStackTrace();
                return null;
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static String getLocationRequestProvider(Object obj) {
        if (obj != null) {
            try {
                return (String) ReflectionUtils.invokeMethod(obj, "android.location.LocationRequest", "getProvider", null, null);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        try {
            StringBuffer stringBuffer = new StringBuffer();
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.toString();
            for (int i = 0; i < 100; i = i + 1 + 1) {
            }
            for (int i2 = 0; i2 < 100; i2 = i2 + 1 + 1) {
            }
        } catch (Exception e2) {
            e2.printStackTrace();
        }
        try {
            StringBuffer stringBuffer2 = new StringBuffer();
            stringBuffer2.append("#");
            stringBuffer2.append("#");
            stringBuffer2.append("#");
            stringBuffer2.append("#");
            stringBuffer2.append("#");
            stringBuffer2.append("#");
            stringBuffer2.toString();
            for (int i3 = 0; i3 < 100; i3 = i3 + 1 + 1) {
            }
            for (int i4 = 0; i4 < 100; i4 = i4 + 1 + 1) {
            }
        } catch (Exception e3) {
            e3.printStackTrace();
        }
        try {
            StringBuffer stringBuffer3 = new StringBuffer();
            stringBuffer3.append("#");
            stringBuffer3.append("#");
            stringBuffer3.append("#");
            stringBuffer3.append("#");
            stringBuffer3.append("#");
            stringBuffer3.append("#");
            stringBuffer3.toString();
            for (int i5 = 0; i5 < 100; i5 = i5 + 1 + 1) {
            }
            for (int i6 = 0; i6 < 100; i6 = i6 + 1 + 1) {
            }
            return "gps";
        } catch (Exception e4) {
            e4.printStackTrace();
            return "gps";
        }
    }

    public static List<CellTowerInfo> getMockCells() {
        return mockCells;
    }

    public static Location getMockLocation() {
        if (mockLocation.getLatitude() == 0.0d && mockLocation.getLongitude() == 0.0d) {
            return null;
        }
        return mockLocation;
    }

    public static String getPackageNameByReceiver(Object obj) {
        try {
            HashMap<Object, String> map = receiverPackages;
            synchronized (map) {
                for (Object obj2 : map.keySet()) {
                    if (obj2 == obj) {
                        return receiverPackages.get(obj2);
                    }
                }
                return null;
            }
        } catch (Throwable th) {
            th.printStackTrace();
            return null;
        }
    }

    public static String getProviderByReceiver(Object obj) {
        String str;
        HashMap<Object, String> map = receiverProviders;
        synchronized (map) {
            str = map.get(obj);
        }
        return str;
    }

    public static List<String> getProviders(Object obj, Criteria criteria, boolean z) {
        log("getProviders: ", criteria, Boolean.valueOf(z));
        if (isHook()) {
            ArrayList arrayList = new ArrayList();
            arrayList.add("gps");
            return arrayList;
        }
        try {
            return getProviders_bak(obj, criteria, z);
        } catch (Throwable th) {
            th.printStackTrace();
            ArrayList arrayList2 = new ArrayList();
            arrayList2.add("gps");
            try {
                StringBuffer stringBuffer = new StringBuffer();
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#" + obj);
                stringBuffer.toString();
                for (int i = 0; i < 100; i = i + 1 + 1) {
                }
                for (int i2 = 0; i2 < 100; i2 = i2 + 1 + 1) {
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                StringBuffer stringBuffer2 = new StringBuffer();
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#" + obj);
                stringBuffer2.toString();
                for (int i3 = 0; i3 < 100; i3 = i3 + 1 + 1) {
                }
                for (int i4 = 0; i4 < 100; i4 = i4 + 1 + 1) {
                }
            } catch (Exception e2) {
                e2.printStackTrace();
            }
            return arrayList2;
        }
    }

    public static List<String> getProviders_bak(Object obj, Criteria criteria, boolean z) {
        try {
            log("getProviders_bak", obj);
            StringBuffer stringBuffer = new StringBuffer();
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.toString();
            for (int i = 0; i < 100; i = i + 1 + 1) {
            }
            for (int i2 = 0; i2 < 100; i2 = i2 + 1 + 1) {
            }
            return getProviders_copy(obj, criteria, z);
        } catch (Exception e) {
            e.printStackTrace();
            try {
                StringBuffer stringBuffer2 = new StringBuffer();
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#" + obj);
                stringBuffer2.toString();
                for (int i3 = 0; i3 < 100; i3 = i3 + 1 + 1) {
                }
                for (int i4 = 0; i4 < 100; i4 = i4 + 1 + 1) {
                }
            } catch (Exception e2) {
                e2.printStackTrace();
            }
            try {
                StringBuffer stringBuffer3 = new StringBuffer();
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#" + obj);
                stringBuffer3.toString();
                for (int i5 = 0; i5 < 100; i5 = i5 + 1 + 1) {
                }
                for (int i6 = 0; i6 < 100; i6 = i6 + 1 + 1) {
                }
            } catch (Exception e3) {
                e3.printStackTrace();
            }
            return getProviders_copy(obj, criteria, z);
        }
    }

    public static List<String> getProviders_copy(Object obj, Criteria criteria, boolean z) {
        try {
            log("getProviders_copy", obj);
            StringBuffer stringBuffer = new StringBuffer();
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.toString();
            for (int i = 0; i < 100; i = i + 1 + 1) {
            }
            for (int i2 = 0; i2 < 100; i2 = i2 + 1 + 1) {
            }
            return new ArrayList();
        } catch (Exception e) {
            e.printStackTrace();
            try {
                StringBuffer stringBuffer2 = new StringBuffer();
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#" + obj);
                stringBuffer2.toString();
                for (int i3 = 0; i3 < 100; i3 = i3 + 1 + 1) {
                }
                for (int i4 = 0; i4 < 100; i4 = i4 + 1 + 1) {
                }
            } catch (Exception e2) {
                e2.printStackTrace();
            }
            try {
                StringBuffer stringBuffer3 = new StringBuffer();
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#" + obj);
                stringBuffer3.toString();
                for (int i5 = 0; i5 < 100; i5 = i5 + 1 + 1) {
                }
                for (int i6 = 0; i6 < 100; i6 = i6 + 1 + 1) {
                }
            } catch (Exception e3) {
                e3.printStackTrace();
            }
            try {
                StringBuffer stringBuffer4 = new StringBuffer();
                stringBuffer4.append("#");
                stringBuffer4.append("#");
                stringBuffer4.append("#");
                stringBuffer4.append("#");
                stringBuffer4.append("#");
                stringBuffer4.append("#" + obj);
                stringBuffer4.toString();
                for (int i7 = 0; i7 < 100; i7 = i7 + 1 + 1) {
                }
                for (int i8 = 0; i8 < 100; i8 = i8 + 1 + 1) {
                }
                return null;
            } catch (Exception e4) {
                e4.printStackTrace();
                return null;
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static Object getProxyListener(Object obj) {
        return proxyListeners.get(obj);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static float[] getRandomLength(float[] fArr, int i) {
        return fArr;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static int[] getRandomLength(int[] iArr, int i) {
        return iArr;
    }

    public static List<String> getSafeApps() {
        List<String> list;
        synchronized (mockPackageLock) {
            list = scopedAllowMockRules;
        }
        return list;
    }

    public static Location getTempLocation() {
        if (tempLocation == null) {
            tempLocation = new Location("gps");
        }
        if (mockLocation == null) {
            mockLocation = new Location("gps");
        }
        tempLocation.set(mockLocation);
        Location location = tempLocation;
        location.setLatitude(location.getLatitude() + (((double) (new SecureRandom().nextBoolean() ? -1 : 1)) * (((double) new SecureRandom().nextFloat()) / 1.0E7d)));
        Location location2 = tempLocation;
        location2.setLongitude(location2.getLongitude() + (((double) (new SecureRandom().nextBoolean() ? -1 : 1)) * (((double) new SecureRandom().nextFloat()) / 1.0E8d)));
        if (tempLocation.getAltitude() == 0.0d) {
            tempLocation.setAltitude(200.0d);
        }
        Location location3 = tempLocation;
        location3.setAltitude(location3.getAltitude() + ((double) generateFloatValue(5.0f)));
        if (tempLocation.getBearing() == 0.0f) {
            tempLocation.setBearing(1.2f);
        }
        int i = Build.VERSION.SDK_INT;
        if (i >= 26 && tempLocation.getBearingAccuracyDegrees() == 0.0f) {
            tempLocation.setBearingAccuracyDegrees(1.0f);
        }
        if (tempLocation.getSpeed() == 0.0f) {
            tempLocation.setSpeed(1.2f);
        }
        if (i >= 26 && tempLocation.getSpeedAccuracyMetersPerSecond() == 0.0f) {
            tempLocation.setSpeedAccuracyMetersPerSecond(1.0f);
        }
        if (tempLocation.getAccuracy() == 0.0f) {
            tempLocation.setAccuracy(1.2f);
        }
        Location location4 = tempLocation;
        location4.setAccuracy(Math.abs(location4.getAccuracy() + generateFloatValue(1.0f)));
        if (i >= 26) {
            if (tempLocation.getVerticalAccuracyMeters() == 0.0f) {
                tempLocation.setVerticalAccuracyMeters(1.0f);
            }
            Location location5 = tempLocation;
            location5.setVerticalAccuracyMeters(Math.abs(location5.getVerticalAccuracyMeters() + generateFloatValue(1.0f)));
        }
        if (i >= 17 && tempLocation.getElapsedRealtimeNanos() == 0) {
            tempLocation.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
        }
        if (tempLocation.getTime() == 0) {
            tempLocation.setTime(System.currentTimeMillis());
        }
        Bundle bundle = tempLocation.getExtras() == null ? new Bundle() : tempLocation.getExtras();
        if (!bundle.containsKey("satellites")) {
            bundle.putInt("satellites", 20);
            tempLocation.setExtras(bundle);
            mockLocation.setExtras(bundle);
        }
        if (tempLocation.getTime() == 0) {
            tempLocation.setTime(System.currentTimeMillis());
        }
        if (tempLocation.getElapsedRealtimeNanos() == 0 && i >= 17) {
            tempLocation.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
        }
        return tempLocation;
    }

    public static void hook(ClassLoader classLoader) {
        try {
            PackageSignatureVerifier.verifyPackageSignature(InjectDex.getApplicationContext(), "com.kail.location", "com.android.server.LocationManagerService");
            Class cls = locationManagerServiceClass;
            Class cls2 = Boolean.TYPE;
            LHooker.hookMethodWithBackup(cls, "isProviderEnabled", cls2, new Class[]{String.class}, MockLocationHookManager.class, "isProviderEnabled", "isProviderEnabled_bak");
            Class cls3 = locationManagerServiceClass;
            Class cls4 = Integer.TYPE;
            LHooker.hookMethodWithBackup(cls3, "isProviderEnabledForUser", cls2, new Class[]{String.class, cls4}, MockLocationHookManager.class, "isProviderEnabledForUser", "isProviderEnabledForUser_bak");
            LHooker.hookMethodWithBackup(locationManagerServiceClass, "getBestProvider", String.class, new Class[]{Criteria.class, cls2}, MockLocationHookManager.class, "getBestProvider", "getBestProvider_bak");
            LHooker.hookMethodWithBackup(locationManagerServiceClass, "getProviders", List.class, new Class[]{Criteria.class, cls2}, MockLocationHookManager.class, "getProviders", "getProviders_bak");
            if (Build.VERSION.SDK_INT < 31) {
                LHooker.hookMethodWithBackup(locationManagerServiceClass, "getLastLocation", Location.class, new Class[]{forName(classLoader, "android.location.LocationRequest"), String.class}, MockLocationHookManager.class, "getLastLocation", "getLastLocation_bak");
                LHooker.hookMethodWithBackup(locationManagerServiceClass, "getLastLocation", Location.class, new Class[]{forName(classLoader, "android.location.LocationRequest"), String.class, String.class}, MockLocationHookManager.class, "getLastLocation_R", "getLastLocation_R_bak");
                LHooker.hookMethodWithBackup(locationManagerServiceClass, "requestLocationUpdatesLocked", Void.TYPE, new Class[]{forName(classLoader, "android.location.LocationRequest"), locationManagerReceiverClass, cls4, cls4, String.class}, MockLocationHookManager.class, "requestLocationUpdatesLocked", "requestLocationUpdatesLocked_bak");
                LHooker.hookMethodWithBackup(locationManagerServiceClass, "requestLocationUpdatesLocked", Void.TYPE, new Class[]{forName(classLoader, "android.location.LocationRequest"), locationManagerReceiverClass, cls4, String.class}, MockLocationHookManager.class, "requestLocationUpdatesLocked_Q", "requestLocationUpdatesLocked_Q_bak");
                LHooker.hookMethodWithBackup(locationManagerServiceClass, "requestLocationUpdatesLocked", Void.TYPE, new Class[]{forName(classLoader, "android.location.LocationRequest"), locationManagerReceiverClass}, MockLocationHookManager.class, "requestLocationUpdatesLocked_R", "requestLocationUpdatesLocked_R_bak");
                LHooker.hookMethodWithBackup(locationManagerServiceClass, "removeUpdatesLocked", Void.TYPE, new Class[]{locationManagerReceiverClass}, MockLocationHookManager.class, "removeUpdatesLocked", "removeUpdatesLocked_bak");
                LHooker.hookMethodWithBackup(locationManagerReceiverClass, "callStatusChangedLocked", cls2, new Class[]{String.class, cls4, Bundle.class}, MockLocationHookManager.class, "callStatusChangedLocked", "callStatusChangedLocked_bak");
                LHooker.hookMethodWithBackup(locationManagerReceiverClass, "callLocationChangedLocked", cls2, new Class[]{Location.class}, MockLocationHookManager.class, "callLocationChangedLocked", "callLocationChangedLocked_bak");
                LHooker.hookMethodWithBackup(locationManagerServiceClass, "registerGnssStatusCallback", cls2, new Class[]{forName(classLoader, "android.location.IGnssStatusListener"), String.class}, MockLocationHookManager.class, "registerGnssStatusCallback", "registerGnssStatusCallback_bak");
                LHooker.hookMethodWithBackup(locationManagerServiceClass, "registerGnssStatusCallback", cls2, new Class[]{forName(classLoader, "android.location.IGnssStatusListener"), String.class, String.class}, MockLocationHookManager.class, "registerGnssStatusCallback_R", "registerGnssStatusCallback_R_bak");
                LHooker.hookMethodWithBackup(locationManagerServiceClass, "addGpsStatusListener", cls2, new Class[]{forName(classLoader, "android.location.IGpsStatusListener"), String.class}, MockLocationHookManager.class, "addGpsStatusListener", "addGpsStatusListener_bak");
                LHooker.hookMethodWithBackup(locationManagerServiceClass, "removeGpsStatusListener", cls2, new Class[]{forName(classLoader, "android.location.IGpsStatusListener")}, MockLocationHookManager.class, "removeGpsStatusListener", "removeGpsStatusListener_bak");
            } else {
                if (LocationListenerHooks.checkNull(null) == 0) {
                    return;
                }
                LHooker.hookMethodWithBackup(locationManagerServiceClass, "getLastLocation", Location.class, new Class[]{String.class, forName(classLoader, "android.location.LastLocationRequest"), String.class, String.class}, LocationListenerHooks.class, "getLastLocation", "getLastLocation_bak");
                LHooker.hookMethodWithBackup(locationManagerServiceClass, "registerLocationListener", Void.TYPE, new Class[]{String.class, forName(classLoader, "android.location.LocationRequest"), iLocationListenerClass, String.class, String.class, String.class}, LocationListenerHooks.class, "registerLocationListener", "registerLocationListener_bak");
                LHooker.hookMethodWithBackup(locationManagerServiceClass, "unregisterLocationListener", Void.TYPE, new Class[]{iLocationListenerClass}, LocationListenerHooks.class, "unregisterLocationListener", "unregisterLocationListener_bak");
                LHooker.hookMethodWithBackup(locationManagerServiceClass, "registerLocationPendingIntent", Void.TYPE, new Class[]{String.class, forName(classLoader, "android.location.LocationRequest"), PendingIntent.class, String.class, String.class}, LocationListenerHooks.class, "registerLocationPendingIntent", "registerLocationPendingIntent_bak");
                LHooker.hookMethodWithBackup(locationManagerServiceClass, "unregisterLocationPendingIntent", Void.TYPE, new Class[]{PendingIntent.class}, LocationListenerHooks.class, "unregisterLocationPendingIntent", "unregisterLocationPendingIntent_bak");
                LHooker.hookMethodWithBackup(iLocationListenerStubProxyClass, "onLocationChanged", Void.TYPE, new Class[]{List.class, iRemoteCallbackClass}, LocationListenerHooks.class, "onLocationChanged", "onLocationChanged_bak");
                LHooker.hookMethodWithBackup(locationListenerTransportClass, "onLocationChanged", Void.TYPE, new Class[]{List.class, iRemoteCallbackClass}, LocationListenerHooks.class, "onLocationChanged2", "onLocationChanged2_bak");
                LHooker.hookMethodWithBackup(locationManagerServiceClass, "registerGnssStatusCallback", Void.TYPE, new Class[]{forName(classLoader, "android.location.IGnssStatusListener"), String.class, String.class, String.class}, LocationListenerHooks.class, "registerGnssStatusCallback", "registerGnssStatusCallback_bak");
            }
            LHooker.hookMethodWithBackup(locationManagerServiceClass, "unregisterGnssStatusCallback", Void.TYPE, new Class[]{forName(classLoader, "android.location.IGnssStatusListener")}, MockLocationHookManager.class, "unregisterGnssStatusCallback", "unregisterGnssStatusCallback_bak");
        } catch (RuntimeException unused) {
            stopMockLocation();
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }

    public static void init(Context context) {
        if (initialized) {
            return;
        }
        try {
            ClassLoader classLoader = context.getClassLoader();
            int i = Build.VERSION.SDK_INT;
            locationManagerServiceClass = forName(classLoader, i >= 30 ? "com.android.server.location.LocationManagerService" : "com.android.server.LocationManagerService");
            iGnssStatusListenerClass = forName(classLoader, "android.location.IGnssStatusListener");
            if (i >= 31) {
                iLocationListenerClass = forName(classLoader, "android.location.ILocationListener");
                iLocationListenerStubClass = forName(classLoader, "android.location.ILocationListener$Stub");
                iLocationListenerStubProxyClass = forName(classLoader, "android.location.ILocationListener$Stub$Proxy");
                iRemoteCallbackClass = forName(classLoader, "android.os.IRemoteCallback");
                locationListenerTransportClass = forName(classLoader, "android.location.LocationManager$LocationListenerTransport");
            } else {
                locationManagerReceiverClass = forName(classLoader, i >= 30 ? "com.android.server.location.LocationManagerService$Receiver" : "com.android.server.LocationManagerService$Receiver");
                iGpsStatusListenerClass = forName(classLoader, "android.location.IGpsStatusListener");
            }
            hook(classLoader);
        } catch (Throwable th) {
            th.printStackTrace();
        }
        new Thread(new MockLocationDispatchLoop()).start();
        initialized = true;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static boolean isAllMock() {
        List<String> list;
        List<String> list2 = allowMockPackages;
        return (list2 == null || list2.isEmpty()) && ((list = scopedAllowMockRules) == null || list.isEmpty());
    }

    public static boolean isAllowMockPackage(String str) {
        String str2;
        String str3 = "" + getCurrentLocationFrom();
        str3.hashCode();
        switch (str3) {
            case "rocker":
                str2 = "h";
                break;
            case "loc":
                return isAllowMockPackage(str, "a");
            case "route":
                str2 = "b";
                break;
            default:
                return isAllowMockPackage(str, "a");
        }
        return isAllowMockPackage(str, str2);
    }

    public static boolean isAllowMockPackage(String str, String str2) {
        if (str == null) {
            return false;
        }
        synchronized (mockPackageLock) {
            if (isAllMock()) {
                return true;
            }
            List<String> list = scopedAllowMockRules;
            if (list != null && !list.isEmpty() && !ScopedListFilter.isAllowed(scopedAllowMockRules, str, str2)) {
                return false;
            }
            List<String> list2 = allowMockPackages;
            if (list2 == null || list2.isEmpty()) {
                return true;
            }
            return allowMockPackages.contains(str);
        }
    }

    public static boolean isHook() {
        return isHook(Binder.getCallingUid());
    }

    private static boolean isHook(int i) {
        boolean z = (isMocking() && (CallingProcessUtils.isCallingShellUid() || !CallingProcessUtils.isSystemUid(i))) && isAllowMockPackage(CallingProcessUtils.getPackageNameForPidOrUid(Binder.getCallingPid(), Binder.getCallingUid()));
        log("isHook: " + z, "uid: " + Binder.getCallingUid(), "pid: " + Binder.getCallingPid());
        return z;
    }

    public static boolean isMockGpsStatus() {
        return mockGpsStatusEnabled;
    }

    public static boolean isMocking() {
        return mocking;
    }

    public static boolean isProviderEnabled(Object obj, String str) {
        log("isProviderEnabled: ", str);
        if (isHook()) {
            return "gps".equals(str) || "network".equals(str) || "passive".equals(str) || "fused".equals(str);
        }
        try {
            return isProviderEnabled_bak(obj, str);
        } catch (Throwable th) {
            th.printStackTrace();
            try {
                StringBuffer stringBuffer = new StringBuffer();
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#" + obj);
                stringBuffer.toString();
                for (int i = 0; i < 100; i = i + 1 + 1) {
                }
                for (int i2 = 0; i2 < 100; i2 = i2 + 1 + 1) {
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                StringBuffer stringBuffer2 = new StringBuffer();
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#" + obj);
                stringBuffer2.toString();
                for (int i3 = 0; i3 < 100; i3 = i3 + 1 + 1) {
                }
                for (int i4 = 0; i4 < 100; i4 = i4 + 1 + 1) {
                }
            } catch (Exception e2) {
                e2.printStackTrace();
            }
            return true;
        }
    }

    public static boolean isProviderEnabledForUser(Object obj, String str, int i) {
        log("isProviderEnabledForUser: ", str);
        if (isHook()) {
            return "gps".equals(str) || "network".equals(str) || "passive".equals(str) || "fused".equals(str);
        }
        try {
            return isProviderEnabledForUser_bak(obj, str, i);
        } catch (Throwable th) {
            th.printStackTrace();
            try {
                StringBuffer stringBuffer = new StringBuffer();
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#" + obj);
                stringBuffer.toString();
                for (int i2 = 0; i2 < 100; i2 = i2 + 1 + 1) {
                }
                for (int i3 = 0; i3 < 100; i3 = i3 + 1 + 1) {
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                StringBuffer stringBuffer2 = new StringBuffer();
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#" + obj);
                stringBuffer2.toString();
                for (int i4 = 0; i4 < 100; i4 = i4 + 1 + 1) {
                }
                for (int i5 = 0; i5 < 100; i5 = i5 + 1 + 1) {
                }
            } catch (Exception e2) {
                e2.printStackTrace();
            }
            return true;
        }
    }

    public static boolean isProviderEnabledForUser_bak(Object obj, String str, int i) {
        try {
            log("isProviderEnabledForUser_bak", obj);
            StringBuffer stringBuffer = new StringBuffer();
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.toString();
            for (int i2 = 0; i2 < 100; i2 = i2 + 1 + 1) {
            }
            for (int i3 = 0; i3 < 100; i3 = i3 + 1 + 1) {
            }
            return isProviderEnabledForUser_copy(obj, str, i);
        } catch (Exception e) {
            e.printStackTrace();
            try {
                StringBuffer stringBuffer2 = new StringBuffer();
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#" + obj);
                stringBuffer2.toString();
                for (int i4 = 0; i4 < 100; i4 = i4 + 1 + 1) {
                }
                for (int i5 = 0; i5 < 100; i5 = i5 + 1 + 1) {
                }
            } catch (Exception e2) {
                e2.printStackTrace();
            }
            try {
                StringBuffer stringBuffer3 = new StringBuffer();
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#" + obj);
                stringBuffer3.toString();
                for (int i6 = 0; i6 < 100; i6 = i6 + 1 + 1) {
                }
                for (int i7 = 0; i7 < 100; i7 = i7 + 1 + 1) {
                }
            } catch (Exception e3) {
                e3.printStackTrace();
            }
            return isProviderEnabledForUser_copy(obj, str, i);
        }
    }

    public static boolean isProviderEnabledForUser_copy(Object obj, String str, int i) {
        try {
            log("isProviderEnabledForUser_copy", obj);
            StringBuffer stringBuffer = new StringBuffer();
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.toString();
            for (int i2 = 0; i2 < 100; i2 = i2 + 1 + 1) {
            }
            for (int i3 = 0; i3 < 100; i3 = i3 + 1 + 1) {
            }
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            try {
                StringBuffer stringBuffer2 = new StringBuffer();
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#" + obj);
                stringBuffer2.toString();
                for (int i4 = 0; i4 < 100; i4 = i4 + 1 + 1) {
                }
                for (int i5 = 0; i5 < 100; i5 = i5 + 1 + 1) {
                }
            } catch (Exception e2) {
                e2.printStackTrace();
            }
            try {
                StringBuffer stringBuffer3 = new StringBuffer();
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#" + obj);
                stringBuffer3.toString();
                for (int i6 = 0; i6 < 100; i6 = i6 + 1 + 1) {
                }
                for (int i7 = 0; i7 < 100; i7 = i7 + 1 + 1) {
                }
            } catch (Exception e3) {
                e3.printStackTrace();
            }
            return false;
        }
    }

    public static boolean isProviderEnabled_bak(Object obj, String str) {
        try {
            log("isProviderEnabled_bak", obj);
            StringBuffer stringBuffer = new StringBuffer();
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.toString();
            for (int i = 0; i < 100; i = i + 1 + 1) {
            }
            for (int i2 = 0; i2 < 100; i2 = i2 + 1 + 1) {
            }
            return isProviderEnabled_copy(obj, str);
        } catch (Exception e) {
            e.printStackTrace();
            try {
                StringBuffer stringBuffer2 = new StringBuffer();
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#" + obj);
                stringBuffer2.toString();
                for (int i3 = 0; i3 < 100; i3 = i3 + 1 + 1) {
                }
                for (int i4 = 0; i4 < 100; i4 = i4 + 1 + 1) {
                }
            } catch (Exception e2) {
                e2.printStackTrace();
            }
            try {
                StringBuffer stringBuffer3 = new StringBuffer();
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#" + obj);
                stringBuffer3.toString();
                for (int i5 = 0; i5 < 100; i5 = i5 + 1 + 1) {
                }
                for (int i6 = 0; i6 < 100; i6 = i6 + 1 + 1) {
                }
            } catch (Exception e3) {
                e3.printStackTrace();
            }
            return isProviderEnabled_copy(obj, str);
        }
    }

    public static boolean isProviderEnabled_copy(Object obj, String str) {
        try {
            log("isProviderEnabled_copy", obj);
            StringBuffer stringBuffer = new StringBuffer();
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.toString();
            for (int i = 0; i < 100; i = i + 1 + 1) {
            }
            for (int i2 = 0; i2 < 100; i2 = i2 + 1 + 1) {
            }
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            try {
                StringBuffer stringBuffer2 = new StringBuffer();
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#" + obj);
                stringBuffer2.toString();
                for (int i3 = 0; i3 < 100; i3 = i3 + 1 + 1) {
                }
                for (int i4 = 0; i4 < 100; i4 = i4 + 1 + 1) {
                }
            } catch (Exception e2) {
                e2.printStackTrace();
            }
            try {
                StringBuffer stringBuffer3 = new StringBuffer();
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#" + obj);
                stringBuffer3.toString();
                for (int i5 = 0; i5 < 100; i5 = i5 + 1 + 1) {
                }
                for (int i6 = 0; i6 < 100; i6 = i6 + 1 + 1) {
                }
            } catch (Exception e3) {
                e3.printStackTrace();
            }
            return false;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void log(Object... objArr) {
        InjectLog.log("MockLocationHook", objArr);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void onRequestLocation(String str, int i, boolean z) {
        synchronized (mockListeners) {
            int i2 = 0;
            while (i2 < mockListeners.size()) {
                IOnMockLocationListener listener = mockListeners.get(i2);
                log("onRequestLocation: " + listener);
                try {
                    listener.onMockLocationChanged(str, i, z);
                } catch (Throwable th) {
                    if (th instanceof DeadObjectException) {
                        mockListeners.remove(listener);
                        i2--;
                    }
                    th.printStackTrace();
                }
                i2++;
            }
        }
    }

    public static boolean registerGnssStatusCallback(Object obj, Object obj2, String str) {
        log("registerGnssStatusCallback", str, obj2);
        if (CallingProcessUtils.isCallingSystemUid()) {
            log("registerGnssStatusCallback System call");
            try {
                return registerGnssStatusCallback_bak(obj, obj2, str);
            } catch (Throwable th) {
                th.printStackTrace();
                return true;
            }
        }
        Object objNewProxyInstance = Proxy.newProxyInstance(obj.getClass().getClassLoader(), new Class[]{iGnssStatusListenerClass}, new GnssStatusCallbackProxy(str, obj2, obj));
        addIGnssStatusListener(str, obj2, objNewProxyInstance);
        try {
            return registerGnssStatusCallback_bak(obj, objNewProxyInstance, str);
        } catch (Throwable th2) {
            th2.printStackTrace();
            try {
                StringBuffer stringBuffer = new StringBuffer();
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#" + obj);
                stringBuffer.toString();
                for (int i = 0; i < 100; i = i + 1 + 1) {
                }
                for (int i2 = 0; i2 < 100; i2 = i2 + 1 + 1) {
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                StringBuffer stringBuffer2 = new StringBuffer();
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#" + obj);
                stringBuffer2.toString();
                for (int i3 = 0; i3 < 100; i3 = i3 + 1 + 1) {
                }
                for (int i4 = 0; i4 < 100; i4 = i4 + 1 + 1) {
                }
            } catch (Exception e2) {
                e2.printStackTrace();
            }
            return true;
        }
    }

    public static boolean registerGnssStatusCallback_R(Object obj, Object obj2, String str, String str2) {
        log("registerGnssStatusCallback_R", str, obj2);
        if (CallingProcessUtils.isCallingSystemUid()) {
            log("registerGnssStatusCallback System call");
            try {
                return registerGnssStatusCallback_R_bak(obj, obj2, str, str2);
            } catch (Throwable th) {
                th.printStackTrace();
                return true;
            }
        }
        Object objNewProxyInstance = Proxy.newProxyInstance(obj.getClass().getClassLoader(), new Class[]{iGnssStatusListenerClass}, new GnssStatusCallbackRProxy(str, obj2, obj));
        addIGnssStatusListener(str, obj2, objNewProxyInstance);
        try {
            return registerGnssStatusCallback_R_bak(obj, objNewProxyInstance, str, str2);
        } catch (Throwable th2) {
            th2.printStackTrace();
            try {
                StringBuffer stringBuffer = new StringBuffer();
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#");
                stringBuffer.append("#" + obj);
                stringBuffer.toString();
                for (int i = 0; i < 100; i = i + 1 + 1) {
                }
                for (int i2 = 0; i2 < 100; i2 = i2 + 1 + 1) {
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                StringBuffer stringBuffer2 = new StringBuffer();
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#" + obj);
                stringBuffer2.toString();
                for (int i3 = 0; i3 < 100; i3 = i3 + 1 + 1) {
                }
                for (int i4 = 0; i4 < 100; i4 = i4 + 1 + 1) {
                }
            } catch (Exception e2) {
                e2.printStackTrace();
            }
            return true;
        }
    }

    public static boolean registerGnssStatusCallback_R_bak(Object obj, Object obj2, String str, String str2) {
        try {
            log("registerGnssStatusCallback_R_bak", obj, str);
            StringBuffer stringBuffer = new StringBuffer();
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.toString();
            for (int i = 0; i < 100; i = i + 1 + 1) {
            }
            for (int i2 = 0; i2 < 100; i2 = i2 + 1 + 1) {
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            StringBuffer stringBuffer2 = new StringBuffer();
            stringBuffer2.append("#");
            stringBuffer2.append("#");
            stringBuffer2.append("#");
            stringBuffer2.append("#");
            stringBuffer2.append("#");
            stringBuffer2.append("#" + obj);
            stringBuffer2.toString();
            for (int i3 = 0; i3 < 100; i3 = i3 + 1 + 1) {
            }
            for (int i4 = 0; i4 < 100; i4 = i4 + 1 + 1) {
            }
        } catch (Exception e2) {
            e2.printStackTrace();
        }
        try {
            StringBuffer stringBuffer3 = new StringBuffer();
            stringBuffer3.append("#");
            stringBuffer3.append("#");
            stringBuffer3.append("#");
            stringBuffer3.append("#");
            stringBuffer3.append("#");
            stringBuffer3.append("#" + obj);
            stringBuffer3.toString();
            for (int i5 = 0; i5 < 100; i5 = i5 + 1 + 1) {
            }
            for (int i6 = 0; i6 < 100; i6 = i6 + 1 + 1) {
            }
        } catch (Exception e3) {
            e3.printStackTrace();
        }
        try {
            StringBuffer stringBuffer4 = new StringBuffer();
            stringBuffer4.append("#");
            stringBuffer4.append("#");
            stringBuffer4.append("#");
            stringBuffer4.append("#");
            stringBuffer4.append("#");
            stringBuffer4.append("#" + obj);
            stringBuffer4.toString();
            for (int i7 = 0; i7 < 100; i7 = i7 + 1 + 1) {
            }
            for (int i8 = 0; i8 < 100; i8 = i8 + 1 + 1) {
            }
        } catch (Exception e4) {
            e4.printStackTrace();
        }
        return registerGnssStatusCallback_R_copy(obj, obj2, str, str2);
    }

    public static boolean registerGnssStatusCallback_R_copy(Object obj, Object obj2, String str, String str2) {
        try {
            log("registerGnssStatusCallback_R_copy", obj);
            StringBuffer stringBuffer = new StringBuffer();
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.toString();
            for (int i = 0; i < 100; i = i + 1 + 1) {
            }
            for (int i2 = 0; i2 < 100; i2 = i2 + 1 + 1) {
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            StringBuffer stringBuffer2 = new StringBuffer();
            stringBuffer2.append("#");
            stringBuffer2.append("#");
            stringBuffer2.append("#");
            stringBuffer2.append("#");
            stringBuffer2.append("#");
            stringBuffer2.append("#" + obj);
            stringBuffer2.toString();
            for (int i3 = 0; i3 < 100; i3 = i3 + 1 + 1) {
            }
            for (int i4 = 0; i4 < 100; i4 = i4 + 1 + 1) {
            }
        } catch (Exception e2) {
            e2.printStackTrace();
        }
        try {
            StringBuffer stringBuffer3 = new StringBuffer();
            stringBuffer3.append("#");
            stringBuffer3.append("#");
            stringBuffer3.append("#");
            stringBuffer3.append("#");
            stringBuffer3.append("#");
            stringBuffer3.append("#" + obj);
            stringBuffer3.toString();
            for (int i5 = 0; i5 < 100; i5 = i5 + 1 + 1) {
            }
            for (int i6 = 0; i6 < 100; i6 = i6 + 1 + 1) {
            }
        } catch (Exception e3) {
            e3.printStackTrace();
        }
        try {
            StringBuffer stringBuffer4 = new StringBuffer();
            stringBuffer4.append("#");
            stringBuffer4.append("#");
            stringBuffer4.append("#");
            stringBuffer4.append("#");
            stringBuffer4.append("#");
            stringBuffer4.append("#" + obj);
            stringBuffer4.toString();
            for (int i7 = 0; i7 < 100; i7 = i7 + 1 + 1) {
            }
            for (int i8 = 0; i8 < 100; i8 = i8 + 1 + 1) {
            }
        } catch (Exception e4) {
            e4.printStackTrace();
        }
        return true;
    }

    public static boolean registerGnssStatusCallback_bak(Object obj, Object obj2, String str) {
        try {
            log("registerGnssStatusCallback_bak", obj, str);
            StringBuffer stringBuffer = new StringBuffer();
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.toString();
            for (int i = 0; i < 100; i = i + 1 + 1) {
            }
            for (int i2 = 0; i2 < 100; i2 = i2 + 1 + 1) {
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            StringBuffer stringBuffer2 = new StringBuffer();
            stringBuffer2.append("#");
            stringBuffer2.append("#");
            stringBuffer2.append("#");
            stringBuffer2.append("#");
            stringBuffer2.append("#");
            stringBuffer2.append("#" + obj);
            stringBuffer2.toString();
            for (int i3 = 0; i3 < 100; i3 = i3 + 1 + 1) {
            }
            for (int i4 = 0; i4 < 100; i4 = i4 + 1 + 1) {
            }
        } catch (Exception e2) {
            e2.printStackTrace();
        }
        try {
            StringBuffer stringBuffer3 = new StringBuffer();
            stringBuffer3.append("#");
            stringBuffer3.append("#");
            stringBuffer3.append("#");
            stringBuffer3.append("#");
            stringBuffer3.append("#");
            stringBuffer3.append("#" + obj);
            stringBuffer3.toString();
            for (int i5 = 0; i5 < 100; i5 = i5 + 1 + 1) {
            }
            for (int i6 = 0; i6 < 100; i6 = i6 + 1 + 1) {
            }
        } catch (Exception e3) {
            e3.printStackTrace();
        }
        return registerGnssStatusCallback_copy(obj, obj2, str);
    }

    public static boolean registerGnssStatusCallback_copy(Object obj, Object obj2, String str) {
        try {
            log("registerGnssStatusCallback_copy", obj);
            StringBuffer stringBuffer = new StringBuffer();
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.toString();
            for (int i = 0; i < 100; i = i + 1 + 1) {
            }
            for (int i2 = 0; i2 < 100; i2 = i2 + 1 + 1) {
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            StringBuffer stringBuffer2 = new StringBuffer();
            stringBuffer2.append("#");
            stringBuffer2.append("#");
            stringBuffer2.append("#");
            stringBuffer2.append("#");
            stringBuffer2.append("#");
            stringBuffer2.append("#" + obj);
            stringBuffer2.toString();
            for (int i3 = 0; i3 < 100; i3 = i3 + 1 + 1) {
            }
            for (int i4 = 0; i4 < 100; i4 = i4 + 1 + 1) {
            }
        } catch (Exception e2) {
            e2.printStackTrace();
        }
        try {
            StringBuffer stringBuffer3 = new StringBuffer();
            stringBuffer3.append("#");
            stringBuffer3.append("#");
            stringBuffer3.append("#");
            stringBuffer3.append("#");
            stringBuffer3.append("#");
            stringBuffer3.append("#" + obj);
            stringBuffer3.toString();
            for (int i5 = 0; i5 < 100; i5 = i5 + 1 + 1) {
            }
            for (int i6 = 0; i6 < 100; i6 = i6 + 1 + 1) {
            }
        } catch (Exception e3) {
            e3.printStackTrace();
        }
        return true;
    }

    public static void removeGpsStatusListener(Object obj, Object obj2) {
        removeIGpsStatusListener(obj2);
        try {
            removeGpsStatusListener_bak(obj, obj2);
        } catch (Throwable th) {
            th.printStackTrace();
        }
        Object proxyListener = getProxyListener(obj2);
        if (proxyListener != null) {
            removeProxyListener(obj2);
            try {
                removeGpsStatusListener_bak(obj, proxyListener);
            } catch (Throwable th2) {
                th2.printStackTrace();
            }
        }
        try {
            StringBuffer stringBuffer = new StringBuffer();
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#" + obj);
            stringBuffer.toString();
            for (int i = 0; i < 100; i = i + 1 + 1) {
            }
            for (int i2 = 0; i2 < 100; i2 = i2 + 1 + 1) {
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            StringBuffer stringBuffer2 = new StringBuffer();
            stringBuffer2.append("#");
            stringBuffer2.append("#");
            stringBuffer2.append("#");
            stringBuffer2.append("#");
            stringBuffer2.append("#");
            stringBuffer2.append("#" + obj);
            stringBuffer2.toString();
            for (int i3 = 0; i3 < 100; i3 = i3 + 1 + 1) {
            }
            for (int i4 = 0; i4 < 100; i4 = i4 + 1 + 1) {
            }
        } catch (Exception e2) {
            e2.printStackTrace();
        }
        try {
            StringBuffer stringBuffer3 = new StringBuffer();
            stringBuffer3.append("#");
            stringBuffer3.append("#");
            stringBuffer3.append("#");
            stringBuffer3.append("#");
            stringBuffer3.append("#");
            stringBuffer3.append("#" + obj);
            stringBuffer3.toString();
            for (int i5 = 0; i5 < 100; i5 = i5 + 1 + 1) {
            }
            for (int i6 = 0; i6 < 100; i6 = i6 + 1 + 1) {
            }
        } catch (Exception e3) {
            e3.printStackTrace();
        }
    }

    public static void removeGpsStatusListener_bak(Object obj, Object obj2) {
        try {
            log("removeGpsStatusListener_bak", obj);
            removeGpsStatusListener_copy(obj, obj2);
            StringBuffer stringBuffer = new StringBuffer();
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.toString();
            for (int i = 0; i < 100; i = i + 1 + 1) {
            }
            for (int i2 = 0; i2 < 100; i2 = i2 + 1 + 1) {
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            StringBuffer stringBuffer2 = new StringBuffer();
            stringBuffer2.append("#");
            stringBuffer2.append("#");
            stringBuffer2.append("#");
            stringBuffer2.append("#");
            stringBuffer2.append("#");
            stringBuffer2.append("#" + obj);
            stringBuffer2.toString();
            for (int i3 = 0; i3 < 100; i3 = i3 + 1 + 1) {
            }
            for (int i4 = 0; i4 < 100; i4 = i4 + 1 + 1) {
            }
        } catch (Exception e2) {
            e2.printStackTrace();
        }
        try {
            StringBuffer stringBuffer3 = new StringBuffer();
            stringBuffer3.append("#");
            stringBuffer3.append("#");
            stringBuffer3.append("#");
            stringBuffer3.append("#");
            stringBuffer3.append("#");
            stringBuffer3.append("#" + obj);
            stringBuffer3.toString();
            for (int i5 = 0; i5 < 100; i5 = i5 + 1 + 1) {
            }
            for (int i6 = 0; i6 < 100; i6 = i6 + 1 + 1) {
            }
        } catch (Exception e3) {
            e3.printStackTrace();
        }
        try {
            StringBuffer stringBuffer4 = new StringBuffer();
            stringBuffer4.append("#");
            stringBuffer4.append("#");
            stringBuffer4.append("#");
            stringBuffer4.append("#");
            stringBuffer4.append("#");
            stringBuffer4.append("#" + obj);
            stringBuffer4.toString();
            for (int i7 = 0; i7 < 100; i7 = i7 + 1 + 1) {
            }
            for (int i8 = 0; i8 < 100; i8 = i8 + 1 + 1) {
            }
        } catch (Exception e4) {
            e4.printStackTrace();
        }
    }

    public static void removeGpsStatusListener_copy(Object obj, Object obj2) {
        try {
            log("removeGpsStatusListener_copy", obj);
            StringBuffer stringBuffer = new StringBuffer();
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.toString();
            for (int i = 0; i < 100; i = i + 1 + 1) {
            }
            for (int i2 = 0; i2 < 100; i2 = i2 + 1 + 1) {
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            StringBuffer stringBuffer2 = new StringBuffer();
            stringBuffer2.append("#");
            stringBuffer2.append("#");
            stringBuffer2.append("#");
            stringBuffer2.append("#");
            stringBuffer2.append("#");
            stringBuffer2.append("#" + obj);
            stringBuffer2.toString();
            for (int i3 = 0; i3 < 100; i3 = i3 + 1 + 1) {
            }
            for (int i4 = 0; i4 < 100; i4 = i4 + 1 + 1) {
            }
        } catch (Exception e2) {
            e2.printStackTrace();
        }
        try {
            StringBuffer stringBuffer3 = new StringBuffer();
            stringBuffer3.append("#");
            stringBuffer3.append("#");
            stringBuffer3.append("#");
            stringBuffer3.append("#");
            stringBuffer3.append("#");
            stringBuffer3.append("#" + obj);
            stringBuffer3.toString();
            for (int i5 = 0; i5 < 100; i5 = i5 + 1 + 1) {
            }
            for (int i6 = 0; i6 < 100; i6 = i6 + 1 + 1) {
            }
        } catch (Exception e3) {
            e3.printStackTrace();
        }
        try {
            StringBuffer stringBuffer4 = new StringBuffer();
            stringBuffer4.append("#");
            stringBuffer4.append("#");
            stringBuffer4.append("#");
            stringBuffer4.append("#");
            stringBuffer4.append("#");
            stringBuffer4.append("#" + obj);
            stringBuffer4.toString();
            for (int i7 = 0; i7 < 100; i7 = i7 + 1 + 1) {
            }
            for (int i8 = 0; i8 < 100; i8 = i8 + 1 + 1) {
            }
        } catch (Exception e4) {
            e4.printStackTrace();
        }
    }

    private static void removeIGnssStatusListener(Object obj) {
        try {
            HashMap<Object, String> map = gnssStatusListenerPackages;
            synchronized (map) {
                Iterator<Object> it = map.keySet().iterator();
                while (true) {
                    if (!it.hasNext()) {
                        break;
                    }
                    Object next = it.next();
                    if (next == obj) {
                        gnssStatusListenerPackages.remove(next);
                        break;
                    }
                }
            }
        } catch (Exception unused) {
        }
    }

    private static void removeIGpsStatusListener(Object obj) {
        try {
            HashMap<Object, String> map = gpsStatusListenerPackages;
            synchronized (map) {
                Iterator<Object> it = map.keySet().iterator();
                while (true) {
                    if (!it.hasNext()) {
                        break;
                    }
                    Object next = it.next();
                    if (next == obj) {
                        gpsStatusListenerPackages.remove(next);
                        break;
                    }
                }
            }
        } catch (Exception unused) {
        }
    }

    public static void removeOnMockListener(IOnMockLocationListener listener) {
        synchronized (mockListeners) {
            mockListeners.remove(listener);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void removeProxyListener(Object obj) {
        proxyListeners.remove(obj);
    }

    private static void removeReceiver(Object obj) {
        try {
            HashMap<Object, String> map = receiverPackages;
            synchronized (map) {
                Iterator<Object> it = map.keySet().iterator();
                while (true) {
                    if (!it.hasNext()) {
                        break;
                    }
                    Object next = it.next();
                    if (next == obj) {
                        receiverPackages.remove(next);
                        break;
                    }
                }
            }
            HashMap<Object, String> map2 = receiverProviders;
            synchronized (map2) {
                map2.remove(obj);
            }
        } catch (Exception unused) {
        }
    }

    private static void removeReceiver(String str) {
        HashMap<Object, String> map = receiverPackages;
        synchronized (map) {
            Iterator<Object> it = map.keySet().iterator();
            while (true) {
                if (!it.hasNext()) {
                    break;
                }
                Object next = it.next();
                HashMap<Object, String> map2 = receiverPackages;
                if (map2.get(next).equals(str)) {
                    map2.remove(next);
                    break;
                }
            }
        }
    }

    public static void removeUpdatesLocked(Object obj, Object obj2) {
        log("removeUpdatesLocked: ", obj2);
        try {
            if (locationManagerReceiverClass != null && isHook()) {
                ReflectionUtils.invokeMethod(obj2, locationManagerReceiverClass, "callLocationChangedLocked", new Class[]{Location.class}, new Object[]{new Location(getTempLocation())});
            }
        } catch (Throwable th) {
            th.printStackTrace();
        }
        removeReceiver(obj2);
        try {
            removeUpdatesLocked_bak(obj, obj2);
        } catch (Throwable th2) {
            th2.printStackTrace();
        }
        Object proxyListener = getProxyListener(obj2);
        if (proxyListener != null) {
            removeProxyListener(obj2);
            try {
                removeUpdatesLocked_bak(obj, proxyListener);
            } catch (Throwable th3) {
                th3.printStackTrace();
            }
        }
        try {
            StringBuffer stringBuffer = new StringBuffer();
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#" + obj);
            stringBuffer.toString();
            for (int i = 0; i < 100; i = i + 1 + 1) {
            }
            for (int i2 = 0; i2 < 100; i2 = i2 + 1 + 1) {
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            StringBuffer stringBuffer2 = new StringBuffer();
            stringBuffer2.append("#");
            stringBuffer2.append("#");
            stringBuffer2.append("#");
            stringBuffer2.append("#");
            stringBuffer2.append("#");
            stringBuffer2.append("#" + obj);
            stringBuffer2.toString();
            for (int i3 = 0; i3 < 100; i3 = i3 + 1 + 1) {
            }
            for (int i4 = 0; i4 < 100; i4 = i4 + 1 + 1) {
            }
        } catch (Exception e2) {
            e2.printStackTrace();
        }
        try {
            StringBuffer stringBuffer3 = new StringBuffer();
            stringBuffer3.append("#");
            stringBuffer3.append("#");
            stringBuffer3.append("#");
            stringBuffer3.append("#");
            stringBuffer3.append("#");
            stringBuffer3.append("#" + obj);
            stringBuffer3.toString();
            for (int i5 = 0; i5 < 100; i5 = i5 + 1 + 1) {
            }
            for (int i6 = 0; i6 < 100; i6 = i6 + 1 + 1) {
            }
        } catch (Exception e3) {
            e3.printStackTrace();
        }
    }

    public static void removeUpdatesLocked_bak(Object obj, Object obj2) {
        int i = 0;
        try {
            log("removeUpdatesLocked_bak", obj);
            removeUpdatesLocked_copy(obj, obj2);
            StringBuffer stringBuffer = new StringBuffer();
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.toString();
            for (int i2 = 0; i2 < 100; i2 = i2 + 1 + 1) {
            }
            while (i < 100) {
                i = i + 1 + 1;
            }
        } catch (Exception e) {
            e.printStackTrace();
            try {
                StringBuffer stringBuffer2 = new StringBuffer();
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#" + obj);
                stringBuffer2.toString();
                for (int i3 = 0; i3 < 100; i3 = i3 + 1 + 1) {
                }
                for (int i4 = 0; i4 < 100; i4 = i4 + 1 + 1) {
                }
            } catch (Exception e2) {
                e2.printStackTrace();
            }
            try {
                StringBuffer stringBuffer3 = new StringBuffer();
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#" + obj);
                stringBuffer3.toString();
                for (int i5 = 0; i5 < 100; i5 = i5 + 1 + 1) {
                }
                for (int i6 = 0; i6 < 100; i6 = i6 + 1 + 1) {
                }
            } catch (Exception e3) {
                e3.printStackTrace();
            }
            try {
                StringBuffer stringBuffer4 = new StringBuffer();
                stringBuffer4.append("#");
                stringBuffer4.append("#");
                stringBuffer4.append("#");
                stringBuffer4.append("#");
                stringBuffer4.append("#");
                stringBuffer4.append("#" + obj);
                stringBuffer4.toString();
                for (int i7 = 0; i7 < 100; i7 = i7 + 1 + 1) {
                }
                for (int i8 = 0; i8 < 100; i8 = i8 + 1 + 1) {
                }
            } catch (Exception e4) {
                e4.printStackTrace();
            }
            try {
                StringBuffer stringBuffer5 = new StringBuffer();
                stringBuffer5.append("#");
                stringBuffer5.append("#");
                stringBuffer5.append("#");
                stringBuffer5.append("#");
                stringBuffer5.append("#");
                stringBuffer5.append("#" + obj);
                stringBuffer5.toString();
                for (int i9 = 0; i9 < 100; i9 = i9 + 1 + 1) {
                }
                while (i < 100) {
                    i = i + 1 + 1;
                }
            } catch (Exception e5) {
                e5.printStackTrace();
            }
        }
    }

    public static void removeUpdatesLocked_copy(Object obj, Object obj2) {
        int i = 0;
        try {
            log("removeUpdatesLocked_copy", obj);
            StringBuffer stringBuffer = new StringBuffer();
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.toString();
            for (int i2 = 0; i2 < 100; i2 = i2 + 1 + 1) {
            }
            while (i < 100) {
                i = i + 1 + 1;
            }
        } catch (Exception e) {
            e.printStackTrace();
            try {
                StringBuffer stringBuffer2 = new StringBuffer();
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#" + obj);
                stringBuffer2.toString();
                for (int i3 = 0; i3 < 100; i3 = i3 + 1 + 1) {
                }
                for (int i4 = 0; i4 < 100; i4 = i4 + 1 + 1) {
                }
            } catch (Exception e2) {
                e2.printStackTrace();
            }
            try {
                StringBuffer stringBuffer3 = new StringBuffer();
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#" + obj);
                stringBuffer3.toString();
                for (int i5 = 0; i5 < 100; i5 = i5 + 1 + 1) {
                }
                for (int i6 = 0; i6 < 100; i6 = i6 + 1 + 1) {
                }
            } catch (Exception e3) {
                e3.printStackTrace();
            }
            try {
                StringBuffer stringBuffer4 = new StringBuffer();
                stringBuffer4.append("#");
                stringBuffer4.append("#");
                stringBuffer4.append("#");
                stringBuffer4.append("#");
                stringBuffer4.append("#");
                stringBuffer4.append("#" + obj);
                stringBuffer4.toString();
                for (int i7 = 0; i7 < 100; i7 = i7 + 1 + 1) {
                }
                for (int i8 = 0; i8 < 100; i8 = i8 + 1 + 1) {
                }
            } catch (Exception e4) {
                e4.printStackTrace();
            }
            try {
                StringBuffer stringBuffer5 = new StringBuffer();
                stringBuffer5.append("#");
                stringBuffer5.append("#");
                stringBuffer5.append("#");
                stringBuffer5.append("#");
                stringBuffer5.append("#");
                stringBuffer5.append("#" + obj);
                stringBuffer5.toString();
                for (int i9 = 0; i9 < 100; i9 = i9 + 1 + 1) {
                }
                while (i < 100) {
                    i = i + 1 + 1;
                }
            } catch (Exception e5) {
                e5.printStackTrace();
            }
        }
    }

    public static void requestLocationUpdatesLocked(Object obj, Object obj2, Object obj3, int i, int i2, String str) {
        log("requestLocationUpdatesLocked: ", obj3, str, Integer.valueOf(i), Integer.valueOf(i2));
        addReceiver(str, obj3, null, getLocationRequestProvider(obj2));
        try {
            requestLocationUpdatesLocked_bak(obj, obj2, obj3, i, i2, str);
        } catch (Throwable th) {
            th.printStackTrace();
        }
        try {
            onRequestLocation(str, i, CallingProcessUtils.isSystemUid(i2));
        } catch (Throwable th2) {
            th2.printStackTrace();
        }
        try {
            StringBuffer stringBuffer = new StringBuffer();
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#" + obj);
            stringBuffer.toString();
            for (int i3 = 0; i3 < 100; i3 = i3 + 1 + 1) {
            }
            for (int i4 = 0; i4 < 100; i4 = i4 + 1 + 1) {
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            StringBuffer stringBuffer2 = new StringBuffer();
            stringBuffer2.append("#");
            stringBuffer2.append("#");
            stringBuffer2.append("#");
            stringBuffer2.append("#");
            stringBuffer2.append("#");
            stringBuffer2.append("#" + obj);
            stringBuffer2.toString();
            for (int i5 = 0; i5 < 100; i5 = i5 + 1 + 1) {
            }
            for (int i6 = 0; i6 < 100; i6 = i6 + 1 + 1) {
            }
        } catch (Exception e2) {
            e2.printStackTrace();
        }
    }

    public static void requestLocationUpdatesLocked_Q(Object obj, Object obj2, Object obj3, int i, String str) {
        log("requestLocationUpdatesLocked_Q: ", obj3, str, Integer.valueOf(i));
        addReceiver(str, obj3, null, getLocationRequestProvider(obj2));
        try {
            requestLocationUpdatesLocked_Q_bak(obj, obj2, obj3, i, str);
        } catch (Throwable th) {
            th.printStackTrace();
        }
        try {
            onRequestLocation(str, i, CallingProcessUtils.isSystemUid(i));
        } catch (Throwable th2) {
            th2.printStackTrace();
        }
        try {
            StringBuffer stringBuffer = new StringBuffer();
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#" + obj);
            stringBuffer.toString();
            for (int i2 = 0; i2 < 100; i2 = i2 + 1 + 1) {
            }
            for (int i3 = 0; i3 < 100; i3 = i3 + 1 + 1) {
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            StringBuffer stringBuffer2 = new StringBuffer();
            stringBuffer2.append("#");
            stringBuffer2.append("#");
            stringBuffer2.append("#");
            stringBuffer2.append("#");
            stringBuffer2.append("#");
            stringBuffer2.append("#" + obj);
            stringBuffer2.toString();
            for (int i4 = 0; i4 < 100; i4 = i4 + 1 + 1) {
            }
            for (int i5 = 0; i5 < 100; i5 = i5 + 1 + 1) {
            }
        } catch (Exception e2) {
            e2.printStackTrace();
        }
        try {
            StringBuffer stringBuffer3 = new StringBuffer();
            stringBuffer3.append("#");
            stringBuffer3.append("#");
            stringBuffer3.append("#");
            stringBuffer3.append("#");
            stringBuffer3.append("#");
            stringBuffer3.append("#" + obj);
            stringBuffer3.toString();
            for (int i6 = 0; i6 < 100; i6 = i6 + 1 + 1) {
            }
            for (int i7 = 0; i7 < 100; i7 = i7 + 1 + 1) {
            }
        } catch (Exception e3) {
            e3.printStackTrace();
        }
        try {
            StringBuffer stringBuffer4 = new StringBuffer();
            stringBuffer4.append("#");
            stringBuffer4.append("#");
            stringBuffer4.append("#");
            stringBuffer4.append("#");
            stringBuffer4.append("#");
            stringBuffer4.append("#" + obj);
            stringBuffer4.toString();
            for (int i8 = 0; i8 < 100; i8 = i8 + 1 + 1) {
            }
            for (int i9 = 0; i9 < 100; i9 = i9 + 1 + 1) {
            }
        } catch (Exception e4) {
            e4.printStackTrace();
        }
    }

    public static void requestLocationUpdatesLocked_Q_bak(Object obj, Object obj2, Object obj3, int i, String str) {
        int i2 = 0;
        try {
            log("requestLocationUpdatesLocked_bak", obj);
            requestLocationUpdatesLocked_Q_copy(obj, obj2, obj3, i, str);
            StringBuffer stringBuffer = new StringBuffer();
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.toString();
            while (i2 < 100) {
                i2 = i2 + 1 + 1;
            }
        } catch (Exception e) {
            e.printStackTrace();
            try {
                StringBuffer stringBuffer2 = new StringBuffer();
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#" + obj);
                stringBuffer2.toString();
                for (int i3 = 0; i3 < 100; i3 = i3 + 1 + 1) {
                }
                for (int i4 = 0; i4 < 100; i4 = i4 + 1 + 1) {
                }
            } catch (Exception e2) {
                e2.printStackTrace();
            }
            try {
                StringBuffer stringBuffer3 = new StringBuffer();
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#" + obj);
                stringBuffer3.toString();
                for (int i5 = 0; i5 < 100; i5 = i5 + 1 + 1) {
                }
                for (int i6 = 0; i6 < 100; i6 = i6 + 1 + 1) {
                }
            } catch (Exception e3) {
                e3.printStackTrace();
            }
            try {
                StringBuffer stringBuffer4 = new StringBuffer();
                stringBuffer4.append("#");
                stringBuffer4.append("#");
                stringBuffer4.append("#");
                stringBuffer4.append("#");
                stringBuffer4.append("#");
                stringBuffer4.append("#" + obj);
                stringBuffer4.toString();
                for (int i7 = 0; i7 < 100; i7 = i7 + 1 + 1) {
                }
                while (i2 < 100) {
                    i2 = i2 + 1 + 1;
                }
            } catch (Exception e4) {
                e4.printStackTrace();
            }
        }
    }

    public static void requestLocationUpdatesLocked_Q_copy(Object obj, Object obj2, Object obj3, int i, String str) {
        int i2 = 0;
        try {
            log("requestLocationUpdatesLocked_copy", obj);
            StringBuffer stringBuffer = new StringBuffer();
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.toString();
            while (i2 < 100) {
                i2 = i2 + 1 + 1;
            }
        } catch (Exception e) {
            e.printStackTrace();
            try {
                StringBuffer stringBuffer2 = new StringBuffer();
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#" + obj);
                stringBuffer2.toString();
                for (int i3 = 0; i3 < 100; i3 = i3 + 1 + 1) {
                }
                for (int i4 = 0; i4 < 100; i4 = i4 + 1 + 1) {
                }
            } catch (Exception e2) {
                e2.printStackTrace();
            }
            try {
                StringBuffer stringBuffer3 = new StringBuffer();
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#" + obj);
                stringBuffer3.toString();
                for (int i5 = 0; i5 < 100; i5 = i5 + 1 + 1) {
                }
                for (int i6 = 0; i6 < 100; i6 = i6 + 1 + 1) {
                }
            } catch (Exception e3) {
                e3.printStackTrace();
            }
            try {
                StringBuffer stringBuffer4 = new StringBuffer();
                stringBuffer4.append("#");
                stringBuffer4.append("#");
                stringBuffer4.append("#");
                stringBuffer4.append("#");
                stringBuffer4.append("#");
                stringBuffer4.append("#" + obj);
                stringBuffer4.toString();
                for (int i7 = 0; i7 < 100; i7 = i7 + 1 + 1) {
                }
                for (int i8 = 0; i8 < 100; i8 = i8 + 1 + 1) {
                }
            } catch (Exception e4) {
                e4.printStackTrace();
            }
            try {
                StringBuffer stringBuffer5 = new StringBuffer();
                stringBuffer5.append("#");
                stringBuffer5.append("#");
                stringBuffer5.append("#");
                stringBuffer5.append("#");
                stringBuffer5.append("#");
                stringBuffer5.append("#" + obj);
                stringBuffer5.toString();
                for (int i9 = 0; i9 < 100; i9 = i9 + 1 + 1) {
                }
                while (i2 < 100) {
                    i2 = i2 + 1 + 1;
                }
            } catch (Exception e5) {
                e5.printStackTrace();
            }
        }
    }

    public static void requestLocationUpdatesLocked_R(Object obj, Object obj2, Object obj3) {
        log("requestLocationUpdatesLocked_R: ", obj3);
        String strM88 = CallingProcessUtils.getPackageNameForPid(Binder.getCallingPid());
        log("requestLocationUpdatesLocked_R.packageName: ", strM88);
        addReceiver(strM88, obj3, null, getLocationRequestProvider(obj2));
        try {
            requestLocationUpdatesLocked_R_bak(obj, obj2, obj3);
        } catch (Throwable th) {
            th.printStackTrace();
        }
        try {
            onRequestLocation(strM88, Binder.getCallingUid(), CallingProcessUtils.isSystemUid(Binder.getCallingUid()));
        } catch (Throwable th2) {
            th2.printStackTrace();
        }
        try {
            StringBuffer stringBuffer = new StringBuffer();
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#" + obj);
            stringBuffer.toString();
            for (int i = 0; i < 100; i = i + 1 + 1) {
            }
            for (int i2 = 0; i2 < 100; i2 = i2 + 1 + 1) {
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            StringBuffer stringBuffer2 = new StringBuffer();
            stringBuffer2.append("#");
            stringBuffer2.append("#");
            stringBuffer2.append("#");
            stringBuffer2.append("#");
            stringBuffer2.append("#");
            stringBuffer2.append("#" + obj);
            stringBuffer2.toString();
            for (int i3 = 0; i3 < 100; i3 = i3 + 1 + 1) {
            }
            for (int i4 = 0; i4 < 100; i4 = i4 + 1 + 1) {
            }
        } catch (Exception e2) {
            e2.printStackTrace();
        }
        try {
            StringBuffer stringBuffer3 = new StringBuffer();
            stringBuffer3.append("#");
            stringBuffer3.append("#");
            stringBuffer3.append("#");
            stringBuffer3.append("#");
            stringBuffer3.append("#");
            stringBuffer3.append("#" + obj);
            stringBuffer3.toString();
            for (int i5 = 0; i5 < 100; i5 = i5 + 1 + 1) {
            }
            for (int i6 = 0; i6 < 100; i6 = i6 + 1 + 1) {
            }
        } catch (Exception e3) {
            e3.printStackTrace();
        }
        try {
            StringBuffer stringBuffer4 = new StringBuffer();
            stringBuffer4.append("#");
            stringBuffer4.append("#");
            stringBuffer4.append("#");
            stringBuffer4.append("#");
            stringBuffer4.append("#");
            stringBuffer4.append("#" + obj);
            stringBuffer4.toString();
            for (int i7 = 0; i7 < 100; i7 = i7 + 1 + 1) {
            }
            for (int i8 = 0; i8 < 100; i8 = i8 + 1 + 1) {
            }
        } catch (Exception e4) {
            e4.printStackTrace();
        }
    }

    public static void requestLocationUpdatesLocked_R_bak(Object obj, Object obj2, Object obj3) {
        int i = 0;
        try {
            log("requestLocationUpdatesLocked_R_bak", obj);
            requestLocationUpdatesLocked_R_copy(obj, obj2, obj3);
            StringBuffer stringBuffer = new StringBuffer();
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.toString();
            while (i < 100) {
                i = i + 1 + 1;
            }
        } catch (Exception e) {
            e.printStackTrace();
            try {
                StringBuffer stringBuffer2 = new StringBuffer();
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#" + obj);
                stringBuffer2.toString();
                for (int i2 = 0; i2 < 100; i2 = i2 + 1 + 1) {
                }
                for (int i3 = 0; i3 < 100; i3 = i3 + 1 + 1) {
                }
            } catch (Exception e2) {
                e2.printStackTrace();
            }
            try {
                StringBuffer stringBuffer3 = new StringBuffer();
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#" + obj);
                stringBuffer3.toString();
                for (int i4 = 0; i4 < 100; i4 = i4 + 1 + 1) {
                }
                for (int i5 = 0; i5 < 100; i5 = i5 + 1 + 1) {
                }
            } catch (Exception e3) {
                e3.printStackTrace();
            }
            try {
                StringBuffer stringBuffer4 = new StringBuffer();
                stringBuffer4.append("#");
                stringBuffer4.append("#");
                stringBuffer4.append("#");
                stringBuffer4.append("#");
                stringBuffer4.append("#");
                stringBuffer4.append("#" + obj);
                stringBuffer4.toString();
                for (int i6 = 0; i6 < 100; i6 = i6 + 1 + 1) {
                }
                while (i < 100) {
                    i = i + 1 + 1;
                }
            } catch (Exception e4) {
                e4.printStackTrace();
            }
        }
    }

    public static void requestLocationUpdatesLocked_R_copy(Object obj, Object obj2, Object obj3) {
        int i = 0;
        try {
            log("requestLocationUpdatesLocked_R_copy", obj);
            StringBuffer stringBuffer = new StringBuffer();
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.toString();
            while (i < 100) {
                i = i + 1 + 1;
            }
        } catch (Exception e) {
            e.printStackTrace();
            try {
                StringBuffer stringBuffer2 = new StringBuffer();
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#" + obj);
                stringBuffer2.toString();
                for (int i2 = 0; i2 < 100; i2 = i2 + 1 + 1) {
                }
                for (int i3 = 0; i3 < 100; i3 = i3 + 1 + 1) {
                }
            } catch (Exception e2) {
                e2.printStackTrace();
            }
            try {
                StringBuffer stringBuffer3 = new StringBuffer();
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#" + obj);
                stringBuffer3.toString();
                for (int i4 = 0; i4 < 100; i4 = i4 + 1 + 1) {
                }
                for (int i5 = 0; i5 < 100; i5 = i5 + 1 + 1) {
                }
            } catch (Exception e3) {
                e3.printStackTrace();
            }
            try {
                StringBuffer stringBuffer4 = new StringBuffer();
                stringBuffer4.append("#");
                stringBuffer4.append("#");
                stringBuffer4.append("#");
                stringBuffer4.append("#");
                stringBuffer4.append("#");
                stringBuffer4.append("#" + obj);
                stringBuffer4.toString();
                for (int i6 = 0; i6 < 100; i6 = i6 + 1 + 1) {
                }
                for (int i7 = 0; i7 < 100; i7 = i7 + 1 + 1) {
                }
            } catch (Exception e4) {
                e4.printStackTrace();
            }
            try {
                StringBuffer stringBuffer5 = new StringBuffer();
                stringBuffer5.append("#");
                stringBuffer5.append("#");
                stringBuffer5.append("#");
                stringBuffer5.append("#");
                stringBuffer5.append("#");
                stringBuffer5.append("#" + obj);
                stringBuffer5.toString();
                for (int i8 = 0; i8 < 100; i8 = i8 + 1 + 1) {
                }
                while (i < 100) {
                    i = i + 1 + 1;
                }
            } catch (Exception e5) {
                e5.printStackTrace();
            }
        }
    }

    public static void requestLocationUpdatesLocked_bak(Object obj, Object obj2, Object obj3, int i, int i2, String str) {
        int i3 = 0;
        try {
            log("requestLocationUpdatesLocked_bak", obj);
            requestLocationUpdatesLocked_copy(obj, obj2, obj3, i, i2, str);
            StringBuffer stringBuffer = new StringBuffer();
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.toString();
            for (int i4 = 0; i4 < i; i4 = i4 + 1 + 1) {
            }
            while (i3 < 100) {
                i3 = i3 + 1 + 1;
            }
        } catch (Exception e) {
            e.printStackTrace();
            try {
                StringBuffer stringBuffer2 = new StringBuffer();
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#" + obj);
                stringBuffer2.toString();
                for (int i5 = 0; i5 < 100; i5 = i5 + 1 + 1) {
                }
                for (int i6 = 0; i6 < 100; i6 = i6 + 1 + 1) {
                }
            } catch (Exception e2) {
                e2.printStackTrace();
            }
            try {
                StringBuffer stringBuffer3 = new StringBuffer();
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#" + obj);
                stringBuffer3.toString();
                for (int i7 = 0; i7 < 100; i7 = i7 + 1 + 1) {
                }
                for (int i8 = 0; i8 < 100; i8 = i8 + 1 + 1) {
                }
            } catch (Exception e3) {
                e3.printStackTrace();
            }
            try {
                StringBuffer stringBuffer4 = new StringBuffer();
                stringBuffer4.append("#");
                stringBuffer4.append("#");
                stringBuffer4.append("#");
                stringBuffer4.append("#");
                stringBuffer4.append("#");
                stringBuffer4.append("#" + obj);
                stringBuffer4.toString();
                for (int i9 = 0; i9 < 100; i9 = i9 + 1 + 1) {
                }
                while (i3 < 100) {
                    i3 = i3 + 1 + 1;
                }
            } catch (Exception e4) {
                e4.printStackTrace();
            }
        }
    }

    public static void requestLocationUpdatesLocked_copy(Object obj, Object obj2, Object obj3, int i, int i2, String str) {
        int i3 = 0;
        try {
            log("requestLocationUpdatesLocked_copy", obj);
            StringBuffer stringBuffer = new StringBuffer();
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.toString();
            for (int i4 = 0; i4 < i; i4 = i4 + 1 + 1) {
            }
            while (i3 < 100) {
                i3 = i3 + 1 + 1;
            }
        } catch (Exception e) {
            e.printStackTrace();
            try {
                StringBuffer stringBuffer2 = new StringBuffer();
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#");
                stringBuffer2.append("#" + obj);
                stringBuffer2.toString();
                for (int i5 = 0; i5 < 100; i5 = i5 + 1 + 1) {
                }
                for (int i6 = 0; i6 < 100; i6 = i6 + 1 + 1) {
                }
            } catch (Exception e2) {
                e2.printStackTrace();
            }
            try {
                StringBuffer stringBuffer3 = new StringBuffer();
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#");
                stringBuffer3.append("#" + obj);
                stringBuffer3.toString();
                for (int i7 = 0; i7 < 100; i7 = i7 + 1 + 1) {
                }
                for (int i8 = 0; i8 < 100; i8 = i8 + 1 + 1) {
                }
            } catch (Exception e3) {
                e3.printStackTrace();
            }
            try {
                StringBuffer stringBuffer4 = new StringBuffer();
                stringBuffer4.append("#");
                stringBuffer4.append("#");
                stringBuffer4.append("#");
                stringBuffer4.append("#");
                stringBuffer4.append("#");
                stringBuffer4.append("#" + obj);
                stringBuffer4.toString();
                for (int i9 = 0; i9 < 100; i9 = i9 + 1 + 1) {
                }
                while (i3 < 100) {
                    i3 = i3 + 1 + 1;
                }
            } catch (Exception e4) {
                e4.printStackTrace();
            }
        }
    }

    public static void setAllowMockPackages(List<String> list) {
        synchronized (mockPackageLock) {
            allowMockPackages = list;
        }
    }

    public static void setIntervalTimeout(long j) {
        mockIntervalMillis = j;
    }

    public static void setMockCells(List<CellTowerInfo> list) {
        mockCells = list;
    }

    public static void setMockGpsStatus(boolean z) {
        mockGpsStatusEnabled = z;
    }

    public static void setMockLocation(Location location) {
        if (location == null) {
            mockLocation.reset();
        } else {
            mockLocation.set(location);
            Bundle extras = location.getExtras();
            if (extras != null) {
                currentLocationSource = extras.getString("from", "loc");
                return;
            }
        }
        currentLocationSource = "loc";
    }

    public static void setSafeApps(List<String> list) {
        synchronized (mockPackageLock) {
            scopedAllowMockRules = list;
        }
    }

    public static void startMockLocation() {
        mocking = true;
    }

    public static void stopMockLocation() {
        mocking = false;
    }

    public static void unregisterGnssStatusCallback(Object obj, Object obj2) {
        removeIGnssStatusListener(obj2);
        try {
            unregisterGnssStatusCallback_bak(obj, obj2);
        } catch (Throwable th) {
            th.printStackTrace();
        }
        Object proxyListener = getProxyListener(obj2);
        if (proxyListener != null) {
            removeProxyListener(obj2);
            try {
                unregisterGnssStatusCallback_bak(obj, proxyListener);
            } catch (Throwable th2) {
                th2.printStackTrace();
            }
        }
        try {
            StringBuffer stringBuffer = new StringBuffer();
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#" + obj);
            stringBuffer.toString();
            for (int i = 0; i < 100; i = i + 1 + 1) {
            }
            for (int i2 = 0; i2 < 100; i2 = i2 + 1 + 1) {
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            StringBuffer stringBuffer2 = new StringBuffer();
            stringBuffer2.append("#");
            stringBuffer2.append("#");
            stringBuffer2.append("#");
            stringBuffer2.append("#");
            stringBuffer2.append("#");
            stringBuffer2.append("#" + obj);
            stringBuffer2.toString();
            for (int i3 = 0; i3 < 100; i3 = i3 + 1 + 1) {
            }
            for (int i4 = 0; i4 < 100; i4 = i4 + 1 + 1) {
            }
        } catch (Exception e2) {
            e2.printStackTrace();
        }
        try {
            StringBuffer stringBuffer3 = new StringBuffer();
            stringBuffer3.append("#");
            stringBuffer3.append("#");
            stringBuffer3.append("#");
            stringBuffer3.append("#");
            stringBuffer3.append("#");
            stringBuffer3.append("#" + obj);
            stringBuffer3.toString();
            for (int i5 = 0; i5 < 100; i5 = i5 + 1 + 1) {
            }
            for (int i6 = 0; i6 < 100; i6 = i6 + 1 + 1) {
            }
        } catch (Exception e3) {
            e3.printStackTrace();
        }
        try {
            StringBuffer stringBuffer4 = new StringBuffer();
            stringBuffer4.append("#");
            stringBuffer4.append("#");
            stringBuffer4.append("#");
            stringBuffer4.append("#");
            stringBuffer4.append("#");
            stringBuffer4.append("#" + obj);
            stringBuffer4.toString();
            for (int i7 = 0; i7 < 100; i7 = i7 + 1 + 1) {
            }
            for (int i8 = 0; i8 < 100; i8 = i8 + 1 + 1) {
            }
        } catch (Exception e4) {
            e4.printStackTrace();
        }
    }

    public static void unregisterGnssStatusCallback_bak(Object obj, Object obj2) {
        try {
            log("unregisterGnssStatusCallback_bak", obj);
            unregisterGnssStatusCallback_copy(obj, obj2);
            StringBuffer stringBuffer = new StringBuffer();
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.toString();
            for (int i = 0; i < 100; i = i + 1 + 1) {
            }
            for (int i2 = 0; i2 < 100; i2 = i2 + 1 + 1) {
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            StringBuffer stringBuffer2 = new StringBuffer();
            stringBuffer2.append("#");
            stringBuffer2.append("#");
            stringBuffer2.append("#");
            stringBuffer2.append("#");
            stringBuffer2.append("#");
            stringBuffer2.append("#" + obj);
            stringBuffer2.toString();
            for (int i3 = 0; i3 < 100; i3 = i3 + 1 + 1) {
            }
            for (int i4 = 0; i4 < 100; i4 = i4 + 1 + 1) {
            }
        } catch (Exception e2) {
            e2.printStackTrace();
        }
        try {
            StringBuffer stringBuffer3 = new StringBuffer();
            stringBuffer3.append("#");
            stringBuffer3.append("#");
            stringBuffer3.append("#");
            stringBuffer3.append("#");
            stringBuffer3.append("#");
            stringBuffer3.append("#" + obj);
            stringBuffer3.toString();
            for (int i5 = 0; i5 < 100; i5 = i5 + 1 + 1) {
            }
            for (int i6 = 0; i6 < 100; i6 = i6 + 1 + 1) {
            }
        } catch (Exception e3) {
            e3.printStackTrace();
        }
        try {
            StringBuffer stringBuffer4 = new StringBuffer();
            stringBuffer4.append("#");
            stringBuffer4.append("#");
            stringBuffer4.append("#");
            stringBuffer4.append("#");
            stringBuffer4.append("#");
            stringBuffer4.append("#" + obj);
            stringBuffer4.toString();
            for (int i7 = 0; i7 < 100; i7 = i7 + 1 + 1) {
            }
            for (int i8 = 0; i8 < 100; i8 = i8 + 1 + 1) {
            }
        } catch (Exception e4) {
            e4.printStackTrace();
        }
    }

    public static void unregisterGnssStatusCallback_copy(Object obj, Object obj2) {
        try {
            log("unregisterGnssStatusCallback_copy", obj);
            StringBuffer stringBuffer = new StringBuffer();
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.toString();
            for (int i = 0; i < 100; i = i + 1 + 1) {
            }
            for (int i2 = 0; i2 < 100; i2 = i2 + 1 + 1) {
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            StringBuffer stringBuffer2 = new StringBuffer();
            stringBuffer2.append("#");
            stringBuffer2.append("#");
            stringBuffer2.append("#");
            stringBuffer2.append("#");
            stringBuffer2.append("#");
            stringBuffer2.append("#" + obj);
            stringBuffer2.toString();
            for (int i3 = 0; i3 < 100; i3 = i3 + 1 + 1) {
            }
            for (int i4 = 0; i4 < 100; i4 = i4 + 1 + 1) {
            }
        } catch (Exception e2) {
            e2.printStackTrace();
        }
        try {
            StringBuffer stringBuffer3 = new StringBuffer();
            stringBuffer3.append("#");
            stringBuffer3.append("#");
            stringBuffer3.append("#");
            stringBuffer3.append("#");
            stringBuffer3.append("#");
            stringBuffer3.append("#" + obj);
            stringBuffer3.toString();
            for (int i5 = 0; i5 < 100; i5 = i5 + 1 + 1) {
            }
            for (int i6 = 0; i6 < 100; i6 = i6 + 1 + 1) {
            }
        } catch (Exception e3) {
            e3.printStackTrace();
        }
        try {
            StringBuffer stringBuffer4 = new StringBuffer();
            stringBuffer4.append("#");
            stringBuffer4.append("#");
            stringBuffer4.append("#");
            stringBuffer4.append("#");
            stringBuffer4.append("#");
            stringBuffer4.append("#" + obj);
            stringBuffer4.toString();
            for (int i7 = 0; i7 < 100; i7 = i7 + 1 + 1) {
            }
            for (int i8 = 0; i8 < 100; i8 = i8 + 1 + 1) {
            }
        } catch (Exception e4) {
            e4.printStackTrace();
        }
        try {
            StringBuffer stringBuffer5 = new StringBuffer();
            stringBuffer5.append("#");
            stringBuffer5.append("#");
            stringBuffer5.append("#");
            stringBuffer5.append("#");
            stringBuffer5.append("#");
            stringBuffer5.append("#" + obj);
            stringBuffer5.toString();
            for (int i9 = 0; i9 < 100; i9 = i9 + 1 + 1) {
            }
            for (int i10 = 0; i10 < 100; i10 = i10 + 1 + 1) {
            }
        } catch (Exception e5) {
            e5.printStackTrace();
        }
    }
}
