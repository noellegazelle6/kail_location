package com.kail.location.views.nfcsimulation

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Parcelable
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import com.kail.location.R
import com.kail.location.auth.UsageManager
import com.kail.location.utils.KailLog
import com.kail.location.views.nfcsimulation.NfcSimulationContract.NavigateDestination
import com.kail.location.views.nfcsimulation.NfcSimulationContract.NfcHistoryItem

class NfcSimulationViewModel : ViewModel() {
    companion object {
        private const val TAG = "NfcSimVM"
    }

    private val _nfcEnabled = MutableStateFlow(false)
    val nfcEnabled: StateFlow<Boolean> = _nfcEnabled.asStateFlow()
    
    private val _tagId = MutableStateFlow<String?>(null)
    val tagId: StateFlow<String?> = _tagId.asStateFlow()
    
    private val _tagType = MutableStateFlow<String?>(null)
    val tagType: StateFlow<String?> = _tagType.asStateFlow()
    
    private val _ndefContent = MutableStateFlow<String?>(null)
    val ndefContent: StateFlow<String?> = _ndefContent.asStateFlow()
    
    private val _scanCount = MutableStateFlow(0)
    val scanCount: StateFlow<Int> = _scanCount.asStateFlow()
    
    private val _mockUrl = MutableStateFlow("")
    val mockUrl: StateFlow<String> = _mockUrl.asStateFlow()
    
    private val _mockPackageName = MutableStateFlow("")
    val mockPackageName: StateFlow<String> = _mockPackageName.asStateFlow()
    
    private val _sendResult = MutableStateFlow<String?>(null)
    val sendResult: StateFlow<String?> = _sendResult.asStateFlow()
    
    private val _historyRecords = MutableStateFlow<List<NfcHistoryItem>>(emptyList())
    val historyRecords: StateFlow<List<NfcHistoryItem>> = _historyRecords.asStateFlow()
    
    private val _navigationEvent = MutableSharedFlow<NavigateDestination>()
    val navigationEvent = _navigationEvent.asSharedFlow()
    
    private var context: Context? = null
    
    fun init(context: Context) {
        this.context = context
        loadHistory()
    }
    
    private fun loadHistory() {
        try {
            val prefs = context?.getSharedPreferences("nfc_history", Context.MODE_PRIVATE)
            val json = prefs?.getString("history", "[]") ?: "[]"
            val array = JSONArray(json)
            val list = mutableListOf<NfcHistoryItem>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(NfcHistoryItem(
                    id = obj.getLong("id"),
                    content = obj.getString("content"),
                    type = obj.getString("type"),
                    timestamp = obj.getLong("timestamp"),
                    name = obj.optString("name", ""),
                    isFavorite = obj.optBoolean("isFavorite", false)
                ))
            }
            _historyRecords.value = list.sortedByDescending { it.isFavorite }
        } catch (e: Exception) {
            KailLog.w(null, TAG, "loadHistory: load NFC history failed: ${e.message}")
            _historyRecords.value = emptyList()
        }
    }
    
    private fun saveHistory() {
        try {
            val prefs = context?.getSharedPreferences("nfc_history", Context.MODE_PRIVATE)
            val array = JSONArray()
            _historyRecords.value.forEach { item ->
                val obj = JSONObject().apply {
                    put("id", item.id)
                    put("content", item.content)
                    put("type", item.type)
                    put("timestamp", item.timestamp)
                    put("name", item.name)
                    put("isFavorite", item.isFavorite)
                }
                array.put(obj)
            }
            prefs?.edit()?.putString("history", array.toString())?.apply()
        } catch (e: Exception) {
            KailLog.w(null, TAG, "saveHistory: save NFC history failed: ${e.message}")
        }
    }
    
    fun setNfcEnabled(enabled: Boolean) {
        _nfcEnabled.value = enabled
    }
    
    fun onNfcTagDetected(tag: Tag) {
        _tagId.value = tag.id.toHexString()
        _tagType.value = tag.techList.joinToString(", ")
        
        val ndef = Ndef.get(tag)
        ndef?.let {
            try {
                it.connect()
                val message = it.cachedNdefMessage
                if (message != null) {
                    val content = StringBuilder()
                    for (record in message.records) {
                        // Check the record type and parse accordingly
                        val tnf = record.tnf
                        val type = record.type
                        
                        when (tnf) {
                            NdefRecord.TNF_WELL_KNOWN -> {
                                if (type.contentEquals(NdefRecord.RTD_URI)) {
                                    val uri = record.toUri()
                                    if (uri != null) {
                                        content.append(uri.toString())
                                    }
                                } else if (type.contentEquals(NdefRecord.RTD_TEXT)) {
                                    // Handle RTD Text records
                                    val payload = record.payload
                                    if (payload.isNotEmpty()) {
                                        val text = parseTextPayload(payload)
                                        if (text != null) content.append(text)
                                    }
                                }
                            }
                            NdefRecord.TNF_MIME_MEDIA -> {
                                // If it's a text MIME type, parse as text
                                val payload = record.payload
                                if (payload.isNotEmpty()) {
                                    val text = String(payload, Charsets.UTF_8)
                                    content.append(text)
                                }
                            }
                            NdefRecord.TNF_ABSOLUTE_URI -> {
                                val payload = record.payload
                                if (payload.isNotEmpty()) {
                                    content.append(String(payload))
                                }
                            }
                            NdefRecord.TNF_EXTERNAL_TYPE -> {
                                // Handle external type records (like vnd.android.nfc://ext/)
                                val payload = record.payload
                                if (payload.isNotEmpty()) {
                                    content.append(String(payload, Charsets.UTF_8))
                                }
                            }
                            else -> {
                                // Fallback
                                val payload = record.payload
                                if (payload.isNotEmpty()) {
                                    content.append(String(payload, Charsets.UTF_8))
                                }
                            }
                        }
                    }
                    val contentStr = cleanContent(content.toString())
                    _ndefContent.value = contentStr
                    
                    // Always fill input fields
                    parseAndAutoFill(contentStr)
                    _scanCount.value++
                    
                    // Only add to history if not exists
                    val existingContent = _historyRecords.value.map { it.content }
                    if (!existingContent.contains(contentStr)) {
                        addToHistory(contentStr, tag.techList.joinToString(", "))
                    }
                }
                it.close()
            } catch (e: Exception) {
                KailLog.w(null, TAG, "onNfcTagDetected: read NDEF message failed: ${e.message}")
                _ndefContent.value = context?.getString(R.string.nfc_sim_read_failed, e.message) ?: context?.getString(R.string.nfc_read_failed, e.message ?: "") ?: "read failed: ${e.message}"
            }
        }
    }
    
    fun onNdefDiscovered(rawMessages: Array<out android.os.Parcelable>?) {
        rawMessages?.forEach { parcelable ->
            if (parcelable is NdefMessage) {
                val content = StringBuilder()
                for (record in parcelable.records) {
                    val tnf = record.tnf
                    val type = record.type
                    
                    when (tnf) {
                        NdefRecord.TNF_WELL_KNOWN -> {
                            if (type.contentEquals(NdefRecord.RTD_URI)) {
                                val uri = record.toUri()
                                if (uri != null) {
                                    content.append(uri.toString())
                                }
                            } else if (type.contentEquals(NdefRecord.RTD_TEXT)) {
                                val payload = record.payload
                                if (payload.isNotEmpty()) {
                                    val text = parseTextPayload(payload)
                                    if (text != null) content.append(text)
                                }
                            }
                        }
                        NdefRecord.TNF_MIME_MEDIA -> {
                            val payload = record.payload
                            if (payload.isNotEmpty()) {
                                content.append(String(payload, Charsets.UTF_8))
                            }
                        }
                        NdefRecord.TNF_ABSOLUTE_URI -> {
                            val payload = record.payload
                            if (payload.isNotEmpty()) {
                                content.append(String(payload))
                            }
                        }
                        NdefRecord.TNF_EXTERNAL_TYPE -> {
                            val payload = record.payload
                            if (payload.isNotEmpty()) {
                                content.append(String(payload, Charsets.UTF_8))
                            }
                        }
                        else -> {
                            val payload = record.payload
                            if (payload.isNotEmpty()) {
                                content.append(String(payload, Charsets.UTF_8))
                            }
                        }
                    }
                }
                val contentStr = cleanContent(content.toString())
                _ndefContent.value = contentStr
                
                // Always fill input fields
                parseAndAutoFill(contentStr)
                _scanCount.value++
                
                // Only add to history if not exists
                val existingContent = _historyRecords.value.map { it.content }
                if (!existingContent.contains(contentStr)) {
                    addToHistory(contentStr, "NDEF")
                }
            }
        }
    }
    
    private fun cleanContent(content: String): String {
        return content
            .replace(" ", "")
            .replace("\n", "")
            .replace("\r", "")
            .replace("\t", "")
            .trim()
    }
    
    private fun parseTextPayload(payload: ByteArray): String? {
        return try {
            if (payload.isEmpty()) return null
            val charsetName = if ((payload[0].toInt() and 0x80) == 0) "UTF-8" else "UTF-16"
            val languageCodeLength = payload[0].toInt() and 0x3F
            String(
                payload,
                1 + languageCodeLength,
                payload.size - 1 - languageCodeLength,
                charset(charsetName)
            )
        } catch (e: Exception) {
            KailLog.d(null, TAG, "parseTextPayload: decode with declared charset failed, falling back to UTF-8: ${e.message}")
            try {
                String(payload, Charsets.UTF_8)
            } catch (e2: Exception) {
                KailLog.d(null, TAG, "parseTextPayload: UTF-8 fallback decode failed: ${e2.message}")
                null
            }
        }
    }
    
    private fun parseAndAutoFill(content: String) {
        try {
            val packagePatterns = listOf("com.", "org.", "net.", "android.", "com.google.")
            
            var url = content
            var packageName = ""
            
            for (pattern in packagePatterns) {
                val idx = content.lastIndexOf(pattern)
                if (idx != -1 && idx > 0) {
                    packageName = content.substring(idx)
                    url = content.substring(0, idx)
                    break
                }
            }
            
            if (!url.startsWith("http://") && !url.startsWith("https://") && url.isNotEmpty()) {
                url = "https://$url"
            }
            
            _mockUrl.value = url
            _mockPackageName.value = packageName
        } catch (e: Exception) {
            KailLog.w(null, TAG, "parseAndAutoFill: parse NFC content failed: ${e.message}")
            _mockUrl.value = content
            _mockPackageName.value = ""
        }
    }
    
    fun updateMockUrl(url: String) {
        _mockUrl.value = url
    }
    
    fun updateMockPackageName(packageName: String) {
        _mockPackageName.value = packageName
    }
    
    fun applyFromHistory(item: NfcHistoryItem) {
        parseAndAutoFill(item.content)
        _scanCount.value++
    }
    
    fun sendMockNfc(context: Context) {
        if (_mockUrl.value.isBlank() && _mockPackageName.value.isBlank()) {
            _sendResult.value = context.getString(R.string.nfc_sim_input_hint)
            return
        }

        GlobalScope.launch {
            if (!UsageManager.canStartSimulation(context)) {
                return@launch
            }
            if (!UsageManager.consumeSimulation(context)) {
                return@launch
            }
            try {
                val result = dispatchNfc(context, _mockUrl.value, _mockPackageName.value)
                _sendResult.value = result
                saveManuallyEnteredToHistory()
            } catch (e: Exception) {
                KailLog.w(null, TAG, "sendMockNfc: dispatch mock NFC failed: ${e.message}")
                _sendResult.value = context.getString(R.string.nfc_sim_send_failed, e.message)
            }
        }
    }

    private fun saveManuallyEnteredToHistory() {
        val url = _mockUrl.value.trim()
        val pkg = _mockPackageName.value.trim()
        if (url.isEmpty() && pkg.isEmpty()) return
        val content = if (pkg.isEmpty()) url else "$url$pkg"
        val existingContent = _historyRecords.value.map { it.content }
        if (!existingContent.contains(content)) {
            addToHistory(content, "manual")
        }
    }
    
    private fun dispatchNfc(context: Context, url: String, packageName: String): String {
        return try {
            val uri = if (url.isNotBlank()) Uri.parse(url) else Uri.parse("https://example.com")
            val pkg = packageName.ifBlank { null }
            
            val uriRecord = NdefRecord.createUri(uri)
            val appRecord = if (pkg != null) NdefRecord.createApplicationRecord(pkg) else null
            
            val records = if (appRecord != null) arrayOf(uriRecord, appRecord) else arrayOf(uriRecord)
            val message = NdefMessage(records)
            
            val intent = Intent(NfcAdapter.ACTION_NDEF_DISCOVERED)
            intent.setData(uri)
            if (pkg != null) {
                intent.setPackage(pkg)
            }
            intent.putExtra(NfcAdapter.EXTRA_NDEF_MESSAGES, arrayOf<Parcelable>(message))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            
            context.startActivity(intent)
            context.getString(R.string.nfc_sim_sent, url.ifBlank { packageName })
        } catch (e: Exception) {
            KailLog.w(null, TAG, "dispatchNfc: send NDEF discovered intent failed, falling back to ACTION_VIEW: ${e.message}")
            try {
                val viewIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                if (packageName.isNotBlank()) {
                    viewIntent.setPackage(packageName)
                }
                viewIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(viewIntent)
                context.getString(R.string.nfc_sim_opened, url)
            } catch (e2: Exception) {
                KailLog.w(null, TAG, "dispatchNfc: ACTION_VIEW fallback failed: ${e2.message}")
                context.getString(R.string.nfc_sim_send_failed, e2.message)
            }
        }
    }
    
    private fun addToHistory(content: String, type: String) {
        val currentList = _historyRecords.value.toMutableList()
        val newItem = NfcHistoryItem(
            id = System.currentTimeMillis(),
            content = content,
            type = type,
            timestamp = System.currentTimeMillis(),
            name = ""
        )
        currentList.add(0, newItem)
        if (currentList.size > 50) {
            currentList.removeAt(currentList.size - 1)
        }
        _historyRecords.value = currentList
        saveHistory()
    }
    
    fun renameHistory(id: Long, newName: String) {
        val currentList = _historyRecords.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == id }
        if (index != -1) {
            currentList[index] = currentList[index].copy(name = newName)
            _historyRecords.value = currentList
            saveHistory()
        }
    }
    
    fun deleteHistory(id: Long) {
        val currentList = _historyRecords.value.toMutableList()
        currentList.removeAll { it.id == id }
        _historyRecords.value = currentList
        saveHistory()
    }

    fun toggleFavorite(id: Long) {
        val currentList = _historyRecords.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == id }
        if (index != -1) {
            currentList[index] = currentList[index].copy(isFavorite = !currentList[index].isFavorite)
            _historyRecords.value = currentList.sortedByDescending { it.isFavorite }
            saveHistory()
        }
    }
    
    fun clearHistory() {
        _historyRecords.value = emptyList()
        saveHistory()
    }
    
    fun clearTag() {
        _tagId.value = null
        _tagType.value = null
        _ndefContent.value = null
    }
    
    fun clearSendResult() {
        _sendResult.value = null
    }
    
    private fun ByteArray.toHexString(): String = joinToString(":") { "%02X".format(it) }
    
    fun navigateTo(navId: Int) {
        kotlinx.coroutines.GlobalScope.launch {
            _navigationEvent.emit(NavigateDestination(navId))
        }
    }
}