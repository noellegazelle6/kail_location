package com.kail.location.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.preference.PreferenceManager
import com.baidu.mapapi.model.LatLng
import com.baidu.mapapi.search.core.SearchResult
import com.baidu.mapapi.search.route.BikingRouteResult
import com.baidu.mapapi.search.route.DrivingRoutePlanOption
import com.baidu.mapapi.search.route.DrivingRouteResult
import com.baidu.mapapi.search.route.IndoorRouteResult
import com.baidu.mapapi.search.route.IntegralRouteResult
import com.baidu.mapapi.search.route.MassTransitRouteResult
import com.baidu.mapapi.search.route.OnGetRoutePlanResultListener
import com.baidu.mapapi.search.route.PlanNode
import com.baidu.mapapi.search.route.RoutePlanSearch
import com.baidu.mapapi.search.route.TransitRouteResult
import com.baidu.mapapi.search.route.WalkingRouteResult
import com.baidu.mapapi.search.sug.OnGetSuggestionResultListener
import com.baidu.mapapi.search.sug.SuggestionResult
import com.baidu.mapapi.search.sug.SuggestionSearch
import com.baidu.mapapi.search.sug.SuggestionSearchOption
import com.kail.location.models.RouteInfo
import com.kail.location.R
import com.kail.location.service.Root.ServiceGoRoot
import com.kail.location.service.Developer.ServiceGoDeveloper
import com.kail.location.service.Xposed.ServiceGoXposed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.kail.location.utils.KailLog
import com.kail.location.utils.GoUtils
import com.kail.location.auth.UsageManager
import com.kail.location.models.UpdateInfo
import com.kail.location.utils.UpdateChecker
import android.content.Context
import android.widget.Toast
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay


import com.kail.location.data.local.AppDatabase
import com.kail.location.repositories.HistoryRepository
import org.json.JSONArray
import org.json.JSONObject

class NavigationSimulationViewModel(application: Application) : AndroidViewModel(application) {

    private val historyRepository: HistoryRepository = HistoryRepository(
        AppDatabase.getDatabase(application).historyDao()
    )

    // --- State ---
    private val _startPoint = MutableStateFlow<String>("")
    val startPoint: StateFlow<String> = _startPoint.asStateFlow()

    private val _startLatLng = MutableStateFlow<LatLng?>(null)
    val startLatLng: StateFlow<LatLng?> = _startLatLng.asStateFlow()

    private val _endPoint = MutableStateFlow<String>("")
    val endPoint: StateFlow<String> = _endPoint.asStateFlow()

    private val _endLatLng = MutableStateFlow<LatLng?>(null)
    val endLatLng: StateFlow<LatLng?> = _endLatLng.asStateFlow()

    private val _isMultiRoute = MutableStateFlow(false)
    val isMultiRoute: StateFlow<Boolean> = _isMultiRoute.asStateFlow()

    private val _historyList = MutableStateFlow<List<RouteInfo>>(emptyList())
    val historyList: StateFlow<List<RouteInfo>> = _historyList.asStateFlow()

    private val _favOrders = MutableStateFlow<Map<Long, Int>>(emptyMap())
    val favOrders: StateFlow<Map<Long, Int>> = _favOrders.asStateFlow()

    private var _selectedHistoryId: Long? = null

    // Search Suggestions
    private val _searchResults = MutableStateFlow<List<Map<String, Any>>>(emptyList())
    val searchResults: StateFlow<List<Map<String, Any>>> = _searchResults.asStateFlow()
    
    // UI State
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    private val _isSimulating = MutableStateFlow(false)
    val isSimulating: StateFlow<Boolean> = _isSimulating.asStateFlow()
    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    // Services
    private val suggestionSearch: SuggestionSearch = SuggestionSearch.newInstance()
    private val routePlanSearch: RoutePlanSearch = RoutePlanSearch.newInstance()
    private val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(application)
    
    private val _runMode = MutableStateFlow("developer")
    val runMode: StateFlow<String> = _runMode.asStateFlow()

    private fun getServiceClass(mode: String) = when (mode) {
        "root" -> ServiceGoRoot::class.java
        "xposed" -> ServiceGoXposed::class.java
        else -> ServiceGoDeveloper::class.java
    }

    private fun getExtraName(mode: String, rootName: String, devName: String) =
        if (mode == "root" || mode == "xposed") rootName else devName
    
    private val _speed = MutableStateFlow(60.0)
    val speed: StateFlow<Double> = _speed.asStateFlow()

    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)
    val updateInfo: StateFlow<UpdateInfo?> = _updateInfo.asStateFlow()

    private val _candidateRoutes = MutableStateFlow<List<List<LatLng>>>(emptyList())
    val candidateRoutes: StateFlow<List<List<LatLng>>> = _candidateRoutes.asStateFlow()

    private val _currentLatLng = MutableStateFlow<LatLng?>(null)
    val currentLatLng: StateFlow<LatLng?> = _currentLatLng.asStateFlow()
    private var monitorJob: kotlinx.coroutines.Job? = null

    private val statusReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.kail.location.service.STATUS_CHANGED") {
                val isSim = intent.getBooleanExtra("is_simulating", false)
                val isPau = intent.getBooleanExtra("is_paused", false)
                _isSimulating.value = isSim
                _isPaused.value = isPau
                if (isSim) {
                    startLocationMonitor()
                } else {
                    stopLocationMonitor()
                }
            }
        }
    }


    companion object {
        private const val TAG = "NavSimVM"
        const val POI_NAME = "name"
        const val POI_ADDRESS = "address"
        const val POI_LATITUDE = "latitude"
        const val POI_LONGITUDE = "longitude"
    }

    init {
        loadFavOrders()
        viewModelScope.launch {
            historyRepository.recentRoutes.collect { entities ->
                _historyList.value = entities.map { entity ->
                    RouteInfo(
                        id = entity.id.toString(),
                        startName = entity.startName,
                        endName = entity.endName,
                        distance = "${entity.startLat},${entity.startLng}|${entity.endLat},${entity.endLng}",
                        isFavorite = entity.isFavorite
                    )
                }
            }
        }

        _runMode.value = sharedPreferences.getString("setting_run_mode", "developer") ?: "developer"
        initSearchListeners()

        // Register receiver
        val filter = android.content.IntentFilter("com.kail.location.service.STATUS_CHANGED")
        ContextCompat.registerReceiver(application, statusReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
    }

    fun selectHistoryRoute(route: RouteInfo) {
        _selectedHistoryId = route.id.toLongOrNull()
        try {
            val parts = route.distance.split("|")
            if (parts.size == 2) {
                val startParts = parts[0].split(",")
                val endParts = parts[1].split(",")
                if (startParts.size == 2 && endParts.size == 2) {
                    val startLat = startParts[0].toDoubleOrNull()
                    val startLng = startParts[1].toDoubleOrNull()
                    val endLat = endParts[0].toDoubleOrNull()
                    val endLng = endParts[1].toDoubleOrNull()
                    
                    if (startLat != null && startLng != null && endLat != null && endLng != null) {
                        selectStartPoint(route.startName, startLat, startLng)
                        selectEndPoint(route.endName, endLat, endLng)
                    }
                }
            }
        } catch (e: Exception) {
            KailLog.e(getApplication(), TAG, "selectHistoryRoute failed", e)
        }
    }

    fun checkUpdate(context: Context, isAuto: Boolean = false) {
        UpdateChecker.check(context) { info, error ->
            if (info != null) {
                _updateInfo.value = info
            } else {
                if (!isAuto) {
                    // Use MainExecutor to show toast
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

    fun dismissUpdateDialog() {
        _updateInfo.value = null
    }

    private fun initSearchListeners() {
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

        routePlanSearch.setOnGetRoutePlanResultListener(object : OnGetRoutePlanResultListener {
            override fun onGetWalkingRouteResult(result: WalkingRouteResult?) {
                _isLoading.value = false
                if (result?.error == SearchResult.ERRORNO.NO_ERROR && result.routeLines.isNotEmpty()) {
                    val route = result.routeLines[0]
                    val points = mutableListOf<LatLng>()
                    route.allStep.forEach { step ->
                        points.addAll(step.wayPoints)
                    }
                    _candidateRoutes.value = listOf(points)
                } else {
                    KailLog.e(getApplication(), "NavSimVM", "Route plan failed: ${result?.error}")
                }
            }

            override fun onGetTransitRouteResult(result: TransitRouteResult?) {}
            override fun onGetMassTransitRouteResult(result: MassTransitRouteResult?) {}
            override fun onGetDrivingRouteResult(result: DrivingRouteResult?) {
                _isLoading.value = false
                if (result?.error == SearchResult.ERRORNO.NO_ERROR && result.routeLines.isNotEmpty()) {
                    _candidateRoutes.value = result.routeLines.map { line ->
                        val points = mutableListOf<LatLng>()
                        line.allStep.forEach { step ->
                            points.addAll(step.wayPoints)
                        }
                        points
                    }
                } else {
                    KailLog.e(getApplication(), "NavSimVM", "Route plan failed: ${result?.error}")
                }
            }

            override fun onGetIndoorRouteResult(result: IndoorRouteResult?) {}
            override fun onGetBikingRouteResult(result: BikingRouteResult?) {
                _isLoading.value = false
                if (result?.error == SearchResult.ERRORNO.NO_ERROR && result.routeLines.isNotEmpty()) {
                    _candidateRoutes.value = result.routeLines.map { line ->
                        val points = mutableListOf<LatLng>()
                        line.allStep.forEach { step ->
                            points.addAll(step.wayPoints)
                        }
                        points
                    }
                } else {
                    KailLog.e(getApplication(), "NavSimVM", "Route plan failed: ${result?.error}")
                }
            }

            override fun onGetIntegralRouteResult(result: IntegralRouteResult?) {}
        })
    }
    
    fun setRunMode(mode: String) {
        _runMode.value = mode
        sharedPreferences.edit().putString("setting_run_mode", mode).apply()
    }
    
    fun setSpeed(value: Double) {
        _speed.value = value
    }

    fun search(query: String) {
        if (query.isEmpty()) {
            _searchResults.value = emptyList()
            return
        }
        suggestionSearch.requestSuggestion(
            SuggestionSearchOption()
                .city(getApplication<Application>().getString(R.string.vm_search_city))
                .keyword(query)
        )
    }

    fun selectStartPoint(name: String, lat: Double, lng: Double) {
        _startPoint.value = name
        _startLatLng.value = LatLng(lat, lng)
        _searchResults.value = emptyList()
    }

    fun selectEndPoint(name: String, lat: Double, lng: Double) {
        _endPoint.value = name
        _endLatLng.value = LatLng(lat, lng)
        _searchResults.value = emptyList()
    }

    fun setMultiRoute(enabled: Boolean) {
        _isMultiRoute.value = enabled
    }

    fun startSimulation() {
        val start = _startLatLng.value
        val end = _endLatLng.value
        if (start == null || end == null) return

        _isLoading.value = true
        val stNode = PlanNode.withLocation(start)
        val enNode = PlanNode.withLocation(end)

        routePlanSearch.drivingSearch(
            DrivingRoutePlanOption()
                .from(stNode)
                .to(enNode)
        )
    }

    private fun startSimulationService(points: List<LatLng>) {
        val app = getApplication<Application>()
        val currentRunMode = runMode.value
        val serviceClass = getServiceClass(currentRunMode)
        val intent = Intent(app, serviceClass)
        
        val pointsArray = DoubleArray(points.size * 2)
        for (i in points.indices) {
            pointsArray[i * 2] = points[i].longitude
            pointsArray[i * 2 + 1] = points[i].latitude
        }
        
        val extraRoutePoints = getExtraName(currentRunMode, ServiceGoRoot.EXTRA_ROUTE_POINTS, ServiceGoDeveloper.EXTRA_ROUTE_POINTS)
        val extraRouteLoop = getExtraName(currentRunMode, ServiceGoRoot.EXTRA_ROUTE_LOOP, ServiceGoDeveloper.EXTRA_ROUTE_LOOP)
        val extraJoystickEnabled = getExtraName(currentRunMode, ServiceGoRoot.EXTRA_JOYSTICK_ENABLED, ServiceGoDeveloper.EXTRA_JOYSTICK_ENABLED)
        val extraRouteSpeed = getExtraName(currentRunMode, ServiceGoRoot.EXTRA_ROUTE_SPEED, ServiceGoDeveloper.EXTRA_ROUTE_SPEED)
        val extraCoordType = getExtraName(currentRunMode, ServiceGoRoot.EXTRA_COORD_TYPE, ServiceGoDeveloper.EXTRA_COORD_TYPE)
        
        intent.putExtra(extraRoutePoints, pointsArray)
        intent.putExtra(extraRouteLoop, false)
        intent.putExtra(extraJoystickEnabled, true)
        intent.putExtra(extraRouteSpeed, _speed.value.toFloat())
        intent.putExtra(extraCoordType, "BD09")
        
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            ContextCompat.startForegroundService(app, intent)
        } else {
            GoUtils.DisplayToast(app, app.getString(R.string.vm_need_location_permission))
            return
        }
        _isSimulating.value = true
        _isPaused.value = false
        startLocationMonitor()
    }

    private fun addToHistory(start: String, end: String) {
        val selId = _selectedHistoryId
        _selectedHistoryId = null

        viewModelScope.launch {
            if (selId != null) {
                historyRepository.updateTimestamp(selId, System.currentTimeMillis())
            } else {
                val startLat = _startLatLng.value?.latitude ?: 0.0
                val startLng = _startLatLng.value?.longitude ?: 0.0
                val endLat = _endLatLng.value?.latitude ?: 0.0
                val endLng = _endLatLng.value?.longitude ?: 0.0
                historyRepository.addRoute(start, end, startLat, startLng, endLat, endLng)
            }
        }
    }
    
    fun toggleFavorite(id: Long) {
        viewModelScope.launch {
            val current = _historyList.value.find { it.id == id.toString() }?.isFavorite ?: return@launch
            val newFav = !current
            historyRepository.updateFavorite(id, newFav)
            val map = _favOrders.value.toMutableMap()
            if (newFav) {
                val maxOrder = map.values.maxOrNull() ?: 0
                map[id] = maxOrder + 1
            } else {
                map.remove(id)
            }
            _favOrders.value = map
        }
    }

    fun deleteHistory(id: Long) {
        viewModelScope.launch {
            historyRepository.deleteRoute(id)
        }
    }

    fun renameHistory(id: Long, newName: String) {
        viewModelScope.launch {
            historyRepository.updateName(id, newName, newName)
        }
    }

    fun getFavoriteOrder(id: Long): Int {
        return _favOrders.value[id] ?: 0
    }

    fun moveFavoriteUp(id: Long) {
        val map = _favOrders.value
        val sortedIds = _historyList.value.filter { it.isFavorite }
            .map { it.id.toLongOrNull() ?: 0L }
            .sortedBy { map[it] ?: 0 }
        val idx = sortedIds.indexOf(id)
        if (idx <= 0) return
        val newMap = map.toMutableMap()
        val curOrder = newMap[id] ?: idx
        val aboveOrder = newMap[sortedIds[idx - 1]] ?: (idx - 1)
        newMap[id] = aboveOrder
        newMap[sortedIds[idx - 1]] = curOrder
        _favOrders.value = newMap
    }

    fun moveFavoriteDown(id: Long) {
        val map = _favOrders.value
        val sortedIds = _historyList.value.filter { it.isFavorite }
            .map { it.id.toLongOrNull() ?: 0L }
            .sortedBy { map[it] ?: 0 }
        val idx = sortedIds.indexOf(id)
        if (idx < 0 || idx >= sortedIds.size - 1) return
        val newMap = map.toMutableMap()
        val curOrder = newMap[id] ?: idx
        val belowOrder = newMap[sortedIds[idx + 1]] ?: (idx + 1)
        newMap[id] = belowOrder
        newMap[sortedIds[idx + 1]] = curOrder
        _favOrders.value = newMap
    }

    fun setFavoriteOrder(ids: List<Long>) {
        val map = mutableMapOf<Long, Int>()
        ids.forEachIndexed { index, id -> map[id] = index }
        _favOrders.value = map
        saveFavOrders()
    }

    private fun loadFavOrders() {
        val json = sharedPreferences.getString("nav_fav_orders", null) ?: return
        try {
            val map = mutableMapOf<Long, Int>()
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                map[obj.getLong("id")] = obj.getInt("order")
            }
            _favOrders.value = map
        } catch (_: Exception) {}
    }

    private fun saveFavOrders() {
        try {
            val arr = JSONArray()
            _favOrders.value.forEach { (id, order) ->
                arr.put(JSONObject().apply {
                    put("id", id)
                    put("order", order)
                })
            }
            sharedPreferences.edit().putString("nav_fav_orders", arr.toString()).apply()
        } catch (_: Exception) {}
    }

    fun clearHistory() {
        viewModelScope.launch {
            historyRepository.clearHistory()
        }
    }

    fun chooseCandidate(index: Int) {
        val routes = _candidateRoutes.value
        if (index in routes.indices) {
            val app = getApplication<Application>()
            viewModelScope.launch {
                if (!UsageManager.canStartSimulation(app)) {
                    return@launch
                }
                if (!UsageManager.consumeSimulation(app)) {
                    return@launch
                }
                addToHistory(_startPoint.value, _endPoint.value)
                startSimulationService(routes[index])
                _candidateRoutes.value = emptyList()
            }
        }
    }

    fun pauseSimulation() {
        val app = getApplication<Application>()
        val serviceClass = getServiceClass(runMode.value)
        val controlAction = getExtraName(runMode.value, ServiceGoRoot.CONTROL_PAUSE, ServiceGoDeveloper.CONTROL_PAUSE)
        val intent = Intent(app, serviceClass)
        intent.putExtra("EXTRA_CONTROL_ACTION", controlAction)
        app.startService(intent)
        _isPaused.value = true
    }

    fun resumeSimulation() {
        val app = getApplication<Application>()
        val serviceClass = getServiceClass(runMode.value)
        val controlAction = getExtraName(runMode.value, ServiceGoRoot.CONTROL_RESUME, ServiceGoDeveloper.CONTROL_RESUME)
        val intent = Intent(app, serviceClass)
        intent.putExtra("EXTRA_CONTROL_ACTION", controlAction)
        app.startService(intent)
        _isPaused.value = false
    }

    fun stopSimulation() {
        val app = getApplication<Application>()
        val serviceClass = getServiceClass(runMode.value)
        app.stopService(Intent(app, serviceClass))
        _isSimulating.value = false
        _isPaused.value = false
        stopLocationMonitor()
    }

    fun seekProgress(ratio: Float) {
        val app = getApplication<Application>()
        val serviceClass = getServiceClass(runMode.value)
        val controlAction = getExtraName(runMode.value, ServiceGoRoot.CONTROL_SEEK, ServiceGoDeveloper.CONTROL_SEEK)
        val seekRatio = getExtraName(runMode.value, ServiceGoRoot.EXTRA_SEEK_RATIO, ServiceGoDeveloper.EXTRA_SEEK_RATIO)
        val intent = Intent(app, serviceClass)
        intent.putExtra("EXTRA_CONTROL_ACTION", controlAction)
        intent.putExtra(seekRatio, ratio)
        app.startService(intent)
    }

    private fun startLocationMonitor() {
        stopLocationMonitor()
        val points = _candidateRoutes.value.getOrNull(0) ?: return
        if (points.size < 2) return
        val speedMs = _speed.value / 3.6
        if (speedMs <= 0) return

        fun segLen(a: LatLng, b: LatLng): Double {
            val midLat = (a.latitude + b.latitude) / 2.0
            val dLat = b.latitude - a.latitude
            val dLng = b.longitude - a.longitude
            val mPerDegLat = com.kail.location.geo.GeoMath.metersPerDegLat(midLat)
            val mPerDegLng = com.kail.location.geo.GeoMath.metersPerDegLng(midLat)
            return kotlin.math.sqrt(dLat * mPerDegLat * dLat * mPerDegLat + dLng * mPerDegLng * dLng * mPerDegLng)
        }
        var totalDist = 0.0
        for (i in 0 until points.size - 1) {
            totalDist += segLen(points[i], points[i + 1])
        }
        if (totalDist <= 0) return
        val totalTimeMs = (totalDist / speedMs * 1000).toLong().coerceAtLeast(1)
        val startTime = android.os.SystemClock.elapsedRealtime()

        monitorJob = viewModelScope.launch {
            while (_isSimulating.value) {
                val elapsed = android.os.SystemClock.elapsedRealtime() - startTime
                val ratio = (elapsed.toFloat() / totalTimeMs).coerceIn(0f, 1f)
                var targetDist = totalDist * ratio
                var accumulated = 0.0
                var pos = points[0]
                for (i in 0 until points.size - 1) {
                    val segDist = segLen(points[i], points[i + 1])
                    if (accumulated + segDist >= targetDist) {
                        val f = ((targetDist - accumulated) / segDist).coerceIn(0.0, 1.0)
                        pos = LatLng(
                            points[i].latitude + (points[i + 1].latitude - points[i].latitude) * f,
                            points[i].longitude + (points[i + 1].longitude - points[i].longitude) * f
                        )
                        break
                    }
                    accumulated += segDist
                    pos = points[i + 1]
                }
                _currentLatLng.value = pos
                delay(1000)
            }
        }
    }

    private fun stopLocationMonitor() {
        monitorJob?.cancel()
        monitorJob = null
    }

    override fun onCleared() {
        super.onCleared()
        try {
            getApplication<Application>().unregisterReceiver(statusReceiver)
        } catch (e: Exception) {
            KailLog.e(getApplication(), TAG, "onCleared failed", e)
        }
        suggestionSearch.destroy()
        routePlanSearch.destroy()
    }
}
