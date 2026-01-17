package com.kail.location.xposed

import android.util.Log
import de.robv.android.xposed.XposedBridge
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

internal object XposedLog {
    private const val TAG = "KAIL_XPOSED"
    private val resolvedPathRef = AtomicReference<String?>(null)
    private val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    fun i(message: String) {
        write("I", message, null)
    }

    fun e(message: String, t: Throwable? = null) {
        write("E", message, t)
    }

    private fun write(level: String, message: String, t: Throwable?) {
        // 1. Log to XposedBridge and Android Log
        val logMsg = "[$level] $message"
        kotlin.runCatching {
            XposedBridge.log("$TAG: $logMsg")
            if (t != null) XposedBridge.log(t)
        }
        kotlin.runCatching {
            if (level == "E") {
                Log.e(TAG, message, t)
            } else {
                Log.i(TAG, message)
            }
        }

        // 2. Log to File
        val path = resolvePath() ?: "/data/local/tmp/kail_location_xposed.log"
        val now = kotlin.runCatching { sdf.format(Date()) }.getOrNull() ?: ""
        val sb = StringBuilder()
        sb.append(now)
        sb.append(" [")
        sb.append(level)
        sb.append("] ")
        sb.append(message)
        sb.append('\n')
        if (t != null) {
            val sw = StringWriter()
            t.printStackTrace(PrintWriter(sw))
            sb.append(sw.toString())
            if (!sb.endsWith("\n")) sb.append('\n')
        }

        val payload = sb.toString()
        val directOk = kotlin.runCatching {
            val file = File(path)
            file.parentFile?.mkdirs()
            FileOutputStream(file, true).use { it.write(payload.toByteArray()) }
            true
        }.getOrDefault(false)

        if (!directOk) {
            suAppend(path, payload)
        }
    }

    private fun resolvePath(): String? {
        val cached = resolvedPathRef.get()
        if (!cached.isNullOrBlank()) return cached

        val ctxPath = resolveContextFilesPath()
        if (!ctxPath.isNullOrBlank() && canAppend(ctxPath)) {
            resolvedPathRef.set(ctxPath)
            return ctxPath
        }

        val candidates = listOf(
            "/data/system/kail_location_xposed.log",
            "/data/local/tmp/kail_location_xposed.log",
            "/data/adb/kail_location_xposed.log",
            "/cache/kail_location_xposed.log"
        )

        for (path in candidates) {
            if (canAppend(path)) {
                resolvedPathRef.set(path)
                return path
            }
        }
        return null
    }

    private fun suAppend(path: String, payload: String) {
        kotlin.runCatching {
            val file = File(path)
            val parent = file.parentFile?.absolutePath ?: "/data/local/tmp"
            val escaped = payload
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("$", "\\$")
                .replace("`", "\\`")
            val cmd = "mkdir -p \"$parent\" && printf \"%s\" \"$escaped\" >> \"$path\""
            Runtime.getRuntime().exec(arrayOf("su", "-c", cmd)).waitFor()
        }
    }

    private fun resolveContextFilesPath(): String? {
        return kotlin.runCatching {
            val at = Class.forName("android.app.ActivityThread")
            val m = at.getDeclaredMethod("currentApplication")
            val app = m.invoke(null) as? android.app.Application ?: return@runCatching null
            File(app.filesDir, "kail_location_xposed.log").absolutePath
        }.getOrNull()
    }

    private fun canAppend(path: String): Boolean {
        return kotlin.runCatching {
            val f = File(path)
            f.parentFile?.mkdirs()
            FileOutputStream(f, true).use { }
            true
        }.getOrDefault(false)
    }
}
