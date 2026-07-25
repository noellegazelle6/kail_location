package com.kail.location.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history_routes ORDER BY timestamp DESC LIMIT 10")
    fun getRecentRoutes(): Flow<List<HistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoute(route: HistoryEntity)

    @Query("UPDATE history_routes SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavorite(id: Long, isFavorite: Boolean)

    @Query("DELETE FROM history_routes WHERE id = :id")
    suspend fun deleteRoute(id: Long)

    @Query("UPDATE history_routes SET startName = :startName, endName = :endName WHERE id = :id")
    suspend fun updateName(id: Long, startName: String, endName: String)

    @Query("UPDATE history_routes SET timestamp = :timestamp WHERE id = :id")
    suspend fun updateTimestamp(id: Long, timestamp: Long)

    @Query("DELETE FROM history_routes")
    suspend fun clearAll()
}
