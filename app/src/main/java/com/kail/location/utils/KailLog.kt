package com.kail.location.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.preference.PreferenceManager
import com.kail.location.viewmodels.SettingsViewModel
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.lang.reflect.Method
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * KailLog —— 全项目统一日志工具。
 *
 * 设计目标：日志「详细但不杂乱」，方便快速定位问题。
 *
 * 每条日志都会带上以下定位信息：
 *   - 时间 (HH:mm:ss.SSS)
 *   - 级别 (V/D/I/W/E)
 *   - 进程名 + 线程名（多进程：主进程 / 沙盒 / Xposed / 注入目标进程）
 *   - 调用位置（文件名:行号#方法名）
 *   - 标签与正文
 *
 * 写入策略（避免文件过大、过杂）：
 *   - 默认不写文件；W/E 始终输出到 Logcat / LSPosed，便于线上排查严重问题。
 *   - 开启「日志」后写入低频日志与关键状态。
 *   - 开启「详细调试」后才写入高频/详细(V/D 高频)日志，并按 [HIGH_FREQ_FILE_INTERVAL_MS]
 *     限流，「少量保存」高频日志；被丢弃的条数会在下次输出时以 (+N suppressed) 标注，
 *     这样既不刷屏，又能反映热点路径的真实触发次数。
 *
 * 注意：避免使用 SimpleDateFormat，防止在 Xposed/系统线程中触发 ICU 相关崩溃。
 */
object KailLog {
    private const val TAG_PREFIX = "KailLog/"

    /** 高频日志的最小写入间隔（同一调用点）。调大可进一步减少高频日志体积。 */
    private const val HIGH_FREQ_FILE_INTERVAL_MS = 1000L
    private const val MAX_THROTTLE_KEYS = 512
    private const val MAX_LOG_FILE_SIZE_BYTES = 4L * 1024L * 1024L
    private const val MAX_ROTATED_FILES = 3

    private val logExecutor = Executors.newSingleThreadExecutor()
    private val highFreqLastWriteMs = ConcurrentHashMap<String, Long>()
    private val highFreqSuppressed = ConcurrentHashMap<String, Int>()
    private val callerCache = ConcurrentHashMap<String, String>()

    @Volatile private var xposedLogMethod: Method? = null
    @Volatile private var xposedLogResolved = false
    @Volatile private var cachedContext: Context? = null
    @Volatile private var cachedProcessName: String? = null
    @Volatile private var headerWritten = false

    @Volatile var fileLogEnabled = false
    @Volatile var detailedLogEnabled = false

    /**
     * 输出日志。
     *
     * @param context           上下文，用于读取偏好与定位私有目录；null 时自动解析。
     * @param tag               日志标签（通常为模块/类名）。
     * @param message           日志正文。
     * @param isHighFrequency   是否为高频日志：高频日志仅在「详细调试」开启时输出并限流。
     * @param level             级别：'v' 'd' 'i' 'w' 'e'。
     */
    fun log(context: Context?, tag: String, message: String, isHighFrequency: Boolean = false, level: Char = 'd') {
        val resolvedContext = context ?: resolveContext()
        updateFlagsFromContext(resolvedContext)

        val normalizedLevel = level.lowercaseChar()
        val isWarningOrError = normalizedLevel == 'w' || normalizedLevel == 'e'
        // V 级别属于最详细日志，等同高频，仅在详细调试开启时输出。
        val verboseLike = isHighFrequency || normalizedLevel == 'v'
        val shouldEmit = isWarningOrError || if (verboseLike) detailedLogEnabled else fileLogEnabled
        if (!shouldEmit) return

        val caller = getCallerInfo()

        var suffix = ""
        if (verboseLike && !isWarningOrError) {
            val dropped = highFreqGate("$tag@$caller")
            if (dropped < 0) return
            if (dropped > 0) suffix = " (+$dropped suppressed)"
        }

        val levelChar = normalizedLevel.uppercaseChar()
        val thread = Thread.currentThread().name
        // Logcat：Android 已自带时间/pid/tid/级别，这里只补充定位信息，保持简洁。
        val logcatTag = "$TAG_PREFIX$tag"
        val logcatMessage = "[$thread] $caller | $message$suffix"

        xposedLogMethod()?.let { method ->
            kotlin.runCatching { method.invoke(null, "$logcatTag: $logcatMessage") }
        }

        when (normalizedLevel) {
            'v' -> Log.v(logcatTag, logcatMessage)
            'i' -> Log.i(logcatTag, logcatMessage)
            'w' -> Log.w(logcatTag, logcatMessage)
            'e' -> Log.e(logcatTag, logcatMessage)
            else -> Log.d(logcatTag, logcatMessage)
        }

        // 文件写入遵循开关：高频/V 需详细调试开启，其余需日志开启。
        // W/E 始终输出到 Logcat（上方），但是否落盘仍受开关控制，避免关闭日志时仍写磁盘。
        val shouldWriteFile = if (verboseLike) detailedLogEnabled else fileLogEnabled
        if (shouldWriteFile) {
            // 文件行需要自带完整定位信息（进程/线程/位置）。
            val fileMessage = "$levelChar [${processName()}/$thread] $tag $caller | $message$suffix"
            saveLogToPrivateFile(resolvedContext, fileMessage)
        }
    }

    fun v(context: Context?, tag: String, message: String, isHighFrequency: Boolean = true) = log(context, tag, message, isHighFrequency, 'v')
    fun d(context: Context?, tag: String, message: String, isHighFrequency: Boolean = false) = log(context, tag, message, isHighFrequency, 'd')
    fun i(context: Context?, tag: String, message: String, isHighFrequency: Boolean = false) = log(context, tag, message, isHighFrequency, 'i')
    fun w(context: Context?, tag: String, message: String, isHighFrequency: Boolean = false) = log(context, tag, message, isHighFrequency, 'w')
    fun e(context: Context?, tag: String, message: String, isHighFrequency: Boolean = false) = log(context, tag, message, isHighFrequency, 'e')

    /** 关键诊断日志：始终输出到 Logcat，文件落盘仍遵循日志开关。 */
    fun persist(context: Context?, tag: String, message: String, level: Char = 'i') {
        val resolvedContext = context ?: resolveContext()
        val normalizedLevel = level.lowercaseChar()
        val caller = getCallerInfo()
        val thread = Thread.currentThread().name
        val logcatTag = "$TAG_PREFIX$tag"
        val logcatMessage = "[$thread] $caller | $message"

        xposedLogMethod()?.let { method ->
            kotlin.runCatching { method.invoke(null, "$logcatTag: $logcatMessage") }
        }
        when (normalizedLevel) {
            'w' -> Log.w(logcatTag, logcatMessage)
            'e' -> Log.e(logcatTag, logcatMessage)
            else -> Log.i(logcatTag, logcatMessage)
        }

        if (resolvedContext != null && shouldWriteFile(resolvedContext)) {
            val levelChar = normalizedLevel.uppercaseChar()
            val fileMessage = "$levelChar [${processName()}/$thread] $tag $caller | $message"
            saveLogToPrivateFile(resolvedContext, fileMessage)
        }
    }

    /** 整块诊断报告：始终输出到 Logcat，文件落盘仍遵循日志开关。 */
    fun persistBlock(context: Context?, tag: String, block: String) {
        val resolvedContext = context ?: resolveContext() ?: return
        // Logcat 按行输出，文件按整块写入。
        block.lineSequence().forEach { line ->
            kotlin.runCatching { Log.i("$TAG_PREFIX$tag", line) }
        }
        if (shouldWriteFile(resolvedContext)) {
            saveLogBlockToPrivateFile(resolvedContext, tag, block)
        }
    }

    /** 记录异常并附带完整堆栈，便于定位。 */
    fun e(context: Context?, tag: String, message: String, tr: Throwable, isHighFrequency: Boolean = false) {
        log(context, tag, "$message\n${stackTraceString(tr)}", isHighFrequency, 'e')
    }

    fun w(context: Context?, tag: String, message: String, tr: Throwable, isHighFrequency: Boolean = false) {
        log(context, tag, "$message\n${stackTraceString(tr)}", isHighFrequency, 'w')
    }

    /**
     * 记录未捕获崩溃。无论日志开关是否开启都会落盘，且带完整线程/堆栈，便于事后定位。
     */
    fun logCrash(context: Context?, thread: Thread, throwable: Throwable) {
        val resolvedContext = context ?: resolveContext()
        val message = "FATAL on thread '${thread.name}': ${throwable.javaClass.name}: ${throwable.message}\n" +
            stackTraceString(throwable)
        kotlin.runCatching { Log.e("${TAG_PREFIX}CRASH", message) }
        xposedLogMethod()?.let { method ->
            kotlin.runCatching { method.invoke(null, "${TAG_PREFIX}CRASH: $message") }
        }
        val fileMessage = "E [${processName()}/${thread.name}] CRASH | $message"
        saveLogToPrivateFile(resolvedContext, fileMessage)
        // 同步等待写入完成，避免进程在崩溃后立即被杀导致日志丢失。
        kotlin.runCatching { logExecutor.submit { }.get() }
    }

    private fun stackTraceString(tr: Throwable): String {
        val sw = StringWriter()
        PrintWriter(sw).use { tr.printStackTrace(it) }
        return sw.toString().trimEnd()
    }

    private fun updateFlagsFromContext(context: Context?) {
        kotlin.runCatching {
            if (context != null && context.packageName != "android") {
                val prefs = PreferenceManager.getDefaultSharedPreferences(context)
                fileLogEnabled = prefs.getBoolean(SettingsViewModel.KEY_LOG_ENABLED, false)
                detailedLogEnabled = prefs.getBoolean(SettingsViewModel.KEY_DEBUG_LOG_ENABLED, false)
            }
        }
    }

    private fun shouldWriteFile(context: Context?): Boolean {
        updateFlagsFromContext(context)
        return fileLogEnabled
    }

    /**
     * 在 Xposed 环境下尝试获取 Application Context。
     */
    private fun resolveContext(): Context? {
        cachedContext?.let { return it }
        return kotlin.runCatching {
            val at = Class.forName("android.app.ActivityThread")
            val m = at.getDeclaredMethod("currentApplication")
            (m.invoke(null) as? Context)?.applicationContext
        }.getOrNull()?.also {
            cachedContext = it
        }
    }

    private fun xposedLogMethod(): Method? {
        if (xposedLogResolved) return xposedLogMethod
        synchronized(this) {
            if (!xposedLogResolved) {
                xposedLogMethod = kotlin.runCatching {
                    Class.forName("de.robv.android.xposed.XposedBridge")
                        .getDeclaredMethod("log", String::class.java)
                }.getOrNull()
                xposedLogResolved = true
            }
            return xposedLogMethod
        }
    }

    /** 进程名（多进程定位用）。优先读 /proc/self/cmdline，失败回退到 ActivityThread。 */
    private fun processName(): String {
        cachedProcessName?.let { return it }
        val name = kotlin.runCatching {
            File("/proc/self/cmdline").readText().trim().trim('\u0000')
        }.getOrNull()?.takeIf { it.isNotEmpty() }
            ?: kotlin.runCatching {
                val at = Class.forName("android.app.ActivityThread")
                at.getDeclaredMethod("currentProcessName").invoke(null) as? String
            }.getOrNull()
            ?: "?"
        // 仅保留进程后缀，缩短噪声：com.kail.location:sandbox -> :sandbox
        val short = name.substringAfter(':', "").let { if (it.isEmpty()) "main" else ":$it" }
        return short.also { cachedProcessName = it }
    }

    /**
     * 返回调用方的「文件名:行号#方法名」。
     * 跳过 KailLog 自身与 Thread 帧。
     */
    private fun getCallerInfo(): String {
        val stackTrace = Thread.currentThread().stackTrace
        for (i in 2 until stackTrace.size) {
            val frame = stackTrace[i]
            if (frame.className != KailLog::class.java.name && !frame.className.contains("java.lang.Thread")) {
                    val fileName = callerCache.getOrPut(frame.className) {
                        val fn = frame.fileName
                        if (fn == null || fn.startsWith("r8-")) frame.className.substringAfterLast('.') + ".java" else fn
                    }
                val line = frame.lineNumber
                return if (line > 0) "$fileName:$line#${frame.methodName}" else "$fileName#${frame.methodName}"
            }
        }
        return "Unknown"
    }

    /**
     * 高频日志限流门：同一调用点 [HIGH_FREQ_FILE_INTERVAL_MS] 内只放行一条，
     * 期间被丢弃的条数在下次放行时返回，便于反映热点真实频率。
     *
     * @return -1 表示被限流丢弃；>=0 表示放行，值为自上次放行以来被丢弃的条数。
     */
    private fun highFreqGate(key: String): Int {
        val now = System.currentTimeMillis()
        if (highFreqLastWriteMs.size > MAX_THROTTLE_KEYS) {
            highFreqLastWriteMs.clear()
            highFreqSuppressed.clear()
        }
        val last = highFreqLastWriteMs[key] ?: 0L
        if (now - last < HIGH_FREQ_FILE_INTERVAL_MS) {
            highFreqSuppressed.merge(key, 1) { a, b -> a + b }
            return -1
        }
        highFreqLastWriteMs[key] = now
        return highFreqSuppressed.remove(key) ?: 0
    }

    private fun saveLogToPrivateFile(context: Context?, message: String) {
        if (context == null || context.packageName == "android") return
        val appContext = context.applicationContext ?: context
        logExecutor.execute {
            val ts = System.currentTimeMillis()
            val day = ts / 86_400_000L
            val fileName = "kail_log_${day}.txt"
            val logEntry = "${formatTime(ts)} $message\n"

            try {
                val logDir = logDir(appContext)
                if (!logDir.exists()) {
                    logDir.mkdirs()
                }

                val logFile = File(logDir, fileName)
                rotateIfNeeded(logFile)
                writeHeaderIfNeeded(appContext, logFile)
                FileOutputStream(logFile, true).use { fos ->
                    fos.write(logEntry.toByteArray())
                }
            } catch (_: Exception) {
            }
        }
    }

    /** 一次性把整块诊断报告写入日志文件，块内每行带时间戳，块首尾有分隔线。 */
    private fun saveLogBlockToPrivateFile(context: Context?, tag: String, block: String) {
        if (context == null || context.packageName == "android") return
        val appContext = context.applicationContext ?: context
        logExecutor.execute {
            val ts = System.currentTimeMillis()
            val day = ts / 86_400_000L
            val fileName = "kail_log_${day}.txt"
            val builder = StringBuilder()
            val time = formatTime(ts)
            builder.append("$time ===== $tag BEGIN =====\n")
            block.lineSequence().forEach { line ->
                builder.append("$time   $line\n")
            }
            builder.append("$time ===== $tag END =====\n")
            try {
                val logDir = logDir(appContext)
                if (!logDir.exists()) logDir.mkdirs()
                val logFile = File(logDir, fileName)
                rotateIfNeeded(logFile)
                writeHeaderIfNeeded(appContext, logFile)
                FileOutputStream(logFile, true).use { it.write(builder.toString().toByteArray()) }
            } catch (_: Exception) {
            }
        }
    }

    /** 每个进程生命周期首次写文件时，追加一段会话头，记录环境信息，便于定位。 */
    private fun writeHeaderIfNeeded(context: Context, logFile: File) {
        if (headerWritten) return
        headerWritten = true
        kotlin.runCatching {
            val versionName = context.packageManager
                .getPackageInfo(context.packageName, 0).versionName ?: "?"
            val header = buildString {
                append("========== KailLog session ==========\n")
                append("${formatTime(System.currentTimeMillis())} process=${processName()} pkg=${context.packageName}\n")
                append("app=$versionName android=${android.os.Build.VERSION.RELEASE}(API ${android.os.Build.VERSION.SDK_INT}) ")
                append("device=${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\n")
                append("======================================\n")
            }
            FileOutputStream(logFile, true).use { it.write(header.toByteArray()) }
        }
    }

    private fun rotateIfNeeded(logFile: File) {
        if (!logFile.exists() || logFile.length() < MAX_LOG_FILE_SIZE_BYTES) return
        for (i in MAX_ROTATED_FILES downTo 1) {
            val source = if (i == 1) logFile else File(logFile.parentFile, "${logFile.name}.$i")
            if (!source.exists()) continue
            if (i == MAX_ROTATED_FILES) {
                source.delete()
            } else {
                source.renameTo(File(logFile.parentFile, "${logFile.name}.${i + 1}"))
            }
        }
        // 轮转后新文件需要重新写会话头。
        headerWritten = false
    }

    private fun formatTime(ts: Long): String {
        val dayMs = 86_400_000L
        val localMs = ((ts + TimeZone.getDefault().getOffset(ts).toLong()) % dayMs + dayMs) % dayMs
        val hour = localMs / 3_600_000L
        val minute = (localMs % 3_600_000L) / 60_000L
        val second = (localMs % 60_000L) / 1000L
        val ms = localMs % 1000L
        return buildString(12) {
            appendPadded(hour, 2)
            append(':')
            appendPadded(minute, 2)
            append(':')
            appendPadded(second, 2)
            append('.')
            appendPadded(ms, 3)
        }
    }

    private fun StringBuilder.appendPadded(value: Long, width: Int) {
        val text = value.toString()
        repeat(width - text.length) { append('0') }
        append(text)
    }

    fun getLogCacheSizeBytes(context: Context? = null): Long {
        return kotlin.runCatching {
            logFiles(context).sumOf { it.length() }
        }.getOrDefault(0L)
    }

    fun clearLogCache(context: Context? = null): Boolean {
        return kotlin.runCatching {
            logFiles(context).forEach { it.delete() }
            true
        }.getOrDefault(false)
    }

    fun exportLogs(context: Context, uri: Uri): Boolean {
        return kotlin.runCatching {
            val files = logFiles(context)
            context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                if (files.isEmpty()) {
                    output.write("No log files found.\n".toByteArray())
                } else {
                    files.forEach { file ->
                        output.write("===== ${file.name} =====\n".toByteArray())
                        file.inputStream().use { input -> input.copyTo(output) }
                        output.write("\n".toByteArray())
                    }
                }
            } ?: return false
            true
        }.getOrDefault(false)
    }

    private fun logFiles(context: Context? = null): List<File> {
        val resolvedContext = context ?: resolveContext()
        if (resolvedContext == null || resolvedContext.packageName == "android") return emptyList()
        val dir = logDir(resolvedContext)
        return dir.listFiles { file -> file.isFile && file.name.startsWith("kail_log_") }
            ?.sortedBy { it.name }
            ?: emptyList()
    }

    private fun logDir(context: Context): File {
        return File(context.filesDir, "logs")
    }
}
