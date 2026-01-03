package com.kail.location.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.baidu.mapapi.model.LatLng
import com.kail.location.views.main.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _isMocking = MutableStateFlow(false)
    val isMocking: StateFlow<Boolean> = _isMocking.asStateFlow()

    private val _targetLocation = MutableStateFlow(LatLng(36.547743718042415, 117.07018449827267))
    val targetLocation: StateFlow<LatLng> = _targetLocation.asStateFlow()

    private val _mapType = MutableStateFlow(1) // BaiduMap.MAP_TYPE_NORMAL = 1
    val mapType: StateFlow<Int> = _mapType.asStateFlow()

    private val _currentCity = MutableStateFlow<String?>(null)
    val currentCity: StateFlow<String?> = _currentCity.asStateFlow()

    // Data classes
    data class PoiInfo(
        val name: String,
        val address: String,
        val latitude: Double,
        val longitude: Double
    )

    data class UpdateInfo(
        val version: String,
        val content: String,
        val downloadUrl: String,
        val filename: String
    )

    private val _selectedPoi = MutableStateFlow<PoiInfo?>(null)
    val selectedPoi: StateFlow<PoiInfo?> = _selectedPoi.asStateFlow()

    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)
    val updateInfo: StateFlow<UpdateInfo?> = _updateInfo.asStateFlow()

    // Search
    private val _searchResults = MutableStateFlow<List<Map<String, Any>>>(emptyList())
    val searchResults: StateFlow<List<Map<String, Any>>> = _searchResults.asStateFlow()

    private val suggestionSearch = com.baidu.mapapi.search.sug.SuggestionSearch.newInstance()

    companion object {
        const val POI_NAME = "POI_NAME"
        const val POI_ADDRESS = "POI_ADDRESS"
        const val POI_LONGITUDE = "POI_LONGITUDE"
        const val POI_LATITUDE = "POI_LATITUDE"
    }

    init {
        suggestionSearch.setOnGetSuggestionResultListener { suggestionResult ->
            if (suggestionResult?.allSuggestions == null) {
                _searchResults.value = emptyList()
            } else {
                val data: MutableList<Map<String, Any>> = java.util.ArrayList()
                for (info in suggestionResult.allSuggestions) {
                    if (info.pt == null) continue
                    val poiItem: MutableMap<String, Any> = java.util.HashMap()
                    poiItem[POI_NAME] = info.key
                    poiItem[POI_ADDRESS] = (info.city ?: "") + " " + (info.district ?: "")
                    poiItem[POI_LONGITUDE] = "" + info.pt.longitude
                    poiItem[POI_LATITUDE] = "" + info.pt.latitude
                    data.add(poiItem)
                }
                _searchResults.value = data
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        suggestionSearch.destroy()
    }

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

    fun clearSearchResults() {
        _searchResults.value = emptyList()
    }

    // UI Events
    fun setMockingState(isMocking: Boolean) {
        _isMocking.value = isMocking
    }

    fun setTargetLocation(latLng: LatLng) {
        _targetLocation.value = latLng
    }

    fun setMapType(type: Int) {
        _mapType.value = type
    }

    fun setCurrentCity(city: String?) {
        _currentCity.value = city
    }

    fun selectPoi(poi: PoiInfo?) {
        _selectedPoi.value = poi
    }

    fun setUpdateInfo(info: UpdateInfo?) {
        _updateInfo.value = info
    }
}
