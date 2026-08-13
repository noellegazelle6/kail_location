package com.kail.location.viewmodels

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kail.location.network.RuoYiClient
import com.kail.location.utils.KailLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class FaqViewModel(application: Application) : AndroidViewModel(application) {

    private companion object {
        const val TAG = "FaqViewModel"
        const val PREF_NAME = "faq_cache"
        const val KEY_FAQS = "faq_list_json"
        const val KEY_UPDATED_AT = "faq_updated_at"
    }

    private val prefs = application.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    var faqList by mutableStateOf<List<RuoYiClient.FaqItem>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var expandedId by mutableStateOf<Long?>(null)
        private set
    var lastUpdatedAt by mutableStateOf<String?>(null)
        private set

    fun loadFaqs() {
        // 1. 先展示本地缓存，秒开
        if (faqList.isEmpty()) {
            val cached = readCache()
            if (cached.isNotEmpty()) {
                faqList = cached
                lastUpdatedAt = prefs.getString(KEY_UPDATED_AT, null)
            }
        }
        // 2. 再请求最新数据（有缓存时静默刷新，无缓存才显示 loading）
        loading = faqList.isEmpty()
        error = null
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                RuoYiClient.getFaqList()
            }
            result.fold(
                onSuccess = { list ->
                    faqList = list
                    lastUpdatedAt = nowString()
                    loading = false
                    saveCache(list)
                },
                onFailure = { e ->
                    error = e.message ?: "加载失败"
                    loading = false
                    KailLog.w(null, TAG, "loadFaqs failed: ${e.message}")
                }
            )
        }
    }

    fun toggle(id: Long) {
        expandedId = if (expandedId == id) null else id
    }

    fun retry() = loadFaqs()

    /** 读取本地缓存 */
    private fun readCache(): List<RuoYiClient.FaqItem> {
        return runCatching {
            val json = prefs.getString(KEY_FAQS, null) ?: return emptyList()
            val arr = JSONArray(json)
            buildList {
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    add(
                        RuoYiClient.FaqItem(
                            id = item.getLong("id"),
                            title = item.optString("title", ""),
                            answerMd = item.optString("answerMd", ""),
                            sort = item.optInt("sort", 0)
                        )
                    )
                }
            }
        }.getOrElse {
            KailLog.w(null, TAG, "readCache failed: ${it.message}")
            emptyList()
        }
    }

    /** 保存本地缓存 */
    private fun saveCache(list: List<RuoYiClient.FaqItem>) {
        runCatching {
            val arr = JSONArray()
            list.forEach { item ->
                arr.put(
                    JSONObject()
                        .put("id", item.id)
                        .put("title", item.title)
                        .put("answerMd", item.answerMd)
                        .put("sort", item.sort)
                )
            }
            prefs.edit()
                .putString(KEY_FAQS, arr.toString())
                .putString(KEY_UPDATED_AT, nowString())
                .apply()
        }.onFailure { KailLog.w(null, TAG, "saveCache failed: ${it.message}") }
    }

    private fun nowString(): String {
        return java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
    }
}
