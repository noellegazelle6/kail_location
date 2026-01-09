package com.kail.location.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

import com.kail.location.models.UpdateInfo
import com.kail.location.utils.UpdateChecker
import android.content.Context
import android.widget.Toast
import kotlinx.coroutines.flow.update
import com.kail.location.models.HistoryRecord
import com.kail.location.repositories.DataBaseHistoryLocation
import androidx.preference.PreferenceManager
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope

/**
 * 位置模拟页面的 ViewModel。
 * 负责位置信息状态、模拟开关、摇杆开关以及更新检查逻辑。
 *
 * @property application 应用上下文。
 */
class LocationSimulationViewModel(application: Application) : AndroidViewModel(application) {
    private val dbHelper = DataBaseHistoryLocation(application)
    private var db: SQLiteDatabase? = null

    /**
     * 当前位置信息的数据结构。
     */
    data class LocationInfo(
        val name: String = "NONE",
        val address: String = "NONE • NONE",
        val latitude: Double = 0.0,
        val longitude: Double = 0.0
    )

    private val _locationInfo = MutableStateFlow(LocationInfo())
    /**
     * 当前位置信息的状态流。
     */
    val locationInfo: StateFlow<LocationInfo> = _locationInfo.asStateFlow()

    private val _isSimulating = MutableStateFlow(false)
    /**
     * 是否正在进行模拟的状态流。
     */
    val isSimulating: StateFlow<Boolean> = _isSimulating.asStateFlow()

    private val _isJoystickEnabled = MutableStateFlow(false)
    /**
     * 摇杆是否启用的状态流。
     */
    val isJoystickEnabled: StateFlow<Boolean> = _isJoystickEnabled.asStateFlow()

    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)
    /**
     * 应用更新信息的状态流（若存在）。
     */
    val updateInfo: StateFlow<UpdateInfo?> = _updateInfo.asStateFlow()

    private val _historyRecords = MutableStateFlow<List<HistoryRecord>>(emptyList())
    val historyRecords: StateFlow<List<HistoryRecord>> = _historyRecords.asStateFlow()

    private val _selectedRecordId = MutableStateFlow<Int?>(null)
    val selectedRecordId: StateFlow<Int?> = _selectedRecordId.asStateFlow()

    private val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(application)
    private val _runMode = MutableStateFlow("noroot")
    val runMode: StateFlow<String> = _runMode.asStateFlow()

    init {
        _runMode.value = sharedPreferences.getString("setting_run_mode", "noroot") ?: "noroot"
        try {
            db = dbHelper.writableDatabase
            loadRecords()
        } catch (_: Exception) {}
    }

    /**
     * 切换模拟状态。
     */
    fun toggleSimulation() {
        _isSimulating.value = !_isSimulating.value
        // Logic to start/stop service would go here or be observed by Activity
    }

    /**
     * 设置是否启用摇杆。
     *
     * @param enabled 为 true 表示启用，false 表示关闭。
     */
    fun setJoystickEnabled(enabled: Boolean) {
        _isJoystickEnabled.value = enabled
    }

    fun setRunMode(mode: String) {
        _runMode.value = mode
        sharedPreferences.edit().putString("setting_run_mode", mode).apply()
    }

    /**
     * 检查应用更新。
     *
     * @param context 用于检查更新的上下文。
     * @param isAuto 是否为自动检查（自动检查时会抑制部分提示）。
     */
    fun checkUpdate(context: Context, isAuto: Boolean = false) {
        UpdateChecker.check(context) { info, error ->
            if (info != null) {
                _updateInfo.value = info
            } else {
                if (!isAuto) {
                    // Use MainExecutor to show toast
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
     * 通过清空更新信息来关闭更新弹窗。
     */
    fun dismissUpdateDialog() {
        _updateInfo.value = null
    }

    fun loadRecords() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = mutableListOf<HistoryRecord>()
            val database = db
            if (database != null) {
                try {
                    val cursor = database.query(
                        DataBaseHistoryLocation.TABLE_NAME, null,
                        DataBaseHistoryLocation.DB_COLUMN_ID + " > ?", arrayOf("0"),
                        null, null, DataBaseHistoryLocation.DB_COLUMN_TIMESTAMP + " DESC", null
                    )
                    while (cursor.moveToNext()) {
                        val id = cursor.getInt(0)
                        val location = cursor.getString(1)
                        val longitude = cursor.getString(2)
                        val latitude = cursor.getString(3)
                        val timeStamp = cursor.getInt(4).toLong()
                        val bd09Longitude = cursor.getString(5)
                        val bd09Latitude = cursor.getString(6)
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
                                displayBd09 = ""
                            )
                        )
                    }
                    cursor.close()
                } catch (_: Exception) {}
            }
            _historyRecords.value = list
        }
    }

    fun selectRecord(record: HistoryRecord) {
        _selectedRecordId.value = record.id
        _locationInfo.value = _locationInfo.value.copy(
            name = record.name,
            address = record.name,
            latitude = record.latitudeBd09.toDoubleOrNull() ?: 0.0,
            longitude = record.longitudeBd09.toDoubleOrNull() ?: 0.0
        )
    }

    fun renameRecord(id: Int, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                db?.let { DataBaseHistoryLocation.updateHistoryLocation(it, id.toString(), newName) }
            } catch (_: Exception) {}
            loadRecords()
        }
    }

    fun deleteRecord(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                db?.delete(DataBaseHistoryLocation.TABLE_NAME, DataBaseHistoryLocation.DB_COLUMN_ID + " = ?", arrayOf(id.toString()))
            } catch (_: Exception) {}
            loadRecords()
            if (_selectedRecordId.value == id) {
                _selectedRecordId.value = null
            }
        }
    }
}
