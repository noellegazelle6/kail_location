package com.kail.location.utils

import android.content.Context
import com.kail.location.models.UpdateInfo
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object UpdateChecker {
    private const val TAG = "UpdateChecker"
    private const val OWNER = "noellegazelle6"
    private const val REPO = "kail_location"
    private const val GITHUB_API = "https://api.github.com/repos/$OWNER/$REPO/releases/latest"

    private val trustAllCertificates = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    }

    private val okHttpClient = OkHttpClient.Builder()
        .sslSocketFactory(
            SSLContext.getInstance("TLS").apply {
                init(null, arrayOf<TrustManager>(trustAllCertificates), java.security.SecureRandom())
            }.socketFactory,
            trustAllCertificates
        )
        .hostnameVerifier { _, _ -> true }
        .build()

    fun check(context: Context, callback: (UpdateInfo?, String?) -> Unit) {
        checkGithub(context, callback)
    }

    private fun buildDownloadUrl(tagName: String): String {
        return "https://github.com/$OWNER/$REPO/releases/download/$tagName/KailLocation.apk"
    }

    private fun getLocalVersionName(context: Context): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
        } catch (e: Exception) { "" }
    }

    private fun parseVersion(version: String): Triple<Int, Int, Int>? {
        val parts = version.replace(Regex("^v"), "").split(".").mapNotNull { it.toIntOrNull() }
        if (parts.size >= 3) return Triple(parts[0], parts[1], parts[2])
        return null
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
                    val versionNew = tagName.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0
                    val versionOld = localVersionName.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0
                    if (versionNew > versionOld) {
                        KailLog.i(context, TAG, "checkGithub: update available $tagName")
                        callback(UpdateInfo(tagName, body, downloadUrl, "KailLocation.apk"), null)
                    } else {
                        KailLog.i(context, TAG, "checkGithub: no update (local=$versionOld github=$versionNew)")
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
