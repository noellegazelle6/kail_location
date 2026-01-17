package com.kail.location.utils

import android.content.Context
import com.kail.location.models.UpdateInfo
import okhttp3.*
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

/**
 * 从 GitHub 检查应用更新的工具。
 */
object UpdateChecker {
    private const val GITHUB_API_URL = "https://api.github.com/repos/noellegazelle6/kail_location/releases/latest"

    /**
     * 检查 GitHub Releases 是否有新版本。
     *
     * @param context 用于获取当前版本号的上下文。
     * @param callback 若存在更新返回 [UpdateInfo]，否则返回错误信息或空。
     */
    fun check(context: Context, callback: (UpdateInfo?, String?) -> Unit) {
        val client = OkHttpClient()
        val request = Request.Builder()
            .url(GITHUB_API_URL)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(null, e.message)
            }

            override fun onResponse(call: Call, response: Response) {
                val res = response.body?.string()
                if (res == null) {
                    callback(null, "Empty response")
                    return
                }

                try {
                    val jsonObject = JSONObject(res)
                    if (!jsonObject.has("tag_name")) {
                         callback(null, "No tag_name in response")
                         return
                    }
                    val tagName = jsonObject.getString("tag_name")
                    val body = jsonObject.getString("body")
                    val assets = jsonObject.getJSONArray("assets")

                    if (assets.length() > 0) {
                        val asset = assets.getJSONObject(0)
                        val downloadUrl = asset.getString("browser_download_url")
                        val filename = asset.getString("name")

                        val versionNew = try {
                            tagName.replace(Regex("[^0-9]"), "").toInt()
                        } catch (e: Exception) {
                            0
                        }
                        val localVersionName = GoUtils.getVersionName(context)
                        val versionOld = try {
                            localVersionName.replace(Regex("[^0-9]"), "").toInt()
                        } catch (e: Exception) {
                            0
                        }

                        if (versionNew > versionOld) {
                            callback(
                                UpdateInfo(
                                    version = tagName,
                                    content = body,
                                    downloadUrl = downloadUrl,
                                    filename = filename
                                ),
                                null
                            )
                        } else {
                            // No update needed
                            callback(null, null) // Success but no update
                        }
                    } else {
                        callback(null, "No assets found")
                    }
                } catch (e: JSONException) {
                    e.printStackTrace()
                    callback(null, e.message)
                }
            }
        })
    }
}
