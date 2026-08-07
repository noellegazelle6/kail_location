package com.kail.location.inject.fakelocation.hook.app;

import android.app.Application;
import android.content.pm.ApplicationInfo;
import com.kail.location.inject.utils.ScopedListFilter;
import com.kail.location.inject.utils.ReflectionUtils;
import com.kail.location.inject.utils.HideRootServiceManager;
import com.kail.location.inject.utils.AntiDetectionServiceManager;
import com.kail.location.inject.utils.MockLocationServiceManager;
import com.kail.location.inject.utils.LAntiDetect;
import com.kail.location.lib.lhooker.LHooker;
import java.util.ArrayList;
import java.util.List;
import com.kail.location.inject.fakelocation.hook.HookClassLoaderUtils;
import com.kail.location.inject.fakelocation.hook.phone.PhoneInterfaceManagerHook;
import com.kail.location.inject.fakelocation.hook.camera.CameraHookMain;

/* JADX INFO: renamed from: ֏.֏.ހ.֏.ށ.֏, reason: contains not printable characters */
/* JADX INFO: loaded from: /home/kail/code/tool/jadx-1.5.5/bin/classes.dex */
public class AppProcessHook {

    /* JADX INFO: renamed from: ֏, reason: contains not printable characters */
    public static String currentPackageName = "#";

    /* JADX INFO: renamed from: ؠ, reason: contains not printable characters */
    static Object allowMockPackageLock = new Object();

    /* JADX INFO: renamed from: ֏.֏.ހ.֏.ށ.֏$֏, reason: contains not printable characters */
    static class SafeAppException extends Exception {
        public SafeAppException(String str) {
            super(str);
        }
    }

    public static void applyHookToApp(Object obj, String str) {
        List<String> listM138;
        boolean z;
        boolean z2;
        boolean z3;
        List<String> listM114;
        List<String> listM137;
        try {
            currentPackageName = str;
            if (str.equals("com.android.phone") && MockLocationServiceManager.getInstance().isMocking()) {
                PhoneInterfaceManagerHook.hook(obj.getClass().getClassLoader());
            }
            if (AntiDetectionServiceManager.getInstance().isAntiDetectionEnabled() && AntiDetectionServiceManager.getInstance().isHookRulesEnabled() && (listM137 = AntiDetectionServiceManager.getInstance().getHookTargetPackages()) != null && !listM137.isEmpty() && listM137.contains(str) && isFuncAvailable(str, AntiDetectionServiceManager.getInstance().getHookMethodRules(), "m")) {
                RuntimeAntiDetectionHook.hookFileList(obj.getClass().getClassLoader());
                listM138 = AntiDetectionServiceManager.getInstance().getHiddenFileNames();
                if (listM138 == null) {
                    listM138 = null;
                }
                if (AntiDetectionServiceManager.getInstance().isFileNameHidingEnabled()) {
                    RuntimeAntiDetectionHook.hookHideXposed(obj.getClass().getClassLoader());
                }
                z = true;
                z2 = true;
            } else {
                listM138 = null;
                z = false;
                z2 = false;
            }
            if (HideRootServiceManager.getInstance().isHideRootEnabled() && (listM114 = HideRootServiceManager.getInstance().getHiddenPackages()) != null && !listM114.isEmpty() && listM114.contains(str) && isFuncAvailable(str, HideRootServiceManager.getInstance().getHiddenProcesses(), "k")) {
                z = z || (isFuncAvailable(str, HideRootServiceManager.getInstance().getHiddenProcesses(), "n") ? HideRootServiceManager.getInstance().isHideAppListEnabled() : false);
                z3 = true;
            } else {
                z3 = false;
            }
            if (MockLocationServiceManager.getInstance().isMocking() && MockLocationServiceManager.getInstance().isMockGpsStatusEnabled() && isAllowMockPackage(str) && isFuncAvailable(str, MockLocationServiceManager.getInstance().getSafeApps(), "l")) {
                ClientMockHook.hook(str, obj.getClass().getClassLoader());
            }
            // Virtual camera: self-gated by /data/kail-loc/camera_config.json.
            try {
                CameraHookMain.hook(str, obj.getClass().getClassLoader());
            } catch (Throwable camTh) {
                camTh.printStackTrace();
            }
            if (z2 || z3) {
                RootHideHook.hook(obj.getClass().getClassLoader());
            }
            if (z) {
                LAntiDetect.loadAndInitialize(str, null, null);
                ArrayList arrayList = new ArrayList();
                if (listM138 != null) {
                    arrayList.addAll(listM138);
                }
                arrayList.addAll(RootHideHook.hiddenFakelocArtifacts);
                LAntiDetect.setAntidetectFileNames((String[]) arrayList.toArray(new String[0]));
                LAntiDetect.setMocking(true);
            }
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }

    public static void callApplicationOnCreate(Object obj, Application application) {
        log("callApplicationOnCreate, app:" + application);
        currentPackageName = application.getPackageName();
        applyHookToApp(application, application.getPackageName());
        try {
            callApplicationOnCreate_bak(obj, application);
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
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.toString();
            for (int i = 0; i < 10; i = i + 1 + 1) {
            }
            for (int i2 = 0; i2 < 10; i2 = i2 + 1 + 1) {
            }
        } catch (Exception e) {
            e.printStackTrace();
            log(1);
        }
        try {
            StringBuffer stringBuffer2 = new StringBuffer();
            stringBuffer2.append("#");
            stringBuffer2.append("#");
            stringBuffer2.append("#");
            stringBuffer2.append("#");
            stringBuffer2.append("#");
            stringBuffer2.append("#");
            stringBuffer2.append("#");
            stringBuffer2.toString();
            for (int i3 = 0; i3 < 10; i3 = i3 + 1 + 1) {
            }
            for (int i4 = 0; i4 < 10; i4 = i4 + 1 + 1) {
            }
        } catch (Exception e2) {
            e2.printStackTrace();
            log(1);
        }
        try {
            StringBuffer stringBuffer3 = new StringBuffer();
            stringBuffer3.append("#");
            stringBuffer3.append("#");
            stringBuffer3.append("#");
            stringBuffer3.append("#");
            stringBuffer3.append("#");
            stringBuffer3.append("#");
            stringBuffer3.append("#");
            stringBuffer3.toString();
            for (int i5 = 0; i5 < 10; i5 = i5 + 1 + 1) {
            }
            for (int i6 = 0; i6 < 10; i6 = i6 + 1 + 1) {
            }
        } catch (Exception e3) {
            e3.printStackTrace();
            log(1);
        }
        try {
            StringBuffer stringBuffer4 = new StringBuffer();
            stringBuffer4.append("#");
            stringBuffer4.append("#");
            stringBuffer4.append("#");
            stringBuffer4.append("#");
            stringBuffer4.append("#");
            stringBuffer4.append("#");
            stringBuffer4.append("#");
            stringBuffer4.toString();
            for (int i7 = 0; i7 < 10; i7 = i7 + 1 + 1) {
            }
            for (int i8 = 0; i8 < 10; i8 = i8 + 1 + 1) {
            }
        } catch (Exception e4) {
            e4.printStackTrace();
            log(1);
        }
    }

    public static void callApplicationOnCreate_bak(Object obj, Application application) {
        log("callApplicationOnCreate_bak, app:" + application);
        callApplicationOnCreate_copy(obj, application);
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
            for (int i = 0; i < 10; i = i + 1 + 1) {
            }
            for (int i2 = 0; i2 < 10; i2 = i2 + 1 + 1) {
            }
        } catch (Exception e) {
            e.printStackTrace();
            log(1);
        }
    }

    public static void callApplicationOnCreate_copy(Object obj, Application application) {
        log("callApplicationOnCreate_copy, app:" + application);
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
            for (int i = 0; i < 10; i = i + 1 + 1) {
            }
            for (int i2 = 0; i2 < 10; i2 = i2 + 1 + 1) {
            }
        } catch (Exception e) {
            e.printStackTrace();
            log(1);
        }
    }

    public static void handleBindApplication(Object obj, Object obj2) {
        log("handleBindApplication, data:" + obj2);
        try {
            Class clsM279 = HookClassLoaderUtils.loadClass(obj.getClass().getClassLoader(), "android.app.ActivityThread$AppBindData");
            ApplicationInfo applicationInfo = (ApplicationInfo) ReflectionUtils.getFieldValue(obj2, clsM279, "appInfo");
            String str = applicationInfo.packageName.equals("android") ? "system" : applicationInfo.packageName;
            String str2 = (String) ReflectionUtils.getFieldValue(obj2, clsM279, "processName");
            currentPackageName = str;
            log("handleBindApplication, PackageName:" + str + ", processName: " + str2 + "," + AntiDetectionServiceManager.getInstance().isAntiDetectionEnabled());
            applyHookToApp(obj, str);
        } catch (Throwable th) {
            th.printStackTrace();
        }
        try {
            handleBindApplication_bak(obj, obj2);
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
    }

    public static void handleBindApplication_bak(Object obj, Object obj2) {
        try {
            log("handleBindApplication_bak", obj);
            StringBuffer stringBuffer = new StringBuffer();
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#" + obj2);
            stringBuffer.toString();
            for (int i = 0; i < 10; i = i + 1 + 1) {
            }
            for (int i2 = 0; i2 < 10; i2 = i2 + 1 + 1) {
            }
        } catch (Exception e) {
            e.printStackTrace();
            log(1);
        }
        try {
            handleBindApplication_copy(obj, obj2);
        } catch (Throwable th) {
            th.printStackTrace();
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
        try {
            StringBuffer stringBuffer6 = new StringBuffer();
            stringBuffer6.append("#");
            stringBuffer6.append("#");
            stringBuffer6.append("#");
            stringBuffer6.append("#");
            stringBuffer6.append("#");
            stringBuffer6.append("#" + obj);
            stringBuffer6.toString();
            for (int i11 = 0; i11 < 100; i11 = i11 + 1 + 1) {
            }
            for (int i12 = 0; i12 < 100; i12 = i12 + 1 + 1) {
            }
        } catch (Exception e6) {
            e6.printStackTrace();
        }
        try {
            StringBuffer stringBuffer7 = new StringBuffer();
            stringBuffer7.append("#");
            stringBuffer7.append("#");
            stringBuffer7.append("#");
            stringBuffer7.append("#");
            stringBuffer7.append("#");
            stringBuffer7.append("#" + obj);
            stringBuffer7.toString();
            for (int i13 = 0; i13 < 100; i13 = i13 + 1 + 1) {
            }
            for (int i14 = 0; i14 < 100; i14 = i14 + 1 + 1) {
            }
        } catch (Exception e7) {
            e7.printStackTrace();
        }
        try {
            StringBuffer stringBuffer8 = new StringBuffer();
            stringBuffer8.append("#");
            stringBuffer8.append("#");
            stringBuffer8.append("#");
            stringBuffer8.append("#");
            stringBuffer8.append("#");
            stringBuffer8.append("#" + obj);
            stringBuffer8.toString();
            for (int i15 = 0; i15 < 100; i15 = i15 + 1 + 1) {
            }
            for (int i16 = 0; i16 < 100; i16 = i16 + 1 + 1) {
            }
        } catch (Exception e8) {
            e8.printStackTrace();
        }
        try {
            StringBuffer stringBuffer9 = new StringBuffer();
            stringBuffer9.append("#");
            stringBuffer9.append("#");
            stringBuffer9.append("#");
            stringBuffer9.append("#");
            stringBuffer9.append("#");
            stringBuffer9.append("#" + obj);
            stringBuffer9.toString();
            for (int i17 = 0; i17 < 100; i17 = i17 + 1 + 1) {
            }
            for (int i18 = 0; i18 < 100; i18 = i18 + 1 + 1) {
            }
        } catch (Exception e9) {
            e9.printStackTrace();
        }
        try {
            StringBuffer stringBuffer10 = new StringBuffer();
            stringBuffer10.append("#");
            stringBuffer10.append("#");
            stringBuffer10.append("#");
            stringBuffer10.append("#");
            stringBuffer10.append("#");
            stringBuffer10.append("#" + obj);
            stringBuffer10.toString();
            for (int i19 = 0; i19 < 100; i19 = i19 + 1 + 1) {
            }
            for (int i20 = 0; i20 < 100; i20 = i20 + 1 + 1) {
            }
        } catch (Exception e10) {
            e10.printStackTrace();
        }
    }

    public static void handleBindApplication_copy(Object obj, Object obj2) {
        try {
            log("handleBindApplication_copy", obj);
            StringBuffer stringBuffer = new StringBuffer();
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#");
            stringBuffer.append("#" + obj2);
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
        try {
            StringBuffer stringBuffer6 = new StringBuffer();
            stringBuffer6.append("#");
            stringBuffer6.append("#");
            stringBuffer6.append("#");
            stringBuffer6.append("#");
            stringBuffer6.append("#");
            stringBuffer6.append("#" + obj);
            stringBuffer6.toString();
            for (int i11 = 0; i11 < 100; i11 = i11 + 1 + 1) {
            }
            for (int i12 = 0; i12 < 100; i12 = i12 + 1 + 1) {
            }
        } catch (Exception e6) {
            e6.printStackTrace();
        }
        try {
            StringBuffer stringBuffer7 = new StringBuffer();
            stringBuffer7.append("#");
            stringBuffer7.append("#");
            stringBuffer7.append("#");
            stringBuffer7.append("#");
            stringBuffer7.append("#");
            stringBuffer7.append("#" + obj);
            stringBuffer7.toString();
            for (int i13 = 0; i13 < 100; i13 = i13 + 1 + 1) {
            }
            for (int i14 = 0; i14 < 100; i14 = i14 + 1 + 1) {
            }
        } catch (Exception e7) {
            e7.printStackTrace();
        }
        try {
            StringBuffer stringBuffer8 = new StringBuffer();
            stringBuffer8.append("#");
            stringBuffer8.append("#");
            stringBuffer8.append("#");
            stringBuffer8.append("#");
            stringBuffer8.append("#");
            stringBuffer8.append("#" + obj);
            stringBuffer8.toString();
            for (int i15 = 0; i15 < 100; i15 = i15 + 1 + 1) {
            }
            for (int i16 = 0; i16 < 100; i16 = i16 + 1 + 1) {
            }
        } catch (Exception e8) {
            e8.printStackTrace();
        }
        try {
            StringBuffer stringBuffer9 = new StringBuffer();
            stringBuffer9.append("#");
            stringBuffer9.append("#");
            stringBuffer9.append("#");
            stringBuffer9.append("#");
            stringBuffer9.append("#");
            stringBuffer9.append("#" + obj);
            stringBuffer9.toString();
            for (int i17 = 0; i17 < 100; i17 = i17 + 1 + 1) {
            }
            for (int i18 = 0; i18 < 100; i18 = i18 + 1 + 1) {
            }
        } catch (Exception e9) {
            e9.printStackTrace();
        }
        try {
            StringBuffer stringBuffer10 = new StringBuffer();
            stringBuffer10.append("#");
            stringBuffer10.append("#");
            stringBuffer10.append("#");
            stringBuffer10.append("#");
            stringBuffer10.append("#");
            stringBuffer10.append("#" + obj);
            stringBuffer10.toString();
            for (int i19 = 0; i19 < 100; i19 = i19 + 1 + 1) {
            }
            for (int i20 = 0; i20 < 100; i20 = i20 + 1 + 1) {
            }
        } catch (Exception e10) {
            e10.printStackTrace();
        }
    }

    private static void handleSafeApp(String str) throws SafeAppException {
        try {
            List<String> listM165 = MockLocationServiceManager.getInstance().getSafeApps();
            if (listM165 != null && !listM165.isEmpty() && (listM165.contains("*") || listM165.contains(str))) {
                throw new SafeAppException("Safe App :" + str);
            }
            List<String> listM139 = AntiDetectionServiceManager.getInstance().getHookMethodRules();
            if (listM139 != null && !listM139.isEmpty() && (listM139.contains("*") || listM139.contains(str))) {
                throw new SafeAppException("Safe App :" + str);
            }
            List<String> listM115 = HideRootServiceManager.getInstance().getHiddenProcesses();
            if (listM115 == null || listM115.isEmpty()) {
                return;
            }
            if (listM115.contains("*") || listM115.contains(str)) {
                throw new SafeAppException("Safe App :" + str);
            }
        } catch (SafeAppException e) {
            throw e;
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }

    public static void hook(ClassLoader classLoader) {
        try {
            LHooker.hookMethodWithBackup(HookClassLoaderUtils.loadClass(classLoader, "android.app.ActivityThread"), "handleBindApplication", Void.TYPE, new Class[]{HookClassLoaderUtils.loadClass(classLoader, "android.app.ActivityThread$AppBindData")}, AppProcessHook.class, "handleBindApplication", "handleBindApplication_bak");
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }

    public static boolean isAllowMockPackage(String str) {
        synchronized (allowMockPackageLock) {
            List<String> listM161 = MockLocationServiceManager.getInstance().getAllowMockPackages();
            if (listM161 != null && !listM161.isEmpty()) {
                return listM161.contains(str);
            }
            return true;
        }
    }

    private static boolean isFuncAvailable(String str, List<String> list, String str2) {
        return ScopedListFilter.isAllowed(list, str, str2);
    }

    private static void log(Object... objArr) {
        com.kail.location.inject.utils.InjectLog.log("AppProcessHook", objArr);
    }
}
