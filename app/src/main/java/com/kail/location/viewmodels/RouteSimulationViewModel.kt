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
import com.kail.location.models.HistoryRecord
import com.kail.location.repositories.DataBaseHistoryLocation
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import com.kail.location.utils.UpdateChecker
import com.kail.location.utils.GoUtils
import com.kail.location.utils.KailLog
import com.kail.location.utils.SimulationDiagnostics
import android.content.Context
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import com.baidu.mapapi.model.LatLng
import com.kail.location.auth.UsageManager
import org.json.JSONObject
import com.baidu.mapapi.search.core.SearchResult
import com.baidu.mapapi.search.geocode.GeoCoder
import com.baidu.mapapi.search.geocode.OnGetGeoCoderResultListener
import com.baidu.mapapi.search.geocode.ReverseGeoCodeOption
import androidx.core.content.ContextCompat
import com.kail.location.R
import com.kail.location.service.Root.ServiceGoRoot
import com.kail.location.service.Developer.ServiceGoDeveloper
import com.kail.location.service.Sandbox.ServiceGoSandbox
import com.kail.location.service.Xposed.ServiceGoXposed
import com.kail.location.utils.service.ServiceConstants
import kotlinx.coroutines.delay

import com.baidu.mapapi.search.sug.SuggestionSearch
import com.baidu.mapapi.search.sug.SuggestionSearchOption
import com.baidu.mapapi.search.sug.OnGetSuggestionResultListener
import com.baidu.mapapi.search.sug.SuggestionResult

/**
 * 路线模拟页面的 ViewModel。
 * 负责加载历史路线并检查应用更新。
 *
 * @property application 应用上下文。
 */
class RouteSimulationViewModel(application: Application) : AndroidViewModel(application) {
    private val dbHelper = DataBaseHistoryLocation(application)
    private var db: SQLiteDatabase? = null

    private val _locationHistoryRecords = MutableStateFlow<List<HistoryRecord>>(emptyList())
    val locationHistoryRecords: StateFlow<List<HistoryRecord>> = _locationHistoryRecords.asStateFlow()

    private val _historyRoutes = MutableStateFlow<List<RouteInfo>>(emptyList())
    /**
     * 历史路线列表的状态流。
     */
    val historyRoutes: StateFlow<List<RouteInfo>> = _historyRoutes.asStateFlow()

    private val _pendingRoutePoints = MutableStateFlow<List<LatLng>?>(null)
    val pendingRoutePoints: StateFlow<List<LatLng>?> = _pendingRoutePoints.asStateFlow()

    private val _pendingRouteName = MutableStateFlow<String?>(null)
    val pendingRouteName: StateFlow<String?> = _pendingRouteName.asStateFlow()

    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)
    /**
     * 可用更新信息的状态流。
     */
    val updateInfo: StateFlow<UpdateInfo?> = _updateInfo.asStateFlow()

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()
    private val _downloadProgress = MutableStateFlow(0)
    val downloadProgress: StateFlow<Int> = _downloadProgress.asStateFlow()
    private val _installUri = MutableStateFlow<android.net.Uri?>(null)
    val installUri: StateFlow<android.net.Uri?> = _installUri.asStateFlow()

    private val _isSimulating = MutableStateFlow(false)
    val isSimulating: StateFlow<Boolean> = _isSimulating.asStateFlow()
    private val _isStarting = MutableStateFlow(false)
    val isStarting: StateFlow<Boolean> = _isStarting.asStateFlow()
    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()
    private val _selectedRouteId = MutableStateFlow<String?>(null)
    val selectedRouteId: StateFlow<String?> = _selectedRouteId.asStateFlow()

    private val _settings = MutableStateFlow(com.kail.location.models.SimulationSettings())
    val settings: StateFlow<com.kail.location.models.SimulationSettings> = _settings.asStateFlow()

    private val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(application)
    private val _runMode = MutableStateFlow("root")
    val runMode: StateFlow<String> = _runMode.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()
    private var startTimeoutJob: kotlinx.coroutines.Job? = null

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ServiceConstants.ACTION_STATUS_CHANGED) return
            val isSim = intent.getBooleanExtra(ServiceConstants.EXTRA_IS_SIMULATING, false)
            val isPau = intent.getBooleanExtra(ServiceConstants.EXTRA_IS_PAUSED, false)
            if (_isStarting.value && !isSim) {
                startTimeoutJob?.cancel()
                _isStarting.value = false
            }
            if (isSim) {
                startTimeoutJob?.cancel()
                _isStarting.value = false
            }
            _isSimulating.value = isSim
            _isPaused.value = isPau
            sharedPreferences.edit()
                .putBoolean("route_sim_is_simulating", isSim)
                .putBoolean("route_sim_is_paused", isPau)
                .apply()
        }
    }

    // Search
    private val _searchResults = MutableStateFlow<List<Map<String, Any>>>(emptyList())
    val searchResults: StateFlow<List<Map<String, Any>>> = _searchResults.asStateFlow()

    private val _searchMarker = MutableStateFlow<LatLng?>(null)
    /**
     * 当前搜索结果选中点的状态流（用于在地图上标点）。
     */
    val searchMarker: StateFlow<LatLng?> = _searchMarker.asStateFlow()

    private val suggestionSearch: SuggestionSearch = SuggestionSearch.newInstance()

    companion object {
        const val POI_NAME = "name"
        const val POI_ADDRESS = "address"
        const val POI_LATITUDE = "latitude"
        const val POI_LONGITUDE = "longitude"

        private const val TAG = "RouteSimVM"

        private fun getServiceClass(mode: String) = when (mode) {
            "root" -> ServiceGoRoot::class.java
            "sandbox" -> ServiceGoSandbox::class.java
            "xposed" -> ServiceGoXposed::class.java
            else -> ServiceGoDeveloper::class.java
        }

        private fun getExtraName(mode: String, rootName: String, devName: String): String {
            return if (mode == "root" || mode == "xposed") rootName else devName
        }
    }
    
    init {
        _isSimulating.value = sharedPreferences.getBoolean("route_sim_is_simulating", false)
        _isPaused.value = sharedPreferences.getBoolean("route_sim_is_paused", false)
        _runMode.value = sharedPreferences.getString("setting_run_mode", "developer") ?: "developer"
        loadSettings()
        loadRoutes()
        try {
            db = dbHelper.writableDatabase
            loadLocationHistoryRecords()
        } catch (_: Exception) {}
        ContextCompat.registerReceiver(
            application,
            statusReceiver,
            IntentFilter(ServiceConstants.ACTION_STATUS_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        suggestionSearch.setOnGetSuggestionResultListener(object : OnGetSuggestionResultListener {
            override fun onGetSuggestionResult(res: SuggestionResult?) {
                if (res == null || res.allSuggestions == null) {
                    _searchResults.value = emptyList()
                    return
                }
                val results = res.allSuggestions.mapNotNull { suggestion ->
                    if (suggestion.pt == null) null
                    else mapOf(
                        POI_NAME to (suggestion.key ?: ""),
                        POI_ADDRESS to (suggestion.address ?: ""),
                        POI_LATITUDE to suggestion.pt.latitude,
                        POI_LONGITUDE to suggestion.pt.longitude
                    )
                }
                _searchResults.value = results
            }
        })
    }

    override fun onCleared() {
        super.onCleared()
        startTimeoutJob?.cancel()
        try {
            getApplication<Application>().unregisterReceiver(statusReceiver)
        } catch (_: Exception) {}
        suggestionSearch.destroy()
    }

    fun search(keyword: String, city: String?) {
        if (keyword.isEmpty()) {
            _searchResults.value = emptyList()
            return
        }
        suggestionSearch.requestSuggestion(
            SuggestionSearchOption()
                .city(city ?: getApplication<Application>().getString(R.string.vm_search_city))
                .keyword(keyword)
        )
    }

    fun clearSearchResults() {
        _searchResults.value = emptyList()
    }

    /**
     * 选中搜索结果。
     * 更新搜索 Marker 并清空搜索结果列表。
     *
     * @param lat 纬度
     * @param lng 经度
     */
    fun selectSearchResult(lat: Double, lng: Double) {
        _searchMarker.value = LatLng(lat, lng)
        _searchResults.value = emptyList()
    }

    /**
     * 清除搜索 Marker。
     */
    fun clearSearchMarker() {
        _searchMarker.value = null
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
                            Toast.makeText(context, context.getString(R.string.vm_update_failed, error), Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, context.getString(R.string.vm_up_to_date), Toast.LENGTH_SHORT).show()
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
    
    fun clearInstallUri() {
        _installUri.value = null
    }
    
    fun clearToastMessage() {
        _toastMessage.value = null
    }

    fun startUpdateDownload(context: Context) {
        val info = _updateInfo.value ?: return
        if (_isDownloading.value) return
        _isDownloading.value = true
        _downloadProgress.value = 0
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val client = okhttp3.OkHttpClient()
                val request = okhttp3.Request.Builder().url(info.downloadUrl).build()
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) throw java.io.IOException("Unexpected code $response")
                val body = response.body ?: throw java.io.IOException("Empty body")
                val total = body.contentLength().takeIf { it > 0 } ?: -1L
                val dir = java.io.File(context.getExternalFilesDir(null), "Updates")
                if (!dir.exists()) dir.mkdirs()
                val outFile = java.io.File(dir, info.filename)
                body.byteStream().use { input ->
                    java.io.FileOutputStream(outFile).use { output ->
                        val buffer = ByteArray(8 * 1024)
                        var bytesRead: Int
                        var sum = 0L
                        while (true) {
                            bytesRead = input.read(buffer)
                            if (bytesRead == -1) break
                            output.write(buffer, 0, bytesRead)
                            sum += bytesRead
                            if (total > 0) {
                                val pct = ((sum * 100) / total).toInt().coerceIn(0, 100)
                                _downloadProgress.value = pct
                            }
                        }
                        output.flush()
                    }
                }
                _downloadProgress.value = 100
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileProvider",
                    outFile
                )
                _installUri.value = uri
            } catch (e: Exception) {
                KailLog.e(getApplication(), TAG, "startUpdateDownload: download update failed", e)
                _isDownloading.value = false
            } finally {
                _isDownloading.value = false
            }
        }
    }

    fun setSimulating(value: Boolean) {
        _isStarting.value = false
        _isSimulating.value = value
        sharedPreferences.edit().putBoolean("route_sim_is_simulating", value).apply()
    }

    fun startSimulation() {
        if (_isStarting.value || _isSimulating.value) return
        viewModelScope.launch {
            val app = getApplication<Application>()
            if (!UsageManager.canStartSimulation(app)) {
                KailLog.persist(app, SimulationDiagnostics.TAG,
                    "路线模拟启动被拦截：未登录或免费次数用尽（canStartSimulation=false）", 'w')
                return@launch
            }
            if (!UsageManager.consumeSimulation(app)) {
                KailLog.persist(app, SimulationDiagnostics.TAG,
                    "路线模拟启动被拦截：扣减模拟次数失败（consumeSimulation=false）", 'w')
                return@launch
            }

            val pending = _pendingRoutePoints.value
            if (pending != null) {
                val newId = saveRouteSync(pending)
                clearPendingRoute()
                if (newId != null) {
                    _selectedRouteId.value = newId
                }
            } else {
                val selId = _selectedRouteId.value
                if (selId != null) {
                    val newId = updateRouteTimestamp(selId)
                    if (newId != null) {
                        _selectedRouteId.value = newId
                    }
                }
            }

            val points = getSelectedRoutePoints()
            if (points == null || points.size < 4) {
                KailLog.persist(app, SimulationDiagnostics.TAG,
                    "路线模拟启动被拦截：未选择有效路线（点数=${points?.size ?: 0}，至少需 4 个坐标值）", 'w')
                _toastMessage.value = app.getString(R.string.route_sim_need_route)
                return@launch
            }

            val currentRunMode = sharedPreferences.getString("setting_run_mode", "root") ?: "root"
            _runMode.value = currentRunMode

            // ROOT 模式靠 ptrace 注入 system_server；刚开机时注入会卡死/重启系统。
            // 开机时长不足时拒绝启动并提示，避免设备卡死。
            if (currentRunMode == "root") {
                val (ready, remainSec) = UsageManager.systemReadiness()
                if (!ready) {
                    KailLog.persist(app, SimulationDiagnostics.TAG,
                        "路线模拟启动被拦截：系统未就绪，开机仅 ${android.os.SystemClock.elapsedRealtime() / 1000}s，" +
                            "需 ${UsageManager.bootReadyThresholdSeconds()}s（还需约 ${remainSec}s）", 'w')
                    _toastMessage.value = app.getString(
                        R.string.vm_system_not_ready,
                        UsageManager.bootReadyThresholdSeconds(),
                        remainSec
                    )
                    return@launch
                }
            }

            if (settings.value.stepFreqSimulation) {
                if (currentRunMode != "root" && currentRunMode != "xposed" && currentRunMode != "sandbox") {
                    KailLog.persist(app, SimulationDiagnostics.TAG,
                        "路线模拟启动被拦截：步频模拟需要 ROOT/Xposed/Sandbox 模式，当前=$currentRunMode", 'w')
                    _toastMessage.value = app.getString(R.string.vm_step_root_required)
                    return@launch
                }
            }

            val serviceClass = getServiceClass(currentRunMode)
            KailLog.persist(app, SimulationDiagnostics.TAG,
                "路线模拟：启动 ${serviceClass.simpleName}（模式=$currentRunMode，路线点=${points.size / 2}，步频=${settings.value.stepFreqSimulation}）")
            val intent = Intent(app, serviceClass)
            val extraRoutePoints = getExtraName(currentRunMode, ServiceGoRoot.EXTRA_ROUTE_POINTS, ServiceGoDeveloper.EXTRA_ROUTE_POINTS)
            val extraRouteLoop = getExtraName(currentRunMode, ServiceGoRoot.EXTRA_ROUTE_LOOP, ServiceGoDeveloper.EXTRA_ROUTE_LOOP)
            val extraJoystickEnabled = getExtraName(currentRunMode, ServiceGoRoot.EXTRA_JOYSTICK_ENABLED, ServiceGoDeveloper.EXTRA_JOYSTICK_ENABLED)
            val extraRouteSpeed = getExtraName(currentRunMode, ServiceGoRoot.EXTRA_ROUTE_SPEED, ServiceGoDeveloper.EXTRA_ROUTE_SPEED)
            val extraCoordType = getExtraName(currentRunMode, ServiceGoRoot.EXTRA_COORD_TYPE, ServiceGoDeveloper.EXTRA_COORD_TYPE)
            val extraSpeedFluctuation = getExtraName(currentRunMode, ServiceGoRoot.EXTRA_SPEED_FLUCTUATION, ServiceGoDeveloper.EXTRA_SPEED_FLUCTUATION)
            intent.putExtra(extraRoutePoints, points)
            intent.putExtra(extraRouteLoop, settings.value.isLoop)
            intent.putExtra(extraJoystickEnabled, false)
            intent.putExtra(extraRouteSpeed, settings.value.speed)
            intent.putExtra(extraCoordType, "BD09")
            intent.putExtra(com.kail.location.views.locationpicker.LocationPickerActivity.ALT_MSG_ID, sharedPreferences.getString("setting_altitude", "55.0")?.toDoubleOrNull() ?: 55.0)
            intent.putExtra(extraSpeedFluctuation, settings.value.speedFluctuation)
            if (currentRunMode == "root" || currentRunMode == "xposed" || currentRunMode == "sandbox") {
                intent.putExtra(ServiceGoRoot.EXTRA_STEP_ENABLED, settings.value.stepFreqSimulation)
                intent.putExtra(ServiceGoRoot.EXTRA_STEP_FREQ, settings.value.stepCadenceSpm)
                intent.putExtra("EXTRA_STEP_SCHEME", sharedPreferences.getString("setting_sim_scheme", "0")?.toIntOrNull() ?: 0)
                intent.putExtra("EXTRA_STEP_MODE", sharedPreferences.getInt("setting_step_mode", 0))
                intent.putExtra("EXTRA_IS_ROUTE_SIMULATION", true)
            }
            if (ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                _isStarting.value = true
                scheduleStartTimeout()
                ContextCompat.startForegroundService(app, intent)
            } else {
                GoUtils.DisplayToast(app, app.getString(R.string.vm_need_location_permission))
                return@launch
            }
            _isPaused.value = false
            sharedPreferences.edit()
                .putBoolean("route_sim_is_paused", false)
                .apply()
        }
    }

    fun stopSimulation() {
        val app = getApplication<Application>()
        val serviceClass = getServiceClass(_runMode.value)
        app.stopService(Intent(app, serviceClass))
        startTimeoutJob?.cancel()
        _isStarting.value = false
        _isSimulating.value = false
        _isPaused.value = false
        sharedPreferences.edit()
            .putBoolean("route_sim_is_simulating", false)
            .putBoolean("route_sim_is_paused", false)
            .apply()
    }

    private fun scheduleStartTimeout() {
        startTimeoutJob?.cancel()
        val app = getApplication<Application>()
        startTimeoutJob = viewModelScope.launch {
            delay(30_000)
            if (_isStarting.value) {
                _isStarting.value = false
                KailLog.persist(app, SimulationDiagnostics.TAG,
                    "路线模拟启动等待服务状态超时：未收到 STATUS_CHANGED=true", 'w')
            }
        }
    }

    fun pauseSimulation() {
        val app = getApplication<Application>()
        val serviceClass = getServiceClass(_runMode.value)
        val controlAction = getExtraName(_runMode.value, ServiceGoRoot.CONTROL_PAUSE, ServiceGoDeveloper.CONTROL_PAUSE)
        val intent = Intent(app, serviceClass)
        intent.putExtra("EXTRA_CONTROL_ACTION", controlAction)
        app.startService(intent)
        _isPaused.value = true
        sharedPreferences.edit().putBoolean("route_sim_is_paused", true).apply()
    }

    fun resumeSimulation() {
        val app = getApplication<Application>()
        val serviceClass = getServiceClass(_runMode.value)
        val controlAction = getExtraName(_runMode.value, ServiceGoRoot.CONTROL_RESUME, ServiceGoDeveloper.CONTROL_RESUME)
        val intent = Intent(app, serviceClass)
        intent.putExtra("EXTRA_CONTROL_ACTION", controlAction)
        app.startService(intent)
        _isPaused.value = false
        sharedPreferences.edit().putBoolean("route_sim_is_paused", false).apply()
    }
    fun setRunMode(mode: String) {
        _runMode.value = mode
        sharedPreferences.edit().putString("setting_run_mode", mode).apply()
        if (mode != "root" && mode != "xposed" && mode != "sandbox") {
            if (_settings.value.stepFreqSimulation) {
                updateStepFreqSimulation(false)
            }
        }
    }

    fun selectRoute(id: String?) {
        _selectedRouteId.value = id
    }

    private val _editingRouteId = MutableStateFlow<String?>(null)
    val editingRouteId: StateFlow<String?> = _editingRouteId.asStateFlow()

    fun editRoute(id: String) {
        _editingRouteId.value = id
    }

    fun clearEditingRoute() {
        _editingRouteId.value = null
    }

    fun getRoutePointsById(id: String): List<LatLng> {
        return try {
            val prefs = PreferenceManager.getDefaultSharedPreferences(getApplication())
            val res = prefs.getString("saved_routes", "[]") ?: "[]"
            val arr = JSONArray(res)
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                if (obj.optLong("time", 0L).toString() == id) {
                    val pts = obj.optJSONArray("points") ?: return emptyList()
                    val result = mutableListOf<LatLng>()
                    for (idx in 0 until pts.length()) {
                        val p = pts.optJSONObject(idx) ?: continue
                        result.add(LatLng(p.optDouble("lat"), p.optDouble("lng")))
                    }
                    return result
                }
            }
            emptyList()
        } catch (e: Exception) {
            KailLog.w(getApplication(), TAG, "getRoutePointsById failed: ${e.message}")
            emptyList()
        }
    }

    fun updateRoute(id: String, points: List<LatLng>) {
        viewModelScope.launch {
            try {
                val prefs = PreferenceManager.getDefaultSharedPreferences(getApplication())
                val res = prefs.getString("saved_routes", "[]") ?: "[]"
                val arr = JSONArray(res)
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    if (obj.optLong("time", 0L).toString() == id) {
                        obj.put("time", System.currentTimeMillis())
                        val pts = JSONArray()
                        points.forEach { pt ->
                            val p = JSONObject()
                            p.put("lat", pt.latitude)
                            p.put("lng", pt.longitude)
                            pts.put(p)
                        }
                        obj.put("points", pts)
                        break
                    }
                }
                prefs.edit().putString("saved_routes", arr.toString()).apply()
                _historyRoutes.value = parseRoutes(arr.toString())
                _editingRouteId.value = null
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    if (obj.optLong("time", 0L).toString() == id) {
                        enrichNamesForRoute(obj)
                        break
                    }
                }
            } catch (e: Exception) {
                KailLog.w(getApplication(), TAG, "updateRoute failed: ${e.message}")
            }
        }
    }

    private fun loadSettings() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(getApplication())
        val speed = prefs.getFloat("route_sim_speed", _settings.value.speed)
        val loop = prefs.getBoolean("route_sim_loop", _settings.value.isLoop)
        val speedFluctuation = prefs.getBoolean("route_sim_speed_fluctuation", _settings.value.speedFluctuation)
        val stepEnabled = prefs.getBoolean("route_sim_step_enabled", _settings.value.stepFreqSimulation)
        val raw = prefs.getFloat("route_sim_step_freq", _settings.value.stepCadenceSpm)
        val stepCadenceSpm = if (raw <= 10f) raw * 60f else raw
        _settings.value = _settings.value.copy(
            speed = speed, 
            isLoop = loop, 
            speedFluctuation = speedFluctuation, 
            stepFreqSimulation = stepEnabled, 
            stepCadenceSpm = stepCadenceSpm
        )
    }

    fun updateSpeed(speed: Float) {
        _settings.value = _settings.value.copy(speed = speed)
        PreferenceManager.getDefaultSharedPreferences(getApplication())
            .edit().putFloat("route_sim_speed", speed).apply()
        if (_isSimulating.value) {
            val app = getApplication<Application>()
            val serviceClass = getServiceClass(_runMode.value)
            val controlAction = getExtraName(_runMode.value, ServiceGoRoot.CONTROL_SET_SPEED, ServiceGoDeveloper.CONTROL_SET_SPEED)
            val routeSpeed = getExtraName(_runMode.value, ServiceGoRoot.EXTRA_ROUTE_SPEED, ServiceGoDeveloper.EXTRA_ROUTE_SPEED)
            val intent = Intent(app, serviceClass)
            intent.putExtra("EXTRA_CONTROL_ACTION", controlAction)
            intent.putExtra(routeSpeed, speed)
            app.startService(intent)
        }
    }

    fun updateLoop(loop: Boolean) {
        _settings.value = _settings.value.copy(isLoop = loop)
        PreferenceManager.getDefaultSharedPreferences(getApplication())
            .edit().putBoolean("route_sim_loop", loop).apply()
    }

    fun updateSpeedFluctuation(enabled: Boolean) {
        _settings.value = _settings.value.copy(speedFluctuation = enabled)
        PreferenceManager.getDefaultSharedPreferences(getApplication())
            .edit().putBoolean("route_sim_speed_fluctuation", enabled).apply()
        if (_isSimulating.value) {
            val app = getApplication<Application>()
            val serviceClass = getServiceClass(_runMode.value)
            val controlAction = getExtraName(_runMode.value, ServiceGoRoot.CONTROL_SET_SPEED_FLUCTUATION, ServiceGoDeveloper.CONTROL_SET_SPEED_FLUCTUATION)
            val speedFluctuation = getExtraName(_runMode.value, ServiceGoRoot.EXTRA_SPEED_FLUCTUATION, ServiceGoDeveloper.EXTRA_SPEED_FLUCTUATION)
            val intent = Intent(app, serviceClass)
            intent.putExtra("EXTRA_CONTROL_ACTION", controlAction)
            intent.putExtra(speedFluctuation, enabled)
            app.startService(intent)
        }
    }

    fun updateStepFreqSimulation(enabled: Boolean) {
        _settings.value = _settings.value.copy(stepFreqSimulation = enabled)
        PreferenceManager.getDefaultSharedPreferences(getApplication())
            .edit().putBoolean("route_sim_step_enabled", enabled).apply()
        if (_isSimulating.value && (_runMode.value == "root" || _runMode.value == "xposed" || _runMode.value == "sandbox")) {
            val app = getApplication<Application>()
            val intent = Intent(app, getServiceClass(_runMode.value))
            intent.putExtra("EXTRA_CONTROL_ACTION", ServiceGoRoot.CONTROL_SET_STEP)
            intent.putExtra(ServiceGoRoot.EXTRA_STEP_ENABLED, enabled)
            intent.putExtra(ServiceGoRoot.EXTRA_STEP_FREQ, _settings.value.stepCadenceSpm)
            app.startService(intent)
        }
    }

    fun updateStepCadenceSpm(spm: Float) {
        _settings.value = _settings.value.copy(stepCadenceSpm = spm)
        PreferenceManager.getDefaultSharedPreferences(getApplication())
            .edit().putFloat("route_sim_step_freq", spm).apply()
        if (_isSimulating.value && (_runMode.value == "root" || _runMode.value == "xposed" || _runMode.value == "sandbox")) {
            val app = getApplication<Application>()
            val intent = Intent(app, getServiceClass(_runMode.value))
            intent.putExtra("EXTRA_CONTROL_ACTION", ServiceGoRoot.CONTROL_SET_STEP)
            intent.putExtra(ServiceGoRoot.EXTRA_STEP_ENABLED, _settings.value.stepFreqSimulation)
            intent.putExtra(ServiceGoRoot.EXTRA_STEP_FREQ, spm)
            app.startService(intent)
        }
    }

    fun updateMode(mode: com.kail.location.models.TransportMode) {
        _settings.value = _settings.value.copy(mode = mode)
    }

    fun loadLocationHistoryRecords() {
        viewModelScope.launch(Dispatchers.IO) {
            val database = db
            if (database == null) return@launch
            try {
                val colInfo = mutableListOf<String>()
                val pc = database.rawQuery("PRAGMA table_info(${DataBaseHistoryLocation.TABLE_NAME})", null)
                while (pc.moveToNext()) { colInfo.add(pc.getString(1)) }
                pc.close()

                val hasFavCol = DataBaseHistoryLocation.DB_COLUMN_FAVORITE in colInfo
                val hasFavTimeCol = DataBaseHistoryLocation.DB_COLUMN_FAVORITE_TIME in colInfo
                val hasFavOrderCol = DataBaseHistoryLocation.DB_COLUMN_FAVORITE_ORDER in colInfo

                val orderClauses = mutableListOf<String>()
                if (hasFavCol) orderClauses.add("${DataBaseHistoryLocation.DB_COLUMN_FAVORITE} DESC")
                orderClauses.add("${DataBaseHistoryLocation.DB_COLUMN_TIMESTAMP} DESC")

                val cursor = database.rawQuery(
                    "SELECT * FROM ${DataBaseHistoryLocation.TABLE_NAME} " +
                    "WHERE ${DataBaseHistoryLocation.DB_COLUMN_ID} > 0 " +
                    "ORDER BY ${orderClauses.joinToString(",")}", null
                )
                val list = mutableListOf<HistoryRecord>()
                while (cursor.moveToNext()) {
                    val id = cursor.getInt(0)
                    val location = cursor.getString(1)
                    val longitude = cursor.getString(2)
                    val latitude = cursor.getString(3)
                    val timeStamp = cursor.getInt(4).toLong()
                    val bd09Longitude = cursor.getString(5)
                    val bd09Latitude = cursor.getString(6)
                    val isFav = if (hasFavCol) cursor.getInt(7) == 1 else false
                    val favTime = if (hasFavTimeCol) cursor.getLong(8) else 0L
                    val favOrder = if (hasFavOrderCol) cursor.getInt(9) else 0
                    list.add(
                        HistoryRecord(
                            id = id,
                            name = location,
                            longitudeWgs84 = longitude,
                            latitudeWgs84 = latitude,
                            timestamp = timeStamp,
                            longitudeBd09 = bd09Longitude,
                            latitudeBd09 = bd09Latitude,
                            displayTime = com.kail.location.utils.GoUtils.timeStamp2Date(timeStamp.toString()),
                            displayWgs84 = "",
                            displayBd09 = "",
                            isFavorite = isFav,
                            favoriteTime = favTime,
                            favoriteOrder = favOrder
                        )
                    )
                }
                cursor.close()
                _locationHistoryRecords.value = list
            } catch (_: Exception) {}
        }
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
                val coordS = String.format("%.6f,%.6f", first.optDouble("lat"), first.optDouble("lng"))
                val coordE = String.format("%.6f,%.6f", last.optDouble("lat"), last.optDouble("lng"))
                val s = obj.optString("startName", coordS).let { if (it.isBlank() || it == "null") coordS else it }
                val e = obj.optString("endName", coordE).let { if (it.isBlank() || it == "null") coordE else it }
                val isFav = obj.optBoolean("isFavorite", false)
                val favTime = obj.optLong("favoriteTime", 0L)
                val favOrder = obj.optInt("favoriteOrder", 0)
                list.add(time to RouteInfo(time.toString(), s, e, "", isFav, favTime, favOrder))
            }
            list.sortByDescending { it.first }
            val routes = list.map { it.second }
            routes.sortedWith(compareByDescending<RouteInfo> { it.isFavorite }.thenBy { it.favoriteOrder })
        } catch (e: Exception) {
            KailLog.w(getApplication(), TAG, "parseRoutes: parse saved routes failed: ${e.message}")
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
                obj.put("isFavorite", false)
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
            } catch (e: Exception) {
                KailLog.w(getApplication(), TAG, "saveRoute: save route failed: ${e.message}")
            }
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
        } catch (e: Exception) {
            KailLog.w(getApplication(), TAG, "getLatestRoutePoints: read latest route points failed: ${e.message}")
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

    fun setPendingRoutePoints(points: List<LatLng>) {
        _pendingRoutePoints.value = points
        _selectedRouteId.value = null
        val name = if (points.isNotEmpty()) {
            String.format("%.4f,%.4f", points.first().latitude, points.first().longitude)
        } else null
        _pendingRouteName.value = name
    }

    fun clearPendingRoute() {
        _pendingRoutePoints.value = null
        _pendingRouteName.value = null
    }

    private fun saveRouteSync(points: List<LatLng>): String? {
        try {
            val prefs = PreferenceManager.getDefaultSharedPreferences(getApplication())
            val existing = prefs.getString("saved_routes", "[]") ?: "[]"
            val arr = JSONArray(existing)
            val obj = JSONObject()
            val time = System.currentTimeMillis()
            obj.put("time", time)
            obj.put("isFavorite", false)
            obj.put("favoriteOrder", 0)
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
            return time.toString()
        } catch (e: Exception) {
            KailLog.w(getApplication(), TAG, "saveRouteSync: save route failed: ${e.message}")
        }
        return null
    }

    private fun updateRouteTimestamp(id: String): String? {
        try {
            val prefs = PreferenceManager.getDefaultSharedPreferences(getApplication())
            val res = prefs.getString("saved_routes", "[]") ?: "[]"
            val arr = JSONArray(res)
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                if (obj.optLong("time", 0L).toString() == id) {
                    val newTime = System.currentTimeMillis()
                    obj.put("time", newTime)
                    prefs.edit().putString("saved_routes", arr.toString()).apply()
                    _historyRoutes.value = parseRoutes(arr.toString())
                    return newTime.toString()
                }
            }
        } catch (e: Exception) {
            KailLog.w(getApplication(), TAG, "updateRouteTimestamp failed: ${e.message}")
        }
        return null
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

    fun toggleFavorite(id: String) {
        viewModelScope.launch {
            val prefs = PreferenceManager.getDefaultSharedPreferences(getApplication())
            val res = prefs.getString("saved_routes", "[]") ?: "[]"
            val arr = JSONArray(res)
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                if (obj.optLong("time", 0L).toString() == id) {
                    val current = obj.optBoolean("isFavorite", false)
                    val newFav = !current
                    obj.put("isFavorite", newFav)
                    if (newFav) {
                        obj.put("favoriteTime", System.currentTimeMillis())
                        val maxOrder = (0 until arr.length()).maxOfOrNull { j ->
                            arr.optJSONObject(j)?.optInt("favoriteOrder", 0) ?: 0
                        } ?: 0
                        obj.put("favoriteOrder", maxOrder + 1)
                    } else {
                        obj.put("favoriteOrder", 0)
                    }
                    break
                }
            }
            prefs.edit().putString("saved_routes", arr.toString()).apply()
            _historyRoutes.value = parseRoutes(arr.toString())
        }
    }

    private fun normalizeFavoriteOrders(arr: JSONArray) {
        val favIndices = (0 until arr.length())
            .filter { arr.optJSONObject(it)?.optBoolean("isFavorite", false) == true }
        favIndices.forEachIndexed { index, i ->
            arr.optJSONObject(i)?.put("favoriteOrder", index + 1)
        }
    }

    fun moveFavoriteUp(id: String) {
        viewModelScope.launch {
            val prefs = PreferenceManager.getDefaultSharedPreferences(getApplication())
            val res = prefs.getString("saved_routes", "[]") ?: "[]"
            val arr = JSONArray(res)
            normalizeFavoriteOrders(arr)
            val favIndices = (0 until arr.length())
                .filter { arr.optJSONObject(it)?.optBoolean("isFavorite", false) == true }
                .sortedBy { arr.optJSONObject(it)?.optInt("favoriteOrder", Int.MAX_VALUE) ?: Int.MAX_VALUE }
            val idx = favIndices.indexOfFirst { arr.optJSONObject(it)?.optLong("time", 0L).toString() == id }
            if (idx <= 0) return@launch
            val curIdx = favIndices[idx]
            val aboveIdx = favIndices[idx - 1]
            val curObj = arr.optJSONObject(curIdx) ?: return@launch
            val aboveObj = arr.optJSONObject(aboveIdx) ?: return@launch
            val curOrder = curObj.optInt("favoriteOrder", 0)
            val aboveOrder = aboveObj.optInt("favoriteOrder", 0)
            curObj.put("favoriteOrder", aboveOrder)
            aboveObj.put("favoriteOrder", curOrder)
            prefs.edit().putString("saved_routes", arr.toString()).apply()
            _historyRoutes.value = parseRoutes(arr.toString())
        }
    }

    fun moveFavoriteDown(id: String) {
        viewModelScope.launch {
            val prefs = PreferenceManager.getDefaultSharedPreferences(getApplication())
            val res = prefs.getString("saved_routes", "[]") ?: "[]"
            val arr = JSONArray(res)
            normalizeFavoriteOrders(arr)
            val favIndices = (0 until arr.length())
                .filter { arr.optJSONObject(it)?.optBoolean("isFavorite", false) == true }
                .sortedBy { arr.optJSONObject(it)?.optInt("favoriteOrder", Int.MAX_VALUE) ?: Int.MAX_VALUE }
            val idx = favIndices.indexOfFirst { arr.optJSONObject(it)?.optLong("time", 0L).toString() == id }
            if (idx < 0 || idx >= favIndices.size - 1) return@launch
            val curIdx = favIndices[idx]
            val belowIdx = favIndices[idx + 1]
            val curObj = arr.optJSONObject(curIdx) ?: return@launch
            val belowObj = arr.optJSONObject(belowIdx) ?: return@launch
            val curOrder = curObj.optInt("favoriteOrder", 0)
            val belowOrder = belowObj.optInt("favoriteOrder", 0)
            curObj.put("favoriteOrder", belowOrder)
            belowObj.put("favoriteOrder", curOrder)
            prefs.edit().putString("saved_routes", arr.toString()).apply()
            _historyRoutes.value = parseRoutes(arr.toString())
        }
    }

    fun setFavoriteOrder(ids: List<String>) {
        viewModelScope.launch {
            val prefs = PreferenceManager.getDefaultSharedPreferences(getApplication())
            val res = prefs.getString("saved_routes", "[]") ?: "[]"
            val arr = JSONArray(res)
            ids.forEachIndexed { index, id ->
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    if (obj.optLong("time", 0L).toString() == id) {
                        obj.put("favoriteOrder", index + 1)
                        break
                    }
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
                if (name.isNotBlank() && name != "null") obj.put("startName", name)
                val prefs = PreferenceManager.getDefaultSharedPreferences(getApplication())
                val res = prefs.getString("saved_routes", "[]") ?: "[]"
                val arr = JSONArray(res)
                prefs.edit().putString("saved_routes", arr.toString()).apply()
                _historyRoutes.value = parseRoutes(arr.toString())
            }
            reverseGeocode(last.optDouble("lat"), last.optDouble("lng")) { name ->
                if (name.isNotBlank() && name != "null") obj.put("endName", name)
                val prefs = PreferenceManager.getDefaultSharedPreferences(getApplication())
                val res = prefs.getString("saved_routes", "[]") ?: "[]"
                val arr = JSONArray(res)
                prefs.edit().putString("saved_routes", arr.toString()).apply()
                _historyRoutes.value = parseRoutes(arr.toString())
            }
        } catch (e: Exception) {
            KailLog.w(getApplication(), TAG, "enrichNamesForRoute: enrich route names failed: ${e.message}")
        }
    }

    private fun reverseGeocode(lat: Double, lng: Double, onResult: (String) -> Unit) {
        try {
            val coder = GeoCoder.newInstance()
            coder.setOnGetGeoCodeResultListener(object : OnGetGeoCoderResultListener {
                override fun onGetGeoCodeResult(geoCodeResult: com.baidu.mapapi.search.geocode.GeoCodeResult?) {}
                override fun onGetReverseGeoCodeResult(result: com.baidu.mapapi.search.geocode.ReverseGeoCodeResult?) {
                    val unknownLocation = getApplication<Application>().getString(R.string.vm_unknown_location)
                    val name = if (result != null && result.error == SearchResult.ERRORNO.NO_ERROR) {
                        result.address ?: unknownLocation
                    } else unknownLocation
                    onResult(name)
                    coder.destroy()
                }
            })
            coder.reverseGeoCode(ReverseGeoCodeOption().location(com.baidu.mapapi.model.LatLng(lat, lng)))
        } catch (e: Exception) {
            KailLog.w(getApplication(), TAG, "reverseGeocode: reverse geocode failed: ${e.message}")
            onResult(getApplication<Application>().getString(R.string.vm_unknown_location))
        }
    }
}
