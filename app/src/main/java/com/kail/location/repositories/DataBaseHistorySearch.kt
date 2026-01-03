package com.kail.location.repositories

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.elvishew.xlog.XLog

class DataBaseHistorySearch(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    override fun onCreate(sqLiteDatabase: SQLiteDatabase) {
        sqLiteDatabase.execSQL(CREATE_TABLE)
    }

    override fun onUpgrade(sqLiteDatabase: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        val sql = "DROP TABLE IF EXISTS $TABLE_NAME"
        sqLiteDatabase.execSQL(sql)
        onCreate(sqLiteDatabase)
    }

    companion object {
        const val TABLE_NAME = "HistorySearch"
        const val DB_COLUMN_ID = "DB_COLUMN_ID"
        const val DB_COLUMN_KEY = "DB_COLUMN_KEY"
        const val DB_COLUMN_DESCRIPTION = "DB_COLUMN_DESCRIPTION"
        const val DB_COLUMN_TIMESTAMP = "DB_COLUMN_TIMESTAMP"
        const val DB_COLUMN_IS_LOCATION = "DB_COLUMN_IS_LOCATION"
        const val DB_COLUMN_LONGITUDE_WGS84 = "DB_COLUMN_LONGITUDE_WGS84"
        const val DB_COLUMN_LATITUDE_WGS84 = "DB_COLUMN_LATITUDE_WGS84"
        const val DB_COLUMN_LONGITUDE_CUSTOM = "DB_COLUMN_LONGITUDE_CUSTOM"
        const val DB_COLUMN_LATITUDE_CUSTOM = "DB_COLUMN_LATITUDE_CUSTOM"
        // 搜索的关键字
        const val DB_SEARCH_TYPE_KEY = 0
        // 搜索结果
        const val DB_SEARCH_TYPE_RESULT = 1

        private const val DB_VERSION = 1
        private const val DB_NAME = "HistorySearch.db"
        private const val CREATE_TABLE = "create table if not exists " + TABLE_NAME +
                " (DB_COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT, DB_COLUMN_KEY TEXT NOT NULL, " +
                "DB_COLUMN_DESCRIPTION TEXT, DB_COLUMN_TIMESTAMP BIGINT NOT NULL, DB_COLUMN_IS_LOCATION INTEGER NOT NULL, " +
                "DB_COLUMN_LONGITUDE_WGS84 TEXT, DB_COLUMN_LATITUDE_WGS84 TEXT, " +
                "DB_COLUMN_LONGITUDE_CUSTOM TEXT, DB_COLUMN_LATITUDE_CUSTOM TEXT)"

        @JvmStatic
        fun saveHistorySearch(sqLiteDatabase: SQLiteDatabase, contentValues: ContentValues) {
            try {
                // 先删除原来的记录，再插入新记录
                val searchKey = contentValues.get(DB_COLUMN_KEY).toString()
                sqLiteDatabase.delete(TABLE_NAME, "$DB_COLUMN_KEY = ?", arrayOf(searchKey))
                sqLiteDatabase.insert(TABLE_NAME, null, contentValues)
            } catch (e: Exception) {
                XLog.e("DATABASE: insert error")
            }
        }

        @JvmStatic
        fun addHistorySearch(sqLiteDatabase: SQLiteDatabase?, key: String) {
            if (sqLiteDatabase == null) return
            try {
                val contentValues = ContentValues()
                contentValues.put(DB_COLUMN_KEY, key)
                contentValues.put(DB_COLUMN_DESCRIPTION, "搜索关键字")
                contentValues.put(DB_COLUMN_IS_LOCATION, DB_SEARCH_TYPE_KEY)
                contentValues.put(DB_COLUMN_TIMESTAMP, System.currentTimeMillis() / 1000)
                saveHistorySearch(sqLiteDatabase, contentValues)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
