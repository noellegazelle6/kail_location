package com.kail.location.viewmodels

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.preference.PreferenceManager
import com.kail.location.utils.GoUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(application)

    // Keys
    companion object {
        const val KEY_JOYSTICK_TYPE = "setting_joystick_type"
        const val KEY_WALK_SPEED = "setting_walk"
        const val KEY_RUN_SPEED = "setting_run"
        const val KEY_BIKE_SPEED = "setting_bike"
        const val KEY_ALTITUDE = "setting_altitude"
        const val KEY_LAT_OFFSET = "setting_lat_max_offset"
        const val KEY_LON_OFFSET = "setting_lon_max_offset"
        const val KEY_RANDOM_OFFSET = "setting_random_offset"
        const val KEY_LOG_OFF = "setting_log_off"
        const val KEY_HISTORY_EXPIRATION = "setting_history_expiration"
    }

    private val _joystickType = MutableStateFlow(prefs.getString(KEY_JOYSTICK_TYPE, "0") ?: "0")
    val joystickType: StateFlow<String> = _joystickType.asStateFlow()

    private val _walkSpeed = MutableStateFlow(prefs.getString(KEY_WALK_SPEED, "1.2") ?: "1.2")
    val walkSpeed: StateFlow<String> = _walkSpeed.asStateFlow()

    private val _runSpeed = MutableStateFlow(prefs.getString(KEY_RUN_SPEED, "3.6") ?: "3.6")
    val runSpeed: StateFlow<String> = _runSpeed.asStateFlow()

    private val _bikeSpeed = MutableStateFlow(prefs.getString(KEY_BIKE_SPEED, "10.0") ?: "10.0")
    val bikeSpeed: StateFlow<String> = _bikeSpeed.asStateFlow()

    private val _altitude = MutableStateFlow(prefs.getString(KEY_ALTITUDE, "55.0") ?: "55.0")
    val altitude: StateFlow<String> = _altitude.asStateFlow()

    private val _latOffset = MutableStateFlow(prefs.getString(KEY_LAT_OFFSET, "10.0") ?: "10.0")
    val latOffset: StateFlow<String> = _latOffset.asStateFlow()

    private val _lonOffset = MutableStateFlow(prefs.getString(KEY_LON_OFFSET, "10.0") ?: "10.0")
    val lonOffset: StateFlow<String> = _lonOffset.asStateFlow()
    
    private val _randomOffset = MutableStateFlow(prefs.getBoolean(KEY_RANDOM_OFFSET, false))
    val randomOffset: StateFlow<Boolean> = _randomOffset.asStateFlow()

    private val _logOff = MutableStateFlow(prefs.getBoolean(KEY_LOG_OFF, false))
    val logOff: StateFlow<Boolean> = _logOff.asStateFlow()

    private val _historyExpiration = MutableStateFlow(prefs.getString(KEY_HISTORY_EXPIRATION, "7.0") ?: "7.0")
    val historyExpiration: StateFlow<String> = _historyExpiration.asStateFlow()

    val appVersion: String = GoUtils.getVersionName(application)

    private val preferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
        when (key) {
            KEY_JOYSTICK_TYPE -> _joystickType.value = sharedPreferences.getString(key, "0") ?: "0"
            KEY_WALK_SPEED -> _walkSpeed.value = sharedPreferences.getString(key, "1.2") ?: "1.2"
            KEY_RUN_SPEED -> _runSpeed.value = sharedPreferences.getString(key, "3.6") ?: "3.6"
            KEY_BIKE_SPEED -> _bikeSpeed.value = sharedPreferences.getString(key, "10.0") ?: "10.0"
            KEY_ALTITUDE -> _altitude.value = sharedPreferences.getString(key, "55.0") ?: "55.0"
            KEY_LAT_OFFSET -> _latOffset.value = sharedPreferences.getString(key, "10.0") ?: "10.0"
            KEY_LON_OFFSET -> _lonOffset.value = sharedPreferences.getString(key, "10.0") ?: "10.0"
            KEY_RANDOM_OFFSET -> _randomOffset.value = sharedPreferences.getBoolean(key, false)
            KEY_LOG_OFF -> _logOff.value = sharedPreferences.getBoolean(key, false)
            KEY_HISTORY_EXPIRATION -> _historyExpiration.value = sharedPreferences.getString(key, "7.0") ?: "7.0"
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
    }

    fun updateStringPreference(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    fun updateBooleanPreference(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    override fun onCleared() {
        super.onCleared()
        prefs.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
    }
}
