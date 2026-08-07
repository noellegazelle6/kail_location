package com.kail.location.lib.lhooker;

import android.os.Build;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LHooker {
    private static volatile String sessionLibraryPath;
    public static boolean initialized;
    private static boolean isHooking;
    static Map<Method, HookRecord> hookRecords = new ConcurrentHashMap();

    static class HookRecord {
        String targetName;
        Member targetMember;
        Method hookMethod;
        Method backupMethod;

        public HookRecord(String targetName, Member targetMember, Method hookMethod, Method backupMethod) {
            this.targetName = targetName;
            this.targetMember = targetMember;
            this.hookMethod = hookMethod;
            this.backupMethod = backupMethod;
        }
    }

    public static native void ensureDeclareClass(Member member, Method method);

    public static native void ensureMethodCached(Method method, Method method2);

    public static native Object findMethodNative(Class cls, String name, String signature);

    public static native Object[] getObjs(byte[] bytes, String name);

    private static native long getThread();

    private static native boolean hookMethodNative(Object target, Method hookMethod, Method backupMethod, Method copyMethod);

    public static native int init(int sdkInt);

    public static native void resumeAll(long threadHandle);

    private static native boolean shouldVisiblyInit();

    /**
     * Returns the native init-time diagnostic log (ArtMethod layout probe
     * results + final offsets). Persisted to the app log file via InjectLog
     * right after init() so the auto-detected layout is inspectable offline.
     */
    public static native String nativeGetInitLog();

    public static native long suspendAll();

    private static native int visiblyInit(long threadHandle);

    public static void hookMethod(Member targetMember, Method hookMethod, Method backupMethod, Method copyMethod) {
        if (targetMember == null) {
            throw new IllegalArgumentException("null target method");
        }
        if (hookMethod == null) {
            throw new IllegalArgumentException("null hook method");
        }
        if (!Modifier.isStatic(hookMethod.getModifiers())) {
            throw new IllegalArgumentException("Hook must be a static method: " + hookMethod);
        }
        checkHookSignature(targetMember, hookMethod, "Original", "Hook");
        if (backupMethod != null) {
            if (!Modifier.isStatic(backupMethod.getModifiers())) {
                throw new IllegalArgumentException("Backup must be a static method: " + backupMethod);
            }
            checkHookSignature(targetMember, backupMethod, "Original", "Backup");
        }
        if (initializeVisibleHooking() != 0) {
            com.kail.location.inject.utils.InjectLog.e("LHooker", "hookMethod: initializeVisibleHooking failed for " + targetMember);
        }
        if (targetMember instanceof Method) {
            ((Method) targetMember).setAccessible(true);
        }
        hookRecords.put(hookMethod, new HookRecord(targetMember.getName(), targetMember, hookMethod, backupMethod));
        isHooking = true;
        if (hookMethodNative(targetMember, hookMethod, backupMethod, copyMethod)) {
            if (backupMethod != null && !backupMethod.isAccessible()) {
                backupMethod.setAccessible(true);
            }
            isHooking = false;
            return;
        }
        isHooking = false;
        throw new RuntimeException("Failed to hook " + targetMember + " with " + hookMethod);
    }

    public static final <T> T callOriginalByName(Object receiver, String className, String methodName, Object... args) {
        Method method;
        try {
            Method[] methods = Class.forName(className).getMethods();
            int length = methods.length;
            int i = 0;
            while (true) {
                if (i >= length) {
                    method = null;
                    break;
                }
                method = methods[i];
                if (method.getName().equals(methodName)) {
                    break;
                }
                i++;
            }
            if (method == null) {
                throw new NoSuchMethodException(className + "/" + methodName);
            }
            HookRecord record = hookRecords.get(method);
            if (record != null) {
                T result = (T) invokeBackup(record.targetMember, record.backupMethod, receiver, args);
                if (result != null) {
                    return result;
                }
                return null;
            }
            throw new NoSuchMethodException(className + "/" + methodName + "(MethodEntity)");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            return null;
        } catch (Throwable th) {
            th.printStackTrace();
            return null;
        }
    }

    public static final <T> T callOriginal(Object receiver, Object... args) {
        return (T) callOriginalByName(receiver, Thread.currentThread().getStackTrace()[3].getClassName(), Thread.currentThread().getStackTrace()[3].getMethodName(), args);
    }

    private static final Object invokeBackup(Member targetMember, Method backupMethod, Object receiver, Object[] args) throws Throwable {
        if (!backupMethod.isAccessible()) {
            backupMethod.setAccessible(true);
        }
        if (Modifier.isStatic(targetMember.getModifiers())) {
            try {
                return backupMethod.invoke(null, args);
            } catch (InvocationTargetException e) {
                if (e.getCause() != null) {
                    throw e.getCause();
                }
                throw e;
            }
        }
        try {
            return backupMethod.invoke(receiver, args);
        } catch (InvocationTargetException e2) {
            if (e2.getCause() != null) {
                throw e2.getCause();
            }
            throw e2;
        }
    }

    private static void checkHookSignature(Object target, Method hookMethod, String originalLabel, String hookLabel) {
        ArrayList originalTypes;
        boolean isMethod = target instanceof Method;
        if (isMethod) {
            originalTypes = new ArrayList(Arrays.asList(((Method) target).getParameterTypes()));
        } else if (!(target instanceof Constructor)) {
            return;
        } else {
            originalTypes = new ArrayList(Arrays.asList(((Constructor) target).getParameterTypes()));
        }
        ArrayList hookTypes = new ArrayList(Arrays.asList(hookMethod.getParameterTypes()));
        if ((isMethod && !Modifier.isStatic(((Method) target).getModifiers())) || (target instanceof Constructor)) {
            originalTypes.add(0, Object.class);
        }
        if (!Modifier.isStatic(hookMethod.getModifiers())) {
            hookTypes.add(0, Object.class);
        }
        if (!isMethod || hookMethod.getReturnType().isAssignableFrom(((Method) target).getReturnType())) {
            if (!((target instanceof Constructor) && hookMethod.getReturnType().equals(Void.class)) && originalTypes.size() == hookTypes.size()) {
                for (int i = 0; i < originalTypes.size() && ((Class) hookTypes.get(i)).isAssignableFrom((Class) originalTypes.get(i)); i++) {
                }
            }
        }
    }

    public static void hookMethodBySignature(Class targetClass, String methodName, String signature, Method hookMethod, Method backupMethod, Method copyMethod) {
        hookMethod(findTargetMember(targetClass, methodName, signature), hookMethod, backupMethod, copyMethod);
    }

    public static void hookConstructor(Class targetClass, Class[] parameterTypes, Class hookClass, String hookName, String backupName, String copyName) {
        try {
            hookMethodByNames(targetClass, "<init>", Void.TYPE, parameterTypes, hookClass, hookName, backupName, copyName);
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }

    public static void hookMethodAutoBackup(Class targetClass, String methodName, Class returnType, Class[] parameterTypes, Class hookClass, String hookName) {
        hookMethodByNames(targetClass, methodName, returnType, parameterTypes, hookClass, hookName, hookName + "_bak", hookName + "_copy");
    }

    public static void hookMethodWithBackup(Class targetClass, String methodName, Class returnType, Class[] parameterTypes, Class hookClass, String hookName, String backupName) {
        hookMethodByNames(targetClass, methodName, returnType, parameterTypes, hookClass, hookName, backupName, backupName.replaceAll("_bak", "_copy"));
    }

    public static void hookMethodByNames(Class targetClass, String methodName, Class returnType, Class[] parameterTypes, Class hookClass, String hookName, String backupName, String copyName) {
        Method hookMethod = null;
        Method backupMethod = null;
        Method copyMethod = null;
        for (Method candidate : hookClass.getMethods()) {
            if (candidate.getName().equals(hookName)) {
                hookMethod = candidate;
            }
            if (candidate.getName().equals(backupName)) {
                backupMethod = candidate;
            }
            if (candidate.getName().equals(copyName)) {
                copyMethod = candidate;
            }
        }
        try {
            hookMethodWithMethods(targetClass, methodName, returnType, parameterTypes, hookMethod, backupMethod, copyMethod);
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }

    public static void hookMethodWithMethods(Class targetClass, String methodName, Class returnType, Class[] parameterTypes, Method hookMethod, Method backupMethod, Method copyMethod) {
        hookMethodBySignature(targetClass, methodName, buildMethodSignature(returnType, parameterTypes), hookMethod, backupMethod, copyMethod);
    }

    private static Member findTargetMember(Class targetClass, String methodName, String signature) {
        if (targetClass == null) {
            throw new IllegalArgumentException("null class");
        }
        if (methodName == null) {
            throw new IllegalArgumentException("null method name");
        }
        if (signature != null) {
            return (Member) findMethodNative(targetClass, methodName, signature);
        }
        throw new IllegalArgumentException("null method signature");
    }

    public static String buildMethodSignature(Class returnType, Class... parameterTypes) {
        StringBuilder signature = new StringBuilder();
        signature.append("(");
        if (parameterTypes != null) {
            for (Class parameterType : parameterTypes) {
                if (parameterType == null) {
                    continue;
                }
                signature.append(toDescriptor(parameterType));
            }
        }
        signature.append(")");
        signature.append(toDescriptor(returnType != null ? returnType : Void.TYPE));
        return signature.toString();
    }

    private static String toDescriptor(Class type) {
        String name = type.getName();
        switch (name) {
            case "double":
                return "D";
            case "int":
                return "I";
            case "byte":
                return "B";
            case "char":
                return "C";
            case "long":
                return "J";
            case "void":
                return "V";
            case "boolean":
                return "Z";
            case "float":
                return "F";
            case "short":
                return "S";
            default:
                String descriptor = name.replaceAll("\\.", "/");
                if (descriptor.contains("/")) {
                    if (!descriptor.startsWith("L") && !descriptor.startsWith("[")) {
                        descriptor = "L" + descriptor;
                    }
                    if (!descriptor.endsWith(";")) {
                        descriptor = descriptor + ";";
                    }
                }
                return descriptor;
        }
    }

    public static int initializeVisibleHooking() {
        try {
            if (shouldVisiblyInit()) {
                return visiblyInit(getThread());
            }
            return 0;
        } catch (Throwable th) {
            th.printStackTrace();
            return -1;
        }
    }

    public static boolean isDeviceArm64() {
        return Build.VERSION.SDK_INT >= 21 ? containsArm64(Build.SUPPORTED_ABIS) : isArm64Abi(Build.CPU_ABI);
    }

    public static boolean isArm64Abi(String abi) {
        return ("" + abi).contains("arm64");
    }

    public static boolean containsArm64(String[] abis) {
        return Arrays.toString(abis).toLowerCase().contains("arm64");
    }

    public static boolean isDeviceX86() {
        return (Build.VERSION.SDK_INT >= 21 ? Arrays.toString(Build.SUPPORTED_ABIS) : Build.CPU_ABI).toLowerCase().contains("x86");
    }

    public static boolean isX86Abi(String abi) {
        return abi.toLowerCase().contains("x86");
    }

    public static boolean isDeviceX86_64() {
        return (Build.VERSION.SDK_INT >= 21 ? Arrays.toString(Build.SUPPORTED_ABIS) : Build.CPU_ABI).toLowerCase().contains("x86_64");
    }

    public static boolean isX86_64Abi(String abi) {
        return abi.toLowerCase().contains("x86_64");
    }

    public static synchronized void loadHookLibrary(String libraryPath) {
        if (initialized) {
            com.kail.location.inject.utils.InjectLog.persist("LHooker", "loadHookLibrary skipped: already initialized path=", libraryPath);
            return;
        }
        String resolvedPath = resolveHookLibraryPath(libraryPath);
        if (!resolvedPath.equals(libraryPath)) {
            com.kail.location.inject.utils.InjectLog.persist("LHooker", "using session library path=", resolvedPath, " default=", libraryPath);
        }
        try {
            com.kail.location.inject.utils.InjectLog.persist("LHooker", "System.load start path=", resolvedPath);
            System.load(resolvedPath);
            com.kail.location.inject.utils.InjectLog.persist("LHooker", "System.load ok path=", resolvedPath);
        } catch (Throwable th) {
            com.kail.location.inject.utils.InjectLog.e("LHooker", "System.load failed path=" + resolvedPath + " default=" + libraryPath, th);
            return;
        }
        try {
            int sdkInt = Build.VERSION.SDK_INT;
            if (sdkInt >= 23) {
                try {
                    if (Build.VERSION.PREVIEW_SDK_INT > 0) {
                        sdkInt++;
                    }
                } catch (Throwable unused) {
                }
            }
            int rc = init(sdkInt);
            initialized = rc == 0;
            com.kail.location.inject.utils.InjectLog.persist("LHooker", "init rc=", rc, " initialized=", initialized);
            persistInitLog();
        } catch (Throwable th2) {
            com.kail.location.inject.utils.InjectLog.e("LHooker", "init failed path=" + resolvedPath, th2);
        }
    }

    public static void setSessionLibraryPath(String libraryPath) {
        if (libraryPath == null) {
            sessionLibraryPath = null;
            return;
        }
        String trimmed = libraryPath.trim();
        sessionLibraryPath = trimmed.isEmpty() ? null : trimmed;
        com.kail.location.inject.utils.InjectLog.persist("LHooker", "session library override=", sessionLibraryPath);
    }

    private static String resolveHookLibraryPath(String defaultPath) {
        String configuredPath = sessionLibraryPath;
        if (configuredPath == null) {
            configuredPath = readSessionLibraryPath();
        }
        if (configuredPath == null) {
            return defaultPath;
        }
        if (!matchesLibraryName(configuredPath, defaultPath)) {
            com.kail.location.inject.utils.InjectLog.persist("LHooker", "ignoring session library path=", configuredPath, " default=", defaultPath);
            return defaultPath;
        }
        return configuredPath;
    }

    private static String readSessionLibraryPath() {
        try {
            File dir = new File("/data/kail-loc");
            File[] files = dir.listFiles((d, name) ->
                name.startsWith("liblhooker") && name.contains("_v") && name.endsWith(".so"));
            if (files != null && files.length > 0) {
                for (File f : files) {
                    String name = f.getName();
                    boolean match = false;
                    if (isDeviceArm64()) match = name.contains("64");
                    else if (isDeviceX86_64()) match = name.contains("x86_64");
                    else if (isDeviceX86()) match = name.contains("x86") && !name.contains("x86_64");
                    else match = !name.contains("64") && !name.contains("x86");
                    if (match) return f.getAbsolutePath();
                }
            }
        } catch (Throwable t) {
            com.kail.location.inject.utils.InjectLog.e("LHooker", "scan lhooker path failed", t);
        }
        return null;
    }

    private static boolean matchesLibraryName(String configuredPath, String defaultPath) {
        String defaultName = new File(defaultPath).getName();
        String configuredName = new File(configuredPath).getName();
        if (configuredName.equals(defaultName)) {
            return true;
        }
        if (!defaultName.endsWith(".so")) {
            return false;
        }
        String prefix = defaultName.substring(0, defaultName.length() - 3);
        return configuredName.startsWith(prefix + "_") && configuredName.endsWith(".so");
    }

    /**
     * Pull the native init-time diagnostic log (ArtMethod layout auto-detection
     * results, final offsets, fail-safe outcome) and write it into the app's
     * own log file via InjectLog so it survives past the volatile logcat buffer
     * and can be inspected when troubleshooting on devices we don't own.
     */
    private static void persistInitLog() {
        try {
            String summary = nativeGetInitLog();
            if (summary == null || summary.isEmpty()) {
                return;
            }
            com.kail.location.inject.utils.InjectLog.persist("LHooker",
                    "init=" + (initialized ? "OK" : "FAILED"));
            for (String line : summary.split("\n")) {
                if (!line.trim().isEmpty()) {
                    com.kail.location.inject.utils.InjectLog.persist("LHooker", line);
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
