package com.kail.location.viewmodels

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.preference.PreferenceManager
import com.kail.location.service.Root.RootDeployer
import com.kail.location.utils.GoUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 设置页面的 ViewModel。
 * 负责读取与写入用户偏好设置。
 *
 * @property application 应用上下文。
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(application)

    // Keys
    companion object {
        // 移动相关
        const val KEY_JOYSTICK_TYPE = "setting_joystick_type"
        const val KEY_JOYSTICK_SPEED = "setting_joystick_speed"
        const val KEY_WALK_SPEED = "setting_walk"
        const val KEY_RUN_SPEED = "setting_run"
        const val KEY_BIKE_SPEED = "setting_bike"

        // 位置模拟参数
        const val KEY_ALTITUDE = "setting_altitude"
        const val KEY_MOCK_SPEED = "setting_mock_speed"
        const val KEY_ACCURACY = "setting_accuracy"
        const val KEY_MIN_SATELLITES = "setting_min_satellites"
        const val KEY_REPORT_INTERVAL = "setting_report_interval"

        // 位置偏移
        const val KEY_LAT_OFFSET = "setting_lat_max_offset"
        const val KEY_LON_OFFSET = "setting_lon_max_offset"
        const val KEY_RANDOM_OFFSET = "setting_random_offset"

        // 开关类：功能控制
        const val KEY_GPS_SATELLITE_SIM = "setting_gps_satellite_sim"
        const val KEY_ENABLE_AGPS = "setting_enable_agps"
        const val KEY_ENABLE_NMEA = "setting_enable_nmea"
        const val KEY_ENABLE_MOCK_WIFI = "setting_enable_mock_wifi"

        // 开关类：拦截控制（正向偏好，保存时以"允许"为 true）
        const val KEY_ALLOW_GET_CURRENT_LOCATION = "setting_allow_get_current_location"
        const val KEY_ALLOW_REGISTER_LISTENER = "setting_allow_register_listener"
        const val KEY_ALLOW_GEOFENCE = "setting_allow_geofence"
        const val KEY_ALLOW_GET_FROM_LOCATION = "setting_allow_get_from_location"

        // 开关类：降级/禁用
        const val KEY_DISABLE_FUSED_LOCATION = "setting_disable_fused_location"
        const val KEY_DOWNGRADE_TO_CDMA = "setting_downgrade_to_cdma"
        const val KEY_DISABLE_WIFI_SCAN = "setting_disable_wifi_scan"
        const val KEY_LOOP_BROADCAST = "setting_loop_broadcast"
        const val KEY_HIDE_MOCK = "setting_hide_mock"
        const val KEY_NATURAL_JITTER = "setting_natural_jitter"

        // 日志/其他
        const val KEY_LOG_ENABLED = "setting_log_enabled"
        const val KEY_DEBUG_LOG_ENABLED = "setting_debug_log_enabled"
        const val KEY_HISTORY_EXPIRATION = "setting_history_expiration"
        const val KEY_BAIDU_MAP_KEY = "setting_baidu_map_key"
        const val KEY_MAP_ZOOM = "setting_map_zoom"
        const val KEY_FLOATING_WINDOW_WIDTH = "setting_floating_window_width"
        const val KEY_FLOATING_WINDOW_HEIGHT = "setting_floating_window_height"
        const val KEY_SIM_SCHEME = "setting_sim_scheme"
        const val KEY_STEP_SIM_ENABLED = "setting_step_sim_enabled"

        // OpenCellID API key
        const val KEY_OPENCELLID_API_KEY = "setting_opencellid_api_key"

        // Root injection mode
        const val KEY_GLOBAL_MODE = "kail_global_mode"
        const val KEY_TARGET_PACKAGES = "kail_target_packages"

        // 注入期间临时将 SELinux 切到宽容模式。开关关闭时 RootDeployer 不会
        // 调用 setenforce 0，也不会调用 setenforce 1（保持系统原本的 SELinux 状态）。
        const val KEY_SELINUX_PERMISSIVE = "setting_selinux_permissive"
    }

    // 已有 StateFlow（保留）
    private val _joystickType = MutableStateFlow(prefs.getString(KEY_JOYSTICK_TYPE, "0") ?: "0")
    val joystickType: StateFlow<String> = _joystickType.asStateFlow()

    private val _joystickSpeed = MutableStateFlow(prefs.getString(KEY_JOYSTICK_SPEED, "1.2") ?: "1.2")
    val joystickSpeed: StateFlow<String> = _joystickSpeed.asStateFlow()

    private val _baiduMapKey = MutableStateFlow(prefs.getString(KEY_BAIDU_MAP_KEY, "") ?: "")
    val baiduMapKey: StateFlow<String> = _baiduMapKey.asStateFlow()

    private val _opencellidApiKey = MutableStateFlow(prefs.getString(KEY_OPENCELLID_API_KEY, "") ?: "")
    val opencellidApiKey: StateFlow<String> = _opencellidApiKey.asStateFlow()

    private val _walkSpeed = MutableStateFlow(prefs.getString(KEY_WALK_SPEED, "1.2") ?: "1.2")
    val walkSpeed: StateFlow<String> = _walkSpeed.asStateFlow()

    private val _runSpeed = MutableStateFlow(prefs.getString(KEY_RUN_SPEED, "3.6") ?: "3.6")
    val runSpeed: StateFlow<String> = _runSpeed.asStateFlow()

    private val _bikeSpeed = MutableStateFlow(prefs.getString(KEY_BIKE_SPEED, "10.0") ?: "10.0")
    val bikeSpeed: StateFlow<String> = _bikeSpeed.asStateFlow()

    private val _altitude = MutableStateFlow(prefs.getString(KEY_ALTITUDE, "80.0") ?: "80.0")
    val altitude: StateFlow<String> = _altitude.asStateFlow()

    private val _latOffset = MutableStateFlow(prefs.getString(KEY_LAT_OFFSET, "10.0") ?: "10.0")
    val latOffset: StateFlow<String> = _latOffset.asStateFlow()

    private val _lonOffset = MutableStateFlow(prefs.getString(KEY_LON_OFFSET, "10.0") ?: "10.0")
    val lonOffset: StateFlow<String> = _lonOffset.asStateFlow()

    private val _randomOffset = MutableStateFlow(prefs.getBoolean(KEY_RANDOM_OFFSET, false))
    val randomOffset: StateFlow<Boolean> = _randomOffset.asStateFlow()

    private val _logEnabled = MutableStateFlow(prefs.getBoolean(KEY_LOG_ENABLED, false))
    val logEnabled: StateFlow<Boolean> = _logEnabled.asStateFlow()

    private val _debugLogEnabled = MutableStateFlow(prefs.getBoolean(KEY_DEBUG_LOG_ENABLED, false))
    val debugLogEnabled: StateFlow<Boolean> = _debugLogEnabled.asStateFlow()

    private val _historyExpiration = MutableStateFlow(prefs.getString(KEY_HISTORY_EXPIRATION, "7.0") ?: "7.0")
    val historyExpiration: StateFlow<String> = _historyExpiration.asStateFlow()

    private val _mapZoom = MutableStateFlow(prefs.getString(KEY_MAP_ZOOM, "17") ?: "17")
    val mapZoom: StateFlow<String> = _mapZoom.asStateFlow()

    private val _gpsSatelliteSim = MutableStateFlow(prefs.getBoolean(KEY_GPS_SATELLITE_SIM, true))
    val gpsSatelliteSim: StateFlow<Boolean> = _gpsSatelliteSim.asStateFlow()

    // 新增 StateFlow
    private val _mockSpeed = MutableStateFlow(prefs.getString(KEY_MOCK_SPEED, "3.05") ?: "3.05")
    val mockSpeed: StateFlow<String> = _mockSpeed.asStateFlow()

    private val _accuracy = MutableStateFlow(prefs.getString(KEY_ACCURACY, "2.5") ?: "2.5")
    val accuracy: StateFlow<String> = _accuracy.asStateFlow()

    private val _minSatellites = MutableStateFlow(prefs.getString(KEY_MIN_SATELLITES, "12") ?: "12")
    val minSatellites: StateFlow<String> = _minSatellites.asStateFlow()

    private val _reportInterval = MutableStateFlow(prefs.getString(KEY_REPORT_INTERVAL, "200") ?: "200")
    val reportInterval: StateFlow<String> = _reportInterval.asStateFlow()

    private val _enableAGPS = MutableStateFlow(prefs.getBoolean(KEY_ENABLE_AGPS, false))
    val enableAGPS: StateFlow<Boolean> = _enableAGPS.asStateFlow()

    private val _enableNMEA = MutableStateFlow(prefs.getBoolean(KEY_ENABLE_NMEA, false))
    val enableNMEA: StateFlow<Boolean> = _enableNMEA.asStateFlow()

    private val _enableMockWifi = MutableStateFlow(prefs.getBoolean(KEY_ENABLE_MOCK_WIFI, false))
    val enableMockWifi: StateFlow<Boolean> = _enableMockWifi.asStateFlow()

    private val _allowGetCurrentLocation = MutableStateFlow(prefs.getBoolean(KEY_ALLOW_GET_CURRENT_LOCATION, true))
    val allowGetCurrentLocation: StateFlow<Boolean> = _allowGetCurrentLocation.asStateFlow()

    private val _allowRegisterListener = MutableStateFlow(prefs.getBoolean(KEY_ALLOW_REGISTER_LISTENER, true))
    val allowRegisterListener: StateFlow<Boolean> = _allowRegisterListener.asStateFlow()

    private val _allowGeofence = MutableStateFlow(prefs.getBoolean(KEY_ALLOW_GEOFENCE, true))
    val allowGeofence: StateFlow<Boolean> = _allowGeofence.asStateFlow()

    private val _allowGetFromLocation = MutableStateFlow(prefs.getBoolean(KEY_ALLOW_GET_FROM_LOCATION, true))
    val allowGetFromLocation: StateFlow<Boolean> = _allowGetFromLocation.asStateFlow()

    private val _disableFusedLocation = MutableStateFlow(prefs.getBoolean(KEY_DISABLE_FUSED_LOCATION, true))
    val disableFusedLocation: StateFlow<Boolean> = _disableFusedLocation.asStateFlow()

    private val _downgradeToCdma = MutableStateFlow(prefs.getBoolean(KEY_DOWNGRADE_TO_CDMA, true))
    val downgradeToCdma: StateFlow<Boolean> = _downgradeToCdma.asStateFlow()

    private val _disableWifiScan = MutableStateFlow(prefs.getBoolean(KEY_DISABLE_WIFI_SCAN, true))
    val disableWifiScan: StateFlow<Boolean> = _disableWifiScan.asStateFlow()

    private val _loopBroadcast = MutableStateFlow(prefs.getBoolean(KEY_LOOP_BROADCAST, false))
    val loopBroadcast: StateFlow<Boolean> = _loopBroadcast.asStateFlow()

    private val _naturalJitter = MutableStateFlow(prefs.getBoolean(KEY_NATURAL_JITTER, false))
    val naturalJitter: StateFlow<Boolean> = _naturalJitter.asStateFlow()

    private val _hideMock = MutableStateFlow(prefs.getBoolean(KEY_HIDE_MOCK, true))
    val hideMock: StateFlow<Boolean> = _hideMock.asStateFlow()

    private val _simScheme = MutableStateFlow(prefs.getString(KEY_SIM_SCHEME, "0") ?: "0")
    val simScheme: StateFlow<String> = _simScheme.asStateFlow()

    private val _stepSimEnabled = MutableStateFlow(prefs.getBoolean(KEY_STEP_SIM_ENABLED, true))
    val stepSimEnabled: StateFlow<Boolean> = _stepSimEnabled.asStateFlow()

    private val _globalMode = MutableStateFlow(prefs.getBoolean(KEY_GLOBAL_MODE, true))
    val globalMode: StateFlow<Boolean> = _globalMode.asStateFlow()

    private val _targetPackages = MutableStateFlow(prefs.getString(KEY_TARGET_PACKAGES, "com.autonavi.minimap") ?: "com.autonavi.minimap")
    val targetPackages: StateFlow<String> = _targetPackages.asStateFlow()

    private val _selinuxPermissiveEnabled = MutableStateFlow(prefs.getBoolean(KEY_SELINUX_PERMISSIVE, false))
    val selinuxPermissiveEnabled: StateFlow<Boolean> = _selinuxPermissiveEnabled.asStateFlow()

    private val _floatingWindowWidth = MutableStateFlow(prefs.getString(KEY_FLOATING_WINDOW_WIDTH, "300") ?: "300")
    val floatingWindowWidth: StateFlow<String> = _floatingWindowWidth.asStateFlow()
    private val _floatingWindowHeight = MutableStateFlow(prefs.getString(KEY_FLOATING_WINDOW_HEIGHT, "500") ?: "500")
    val floatingWindowHeight: StateFlow<String> = _floatingWindowHeight.asStateFlow()

    /** 应用版本号（字符串）。 */
    val appVersion: String = GoUtils.getVersionName(application)

    private val preferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
        when (key) {
            KEY_JOYSTICK_TYPE -> _joystickType.value = sharedPreferences.getString(key, "0") ?: "0"
            KEY_JOYSTICK_SPEED -> _joystickSpeed.value = sharedPreferences.getString(key, "1.2") ?: "1.2"
            KEY_BAIDU_MAP_KEY -> _baiduMapKey.value = sharedPreferences.getString(key, "") ?: ""
            KEY_OPENCELLID_API_KEY -> _opencellidApiKey.value = sharedPreferences.getString(key, "") ?: ""
            KEY_WALK_SPEED -> _walkSpeed.value = sharedPreferences.getString(key, "1.2") ?: "1.2"
            KEY_RUN_SPEED -> _runSpeed.value = sharedPreferences.getString(key, "3.6") ?: "3.6"
            KEY_BIKE_SPEED -> _bikeSpeed.value = sharedPreferences.getString(key, "10.0") ?: "10.0"
            KEY_ALTITUDE -> _altitude.value = sharedPreferences.getString(key, "80.0") ?: "80.0"
            KEY_LAT_OFFSET -> _latOffset.value = sharedPreferences.getString(key, "10.0") ?: "10.0"
            KEY_LON_OFFSET -> _lonOffset.value = sharedPreferences.getString(key, "10.0") ?: "10.0"
            KEY_RANDOM_OFFSET -> _randomOffset.value = sharedPreferences.getBoolean(key, false)
            KEY_LOG_ENABLED -> _logEnabled.value = sharedPreferences.getBoolean(key, false)
            KEY_DEBUG_LOG_ENABLED -> _debugLogEnabled.value = sharedPreferences.getBoolean(key, false)
            KEY_HISTORY_EXPIRATION -> _historyExpiration.value = sharedPreferences.getString(key, "7.0") ?: "7.0"
            KEY_MAP_ZOOM -> _mapZoom.value = sharedPreferences.getString(key, "17") ?: "17"
            KEY_GPS_SATELLITE_SIM -> _gpsSatelliteSim.value = sharedPreferences.getBoolean(key, true)
            // 新增
            KEY_MOCK_SPEED -> _mockSpeed.value = sharedPreferences.getString(key, "3.05") ?: "3.05"
            KEY_ACCURACY -> _accuracy.value = sharedPreferences.getString(key, "2.5") ?: "2.5"
            KEY_MIN_SATELLITES -> _minSatellites.value = sharedPreferences.getString(key, "12") ?: "12"
            KEY_REPORT_INTERVAL -> _reportInterval.value = sharedPreferences.getString(key, "200") ?: "200"
            KEY_ENABLE_AGPS -> _enableAGPS.value = sharedPreferences.getBoolean(key, false)
            KEY_ENABLE_NMEA -> _enableNMEA.value = sharedPreferences.getBoolean(key, false)
            KEY_ENABLE_MOCK_WIFI -> _enableMockWifi.value = sharedPreferences.getBoolean(key, false)
            KEY_ALLOW_GET_CURRENT_LOCATION -> _allowGetCurrentLocation.value = sharedPreferences.getBoolean(key, true)
            KEY_ALLOW_REGISTER_LISTENER -> _allowRegisterListener.value = sharedPreferences.getBoolean(key, true)
            KEY_ALLOW_GEOFENCE -> _allowGeofence.value = sharedPreferences.getBoolean(key, true)
            KEY_ALLOW_GET_FROM_LOCATION -> _allowGetFromLocation.value = sharedPreferences.getBoolean(key, true)
            KEY_DISABLE_FUSED_LOCATION -> _disableFusedLocation.value = sharedPreferences.getBoolean(key, true)
            KEY_DOWNGRADE_TO_CDMA -> _downgradeToCdma.value = sharedPreferences.getBoolean(key, true)
            KEY_DISABLE_WIFI_SCAN -> _disableWifiScan.value = sharedPreferences.getBoolean(key, true)
            KEY_LOOP_BROADCAST -> _loopBroadcast.value = sharedPreferences.getBoolean(key, false)
            KEY_NATURAL_JITTER -> _naturalJitter.value = sharedPreferences.getBoolean(key, false)
            KEY_HIDE_MOCK -> _hideMock.value = sharedPreferences.getBoolean(key, true)
            KEY_SIM_SCHEME -> _simScheme.value = sharedPreferences.getString(key, "0") ?: "0"
            KEY_STEP_SIM_ENABLED -> _stepSimEnabled.value = sharedPreferences.getBoolean(key, true)
            KEY_GLOBAL_MODE -> _globalMode.value = sharedPreferences.getBoolean(key, true)
            KEY_TARGET_PACKAGES -> _targetPackages.value = sharedPreferences.getString(key, "com.autonavi.minimap") ?: "com.autonavi.minimap"
            KEY_SELINUX_PERMISSIVE -> _selinuxPermissiveEnabled.value = sharedPreferences.getBoolean(key, false)
            KEY_FLOATING_WINDOW_WIDTH -> _floatingWindowWidth.value = sharedPreferences.getString(key, "300") ?: "300"
            KEY_FLOATING_WINDOW_HEIGHT -> _floatingWindowHeight.value = sharedPreferences.getString(key, "500") ?: "500"
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
    }

    /**
     * 更新字符串类型的偏好设置。
     */
    fun updateStringPreference(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    /**
     * 更新布尔类型的偏好设置。
     */
    fun updateBooleanPreference(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
        if (key == KEY_LOG_ENABLED || key == KEY_DEBUG_LOG_ENABLED) {
            Thread({ RootDeployer.syncInjectLogMarkers(getApplication()) }, "KailLogMarkerSync").start()
        }
    }

    /**
     * 读取当前全部 Root 相关配置，打包成 Bundle。
     */
    fun buildRootConfigBundle(): android.os.Bundle {
        return android.os.Bundle().apply {
            putBoolean("enableMockGnss", _gpsSatelliteSim.value)
            putBoolean("enableMockWifi", _enableMockWifi.value)
            putBoolean("disableGetCurrentLocation", !_allowGetCurrentLocation.value)
            putBoolean("disableRegisterLocationListener", !_allowRegisterListener.value)
            putBoolean("disableFusedLocation", _disableFusedLocation.value)
            putBoolean("disableNetworkLocation", true) // 默认保持 true
            putBoolean("disableRequestGeofence", !_allowGeofence.value)
            putBoolean("disableGetFromLocation", !_allowGetFromLocation.value)
            putBoolean("enableAGPS", _enableAGPS.value)
            putBoolean("enableNMEA", _enableNMEA.value)
            putBoolean("hideMock", _hideMock.value)
            putBoolean("hookWifi", _disableWifiScan.value)
            putBoolean("needDowngradeToCdma", _downgradeToCdma.value)
            putBoolean("loopBroadcastLocation", _loopBroadcast.value)
            putBoolean("enableNaturalJitter", _naturalJitter.value)
            putInt("minSatellites", _minSatellites.value.toIntOrNull() ?: 12)
            putFloat("accuracy", _accuracy.value.toFloatOrNull() ?: 2.5f)
            putInt("reportIntervalMs", _reportInterval.value.toIntOrNull() ?: 200)
            putBoolean("enableFileLog", _logEnabled.value)
            putBoolean("enableDebugLog", _debugLogEnabled.value)
        }
    }

    override fun onCleared() {
        super.onCleared()
        prefs.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
    }
}
