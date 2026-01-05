package com.kail.location.repositories

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.elvishew.xlog.XLog

/**
 * 历史定位数据的 SQLite 辅助类。
 *
 * @param context 应用上下文。
 */
class DataBaseHistoryLocation(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    /**
     * 首次创建数据库时回调。
     *
     * @param sqLiteDatabase 数据库实例。
     */
    override fun onCreate(sqLiteDatabase: SQLiteDatabase) {
        sqLiteDatabase.execSQL(CREATE_TABLE)
    }

    /**
     * 数据库需要升级时回调。
     *
     * @param sqLiteDatabase 数据库实例。
     * @param oldVersion 旧版本号。
     * @param newVersion 新版本号。
     */
    override fun onUpgrade(sqLiteDatabase: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        val sql = "DROP TABLE IF EXISTS $TABLE_NAME"
        sqLiteDatabase.execSQL(sql)
        onCreate(sqLiteDatabase)
    }

    companion object {
        const val TABLE_NAME = "HistoryLocation"
        const val DB_COLUMN_ID = "DB_COLUMN_ID"
        const val DB_COLUMN_LOCATION = "DB_COLUMN_LOCATION"
        const val DB_COLUMN_LONGITUDE_WGS84 = "DB_COLUMN_LONGITUDE_WGS84"
        const val DB_COLUMN_LATITUDE_WGS84 = "DB_COLUMN_LATITUDE_WGS84"
        const val DB_COLUMN_TIMESTAMP = "DB_COLUMN_TIMESTAMP"
        const val DB_COLUMN_LONGITUDE_CUSTOM = "DB_COLUMN_LONGITUDE_CUSTOM"
        const val DB_COLUMN_LATITUDE_CUSTOM = "DB_COLUMN_LATITUDE_CUSTOM"

        private const val DB_VERSION = 1
        private const val DB_NAME = "HistoryLocation.db"
        private const val CREATE_TABLE = "create table if not exists " + TABLE_NAME +
                " (DB_COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT, DB_COLUMN_LOCATION TEXT, " +
                "DB_COLUMN_LONGITUDE_WGS84 TEXT NOT NULL, DB_COLUMN_LATITUDE_WGS84 TEXT NOT NULL, " +
                "DB_COLUMN_TIMESTAMP BIGINT NOT NULL, DB_COLUMN_LONGITUDE_CUSTOM TEXT NOT NULL, DB_COLUMN_LATITUDE_CUSTOM TEXT NOT NULL)"

        /**
         * 保存历史定位记录。
         * 在插入前，若存在相同 WGS84 坐标的记录则先删除。
         *
         * @param sqLiteDatabase 数据库实例。
         * @param contentValues 要保存的键值对。
         */
        @JvmStatic
        fun saveHistoryLocation(sqLiteDatabase: SQLiteDatabase, contentValues: ContentValues) {
            try {
                // 先删除原来的记录，再插入新记录
                val longitudeWgs84 = contentValues.getAsString(DB_COLUMN_LONGITUDE_WGS84)
                val latitudeWgs84 = contentValues.getAsString(DB_COLUMN_LATITUDE_WGS84)
                sqLiteDatabase.delete(
                    TABLE_NAME,
                    "$DB_COLUMN_LONGITUDE_WGS84 = ? AND $DB_COLUMN_LATITUDE_WGS84 = ?",
                    arrayOf(longitudeWgs84, latitudeWgs84)
                )
                sqLiteDatabase.insert(TABLE_NAME, null, contentValues)
            } catch (e: Exception) {
                XLog.e("DATABASE: insert error")
            }
        }

        /**
         * 新增一条历史定位记录。
         *
         * @param sqLiteDatabase 数据库实例。
         * @param name 位置名称。
         * @param lonWgs84 WGS84 经度。
         * @param latWgs84 WGS84 纬度。
         * @param timestamp 时间戳字符串。
         * @param lonCustom 自定义经度（BD-09）。
         * @param latCustom 自定义纬度（BD-09）。
         */
        @JvmStatic
        fun addHistoryLocation(
            sqLiteDatabase: SQLiteDatabase?,
            name: String,
            lonWgs84: String,
            latWgs84: String,
            timestamp: String,
            lonCustom: String,
            latCustom: String
        ) {
            if (sqLiteDatabase == null) return
            val contentValues = ContentValues()
            contentValues.put(DB_COLUMN_LOCATION, name)
            contentValues.put(DB_COLUMN_LONGITUDE_WGS84, lonWgs84)
            contentValues.put(DB_COLUMN_LATITUDE_WGS84, latWgs84)
            contentValues.put(DB_COLUMN_TIMESTAMP, timestamp)
            contentValues.put(DB_COLUMN_LONGITUDE_CUSTOM, lonCustom)
            contentValues.put(DB_COLUMN_LATITUDE_CUSTOM, latCustom)
            saveHistoryLocation(sqLiteDatabase, contentValues)
        }

        /**
         * 更新历史定位记录的名称。
         *
         * @param sqLiteDatabase 数据库实例。
         * @param locID 要更新的记录 ID。
         * @param location 新的位置名称。
         */
        @JvmStatic
        fun updateHistoryLocation(sqLiteDatabase: SQLiteDatabase, locID: String, location: String?) {
            try {
                val contentValues = ContentValues()
                contentValues.put(DB_COLUMN_LOCATION, location)
                sqLiteDatabase.update(TABLE_NAME, contentValues, "$DB_COLUMN_ID = ?", arrayOf(locID))
            } catch (e: Exception) {
                XLog.e("DATABASE: update error")
            }
        }
    }
}
