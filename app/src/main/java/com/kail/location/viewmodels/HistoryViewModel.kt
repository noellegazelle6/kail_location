package com.kail.location.viewmodels

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import com.kail.location.models.HistoryRecord
import com.kail.location.repositories.DataBaseHistoryLocation
import com.kail.location.utils.GoUtils
import com.kail.location.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

/**
 * 历史记录管理的 ViewModel。
 * 负责加载、搜索、删除与更新历史定位记录。
 *
 * @property application 应用上下文。
 */
class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val dbHelper = DataBaseHistoryLocation(application)
    private var db: SQLiteDatabase? = null
    private val sharedPreferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(application)

    private val _historyRecords = MutableStateFlow<List<HistoryRecord>>(emptyList())
    /**
     * 用于承载界面展示的历史记录列表。
     */
    val historyRecords: StateFlow<List<HistoryRecord>> = _historyRecords.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    /**
     * 当前的搜索关键字。
     */
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private var allRecords: List<HistoryRecord> = emptyList()

    init {
        try {
            db = dbHelper.writableDatabase
            recordArchive() // Auto-delete old records
            loadRecords()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 设置搜索关键字并过滤历史记录。
     *
     * @param query 搜索关键字。
     */
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        filterRecords(query)
    }

    /**
     * 根据搜索关键字过滤历史记录。
     *
     * @param query 搜索关键字。
     */
    private fun filterRecords(query: String) {
        if (query.isEmpty()) {
            _historyRecords.value = allRecords
        } else {
            _historyRecords.value = allRecords.filter {
                it.name.contains(query, ignoreCase = true) ||
                it.displayTime.contains(query, ignoreCase = true) ||
                it.displayBd09.contains(query, ignoreCase = true)
            }
        }
    }

    /**
     * 从数据库加载所有历史记录并更新状态。
     * 运行在 IO 线程。
     */
    fun loadRecords() {
        viewModelScope.launch(Dispatchers.IO) {
            allRecords = fetchAllRecord()
            filterRecords(_searchQuery.value)
        }
    }

    /**
     * 从 SQLite 数据库读取所有历史记录。
     *
     * @return 历史记录的列表。
     */
    private fun fetchAllRecord(): List<HistoryRecord> {
        val list = mutableListOf<HistoryRecord>()
        val database = db ?: return list

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

                val bigDecimalLongitude = BigDecimal.valueOf(longitude.toDouble())
                val bigDecimalLatitude = BigDecimal.valueOf(latitude.toDouble())
                val bigDecimalBDLongitude = BigDecimal.valueOf(bd09Longitude.toDouble())
                val bigDecimalBDLatitude = BigDecimal.valueOf(bd09Latitude.toDouble())
                val doubleLongitude = bigDecimalLongitude.setScale(11, RoundingMode.HALF_UP).toDouble()
                val doubleLatitude = bigDecimalLatitude.setScale(11, RoundingMode.HALF_UP).toDouble()
                val doubleBDLongitude = bigDecimalBDLongitude.setScale(11, RoundingMode.HALF_UP).toDouble()
                val doubleBDLatitude = bigDecimalBDLatitude.setScale(11, RoundingMode.HALF_UP).toDouble()

                list.add(HistoryRecord(
                    id = id,
                    name = location,
                    longitudeWgs84 = longitude,
                    latitudeWgs84 = latitude,
                    timestamp = timeStamp,
                    longitudeBd09 = bd09Longitude,
                    latitudeBd09 = bd09Latitude,
                    displayTime = GoUtils.timeStamp2Date(timeStamp.toString()),
                    displayWgs84 = "[经度:$doubleLongitude 纬度:$doubleLatitude]",
                    displayBd09 = "[经度:$doubleBDLongitude 纬度:$doubleBDLatitude]"
                ))
            }
            cursor.close()
        } catch (e: Exception) {
            Log.e("HistoryViewModel", "ERROR - fetchAllRecord", e)
        }
        return list
    }

    /**
     * 按 ID 删除历史记录。
     * 当 ID ≤ -1 时删除所有记录。
     *
     * @param id 要删除的记录 ID。
     */
    fun deleteRecord(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (id <= -1) {
                    db?.delete(DataBaseHistoryLocation.TABLE_NAME, null, null)
                } else {
                    db?.delete(
                        DataBaseHistoryLocation.TABLE_NAME,
                        "${DataBaseHistoryLocation.DB_COLUMN_ID} = ?",
                        arrayOf(id.toString())
                    )
                }
                loadRecords()
            } catch (e: Exception) {
                Log.e("HistoryViewModel", "ERROR - deleteRecord", e)
            }
        }
    }

    /**
     * 更新历史记录的名称。
     *
     * @param id 要更新的记录 ID。
     * @param newName 新的名称。
     */
    fun updateRecordName(id: Int, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                DataBaseHistoryLocation.updateHistoryLocation(db!!, id.toString(), newName)
                loadRecords()
            } catch (e: Exception) {
                Log.e("HistoryViewModel", "ERROR - updateRecordName", e)
            }
        }
    }

    /**
     * 根据用户设置自动清理过期的历史记录。
     */
    private fun recordArchive() {
        // Automatically delete old records based on settings
        var limits: Double
        try {
            limits = sharedPreferences.getString(
                "setting_pos_history",
                getApplication<Application>().getString(R.string.history_expiration)
            )!!.toDouble()
        } catch (e: Exception) {
            limits = 7.0
        }
        val weekSecond = (limits * 24 * 60 * 60).toLong()

        try {
            db?.delete(
                DataBaseHistoryLocation.TABLE_NAME,
                "${DataBaseHistoryLocation.DB_COLUMN_TIMESTAMP} < ?",
                arrayOf((System.currentTimeMillis() / 1000 - weekSecond).toString())
            )
        } catch (e: Exception) {
            Log.e("HistoryViewModel", "ERROR - recordArchive", e)
        }
    }
    
    /**
     * 获取记录的最终坐标；若开启随机偏移则应用偏移。
     *
     * @param record 历史记录。
     * @return (经度, 纬度) 字符串对。
     */
    fun getFinalCoordinates(record: HistoryRecord): Pair<String, String> {
        var lon = record.longitudeBd09
        var lat = record.latitudeBd09

        if (sharedPreferences.getBoolean("setting_random_offset", false)) {
            val maxOffsetDefault = getApplication<Application>().getString(R.string.setting_random_offset_default)
            val lonMaxOffset = sharedPreferences.getString("setting_lon_max_offset", maxOffsetDefault)!!.toDouble()
            val latMaxOffset = sharedPreferences.getString("setting_lat_max_offset", maxOffsetDefault)!!.toDouble()
            
            val randomLonOffset = (Math.random() * 2 - 1) * lonMaxOffset
            val randomLatOffset = (Math.random() * 2 - 1) * latMaxOffset

            val lonVal = lon.toDouble() + randomLonOffset / 111320
            val latVal = lat.toDouble() + randomLatOffset / 110574
            
            lon = lonVal.toString()
            lat = latVal.toString()

            val offsetMessage = String.format(
                Locale.US,
                "经度偏移: %.2f米\n纬度偏移: %.2f米",
                randomLonOffset,
                randomLatOffset
            )
            // Ideally we shouldn't show toast from ViewModel, but for migration it's acceptable or use a Channel/SharedFlow for events.
            // For now, I'll return the message as part of result or just show toast in UI layer?
            // Since this is just getting coordinates, I'll let the UI handle Toast if I can return it.
            // But to minimize changes, I will just display Toast here using Application Context.
             GoUtils.DisplayToast(getApplication(), offsetMessage)
        }
        
        return Pair(lon, lat)
    }

    override fun onCleared() {
        super.onCleared()
        // db?.close() 
    }
}
