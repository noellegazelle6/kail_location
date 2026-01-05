package com.kail.location.utils

import android.app.Application
import com.baidu.location.LocationClient
import com.baidu.mapapi.CoordType
import com.baidu.mapapi.SDKInitializer
import com.elvishew.xlog.LogConfiguration
import com.elvishew.xlog.LogLevel
import com.elvishew.xlog.XLog
import com.elvishew.xlog.printer.ConsolePrinter
import com.elvishew.xlog.printer.file.FilePrinter
import com.elvishew.xlog.printer.file.backup.NeverBackupStrategy
import com.elvishew.xlog.printer.file.clean.FileLastModifiedCleanStrategy
import com.elvishew.xlog.printer.file.naming.ChangelessFileNameGenerator

/**
 * 自定义 Application，负责应用级初始化。
 * 包含崩溃日志写入、XLog 初始化以及百度地图/定位 SDK 的设置。
 */
class GoApplication : Application() {
    companion object {
        const val APP_NAME = "KailLocation"
        const val LOG_FILE_NAME = "$APP_NAME.log"
        private const val MAX_TIME = (1000 * 60 * 60 * 24 * 3).toLong() // 3 days
    }

    /**
     * 将崩溃信息写入文件。
     *
     * @param ex 抛出的异常对象。
     */
    private fun writeCrashToFile(ex: Throwable) {
        try {
            val logPath = getExternalFilesDir("Logs") ?: return
            val crashFile = java.io.File(logPath, "crash_${System.currentTimeMillis()}.txt")
            val pw = java.io.PrintWriter(crashFile)
            ex.printStackTrace(pw)
            pw.flush()
            pw.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private var mDefaultHandler: Thread.UncaughtExceptionHandler? = null

    /**
     * 应用启动回调。
     * 初始化崩溃处理、XLog 以及百度地图/定位 SDK。
     */
    override fun onCreate() {
        super.onCreate()

        mDefaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            writeCrashToFile(throwable)
            throwable.printStackTrace()
            mDefaultHandler?.uncaughtException(thread, throwable)
        }

        initXlog()

        // 百度地图 7.5 开始，要求必须同意隐私政策，默认为false
        SDKInitializer.setAgreePrivacy(this, true)
        // 百度定位 7.5 开始，要求必须同意隐私政策，默认为false(官方说可以统一为以上接口，但实际测试并不行，定位还是需要单独设置)
        LocationClient.setAgreePrivacy(true)

        try {
            // 在使用 SDK 各组间之前初始化 context 信息，传入 ApplicationContext
            SDKInitializer.initialize(this)
            SDKInitializer.setCoordType(CoordType.BD09LL)
        } catch (e: Throwable) {
            e.printStackTrace()
            // 记录初始化失败日志
            XLog.e("Baidu Map SDK init failed", e)
        }
    }

    /**
     * 初始化 XLog：配置日志级别、标签与文件输出。
     */
    private fun initXlog() {
        val config = LogConfiguration.Builder()
            .logLevel(LogLevel.ALL)
            .tag(APP_NAME)
            .enableThreadInfo()
            .enableStackTrace(2)
            .enableBorder()
            .build()

        val consolePrinter = ConsolePrinter()
        val logPath = getExternalFilesDir("Logs")
        
        if (logPath != null) {
            val filePrinter = FilePrinter.Builder(logPath.path)
                .fileNameGenerator(ChangelessFileNameGenerator(LOG_FILE_NAME))
                .backupStrategy(NeverBackupStrategy())
                .cleanStrategy(FileLastModifiedCleanStrategy(MAX_TIME))
                .build()
            XLog.init(config, consolePrinter, filePrinter)
        } else {
            // Fallback to console only if storage is not available
            XLog.init(config, consolePrinter)
        }
    }
}
