package com.kail.location.viewmodels

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import com.baidu.mapapi.model.LatLng
import com.baidu.mapapi.search.sug.SuggestionSearch
import com.baidu.mapapi.search.sug.SuggestionSearchOption
import com.kail.location.R
import com.kail.location.repositories.DataBaseHistoryLocation
import com.kail.location.utils.GoUtils
import com.kail.location.utils.KailLog
import com.kail.location.utils.MapUtils
import com.kail.location.views.history.HistoryActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.*
import kotlin.math.cos
import kotlin.math.sin

/**
 * ViewModel for managing joystick-related states and business logic.
 */
class JoystickViewModel(application: Application) : AndroidViewModel(application) {

    private val sharedPreferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(application)
    private val suggestionSearch: SuggestionSearch = SuggestionSearch.newInstance()

    // --- States ---

    private val _windowType = MutableStateFlow(WindowType.JOYSTICK)
    val windowType: StateFlow<WindowType> = _windowType.asStateFlow()

    private val _currentLocation = MutableStateFlow(LatLng(0.0, 0.0))
    val currentLocation: StateFlow<LatLng> = _currentLocation.asStateFlow()

    private val _markLocation = MutableStateFlow<LatLng?>(null)
    val markLocation: StateFlow<LatLng?> = _markLocation.asStateFlow()



    private val _speed = MutableStateFlow(1.2)
    val speed: StateFlow<Double> = _speed.asStateFlow()

    private val _altitude = MutableStateFlow(55.0)
    val altitude: StateFlow<Double> = _altitude.asStateFlow()

    private val _searchResults = MutableStateFlow<List<Map<String, Any>>>(emptyList())
    val searchResults: StateFlow<List<Map<String, Any>>> = _searchResults.asStateFlow()

    private val _historyRecords = MutableStateFlow<List<Map<String, Any>>>(emptyList())
    val historyRecords: StateFlow<List<Map<String, Any>>> = _historyRecords.asStateFlow()

    private val _isHistoryPinned = MutableStateFlow(false)
    val isHistoryPinned: StateFlow<Boolean> = _isHistoryPinned.asStateFlow()

    // Route states
    private val _isRoutePaused = MutableStateFlow(false)
    val isRoutePaused: StateFlow<Boolean> = _isRoutePaused.asStateFlow()

    private val _routeProgress = MutableStateFlow(0f)
    val routeProgress: StateFlow<Float> = _routeProgress.asStateFlow()

    private val _routeTotalDistance = MutableStateFlow("0m")
    val routeTotalDistance: StateFlow<String> = _routeTotalDistance.asStateFlow()

    private val _routeSpeed = MutableStateFlow(0.0)
    val routeSpeed: StateFlow<Double> = _routeSpeed.asStateFlow()

    private val preferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
        if (key == SettingsViewModel.KEY_JOYSTICK_SPEED) {
            _speed.value = sharedPreferences.getString(key, "1.2")?.toDoubleOrNull() ?: 1.2
        }
    }

    // --- Initialization ---

    init {
        _speed.value = sharedPreferences.getString(SettingsViewModel.KEY_JOYSTICK_SPEED, "1.2")?.toDoubleOrNull() ?: 1.2
        sharedPreferences.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
        fetchHistoryRecords()
        viewModelScope.launch {
            DataBaseHistoryLocation.refreshSignal.collectLatest {
                fetchHistoryRecords()
            }
        }
        
        suggestionSearch.setOnGetSuggestionResultListener { result ->
            if (result?.allSuggestions == null) {
                _searchResults.value = emptyList()
            } else {
                val data = result.allSuggestions.mapNotNull { info ->
                    if (info.pt == null) null
                    else mapOf(
                        LocationPickerViewModel.POI_NAME to (info.key ?: ""),
                        LocationPickerViewModel.POI_ADDRESS to ((info.city ?: "") + " " + (info.district ?: "")),
                        LocationPickerViewModel.POI_LONGITUDE to info.pt.longitude.toString(),
                        LocationPickerViewModel.POI_LATITUDE to info.pt.latitude.toString()
                    )
                }
                _searchResults.value = data
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        suggestionSearch.destroy()
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
    }

    // --- Actions ---

    fun setWindowType(type: WindowType) {
        _windowType.value = type
        if (type == WindowType.HISTORY) {
            fetchHistoryRecords()
        }
    }

    fun setCurrentPosition(lng: Double, lat: Double, alt: Double) {
        val bd = MapUtils.wgs2bd(lng, lat)
        _currentLocation.value = LatLng(bd[1], bd[0])
        _altitude.value = alt
    }

    fun updateMarkLocation(ll: LatLng) {
        _markLocation.value = ll
    }

    fun setSpeed(speed: Double) {
        _speed.value = speed
    }

    fun search(query: String, city: String?) {
        if (query.isNotEmpty()) {
            suggestionSearch.requestSuggestion(
                SuggestionSearchOption().keyword(query).city(city ?: getApplication<Application>().getString(R.string.vm_search_city))
            )
        } else {
            _searchResults.value = emptyList()
        }
    }

    fun fetchHistoryRecords() {
        viewModelScope.launch(Dispatchers.IO) {
            val records = mutableListOf<Map<String, Any>>()
            try {
                val dbHelper = DataBaseHistoryLocation(getApplication())
                val db = dbHelper.readableDatabase

                val colInfo = try {
                    val p = db.rawQuery("PRAGMA table_info(${DataBaseHistoryLocation.TABLE_NAME})", null)
                    val cols = mutableListOf<String>()
                    while (p.moveToNext()) { cols.add(p.getString(1)) }
                    p.close(); cols
                } catch (_: Exception) { emptyList() }

                val hasFavCol = DataBaseHistoryLocation.DB_COLUMN_FAVORITE in colInfo
                val hasFavTimeCol = DataBaseHistoryLocation.DB_COLUMN_FAVORITE_TIME in colInfo

                val orderClauses = mutableListOf<String>()
                if (hasFavCol) orderClauses.add("${DataBaseHistoryLocation.DB_COLUMN_FAVORITE} DESC")
                if (hasFavCol && DataBaseHistoryLocation.DB_COLUMN_FAVORITE_ORDER in colInfo) orderClauses.add("${DataBaseHistoryLocation.DB_COLUMN_FAVORITE_ORDER} ASC")
                orderClauses.add("${DataBaseHistoryLocation.DB_COLUMN_TIMESTAMP} DESC")
                val cursor = db.rawQuery("SELECT * FROM ${DataBaseHistoryLocation.TABLE_NAME} WHERE ${DataBaseHistoryLocation.DB_COLUMN_ID} > 0 ORDER BY ${orderClauses.joinToString(",")}", null)

                while (cursor.moveToNext()) {
                    val item = mutableMapOf<String, Any>()
                    val id = cursor.getInt(0)
                    val location = cursor.getString(1)
                    val lng = cursor.getString(2)
                    val lat = cursor.getString(3)
                    val timestamp = cursor.getLong(4)
                    val bdLng = cursor.getString(5)
                    val bdLat = cursor.getString(6)
                    val hasFavOrderCol = DataBaseHistoryLocation.DB_COLUMN_FAVORITE_ORDER in colInfo
                    val isFav = if (hasFavCol) cursor.getInt(7) == 1 else false
                    val favTime = if (hasFavTimeCol) cursor.getLong(8) else 0L
                    val favOrder = if (hasFavOrderCol) cursor.getInt(9) else 0

                    val doubleLng = BigDecimal(lng).setScale(11, RoundingMode.HALF_UP).toDouble()
                    val doubleLat = BigDecimal(lat).setScale(11, RoundingMode.HALF_UP).toDouble()
                    val doubleBdLng = BigDecimal(bdLng).setScale(11, RoundingMode.HALF_UP).toDouble()
                    val doubleBdLat = BigDecimal(bdLat).setScale(11, RoundingMode.HALF_UP).toDouble()

                    item[HistoryActivity.KEY_ID] = id.toString()
                    item[HistoryActivity.KEY_LOCATION] = location
                    item[HistoryActivity.KEY_TIME] = GoUtils.timeStamp2Date(timestamp.toString())
                    item["rawTimestamp"] = timestamp
                    item[HistoryActivity.KEY_LNG_LAT_WGS] = String.format(getApplication<Application>().getString(R.string.history_vm_coord_wgs84), doubleLng, doubleLat)
                    item[HistoryActivity.KEY_LNG_LAT_CUSTOM] = String.format(getApplication<Application>().getString(R.string.history_vm_coord_bd09), doubleBdLng, doubleBdLat)
                    item["isFavorite"] = isFav
                    item["favoriteTime"] = favTime
                    item["favoriteOrder"] = favOrder
                    records.add(item)
                }
                cursor.close()
                db.close()
                _historyRecords.value = records
            } catch (e: Exception) {
                KailLog.e(getApplication(), "JOYSTICK", "Error fetching history: ${e.message}")
            }
        }
    }

    fun confirmTeleport(actionListener: ActionListener) {
        val mark = _markLocation.value
        if (mark != null) {
            val wgs = MapUtils.bd2wgs(mark.longitude, mark.latitude)
            actionListener.onPositionInfo(wgs[0], wgs[1], _altitude.value)
            saveLocationToHistory(wgs[0], wgs[1])
            _markLocation.value = null
        }
    }

    private fun saveLocationToHistory(lng: Double, lat: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bd = MapUtils.wgs2bd(lng, lat)
                val dbHelper = DataBaseHistoryLocation(getApplication())
                val db = dbHelper.writableDatabase
                DataBaseHistoryLocation.addHistoryLocation(
                    db, "", lng.toString(), lat.toString(),
                    (System.currentTimeMillis() / 1000).toString(),
                    bd[0].toString(), bd[1].toString()
                )
                db.close()
            } catch (_: Exception) {}
        }
    }

    fun selectHistoryRecord(record: Map<String, Any>, actionListener: ActionListener) {
        try {
            val wgs84LatLng = record[HistoryActivity.KEY_LNG_LAT_WGS].toString()
            val inner = wgs84LatLng.substring(wgs84LatLng.indexOf('[') + 1, wgs84LatLng.indexOf(']'))
            val parts = inner.split(" ".toRegex()).toTypedArray()
            val wgs84Longitude = parts[0].substring(parts[0].indexOf(':') + 1).toDouble()
            val wgs84Latitude = parts[1].substring(parts[1].indexOf(':') + 1).toDouble()
            
            actionListener.onPositionInfo(wgs84Longitude, wgs84Latitude, _altitude.value)
            if (!_isHistoryPinned.value) {
                setWindowType(WindowType.JOYSTICK)
            }
        } catch (e: Exception) {
            KailLog.e(getApplication(), "JOYSTICK", "Error selecting history: ${e.message}")
        }
    }

    fun moveFavorite(id: String, up: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val favs = _historyRecords.value.filter { (it["isFavorite"] as? Boolean) == true }
                    .sortedByDescending { (it["favoriteTime"] as? Long) ?: 0L }
                val idx = favs.indexOfFirst { it[HistoryActivity.KEY_ID] == id }
                if (idx < 0) return@launch
                val swapIdx = if (up) idx - 1 else idx + 1
                if (swapIdx < 0 || swapIdx >= favs.size) return@launch
                val dbHelper = DataBaseHistoryLocation(getApplication())
                val db = dbHelper.writableDatabase
                DataBaseHistoryLocation.updateFavoriteOrder(db, id.toIntOrNull() ?: return@launch, swapIdx)
                DataBaseHistoryLocation.updateFavoriteOrder(db, favs[swapIdx][HistoryActivity.KEY_ID].toString().toIntOrNull() ?: return@launch, idx)
                db.close()
                DataBaseHistoryLocation.notifyChanged()
            } catch (e: Exception) {
                KailLog.e(getApplication(), "JOYSTICK", "Error moving favorite: ${e.message}")
            }
        }
    }

    fun setFavoriteOrder(ids: List<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dbHelper = DataBaseHistoryLocation(getApplication())
                val db = dbHelper.writableDatabase
                ids.forEachIndexed { index, id ->
                    DataBaseHistoryLocation.updateFavoriteOrder(db, id.toIntOrNull() ?: return@launch, index)
                }
                db.close()
                DataBaseHistoryLocation.notifyChanged()
            } catch (e: Exception) {
                KailLog.e(getApplication(), "JOYSTICK", "Error setting favorite order: ${e.message}")
            }
        }
    }

    fun toggleHistoryPin() {
        _isHistoryPinned.value = !_isHistoryPinned.value
    }

    fun toggleHistoryFavorite(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val record = _historyRecords.value.find { it[HistoryActivity.KEY_ID] == id } ?: return@launch
                val current = (record["isFavorite"] as? Boolean) ?: false
                val dbHelper = DataBaseHistoryLocation(getApplication())
                val db = dbHelper.writableDatabase
                DataBaseHistoryLocation.updateFavorite(db, id.toIntOrNull() ?: return@launch, !current)
                db.close()
                DataBaseHistoryLocation.notifyChanged()
                fetchHistoryRecords()
            } catch (_: Exception) {}
        }
    }

    fun renameHistoryRecord(id: String, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dbHelper = DataBaseHistoryLocation(getApplication())
                val db = dbHelper.writableDatabase
                DataBaseHistoryLocation.updateHistoryLocation(db, id, newName)
                db.close()
                DataBaseHistoryLocation.notifyChanged()
                fetchHistoryRecords()
            } catch (_: Exception) {}
        }
    }

    fun deleteHistoryRecord(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dbHelper = DataBaseHistoryLocation(getApplication())
                val db = dbHelper.writableDatabase
                db.delete(DataBaseHistoryLocation.TABLE_NAME, "${DataBaseHistoryLocation.DB_COLUMN_ID}=?", arrayOf(id))
                db.close()
                DataBaseHistoryLocation.notifyChanged()
                fetchHistoryRecords()
            } catch (_: Exception) {}
        }
    }

    fun updateRouteStatus(progress: Float, distance: String, currentLatLng: LatLng?) {
        _routeProgress.value = progress
        _routeTotalDistance.value = distance
        currentLatLng?.let { _currentLocation.value = it }
    }

    fun setRoutePauseState(isPaused: Boolean) {
        _isRoutePaused.value = isPaused
    }

    fun setRouteSpeed(speed: Double) {
        _routeSpeed.value = speed
    }

    // --- Helper Methods ---

    enum class WindowType {
        JOYSTICK, MAP, HISTORY, ROUTE_CONTROL
    }

    /**
     * Interface for actions triggered by the joystick UI that need to be handled by the service.
     */
    interface ActionListener {
        fun onMoveInfo(speed: Double, disLng: Double, disLat: Double, angle: Double)
        fun onPositionInfo(lng: Double, lat: Double, alt: Double)
        fun onRouteControl(action: String)
        fun onRouteSeek(progress: Float)
        fun onRouteSpeedChange(speed: Double)
    }
}
