package com.kail.location.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import com.baidu.mapapi.model.LatLng
import com.kail.location.views.main.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 主页面的 ViewModel。
 * 负责地图状态、POI 搜索、位置选择及应用更新相关逻辑。
 *
 * @property application 应用上下文。
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _isMocking = MutableStateFlow(false)
    /**
     * 是否正在进行模拟的状态流。
     */
    val isMocking: StateFlow<Boolean> = _isMocking.asStateFlow()

    private val _targetLocation = MutableStateFlow(LatLng(36.547743718042415, 117.07018449827267))
    /**
     * 模拟目标位置的状态流。
     */
    val targetLocation: StateFlow<LatLng> = _targetLocation.asStateFlow()

    private val _mapType = MutableStateFlow(1) // BaiduMap.MAP_TYPE_NORMAL = 1
    /**
     * 当前地图类型（普通、卫星等）的状态流。
     */
    val mapType: StateFlow<Int> = _mapType.asStateFlow()

    private val _currentCity = MutableStateFlow<String?>(null)
    /**
     * 当前城市名的状态流。
     */
    val currentCity: StateFlow<String?> = _currentCity.asStateFlow()

    /**
     * POI（兴趣点）信息的数据结构。
     */
    data class PoiInfo(
        val name: String,
        val address: String,
        val latitude: Double,
        val longitude: Double
    )

    /**
     * 应用更新信息的数据结构。
     */
    data class UpdateInfo(
        val version: String,
        val content: String,
        val downloadUrl: String,
        val filename: String
    )

    private val _selectedPoi = MutableStateFlow<PoiInfo?>(null)
    /**
     * 当前选中的 POI 状态流。
     */
    val selectedPoi: StateFlow<PoiInfo?> = _selectedPoi.asStateFlow()

    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)
    /**
     * 可用的更新信息状态流。
     */
    val updateInfo: StateFlow<UpdateInfo?> = _updateInfo.asStateFlow()

    // Search
    private val _searchResults = MutableStateFlow<List<Map<String, Any>>>(emptyList())
    /**
     * 搜索结果列表的状态流。
     */
    val searchResults: StateFlow<List<Map<String, Any>>> = _searchResults.asStateFlow()

    private val suggestionSearch = com.baidu.mapapi.search.sug.SuggestionSearch.newInstance()

    companion object {
        const val POI_NAME = "POI_NAME"
        const val POI_ADDRESS = "POI_ADDRESS"
        const val POI_LONGITUDE = "POI_LONGITUDE"
        const val POI_LATITUDE = "POI_LATITUDE"
        const val KEY_RUN_MODE = "setting_run_mode"
        const val RUN_MODE_ROOT = "root"
        const val RUN_MODE_NOROOT = "noroot"
    }

    private val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(application)

    private val _runMode = MutableStateFlow(RUN_MODE_NOROOT)
    val runMode: StateFlow<String> = _runMode.asStateFlow()

    init {
        _runMode.value = sharedPreferences.getString(KEY_RUN_MODE, RUN_MODE_NOROOT) ?: RUN_MODE_NOROOT
        suggestionSearch.setOnGetSuggestionResultListener { suggestionResult ->
            if (suggestionResult == null || suggestionResult.allSuggestions == null) {
                _searchResults.value = emptyList()
                return@setOnGetSuggestionResultListener
            }
            val results = suggestionResult.allSuggestions.mapNotNull { info ->
                if (info.pt == null) return@mapNotNull null
                mapOf(
                    POI_NAME to (info.key ?: ""),
                    POI_ADDRESS to ("${info.city}${info.district}"),
                    POI_LONGITUDE to info.pt.longitude,
                    POI_LATITUDE to info.pt.latitude
                )
            }
            _searchResults.value = results
        }
    }

    override fun onCleared() {
        super.onCleared()
        suggestionSearch.destroy()
    }

    /**
     * 根据关键字与城市执行 POI 搜索。
     *
     * @param keyword 搜索关键字。
     * @param city 搜索的城市。
     */
    fun search(keyword: String, city: String?) {
        if (keyword.isEmpty()) {
            _searchResults.value = emptyList()
            return
        }
        try {
            suggestionSearch.requestSuggestion(
                com.baidu.mapapi.search.sug.SuggestionSearchOption()
                    .keyword(keyword)
                    .city(city ?: "")
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 清空当前搜索结果。
     */
    fun clearSearchResults() {
        _searchResults.value = emptyList()
    }

    // UI Events
    /**
     * 设置模拟状态。
     *
     * @param isMocking 为 true 表示正在模拟。
     */
    fun setMockingState(isMocking: Boolean) {
        _isMocking.value = isMocking
    }

    /**
     * 设置目标位置。
     *
     * @param latLng 目标经纬度。
     */
    fun setTargetLocation(latLng: LatLng) {
        _targetLocation.value = latLng
    }

    /**
     * 设置地图类型。
     *
     * @param type 百度地图类型常量。
     */
    fun setMapType(type: Int) {
        _mapType.value = type
    }

    /**
     * 设置当前城市。
     *
     * @param city 城市名称。
     */
    fun setCurrentCity(city: String?) {
        _currentCity.value = city
    }

    /**
     * 选择一个 POI。
     *
     * @param poi 选中的 POI。
     */
    fun selectPoi(poi: PoiInfo?) {
        _selectedPoi.value = poi
    }

    /**
     * 设置更新信息。
     *
     * @param info 更新信息。
     */
    fun setUpdateInfo(info: UpdateInfo?) {
        _updateInfo.value = info
    }

    fun setRunMode(mode: String) {
        _runMode.value = mode
        sharedPreferences.edit().putString(KEY_RUN_MODE, mode).apply()
    }
}
