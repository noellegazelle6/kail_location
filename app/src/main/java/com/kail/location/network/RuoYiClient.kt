package com.kail.location.network

import com.kail.location.BuildConfig
import com.kail.location.utils.KailLog
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object RuoYiClient {

    private const val TAG = "RuoYiClient"
    private const val JSON_TYPE = "application/json"

    var baseUrl: String = BuildConfig.APP_API_URL

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    data class AuthResult(
        val token: String,
        val email: String,
        val id: String,
        val verified: Boolean
    )

    private fun Request.Builder.withTenant(): Request.Builder {
        return this.header("tenant-id", "1")
    }

    private fun Request.Builder.withAuth(token: String): Request.Builder {
        return this.header("Authorization", "Bearer $token")
    }

    fun checkSimulation(token: String): Result<Int> {
        return runCatching {
            val url = "$baseUrl/member/simulation/check"
            val request = Request.Builder()
                .url(url)
                .get()
                .header("Content-Type", JSON_TYPE)
                .withAuth(token)
                .withTenant()
                .build()

            val response = okHttpClient.newCall(request).execute()
            val body = response.body?.string() ?: throw Exception("Empty response")
            val root = JSONObject(body)
            val code = root.optInt("code", -1)
            if (code != 0) {
                throw Exception(root.optString("msg", "获取剩余次数失败"))
            }
            val remaining = root.getJSONObject("data").optInt("remainingToday", 0)
            KailLog.i(null, TAG, "checkSimulation: http=${response.code} code=$code remainingToday=$remaining")
            remaining
        }.onFailure { KailLog.w(null, TAG, "checkSimulation failed: ${it.message}") }
    }

    fun useSimulation(token: String): Result<Unit> {
        return runCatching {
            val url = "$baseUrl/member/simulation/use"
            val request = Request.Builder()
                .url(url)
                .post("{}".toRequestBody(JSON_TYPE.toMediaType()))
                .header("Content-Type", JSON_TYPE)
                .withAuth(token)
                .withTenant()
                .build()

            val response = okHttpClient.newCall(request).execute()
            val body = response.body?.string() ?: throw Exception("Empty response")

            val root = JSONObject(body)
            val code = root.optInt("code", -1)
            KailLog.i(null, TAG, "useSimulation: http=${response.code} code=$code msg=${root.optString("msg", "")}")
            if (code != 0) {
                throw Exception(root.optString("msg", "模拟次数已用完"))
            }
        }.onFailure { KailLog.w(null, TAG, "useSimulation failed: ${it.message}") }
    }

    fun sendMailCode(mail: String, scene: Int): Result<Unit> {
        return runCatching {
            val url = "$baseUrl/member/auth/send-mail-code"
            val json = JSONObject().apply {
                put("mail", mail)
                put("scene", scene)
            }

            val request = Request.Builder()
                .url(url)
                .post(json.toString().toRequestBody(JSON_TYPE.toMediaType()))
                .header("Content-Type", JSON_TYPE)
                .withTenant()
                .build()

            val response = okHttpClient.newCall(request).execute()
            val body = response.body?.string() ?: throw Exception("Empty response")

            val root = JSONObject(body)
            val code = root.optInt("code", -1)
            KailLog.i(null, TAG, "sendMailCode(scene=$scene): http=${response.code} code=$code")
            if (code != 0) {
                throw Exception(root.optString("msg", "发送验证码失败"))
            }
        }.onFailure { KailLog.w(null, TAG, "sendMailCode failed: ${it.message}") }
    }

    fun loginByMail(mail: String, code: String): Result<AuthResult> {
        return runCatching {
            val url = "$baseUrl/member/auth/mail-login"
            val json = JSONObject().apply {
                put("mail", mail)
                put("code", code)
            }

            val request = Request.Builder()
                .url(url)
                .post(json.toString().toRequestBody(JSON_TYPE.toMediaType()))
                .header("Content-Type", JSON_TYPE)
                .withTenant()
                .build()

            val response = okHttpClient.newCall(request).execute()
            val body = response.body?.string() ?: throw Exception("Empty response")

            val root = JSONObject(body)
            val respCode = root.optInt("code", -1)
            // 不打印 body：包含 accessToken。
            KailLog.i(null, TAG, "loginByMail: http=${response.code} code=$respCode")
            if (respCode != 0) {
                throw Exception(root.optString("msg", "登录失败"))
            }

            val data = root.getJSONObject("data")
            AuthResult(
                token = data.getString("accessToken"),
                email = mail,
                id = data.optString("userId", ""),
                verified = true
            )
        }
    }

    data class SubscriptionStatus(
        val active: Boolean,
        val planName: String,
        val expiresAt: String,
        val daysRemaining: Int
    )

    data class SubscriptionPlan(
        val id: Long,
        val name: String,
        val description: String,
        val price: Int,
        val currency: String,
        val billingInterval: String,
        val billingIntervalCount: Int,
        val trialDays: Int
    )

    suspend fun getPlans(token: String): Result<List<SubscriptionPlan>> {
        return runCatching {
            val url = "$baseUrl/member/subscription-plan/list"
            val request = Request.Builder()
                .url(url)
                .get()
                .header("Content-Type", JSON_TYPE)
                .withAuth(token)
                .withTenant()
                .build()
            val response = okHttpClient.newCall(request).execute()
            val body = response.body?.string() ?: throw Exception("Empty response")
            val root = JSONObject(body)
            val respCode = root.optInt("code", -1)
            if (respCode != 0) {
                throw Exception(root.optString("msg", "获取套餐列表失败"))
            }
            val arr = root.getJSONArray("data")
            val plans = mutableListOf<SubscriptionPlan>()
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                plans.add(SubscriptionPlan(
                    id = item.getLong("id"),
                    name = item.getString("name"),
                    description = item.optString("description", ""),
                    price = item.getInt("price"),
                    currency = item.optString("currency", "CNY"),
                    billingInterval = item.optString("billingInterval", "month"),
                    billingIntervalCount = item.optInt("billingIntervalCount", 1),
                    trialDays = item.optInt("trialDays", 0)
                ))
            }
            KailLog.i(null, TAG, "getPlans: http=${response.code} code=$respCode count=${plans.size}")
            plans
        }.onFailure { KailLog.w(null, TAG, "getPlans failed: ${it.message}") }
    }

    data class NoticeInfo(
        val id: Long,
        val title: String,
        val type: Int,
        val content: String,
        val createTime: String
    )

    suspend fun getNoticeList(): Result<List<NoticeInfo>> {
        return runCatching {
            val url = "$baseUrl/system/notice/list"
            val request = Request.Builder()
                .url(url)
                .get()
                .header("Content-Type", JSON_TYPE)
                .withTenant()
                .build()

            val response = okHttpClient.newCall(request).execute()
            val body = response.body?.string() ?: throw Exception("Empty response")
            val root = JSONObject(body)
            val code = root.optInt("code", -1)
            if (code != 0) {
                throw Exception(root.optString("msg", "获取公告失败"))
            }
            val arr = root.optJSONArray("data") ?: return@runCatching emptyList()
            val list = mutableListOf<NoticeInfo>()
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                list.add(NoticeInfo(
                    id = item.getLong("id"),
                    title = item.optString("title", ""),
                    type = item.optInt("type", 0),
                    content = item.optString("content", ""),
                    createTime = item.optString("createTime", "")
                ))
            }
            list
        }.onFailure { KailLog.w(null, TAG, "getNoticeList failed: ${it.message}") }
    }

    data class FaqItem(
        val id: Long,
        val title: String,
        val answerMd: String,
        val sort: Int
    )

    suspend fun getFaqList(): Result<List<FaqItem>> {
        return runCatching {
            val url = "$baseUrl/system/faq/list"
            val request = Request.Builder()
                .url(url)
                .get()
                .header("Content-Type", JSON_TYPE)
                .withTenant()
                .build()

            val response = okHttpClient.newCall(request).execute()
            val body = response.body?.string() ?: throw Exception("Empty response")
            val root = JSONObject(body)
            val code = root.optInt("code", -1)
            if (code != 0) {
                throw Exception(root.optString("msg", "获取常见问题失败"))
            }
            val arr = root.optJSONArray("data") ?: return@runCatching emptyList()
            val list = mutableListOf<FaqItem>()
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                list.add(FaqItem(
                    id = item.getLong("id"),
                    title = item.optString("title", ""),
                    answerMd = item.optString("answerMd", ""),
                    sort = item.optInt("sort", 0)
                ))
            }
            list
        }.onFailure { KailLog.w(null, TAG, "getFaqList failed: ${it.message}") }
    }

    suspend fun getSubscriptionStatus(token: String): Result<SubscriptionStatus> {
        return runCatching {
            val url = "$baseUrl/member/subscription/status"
            val request = Request.Builder()
                .url(url)
                .get()
                .header("Content-Type", JSON_TYPE)
                .withAuth(token)
                .withTenant()
                .build()

            val response = okHttpClient.newCall(request).execute()
            val body = response.body?.string() ?: throw Exception("Empty response")

            val root = JSONObject(body)
            val respCode = root.optInt("code", -1)
            if (respCode != 0) {
                throw Exception(root.optString("msg", "获取订阅状态失败"))
            }

            val data = root.getJSONObject("data")
            val status = SubscriptionStatus(
                active = data.optBoolean("active", false),
                planName = data.optString("planName", ""),
                expiresAt = data.optString("expiresAt", ""),
                daysRemaining = data.optInt("daysRemaining", 0)
            )
            KailLog.i(null, TAG, "getSubscriptionStatus: http=${response.code} active=${status.active} daysRemaining=${status.daysRemaining}")
            status
        }.onFailure { KailLog.w(null, TAG, "getSubscriptionStatus failed: ${it.message}") }
    }

}
