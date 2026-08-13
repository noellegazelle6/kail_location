package com.kail.location.utils

import android.content.Context
import android.os.Build
import com.kail.location.models.UpdateInfo
import com.kail.location.network.RuoYiClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLDecoder

object UpdateChecker {
    private const val TAG = "UpdateChecker"
    private const val OWNER = "noellegazelle6"
    private const val REPO = "kail_location"
    private const val GITHUB_API = "https://api.github.com/repos/$OWNER/$REPO/releases/latest"
    private const val GITHUB_LATEST_DOWNLOAD = "https://github.com/$OWNER/$REPO/releases/latest/download/KailLocation.apk"
    private const val APP_KEY = "KailLocation"

    private val okHttpClient get() = RuoYiClient.okHttpClient

    fun check(context: Context, callback: (UpdateInfo?, String?) -> Unit) {
        checkYudao(context, callback)
    }

    /**
     * 后端（yudao app-api）为第一检查源。
     * 仅在后端不可用（网络失败 / HTTP 错误 / 响应解析失败）时回退到 GitHub；
     * 后端明确返回"无更新"时直接结束，避免两套来源的版本判断不一致。
     */
    private fun checkYudao(context: Context, callback: (UpdateInfo?, String?) -> Unit) {
        val versionCode = getVersionCode(context)
        if (versionCode <= 0L) {
            checkGithub(context, callback)
            return
        }
        val url = "${RuoYiClient.baseUrl}/infra/app-version/check?appKey=$APP_KEY&versionCode=$versionCode"
        KailLog.i(context, TAG, "checkYudao: checking $url")
        val request = Request.Builder()
            .url(url)
            .header("tenant-id", "1")
            .build()
        okHttpClient.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                KailLog.w(context, TAG, "checkYudao: failed: ${e.message}, fallback to github")
                checkGithub(context, callback)
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                if (!response.isSuccessful) {
                    KailLog.w(context, TAG, "checkYudao: HTTP ${response.code}, fallback to github")
                    response.close()
                    checkGithub(context, callback)
                    return
                }
                val res = response.body?.string()
                response.close()
                if (res == null) {
                    checkGithub(context, callback)
                    return
                }
                try {
                    val json = JSONObject(res)
                    val code = json.optInt("code", -1)
                    val data = json.optJSONObject("data")
                    if (code != 0 || data == null) {
                        KailLog.w(context, TAG, "checkYudao: invalid response code=$code data=${data != null}, fallback to github")
                        checkGithub(context, callback)
                        return
                    }
                    if (!data.optBoolean("hasUpdate", false)) {
                        KailLog.i(context, TAG, "checkYudao: backend reports no update")
                        callback(null, null)
                        return
                    }
                    val versionName = data.optString("versionName", "")
                    val description = data.optString("description", "")
                    val fileUrl = data.optString("fileUrl", "")
                    val finalUrl = if (fileUrl.startsWith("http")) fileUrl else "${RuoYiClient.baseUrl.substringBefore("/app-api")}$fileUrl"
                    val filename = decodeFilename(finalUrl)
                    val info = UpdateInfo(
                        version = versionName,
                        content = description,
                        downloadUrl = finalUrl,
                        filename = filename,
                        forceUpdate = data.optBoolean("forceUpdate", false),
                        fileSize = data.optLong("fileSize", 0L),
                        fallbackUrl = GITHUB_LATEST_DOWNLOAD,
                        fallbackFilename = "KailLocation.apk"
                    )
                    KailLog.i(context, TAG, "checkYudao: update available $versionName")
                    callback(info, null)
                } catch (e: Exception) {
                    KailLog.e(context, TAG, "checkYudao: parse error", e)
                    checkGithub(context, callback)
                }
            }
        })
    }

    private fun buildDownloadUrl(tagName: String): String {
        return "https://github.com/$OWNER/$REPO/releases/download/$tagName/KailLocation.apk"
    }

    /**
     * 从下载地址提取文件名，处理 URL 编码与 query 参数。
     */
    private fun decodeFilename(url: String): String {
        val raw = url.substringBefore('?').substringAfterLast('/')
        val decoded = try {
            URLDecoder.decode(raw, "UTF-8")
        } catch (e: Exception) { raw }
        return decoded.takeIf { it.isNotBlank() } ?: "KailLocation.apk"
    }

    private fun getVersionCode(context: Context): Long {
        return try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()
        } catch (e: Exception) { 0L }
    }

    private fun getLocalVersionName(context: Context): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
        } catch (e: Exception) { "" }
    }

    /**
     * 解析 x.y.z 三段版本号（自动去掉 v 前缀），返回三元组用于字典序比较。
     */
    private fun parseVersion(version: String): Triple<Int, Int, Int>? {
        val parts = version.replace(Regex("^v"), "").split(".").mapNotNull { it.toIntOrNull() }
        if (parts.size >= 3) return Triple(parts[0], parts[1], parts[2])
        return null
    }

    /**
     * 逐段比较版本号，newer 严格大于 older 时返回 true。
     */
    private fun isNewer(newer: String, older: String): Boolean {
        val a = parseVersion(newer) ?: return false
        val b = parseVersion(older) ?: return false
        return a.first > b.first ||
            (a.first == b.first && a.second > b.second) ||
            (a.first == b.first && a.second == b.second && a.third > b.third)
    }

    private fun tryHeadCheck(context: Context, callback: (UpdateInfo?, String?) -> Unit) {
        val localVersionName = getLocalVersionName(context)
        val parts = parseVersion(localVersionName) ?: run {
            callback(null, null); return
        }
        val candidates = listOf(
            Triple(parts.first, parts.second, parts.third + 1),
            Triple(parts.first, parts.second + 1, 0),
            Triple(parts.first + 1, 0, 0)
        )
        headNext(context, callback, candidates, 0)
    }

    private fun headNext(
        context: Context, callback: (UpdateInfo?, String?) -> Unit,
        candidates: List<Triple<Int, Int, Int>>, index: Int
    ) {
        if (index >= candidates.size) {
            KailLog.i(context, TAG, "headNext: no update found")
            callback(null, null); return
        }
        val tagName = "v${candidates[index].first}.${candidates[index].second}.${candidates[index].third}"
        val url = buildDownloadUrl(tagName)
        KailLog.i(context, TAG, "headNext: checking $url")
        val request = Request.Builder().url(url).method("HEAD", null).build()
        okHttpClient.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                KailLog.w(context, TAG, "headNext: failed ${e.message}")
                headNext(context, callback, candidates, index + 1)
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.close()
                if (response.isSuccessful) {
                    KailLog.i(context, TAG, "headNext: found $tagName")
                    callback(UpdateInfo(tagName, "", url, "KailLocation.apk"), null)
                } else {
                    headNext(context, callback, candidates, index + 1)
                }
            }
        })
    }

    private fun checkGithub(context: Context, callback: (UpdateInfo?, String?) -> Unit) {
        KailLog.i(context, TAG, "checkGithub: checking $GITHUB_API")
        val request = Request.Builder().url(GITHUB_API).build()
        okHttpClient.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                KailLog.w(context, TAG, "checkGithub: failed: ${e.message}")
                tryHeadCheck(context, callback)
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                if (!response.isSuccessful) {
                    KailLog.w(context, TAG, "checkGithub: HTTP ${response.code}")
                    tryHeadCheck(context, callback)
                    return
                }
                val res = response.body?.string() ?: run {
                    tryHeadCheck(context, callback); return
                }
                try {
                    val json = JSONObject(res)
                    val tagName = json.optString("tag_name", "")
                    if (tagName.isEmpty()) { tryHeadCheck(context, callback); return }
                    val body = json.optString("body", "")
                    val downloadUrl = buildDownloadUrl(tagName)
                    val localVersionName = getLocalVersionName(context)
                    if (isNewer(tagName, localVersionName)) {
                        KailLog.i(context, TAG, "checkGithub: update available $tagName")
                        callback(UpdateInfo(tagName, body, downloadUrl, "KailLocation.apk"), null)
                    } else {
                        KailLog.i(context, TAG, "checkGithub: no update (local=$localVersionName github=$tagName)")
                        callback(null, null)
                    }
                } catch (e: Exception) {
                    KailLog.e(context, TAG, "checkGithub: parse error", e)
                    tryHeadCheck(context, callback)
                }
            }
        })
    }
}
