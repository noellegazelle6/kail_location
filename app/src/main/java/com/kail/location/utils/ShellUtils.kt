package com.kail.location.utils

import java.io.File

object ShellUtils {
    fun hasRoot(): Boolean {
        val runtime = Runtime.getRuntime()
        var process: Process? = null
        try {
            process = runtime.exec("su")
            process.outputStream.write("exit\n".toByteArray())
            process.outputStream.flush()
            process.waitFor()
            return process.exitValue() == 0
        } catch (e: Exception) {
            return false
        } finally {
            process?.destroy()
        }
    }

    fun executeCommand(command: String): String {
        val runtime = Runtime.getRuntime()
        var process: Process? = null
        try {
            process = runtime.exec("su")
            process.outputStream.write("$command\n".toByteArray())
            process.outputStream.write("exit\n".toByteArray())
            process.outputStream.flush()
            process.waitFor()
            if (process.exitValue() != 0) {
                return process.errorStream.bufferedReader().readText()
            }
            return process.inputStream.bufferedReader().readText()
        } catch (e: Exception) {
            e.printStackTrace()
            return ""
        } finally {
            process?.destroy()
        }
    }

    fun executeCommandToBytes(command: String): ByteArray {
        val runtime = Runtime.getRuntime()
        var process: Process? = null
        try {
            process = runtime.exec("su")
            process.outputStream.write("$command\n".toByteArray())
            process.outputStream.write("exit\n".toByteArray())
            process.outputStream.flush()
            process.waitFor()
            if (process.exitValue() != 0) {
                return process.errorStream.use { it.readBytes() }
            }
            return process.inputStream.use { it.readBytes() }
        } catch (e: Exception) {
            e.printStackTrace()
            return ByteArray(0)
        } finally {
            process?.destroy()
        }
    }
}
