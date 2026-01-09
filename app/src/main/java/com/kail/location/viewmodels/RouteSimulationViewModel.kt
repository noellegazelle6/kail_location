package com.kail.location.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import com.kail.location.models.RouteInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.util.ArrayList

import com.kail.location.models.UpdateInfo
import com.kail.location.utils.UpdateChecker
import android.content.Context
import android.widget.Toast
import com.baidu.mapapi.model.LatLng
import org.json.JSONObject
import com.baidu.mapapi.search.core.SearchResult
import com.baidu.mapapi.search.geocode.GeoCoder
import com.baidu.mapapi.search.geocode.OnGetGeoCoderResultListener
import com.baidu.mapapi.search.geocode.ReverseGeoCodeOption

/**
 * 路线模拟页面的 ViewModel。
 * 负责加载历史路线并检查应用更新。
 *
 * @property application 应用上下文。
 */
class RouteSimulationViewModel(application: Application) : AndroidViewModel(application) {
    private val _historyRoutes = MutableStateFlow<List<RouteInfo>>(emptyList())
    /**
     * 历史路线列表的状态流。
     */
    val historyRoutes: StateFlow<List<RouteInfo>> = _historyRoutes.asStateFlow()

    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)
    /**
     * 可用更新信息的状态流。
     */
    val updateInfo: StateFlow<UpdateInfo?> = _updateInfo.asStateFlow()

    private val _isSimulating = MutableStateFlow(false)
    val isSimulating: StateFlow<Boolean> = _isSimulating.asStateFlow()
    private val _selectedRouteId = MutableStateFlow<String?>(null)
    val selectedRouteId: StateFlow<String?> = _selectedRouteId.asStateFlow()

    private val _settings = MutableStateFlow(com.kail.location.models.SimulationSettings())
    val settings: StateFlow<com.kail.location.models.SimulationSettings> = _settings.asStateFlow()

    private val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(application)
    private val _runMode = MutableStateFlow("noroot")
    val runMode: StateFlow<String> = _runMode.asStateFlow()
    
    init {
        _runMode.value = sharedPreferences.getString("setting_run_mode", "noroot") ?: "noroot"
        loadSettings()
        loadRoutes()
    }

    /**
     * 检查应用更新。
     *
     * @param context 用于检查更新的上下文。
     * @param isAuto 是否为自动检查。
     */
    fun checkUpdate(context: Context, isAuto: Boolean = false) {
        UpdateChecker.check(context) { info, error ->
            if (info != null) {
                _updateInfo.value = info
            } else {
                if (!isAuto) {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        if (error != null) {
                            Toast.makeText(context, "检查更新失败: $error", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "当前已是最新版本", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    /**
     * 关闭更新弹窗。
     */
    fun dismissUpdateDialog() {
        _updateInfo.value = null
    }

    fun setSimulating(value: Boolean) {
        _isSimulating.value = value
    }

    fun setRunMode(mode: String) {
        _runMode.value = mode
        sharedPreferences.edit().putString("setting_run_mode", mode).apply()
    }

    fun selectRoute(id: String?) {
        _selectedRouteId.value = id
    }

    private fun loadSettings() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(getApplication())
        val speed = prefs.getFloat("route_sim_speed", _settings.value.speed)
        val loop = prefs.getBoolean("route_sim_loop", _settings.value.isLoop)
        _settings.value = _settings.value.copy(speed = speed, isLoop = loop)
    }

    fun updateSpeed(speed: Float) {
        _settings.value = _settings.value.copy(speed = speed)
        PreferenceManager.getDefaultSharedPreferences(getApplication())
            .edit().putFloat("route_sim_speed", speed).apply()
    }

    fun updateLoop(loop: Boolean) {
        _settings.value = _settings.value.copy(isLoop = loop)
        PreferenceManager.getDefaultSharedPreferences(getApplication())
            .edit().putBoolean("route_sim_loop", loop).apply()
    }

    /**
     * 从 SharedPreferences 加载已保存的路线。
     */
    fun loadRoutes() {
        viewModelScope.launch {
            val prefs = PreferenceManager.getDefaultSharedPreferences(getApplication())
            val res = prefs.getString("saved_routes", "[]") ?: "[]"
            val list = parseRoutes(res)
            _historyRoutes.value = list
            enrichRouteNamesIfNeeded()
        }
    }

    /**
     * 解析路线的 JSON 字符串为 RouteInfo 列表。
     *
     * @param json 包含路线数据的 JSON 字符串。
     * @return RouteInfo 列表。
     */
    private fun parseRoutes(json: String): List<RouteInfo> {
        return try {
            val arr = JSONArray(json)
            val list = ArrayList<Pair<Long, RouteInfo>>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val time = obj.optLong("time", 0L)
                val points = obj.optJSONArray("points") ?: continue
                if (points.length() == 0) continue
                
                val first = points.optJSONObject(0) ?: continue
                val last = points.optJSONObject(points.length() - 1) ?: continue
                val s = obj.optString("startName",
                    String.format("%.6f,%.6f", first.optDouble("lat"), first.optDouble("lng")))
                val e = obj.optString("endName",
                    String.format("%.6f,%.6f", last.optDouble("lat"), last.optDouble("lng")))
                list.add(time to RouteInfo(time.toString(), s, e, ""))
            }
            list.sortByDescending { it.first }
            list.map { it.second }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveRoute(points: List<LatLng>) {
        viewModelScope.launch {
            try {
                val prefs = PreferenceManager.getDefaultSharedPreferences(getApplication())
                val existing = prefs.getString("saved_routes", "[]") ?: "[]"
                val arr = JSONArray(existing)
                val obj = JSONObject()
                obj.put("time", System.currentTimeMillis())
                val pts = JSONArray()
                points.forEach { pt ->
                    val p = JSONObject()
                    p.put("lat", pt.latitude)
                    p.put("lng", pt.longitude)
                    pts.put(p)
                }
                obj.put("points", pts)
                arr.put(obj)
                prefs.edit().putString("saved_routes", arr.toString()).apply()
                _historyRoutes.value = parseRoutes(arr.toString())
                enrichNamesForRoute(obj)
            } catch (_: Exception) {}
        }
    }

    fun getLatestRoutePoints(): DoubleArray? {
        return try {
            val prefs = PreferenceManager.getDefaultSharedPreferences(getApplication())
            val res = prefs.getString("saved_routes", "[]") ?: "[]"
            val arr = JSONArray(res)
            if (arr.length() == 0) return null
            val obj = arr.optJSONObject(arr.length() - 1) ?: return null
            val points = obj.optJSONArray("points") ?: return null
            val out = DoubleArray(points.length() * 2)
            var i = 0
            for (idx in 0 until points.length()) {
                val p = points.optJSONObject(idx) ?: continue
                out[i++] = p.optDouble("lng")
                out[i++] = p.optDouble("lat")
            }
            out
        } catch (_: Exception) {
            null
        }
    }

    fun getSelectedRoutePoints(): DoubleArray? {
        val id = _selectedRouteId.value
        val prefs = PreferenceManager.getDefaultSharedPreferences(getApplication())
        val res = prefs.getString("saved_routes", "[]") ?: "[]"
        val arr = JSONArray(res)
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            if (obj.optLong("time", 0L).toString() == id) {
                val points = obj.optJSONArray("points") ?: return null
                val out = DoubleArray(points.length() * 2)
                var j = 0
                for (idx in 0 until points.length()) {
                    val p = points.optJSONObject(idx) ?: continue
                    out[j++] = p.optDouble("lng")
                    out[j++] = p.optDouble("lat")
                }
                return out
            }
        }
        return getLatestRoutePoints()
    }

    fun renameRoute(id: String, newName: String) {
        viewModelScope.launch {
            val prefs = PreferenceManager.getDefaultSharedPreferences(getApplication())
            val res = prefs.getString("saved_routes", "[]") ?: "[]"
            val arr = JSONArray(res)
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                if (obj.optLong("time", 0L).toString() == id) {
                    obj.put("label", newName)
                    // 也更新 start/end 文本以便旧界面显示更友好
                    obj.put("startName", newName)
                    obj.put("endName", newName)
                    break
                }
            }
            prefs.edit().putString("saved_routes", arr.toString()).apply()
            _historyRoutes.value = parseRoutes(arr.toString())
        }
    }

    fun deleteRoute(id: String) {
        viewModelScope.launch {
            val prefs = PreferenceManager.getDefaultSharedPreferences(getApplication())
            val res = prefs.getString("saved_routes", "[]") ?: "[]"
            val arr = JSONArray(res)
            val outArr = JSONArray()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                if (obj.optLong("time", 0L).toString() != id) {
                    outArr.put(obj)
                }
            }
            prefs.edit().putString("saved_routes", outArr.toString()).apply()
            _historyRoutes.value = parseRoutes(outArr.toString())
            if (_selectedRouteId.value == id) _selectedRouteId.value = null
        }
    }

    private fun enrichRouteNamesIfNeeded() {
        viewModelScope.launch {
            val prefs = PreferenceManager.getDefaultSharedPreferences(getApplication())
            val res = prefs.getString("saved_routes", "[]") ?: "[]"
            val arr = JSONArray(res)
            var changed = false
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                if (!obj.has("startName") || !obj.has("endName")) {
                    enrichNamesForRoute(obj)
                    changed = true
                }
            }
            if (changed) {
                prefs.edit().putString("saved_routes", arr.toString()).apply()
                _historyRoutes.value = parseRoutes(arr.toString())
            }
        }
    }

    private fun enrichNamesForRoute(obj: JSONObject) {
        try {
            val points = obj.optJSONArray("points") ?: return
            if (points.length() < 1) return
            val first = points.optJSONObject(0) ?: return
            val last = points.optJSONObject(points.length() - 1) ?: return
            reverseGeocode(first.optDouble("lat"), first.optDouble("lng")) { name ->
                obj.put("startName", name)
                val prefs = PreferenceManager.getDefaultSharedPreferences(getApplication())
                val res = prefs.getString("saved_routes", "[]") ?: "[]"
                val arr = JSONArray(res)
                prefs.edit().putString("saved_routes", arr.toString()).apply()
                _historyRoutes.value = parseRoutes(arr.toString())
            }
            reverseGeocode(last.optDouble("lat"), last.optDouble("lng")) { name ->
                obj.put("endName", name)
                val prefs = PreferenceManager.getDefaultSharedPreferences(getApplication())
                val res = prefs.getString("saved_routes", "[]") ?: "[]"
                val arr = JSONArray(res)
                prefs.edit().putString("saved_routes", arr.toString()).apply()
                _historyRoutes.value = parseRoutes(arr.toString())
            }
        } catch (_: Exception) {}
    }

    private fun reverseGeocode(lat: Double, lng: Double, onResult: (String) -> Unit) {
        try {
            val coder = GeoCoder.newInstance()
            coder.setOnGetGeoCodeResultListener(object : OnGetGeoCoderResultListener {
                override fun onGetGeoCodeResult(geoCodeResult: com.baidu.mapapi.search.geocode.GeoCodeResult?) {}
                override fun onGetReverseGeoCodeResult(result: com.baidu.mapapi.search.geocode.ReverseGeoCodeResult?) {
                    val name = if (result != null && result.error == SearchResult.ERRORNO.NO_ERROR) {
                        result.address ?: "未知地点"
                    } else "未知地点"
                    onResult(name)
                    coder.destroy()
                }
            })
            coder.reverseGeoCode(ReverseGeoCodeOption().location(com.baidu.mapapi.model.LatLng(lat, lng)))
        } catch (_: Exception) {
            onResult("未知地点")
        }
    }
}
