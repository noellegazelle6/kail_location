package com.kail.locationxposed.xposed.utils

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * KailLog —— Xposed 模块统一日志工具。
 *
 * 设计目标：日志「详细但不杂乱」，方便在被 hook 的宿主进程里快速定位问题。
 *
 * 每条日志都会带上：时间、级别、进程名、线程名、调用位置(文件:行号#方法)、标签、正文。
 *
 * 写入策略：
 *   - 低频日志 + W/E：输出到 Logcat/XposedBridge，并在开启「日志」时落盘到公共目录。
 *   - 高频/V 级别日志：仅在「详细调试」开启时输出，并限流「少量保存」，
 *     被丢弃的条数会在下次输出时以 (+N suppressed) 标注。
 *
 * 注意：不能在非标准线程（native hook 线程）中使用 SimpleDateFormat，避免 ICU 崩溃。
 */
object KailLog {
    private const val TAG_PREFIX = "KailLog/"
    private const val HIGH_FREQ_FILE_INTERVAL_MS = 1000L
    private const val MAX_THROTTLE_KEYS = 512
    private const val PUBLIC_LOG_DIR = "/sdcard/Documents/KailLocation/logs"

    @Volatile var fileLogEnabled = true
    @Volatile var detailedLogEnabled = true

    private val logExecutor = Executors.newSingleThreadExecutor()
    private val highFreqLastWriteMs = ConcurrentHashMap<String, Long>()
    private val highFreqSuppressed = ConcurrentHashMap<String, Int>()
    private val callerCache = ConcurrentHashMap<String, String>()

    @Volatile private var cachedProcessName: String? = null

    /**
     * 输出日志。
     *
     * @param context 上下文，用于读取偏好设置。若为 null 则尝试自动获取或仅输出到控制台。
     * @param tag 日志标签。
     * @param message 日志内容。
     * @param isHighFrequency 是否为高频日志。高频日志仅在详细调试开启时输出并限流，不落盘。
     * @param level 日志级别：'v', 'd', 'i', 'w', 'e'。
     */
    fun log(context: Context?, tag: String, message: String, isHighFrequency: Boolean = false, level: Char = 'd') {
        val resolvedContext = context ?: resolveContext()

        // 安全读取偏好设置
        val logEnabled = kotlin.runCatching {
            if (resolvedContext != null && resolvedContext.packageName != "android") {
                PreferenceManager.getDefaultSharedPreferences(resolvedContext)
                    .getBoolean("setting_log_enabled", false)
            } else {
                true // 系统进程或拿不到 Context 时默认开启
            }
        }.getOrDefault(true)

        val normalizedLevel = level.lowercaseChar()
        val isWarningOrError = normalizedLevel == 'w' || normalizedLevel == 'e'
        val verboseLike = isHighFrequency || normalizedLevel == 'v'

        // 开关：W/E 始终输出；其余受总开关与详细开关控制。
        if (!isWarningOrError) {
            if (!logEnabled) return
            if (verboseLike && !detailedLogEnabled) return
        }

        val caller = getCallerInfo()

        var suffix = ""
        if (verboseLike && !isWarningOrError) {
            val dropped = highFreqGate("$tag@$caller")
            if (dropped < 0) return
            if (dropped > 0) suffix = " (+$dropped suppressed)"
        }

        val levelChar = normalizedLevel.uppercaseChar()
        val thread = Thread.currentThread().name
        val logcatTag = "$TAG_PREFIX$tag"
        val logcatMessage = "[$thread] $caller | $message$suffix"

        // 尝试输出到 XposedBridge
        kotlin.runCatching {
            val bridgeClass = Class.forName("de.robv.android.xposed.XposedBridge")
            val logMethod = bridgeClass.getDeclaredMethod("log", String::class.java)
            logMethod.invoke(null, "$logcatTag: $logcatMessage")
        }

        when (normalizedLevel) {
            'v' -> Log.v(logcatTag, logcatMessage)
            'i' -> Log.i(logcatTag, logcatMessage)
            'w' -> Log.w(logcatTag, logcatMessage)
            'e' -> Log.e(logcatTag, logcatMessage)
            else -> Log.d(logcatTag, logcatMessage)
        }

        // 仅低频日志（含 W/E）才落盘，避免高频日志撑爆公共目录。
        if (!verboseLike && fileLogEnabled) {
            val fileMessage = "$levelChar [${processName()}/$thread] $tag $caller | $message$suffix"
            saveLogToPublicFile(fileMessage)
        }
    }

    fun v(context: Context?, tag: String, message: String, isHighFrequency: Boolean = true) = log(context, tag, message, isHighFrequency, 'v')
    fun d(context: Context?, tag: String, message: String, isHighFrequency: Boolean = false) = log(context, tag, message, isHighFrequency, 'd')
    fun i(context: Context?, tag: String, message: String, isHighFrequency: Boolean = false) = log(context, tag, message, isHighFrequency, 'i')
    fun w(context: Context?, tag: String, message: String, isHighFrequency: Boolean = false) = log(context, tag, message, isHighFrequency, 'w')
    fun e(context: Context?, tag: String, message: String, isHighFrequency: Boolean = false) = log(context, tag, message, isHighFrequency, 'e')

    fun e(context: Context?, tag: String, message: String, tr: Throwable, isHighFrequency: Boolean = false) {
        log(context, tag, "$message\n${stackTraceString(tr)}", isHighFrequency, 'e')
    }

    fun w(context: Context?, tag: String, message: String, tr: Throwable, isHighFrequency: Boolean = false) {
        log(context, tag, "$message\n${stackTraceString(tr)}", isHighFrequency, 'w')
    }

    private fun stackTraceString(tr: Throwable): String {
        val sw = StringWriter()
        PrintWriter(sw).use { tr.printStackTrace(it) }
        return sw.toString().trimEnd()
    }

    /**
     * 在 Xposed 环境下尝试获取 Application Context
     */
    private fun resolveContext(): Context? {
        return kotlin.runCatching {
            val at = Class.forName("android.app.ActivityThread")
            val m = at.getDeclaredMethod("currentApplication")
            m.invoke(null) as? Context
        }.getOrNull()
    }

    /** 进程名（被 hook 的宿主进程名），用于区分日志来源。 */
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
        return name.also { cachedProcessName = it }
    }

    /**
     * 获取调用者的「文件名:行号#方法名」。
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
     * 高频日志限流门：同一调用点 [HIGH_FREQ_FILE_INTERVAL_MS] 内只放行一条。
     * @return -1 表示被限流丢弃；>=0 表示放行，值为被丢弃的条数。
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

    /**
     * 将日志保存到外部存储的公共目录（Documents/KailLocation/logs）
     * 仅使用硬编码路径以避开 Environment API 在系统进程中的限制。
     */
    private fun saveLogToPublicFile(message: String) {
        logExecutor.execute {
            val ts = System.currentTimeMillis()
            val day = ts / 86_400_000L
            val fileName = "kail_log_${day}.txt"
            val logEntry = "${formatTime(ts)} $message\n"
            try {
                val publicDir = File(PUBLIC_LOG_DIR)
                if (!publicDir.exists()) {
                    publicDir.mkdirs()
                }
                val logFile = File(publicDir, fileName)
                FileOutputStream(logFile, true).use { fos ->
                    fos.write(logEntry.toByteArray())
                }
            } catch (e: Exception) {
                // 如果常规写入失败（常见于权限问题），尝试使用 su 兜底
                suAppend("$PUBLIC_LOG_DIR/$fileName", logEntry)
            }
        }
    }

    /**
     * 最后的兜底方案：尝试通过 su 命令写入日志
     */
    private fun suAppend(path: String, payload: String) {
        kotlin.runCatching {
            val file = File(path)
            val parent = file.parentFile?.absolutePath ?: "/sdcard"
            val escaped = payload
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("$", "\\$")
                .replace("`", "\\`")
            val cmd = "mkdir -p \"$parent\" && printf \"%s\" \"$escaped\" >> \"$path\""
            Runtime.getRuntime().exec(arrayOf("su", "-c", cmd)).waitFor()
        }
    }

    /** 不依赖 SimpleDateFormat 的轻量时间格式化：HH:mm:ss.SSS（本地时区）。 */
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
}
