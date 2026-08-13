package com.kail.location.utils

import android.content.Context
import com.kail.location.models.UpdateInfo
import com.kail.location.network.RuoYiClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * 应用安装包下载器。
 * 优先使用主下载地址（后端 fileUrl），失败或字节数与预期不符时自动回退到备用地址（GitHub）。
 */
object UpdateDownloader {
    private const val TAG = "UpdateDownloader"

    private class Candidate(val url: String, val filename: String, val expectedSize: Long)

    /**
     * 下载更新安装包。
     *
     * 依次尝试主地址和备用地址；主地址已知期望字节数（info.fileSize）时会校验
     * 下载产物大小，不匹配视为失败并切换到备用地址。全部失败时抛出最后一次异常。
     *
     * @param context 上下文，用于定位外部存储目录。
     * @param info 更新信息（包含主/备用下载地址与期望大小）。
     * @param onProgress 下载进度回调（0-100，仅在服务器返回 Content-Length 时更新）。
     * @param onTotalKnown 是否知道总长度的回调：服务器返回 Content-Length 时传 true，
     *                     否则（chunked）传 false，用于 UI 显示不定进度。
     * @return 下载并校验通过的安装包文件。
     */
    @Throws(Exception::class)
    fun download(
        context: Context,
        info: UpdateInfo,
        onProgress: (Int) -> Unit = {},
        onTotalKnown: (Boolean) -> Unit = {}
    ): File {
        val candidates = mutableListOf<Candidate>()
        if (info.downloadUrl.isNotBlank()) candidates.add(Candidate(info.downloadUrl, info.filename, info.fileSize))
        if (!info.fallbackUrl.isNullOrBlank()) candidates.add(Candidate(info.fallbackUrl, info.fallbackFilename, 0L))
        if (candidates.isEmpty()) throw IOException("no download url")

        var lastError: Exception? = null
        for (candidate in candidates) {
            try {
                return downloadFromUrl(context, candidate, onProgress, onTotalKnown)
            } catch (e: Exception) {
                lastError = e
                KailLog.w(context, TAG, "download failed: ${candidate.url} -> ${e.message}")
            }
        }
        throw lastError ?: IOException("download failed")
    }

    private fun downloadFromUrl(
        context: Context,
        candidate: Candidate,
        onProgress: (Int) -> Unit,
        onTotalKnown: (Boolean) -> Unit
    ): File {
        val request = Request.Builder().url(candidate.url).build()
        val response = RuoYiClient.okHttpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            response.close()
            throw IOException("HTTP ${response.code}")
        }
        val body = response.body ?: run {
            response.close()
            throw IOException("empty body")
        }
        val total = body.contentLength().takeIf { it > 0 } ?: -1L
        onTotalKnown(total > 0)
        val dir = File(context.getExternalFilesDir(null), "Updates")
        if (!dir.exists()) dir.mkdirs()
        val outFile = File(dir, candidate.filename)
        var sum = 0L
        body.byteStream().use { input ->
            FileOutputStream(outFile).use { output ->
                val buffer = ByteArray(8 * 1024)
                var bytesRead: Int
                while (true) {
                    bytesRead = input.read(buffer)
                    if (bytesRead == -1) break
                    output.write(buffer, 0, bytesRead)
                    sum += bytesRead
                    if (total > 0) {
                        onProgress(((sum * 100) / total).toInt().coerceIn(0, 100))
                    }
                }
                output.flush()
            }
        }
        if (total > 0 && sum != total) {
            outFile.delete()
            throw IOException("size mismatch: expected $total bytes, got $sum")
        }
        if (candidate.expectedSize > 0 && sum != candidate.expectedSize) {
            outFile.delete()
            throw IOException("integrity check failed: expected ${candidate.expectedSize} bytes, got $sum")
        }
        KailLog.i(context, TAG, "download done: ${candidate.filename} from ${candidate.url} ($sum bytes)")
        return outFile
    }
}
