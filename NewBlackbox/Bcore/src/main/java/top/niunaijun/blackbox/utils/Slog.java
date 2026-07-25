
package top.niunaijun.blackbox.utils;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class Slog {
    public static final int LOG_ID_SYSTEM = 3;
    private static final String TAG_PREFIX = "KailLog/";
    private static final long HIGH_FREQ_FILE_INTERVAL_MS = 1000L;
    private static final int MAX_THROTTLE_KEYS = 512;
    private static final long MAX_LOG_FILE_SIZE_BYTES = 4L * 1024L * 1024L;
    private static final int MAX_ROTATED_FILES = 3;

    private static final ExecutorService logExecutor = Executors.newSingleThreadExecutor();
    private static final Map<String, Long> highFreqLastWriteMs = new ConcurrentHashMap<>();
    private static final Map<String, Integer> highFreqSuppressed = new ConcurrentHashMap<>();
    private static final Map<String, String> callerCache = new ConcurrentHashMap<>();

    private static volatile Context cachedContext = null;
    private static volatile String cachedProcessName = null;
    private static volatile boolean headerWritten = false;
    private static volatile boolean fileLogEnabled = false;
    private static volatile boolean detailedLogEnabled = false;

    private Slog() {
    }

    private static void resolveContextAndFlags() {
        if (cachedContext != null) {
            updateFlags(cachedContext);
            return;
        }
        try {
            Class<?> at = Class.forName("android.app.ActivityThread");
            Method m = at.getDeclaredMethod("currentApplication");
            Context ctx = (Context) m.invoke(null);
            if (ctx != null) {
                ctx = ctx.getApplicationContext();
                cachedContext = ctx;
                updateFlags(ctx);
            }
        } catch (Exception ignored) {
        }
    }

    private static void updateFlags(Context context) {
        try {
            if (context != null && !"android".equals(context.getPackageName())) {
                Class<?> pmClass = Class.forName("androidx.preference.PreferenceManager");
                Method gdsm = pmClass.getDeclaredMethod("getDefaultSharedPreferences", Context.class);
                Object prefs = gdsm.invoke(null, context);
                Method gb = prefs.getClass().getMethod("getBoolean", String.class, boolean.class);
                fileLogEnabled = (Boolean) gb.invoke(prefs, "setting_log_enabled", false);
                detailedLogEnabled = (Boolean) gb.invoke(prefs, "setting_debug_log_enabled", false);
            }
        } catch (Exception ignored) {
        }
    }

    private static boolean shouldWriteToFile() {
        resolveContextAndFlags();
        return fileLogEnabled;
    }

    private static String processName() {
        if (cachedProcessName != null) return cachedProcessName;
        try {
            java.io.FileInputStream fis = new java.io.FileInputStream("/proc/self/cmdline");
            byte[] buf = new byte[256];
            int len = fis.read(buf);
            fis.close();
            String name = len > 0 ? new String(buf, 0, len).trim().trim().trim() : "";
            if (name.isEmpty()) {
                Class<?> at = Class.forName("android.app.ActivityThread");
                Method m = at.getDeclaredMethod("currentProcessName");
                name = (String) m.invoke(null);
            }
            if (name != null && !name.isEmpty()) {
                int idx = name.indexOf(':');
                String shortName = (idx >= 0) ? ":" + name.substring(idx + 1) : "main";
                cachedProcessName = shortName;
                return shortName;
            }
        } catch (Exception ignored) {
        }
        return "?";
    }

    private static String getCallerInfo() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        String slogClass = Slog.class.getName();
        for (int i = 3; i < stack.length; i++) {
            StackTraceElement frame = stack[i];
            String clsName = frame.getClassName();
            if (!clsName.equals(slogClass) && !clsName.contains("java.lang.Thread")) {
                String fileName = callerCache.get(clsName);
                if (fileName == null) {
                    fileName = frame.getFileName();
                    if (fileName == null || fileName.startsWith("r8-")) {
                        fileName = clsName.substring(clsName.lastIndexOf('.') + 1) + ".java";
                    }
                    callerCache.put(clsName, fileName);
                }
                int line = frame.getLineNumber();
                return (line > 0) ? fileName + ":" + line + "#" + frame.getMethodName()
                        : fileName + "#" + frame.getMethodName();
            }
        }
        return "Unknown";
    }

    private static int highFreqGate(String key) {
        long now = System.currentTimeMillis();
        if (highFreqLastWriteMs.size() > MAX_THROTTLE_KEYS) {
            highFreqLastWriteMs.clear();
            highFreqSuppressed.clear();
        }
        Long last = highFreqLastWriteMs.get(key);
        if (last != null && now - last < HIGH_FREQ_FILE_INTERVAL_MS) {
            highFreqSuppressed.merge(key, 1, Integer::sum);
            return -1;
        }
        highFreqLastWriteMs.put(key, now);
        Integer suppressed = highFreqSuppressed.remove(key);
        return (suppressed != null) ? suppressed : 0;
    }

    private static String stackTraceString(Throwable tr) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        tr.printStackTrace(pw);
        pw.flush();
        return sw.toString();
    }

    private static void logOut(int priority, String tag, String message, boolean isHighFrequency) {
        resolveContextAndFlags();
        boolean isWarningOrError = priority >= Log.WARN;
        boolean verboseLike = isHighFrequency || priority <= Log.VERBOSE;
        boolean shouldEmit = isWarningOrError || (verboseLike ? detailedLogEnabled : fileLogEnabled);
        if (!shouldEmit) return;

        String caller = getCallerInfo();
        String thread = Thread.currentThread().getName();
        String levelChar = String.valueOf(priorityChar(priority));

        String suffix = "";
        if (verboseLike && !isWarningOrError) {
            int dropped = highFreqGate(tag + "@" + caller);
            if (dropped < 0) return;
            if (dropped > 0) suffix = " (+" + dropped + " suppressed)";
        }

        String logcatTag = TAG_PREFIX + tag;
        String logcatMsg = "[" + thread + "] " + caller + " | " + message + suffix;

        switch (priority) {
            case Log.VERBOSE: Log.v(logcatTag, logcatMsg); break;
            case Log.DEBUG:   Log.d(logcatTag, logcatMsg); break;
            case Log.INFO:    Log.i(logcatTag, logcatMsg); break;
            case Log.WARN:    Log.w(logcatTag, logcatMsg); break;
            case Log.ERROR:   Log.e(logcatTag, logcatMsg); break;
            default:          Log.d(logcatTag, logcatMsg); break;
        }

        boolean shouldWriteFile = verboseLike ? detailedLogEnabled : fileLogEnabled;
        if (shouldWriteFile && cachedContext != null) {
            String fileMsg = levelChar + " [" + processName() + "/" + thread + "] " + tag + " " + caller + " | " + message + suffix;
            saveToFile(cachedContext, fileMsg);
        }
    }

    private static void logOut(int priority, String tag, String message, Throwable tr, boolean isHighFrequency) {
        String combined = message + "\n" + stackTraceString(tr);
        logOut(priority, tag, combined, isHighFrequency);
    }

    private static char priorityChar(int priority) {
        switch (priority) {
            case Log.VERBOSE: return 'V';
            case Log.DEBUG:   return 'D';
            case Log.INFO:    return 'I';
            case Log.WARN:    return 'W';
            case Log.ERROR:   return 'E';
            default:          return '?';
        }
    }

    // === Public API ===

    public static int v(String tag, String msg) {
        logOut(Log.VERBOSE, tag, msg, true);
        return 0;
    }

    public static int v(String tag, String msg, Throwable tr) {
        logOut(Log.VERBOSE, tag, msg, tr, true);
        return 0;
    }

    public static int d(String tag, String msg) {
        logOut(Log.DEBUG, tag, msg, false);
        return 0;
    }

    public static int d(String tag, String msg, Throwable tr) {
        logOut(Log.DEBUG, tag, msg, tr, false);
        return 0;
    }

    public static int i(String tag, String msg) {
        logOut(Log.INFO, tag, msg, false);
        return 0;
    }

    public static int i(String tag, String msg, Throwable tr) {
        logOut(Log.INFO, tag, msg, tr, false);
        return 0;
    }

    public static int w(String tag, String msg) {
        logOut(Log.WARN, tag, msg, false);
        return 0;
    }

    public static int w(String tag, String msg, Throwable tr) {
        logOut(Log.WARN, tag, msg, tr, false);
        return 0;
    }

    public static int w(String tag, Throwable tr) {
        logOut(Log.WARN, tag, "", tr, false);
        return 0;
    }

    public static int e(String tag, String msg) {
        logOut(Log.ERROR, tag, msg, false);
        return 0;
    }

    public static int e(String tag, String msg, Throwable tr) {
        logOut(Log.ERROR, tag, msg, tr, false);
        return 0;
    }

    public static int println(int priority, String tag, String msg) {
        logOut(priority, tag, msg, false);
        return 0;
    }

    // === File I/O (mirrors KailLog) ===

    public static void persist(String tag, String message) {
        resolveContextAndFlags();
        String caller = getCallerInfo();
        String thread = Thread.currentThread().getName();
        String logcatTag = TAG_PREFIX + tag;
        String logcatMsg = "[" + thread + "] " + caller + " | " + message;
        Log.i(logcatTag, logcatMsg);
        if (shouldWriteToFile() && cachedContext != null) {
            String fileMsg = "I [" + processName() + "/" + thread + "] " + tag + " " + caller + " | " + message;
            saveToFile(cachedContext, fileMsg);
        }
    }

    private static void saveToFile(Context context, String message) {
        if (context == null || "android".equals(context.getPackageName())) return;
        Context appContext = context.getApplicationContext();
        logExecutor.execute(() -> {
            long ts = System.currentTimeMillis();
            long day = ts / 86400000L;
            String fileName = "kail_log_" + day + ".txt";
            String logEntry = formatTime(ts) + " " + message + "\n";
            try {
                File logDir = new File(appContext.getFilesDir(), "logs");
                if (!logDir.exists()) logDir.mkdirs();
                File logFile = new File(logDir, fileName);
                rotateIfNeeded(logFile);
                writeHeaderIfNeeded(appContext, logFile);
                FileOutputStream fos = new FileOutputStream(logFile, true);
                try {
                    fos.write(logEntry.getBytes());
                } finally {
                    fos.close();
                }
            } catch (Exception ignored) {
            }
        });
    }

    private static void writeHeaderIfNeeded(Context context, File logFile) {
        if (headerWritten) return;
        headerWritten = true;
        try {
            String versionName = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0).versionName;
            if (versionName == null) versionName = "?";
            String time = formatTime(System.currentTimeMillis());
            StringBuilder sb = new StringBuilder();
            sb.append("========== KailLog session ==========\n");
            sb.append(time).append(" process=").append(processName())
                    .append(" pkg=").append(context.getPackageName()).append("\n");
            sb.append("app=").append(versionName)
                    .append(" android=").append(android.os.Build.VERSION.RELEASE)
                    .append("(API ").append(android.os.Build.VERSION.SDK_INT).append(")")
                    .append(" device=").append(android.os.Build.MANUFACTURER)
                    .append(" ").append(android.os.Build.MODEL).append("\n");
            sb.append("======================================\n");
            FileOutputStream fos = new FileOutputStream(logFile, true);
            try {
                fos.write(sb.toString().getBytes());
            } finally {
                fos.close();
            }
        } catch (Exception ignored) {
        }
    }

    private static void rotateIfNeeded(File logFile) {
        if (!logFile.exists() || logFile.length() < MAX_LOG_FILE_SIZE_BYTES) return;
        for (int i = MAX_ROTATED_FILES; i >= 1; i--) {
            File source = (i == 1) ? logFile : new File(logFile.getParentFile(), logFile.getName() + "." + i);
            if (!source.exists()) continue;
            if (i == MAX_ROTATED_FILES) {
                source.delete();
            } else {
                source.renameTo(new File(logFile.getParentFile(), logFile.getName() + "." + (i + 1)));
            }
        }
        headerWritten = false;
    }

    private static String formatTime(long ts) {
        long dayMs = 86400000L;
        int offset = TimeZone.getDefault().getOffset(ts);
        long localMs = ((ts + offset) % dayMs + dayMs) % dayMs;
        long hour = localMs / 3600000L;
        long minute = (localMs % 3600000L) / 60000L;
        long second = (localMs % 60000L) / 1000L;
        long ms = localMs % 1000L;
        return pad(hour, 2) + ":" + pad(minute, 2) + ":" + pad(second, 2) + "." + pad(ms, 3);
    }

    private static String pad(long value, int width) {
        StringBuilder sb = new StringBuilder();
        String text = Long.toString(value);
        for (int i = text.length(); i < width; i++) sb.append('0');
        sb.append(text);
        return sb.toString();
    }

    public static void logCrash(Context context, Thread thread, Throwable throwable) {
        Context ctx = context != null ? context : cachedContext;
        if (ctx == null) {
            resolveContextAndFlags();
            ctx = cachedContext;
        }
        String msg = "FATAL on thread '" + thread.getName() + "': "
                + throwable.getClass().getName() + ": " + throwable.getMessage() + "\n"
                + stackTraceString(throwable);
        Log.e(TAG_PREFIX + "CRASH", msg);
        String fileMsg = "E [" + processName() + "/" + thread.getName() + "] CRASH | " + msg;
        saveToFile(ctx, fileMsg);
        try {
            logExecutor.submit(() -> {}).get();
        } catch (Exception ignored) {
        }
    }
}
